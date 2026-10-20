import java.util.ArrayList;
import java.util.List;

/**
 * One show of a movie. Each show owns its own 5 x 8 seat grid (FR-8),
 * stored in a 2D char array: 'O' = free, 'X' = booked.
 */
public class Show implements Bookable {

    public static final int ROWS = 5;
    public static final int COLS = 8;
    public static final char FREE = 'O';
    public static final char BOOKED = 'X';

    private final int showNo;
    private final String movieName;
    private final String time;
    private final char[][] seats = new char[ROWS][COLS];

    public Show(int showNo, String movieName, String time) {
        this.showNo = showNo;
        this.movieName = movieName;
        this.time = time;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                seats[r][c] = FREE;
            }
        }
    }

    public int getShowNo() {
        return showNo;
    }

    public String getMovieName() {
        return movieName;
    }

    public String getTime() {
        return time;
    }

    /** Prints the seat map with column numbers on top and row letters on the left (FR-2). */
    public void displaySeatMap() {
        System.out.println("Show: " + movieName + " (" + time + ")");
        System.out.print("     ");
        for (int c = 1; c <= COLS; c++) {
            System.out.printf("%-2d", c);
        }
        System.out.println();
        for (int r = 0; r < ROWS; r++) {
            System.out.printf("  %c  ", (char) ('A' + r));
            for (int c = 0; c < COLS; c++) {
                System.out.printf("%-2c", seats[r][c]);
            }
            System.out.printf("  %s (Rs. %.0f)%n", SeatType.forRow(r).getLabel(), SeatType.forRow(r).getPrice());
        }
        System.out.println("  O = free, X = booked");
    }

    /**
     * Converts a code such as "C3" into {row, col} indexes.
     * Returns null when the code is not a valid seat (for example "Z9" or "A0").
     */
    public static int[] parseSeat(String code) {
        if (code == null) {
            return null;
        }
        code = code.trim().toUpperCase();
        if (code.length() < 2 || code.length() > 3) {
            return null;
        }
        int row = code.charAt(0) - 'A';
        if (row < 0 || row >= ROWS) {
            return null;
        }
        int col;
        try {
            col = Integer.parseInt(code.substring(1)) - 1;
        } catch (NumberFormatException e) {
            return null;
        }
        if (col < 0 || col >= COLS) {
            return null;
        }
        return new int[] {row, col};
    }

    @Override
    public void bookSeats(List<String> seatCodes) {
        if (seatCodes == null || seatCodes.isEmpty()) {
            throw new IllegalArgumentException("No seats entered.");
        }
        // Validate every seat first; only then mark them, so the booking is all-or-nothing.
        List<int[]> positions = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (String code : seatCodes) {
            int[] pos = parseSeat(code);
            if (pos == null) {
                throw new IllegalArgumentException("Invalid seat code: " + code);
            }
            if (seen.contains(code)) {
                throw new IllegalArgumentException("Seat entered twice: " + code);
            }
            if (seats[pos[0]][pos[1]] == BOOKED) {
                throw new IllegalArgumentException("Seat already booked: " + code);
            }
            seen.add(code);
            positions.add(pos);
        }
        for (int[] pos : positions) {
            seats[pos[0]][pos[1]] = BOOKED;
        }
    }

    @Override
    public void cancelBooking(Booking booking) {
        for (String code : booking.getSeats()) {
            int[] pos = parseSeat(code);
            seats[pos[0]][pos[1]] = FREE;
        }
    }

    public int countFreeSeats() {
        int free = 0;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                if (seats[r][c] == FREE) {
                    free++;
                }
            }
        }
        return free;
    }

    @Override
    public String toString() {
        return String.format("%d. %-22s %-9s (%d seats free)", showNo, movieName, time, countFreeSeats());
    }
}
