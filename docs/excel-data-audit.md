# Excel data audit: `Popcorn_Sheet.xlsx`

Audit of the historical spreadsheet before import (10 Oct 2026). This file is meant to be
committed, so it describes structure and patterns only: no review text, and rows are
referred to by number rather than title wherever possible.

## 1. Workbook structure

| Sheet | Used range | Data rows | Notes |
|---|---|---|---|
| `Watch_Sheet` | A1:K158 (formatted to Z1144) | **157** (rows 2–158) | One row per title watched |
| `Recommendations` | A1:C5 | **1** (row 5; rows 2–4 blank) | Titles recommended to watch |

- No formulas, merged cells, comments, hyperlinks, tables or meaningful cell colours.
- Rows 159–1144 and columns L–Z are formatted but empty; nothing is lost by ignoring them.
- Data validation (dropdowns) exists on Genre (C2:C1144, 29 values, stored as two
  concatenated string literals because it exceeds Excel's 255-character limit), Rating
  (D: `1`–`10`, `Infinity`), Format (G2:G158: `Movie`, `Series`), Language (F: 10 languages
  plus the stray value `Language`) and a date check on Watch Date (J).

## 2. What a row represents

Each `Watch_Sheet` row is **one title, with at most one recorded viewing**. Normalised titles
(lower-case, letters and digits only) are all unique, so the sheet has no repeated movies.
Seasons of a series are separate rows (e.g. "… : S1", "… ( S2 )") with their own rating and
are kept as separate titles.

Rows look reverse-chronological (newest first): the 25 dated rows are rows 5–29 in strictly
descending date order. That ordering is **not** used to invent dates for undated rows.

## 3. Columns and mapping

| Col | Header | Filled | Type / values | Maps to | Notes |
|---|---|---|---|---|---|
| A | Movie / Series Name | 157 | text; row 140 is the number `2018` | `movies.title` | the film *2018*; stored as the text "2018" |
| B | Description | 154 | text, 39–636 chars | `movies.synopsis` | the user's own plot summary |
| C | Genre | 157 | 27 distinct labels like `Thriller - Crime` | `genres` + `movie_genres` | full label kept; category = part before ` - ` / ` / ` |
| D | Rating | 154 | integers 2–10 (145), `Infinity` (9) | `movies.rating` / `movies.rating_beyond_scale` | see §4 |
| E | Review | 154 | text, 22–507 chars | `movies.review` | |
| F | Language | 157 | 10 languages, consistent spelling | `movies.language` | |
| G | Format | 157 | `Movie` 136, `Series` 21 | `movies.format` | |
| H | Platform | 148 | 18 spellings incl. `-` (11) | `platforms` → `viewing_sessions.platform_id` | see §5 |
| I | Year | 154 | 1957–2026 | `movies.release_year` | always ≤ watch year, so it is the release year |
| J | Watch Date | **25** | real dates, Dec 2025 – Jul 2026 | `viewing_sessions.watched_on` | 132 rows have no date |
| K | Theatre | 157 | `-` (127) or one of 14 venues | `venues` → `viewing_sessions.venue_id` | |
| Recommendations A–C | Name, Recommended By, Genre | 1 | text | `watchlist_items` | kept out of "watched" counts |

Every imported row's original cells are stored verbatim (JSONB) in `import_source_rows`
and shown on the movie page under "Original spreadsheet row".

## 4. Ratings

- 1–10 integers become `movies.rating`.
- `Infinity` (9 titles, 4 movies and 5 series) is **a separate tier**: `rating_beyond_scale = true`
  and `rating = NULL`. It sorts above 10, has its own bar in the distribution, and is
  **excluded from numeric averages** (decision confirmed by the user). It does not make a
  title a favourite.
- 3 rows have no rating (rows 2–4). They are imported as unrated, not as 0.
- The sheet holds one rating per title, so it maps to the movie-level rating. Viewing-level
  ratings exist in the schema but are only filled when a later row repeats a movie.

## 5. Platform and theatre

- `-` means "nothing recorded" and becomes NULL. This applies to every column.
- Spelling variants are merged, with the original text kept in the source row:
  `jio Hotstar` → **Jio Hotstar**, `Youtube` → **YouTube**, `Zee5` → **Zee 5**.
  Merging compares names by letters and digits only. `Disney+Hotstar` and `Jio Hotstar`
  stay separate because that is how they were recorded.
- `YTS` and `5movierulz` are download sites, so their viewings use method **OTHER**, not
  STREAMING (configurable via `popcorn.import.non-streaming-platforms`).
- **Rows with both a theatre and a platform** (12 rows) become **two viewings**: a CINEMA
  viewing at the theatre carrying the row's date, plus an undated viewing on the platform
  (decision confirmed by the user).
- 8 cinema rows have a blank platform. One row (row 19) has `-` in both columns but has a
  date, so it gets a dated viewing with method "not recorded".

## 6. Dates

- 25 rows have dates; 132 do not. Undated rows still get a viewing (platform/theatre known,
  `watched_on = NULL`, decision confirmed by the user). Undated viewings count towards
  totals but not towards monthly/yearly charts, and the UI says so.
- One row (row 3) has no date, platform or theatre: the movie is imported **without** a
  viewing, rather than inventing one.
- Dates are calendar days stored as `DATE` (no time zone).

## 7. Data-quality observations

| Observation | Rows | Handling |
|---|---|---|
| Numeric title | 140 | stored as text "2018" (info message) |
| No description, rating, review or year | 2, 3, 4 | imported as-is; listed under "Unrated", "No review", "Missing details" |
| Platform blank | row 3 and 8 cinema rows | no platform viewing |
| `-` in Platform and Theatre | 19 | dated viewing, method not recorded |
| Spelling variants | 3 platforms | merged, originals preserved |
| Trailing space in review | 136 | trimmed in `movies.review`; original kept in source row |
| `Language` in the Language dropdown | (none) | ignored; a cell with that value would be left empty with a warning |
| Dropdown genres never used (`Comedy - Dark`, `Horror`) | (none) | created as genres so they're available in the app |
| Duplicates | none | n/a; the importer still supports them (§8) |

Wrong or ambiguous values are never guessed: the field is left empty with a warning, and
the original cell remains in `import_source_rows.raw_values`.

## 8. Import rules

- **Identity** of a movie = normalised title + format + release year + language, matching the
  `ux_movies_identity` unique index. Same title with a different year or language is a
  different film (flagged as "similar title" in the report).
- **Repeated rows** for the same identity become extra viewings of one movie; the repeat's
  rating/review is kept on that viewing.
- **Re-running** is safe: each row's natural key is stored (`movies.import_key`,
  `import_source_rows.record_key`). Rows seen before are reported `ALREADY_IMPORTED` and
  never modified, even if the sheet or the app has since changed (differences are listed).
  Movies deleted in the app are not re-created. A movie added by hand with the same identity
  is reported as a conflict and the row is skipped.
- **Transactions**: a commit is one transaction holding an advisory lock. Any failure rolls
  everything back, so the recovery is to fix the cause and run it again.
- **Dry run** is the default for the CLI and the first step in the web UI.

## 9. Results

Dry run, then commit (run #1), then a third run to confirm nothing changes:

| | Dry run | Commit | Re-run |
|---|---|---|---|
| Rows read | 158 | 158 | 158 |
| New movies | 157 | 157 | 0 |
| New viewings | 168 | 168 | 0 |
| Watchlist items | 1 | 1 | 0 |
| Already imported | 0 | 0 | 158 |
| Errors / warnings | 0 / 0 | 0 / 0 | 0 / 0 |

New reference data: 29 genres (the full dropdown list), 14 platforms, 14 venues.

**Independent verification.** The sheet was re-read with a separate parser (Python/openpyxl)
and compared with PostgreSQL field by field: 157 rows × 10 checks (title, synopsis, genre,
rating, ∞, review, language, format, year, viewing set). 1,569 of 1,570 matched exactly; the
one difference is row 136's trailing space (see §7). Database totals: 157 movies, 168
viewings (25 dated, 143 undated), 9 ∞, 145 rated, 3 unrated, 158 source rows.
