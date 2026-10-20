/**
 * Seat categories with their ticket price.
 * Rows A-B are REGULAR, C-D are PREMIUM and E is VIP (FR-3).
 * All prices come from here; booking logic never hard-codes a price.
 */
public enum SeatType {
    REGULAR("Regular", 150.0),
    PREMIUM("Premium", 250.0),
    VIP("VIP", 400.0);

    private final String label;
    private final double price;

    SeatType(String label, double price) {
        this.label = label;
        this.price = price;
    }

    public String getLabel() {
        return label;
    }

    public double getPrice() {
        return price;
    }

    /** Returns the seat type for a row index (0 = A ... 4 = E). */
    public static SeatType forRow(int row) {
        if (row <= 1) {
            return REGULAR;
        } else if (row <= 3) {
            return PREMIUM;
        }
        return VIP;
    }
}