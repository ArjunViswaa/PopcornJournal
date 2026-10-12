-- Posters can also be uploaded by the user, in which case there is no source image URL.
ALTER TABLE movie_posters ALTER COLUMN source_image_url DROP NOT NULL;
