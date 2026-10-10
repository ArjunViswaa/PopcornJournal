# Architecture, data model and API

## Overview

```
 Browser ──► Vite dev server :5180 ──/api proxy──► Spring Boot :8080 ──JDBC──► PostgreSQL 17 :5432
 (React, TanStack Query)                            (REST, Flyway, JPA)          (popcorn database)
```

- **Frontend** (`frontend/`): React 19 + TypeScript (strict) + Vite, React Router 7,
  TanStack Query 5 for server state, Tailwind CSS 4, Recharts. It has no persistence of its
  own; `localStorage` holds only the grid/list preference.
- **Backend** (`backend/`): Java 21 (bytecode level), Spring Boot 3.5, Spring Web, Data JPA,
  Bean Validation, Flyway, springdoc-openapi, Apache POI for the spreadsheet.
- **Database**: PostgreSQL 17. The schema is owned by Flyway; Hibernate only validates it
  (`ddl-auto: validate`).
- **No Spring Security.** This is a single-user app bound to 127.0.0.1, so nothing needs
  authenticating. Ownership columns or Spring Security can be added later without
  restructuring (see "Multi-user later").

### Backend packages (`com.popcornjournal`)

| Package | Responsibility |
|---|---|
| `controller` | Thin REST controllers: validation, status codes, DTOs in and out |
| `service` | Business rules: duplicate identity, viewing method defaults, statistics assembly, transactions |
| `repository` | Spring Data repositories, plus two JDBC repositories for search and aggregations |
| `entity` | JPA entities (never serialised directly) |
| `dto` | Request/response records with Bean Validation |
| `mapper` | Entity → DTO mapping |
| `importer` | Spreadsheet reader, row interpreter, planner (dry run) and transactional writer, CLI runner |
| `exception` | Exceptions and the global RFC 9457 problem-document handler |
| `config` | Clock, CORS, OpenAPI |
| `support` | Title normalisation shared by search, duplicate detection and import |

### Frontend structure (`frontend/src`)

| Folder | Contents |
|---|---|
| `api/` | `client.ts` (fetch + `ApiError`), `endpoints.ts`, `queries.ts` (TanStack hooks and cache invalidation), `types.ts` |
| `components/ui` | Button, Field/Input/Select/Textarea, Dialog/ConfirmDialog (native `<dialog>`), Toast, Skeleton/Empty/Error states, Rating badge/input, Poster placeholder, Pagination |
| `components/charts` | `BarChartCard` (chart + table view), `StatTile` |
| `components/layout` | App shell with sidebar/mobile navigation and page header |
| `features/*` | dashboard, movies (library, detail, form), viewings (dialog, diary), stats, watchlist, import |

## Data model

```
genres ◄── movie_genres ──► movies ◄── viewing_sessions ──► platforms
                              ▲               │
                              │               └──────────► venues
                    import_source_rows ──► import_runs
watchlist_items ──► genres
```

| Table | Purpose and key decisions |
|---|---|
| `movies` | One row per title (movie or series season). Stable `BIGSERIAL` id. `title_key` (letters and digits, lower case) supports duplicate detection and punctuation-insensitive search. Unique index `ux_movies_identity` on (title_key, format, year, language), so remakes with a different year or language are allowed. `rating` 1–10 *or* `rating_beyond_scale` (∞), never both (CHECK). Optional metadata (director, cast, runtime, poster URL, metadata source) is nullable. `import_key` stores the natural key at first import and never changes. |
| `viewing_sessions` | Each time a title was watched. `watched_on DATE NULL` (NULL = not recorded, never "today"), optional `watched_at_time` (CHECK: needs a date). `method` (CINEMA/STREAMING/TELEVISION/PHYSICAL_MEDIA/OTHER, NULL = not recorded) is separate from *where*: `platform_id` and/or `venue_id`, plus free-text `location`. A viewing has its own optional rating and notes, distinct from the movie's. `ON DELETE CASCADE` from movies; deleting a viewing never touches the movie. |
| `genres`, `movie_genres` | Many-to-many. The genre keeps the user's full label ("Thriller - Crime") and a derived `category` ("Thriller") for grouping. Deleting a genre in use is refused (RESTRICT). |
| `platforms` | Reusable streaming services/sources with a `default_method` used when a viewing doesn't say. Names are unique case-insensitively; the app also matches on letters and digits so "Zee5" finds "Zee 5". |
| `venues` | Cinemas/theatres, optional city. |
| `watchlist_items` | Recommendations. Separate from movies so they never count as watched. |
| `import_runs` | One row per committed import: file name, SHA-256, who triggered it, summary JSON. |
| `import_source_rows` | The original cells of every imported row (JSONB), linked to the movie or watchlist item (`ON DELETE SET NULL`, so a movie deleted in the app stays recognisable and isn't re-imported). Keyed by natural key, not row number, because inserting rows in Excel shifts the numbers. |

**Dates and times.** Viewing dates are `DATE` with no time zone: the calendar day as
remembered. Audit columns are `TIMESTAMPTZ`, written in UTC. "This year" and "this month"
on the dashboard use the server's local date (an injectable `Clock`).

**Indexes.** Trigram GIN on `lower(title)` (substring search), btree on language, rating,
release year, `viewing_sessions(movie_id)`, `(watched_on)`, `(platform_id)`, `(venue_id)`,
`movie_genres(genre_id)`, and a partial index for favourites.

**What was not normalised.** Language is a plain column (one value per title, no extra
attributes). Cast is free text. Neither would gain much from a table yet.

## Statistics: missing is not zero

All aggregations run in SQL (`StatsRepository`):

- Averages use 1–10 ratings only. ∞ ratings are counted separately, and unrated titles are
  counted as unrated. A group with no numeric ratings has `averageRating: null` and is left
  off average charts.
- Monthly and yearly charts use dated viewings only. The number of undated viewings is
  returned and shown next to them.
- A viewing with no method is reported as `NOT_RECORDED`, not dropped.
- The monthly timeline fills empty months *between* the first and last dated viewing, but
  doesn't extend past them.

## REST API (`/api/v1`)

Interactive documentation is at `http://127.0.0.1:8080/swagger-ui.html`, and the OpenAPI
JSON at `/api/v1/openapi`.

| Method | Path | Purpose |
|---|---|---|
| GET | `/movies` | Search/filter/sort/page. Params: `q, language, genreId, category, platformId, venueId, method, format, watchedYear, watchedFrom, watchedTo, minRating, maxRating, ratingTier (RATED/UNRATED/BEYOND_SCALE), favourite, list (FAVOURITES/TOP_RATED/RECENTLY_WATCHED/UNRATED/NO_REVIEW/MISSING_METADATA), sort (TITLE/LATEST_WATCHED/EARLIEST_WATCHED/HIGHEST_RATED/LOWEST_RATED/RECENTLY_ADDED/RELEASE_YEAR), page, size≤100` |
| GET | `/movies/{id}` | Detail with genres, viewings and original spreadsheet rows |
| POST | `/movies` | `{movie, initialViewing?}`; 201 + Location; 409 with `conflictsWith` for an exact duplicate |
| PUT | `/movies/{id}` | Replace editable fields |
| PATCH | `/movies/{id}/favourite` | `{favourite}` |
| DELETE | `/movies/{id}` | 204; also removes its viewings |
| GET | `/movies/duplicates` | Same-title candidates for a warning while typing |
| GET/POST | `/movies/{id}/viewings` | List / add viewings |
| GET | `/viewings` | Diary: `year`, `undated`, `page`, `size` |
| GET/PUT/DELETE | `/viewings/{id}` | One viewing |
| GET | `/stats/dashboard`, `/stats/insights` | Statistics |
| GET/POST/PUT/DELETE | `/genres`, `/platforms`, `/venues` | Reference data; delete returns 409 while in use |
| GET | `/languages` | Distinct languages |
| GET/POST/PUT/DELETE | `/watchlist` | Recommendations |
| POST | `/imports/preview` | Multipart `file`: dry-run report |
| POST | `/imports` | Multipart `file`: commit, returns the report |
| GET | `/imports` | Recent committed runs |
| GET | `/health` (and `/actuator/health`) | API + database status and schema version |

**Errors** are RFC 9457 problem documents: `{type, title, status, detail, instance}` plus
`errors: [{field, message}]` for validation and `conflictsWith` for duplicates. Stack traces,
SQL and constraint names never reach the client; unexpected errors are logged on the server.

**N+1 avoidance.** The library query fetches a page with a lateral aggregate (viewing count,
last/first watched, platforms, venues), then the page's genres in one `IN` query. Detail
pages fetch the movie with genres via an entity graph and the viewings with platform and
venue via fetch joins.

## Import pipeline

`SpreadsheetReader` (cells → typed values, headers matched by name) → `RowInterpreter`
(types, ∞, `-`, dates, warnings) → `ImportPlanner` (identity, repeats, existing data,
viewings to create; read-only) → `SpreadsheetImportService.commit` (advisory lock, one
transaction). The same plan powers the dry-run report, the CLI and the web importer.
Details and decisions are in [excel-data-audit.md](excel-data-audit.md).

## Security and privacy

- Backend binds to `127.0.0.1` (`SERVER_ADDRESS`), the Vite dev server to `127.0.0.1`, and
  Docker publishes ports on `127.0.0.1` only. The native PostgreSQL `pg_hba.conf` accepts
  only local connections; `listen_addresses` was set to `localhost` (applies after the
  service restarts).
- CORS allows only the configured frontend origins; other origins get 403.
- Credentials live in `.env` (git-ignored); `.env.example` has placeholders.
- Uploads: `.xlsx` only, 5 MB limit, content checked as a zip/OOXML workbook, client file
  names reduced to a safe base name and never used as paths.
- Review text is not logged; import messages never quote reviews or synopses.
- The spreadsheet, import reports, backups and `.env` are git-ignored.
- **This is not hardened for the internet.** There is no authentication, rate limiting or
  TLS. Don't expose it beyond localhost without adding those.

## Multi-user later

Add a `users` table and an `owner_id` column on `movies`, `watchlist_items`,
`platforms`/`venues` (or keep those shared), include `owner_id` in `ux_movies_identity`,
and add Spring Security. Services already go through a single place per aggregate, so
scoping queries by owner is a contained change.
