#!/bin/bash
# =============================================================================
# 1edu CRM — Cron Setup Script
# Installs automated cron jobs on the server:
#   - Daily backup at 03:00
#   - Weekly Docker image/container cleanup on Sunday at 04:00
#
# Run once on the server after first deploy:
#   ./scripts/cron-setup.sh
# =============================================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKUP_SCRIPT="$ROOT/scripts/backup.sh"
CRON_LOG="/var/log/1edu_cron.log"

RED='\033[0;31m'; GREEN='\033[0;32m'; CYAN='\033[0;36m'; NC='\033[0m'
log()  { echo -e "${CYAN}[setup]${NC} $*"; }
ok()   { echo -e "${GREEN}[setup] ✓${NC} $*"; }
fail() { echo -e "${RED}[setup] ✗${NC} $*"; exit 1; }

# ─── Sanity checks ────────────────────────────────────────────────────────────
[[ -f "$BACKUP_SCRIPT" ]] || fail "backup.sh not found at $BACKUP_SCRIPT"

# Make scripts executable
chmod +x "$ROOT/scripts/"*.sh
ok "Scripts marked executable."

# Create backup directory
mkdir -p /backups/1edu_crm
ok "Backup directory: /backups/1edu_crm"

# Create log file
touch "$CRON_LOG"
ok "Cron log: $CRON_LOG"

# ─── Build cron job definitions ───────────────────────────────────────────────
CRON_MARKER="# 1edu CRM automated jobs"
NEW_CRONS="$CRON_MARKER
# Daily backup at 03:00
0 3 * * * $BACKUP_SCRIPT >> $CRON_LOG 2>&1
# Weekly Docker cleanup (unused images/containers) — Sunday 04:00
0 4 * * 0 docker system prune -f --filter 'until=168h' >> $CRON_LOG 2>&1
# Weekly log rotation — Saturday 02:00
0 2 * * 6 find /backups/1edu_crm -name '*.log' -size +50M -exec truncate -s 0 {} \\; >> $CRON_LOG 2>&1"

# ─── Install cron (non-destructive) ──────────────────────────────────────────
log "Installing cron jobs..."

# Read current crontab, remove any previous 1edu block, append new block
CURRENT_CRON=$(crontab -l 2>/dev/null || echo "")

# Remove old 1edu block if present
CLEANED_CRON=$(echo "$CURRENT_CRON" | \
    awk "/$CRON_MARKER/{found=1} !found{print} /^$/{if(found)found=0}" || echo "$CURRENT_CRON")

# Add new block
NEW_CRONTAB="$CLEANED_CRON

$NEW_CRONS"

echo "$NEW_CRONTAB" | crontab -

ok "Cron jobs installed."
echo ""
log "Active cron jobs:"
crontab -l | grep -A 10 "$CRON_MARKER" | sed 's/^/  /'
echo ""

# ─── Test backup immediately (optional) ──────────────────────────────────────
echo ""
read -p "$(echo -e ${CYAN}[setup]${NC}) Run a test backup now? [y/N] " -n 1 -r RUN_TEST
echo ""
if [[ "$RUN_TEST" =~ ^[Yy]$ ]]; then
    log "Running test backup..."
    "$BACKUP_SCRIPT"
else
    log "Skipping test backup."
    log "To run manually: ./scripts/backup.sh"
fi

echo ""
ok "Cron setup complete."
echo ""
echo "  Backup location : /backups/1edu_crm/"
echo "  Cron log        : $CRON_LOG"
echo "  View schedule   : crontab -l"
echo "  Run backup now  : $BACKUP_SCRIPT"
