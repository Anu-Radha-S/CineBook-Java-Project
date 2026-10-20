import java.util.ArrayList;
import java.util.List;

/** The cinema: holds the list of shows and all bookings (composition). */
public class Theatre {

    private final String name;
    private final List<Show> shows = new ArrayList<>();
    private final List<Booking> bookings = new ArrayList<>();

    public Theatre(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void addShow(Show show) {
        shows.add(show);
    }

    public List<Show> getShows() {
        return shows;
    }

    public List<Booking> getBookings() {
        return bookings;
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

    /**
     * Books the seats on the show and records the booking.
     * @throws IllegalArgumentException if any seat is invalid or taken (nothing is booked then)
     */
    public Booking book(Show show, List<String> seatCodes, String coupon) {
        show.bookSeats(seatCodes);   // throws before anything changes if a seat is bad
        Booking booking = new Booking(show, seatCodes);
        boolean useCoupon = Booking.isValidCoupon(coupon);
        double total = useCoupon ? booking.calculateTotal(coupon) : booking.calculateTotal();
        booking.setAmount(total, useCoupon);
        bookings.add(booking);
        return booking;
    }

    /** Returns the active booking with this ID, or null. */
    public Booking findBooking(String bookingId) {
        for (Booking b : bookings) {
            if (b.getBookingId().equalsIgnoreCase(bookingId.trim())) {
                return b;
            }
        }
        return null;
    }

    /** Cancels the booking, frees its seats and returns the refund (80% of the amount). */
    public double cancel(Booking booking) {
        booking.getShow().cancelBooking(booking);
        booking.markCancelled();
        return booking.getRefundAmount();
    }
}
