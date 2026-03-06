#!/bin/bash
# Start all 1edu CRM microservices in background
# Logs are saved to ./logs/<service-name>.log
# PIDs are saved to ./logs/services.pid

set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
LOGS="$ROOT/logs"
PID_FILE="$LOGS/services.pid"

mkdir -p "$LOGS"

# Clear old PID file
> "$PID_FILE"

start_service() {
    local name=$1
    local module=$2
    local log="$LOGS/$name.log"

    echo "  Starting $name..."
    cd "$ROOT"
    ./gradlew ":services:$module:bootRun" > "$log" 2>&1 &
    local pid=$!
    echo "$pid $name" >> "$PID_FILE"
    echo "    PID=$pid  log: logs/$name.log"
}

echo "======================================"
echo " 1edu CRM — Starting all services"
echo "======================================"
echo ""

echo "[1/3] Starting service-registry (Eureka)..."
start_service "service-registry" "service-registry"
echo "      Waiting 15s for Eureka to be ready..."
sleep 15

echo ""
echo "[2/3] Starting api-gateway..."
start_service "api-gateway" "api-gateway"
echo "      Waiting 5s..."
sleep 5

echo ""
echo "[3/3] Starting business services..."
start_service "tenant-service"       "tenant-service"
start_service "auth-service"         "auth-service"
start_service "student-service"      "student-service"
start_service "lead-service"         "lead-service"
start_service "course-service"       "course-service"
start_service "schedule-service"     "schedule-service"
start_service "payment-service"      "payment-service"
start_service "finance-service"      "finance-service"
start_service "analytics-service"    "analytics-service"
start_service "notification-service" "notification-service"
start_service "file-service"         "file-service"
start_service "staff-service"        "staff-service"
start_service "task-service"         "task-service"
start_service "lesson-service"       "lesson-service"
start_service "settings-service"     "settings-service"
start_service "report-service"       "report-service"
start_service "audit-service"        "audit-service"

echo ""
echo "======================================"
echo " All services started!"
echo " Eureka dashboard: http://localhost:8761"
echo " API Gateway:      http://localhost:8090"
echo ""
echo " To stop all:  make services-down"
echo " To view logs: tail -f logs/<service>.log"
echo "======================================"
