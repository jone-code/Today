#!/usr/bin/env bash
# Apply incremental migrations for existing Today MySQL databases.
# Fresh installs: prefer `npm run db:init` / `db:init:docker` (schema + migrations + verify).
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec bash "$ROOT/scripts/db-init.sh" "$@"