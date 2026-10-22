#!/usr/bin/env bash
# Runs the Spring Boot backend with the settings from .env.
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT"

if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  source .env
  set +a
fi

cd "$ROOT/backend"
exec mvn -B spring-boot:run
