#!/usr/bin/env sh
# Replaces the journal database with a backup. Takes a fresh backup first.
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
set -a; . "$ROOT/.env"; set +a
[ -f "$1" ] || { echo "usage: $0 backups/<file>.dump"; exit 1; }
printf "This replaces everything in '%s'. Type the database name to continue: " "$DB_NAME"; read -r answer
[ "$answer" = "$DB_NAME" ] || { echo "Cancelled."; exit 1; }
"$ROOT/scripts/backup.sh"
PGPASSWORD="$DB_PASSWORD" pg_restore -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" --clean --if-exists --no-owner "$1"
echo "Restore complete. Restart the backend."
