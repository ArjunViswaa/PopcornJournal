#!/usr/bin/env sh
# Compressed pg_dump of the journal into backups/ (git-ignored).
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
set -a; . "$ROOT/.env"; set +a
mkdir -p "$ROOT/backups"
TARGET="$ROOT/backups/$DB_NAME-$(date +%Y%m%d-%H%M%S).dump"
PGPASSWORD="$DB_PASSWORD" pg_dump -h "$DB_HOST" -p "$DB_PORT" -U "$DB_USER" -d "$DB_NAME" -Fc -f "$TARGET"
echo "Backup written to $TARGET"
