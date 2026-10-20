-- =====================================================================
--  CineBook - MySQL schema
--  Run once:  mysql -u root -p < database/cinebook_schema.sql
--  WARNING: drops and recreates the "cinebook" database (all old data is lost).
-- =====================================================================

DROP DATABASE IF EXISTS cinebook;
CREATE DATABASE cinebook;
USE cinebook;

-- One row per show (movie + time + movie details)
CREATE TABLE shows (
    show_id      INT AUTO_INCREMENT PRIMARY KEY,
    movie_name   VARCHAR(100) NOT NULL,
    show_time    VARCHAR(20)  NOT NULL,
    genre        VARCHAR(50)  NOT NULL DEFAULT '',
    duration_min INT          NOT NULL DEFAULT 0,
    certificate  VARCHAR(5)   NOT NULL DEFAULT '',
    poster       VARCHAR(100) NOT NULL DEFAULT ''   -- image file in CineBook-Web/web/posters
);

-- 40 seats (5 rows x 8 columns) for every show; each show has its own seats
CREATE TABLE seats (
    show_id     INT         NOT NULL,
    seat_code   VARCHAR(3)  NOT NULL,            -- e.g. 'C3'
    row_label   CHAR(1)     NOT NULL,            -- 'A' .. 'E'
    seat_no     INT         NOT NULL,            -- 1 .. 8
    seat_type   VARCHAR(10) NOT NULL,            -- REGULAR / PREMIUM / VIP (price lives in the Java enum)
    status      ENUM('FREE', 'BOOKED') NOT NULL DEFAULT 'FREE',
    PRIMARY KEY (show_id, seat_code),
    FOREIGN KEY (show_id) REFERENCES shows(show_id)
);

-- One row per booking transaction
CREATE TABLE bookings (
    booking_id    VARCHAR(10)   PRIMARY KEY,      -- e.g. 'BK1001'
    show_id       INT           NOT NULL,
    total_amount  DECIMAL(10,2) NOT NULL,
    coupon_code   VARCHAR(20)   NULL,
    status        ENUM('BOOKED', 'CANCELLED') NOT NULL DEFAULT 'BOOKED',
    refund_amount DECIMAL(10,2) NULL,
    booked_at     TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    cancelled_at  TIMESTAMP     NULL,
    FOREIGN KEY (show_id) REFERENCES shows(show_id)
);

-- Which seats belong to which booking
CREATE TABLE booking_seats (
    booking_id  VARCHAR(10) NOT NULL,
    show_id     INT         NOT NULL,
    seat_code   VARCHAR(3)  NOT NULL,
    PRIMARY KEY (booking_id, seat_code),
    FOREIGN KEY (booking_id) REFERENCES bookings(booking_id),
    FOREIGN KEY (show_id, seat_code) REFERENCES seats(show_id, seat_code)
);

-- Sample shows
INSERT INTO shows (movie_name, show_time, genre, duration_min, certificate, poster) VALUES
    ('Interstellar',    '7:30 PM',  'Sci-Fi, Drama',     169, 'UA', 'interstellar.jpg'),
    ('Inception',       '10:00 AM', 'Sci-Fi, Thriller',  148, 'UA', 'inception.jpg'),
    ('The Dark Knight', '3:00 PM',  'Action, Crime',     152, 'UA', 'dark-knight.jpg'),
    ('Avatar',          '9:45 PM',  'Sci-Fi, Adventure', 162, 'UA', 'avatar.jpg');

-- Create the 5 x 8 seat grid for every show
INSERT INTO seats (show_id, seat_code, row_label, seat_no, seat_type)
SELECT s.show_id,
       CONCAT(r.row_label, c.seat_no),
       r.row_label,
       c.seat_no,
       CASE WHEN r.row_label IN ('A', 'B') THEN 'REGULAR'
            WHEN r.row_label IN ('C', 'D') THEN 'PREMIUM'
            ELSE 'VIP' END
FROM shows s
CROSS JOIN (SELECT 'A' AS row_label UNION ALL SELECT 'B' UNION ALL SELECT 'C'
            UNION ALL SELECT 'D' UNION ALL SELECT 'E') r
CROSS JOIN (SELECT 1 AS seat_no UNION ALL SELECT 2 UNION ALL SELECT 3 UNION ALL SELECT 4
            UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8) c;

-- Quick check: should print 4 shows with 40 seats each
SELECT s.show_id, s.movie_name, s.show_time, COUNT(*) AS seats
FROM shows s JOIN seats st ON st.show_id = s.show_id
GROUP BY s.show_id, s.movie_name, s.show_time;
