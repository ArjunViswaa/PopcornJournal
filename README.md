# Popcorn Journal

A personal, local-first movie journal: every film and series you've watched, where and
when you watched it, your ratings and reviews, and the patterns in your viewing, stored in
PostgreSQL. It starts from your existing Excel spreadsheet.

- **Dashboard**: unique titles vs viewing sessions, this year/month, average rating (∞ kept
  separate), recently watched, highest rated, platforms, languages, genres, timeline.
- **Library**: most recently watched first by default; search, filters (language, genre,
  platform, format, watch year, rating), other sort orders, grid/list, personal lists (favourites, top rated, unrated, no review, missing details).
- **Movie pages**: rating, review, private notes, favourite, metadata, full viewing history
  with add/edit/delete, and the original spreadsheet row it came from.
- **Diary**, **Statistics**, **Watchlist**, and a **web importer** with a dry-run preview.
- **Posters** (optional): looked up on Wikipedia and stored locally in PostgreSQL.

Architecture, schema and API: [docs/architecture.md](docs/architecture.md).
Spreadsheet audit and import decisions: [docs/excel-data-audit.md](docs/excel-data-audit.md).

## Requirements

| Tool | Version | Notes |
|---|---|---|
| JDK | 21 or newer | built and tested with JDK 23 targeting Java 21 |
| Maven | 3.9+ | |
| Node.js | 22 LTS (20.19+ works) | npm 10 |
| PostgreSQL | 17 | native install **or** Docker Desktop |
| Microsoft Edge or Chrome | any recent | only for the Playwright journey |

No cloud accounts or API keys are needed.

## Project layout

```
backend/            Spring Boot API (Maven)   src/main/resources/db/migration = Flyway
frontend/           React + Vite app          e2e/ = Playwright journey
data/import/        put Popcorn_Sheet.xlsx here (git-ignored)
docs/               architecture and spreadsheet audit
scripts/            PowerShell (*.ps1) and shell (*.sh) helpers
assets/             app icons used by the desktop shortcuts
start.cmd           one-command launcher for Windows (logs in the window)
docker-compose.yml  PostgreSQL (and optional containerised app)
.env.example        configuration template (copy to .env)
```

## 1. Configure

**Windows (PowerShell)**

```powershell
Copy-Item .env.example .env
notepad .env        # set DB_PASSWORD (and PG_SUPERUSER_PASSWORD for a native install)
```

**macOS / Linux**

```bash
cp .env.example .env && ${EDITOR:-nano} .env
```

| Variable | Default | Meaning |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME` | localhost, 5432, popcorn | database location |
| `DB_USER`, `DB_PASSWORD` | popcorn, — | application role |
| `SERVER_PORT` | 8080 | backend port (binds to 127.0.0.1) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5180,http://127.0.0.1:5180` | allowed browser origins |
| `PG_SUPERUSER_PASSWORD` | — | native installs only; used by `db-setup.ps1` / `reset-dev-db.ps1` |
| `POSTERS_ENABLED` | false | turns on the optional Wikipedia poster lookup (see below) |

The backend reads `.env` directly; real environment variables take precedence.

## 2. Start PostgreSQL

**Option A: native PostgreSQL 17 (what this machine uses)**

```powershell
winget install -e --id PostgreSQL.PostgreSQL.17      # once; remember the postgres password
powershell -ExecutionPolicy Bypass -File .\scripts\db-setup.ps1
```

`db-setup.ps1` creates the `popcorn` role and the `popcorn` and `popcorn_e2e` databases. It
is safe to run again. On macOS/Linux, create them with `createuser`/`createdb` or use Docker.

**Option B: Docker**

```bash
docker compose up -d db
```

Port 5432 is published on 127.0.0.1 only. Data persists in the `popcorn-db` volume.

## 3. Migrations

Flyway runs automatically when the backend starts and creates or upgrades the schema
(`backend/src/main/resources/db/migration`). Nothing to run by hand.

## 4. Start the app

**Desktop shortcut (Windows).** Run this once to put a **Popcorn Journal** icon on your
Desktop (plus Start menu entries, including **Stop Popcorn Journal**):

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\install-shortcuts.ps1
```

Double-clicking the icon starts the backend and frontend in the background, waits until
both are ready (about 15 seconds), opens the app in your browser and closes its window.
If the app is already running it just opens the browser. Stop it with **Stop Popcorn
Journal** from the Start menu (`scripts\stop.ps1`). Logs go to `logs\`. Remove the shortcuts
with `install-shortcuts.ps1 -Remove`; run it again after moving the project folder.

**From a terminal.**

One command starts the backend, waits until it reports that the database is up, then
starts the frontend and opens **http://127.0.0.1:5180** in your browser. Ctrl+C stops both.

**Windows**: double-click `start.cmd`, or in a terminal:

```powershell
.\start.cmd
```

**macOS / Linux**

```bash
./scripts/start.sh
```

Backend output goes to `logs/backend.log`. If a backend is already running on the port, it
is reused. API docs: http://127.0.0.1:8080/swagger-ui.html.

To run the two parts separately instead (two terminals):

```powershell
cd backend;  mvn spring-boot:run
cd frontend; npm install; npm run dev
```

## 5. Import the spreadsheet

Put the file at `data/import/Popcorn_Sheet.xlsx`, then do a dry run first. It validates
every row and prints what would happen without writing anything:

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\import.ps1            # dry run
powershell -ExecutionPolicy Bypass -File .\scripts\import.ps1 -Commit    # import
```

```bash
./scripts/import.sh                 # dry run
./scripts/import.sh --commit        # import
```

JSON reports are saved in `data/import/reports/`. You can also use the **Import** page in
the app (upload → preview → confirm).

Running the import again is safe: rows imported before are reported as *already imported*
and never changed, edits made in the app are kept, and movies you deleted are not brought
back. The import runs in a single transaction, so if it fails nothing is written. Fix the
reported problem and run it again.

## Posters (optional)

With `POSTERS_ENABLED=true` in `.env` (and the backend restarted), the Library page shows
**Find posters**. It looks up each title on the English Wikipedia API, which is free and
needs no account or key. Matching images are downloaded once and stored in PostgreSQL, so
posters work offline and your browser never contacts Wikipedia.

- **What is sent:** only the title, release year, format and language of titles without a
  poster. Nothing else is sent, and requests go out only when you start a lookup.
- **Matching is strict.** A poster is used only when the title, year and film/series type
  agree; small transliteration differences ("Kanguvaa" vs "Kanguva") need the year to match,
  and part or side letters/numbers must match exactly. Otherwise the placeholder stays.
- **You stay in control.** A poster URL you enter on the Edit page always wins and is never
  replaced. "Wrong poster? Remove it" on a movie page deletes a found poster, and bulk
  runs won't fetch it again. Ratings, reviews and notes are never touched.
- Popular US sitcoms (Friends, The Office…) usually have no lead image on Wikipedia, so
  they keep the placeholder.

**Uploading your own.** On any movie page, **Upload poster** (or **Replace image**) stores
a JPEG, PNG, WebP or GIF of up to 5 MB in the database. This works even with the lookup
turned off, and nothing is sent anywhere. The lookup never replaces an uploaded poster.

## 6. Tests

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\test-all.ps1     # everything
```

or individually:

```bash
cd backend  && mvn verify          # 71 tests: unit + integration on an embedded PostgreSQL 17
cd frontend && npm run typecheck && npm test && npm run build
cd frontend && npx playwright test # journey against popcorn_e2e on ports 8081/5181
```

Backend integration tests start a throwaway embedded PostgreSQL (real binaries via
zonky, no Docker) and use generated fixture workbooks, never your spreadsheet. The
Playwright journey uses the separate `popcorn_e2e` database and installed Edge
(`PLAYWRIGHT_CHANNEL=chrome` to use Chrome).

## 7. Docker (optional, everything containerised)

```bash
docker compose --profile app up -d --build
```

This serves the app at http://127.0.0.1:8088, with the API at :8080. To import inside this
setup, use the web importer. The Dockerfiles are provided but were not exercised on this
machine, which has no Docker installed.

## Backup, restore and reset

```powershell
powershell -ExecutionPolicy Bypass -File .\scripts\backup.ps1                       # -> backups\popcorn-<timestamp>.dump
powershell -ExecutionPolicy Bypass -File .\scripts\restore.ps1 -File backups\x.dump  # asks you to type the db name; backs up first
powershell -ExecutionPolicy Bypass -File .\scripts\reset-dev-db.ps1                 # recreates popcorn_e2e
powershell -ExecutionPolicy Bypass -File .\scripts\reset-dev-db.ps1 -Database popcorn  # real journal: confirmation + backup first
```

macOS/Linux: `./scripts/backup.sh`, `./scripts/restore.sh backups/<file>.dump`.
With Docker you can also run `docker compose exec db pg_dump -U popcorn -Fc popcorn > backup.dump`.

## Troubleshooting

| Problem | Fix |
|---|---|
| `password authentication failed for user "popcorn"` | `DB_PASSWORD` in `.env` doesn't match the role. Run `db-setup.ps1` (creates the role) or `ALTER ROLE popcorn PASSWORD '…'` as postgres. |
| `Schema-validation: missing table` / Flyway errors | The backend must be pointed at a database it owns. Check `DB_NAME`; for a scratch db use `reset-dev-db.ps1`. |
| Port 5180 or 8080 already in use | Stop the other process, or set `VITE_PORT` / `SERVER_PORT`. If you change the frontend port, add it to `CORS_ALLOWED_ORIGINS` as well. |
| Frontend shows "The server is not reachable" | The backend isn't running, or isn't on the port in `VITE_API_PROXY_TARGET` (default `http://127.0.0.1:8080`). |
| `running scripts is disabled on this system` | Use `powershell -ExecutionPolicy Bypass -File …` as shown. |
| Import says "not an .xlsx workbook" | Save the file as Excel Workbook (.xlsx); .xls and .csv aren't supported. |
| Playwright can't find Edge | `set PLAYWRIGHT_CHANNEL=chrome`, or `npx playwright install chromium` and `PLAYWRIGHT_CHANNEL=chromium`. |

## Privacy and exposure

Everything runs and stays on this machine. `.env`, the spreadsheet, import reports and
backups are git-ignored. The app has **no authentication** and binds to 127.0.0.1. Running
locally does not make it safe to publish: don't expose it to a network or the internet
without adding authentication and TLS.

## Known limitations

- Posters come only from Wikipedia (optional, off by default). Titles without a confident
  match keep the generated placeholder. No other metadata (cast, runtime) is fetched.
- Custom tags and lists are not implemented; the personal lists are fixed filters.
- Dark theme only.
- Movies vs series: each season is its own title, as in the spreadsheet.
