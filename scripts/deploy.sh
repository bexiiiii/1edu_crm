#!/bin/bash

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

COMPOSE_FILE="docker-compose.prod.yml"
PROJECT_NAME="1edu"
NETWORK_NAME="1edu_1edu-network"
TEMP_CERTBOT_WEB="1edu-certbot-bootstrap"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
CYAN='\033[0;36m'
NC='\033[0m'

log()  { echo -e "${CYAN}[$(date '+%H:%M:%S')]${NC} $*"; }
ok()   { echo -e "${GREEN}[$(date '+%H:%M:%S')]${NC} $*"; }
warn() { echo -e "${YELLOW}[$(date '+%H:%M:%S')]${NC} $*"; }
fail() { echo -e "${RED}[$(date '+%H:%M:%S')]${NC} $*" >&2; exit 1; }

compose() {
    docker compose -f "$COMPOSE_FILE" -p "$PROJECT_NAME" "$@"
}

load_env() {
    if [[ ! -f .env ]]; then
        if [[ -f .env.production ]]; then
            cp .env.production .env
            fail ".env was missing. A template was copied from .env.production; fill in secrets and rerun."
        fi
        fail ".env is missing."
    fi

    set -a
    # shellcheck disable=SC1091
    source ./.env
    set +a
}

require_cmd() {
    command -v "$1" >/dev/null 2>&1 || fail "Required command not found: $1"
}

require_var() {
    local name="$1"
    [[ -n "${!name:-}" ]] || fail "Required variable is empty: $name"
}

check_prereqs() {
    require_cmd docker
    docker compose version >/dev/null 2>&1 || fail "docker compose plugin is not available"
    require_cmd java
    require_cmd git
}

check_resources() {
    local total_mem total_cpu disk_free
    total_mem=$(free -g 2>/dev/null | awk '/^Mem:/{print $2}' || echo "unknown")
    total_cpu=$(nproc 2>/dev/null || echo "unknown")
    disk_free=$(df -BG / | awk 'NR==2{print $4}' | tr -d 'G' || echo "unknown")

    log "Resources: RAM=${total_mem}G CPU=${total_cpu} DiskFree=${disk_free}G"

    if [[ "$total_mem" != "unknown" && "$total_mem" -lt 8 ]]; then
        warn "This stack is not comfortable below 8 GB RAM."
    fi
}

ensure_directories() {
    mkdir -p infrastructure/certbot/conf infrastructure/certbot/www infrastructure/nginx/tenants
}

ensure_network() {
    if ! docker network inspect "$NETWORK_NAME" >/dev/null 2>&1; then
        log "Creating Docker network $NETWORK_NAME"
        docker network create "$NETWORK_NAME" >/dev/null
    fi
}

prepare_keycloak_realm() {
    load_env
    require_var KEYCLOAK_CLIENT_SECRET

    local realm_file="infrastructure/keycloak/realms/ondeedu-realm.json"
    if grep -q '__CRM_ADMIN_CLIENT_SECRET__' "$realm_file"; then
        log "Injecting crm-admin client secret into realm import"
        sed -i "s/__CRM_ADMIN_CLIENT_SECRET__/${KEYCLOAK_CLIENT_SECRET}/g" "$realm_file"
    fi
}

build_all_jars() {
    log "Building all JARs"
    ./gradlew build -x test --no-daemon
}

build_service_jar() {
    local service="$1"
    log "Building JAR for $service"
    ./gradlew ":services:${service}:bootJar" -x test --no-daemon
}

get_container_id() {
    compose ps -q "$1" 2>/dev/null | head -1
}

wait_healthy() {
    local service="$1"
    local max_wait="${2:-180}"
    local elapsed=0
    local status=""

    while (( elapsed < max_wait )); do
        local cid
        cid=$(get_container_id "$service")

        if [[ -n "$cid" ]]; then
            status=$(docker inspect --format='{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' "$cid" 2>/dev/null || echo "missing")
            if [[ "$status" == "healthy" || "$status" == "running" ]]; then
                ok "$service is $status"
                return 0
            fi
            if [[ "$status" == "unhealthy" || "$status" == "exited" ]]; then
                warn "$service is $status"
                docker logs --tail=50 "$cid" 2>&1 | sed 's/^/    /' || true
                return 1
            fi
        fi

        sleep 5
        elapsed=$((elapsed + 5))
    done

    warn "$service did not become healthy in ${max_wait}s"
    local cid
    cid=$(get_container_id "$service")
    if [[ -n "$cid" ]]; then
        docker logs --tail=50 "$cid" 2>&1 | sed 's/^/    /' || true
    fi
    return 1
}

ensure_api_certificate() {
    load_env

    require_var API_DOMAIN
    require_var CERT_EMAIL

    local cert_path="infrastructure/certbot/conf/live/${API_DOMAIN}/fullchain.pem"
    local temp_started=false

    if ! docker ps --format '{{.Names}}' | grep -q '^1edu-nginx$'; then
        log "Starting temporary webroot server for ACME challenge"
        docker rm -f "$TEMP_CERTBOT_WEB" >/dev/null 2>&1 || true
        docker run -d --name "$TEMP_CERTBOT_WEB" -p 80:80 \
            -v "$ROOT/infrastructure/certbot/www:/usr/share/nginx/html:ro" \
            nginx:alpine >/dev/null
        temp_started=true
    fi

    log "Ensuring TLS certificate for ${API_DOMAIN}"
    docker run --rm \
        -v "$ROOT/infrastructure/certbot/conf:/etc/letsencrypt" \
        -v "$ROOT/infrastructure/certbot/www:/var/www/certbot" \
        certbot/certbot certonly \
        --webroot \
        --webroot-path=/var/www/certbot \
        --email "$CERT_EMAIL" \
        --agree-tos \
        --non-interactive \
        --no-eff-email \
        --keep-until-expiring \
        -d "$API_DOMAIN"

    if [[ "$temp_started" == true ]]; then
        docker rm -f "$TEMP_CERTBOT_WEB" >/dev/null 2>&1 || true
    fi

    [[ -f "$cert_path" ]] || fail "TLS certificate was not created for ${API_DOMAIN}"
    ok "TLS certificate is ready for ${API_DOMAIN}"
}

deploy_infra() {
    prepare_keycloak_realm
    log "Starting infrastructure"
    compose up -d postgres redis rabbitmq elasticsearch mongodb minio keycloak zipkin prometheus grafana

    wait_healthy postgres 180
    wait_healthy redis 90
    wait_healthy rabbitmq 180
    wait_healthy mongodb 180
    wait_healthy minio 120
    wait_healthy elasticsearch 180
    wait_healthy keycloak 240
}

deploy_services() {
    log "Starting service-registry"
    compose up -d --build service-registry
    wait_healthy service-registry 180

    log "Starting gateway, auth, and tenant bootstrap services"
    compose up -d --build api-gateway auth-service tenant-service notification-service
    wait_healthy api-gateway 240
    wait_healthy auth-service 180
    wait_healthy tenant-service 240

    log "Starting core business services"
    compose up -d --build \
        student-service lead-service course-service schedule-service \
        payment-service finance-service analytics-service \
        file-service staff-service task-service lesson-service settings-service

    wait_healthy student-service 420
    wait_healthy payment-service 300
    wait_healthy analytics-service 600
    wait_healthy file-service 240
    wait_healthy settings-service 240

    log "Starting dependent services"
    compose up -d --build report-service audit-service
    wait_healthy report-service 180
    wait_healthy audit-service 180

    log "Starting edge services"
    compose up -d --build nginx cert-issuer
}

verify_edge() {
    load_env
    require_var API_DOMAIN

    log "Verifying public health endpoint"
    curl -fsS --retry 12 --retry-delay 5 "https://${API_DOMAIN}/health" >/dev/null
    ok "Public HTTPS health check passed"
}

show_status() {
    compose ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}"
    local ids
    ids=$(compose ps -q)
    if [[ -n "$ids" ]]; then
        echo
        docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}" $ids 2>/dev/null || true
    fi
}

show_logs() {
    if [[ -n "${1:-}" ]]; then
        compose logs -f --tail=100 "$1"
    else
        compose logs -f --tail=100
    fi
}

restart_service() {
    local service="${1:-}"
    [[ -n "$service" ]] || fail "Usage: ./deploy.sh restart <service-name>"

    build_service_jar "$service"
    log "Rebuilding and restarting $service"
    compose up -d --build --no-deps "$service"
    wait_healthy "$service" 180 || true
}

run_backup() {
    "$ROOT/scripts/backup.sh"
}

stop_all() {
    compose down
}

full_deploy() {
    load_env
    require_var BASE_DOMAIN
    require_var API_DOMAIN
    require_var CERT_EMAIL
    require_var KEYCLOAK_INTERNAL_URL
    require_var KEYCLOAK_PUBLIC_URL

    check_resources
    ensure_directories
    ensure_network
    build_all_jars
    ensure_api_certificate
    deploy_infra
    deploy_services
    verify_edge
    echo
    show_status
}

renew_or_issue_ssl() {
    ensure_directories
    ensure_network
    ensure_api_certificate

    if docker ps --format '{{.Names}}' | grep -q '^1edu-nginx$'; then
        compose exec -T nginx nginx -s reload
        ok "nginx reloaded"
    fi
}

usage() {
    cat <<'EOF'
Usage: ./deploy.sh [command]

Commands:
  full           Build JARs, ensure TLS, and deploy the full production stack
  infra          Start only infrastructure services
  services       Build JARs and start/restart application services
  ssl            Issue or renew the API certificate and reload nginx
  status         Show container status and one-shot resource usage
  logs [svc]     Tail logs for all services or one service
  restart <svc>  Rebuild and restart a single service
  backup         Run the backup script
  stop           Stop the production stack
EOF
}

main() {
    check_prereqs

    case "${1:-full}" in
        full)
            full_deploy
            ;;
        infra)
            load_env
            ensure_directories
            ensure_network
            deploy_infra
            ;;
        services)
            load_env
            ensure_directories
            ensure_network
            build_all_jars
            deploy_services
            verify_edge
            ;;
        ssl)
            load_env
            renew_or_issue_ssl
            ;;
        status)
            show_status
            ;;
        logs)
            show_logs "${2:-}"
            ;;
        restart)
            restart_service "${2:-}"
            ;;
        backup)
            run_backup
            ;;
        stop)
            stop_all
            ;;
        -h|--help|help)
            usage
            ;;
        *)
            usage
            exit 1
            ;;
    esac
}

main "$@"
