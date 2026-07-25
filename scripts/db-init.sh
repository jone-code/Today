#!/usr/bin/env bash
# Initialize Today MySQL schema (create DB + all tables).
# Idempotent: schema.sql uses CREATE TABLE IF NOT EXISTS.
#
# Usage:
#   npm run db:init
#   bash scripts/db-init.sh
#   bash scripts/db-init.sh --docker          # via today-mysql container (no local mysql client)
#   MYSQL_USER=root MYSQL_PASSWORD=root npm run db:init
#
# Defaults match docker-compose / .env.example.
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SCHEMA="$ROOT/apps/api/src/main/resources/db/schema.sql"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
# Prefer root for CREATE DATABASE; override with MYSQL_USER=today if you already have the DB.
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root}"
MYSQL_DATABASE="${MYSQL_DATABASE:-today}"

USE_DOCKER=0
for arg in "$@"; do
  case "$arg" in
    --docker|-d) USE_DOCKER=1 ;;
    -h|--help)
      sed -n '2,12p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
  esac
done

if [[ ! -f "$SCHEMA" ]]; then
  echo "ERROR: schema not found: $SCHEMA" >&2
  exit 1
fi

run_mysql() {
  if [[ "$USE_DOCKER" -eq 1 ]]; then
    if ! docker ps --format '{{.Names}}' | grep -qx 'today-mysql'; then
      echo "ERROR: container today-mysql is not running. Start it with: npm run db:up" >&2
      exit 1
    fi
    docker exec -i today-mysql \
      mysql -h127.0.0.1 -P3306 -u"$MYSQL_USER" "-p$MYSQL_PASSWORD" "$@"
  else
    if ! command -v mysql >/dev/null 2>&1; then
      echo "ERROR: mysql client not found. Install it, or run: bash scripts/db-init.sh --docker" >&2
      exit 1
    fi
    mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" "-p$MYSQL_PASSWORD" "$@"
  fi
}

echo "Initializing schema → $MYSQL_DATABASE"
if [[ "$USE_DOCKER" -eq 1 ]]; then
  echo "Mode: docker exec today-mysql (user=$MYSQL_USER)"
else
  echo "Mode: local mysql client → $MYSQL_HOST:$MYSQL_PORT (user=$MYSQL_USER)"
fi

run_mysql < "$SCHEMA"

echo
echo "Tables in $MYSQL_DATABASE:"
run_mysql -e "USE \`$MYSQL_DATABASE\`; SHOW TABLES;"

echo
echo "Done. Fresh full schema applied from:"
echo "  $SCHEMA"
echo
echo "If you only need incremental migrations on an older DB:"
echo "  npm run db:migrate"
