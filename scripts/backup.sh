#!/bin/bash
# =============================================================================
# 1edu CRM — Automated Backup Script
# Backs up PostgreSQL (all databases) + MongoDB
# Stores compressed archives in /backups/, keeps last 7 days
#
# Usage:
#   ./scripts/backup.sh                  # Interactive run
#   ./scripts/backup.sh --notify-slack   # + post result to Slack (if SLACK_WEBHOOK set)
#
# Cron: runs daily at 03:00 via scripts/cron-setup.sh
# =============================================================================

set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

BACKUP_DIR="${BACKUP_DIR:-/backups/1edu_crm}"
RETAIN_DAYS=7
TIMESTAMP=$(date '+%Y-%m-%d_%H-%M-%S')
DATE_TAG=$(date '+%Y-%m-%d')
LOG_FILE="$BACKUP_DIR/backup.log"

# ─── Colors (only in terminal) ───────────────────────────────────────────────
if [ -t 1 ]; then
    RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'
else
    RED=''; GREEN=''; YELLOW=''; CYAN=''; NC=''
fi

log()  { echo -e "${CYAN}[$(date '+%H:%M:%S')]${NC} $*" | tee -a "$LOG_FILE"; }
ok()   { echo -e "${GREEN}[$(date '+%H:%M:%S')] ✓${NC} $*" | tee -a "$LOG_FILE"; }
fail() { echo -e "${RED}[$(date '+%H:%M:%S')] ✗${NC} $*" | tee -a "$LOG_FILE"; exit 1; }

mkdir -p "$BACKUP_DIR"

echo "" >> "$LOG_FILE"
echo "════════════════════════════════════════" >> "$LOG_FILE"
log "Backup started: $TIMESTAMP"
echo "════════════════════════════════════════" >> "$LOG_FILE"

# ─── Load .env if available ───────────────────────────────────────────────────
if [[ -f "$ROOT/.env" ]]; then
    set -a
    # shellcheck disable=SC1091
    source "$ROOT/.env"
    set +a
fi

# Defaults (override in .env)
POSTGRES_HOST="${POSTGRES_HOST:-localhost}"
POSTGRES_PORT="${POSTGRES_PORT:-5432}"
POSTGRES_USER="${POSTGRES_USER:-postgres}"
POSTGRES_PASSWORD="${POSTGRES_PASSWORD:-postgres}"
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-1edu-postgres}"

MONGO_CONTAINER="${MONGO_CONTAINER:-1edu-mongodb}"
MONGO_USER="${MONGO_USER:-}"
MONGO_PASSWORD="${MONGO_PASSWORD:-}"

ERRORS=0

# ─── PostgreSQL Backup ────────────────────────────────────────────────────────
PG_BACKUP="$BACKUP_DIR/postgres_${DATE_TAG}.sql.gz"
PG_BACKUP_LATEST="$BACKUP_DIR/postgres_latest.sql.gz"

log "Backing up PostgreSQL..."
if docker ps --format '{{.Names}}' | grep -q "^${POSTGRES_CONTAINER}$"; then
    if docker exec "$POSTGRES_CONTAINER" \
        env PGPASSWORD="$POSTGRES_PASSWORD" \
        pg_dumpall -U "$POSTGRES_USER" \
        | gzip > "$PG_BACKUP" 2>>"$LOG_FILE"; then

        # Create latest symlink
        ln -sf "$PG_BACKUP" "$PG_BACKUP_LATEST"
        SIZE=$(du -sh "$PG_BACKUP" | cut -f1)
        ok "PostgreSQL backup: $PG_BACKUP ($SIZE)"
    else
        echo -e "${RED}[ERROR] PostgreSQL backup failed${NC}" | tee -a "$LOG_FILE"
        (( ERRORS++ )) || true
    fi
else
    echo -e "${YELLOW}[WARN] Container $POSTGRES_CONTAINER not running — skipping PostgreSQL backup${NC}" | tee -a "$LOG_FILE"
fi

# ─── MongoDB Backup ───────────────────────────────────────────────────────────
MONGO_BACKUP_DIR="$BACKUP_DIR/mongodb_${DATE_TAG}"
MONGO_BACKUP_ARCHIVE="$BACKUP_DIR/mongodb_${DATE_TAG}.tar.gz"
MONGO_BACKUP_LATEST="$BACKUP_DIR/mongodb_latest.tar.gz"

log "Backing up MongoDB..."
if docker ps --format '{{.Names}}' | grep -q "^${MONGO_CONTAINER}$"; then
    # Run mongodump inside container
    MONGO_URI="mongodb://localhost:27017"
    if [[ -n "${MONGO_USER:-}" ]]; then
        MONGO_URI="mongodb://${MONGO_USER}:${MONGO_PASSWORD}@localhost:27017"
    fi

    if docker exec "$MONGO_CONTAINER" \
        mongodump --uri="$MONGO_URI" --out=/tmp/mongodump_backup --quiet 2>>"$LOG_FILE" \
        && docker cp "${MONGO_CONTAINER}:/tmp/mongodump_backup" "$MONGO_BACKUP_DIR" 2>>"$LOG_FILE" \
        && docker exec "$MONGO_CONTAINER" rm -rf /tmp/mongodump_backup 2>>"$LOG_FILE"; then

        # Compress
        tar -czf "$MONGO_BACKUP_ARCHIVE" -C "$(dirname "$MONGO_BACKUP_DIR")" "$(basename "$MONGO_BACKUP_DIR")"
        rm -rf "$MONGO_BACKUP_DIR"
        ln -sf "$MONGO_BACKUP_ARCHIVE" "$MONGO_BACKUP_LATEST"
        SIZE=$(du -sh "$MONGO_BACKUP_ARCHIVE" | cut -f1)
        ok "MongoDB backup: $MONGO_BACKUP_ARCHIVE ($SIZE)"
    else
        echo -e "${RED}[ERROR] MongoDB backup failed${NC}" | tee -a "$LOG_FILE"
        (( ERRORS++ )) || true
    fi
else
    echo -e "${YELLOW}[WARN] Container $MONGO_CONTAINER not running — skipping MongoDB backup${NC}" | tee -a "$LOG_FILE"
fi

# ─── Cleanup old backups ──────────────────────────────────────────────────────
log "Cleaning up backups older than $RETAIN_DAYS days..."
find "$BACKUP_DIR" -name "postgres_*.sql.gz" -mtime "+$RETAIN_DAYS" -delete 2>>"$LOG_FILE" || true
find "$BACKUP_DIR" -name "mongodb_*.tar.gz"  -mtime "+$RETAIN_DAYS" -delete 2>>"$LOG_FILE" || true
ok "Old backups cleaned."

# ─── Disk usage report ────────────────────────────────────────────────────────
TOTAL_SIZE=$(du -sh "$BACKUP_DIR" 2>/dev/null | cut -f1 || echo "?")
log "Total backup size: $TOTAL_SIZE"

# ─── Slack notification (optional) ───────────────────────────────────────────
if [[ -n "${SLACK_WEBHOOK:-}" ]]; then
    if (( ERRORS == 0 )); then
        MSG="✅ *1edu CRM backup succeeded* ($DATE_TAG) — Total: $TOTAL_SIZE"
    else
        MSG="❌ *1edu CRM backup FAILED* ($DATE_TAG) — $ERRORS error(s). Check /backups/1edu_crm/backup.log"
    fi
    curl -s -X POST "$SLACK_WEBHOOK" \
        -H 'Content-type: application/json' \
        --data "{\"text\":\"$MSG\"}" > /dev/null 2>&1 || true
fi

# ─── Result ───────────────────────────────────────────────────────────────────
if (( ERRORS > 0 )); then
    fail "Backup completed with $ERRORS error(s)."
else
    ok "Backup complete. Next run: tomorrow at 03:00."
fi
