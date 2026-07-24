#!/usr/bin/env bash
# Apply incremental migrations for existing Today MySQL databases.
# Fresh installs can just load schema.sql (already includes these objects).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DB_DIR="$ROOT/apps/api/src/main/resources/db"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-today}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-today}"
MYSQL_DATABASE="${MYSQL_DATABASE:-today}"

MYSQL=(mysql -h"$MYSQL_HOST" -P"$MYSQL_PORT" -u"$MYSQL_USER" "-p$MYSQL_PASSWORD" "$MYSQL_DATABASE")

echo "Migrating $MYSQL_DATABASE @ $MYSQL_HOST:$MYSQL_PORT as $MYSQL_USER"
for file in \
  migration-auth-reminder.sql \
  migration-memory-embedding.sql \
  migration-todo-punch.sql \
  migration-memory-manage.sql \
  migration-proactive-events.sql
do
  path="$DB_DIR/$file"
  echo "→ $file"
  "${MYSQL[@]}" < "$path"
done

echo "Done. Tables:"
"${MYSQL[@]}" -e "SHOW TABLES;"
