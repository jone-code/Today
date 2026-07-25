#!/bin/bash
set -euo pipefail

HOST="${MYSQL_HOST:-mysql}"
PORT="${MYSQL_TCP_PORT:-3306}"
TRIES="${MYSQL_WAIT_TRIES:-60}"

echo "waiting for MySQL ${HOST}:${PORT} ..."
for i in $(seq 1 "$TRIES"); do
  if bash -c "exec 3<>/dev/tcp/${HOST}/${PORT}" 2>/dev/null; then
    echo "MySQL port is open"
    # brief settle — ping can succeed before auth/SQL is ready
    sleep 3
    exec java ${JAVA_OPTS:-} -jar /app/app.jar
  fi
  sleep 2
done

echo "ERROR: MySQL ${HOST}:${PORT} not reachable after ${TRIES} attempts" >&2
exit 1
