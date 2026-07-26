#!/usr/bin/env bash
# Orchestrate e2e smoke: API + UI (login → checkin → memory)
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

API_BASE_URL="${API_BASE_URL:-http://127.0.0.1:3001}"
WEB_BASE_URL="${WEB_BASE_URL:-http://127.0.0.1:3000}"
API_PORT="${API_PORT:-3001}"
WEB_PORT="${WEB_PORT:-3000}"
TMUX_CONF="/exec-daemon/tmux.portal.conf"
TMUX=(tmux)
if [[ -f "$TMUX_CONF" ]]; then
  TMUX=(tmux -f "$TMUX_CONF")
fi

STARTED_API=0
STARTED_WEB=0

cleanup() {
  if [[ "$STARTED_WEB" == "1" ]]; then
    "${TMUX[@]}" kill-session -t e2e-web 2>/dev/null || true
  fi
  if [[ "$STARTED_API" == "1" ]]; then
    "${TMUX[@]}" kill-session -t e2e-api 2>/dev/null || true
  fi
}
trap cleanup EXIT

wait_http() {
  local url="$1"
  local name="$2"
  local attempts="${3:-60}"
  for ((i=1; i<=attempts; i++)); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      echo "✓ $name ready ($url)"
      return 0
    fi
    sleep 1
  done
  echo "✗ $name not ready after ${attempts}s: $url" >&2
  return 1
}

ensure_api() {
  if curl -fsS "$API_BASE_URL/health" >/dev/null 2>&1; then
    echo "✓ API already up"
    return 0
  fi
  echo "→ starting API on :$API_PORT"
  "${TMUX[@]}" has-session -t "=e2e-api" 2>/dev/null || \
    "${TMUX[@]}" new-session -d -s e2e-api -c "$ROOT/apps/api" -- \
      env PORT="$API_PORT" mvn -q spring-boot:run
  STARTED_API=1
  wait_http "$API_BASE_URL/health" "API" 90
}

ensure_web() {
  if curl -fsS "$WEB_BASE_URL" >/dev/null 2>&1; then
    echo "✓ Web already up"
    return 0
  fi
  echo "→ starting Web on :$WEB_PORT"
  "${TMUX[@]}" has-session -t "=e2e-web" 2>/dev/null || \
    "${TMUX[@]}" new-session -d -s e2e-web -c "$ROOT" -- \
      env PORT="$WEB_PORT" \
        NEXT_PUBLIC_API_BASE_URL=/api \
        API_PROXY_TARGET="$API_BASE_URL" \
      npm run dev -w @today/web -- --port "$WEB_PORT" --hostname 127.0.0.1
  STARTED_WEB=1
  wait_http "$WEB_BASE_URL" "Web" 90
}

echo "== e2e smoke =="
ensure_api

echo ""
echo "== API smoke =="
API_BASE_URL="$API_BASE_URL" node "$ROOT/scripts/e2e-smoke-api.mjs"

SKIP_UI="${E2E_SKIP_UI:-0}"
if [[ "$SKIP_UI" == "1" ]]; then
  echo ""
  echo "Skipping UI smoke (E2E_SKIP_UI=1)"
  exit 0
fi

echo ""
echo "== UI smoke =="
ensure_web
export API_BASE_URL WEB_BASE_URL
npx playwright test --config="$ROOT/playwright.config.ts"

echo ""
echo "All e2e smoke checks passed."
