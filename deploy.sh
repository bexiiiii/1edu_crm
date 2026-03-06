#!/bin/bash
# ============================================================
# 1edu CRM — Production Deploy Script
# ============================================================
# Usage:
#   ./deploy.sh                  # Full deploy (infra → services)
#   ./deploy.sh infra            # Only infrastructure
#   ./deploy.sh services         # Only business services (rebuild)
#   ./deploy.sh ssl              # Setup SSL certificate
#   ./deploy.sh status           # Check all containers
#   ./deploy.sh logs <service>   # Tail service logs
#   ./deploy.sh restart <service># Restart one service
#   ./deploy.sh backup           # Backup databases
# ============================================================

set -euo pipefail

COMPOSE_FILE="docker-compose.prod.yml"
PROJECT_NAME="1edu"

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

log()   { echo -e "${GREEN}[$(date '+%H:%M:%S')]${NC} $1"; }
warn()  { echo -e "${YELLOW}[$(date '+%H:%M:%S')] ⚠ $1${NC}"; }
error() { echo -e "${RED}[$(date '+%H:%M:%S')] ✗ $1${NC}"; exit 1; }

# ─── Check prerequisites ────────────────────────────────────
check_prereqs() {
    command -v docker >/dev/null 2>&1 || error "Docker not installed"
    command -v docker compose >/dev/null 2>&1 || error "Docker Compose not installed"
    
    if [ ! -f ".env" ]; then
        if [ -f ".env.production" ]; then
            warn ".env not found, copying from .env.production"
            cp .env.production .env
            warn "IMPORTANT: Edit .env and change all CHANGE_ME passwords!"
            exit 1
        else
            error ".env file not found. Copy .env.production to .env and configure it."
        fi
    fi
}

# ─── Check system resources ──────────────────────────────────
check_resources() {
    log "Checking system resources..."
    
    TOTAL_MEM=$(free -g 2>/dev/null | awk '/^Mem:/{print $2}' || echo "unknown")
    TOTAL_CPU=$(nproc 2>/dev/null || echo "unknown")
    DISK_FREE=$(df -BG / | awk 'NR==2{print $4}' | tr -d 'G' || echo "unknown")
    
    log "RAM: ${TOTAL_MEM}GB | CPUs: ${TOTAL_CPU} | Disk free: ${DISK_FREE}GB"
    
    if [ "$TOTAL_MEM" != "unknown" ] && [ "$TOTAL_MEM" -lt 12 ]; then
        warn "Recommended minimum RAM: 16GB. You have ${TOTAL_MEM}GB."
        warn "Services may run slowly or crash."
    fi
    
    if [ "$DISK_FREE" != "unknown" ] && [ "$DISK_FREE" -lt 30 ]; then
        warn "Low disk space: ${DISK_FREE}GB free. Recommended: 50GB+"
    fi
}

# ─── Deploy infrastructure ──────────────────────────────────
deploy_infra() {
    log "━━━ LAYER 1: Starting data stores ━━━"
    docker compose -f $COMPOSE_FILE -p $PROJECT_NAME up -d \
        postgres redis rabbitmq elasticsearch mongodb minio
    
    log "Waiting for data stores to become healthy..."
    wait_healthy postgres redis rabbitmq mongodb minio
    wait_healthy_timeout elasticsearch 120
    
    log "━━━ LAYER 2: Starting platform services ━━━"
    docker compose -f $COMPOSE_FILE -p $PROJECT_NAME up -d \
        keycloak zipkin prometheus grafana
    
    log "Waiting for Keycloak to start (this takes a while)..."
    wait_healthy_timeout keycloak 180
    
    log "Infrastructure is ready!"
}

# ─── Deploy application services ────────────────────────────
deploy_services() {
    log "━━━ LAYER 3: Starting service registry ━━━"
    docker compose -f $COMPOSE_FILE -p $PROJECT_NAME up -d --build service-registry
    wait_healthy_timeout service-registry 120
    
    log "━━━ LAYER 4: Starting API gateway ━━━"
    docker compose -f $COMPOSE_FILE -p $PROJECT_NAME up -d --build api-gateway
    wait_healthy_timeout api-gateway 120
    
    log "━━━ LAYER 5: Starting business services (batch 1/4) ━━━"
    docker compose -f $COMPOSE_FILE -p $PROJECT_NAME up -d --build \
        tenant-service \
        auth-service \
        student-service
    wait_healthy_timeout tenant-service 180
    
    log "━━━ LAYER 5: Starting business services (batch 2/4) ━━━"
    docker compose -f $COMPOSE_FILE -p $PROJECT_NAME up -d --build \
        lead-service \
        course-service \
        schedule-service
    sleep 10
    
    log "━━━ LAYER 5: Starting business services (batch 3/4) ━━━"
    docker compose -f $COMPOSE_FILE -p $PROJECT_NAME up -d --build \
        payment-service \
        finance-service \
        analytics-service
    sleep 10
    
    log "━━━ LAYER 5: Starting business services (batch 4/4) ━━━"
    docker compose -f $COMPOSE_FILE -p $PROJECT_NAME up -d --build \
        staff-service \
        task-service \
        lesson-service \
        settings-service \
        notification-service \
        file-service
    
    log "Waiting for business services..."
    sleep 30
    
    # Services that depend on other business services
    log "━━━ LAYER 6: Starting dependent services ━━━"
    docker compose -f $COMPOSE_FILE -p $PROJECT_NAME up -d --build \
        report-service \
        audit-service
    
    log "━━━ LAYER 7: Starting reverse proxy ━━━"
    docker compose -f $COMPOSE_FILE -p $PROJECT_NAME up -d nginx
    
    log "All services started!"
}

# ─── Wait for container to be healthy ────────────────────────
wait_healthy() {
    for service in "$@"; do
        local max_wait=90
        local waited=0
        while [ $waited -lt $max_wait ]; do
            status=$(docker inspect --format='{{.State.Health.Status}}' "${PROJECT_NAME}-${service}" 2>/dev/null || echo "not_found")
            if [ "$status" = "healthy" ]; then
                log "  ✓ ${service} is healthy"
                break
            fi
            sleep 5
            waited=$((waited + 5))
        done
        if [ $waited -ge $max_wait ]; then
            warn "  ${service} not healthy after ${max_wait}s (status: ${status})"
        fi
    done
}

wait_healthy_timeout() {
    local service=$1
    local max_wait=${2:-90}
    local waited=0
    while [ $waited -lt $max_wait ]; do
        status=$(docker inspect --format='{{.State.Health.Status}}' "${PROJECT_NAME}-${service}" 2>/dev/null || echo "not_found")
        if [ "$status" = "healthy" ]; then
            log "  ✓ ${service} is healthy"
            return
        fi
        sleep 5
        waited=$((waited + 5))
    done
    warn "  ${service} not healthy after ${max_wait}s (status: ${status})"
}

# ─── Status ──────────────────────────────────────────────────
show_status() {
    echo ""
    log "━━━ Container Status ━━━"
    docker compose -f $COMPOSE_FILE -p $PROJECT_NAME ps --format "table {{.Name}}\t{{.Status}}\t{{.Ports}}"
    echo ""
    
    log "━━━ Resource Usage ━━━"
    docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}\t{{.MemPerc}}" \
        $(docker compose -f $COMPOSE_FILE -p $PROJECT_NAME ps -q) 2>/dev/null || true
}

# ─── SSL Setup ───────────────────────────────────────────────
setup_ssl() {
    source .env
    
    if [ -z "${DOMAIN:-}" ] || [ "$DOMAIN" = "your-domain.com" ]; then
        error "Set DOMAIN in .env file first"
    fi
    
    log "Setting up SSL for ${DOMAIN}..."
    
    # Start nginx first with HTTP only for certbot challenge
    docker compose -f $COMPOSE_FILE -p $PROJECT_NAME up -d nginx
    
    # Get certificate
    docker compose -f $COMPOSE_FILE -p $PROJECT_NAME run --rm certbot \
        certbot certonly --webroot \
        --webroot-path=/var/www/certbot \
        --email admin@${DOMAIN} \
        --agree-tos \
        --no-eff-email \
        -d ${DOMAIN} \
        -d www.${DOMAIN}
    
    # Reload nginx with SSL
    docker compose -f $COMPOSE_FILE -p $PROJECT_NAME exec nginx nginx -s reload
    
    log "SSL certificate installed!"
}

# ─── Backup ──────────────────────────────────────────────────
backup() {
    local BACKUP_DIR="./backups/$(date +%Y%m%d_%H%M%S)"
    mkdir -p "$BACKUP_DIR"
    
    log "━━━ Backing up databases to ${BACKUP_DIR} ━━━"
    
    # PostgreSQL
    log "Backing up PostgreSQL..."
    docker compose -f $COMPOSE_FILE -p $PROJECT_NAME exec -T postgres \
        pg_dump -U "${DB_USERNAME:-ondeedu}" "${DB_NAME:-ondeedu_crm}" \
        | gzip > "${BACKUP_DIR}/postgres.sql.gz"
    
    # MongoDB
    log "Backing up MongoDB..."
    docker compose -f $COMPOSE_FILE -p $PROJECT_NAME exec -T mongodb \
        mongodump --archive --gzip \
        -u "${MONGO_USERNAME:-ondeedu}" -p "${MONGO_PASSWORD:-}" --authenticationDatabase admin \
        > "${BACKUP_DIR}/mongodb.gz"
    
    # Redis
    log "Saving Redis snapshot..."
    docker compose -f $COMPOSE_FILE -p $PROJECT_NAME exec redis \
        redis-cli -a "${REDIS_PASSWORD:-}" BGSAVE >/dev/null 2>&1 || true
    
    BACKUP_SIZE=$(du -sh "$BACKUP_DIR" | cut -f1)
    log "Backup complete! Size: ${BACKUP_SIZE} → ${BACKUP_DIR}"
}

# ─── Logs ────────────────────────────────────────────────────
show_logs() {
    local service=${1:-}
    if [ -z "$service" ]; then
        docker compose -f $COMPOSE_FILE -p $PROJECT_NAME logs -f --tail=100
    else
        docker compose -f $COMPOSE_FILE -p $PROJECT_NAME logs -f --tail=100 "$service"
    fi
}

# ─── Restart service ────────────────────────────────────────
restart_service() {
    local service=${1:-}
    if [ -z "$service" ]; then
        error "Usage: ./deploy.sh restart <service-name>"
    fi
    log "Restarting ${service}..."
    docker compose -f $COMPOSE_FILE -p $PROJECT_NAME up -d --build --no-deps "$service"
    log "${service} restarted"
}

# ─── Stop everything ────────────────────────────────────────
stop_all() {
    warn "Stopping all containers..."
    docker compose -f $COMPOSE_FILE -p $PROJECT_NAME down
    log "All stopped"
}

# ─── Main ────────────────────────────────────────────────────
main() {
    cd "$(dirname "$0")"
    check_prereqs
    
    case "${1:-full}" in
        full)
            check_resources
            deploy_infra
            deploy_services
            echo ""
            show_status
            echo ""
            log "━━━ 1edu CRM is running! ━━━"
            log "API Gateway:  http://localhost:8090"
            log "Keycloak:     http://localhost:8080"
            log "RabbitMQ UI:  http://localhost:15672"
            log "Grafana:      http://localhost:3000"
            log "MinIO:        http://localhost:9001"
            ;;
        infra)
            deploy_infra
            ;;
        services)
            deploy_services
            ;;
        ssl)
            setup_ssl
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
            source .env 2>/dev/null || true
            backup
            ;;
        stop)
            stop_all
            ;;
        *)
            echo "Usage: $0 {full|infra|services|ssl|status|logs|restart|backup|stop}"
            echo ""
            echo "  full      - Deploy everything (default)"
            echo "  infra     - Start only infrastructure"
            echo "  services  - Build and start only app services"
            echo "  ssl       - Setup Let's Encrypt SSL"
            echo "  status    - Show container status and resource usage"
            echo "  logs [s]  - Tail logs (optionally for specific service)"
            echo "  restart s - Restart specific service"
            echo "  backup    - Backup PostgreSQL + MongoDB"
            echo "  stop      - Stop everything"
            ;;
    esac
}

main "$@"
