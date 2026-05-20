import java.sql.*;
import java.util.*;

public class CreateSequences {
    public static void main(String[] args) throws Exception {
        String url      = "jdbc:oracle:thin:@//192.168.64.30:1521/lin";
        String user     = "ATUL_VERMA_LinDB";
        String password = "VATGYurlkJHDfJk";

        String[] needed = {
            "CALENDAR_EVENT_STATUS_SEQ",
            "CUSTOMER_USER_SYNC_SEQ",
            "CU_SYNC_CALENDAR_SEQ",
            "CU_SYNC_CALENDAR_EVENT_SEQ",
            "CU_SYNC_CALENDAR_EVENT_GUEST_SEQ",
            "WEBHOOK_STATUS_SEQ",
            "SYNC_STATUS_SEQ",
            "SYNC_VENDOR_SEQ",
            "EVENT_GUEST_RESPONSE_SEQ",
            "OM_EVENT_SEQ",
            "OM_EVENT_GUEST_SEQ",
            "CU_SYNC_CALENDAR_WEBHOOK_SEQ",
            "EVENT_REMINDER_SEQ",
            "CUSTOMER_USER_SEQ"
        };

        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("Connected to Oracle.");

            // Find existing sequences
            Set<String> existing = new HashSet<>();
            try (ResultSet rs = conn.createStatement()
                    .executeQuery("SELECT SEQUENCE_NAME FROM USER_SEQUENCES")) {
                while (rs.next()) existing.add(rs.getString(1).toUpperCase());
            }
            System.out.println("Existing sequences: " + existing);

            // Create missing sequences
            for (String seq : needed) {
                if (existing.contains(seq.toUpperCase())) {
                    System.out.println("SKIP (exists): " + seq);
                } else {
                    String sql = "CREATE SEQUENCE " + seq + " START WITH 1 INCREMENT BY 1 NOCACHE NOCYCLE";
                    try {
                        conn.createStatement().execute(sql);
                        System.out.println("CREATED: " + seq);
                    } catch (SQLException e) {
                        System.out.println("ERROR creating " + seq + ": " + e.getMessage());
                    }
                }
            }
            System.out.println("Done.");
        }
    }
}
