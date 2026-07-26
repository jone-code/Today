#!/bin/bash
set -euo pipefail

# IMPORTANT: do not assign MYSQL wait vars to PORT — Spring uses PORT for server.port.
MYSQL_HOST="${MYSQL_HOST:-mysql}"
MYSQL_TCP_PORT="${MYSQL_TCP_PORT:-3306}"
MYSQL_WAIT_TRIES="${MYSQL_WAIT_TRIES:-60}"
APP_PORT="${PORT:-3001}"
APP_ADDRESS="${SERVER_ADDRESS:-0.0.0.0}"

echo "waiting for MySQL ${MYSQL_HOST}:${MYSQL_TCP_PORT} ..."
for i in $(seq 1 "$MYSQL_WAIT_TRIES"); do
  if bash -c "exec 3<>/dev/tcp/${MYSQL_HOST}/${MYSQL_TCP_PORT}" 2>/dev/null; then
    echo "MySQL port is open"
    # brief settle — ping can succeed before auth/SQL is ready
    sleep 3
    echo "starting today-api on ${APP_ADDRESS}:${APP_PORT}"
    # shellcheck disable=SC2086
    exec java ${JAVA_OPTS:-} \
      -Dserver.port="${APP_PORT}" \
      -Dserver.address="${APP_ADDRESS}" \
      -jar /app/app.jar
  fi
  sleep 2
done

echo "ERROR: MySQL ${MYSQL_HOST}:${MYSQL_TCP_PORT} not reachable after ${MYSQL_WAIT_TRIES} attempts" >&2
exit 1
