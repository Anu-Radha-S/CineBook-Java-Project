import java.util.List;

/** Anything that can have seats booked and cancelled. */
public interface Bookable {

    /**
     * Books all the given seats, or none of them (all-or-nothing).
     * @throws IllegalArgumentException if any seat code is invalid or already booked
     */
    void bookSeats(List<String> seatCodes);

    /** Frees every seat held by the given booking. */
    void cancelBooking(Booking booking);
}