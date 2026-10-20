import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** The cinema: holds the list of shows and works with the database for bookings (composition). */
public class Theatre {

    private final String name;
    private final BookingDAO dao = new BookingDAO();
    private List<Show> shows;

    public Theatre(String name) {
        this.name = name;
    }

    /** Loads the shows and sets the booking ID counter from the database. Call once at start-up. */
    public void loadFromDatabase() throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            shows = dao.loadShows(con);
            Booking.setCounter(dao.findLastBookingNumber(con));
        }
    }

    public String getName() {
        return name;
    }

    public List<Show> getShows() {
        return shows;
    }

    /** Returns the show with the given number, or null. */
    public Show findShow(int showNo) {
        for (Show s : shows) {
            if (s.getShowNo() == showNo) {
                return s;
            }
        }
        return null;
    }

    /** Refreshes the show's seat grid with the latest data from the database. */
    public void refreshSeats(Show show) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            dao.loadSeatGrid(con, show, false);
        }
    }

    /** Refreshes every show's seat grid (used for the show list's "seats free" count). */
    public void refreshAllSeats() throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            for (Show s : shows) {
                dao.loadSeatGrid(con, s, false);
            }
        }
    }

    /**
     * Books the seats in one database transaction: all seats are saved, or none are.
     * @throws IllegalArgumentException if any seat is invalid or taken (nothing is booked then)
     */
    public Booking book(Show show, List<String> seatCodes, String coupon) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                dao.loadSeatGrid(con, show, true);   // lock this show's seats and get the latest status
                show.bookSeats(seatCodes);           // validates every seat on the 2D array; throws if any is bad
                Booking booking = new Booking(show, seatCodes);
                boolean useCoupon = Booking.isValidCoupon(coupon);
                double total = useCoupon ? booking.calculateTotal(coupon) : booking.calculateTotal();
                booking.setAmount(total, useCoupon);
                dao.saveBooking(con, booking, coupon);
                con.commit();
                return booking;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                dao.loadSeatGrid(con, show, false);  // undo any change made to the in-memory grid
                throw e;
            }
        }
    }

    /** Returns the booking with this ID, or null. */
    public Booking findBooking(String bookingId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return dao.findBooking(con, bookingId, shows, false);
        }
    }

    public List<Booking> getBookings() throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            return dao.findAllBookings(con, shows);
        }
    }

    /**
     * Cancels the booking, frees its seats and returns the refund (80% of the amount).
     * Returns -1 if the booking does not exist or was already cancelled.
     */
    public double cancel(String bookingId) throws SQLException {
        try (Connection con = DBConnection.getConnection()) {
            con.setAutoCommit(false);
            try {
                Booking booking = dao.findBooking(con, bookingId, shows, true);
                if (booking == null || booking.isCancelled()) {
                    con.rollback();
                    return -1;
                }
                double refund = booking.getRefundAmount();
                dao.cancelBooking(con, booking, refund);
                con.commit();
                booking.getShow().cancelBooking(booking);   // free the seats in the 2D array too
                booking.markCancelled();
                return refund;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }
}