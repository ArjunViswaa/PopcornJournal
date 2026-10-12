-- Posters found by the optional lookup, stored locally so the app works offline and the
-- browser never contacts the source. A poster URL typed by the user (movies.poster_url)
-- always takes precedence and is never changed by the lookup.
CREATE TABLE movie_posters (
    movie_id         BIGINT PRIMARY KEY REFERENCES movies (id) ON DELETE CASCADE,
    image            BYTEA        NOT NULL,
    content_type     VARCHAR(30)  NOT NULL
        CHECK (content_type IN ('image/jpeg', 'image/png', 'image/webp', 'image/gif')),
    source_name      VARCHAR(40)  NOT NULL,
    source_page_url  VARCHAR(500),
    source_image_url VARCHAR(1000) NOT NULL,
    matched_title    VARCHAR(300),
    fetched_at       TIMESTAMPTZ  NOT NULL DEFAULT now()
);

-- Outcome of the last automatic lookup, so bulk runs don't repeat finished work.
-- REJECTED = the user removed a found poster; bulk runs never fetch it again.
ALTER TABLE movies
    ADD COLUMN poster_lookup_status VARCHAR(12)
        CHECK (poster_lookup_status IN ('FOUND', 'NOT_FOUND', 'FAILED', 'REJECTED')),
    ADD COLUMN poster_lookup_at     TIMESTAMPTZ;
