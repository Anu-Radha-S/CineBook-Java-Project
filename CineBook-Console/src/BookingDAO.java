import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** All SQL for CineBook lives here (Data Access Object). */
public class BookingDAO {

    /** Reads every show from the shows table. */
    public List<Show> loadShows(Connection con) throws SQLException {
        List<Show> shows = new ArrayList<>();
        String sql = "SELECT show_id, movie_name, show_time FROM shows ORDER BY show_id";
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                shows.add(new Show(rs.getInt("show_id"), rs.getString("movie_name"), rs.getString("show_time")));
            }
        }
        return shows;
    }

    /**
     * Copies the seat status from the seats table into the show's 2D array.
     * With lock = true the rows are locked (SELECT ... FOR UPDATE) until the transaction ends,
     * so two counters cannot book the same seat at the same time.
     */
    public void loadSeatGrid(Connection con, Show show, boolean lock) throws SQLException {
        String sql = "SELECT seat_code, status FROM seats WHERE show_id = ?" + (lock ? " FOR UPDATE" : "");
        show.clearSeats();
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, show.getShowNo());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    if ("BOOKED".equals(rs.getString("status"))) {
                        show.markBooked(rs.getString("seat_code"));
                    }
                }
            }
        }
    }

    /** Highest booking number stored so far (e.g. 1005 for BK1005), or 1000 if there are none. */
    public int findLastBookingNumber(Connection con) throws SQLException {
        String sql = "SELECT MAX(CAST(SUBSTRING(booking_id, 3) AS UNSIGNED)) FROM bookings";
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            if (rs.next() && rs.getObject(1) != null) {
                return rs.getInt(1);
            }
        }
        return 1000;
    }

    /** Inserts the booking, its seats, and marks those seats BOOKED. Must run inside a transaction. */
    public void saveBooking(Connection con, Booking booking, String coupon) throws SQLException {
        String insertBooking = "INSERT INTO bookings (booking_id, show_id, total_amount, coupon_code, status) "
                + "VALUES (?, ?, ?, ?, 'BOOKED')";
        try (PreparedStatement ps = con.prepareStatement(insertBooking)) {
            ps.setString(1, booking.getBookingId());
            ps.setInt(2, booking.getShow().getShowNo());
            ps.setDouble(3, booking.getAmount());
            ps.setString(4, booking.isCouponApplied() ? coupon.trim().toUpperCase() : null);
            ps.executeUpdate();
        }

        String insertSeat = "INSERT INTO booking_seats (booking_id, show_id, seat_code) VALUES (?, ?, ?)";
        String updateSeat = "UPDATE seats SET status = 'BOOKED' WHERE show_id = ? AND seat_code = ?";
        try (PreparedStatement ins = con.prepareStatement(insertSeat);
             PreparedStatement upd = con.prepareStatement(updateSeat)) {
            for (String code : booking.getSeats()) {
                ins.setString(1, booking.getBookingId());
                ins.setInt(2, booking.getShow().getShowNo());
                ins.setString(3, code);
                ins.addBatch();

                upd.setInt(1, booking.getShow().getShowNo());
                upd.setString(2, code);
                upd.addBatch();
            }
            ins.executeBatch();
            upd.executeBatch();
        }
    }

    /** Marks the booking CANCELLED with its refund and frees its seats. Must run inside a transaction. */
    public void cancelBooking(Connection con, Booking booking, double refund) throws SQLException {
        String cancel = "UPDATE bookings SET status = 'CANCELLED', refund_amount = ?, cancelled_at = NOW() "
                + "WHERE booking_id = ?";
        try (PreparedStatement ps = con.prepareStatement(cancel)) {
            ps.setDouble(1, refund);
            ps.setString(2, booking.getBookingId());
            ps.executeUpdate();
        }
        String free = "UPDATE seats s JOIN booking_seats bs ON bs.show_id = s.show_id AND bs.seat_code = s.seat_code "
                + "SET s.status = 'FREE' WHERE bs.booking_id = ?";
        try (PreparedStatement ps = con.prepareStatement(free)) {
            ps.setString(1, booking.getBookingId());
            ps.executeUpdate();
        }
    }

    /** Loads one booking by ID (null if not found). Use lock = true inside a transaction before changing it. */
    public Booking findBooking(Connection con, String bookingId, List<Show> shows, boolean lock) throws SQLException {
        String sql = "SELECT booking_id, show_id, total_amount, coupon_code, status FROM bookings WHERE booking_id = ?"
                + (lock ? " FOR UPDATE" : "");
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, bookingId.trim().toUpperCase());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return readBooking(con, rs, shows);
                }
            }
        }
        return null;
    }

    /** Loads every booking, oldest first. */
    public List<Booking> findAllBookings(Connection con, List<Show> shows) throws SQLException {
        List<Booking> list = new ArrayList<>();
        String sql = "SELECT booking_id, show_id, total_amount, coupon_code, status FROM bookings "
                + "ORDER BY booked_at, booking_id";
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(readBooking(con, rs, shows));
            }
        }
        return list;
    }

    private Booking readBooking(Connection con, ResultSet rs, List<Show> shows) throws SQLException {
        String id = rs.getString("booking_id");
        int showId = rs.getInt("show_id");
        Show show = null;
        for (Show s : shows) {
            if (s.getShowNo() == showId) {
                show = s;
            }
        }
        List<String> seats = new ArrayList<>();
        String sql = "SELECT seat_code FROM booking_seats WHERE booking_id = ? "
                + "ORDER BY LEFT(seat_code, 1), CAST(SUBSTRING(seat_code, 2) AS UNSIGNED)";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, id);
            try (ResultSet srs = ps.executeQuery()) {
                while (srs.next()) {
                    seats.add(srs.getString("seat_code"));
                }
            }
        }
        return new Booking(id, show, seats, rs.getDouble("total_amount"),
                rs.getString("coupon_code") != null, "CANCELLED".equals(rs.getString("status")));
    }
}