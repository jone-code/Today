#!/usr/bin/env bash
# Reindex memories into VectorStore (MySQL embedding_json → Qdrant/mysql index)
set -euo pipefail

API_BASE_URL="${API_BASE_URL:-http://127.0.0.1:3001}"
TOKEN="${TODAY_AUTH_TOKEN:-}"
ADMIN_TOKEN="${TODAY_VECTOR_ADMIN_TOKEN:-}"
SCOPE="${SCOPE:-user}" # user | all
FILL="${FILL_MISSING:-false}"
RECREATE="${RECREATE:-false}"

if [[ -z "$TOKEN" ]]; then
  echo "Set TODAY_AUTH_TOKEN to a Bearer JWT (login/register first)." >&2
  exit 1
fi

if [[ "$SCOPE" == "all" ]]; then
  url="$API_BASE_URL/v1/admin/vector/reindex?fillMissingEmbeddings=$FILL&recreate=$RECREATE"
  headers=(-H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json")
  if [[ -n "$ADMIN_TOKEN" ]]; then
    headers+=(-H "X-Today-Admin-Token: $ADMIN_TOKEN")
  fi
else
  url="$API_BASE_URL/v1/memories/reindex?fillMissingEmbeddings=$FILL&recreate=$RECREATE"
  headers=(-H "Authorization: Bearer $TOKEN" -H "Content-Type: application/json")
fi

echo "→ POST $url"
curl -fsS -X POST "${headers[@]}" "$url"
echo
echo "✓ reindex requested"
