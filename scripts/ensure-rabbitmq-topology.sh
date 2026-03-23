#!/bin/bash

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

if [[ ! -f .env ]]; then
    echo "[rabbitmq-topology] .env is missing" >&2
    exit 1
fi

set -a
# shellcheck disable=SC1091
source ./.env
set +a

require_var() {
    local name="$1"
    [[ -n "${!name:-}" ]] || {
        echo "[rabbitmq-topology] missing required variable: $name" >&2
        exit 1
    }
}

require_var RABBITMQ_USERNAME
require_var RABBITMQ_PASSWORD
require_var RABBITMQ_VHOST

require_cmd() {
    command -v "$1" >/dev/null 2>&1 || {
        echo "[rabbitmq-topology] missing required command: $1" >&2
        exit 1
    }
}

require_cmd curl
require_cmd python3

RABBITMQ_API_HOST="${RABBITMQ_API_HOST:-127.0.0.1}"
RABBITMQ_API_PORT="${RABBITMQ_API_PORT:-15672}"
RABBITMQ_API_BASE="http://${RABBITMQ_API_HOST}:${RABBITMQ_API_PORT}/api"
AUTH="${RABBITMQ_USERNAME}:${RABBITMQ_PASSWORD}"

urlencode() {
    python3 -c 'import sys, urllib.parse; print(urllib.parse.quote(sys.argv[1], safe=""))' "$1"
}

api_request() {
    local method="$1"
    local path="$2"
    local payload="${3:-}"

    if [[ -n "$payload" ]]; then
        curl -fsS --retry 12 --retry-delay 2 \
            -u "$AUTH" \
            -H "content-type: application/json" \
            -X "$method" \
            "${RABBITMQ_API_BASE}${path}" \
            -d "$payload" >/dev/null
    else
        curl -fsS --retry 12 --retry-delay 2 \
            -u "$AUTH" \
            -H "content-type: application/json" \
            -X "$method" \
            "${RABBITMQ_API_BASE}${path}" >/dev/null
    fi
}

ensure_exchange() {
    local name="$1"
    local type="$2"
    local vhost
    local exchange

    vhost="$(urlencode "$RABBITMQ_VHOST")"
    exchange="$(urlencode "$name")"
    api_request PUT "/exchanges/${vhost}/${exchange}" "{\"type\":\"${type}\",\"durable\":true,\"auto_delete\":false,\"internal\":false,\"arguments\":{}}"
    echo "[rabbitmq-topology] exchange ready: ${name}"
}

ensure_queue() {
    local name="$1"
    local vhost
    local queue

    vhost="$(urlencode "$RABBITMQ_VHOST")"
    queue="$(urlencode "$name")"
    api_request PUT "/queues/${vhost}/${queue}" '{"durable":true,"auto_delete":false,"arguments":{}}'
    echo "[rabbitmq-topology] queue ready: ${name}"
}

ensure_binding() {
    local exchange_name="$1"
    local queue_name="$2"
    local routing_key="$3"
    local vhost
    local exchange
    local queue

    vhost="$(urlencode "$RABBITMQ_VHOST")"
    exchange="$(urlencode "$exchange_name")"
    queue="$(urlencode "$queue_name")"
    api_request POST "/bindings/${vhost}/e/${exchange}/q/${queue}" "{\"routing_key\":\"${routing_key}\",\"arguments\":{}}"
    echo "[rabbitmq-topology] binding ready: ${exchange_name} -> ${queue_name} (${routing_key})"
}

main() {
    ensure_exchange "student.exchange" "topic"
    ensure_exchange "payment.exchange" "topic"
    ensure_exchange "notification.exchange" "topic"
    ensure_exchange "lead.exchange" "topic"
    ensure_exchange "audit.exchange" "topic"

    ensure_queue "student.created.queue"
    ensure_binding "student.exchange" "student.created.queue" "student.created"

    ensure_queue "payment.completed.queue"
    ensure_binding "payment.exchange" "payment.completed.queue" "payment.completed"

    ensure_queue "notification.email.queue"
    ensure_binding "notification.exchange" "notification.email.queue" "notification.email"

    ensure_queue "notification.sms.queue"
    ensure_binding "notification.exchange" "notification.sms.queue" "notification.sms"

    ensure_queue "notification.assignment.queue"
    ensure_binding "notification.exchange" "notification.assignment.queue" "notification.assignment"

    ensure_queue "lead.created.queue"
    ensure_binding "lead.exchange" "lead.created.queue" "lead.created"

    ensure_queue "audit.system.queue"
    ensure_binding "audit.exchange" "audit.system.queue" "audit.system"

    ensure_queue "audit.tenant.queue"
    ensure_binding "audit.exchange" "audit.tenant.queue" "audit.tenant"
}

main "$@"
