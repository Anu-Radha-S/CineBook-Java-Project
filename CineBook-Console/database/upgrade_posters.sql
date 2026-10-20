-- =====================================================================
--  CineBook - add a poster image to every show in an EXISTING cinebook database.
--  Keeps all your bookings. Run once.
--  The images themselves go in CineBook-Web/web/posters with these file names.
-- =====================================================================

USE cinebook;

ALTER TABLE shows
    ADD COLUMN poster VARCHAR(100) NOT NULL DEFAULT '';

SET SQL_SAFE_UPDATES = 0;

UPDATE shows SET poster = 'interstellar.jpg' WHERE movie_name = 'Interstellar';
UPDATE shows SET poster = 'inception.jpg'    WHERE movie_name = 'Inception';
UPDATE shows SET poster = 'dark-knight.jpg'  WHERE movie_name = 'The Dark Knight';
UPDATE shows SET poster = 'avatar.jpg'       WHERE movie_name = 'Avatar';

SET SQL_SAFE_UPDATES = 1;

-- Check: should show the 4 movies with their poster file names
SELECT show_id, movie_name, poster FROM shows;