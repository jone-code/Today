#!/usr/bin/env bash
# Initialize Today MySQL schema (create DB + all tables) and verify required tables.
# Idempotent: schema/migrations use CREATE TABLE IF NOT EXISTS.
#
# Usage:
#   npm run db:init:docker                 # recommended with Compose
#   bash scripts/db-init.sh --docker
#   bash scripts/db-init.sh --local        # host mysql client → MYSQL_HOST
#   MYSQL_USER=root MYSQL_PASSWORD=root npm run db:init
#
# Defaults match docker-compose / .env.example (root/root).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
DB_DIR="$ROOT/apps/api/src/main/resources/db"
SCHEMA="$DB_DIR/schema.sql"

MYSQL_HOST="${MYSQL_HOST:-127.0.0.1}"
MYSQL_PORT="${MYSQL_PORT:-3306}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASSWORD="${MYSQL_PASSWORD:-root}"
MYSQL_DATABASE="${MYSQL_DATABASE:-today}"

USE_DOCKER=""
for arg in "$@"; do
  case "$arg" in
    --docker|-d) USE_DOCKER=1 ;;
    --local|-l) USE_DOCKER=0 ;;
    -h|--help)
      sed -n '2,14p' "$0" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
  esac
done

# Prefer the Compose MySQL container when present (avoids initializing a different local MySQL on :3306).
if [[ -z "$USE_DOCKER" ]]; then
  if docker ps --format '{{.Names}}' 2>/dev/null | grep -qx 'today-mysql'; then
    USE_DOCKER=1
    echo "Auto: today-mysql is running → using --docker"
  else
    USE_DOCKER=0
  fi
fi

REQUIRED_TABLES=(
  users
  checkins
  day_summaries
  memories
  reminders
  reminder_deliveries
  todos
  punch_habits
  punch_logs
  proactive_prompt_events
  checkin_ai_jobs
  ai_call_logs
  agent_profiles
  agent_media
  conversations
  conversation_messages
  agent_proposals
  agent_action_logs
  memory_sources
  vector_sync_jobs
  checkin_memory_candidates
  followup_topics
  one_off_appointments
  followup_preferences
  inbox_items
  api_rate_limits
  create_requests
  punch_photo_cleanup_jobs
  feedback_reports
)

REQUIRED_COLUMNS=(
  conversations:deleted_at
  conversations:client_request_id
  conversation_messages:deleted_at
  conversation_messages:generation_attempt
  conversation_messages:generation_started_at
  conversation_messages:client_message_id
  agent_proposals:source_message_id
  agent_proposals:status
  agent_action_logs:undo_snapshot_json
  agent_action_logs:undone_at
  memory_sources:active
  vector_sync_jobs:version
  vector_sync_jobs:lease_until
  checkin_memory_candidates:source_hash
  checkin_memory_candidates:status
  followup_topics:source_conversation_id
  one_off_appointments:source_conversation_id
  inbox_items:source_id
  punch_photo_cleanup_jobs:lease_until
  agent_media:next_cleanup_at
  create_requests:response_json
  create_requests:request_hash
  feedback_reports:deleted_at
)

REQUIRED_UNIQUE_INDEXES=(
  conversations:uk_conversation_client_request
  conversation_messages:uk_conversation_client_message
  conversation_messages:uk_conversation_reply
  agent_proposals:uk_agent_proposal_source_kind
  checkin_memory_candidates:uk_checkin_candidate_version
  one_off_appointments:uk_one_off_proposal
  inbox_items:uk_inbox_source_schedule
  api_rate_limits:PRIMARY
  create_requests:uk_create_request
)

REQUIRED_INDEXES=(
  punch_logs:idx_punch_logs_user_photo
  agent_media:idx_agent_media_cleanup_due
  feedback_reports:idx_feedback_owner
)

MIGRATIONS=(
  migration-auth-reminder.sql
  migration-memory-embedding.sql
  migration-todo-punch.sql
  migration-memory-manage.sql
  migration-proactive-events.sql
  migration-checkin-ai-jobs.sql
  migration-ai-call-logs.sql
  migration-punch-photo.sql
  migration-agent-conversation.sql
  migration-conversation-create-idempotency.sql
  migration-rate-limits.sql
  migration-create-idempotency.sql
  migration-punch-photo-cleanup.sql
  migration-agent-media-cleanup.sql
  migration-feedback-reports.sql
  migration-agent-action-undo.sql
)

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
  echo "NOTE: API in Compose uses container MySQL. Prefer --docker if you run stack:up."
fi

echo "→ schema.sql"
run_mysql < "$SCHEMA"

for file in "${MIGRATIONS[@]}"; do
  path="$DB_DIR/$file"
  echo "→ $file"
  run_mysql "$MYSQL_DATABASE" < "$path"
done

echo
echo "Connection:"
run_mysql -N -e "SELECT CONCAT('host=', @@hostname, ' port=', @@port, ' db=', DATABASE());" "$MYSQL_DATABASE" \
  || run_mysql -N -e "SELECT CONCAT('host=', @@hostname, ' port=', @@port); USE \`$MYSQL_DATABASE\`; SELECT CONCAT('db=', DATABASE());"

echo
echo "Tables in $MYSQL_DATABASE:"
run_mysql -e "USE \`$MYSQL_DATABASE\`; SHOW TABLES;"

echo
echo "Verifying required tables..."
missing=0
for t in "${REQUIRED_TABLES[@]}"; do
  exists="$(run_mysql -N -e "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='${MYSQL_DATABASE}' AND table_name='${t}';")"
  if [[ "$exists" == "1" ]]; then
    echo "  OK  $t"
  else
    echo "  MISSING  $t" >&2
    missing=1
  fi
done

for entry in "${REQUIRED_COLUMNS[@]}"; do
  table="${entry%%:*}"
  column="${entry#*:}"
  exists="$(run_mysql -N -e "SELECT COUNT(*) FROM information_schema.columns WHERE table_schema='${MYSQL_DATABASE}' AND table_name='${table}' AND column_name='${column}';")"
  if [[ "$exists" == "1" ]]; then
    echo "  OK  $entry"
  else
    echo "  MISSING  $entry" >&2
    missing=1
  fi
done

for entry in "${REQUIRED_UNIQUE_INDEXES[@]}"; do
  table="${entry%%:*}"
  index="${entry#*:}"
  exists="$(run_mysql -N -e "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema='${MYSQL_DATABASE}' AND table_name='${table}' AND index_name='${index}' AND non_unique=0;")"
  if [[ "$exists" == "1" ]]; then
    echo "  OK  $entry"
  else
    echo "  MISSING  $entry" >&2
    missing=1
  fi
done

for entry in "${REQUIRED_INDEXES[@]}"; do
  table="${entry%%:*}"
  index="${entry#*:}"
  exists="$(run_mysql -N -e "SELECT COUNT(DISTINCT index_name) FROM information_schema.statistics WHERE table_schema='${MYSQL_DATABASE}' AND table_name='${table}' AND index_name='${index}';")"
  if [[ "$exists" == "1" ]]; then
    echo "  OK  $entry"
  else
    echo "  MISSING  $entry" >&2
    missing=1
  fi
done

if [[ "$missing" -ne 0 ]]; then
  echo >&2
  echo "ERROR: schema incomplete. API will refuse to start; repair the missing migration requirements." >&2
  echo "Retry: npm run db:init:docker" >&2
  exit 1
fi

echo
echo "Done. Required tables, columns and indexes present."
echo "Restart API if it was already running: docker compose restart api"
