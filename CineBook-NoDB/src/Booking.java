import java.util.ArrayList;
import java.util.List;

/** A confirmed booking for one show. Booking IDs are generated from a static counter. */
public class Booking {

    public static final String COUPON_CODE = "CINE10";
    public static final double COUPON_DISCOUNT = 0.10;   // 10% off
    public static final double REFUND_RATE = 0.80;       // 80% back on cancellation

    private static int counter = 1000;

    private final String bookingId;
    private final Show show;
    private final List<String> seats;
    private double amount;
    private boolean couponApplied;
    private boolean cancelled;

    public Booking(Show show, List<String> seats) {
        counter++;
        this.bookingId = "BK" + counter;
        this.show = show;
        this.seats = new ArrayList<>(seats);
    }

    /** Total price of all seats, taken from the SeatType enum. */
    public double calculateTotal() {
        double total = 0;
        for (String code : seats) {
            int[] pos = Show.parseSeat(code);
            total += SeatType.forRow(pos[0]).getPrice();
        }
        return total;
    }

    /** Overloaded version: applies the coupon discount when the code is valid (FR-6). */
    public double calculateTotal(String coupon) {
        double total = calculateTotal();
        if (isValidCoupon(coupon)) {
            total = total - total * COUPON_DISCOUNT;
        }
        return total;
    }

    public static boolean isValidCoupon(String coupon) {
        return coupon != null && coupon.trim().equalsIgnoreCase(COUPON_CODE);
    }

    public void setAmount(double amount, boolean couponApplied) {
        this.amount = amount;
        this.couponApplied = couponApplied;
    }

    public double getRefundAmount() {
        return amount * REFUND_RATE;
    }

    public String getBookingId() {
        return bookingId;
    }

    public Show getShow() {
        return show;
    }

    public List<String> getSeats() {
        return seats;
    }

    public double getAmount() {
        return amount;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    public void markCancelled() {
        this.cancelled = true;
    }

    /** "C3, C4 (Premium)" when all seats share a type, otherwise "A1 (Regular), C3 (Premium)". */
    public String describeSeats() {
        SeatType first = SeatType.forRow(Show.parseSeat(seats.get(0))[0]);
        boolean sameType = true;
        for (String code : seats) {
            if (SeatType.forRow(Show.parseSeat(code)[0]) != first) {
                sameType = false;
            }
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < seats.size(); i++) {
            String code = seats.get(i);
            if (i > 0) {
                sb.append(", ");
            }
            sb.append(code);
            if (!sameType) {
                sb.append(" (").append(SeatType.forRow(Show.parseSeat(code)[0]).getLabel()).append(")");
            }
        }
        if (sameType) {
            sb.append(" (").append(first.getLabel()).append(")");
        }
        return sb.toString();
    }

    /** Prints the booking summary (FR-5). */
    public void printSummary() {
        System.out.println("----------------------------------------");
        System.out.println("Booking ID : " + bookingId);
        System.out.println("Show       : " + show.getMovieName() + " (" + show.getTime() + ")");
        System.out.println("Seats      : " + describeSeats());
        System.out.printf("Total      : Rs. %.2f%s%n", amount,
                couponApplied ? " (after " + (int) (COUPON_DISCOUNT * 100) + "% discount)" : "");
        System.out.println("Status     : " + (cancelled ? "CANCELLED" : "BOOKED"));
        System.out.println("----------------------------------------");
    }
}
