#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# One-command local setup.
#
#   ./scripts/setup.sh
#
# Creates the .env file, the PostgreSQL role and both databases, and installs
# the frontend dependencies.
# ---------------------------------------------------------------------------
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

DB_NAME="${DB_NAME:-bookleaf}"
TEST_DB_NAME="${TEST_DB_NAME:-bookleaf_test}"
DB_USER="${DB_USER:-bookleaf}"
DB_PASSWORD="${DB_PASSWORD:-bookleaf}"

echo "==> Copying .env.example to .env (if it does not exist yet)"
if [ ! -f .env ]; then
  cp .env.example .env
  echo "    created .env — put your API key in it if you have one"
else
  echo "    .env already exists, leaving it alone"
fi

echo "==> Creating the PostgreSQL role and databases"
if command -v psql >/dev/null 2>&1; then
  psql -d postgres -tc "SELECT 1 FROM pg_roles WHERE rolname='${DB_USER}'" | grep -q 1 \
    || psql -d postgres -c "CREATE ROLE ${DB_USER} LOGIN PASSWORD '${DB_PASSWORD}';"
  psql -d postgres -tc "SELECT 1 FROM pg_database WHERE datname='${DB_NAME}'" | grep -q 1 \
    || psql -d postgres -c "CREATE DATABASE ${DB_NAME} OWNER ${DB_USER};"
  psql -d postgres -tc "SELECT 1 FROM pg_database WHERE datname='${TEST_DB_NAME}'" | grep -q 1 \
    || psql -d postgres -c "CREATE DATABASE ${TEST_DB_NAME} OWNER ${DB_USER};"
  echo "    databases ready (${DB_NAME}, ${TEST_DB_NAME})"
else
  echo "    psql not found — create the databases manually, or use: docker compose up -d db"
fi

echo "==> Installing frontend dependencies"
cd "$ROOT/frontend"
npm install --silent
echo "    done"

cat <<'EOF'

Setup complete.

  Terminal 1 — backend:  ./scripts/dev-backend.sh     (http://localhost:8080)
  Terminal 2 — frontend: ./scripts/dev-frontend.sh    (http://localhost:5173)

  Demo logins
    author  priya.sharma@email.com / Author@1234
    admin   ops@bookleaf.in        / Admin@1234

EOF
