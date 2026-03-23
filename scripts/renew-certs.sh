#!/bin/bash

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

COMPOSE_FILE="docker-compose.prod.yml"
PROJECT_NAME="1edu"

log() {
    echo "[$(date '+%F %T')] $*"
}

compose() {
    docker compose -f "$COMPOSE_FILE" -p "$PROJECT_NAME" "$@"
}

[[ -f .env ]] || { log ".env is missing"; exit 1; }

set -a
# shellcheck disable=SC1091
source ./.env
set +a

log "Running certificate renewal check"
compose run --rm --entrypoint certbot certbot renew --quiet

if compose ps -q nginx | grep -q .; then
    compose exec -T nginx nginx -s reload
    log "nginx reloaded after certificate check"
fi
