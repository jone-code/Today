#!/usr/bin/env bash
# Today Docker 全栈部署脚本（MySQL + Qdrant + API + Web）
#
# Usage:
#   bash scripts/deploy-docker.sh              # 默认 up（构建并启动）
#   bash scripts/deploy-docker.sh up           # 同上
#   bash scripts/deploy-docker.sh up --no-build
#   bash scripts/deploy-docker.sh down         # 停止（保留数据卷）
#   bash scripts/deploy-docker.sh down -v      # 停止并删除数据卷
#   bash scripts/deploy-docker.sh restart
#   bash scripts/deploy-docker.sh status
#   bash scripts/deploy-docker.sh logs [svc]
#   bash scripts/deploy-docker.sh health
#   bash scripts/deploy-docker.sh migrate      # 对 today-mysql 跑 schema/迁移
#
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"

COMPOSE=(docker compose)
ENV_FILE="$ROOT/.env"
ENV_EXAMPLE="$ROOT/.env.example"

# shellcheck disable=SC1091
[[ -f "$ENV_FILE" ]] && set -a && source "$ENV_FILE" && set +a

WEB_PORT="${WEB_PORT:-3000}"
API_PORT="${API_PORT:-3001}"
MEDIA_PATH="${TODAY_MEDIA_PATH:-./data/media}"

log() { printf '[deploy] %s\n' "$*"; }
die() { printf '[deploy] ERROR: %s\n' "$*" >&2; exit 1; }

need_cmd() {
  command -v "$1" >/dev/null 2>&1 || die "missing command: $1"
}

ensure_env() {
  if [[ ! -f "$ENV_FILE" ]]; then
    [[ -f "$ENV_EXAMPLE" ]] || die ".env.example not found"
    cp "$ENV_EXAMPLE" "$ENV_FILE"
    log "created .env from .env.example — please review TODAY_JWT_SECRET / OPENAI_API_KEY"
  else
    log "using existing .env"
  fi
}

ensure_media_dir() {
  local dir="$MEDIA_PATH"
  # Resolve relative to repo root
  if [[ "$dir" != /* ]]; then
    dir="$ROOT/${dir#./}"
  fi
  mkdir -p "$dir"
  # Container runs as nobody; make writable on Linux bind mounts.
  chmod 777 "$dir" 2>/dev/null || true
  log "media dir: $dir (TODAY_MEDIA_ROOT=${TODAY_MEDIA_ROOT:-/data/media})"
}

wait_http() {
  local url="$1"
  local name="$2"
  local tries="${3:-60}"
  local i
  for i in $(seq 1 "$tries"); do
    if curl -fsS "$url" >/dev/null 2>&1; then
      log "$name ready: $url"
      return 0
    fi
    sleep 2
  done
  die "$name not ready after ${tries} attempts: $url"
}

cmd_up() {
  local build=1
  for arg in "$@"; do
    case "$arg" in
      --no-build) build=0 ;;
      -h|--help)
        usage
        exit 0
        ;;
    esac
  done

  need_cmd docker
  need_cmd curl
  docker compose version >/dev/null 2>&1 || die "Docker Compose v2 required (docker compose)"

  ensure_env
  ensure_media_dir

  if [[ "$build" -eq 1 ]]; then
    log "building & starting stack..."
    "${COMPOSE[@]}" up -d --build
  else
    log "starting stack (no rebuild)..."
    "${COMPOSE[@]}" up -d
  fi

  log "waiting for health..."
  wait_http "http://127.0.0.1:${API_PORT}/health/live" "api" 90
  wait_http "http://127.0.0.1:${WEB_PORT}/" "web" 60

  # Best-effort schema migrate for existing volumes
  if docker ps --format '{{.Names}}' | grep -qx 'today-mysql'; then
    log "applying DB schema/migrations (idempotent)..."
    bash "$ROOT/scripts/db-init.sh" --docker || log "db-init reported errors (see above); API schema bootstrap may still apply"
  fi

  cmd_status
  cat <<EOF

[deploy] Today is up.
  Web:    http://localhost:${WEB_PORT}
  API:    http://localhost:${API_PORT}/health
  Media:  ${MEDIA_PATH} → container /data/media
  Logs:   bash scripts/deploy-docker.sh logs
  Stop:   bash scripts/deploy-docker.sh down

EOF
}

cmd_down() {
  need_cmd docker
  log "stopping stack..."
  "${COMPOSE[@]}" down "$@"
  log "stopped"
}

cmd_restart() {
  need_cmd docker
  ensure_env
  ensure_media_dir
  log "restarting stack..."
  "${COMPOSE[@]}" up -d --build
  wait_http "http://127.0.0.1:${API_PORT}/health/live" "api" 90
  wait_http "http://127.0.0.1:${WEB_PORT}/" "web" 60
  cmd_status
}

cmd_status() {
  need_cmd docker
  "${COMPOSE[@]}" ps
  echo
  curl -fsS "http://127.0.0.1:${API_PORT}/health" 2>/dev/null | head -c 2000 || true
  echo
}

cmd_logs() {
  need_cmd docker
  if [[ $# -gt 0 ]]; then
    "${COMPOSE[@]}" logs -f --tail=200 "$@"
  else
    "${COMPOSE[@]}" logs -f --tail=200 api web
  fi
}

cmd_health() {
  need_cmd curl
  echo "== api /health/live =="
  curl -fsS "http://127.0.0.1:${API_PORT}/health/live" || true
  echo
  echo "== api /health =="
  curl -fsS "http://127.0.0.1:${API_PORT}/health" || true
  echo
  echo "== web =="
  curl -fsSI "http://127.0.0.1:${WEB_PORT}/" | head -n 5 || true
}

cmd_migrate() {
  need_cmd docker
  bash "$ROOT/scripts/db-init.sh" --docker
}

usage() {
  cat <<'EOF'
Today Docker 全栈部署脚本（MySQL + Qdrant + API + Web）

Usage:
  bash scripts/deploy-docker.sh              # 默认 up（构建并启动）
  bash scripts/deploy-docker.sh up           # 同上
  bash scripts/deploy-docker.sh up --no-build
  bash scripts/deploy-docker.sh down         # 停止（保留数据卷）
  bash scripts/deploy-docker.sh down -v      # 停止并删除数据卷
  bash scripts/deploy-docker.sh restart
  bash scripts/deploy-docker.sh status
  bash scripts/deploy-docker.sh logs [svc]
  bash scripts/deploy-docker.sh health
  bash scripts/deploy-docker.sh migrate      # 对 today-mysql 跑 schema/迁移
EOF
}

main() {
  local cmd="${1:-up}"
  shift || true
  case "$cmd" in
    up) cmd_up "$@" ;;
    down) cmd_down "$@" ;;
    restart) cmd_restart "$@" ;;
    status|ps) cmd_status "$@" ;;
    logs) cmd_logs "$@" ;;
    health) cmd_health "$@" ;;
    migrate|db-init) cmd_migrate "$@" ;;
    -h|--help|help) usage ;;
    *) die "unknown command: $cmd (try: up|down|restart|status|logs|health|migrate)" ;;
  esac
}

main "$@"
