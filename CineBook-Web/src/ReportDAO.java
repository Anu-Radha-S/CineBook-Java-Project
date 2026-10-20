import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Summary queries for the Reports page (COUNT, SUM and GROUP BY). */
public class ReportDAO {

    /** One row of the per-show report. */
    public static class ShowReport {
        public int showId;
        public String movie;
        public String time;
        public int seatsBooked;
        public int seatsFree;
        public int activeBookings;
        public int cancelledBookings;
        public double revenue;
    }

    /**
     * Seats booked, active and cancelled bookings, and revenue for every show.
     * Revenue = full amount of active bookings + the 20% kept from cancelled ones.
     */
    public List<ShowReport> showReport(Connection con) throws SQLException {
        String sql = "SELECT s.show_id, s.movie_name, s.show_time, "
                + "  (SELECT COUNT(*) FROM seats st WHERE st.show_id = s.show_id AND st.status = 'BOOKED') AS seats_booked, "
                + "  (SELECT COUNT(*) FROM seats st WHERE st.show_id = s.show_id AND st.status = 'FREE') AS seats_free, "
                + "  COUNT(CASE WHEN b.status = 'BOOKED' THEN 1 END) AS active_bookings, "
                + "  COUNT(CASE WHEN b.status = 'CANCELLED' THEN 1 END) AS cancelled_bookings, "
                + "  COALESCE(SUM(CASE WHEN b.status = 'BOOKED' THEN b.total_amount "
                + "                    ELSE b.total_amount - b.refund_amount END), 0) AS revenue "
                + "FROM shows s LEFT JOIN bookings b ON b.show_id = s.show_id "
                + "GROUP BY s.show_id, s.movie_name, s.show_time "
                + "ORDER BY s.show_id";
        List<ShowReport> list = new ArrayList<>();
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                ShowReport r = new ShowReport();
                r.showId = rs.getInt("show_id");
                r.movie = rs.getString("movie_name");
                r.time = rs.getString("show_time");
                r.seatsBooked = rs.getInt("seats_booked");
                r.seatsFree = rs.getInt("seats_free");
                r.activeBookings = rs.getInt("active_bookings");
                r.cancelledBookings = rs.getInt("cancelled_bookings");
                r.revenue = rs.getDouble("revenue");
                list.add(r);
            }
        }
        return list;
    }

    /** Number of booked seats per seat type across all shows, e.g. {REGULAR=4, PREMIUM=2, VIP=0}. */
    public Map<String, Integer> seatsSoldByType(Connection con) throws SQLException {
        Map<String, Integer> map = new LinkedHashMap<>();
        map.put("REGULAR", 0);
        map.put("PREMIUM", 0);
        map.put("VIP", 0);
        String sql = "SELECT seat_type, COUNT(*) AS sold FROM seats WHERE status = 'BOOKED' GROUP BY seat_type";
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                map.put(rs.getString("seat_type"), rs.getInt("sold"));
            }
        }
        return map;
    }
}
