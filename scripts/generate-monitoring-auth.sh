#!/bin/bash

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ ! -f .env ]]; then
    echo "[monitoring-auth] .env is missing" >&2
    exit 1
fi

set -a
# shellcheck disable=SC1091
source ./.env
set +a

require_var() {
    local name="$1"
    [[ -n "${!name:-}" ]] || {
        echo "[monitoring-auth] missing required variable: $name" >&2
        exit 1
    }
}

require_cmd() {
    command -v "$1" >/dev/null 2>&1 || {
        echo "[monitoring-auth] missing required command: $1" >&2
        exit 1
    }
}

require_var MONITORING_BASIC_AUTH_USER
require_var MONITORING_BASIC_AUTH_PASSWORD
require_cmd openssl

mkdir -p "$ROOT/infrastructure/nginx/tenants"

HASH="$(openssl passwd -apr1 "$MONITORING_BASIC_AUTH_PASSWORD")"
printf '%s:%s\n' "$MONITORING_BASIC_AUTH_USER" "$HASH" > "$ROOT/infrastructure/nginx/tenants/monitoring.htpasswd"
chmod 644 "$ROOT/infrastructure/nginx/tenants/monitoring.htpasswd"

echo "[monitoring-auth] htpasswd file written to infrastructure/nginx/tenants/monitoring.htpasswd"
