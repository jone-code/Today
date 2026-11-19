#!/usr/bin/env bash
# Create a compressed, transaction-consistent backup before migrations or release.
# The output contains user data; keep it outside version control and protect it.
set -euo pipefail
umask 077

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root}"
MYSQL_DATABASE="${MYSQL_DATABASE:-today}"
BACKUP_DIR="${TODAY_BACKUP_DIR:-$ROOT/backups}"
BACKUP_CONTAINER="${TODAY_BACKUP_CONTAINER:-today-mysql}"
STAMP="$(date -u +%Y%m%dT%H%M%SZ)"

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  sed -n '2,3p' "$0" | sed 's/^# \{0,1\}//'
  echo "Usage: $0 [output.sql.gz]"
  exit 0
fi

OUTPUT="${1:-$BACKUP_DIR/today-${STAMP}.sql.gz}"
if [[ -e "$OUTPUT" || -L "$OUTPUT" ]]; then
  echo "ERROR: backup already exists: $OUTPUT" >&2
  exit 1
fi
mkdir -p "$(dirname "$OUTPUT")"
TEMP_OUTPUT="$(mktemp "${OUTPUT}.tmp.XXXXXX")"
trap 'rm -f "$TEMP_OUTPUT"' EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

if docker ps --format '{{.Names}}' 2>/dev/null | grep -Fxq "$BACKUP_CONTAINER"; then
  echo "Backing up Compose database $MYSQL_DATABASE → $OUTPUT"
  docker exec "$BACKUP_CONTAINER" mysqldump -h127.0.0.1 -P3306 \
    -u"$MYSQL_USER" "-p$MYSQL_PASSWORD" \
    --single-transaction --routines --events --triggers --hex-blob "$MYSQL_DATABASE" \
    | gzip -c > "$TEMP_OUTPUT"
else
  if [[ -n "${TODAY_BACKUP_CONTAINER:-}" ]]; then
    echo "ERROR: requested backup container is not running: $BACKUP_CONTAINER" >&2
    exit 1
  fi
  if ! command -v mysqldump >/dev/null 2>&1; then
    echo "ERROR: mysqldump not found and $BACKUP_CONTAINER is not running" >&2
    exit 1
  fi
  echo "Backing up local database $MYSQL_DATABASE → $OUTPUT"
  mysqldump -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" "-p$MYSQL_PASSWORD" \
    --single-transaction --routines --events --triggers --hex-blob "$MYSQL_DATABASE" \
    | gzip -c > "$TEMP_OUTPUT"
fi

if [[ ! -s "$TEMP_OUTPUT" ]]; then
  echo "ERROR: backup output is empty: $TEMP_OUTPUT" >&2
  rm -f "$TEMP_OUTPUT"
  exit 1
fi
chmod 600 "$TEMP_OUTPUT"
# Publish without overwriting a destination created while the dump was running.
# The temporary file is in the same directory, so this link is atomic.
if ! ln "$TEMP_OUTPUT" "$OUTPUT"; then
  echo "ERROR: backup destination already exists: $OUTPUT" >&2
  exit 1
fi
echo "Backup complete: $OUTPUT"
