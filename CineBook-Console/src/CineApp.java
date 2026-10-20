import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

/** Entry point of CineBook (MySQL version). Contains only the menu and user input. */
public class CineApp {

    private static final Scanner sc = new Scanner(System.in);

    public static void main(String[] args) {
        Theatre theatre = new Theatre("CineBook Cinema");
        try {
            theatre.loadFromDatabase();
        } catch (SQLException e) {
            System.out.println("Could not connect to MySQL: " + e.getMessage());
            System.out.println("Check that MySQL is running, the cinebook database exists, "
                    + "and the user/password in db.properties are correct.");
            return;
        }

        System.out.println("========================================");
        System.out.println("     Welcome to " + theatre.getName());
        System.out.println("     (connected to MySQL)");
        System.out.println("========================================");

        boolean running = true;
        while (running) {
            System.out.println();
            System.out.println("1. Book tickets");
            System.out.println("2. View seat map");
            System.out.println("3. Cancel a booking");
            System.out.println("4. View all bookings");
            System.out.println("5. Exit");
            int choice = readInt("Enter choice: ");
            try {
                switch (choice) {
                    case 1 -> bookTickets(theatre);
                    case 2 -> {
                        Show show = chooseShow(theatre);
                        if (show != null) {
                            show.displaySeatMap();
                        }
                    }
                    case 3 -> cancelBooking(theatre);
                    case 4 -> listBookings(theatre);
                    case 5 -> running = false;
                    default -> System.out.println("Please choose 1-5.");
                }
            } catch (SQLException e) {
                System.out.println("Database error: " + e.getMessage());
            }
        }
        System.out.println("Thank you for using CineBook!");
    }

    private static void bookTickets(Theatre theatre) throws SQLException {
        Show show = chooseShow(theatre);
        if (show == null) {
            return;
        }
        show.displaySeatMap();
        System.out.print("Enter seats (e.g. C3,C4): ");
        String line = sc.nextLine().trim().toUpperCase();
        List<String> seats = new ArrayList<>();
        for (String s : line.split(",")) {
            if (!s.isBlank()) {
                seats.add(s.trim());
            }
        }
        System.out.print("Coupon code (Enter to skip): ");
        String coupon = sc.nextLine().trim();
        if (!coupon.isEmpty() && !Booking.isValidCoupon(coupon)) {
            System.out.println("Coupon '" + coupon + "' is not valid; booking at full price.");
        }
        try {
            Booking booking = theatre.book(show, seats, coupon);
            System.out.println("Booking confirmed and saved to the database!");
            booking.printSummary();
        } catch (IllegalArgumentException e) {
            System.out.println("Booking failed: " + e.getMessage() + " No seats were booked.");
        }
    }

    private static void cancelBooking(Theatre theatre) throws SQLException {
        System.out.print("Enter booking ID (e.g. BK1001): ");
        String id = sc.nextLine().trim();
        Booking booking = theatre.findBooking(id);
        if (booking == null) {
            System.out.println("No booking found with ID " + id);
        } else if (booking.isCancelled()) {
            System.out.println("Booking " + booking.getBookingId() + " is already cancelled.");
        } else {
            double refund = theatre.cancel(id);
            if (refund < 0) {
                System.out.println("Booking " + booking.getBookingId() + " could not be cancelled.");
                return;
            }
            System.out.println("Booking " + booking.getBookingId() + " cancelled. Seats freed: "
                    + String.join(", ", booking.getSeats()));
            System.out.printf("Refund (80%% of Rs. %.2f): Rs. %.2f%n", booking.getAmount(), refund);
        }
    }

    private static void listBookings(Theatre theatre) throws SQLException {
        List<Booking> bookings = theatre.getBookings();
        if (bookings.isEmpty()) {
            System.out.println("No bookings yet.");
            return;
        }
        for (Booking b : bookings) {
            b.printSummary();
        }
    }

    private static Show chooseShow(Theatre theatre) throws SQLException {
        theatre.refreshAllSeats();
        System.out.println("Available shows:");
        for (Show s : theatre.getShows()) {
            System.out.println("  " + s);
        }
        int no = readInt("Choose show number: ");
        Show show = theatre.findShow(no);
        if (show == null) {
            System.out.println("Invalid show number.");
        }
        return show;
    }

    private static int readInt(String prompt) {
        while (true) {
            System.out.print(prompt);
            if (!sc.hasNextLine()) {
                System.exit(0);
            }
            String line = sc.nextLine().trim();
            try {
                return Integer.parseInt(line);
            } catch (NumberFormatException e) {
                System.out.println("Please enter a number.");
            }
        }
    }
}