#!/usr/bin/env bash
# Runs the Vite dev server (proxies /api to the backend on :8080).
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT/frontend"

[ -d node_modules ] || npm install
exec npm run dev
