import java.io.FileInputStream;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Opens connections to the MySQL database.
 * The URL, user and password are read from db.properties (kept out of GitHub by .gitignore).
 */
public class DBConnection {

    private static final String CONFIG_FILE = "db.properties";
    private static final Properties props = new Properties();

    static {
        try (FileInputStream in = new FileInputStream(CONFIG_FILE)) {
            props.load(in);
        } catch (IOException e) {
            System.out.println("Could not read " + CONFIG_FILE + ". Copy db.properties.example to "
                    + CONFIG_FILE + " and put your MySQL password in it.");
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(
                props.getProperty("db.url", "jdbc:mysql://localhost:3306/cinebook"),
                props.getProperty("db.user", "root"),
                props.getProperty("db.password", ""));
    }
}