#!/usr/bin/env sh
# Starts the backend, waits until it reports healthy, then runs the frontend.
# Ctrl+C stops both. Backend output goes to logs/backend.log.
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
PORT="$(grep -E '^SERVER_PORT=' "$ROOT/.env" 2>/dev/null | cut -d= -f2)"; PORT="${PORT:-8080}"
HEALTH="http://127.0.0.1:$PORT/api/v1/health"
mkdir -p "$ROOT/logs"
[ -d "$ROOT/frontend/node_modules" ] || (cd "$ROOT/frontend" && npm install --no-audit --no-fund)

(cd "$ROOT/backend" && exec mvn -q spring-boot:run) > "$ROOT/logs/backend.log" 2>&1 &
BACKEND=$!
trap 'echo "Stopping backend..."; kill $BACKEND 2>/dev/null; pkill -P $BACKEND 2>/dev/null' EXIT INT TERM

printf "Starting backend"
for _ in $(seq 1 90); do
  curl -sf "$HEALTH" >/dev/null && break
  kill -0 $BACKEND 2>/dev/null || { echo; echo "Backend stopped during startup:"; tail -25 "$ROOT/logs/backend.log"; exit 1; }
  printf "."; sleep 2
done
curl -sf "$HEALTH" >/dev/null || { echo; echo "Backend did not become healthy:"; tail -25 "$ROOT/logs/backend.log"; exit 1; }
echo; echo "Backend is up. Starting frontend: http://127.0.0.1:5180"
cd "$ROOT/frontend" && npm run dev:open
