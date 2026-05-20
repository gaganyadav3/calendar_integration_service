import java.sql.*;
public class CheckConstraints {
    public static void main(String[] args) throws Exception {
        try (Connection conn = DriverManager.getConnection(
                "jdbc:oracle:thin:@//192.168.64.30:1521/lin",
                "ATUL_VERMA_LinDB", "VATGYurlkJHDfJk")) {
            ResultSet rs = conn.createStatement().executeQuery(
                "SELECT TABLE_NAME, CONSTRAINT_NAME, SEARCH_CONDITION " +
                "FROM USER_CONSTRAINTS " +
                "WHERE CONSTRAINT_TYPE = 'C' " +
                "AND TABLE_NAME IN ('SYNC_VENDOR','SYNC_STATUS','CALENDAR_EVENT_STATUS','EVENT_GUEST_RESPONSE','WEBHOOK_STATUS') " +
                "ORDER BY TABLE_NAME, CONSTRAINT_NAME");
            while (rs.next()) {
                System.out.println(rs.getString(1) + " | " + rs.getString(2) + " | " + rs.getString(3));
            }
        }
    }
}
