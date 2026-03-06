#!/bin/bash
# Stop all 1edu CRM microservices

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PID_FILE="$ROOT/logs/services.pid"

if [ ! -f "$PID_FILE" ]; then
    echo "No services.pid found. Killing all bootRun processes..."
    pkill -f "bootRun" 2>/dev/null || true
    echo "Done."
    exit 0
fi

echo "Stopping all services..."
while IFS=" " read -r pid name; do
    if kill -0 "$pid" 2>/dev/null; then
        echo "  Stopping $name (PID=$pid)..."
        kill "$pid" 2>/dev/null || true
    fi
done < "$PID_FILE"

rm -f "$PID_FILE"
echo "All services stopped."
