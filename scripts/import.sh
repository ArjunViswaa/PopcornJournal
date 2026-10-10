#!/usr/bin/env sh
# Dry run by default; pass --commit to import.  ./scripts/import.sh [file.xlsx] [--commit]
set -e
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
FILE="$ROOT/data/import/Popcorn_Sheet.xlsx"; COMMIT=false
for arg in "$@"; do
  case "$arg" in
    --commit) COMMIT=true ;;
    *) FILE="$(cd "$(dirname "$arg")" && pwd)/$(basename "$arg")" ;;
  esac
done
mvn -q -f "$ROOT/backend/pom.xml" spring-boot:run \
  -Dspring-boot.run.arguments="--spring.main.web-application-type=none --import.file=$FILE --import.commit=$COMMIT --import.report-dir=$ROOT/data/import/reports --logging.level.root=WARN --spring.main.banner-mode=off"
