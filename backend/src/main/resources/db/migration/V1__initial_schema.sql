-- Popcorn Journal schema, version 1.
-- Dates a viewing happened on are stored as DATE (the calendar day as recorded,
-- no time zone). Audit timestamps are TIMESTAMPTZ.

CREATE EXTENSION IF NOT EXISTS pg_trgm;

CREATE TABLE genres (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(80)  NOT NULL CHECK (btrim(name) <> ''),
    -- "Thriller - Crime" belongs to the "Thriller" category
    category   VARCHAR(60)  NOT NULL,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_genres_name ON genres (lower(name));
CREATE INDEX ix_genres_category ON genres (lower(category));

CREATE TABLE platforms (
    id             BIGSERIAL PRIMARY KEY,
    name           VARCHAR(80) NOT NULL CHECK (btrim(name) <> ''),
    -- method suggested when this platform is picked for a new viewing
    default_method VARCHAR(20) NOT NULL DEFAULT 'STREAMING'
        CHECK (default_method IN ('CINEMA', 'STREAMING', 'TELEVISION', 'PHYSICAL_MEDIA', 'OTHER')),
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_platforms_name ON platforms (lower(name));

CREATE TABLE venues (
    id         BIGSERIAL PRIMARY KEY,
    name       VARCHAR(160) NOT NULL CHECK (btrim(name) <> ''),
    city       VARCHAR(80),
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_venues_name ON venues (lower(name));

CREATE TABLE movies (
    id                  BIGSERIAL PRIMARY KEY,
    title               VARCHAR(250) NOT NULL CHECK (btrim(title) <> ''),
    original_title      VARCHAR(250),
    -- lower-cased alphanumerics of the title, maintained by the application
    title_key           VARCHAR(250) NOT NULL,
    format              VARCHAR(10)  NOT NULL DEFAULT 'MOVIE' CHECK (format IN ('MOVIE', 'SERIES')),
    release_year        INTEGER CHECK (release_year BETWEEN 1870 AND 2100),
    language            VARCHAR(40),
    synopsis            TEXT,
    rating              INTEGER CHECK (rating BETWEEN 1 AND 10),
    -- the spreadsheet's "Infinity": above the 1-10 scale, kept out of averages
    rating_beyond_scale BOOLEAN      NOT NULL DEFAULT FALSE,
    review              TEXT,
    private_notes       TEXT,
    favourite           BOOLEAN      NOT NULL DEFAULT FALSE,
    director            VARCHAR(250),
    cast_members        TEXT,
    runtime_minutes     INTEGER CHECK (runtime_minutes BETWEEN 1 AND 2000),
    poster_url          VARCHAR(500),
    metadata_source     VARCHAR(120),
    -- natural key captured when a row was imported; never changes afterwards,
    -- so re-imports still recognise a movie whose title was edited in the app
    import_key          VARCHAR(400) UNIQUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_movies_single_rating CHECK (NOT (rating_beyond_scale AND rating IS NOT NULL))
);
-- Same title is allowed when year, format or language tell the movies apart.
CREATE UNIQUE INDEX ux_movies_identity
    ON movies (title_key, format, COALESCE(release_year, 0), COALESCE(lower(language), ''));
CREATE INDEX ix_movies_title_trgm ON movies USING gin (lower(title) gin_trgm_ops);
CREATE INDEX ix_movies_language ON movies (lower(language));
CREATE INDEX ix_movies_rating ON movies (rating);
CREATE INDEX ix_movies_release_year ON movies (release_year);
CREATE INDEX ix_movies_favourite ON movies (id) WHERE favourite;

CREATE TABLE movie_genres (
    movie_id BIGINT NOT NULL REFERENCES movies (id) ON DELETE CASCADE,
    genre_id BIGINT NOT NULL REFERENCES genres (id) ON DELETE RESTRICT,
    PRIMARY KEY (movie_id, genre_id)
);
CREATE INDEX ix_movie_genres_genre ON movie_genres (genre_id);

CREATE TABLE viewing_sessions (
    id                  BIGSERIAL PRIMARY KEY,
    movie_id            BIGINT      NOT NULL REFERENCES movies (id) ON DELETE CASCADE,
    -- NULL means the date was never recorded; it is not "today"
    watched_on          DATE,
    watched_at_time     TIME,
    -- NULL means the way it was watched was never recorded
    method              VARCHAR(20)
        CHECK (method IN ('CINEMA', 'STREAMING', 'TELEVISION', 'PHYSICAL_MEDIA', 'OTHER')),
    platform_id         BIGINT REFERENCES platforms (id) ON DELETE RESTRICT,
    venue_id            BIGINT REFERENCES venues (id) ON DELETE RESTRICT,
    location            VARCHAR(200),
    rating              INTEGER CHECK (rating BETWEEN 1 AND 10),
    rating_beyond_scale BOOLEAN     NOT NULL DEFAULT FALSE,
    notes               TEXT,
    import_key          VARCHAR(420) UNIQUE,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT ck_sessions_time_needs_date CHECK (watched_at_time IS NULL OR watched_on IS NOT NULL),
    CONSTRAINT ck_sessions_single_rating CHECK (NOT (rating_beyond_scale AND rating IS NOT NULL))
);
CREATE INDEX ix_sessions_movie ON viewing_sessions (movie_id);
CREATE INDEX ix_sessions_watched_on ON viewing_sessions (watched_on);
CREATE INDEX ix_sessions_platform ON viewing_sessions (platform_id);
CREATE INDEX ix_sessions_venue ON viewing_sessions (venue_id);

CREATE TABLE watchlist_items (
    id             BIGSERIAL PRIMARY KEY,
    title          VARCHAR(250) NOT NULL CHECK (btrim(title) <> ''),
    recommended_by VARCHAR(160),
    genre_id       BIGINT REFERENCES genres (id) ON DELETE SET NULL,
    notes          TEXT,
    import_key     VARCHAR(400) UNIQUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE TABLE import_runs (
    id               BIGSERIAL PRIMARY KEY,
    source_file_name VARCHAR(255) NOT NULL,
    source_sha256    VARCHAR(64)  NOT NULL,
    triggered_by     VARCHAR(10)  NOT NULL CHECK (triggered_by IN ('CLI', 'WEB')),
    started_at       TIMESTAMPTZ  NOT NULL,
    finished_at      TIMESTAMPTZ  NOT NULL,
    summary          JSONB        NOT NULL
);

-- One row per spreadsheet record ever imported, with the original cell values.
-- record_key is the natural key, because row numbers shift when rows are inserted.
CREATE TABLE import_source_rows (
    id                  BIGSERIAL PRIMARY KEY,
    sheet_name          VARCHAR(60)  NOT NULL,
    record_key          VARCHAR(400) NOT NULL,
    row_number          INTEGER      NOT NULL,
    raw_values          JSONB        NOT NULL,
    movie_id            BIGINT REFERENCES movies (id) ON DELETE SET NULL,
    watchlist_item_id   BIGINT REFERENCES watchlist_items (id) ON DELETE SET NULL,
    first_import_run_id BIGINT       NOT NULL REFERENCES import_runs (id),
    last_import_run_id  BIGINT       NOT NULL REFERENCES import_runs (id),
    CONSTRAINT ux_source_rows_record UNIQUE (sheet_name, record_key)
);
CREATE INDEX ix_source_rows_movie ON import_source_rows (movie_id);
