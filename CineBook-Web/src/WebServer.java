import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Entry point of CineBook (website version).
 * Uses Java's built-in HTTP server: serves the pages in the web/ folder
 * and a small JSON API that the pages call to book and cancel tickets.
 */
public class WebServer {

    private static final int PORT = 8080;
    private static final Path WEB_FOLDER = Paths.get("web");

    private static Theatre theatre;

    public static void main(String[] args) throws IOException {
        theatre = new Theatre("CineBook Cinema");
        try {
            theatre.loadFromDatabase();
        } catch (SQLException e) {
            System.out.println("Could not connect to MySQL: " + e.getMessage());
            System.out.println("Check that MySQL is running, the cinebook database exists, "
                    + "and the user/password in db.properties are correct.");
            return;
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(PORT), 0);
        server.createContext("/api/shows", WebServer::handleShows);
        server.createContext("/api/seats", WebServer::handleSeats);
        server.createContext("/api/book", WebServer::handleBook);
        server.createContext("/api/cancel", WebServer::handleCancel);
        server.createContext("/api/bookings", WebServer::handleBookings);
        server.createContext("/api/report", WebServer::handleReport);
        server.createContext("/", WebServer::handleStaticFile);
        server.start();

        System.out.println("CineBook website is running (connected to MySQL).");
        System.out.println("Open http://localhost:" + PORT + " in your browser.");
        System.out.println("Press Ctrl+C here to stop the server.");
    }

    // ------------------------------------------------------------------ API

    /** GET /api/shows -> list of shows with free seat counts (FR-1). */
    private static void handleShows(HttpExchange ex) throws IOException {
        try {
            theatre.refreshAllSeats();
            List<String> items = new ArrayList<>();
            for (Show s : theatre.getShows()) {
                items.add("{\"id\":" + s.getShowNo()
                        + ",\"movie\":" + quote(s.getMovieName())
                        + ",\"time\":" + quote(s.getTime())
                        + "," + detailsJson(s)
                        + ",\"free\":" + s.countFreeSeats() + "}");
            }
            sendJson(ex, 200, "[" + String.join(",", items) + "]");
        } catch (SQLException e) {
            sendError(ex, 500, "Database error: " + e.getMessage());
        }
    }

    /** GET /api/seats?show=1 -> the 5 x 8 seat grid of one show (FR-2, FR-3). */
    private static void handleSeats(HttpExchange ex) throws IOException {
        Map<String, String> params = parseParams(ex.getRequestURI().getRawQuery());
        Show show = findShow(params.get("show"));
        if (show == null) {
            sendError(ex, 404, "Show not found.");
            return;
        }
        try {
            theatre.refreshSeats(show);
        } catch (SQLException e) {
            sendError(ex, 500, "Database error: " + e.getMessage());
            return;
        }
        List<String> rows = new ArrayList<>();
        for (int r = 0; r < Show.ROWS; r++) {
            StringBuilder seats = new StringBuilder();
            for (int c = 0; c < Show.COLS; c++) {
                seats.append(show.getSeat(r, c));
            }
            SeatType type = SeatType.forRow(r);
            rows.add("{\"row\":\"" + (char) ('A' + r) + "\""
                    + ",\"seats\":\"" + seats + "\""
                    + ",\"type\":" + quote(type.getLabel())
                    + ",\"price\":" + type.getPrice() + "}");
        }
        sendJson(ex, 200, "{\"id\":" + show.getShowNo()
                + ",\"movie\":" + quote(show.getMovieName())
                + ",\"time\":" + quote(show.getTime())
                + "," + detailsJson(show)
                + ",\"rows\":[" + String.join(",", rows) + "]}");
    }

    /** POST /api/book (show, seats, coupon) -> booking summary (FR-4, FR-5, FR-6). */
    private static void handleBook(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            sendError(ex, 405, "Use POST.");
            return;
        }
        Map<String, String> params = parseParams(readBody(ex));
        Show show = findShow(params.get("show"));
        if (show == null) {
            sendError(ex, 404, "Show not found.");
            return;
        }
        List<String> seats = new ArrayList<>();
        for (String s : params.getOrDefault("seats", "").toUpperCase().split(",")) {
            if (!s.isBlank()) {
                seats.add(s.trim());
            }
        }
        String coupon = params.getOrDefault("coupon", "").trim();
        try {
            Booking booking = theatre.book(show, seats, coupon);
            sendJson(ex, 200, bookingJson(booking, coupon.isEmpty() || booking.isCouponApplied()
                    ? "" : "Coupon '" + coupon + "' is not valid; booked at full price."));
        } catch (IllegalArgumentException e) {
            sendError(ex, 400, e.getMessage() + " No seats were booked.");
        } catch (SQLException e) {
            sendError(ex, 500, "Database error: " + e.getMessage());
        }
    }

    /** POST /api/cancel (id) -> refund amount (FR-7). */
    private static void handleCancel(HttpExchange ex) throws IOException {
        if (!"POST".equals(ex.getRequestMethod())) {
            sendError(ex, 405, "Use POST.");
            return;
        }
        String id = parseParams(readBody(ex)).getOrDefault("id", "").trim();
        try {
            Booking booking = theatre.findBooking(id);
            if (booking == null) {
                sendError(ex, 404, "No booking found with ID " + id + ".");
                return;
            }
            if (booking.isCancelled()) {
                sendError(ex, 400, "Booking " + booking.getBookingId() + " is already cancelled.");
                return;
            }
            double refund = theatre.cancel(id);
            if (refund < 0) {
                sendError(ex, 400, "Booking " + booking.getBookingId() + " could not be cancelled.");
                return;
            }
            sendJson(ex, 200, "{\"bookingId\":" + quote(booking.getBookingId())
                    + ",\"seats\":" + quote(String.join(", ", booking.getSeats()))
                    + ",\"amount\":" + booking.getAmount()
                    + ",\"refund\":" + refund + "}");
        } catch (SQLException e) {
            sendError(ex, 500, "Database error: " + e.getMessage());
        }
    }

    /** GET /api/bookings -> every booking, newest last. */
    private static void handleBookings(HttpExchange ex) throws IOException {
        try {
            List<String> items = new ArrayList<>();
            for (Booking b : theatre.getBookings()) {
                items.add(bookingJson(b, ""));
            }
            sendJson(ex, 200, "[" + String.join(",", items) + "]");
        } catch (SQLException e) {
            sendError(ex, 500, "Database error: " + e.getMessage());
        }
    }

    /** GET /api/report -> per-show totals and seats sold per seat type. */
    private static void handleReport(HttpExchange ex) throws IOException {
        ReportDAO reports = new ReportDAO();
        try (Connection con = DBConnection.getConnection()) {
            List<String> shows = new ArrayList<>();
            for (ReportDAO.ShowReport r : reports.showReport(con)) {
                shows.add("{\"id\":" + r.showId
                        + ",\"movie\":" + quote(r.movie)
                        + ",\"time\":" + quote(r.time)
                        + ",\"seatsBooked\":" + r.seatsBooked
                        + ",\"seatsFree\":" + r.seatsFree
                        + ",\"activeBookings\":" + r.activeBookings
                        + ",\"cancelledBookings\":" + r.cancelledBookings
                        + ",\"revenue\":" + r.revenue + "}");
            }
            List<String> types = new ArrayList<>();
            for (Map.Entry<String, Integer> e : reports.seatsSoldByType(con).entrySet()) {
                SeatType type = SeatType.valueOf(e.getKey());
                types.add("{\"type\":" + quote(type.getLabel())
                        + ",\"price\":" + type.getPrice()
                        + ",\"sold\":" + e.getValue() + "}");
            }
            sendJson(ex, 200, "{\"shows\":[" + String.join(",", shows) + "]"
                    + ",\"seatTypes\":[" + String.join(",", types) + "]}");
        } catch (SQLException e) {
            sendError(ex, 500, "Database error: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------ web pages

    /** Serves the pages, style.css, app.js and poster images from the web/ folder. */
    private static void handleStaticFile(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        if (path.equals("/")) {
            path = "/index.html";
        }
        Path file = WEB_FOLDER.resolve(path.substring(1)).normalize();
        if (!file.startsWith(WEB_FOLDER) || !Files.isRegularFile(file)) {
            send(ex, 404, "text/plain; charset=utf-8", "404 Not Found".getBytes(StandardCharsets.UTF_8));
            return;
        }
        String type = "text/plain; charset=utf-8";
        if (path.endsWith(".html")) {
            type = "text/html; charset=utf-8";
        } else if (path.endsWith(".css")) {
            type = "text/css; charset=utf-8";
        } else if (path.endsWith(".js")) {
            type = "application/javascript; charset=utf-8";
        } else if (path.endsWith(".jpg") || path.endsWith(".jpeg")) {
            type = "image/jpeg";
        } else if (path.endsWith(".png")) {
            type = "image/png";
        } else if (path.endsWith(".webp")) {
            type = "image/webp";
        }
        send(ex, 200, type, Files.readAllBytes(file));
    }

    // -------------------------------------------------------------- helpers

    private static Show findShow(String id) {
        try {
            return theatre.findShow(Integer.parseInt(id));
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }

    /** "genre", "duration", "certificate" and "poster" fields for a show. */
    private static String detailsJson(Show s) {
        return "\"genre\":" + quote(s.getGenre())
                + ",\"duration\":" + s.getDurationMin()
                + ",\"certificate\":" + quote(s.getCertificate())
                + ",\"poster\":" + quote(s.getPoster());
    }

    private static String bookingJson(Booking b, String note) {
        return "{\"bookingId\":" + quote(b.getBookingId())
                + ",\"movie\":" + quote(b.getShow().getMovieName())
                + ",\"time\":" + quote(b.getShow().getTime())
                + ",\"seats\":" + quote(b.describeSeats())
                + ",\"amount\":" + b.getAmount()
                + ",\"couponApplied\":" + b.isCouponApplied()
                + ",\"status\":" + quote(b.isCancelled() ? "CANCELLED" : "BOOKED")
                + ",\"note\":" + quote(note) + "}";
    }

    /** Turns "a=1&b=x%20y" into a map {a=1, b=x y}. */
    private static Map<String, String> parseParams(String text) {
        Map<String, String> map = new HashMap<>();
        if (text == null || text.isEmpty()) {
            return map;
        }
        for (String pair : text.split("&")) {
            String[] kv = pair.split("=", 2);
            String key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
            String value = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
            map.put(key, value);
        }
        return map;
    }

    private static String readBody(HttpExchange ex) throws IOException {
        return new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    }

    /** Wraps text in double quotes for JSON, escaping special characters. */
    private static String quote(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (char ch : s.toCharArray()) {
            switch (ch) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '<' -> sb.append("\\u003c");
                default -> sb.append(ch);
            }
        }
        return sb.append('"').toString();
    }

    private static void sendJson(HttpExchange ex, int status, String json) throws IOException {
        send(ex, status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8));
    }

    private static void sendError(HttpExchange ex, int status, String message) throws IOException {
        sendJson(ex, status, "{\"error\":" + quote(message) + "}");
    }

    private static void send(HttpExchange ex, int status, String type, byte[] body) throws IOException {
        ex.getResponseHeaders().set("Content-Type", type);
        ex.sendResponseHeaders(status, body.length);
        try (OutputStream out = ex.getResponseBody()) {
            out.write(body);
        }
    }
}
