-- =====================================================================
--  CineBook - add movie details to an EXISTING cinebook database.
--  Keeps all your bookings. Run once.
-- =====================================================================

USE cinebook;

ALTER TABLE shows
    ADD COLUMN genre        VARCHAR(50) NOT NULL DEFAULT '',
    ADD COLUMN duration_min INT         NOT NULL DEFAULT 0,
    ADD COLUMN certificate  VARCHAR(5)  NOT NULL DEFAULT '';

UPDATE shows SET genre = 'Sci-Fi, Drama',     duration_min = 169, certificate = 'UA' WHERE movie_name = 'Interstellar';
UPDATE shows SET genre = 'Sci-Fi, Thriller',  duration_min = 148, certificate = 'UA' WHERE movie_name = 'Inception';
UPDATE shows SET genre = 'Action, Crime',     duration_min = 152, certificate = 'UA' WHERE movie_name = 'The Dark Knight';
UPDATE shows SET genre = 'Sci-Fi, Adventure', duration_min = 162, certificate = 'UA' WHERE movie_name = 'Avatar';

-- Check: should show the 4 movies with their new details
SELECT show_id, movie_name, show_time, genre, duration_min, certificate FROM shows;
