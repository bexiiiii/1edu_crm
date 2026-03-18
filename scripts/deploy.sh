#!/bin/bash
# =============================================================================
# 1edu CRM — Production Deploy Script
# Usage:
#   ./scripts/deploy.sh           # Full deploy (build + start)
#   ./scripts/deploy.sh --no-build  # Skip Gradle build (images already built)
#   ./scripts/deploy.sh --service notification-service  # Deploy single service
# =============================================================================

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

# ─── Colors ──────────────────────────────────────────────────────────────────
RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'
CYAN='\033[0;36m'; BOLD='\033[1m'; NC='\033[0m'

log()  { echo -e "${CYAN}[$(date '+%H:%M:%S')]${NC} $*"; }
ok()   { echo -e "${GREEN}[$(date '+%H:%M:%S')] ✓${NC} $*"; }
warn() { echo -e "${YELLOW}[$(date '+%H:%M:%S')] ⚠${NC} $*"; }
fail() { echo -e "${RED}[$(date '+%H:%M:%S')] ✗${NC} $*"; exit 1; }

# ─── Args ─────────────────────────────────────────────────────────────────────
BUILD=true
SINGLE_SERVICE=""

while [[ $# -gt 0 ]]; do
    case "$1" in
        --no-build)   BUILD=false; shift ;;
        --service)    SINGLE_SERVICE="$2"; shift 2 ;;
        -h|--help)
            echo "Usage: $0 [--no-build] [--service <name>]"
            exit 0 ;;
        *) fail "Unknown argument: $1" ;;
    esac
done

# ─── Single service deploy ────────────────────────────────────────────────────
if [[ -n "$SINGLE_SERVICE" ]]; then
    log "Deploying single service: ${BOLD}$SINGLE_SERVICE${NC}"
    docker compose stop "$SINGLE_SERVICE" || true
    if $BUILD; then
        log "Building JAR for $SINGLE_SERVICE..."
        ./gradlew ":services:${SINGLE_SERVICE}:bootJar" -x test --no-daemon
    fi
    docker compose build --no-deps "$SINGLE_SERVICE"
    docker compose up -d --no-deps "$SINGLE_SERVICE"
    ok "Service $SINGLE_SERVICE deployed."
    exit 0
fi

# ─── Full deploy ──────────────────────────────────────────────────────────────
echo ""
echo -e "${BOLD}╔══════════════════════════════════════════════════╗${NC}"
echo -e "${BOLD}║       1edu CRM — Production Deploy               ║${NC}"
echo -e "${BOLD}╚══════════════════════════════════════════════════╝${NC}"
echo ""

# ─── Step 1: Build JARs ───────────────────────────────────────────────────────
if $BUILD; then
    log "Building all JARs (skipping tests)..."
    ./gradlew build -x test --no-daemon
    ok "All JARs built."
fi

# ─── Step 2: Stop running services gracefully ────────────────────────────────
log "Stopping all services..."
docker compose down --timeout 30 || true
ok "All containers stopped."

# ─── Step 3: Build Docker images ─────────────────────────────────────────────
log "Building Docker images..."
docker compose build
ok "Docker images built."

# ─── Helper: wait for container healthy ──────────────────────────────────────
wait_healthy() {
    local name="$1"
    local max_wait="${2:-300}"  # seconds
    local interval=5
    local elapsed=0

    log "Waiting for ${BOLD}$name${NC} to become healthy..."
    while true; do
        local status
        status=$(docker inspect --format='{{.State.Health.Status}}' "$name" 2>/dev/null || echo "missing")

        case "$status" in
            healthy)
                ok "$name is healthy."
                return 0 ;;
            unhealthy)
                warn "$name marked unhealthy. Logs:"
                docker logs --tail=20 "$name" 2>&1 | sed 's/^/    /'
                return 1 ;;
            missing)
                warn "$name container not found."
                return 1 ;;
        esac

        if (( elapsed >= max_wait )); then
            warn "$name did not become healthy in ${max_wait}s — continuing anyway."
            return 0
        fi

        sleep $interval
        (( elapsed += interval ))
        echo -ne "  ${YELLOW}waiting... ${elapsed}s${NC}\r"
    done
}

# ─── Helper: start a group of services ───────────────────────────────────────
start_group() {
    local label="$1"; shift
    log "Starting group: ${BOLD}$label${NC}"
    docker compose up -d --no-deps "$@"
}

# ─── Step 4: Infrastructure ───────────────────────────────────────────────────
log "═══ [1/5] Infrastructure ═══"
start_group "infrastructure" \
    postgres redis rabbitmq mongodb elasticsearch minio keycloak \
    prometheus grafana zipkin postgres-exporter redis-exporter

log "Waiting for core infra to be healthy (up to 3 min)..."
wait_healthy "1edu-postgres"   180
wait_healthy "1edu-redis"      60
wait_healthy "1edu-rabbitmq"   120
wait_healthy "1edu-mongodb"    60

ok "Infrastructure ready."
echo ""

# ─── Step 5: Service Registry (Eureka) ───────────────────────────────────────
log "═══ [2/5] Service Registry ═══"
start_group "service-registry" service-registry
wait_healthy "1edu-service-registry" 180
echo ""

# ─── Step 6: Gateway + Auth ───────────────────────────────────────────────────
log "═══ [3/5] API Gateway + Auth ═══"
start_group "gateway & auth" api-gateway auth-service
wait_healthy "1edu-api-gateway" 240
echo ""

# ─── Step 7: Tenant + Notification (early — other services may call them) ────
log "═══ [4/5] Tenant + Notification ═══"
start_group "tenant & notification" tenant-service notification-service
wait_healthy "1edu-tenant-service" 300
echo ""

# ─── Step 8: All business services ───────────────────────────────────────────
log "═══ [5/5] Business Services ═══"
start_group "business services" \
    student-service lead-service course-service schedule-service \
    payment-service finance-service analytics-service \
    file-service report-service staff-service task-service \
    lesson-service settings-service audit-service

log "Waiting for business services to start (up to 5 min)..."
sleep 60  # give them time to register with Eureka

ok "Business services started."
echo ""

# ─── Done ─────────────────────────────────────────────────────────────────────
echo -e "${BOLD}${GREEN}"
echo "╔══════════════════════════════════════════════════╗"
echo "║              Deploy Complete! 🚀                  ║"
echo "╚══════════════════════════════════════════════════╝"
echo -e "${NC}"
log "Running containers:"
docker compose ps --format "table {{.Name}}\t{{.Status}}" | grep -v "^$"

echo ""
echo -e "  ${CYAN}API Gateway:${NC}       https://beta.1edu.kz"
echo -e "  ${CYAN}Keycloak Admin:${NC}    https://beta.1edu.kz/auth/admin"
echo -e "  ${CYAN}Grafana:${NC}           http://localhost:3000"
echo -e "  ${CYAN}RabbitMQ UI:${NC}       http://localhost:15672"
echo -e "  ${CYAN}Zipkin:${NC}            http://localhost:9411"
echo ""
echo -e "  ${YELLOW}Logs:${NC} docker compose logs -f <service>"
