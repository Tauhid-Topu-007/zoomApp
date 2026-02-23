package org.example.zoom;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Database {

    // ✅ Updated connection info for zoom_user
    public static final String URL = "jdbc:mysql://localhost:3306/zoom_app?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    public static final String USER = "zoom_user";
    public static final String PASSWORD = "zoom123";

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver"); // load MySQL driver
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    // Central connection method
    private static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    /* ==============================
       USER MANAGEMENT - ENHANCED
     ============================== */
    public static boolean registerUser(String username, String password) {
        String sql = "INSERT INTO users(username, password) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.executeUpdate();

            // Log the registration
            logSecurityEvent(username, "USER_REGISTERED", "New user registered successfully");
            System.out.println("✅ User registered: " + username);
            return true;
        } catch (SQLException e) {
            System.err.println("❌ registerUser error: " + e.getMessage());
            return false;
        }
    }

    public static boolean authenticateUser(String username, String password) {
        String sql = "SELECT * FROM users WHERE username = ? AND password = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            ResultSet rs = stmt.executeQuery();
            boolean authenticated = rs.next();

            if (authenticated) {
                logSecurityEvent(username, "USER_LOGIN", "User logged in successfully");
                System.out.println("✅ User authenticated: " + username);
            } else {
                System.out.println("❌ Authentication failed for: " + username);
            }

            return authenticated;
        } catch (SQLException e) {
            System.err.println("❌ authenticateUser error: " + e.getMessage());
            return false;
        }
    }

    // NEW: Get all registered users with detailed information
    public static List<UserDetail> getAllUserDetails() {
        List<UserDetail> users = new ArrayList<>();
        String sql = "SELECT username, created_at, " +
                "(SELECT COUNT(*) FROM meetings WHERE username = users.username) as meeting_count, " +
                "(SELECT COUNT(*) FROM contacts WHERE username = users.username) as contact_count, " +
                "(SELECT MAX(last_used) FROM server_config WHERE username = users.username) as last_active " +
                "FROM users ORDER BY username";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                users.add(new UserDetail(
                        rs.getString("username"),
                        rs.getTimestamp("created_at"),
                        rs.getInt("meeting_count"),
                        rs.getInt("contact_count"),
                        rs.getTimestamp("last_active")
                ));
            }
            System.out.println("✅ Loaded " + users.size() + " user details from database");
        } catch (SQLException e) {
            System.err.println("❌ getAllUserDetails error: " + e.getMessage());
        }
        return users;
    }

    // NEW: Get user count for statistics
    public static int getUserCount() {
        String sql = "SELECT COUNT(*) as user_count FROM users";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt("user_count");
            }
        } catch (SQLException e) {
            System.err.println("❌ getUserCount error: " + e.getMessage());
        }
        return 0;
    }

    // NEW: Search users by username pattern
    public static List<String> searchUsers(String searchPattern) {
        List<String> users = new ArrayList<>();
        String sql = "SELECT username FROM users WHERE username LIKE ? ORDER BY username";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, "%" + searchPattern + "%");
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                users.add(rs.getString("username"));
            }
            System.out.println("✅ Found " + users.size() + " users matching: " + searchPattern);
        } catch (SQLException e) {
            System.err.println("❌ searchUsers error: " + e.getMessage());
        }
        return users;
    }

    // NEW: Delete user account (admin function)
    public static boolean deleteUser(String username) {
        String deleteServerConfig = "DELETE FROM server_config WHERE username = ?";
        String deleteUserPreferences = "DELETE FROM user_preferences WHERE username = ?";
        String deleteContacts = "DELETE FROM contacts WHERE username = ?";
        String deleteMeetings = "DELETE FROM meetings WHERE username = ?";
        String deleteUser = "DELETE FROM users WHERE username = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false); // Start transaction

            try (PreparedStatement stmt1 = conn.prepareStatement(deleteServerConfig);
                 PreparedStatement stmt2 = conn.prepareStatement(deleteUserPreferences);
                 PreparedStatement stmt3 = conn.prepareStatement(deleteContacts);
                 PreparedStatement stmt4 = conn.prepareStatement(deleteMeetings);
                 PreparedStatement stmt5 = conn.prepareStatement(deleteUser)) {

                // Delete user data from all tables
                stmt1.setString(1, username);
                stmt1.executeUpdate();

                stmt2.setString(1, username);
                stmt2.executeUpdate();

                stmt3.setString(1, username);
                stmt3.executeUpdate();

                stmt4.setString(1, username);
                stmt4.executeUpdate();

                stmt5.setString(1, username);
                int rowsDeleted = stmt5.executeUpdate();

                conn.commit();

                if (rowsDeleted > 0) {
                    logSecurityEvent("SYSTEM", "USER_DELETED", "User account deleted: " + username);
                    System.out.println("✅ User deleted: " + username);
                    return true;
                } else {
                    System.out.println("❌ No user found: " + username);
                    return false;
                }

            } catch (SQLException e) {
                conn.rollback();
                System.err.println("❌ deleteUser transaction error: " + e.getMessage());
                return false;
            }
        } catch (SQLException e) {
            System.err.println("❌ deleteUser error: " + e.getMessage());
            return false;
        }
    }

    // Enhanced password update with validation
    public static boolean updatePassword(String username, String newPassword) {
        if (newPassword == null || newPassword.length() < 3) {
            System.err.println("❌ Password must be at least 3 characters long");
            return false;
        }

        String sql = "UPDATE users SET password = ? WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, newPassword);
            stmt.setString(2, username);
            int rowsUpdated = stmt.executeUpdate();

            if (rowsUpdated > 0) {
                System.out.println("✅ Password updated successfully for user: " + username);

                // Log the password change for security
                logSecurityEvent(username, "PASSWORD_CHANGED", "User changed their password");
                return true;
            } else {
                System.err.println("❌ No user found with username: " + username);
                return false;
            }
        } catch (SQLException e) {
            System.err.println("❌ updatePassword error: " + e.getMessage());
            return false;
        }
    }

    // NEW: Enhanced password reset functionality
    public static boolean resetPassword(String username, String newPassword) {
        return updatePassword(username, newPassword);
    }

    // NEW: Get user profile information
    public static UserProfile getUserProfile(String username) {
        String sql = "SELECT username, created_at FROM users WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new UserProfile(
                        rs.getString("username"),
                        rs.getTimestamp("created_at")
                );
            }
        } catch (SQLException e) {
            System.err.println("❌ getUserProfile error: " + e.getMessage());
        }
        return null;
    }

    // NEW: Security event logging
    private static void logSecurityEvent(String username, String eventType, String description) {
        String sql = "INSERT INTO security_logs (username, event_type, description, ip_address, user_agent) VALUES (?, ?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, eventType);
            stmt.setString(3, description);
            stmt.setString(4, "127.0.0.1"); // In real app, get from request
            stmt.setString(5, "JavaFX Client"); // In real app, get from request
            stmt.executeUpdate();
        } catch (SQLException e) {
            System.err.println("❌ logSecurityEvent error: " + e.getMessage());
        }
    }

    public static boolean updateUsername(String oldUsername, String newUsername) {
        String updateUsers = "UPDATE users SET username = ? WHERE username = ?";
        String updateMeetings = "UPDATE meetings SET username = ? WHERE username = ?";
        String updateContacts = "UPDATE contacts SET username = ? WHERE username = ?";
        String updateServerConfig = "UPDATE server_config SET username = ? WHERE username = ?";
        String updateUserPreferences = "UPDATE user_preferences SET username = ? WHERE username = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false); // start transaction

            try (PreparedStatement stmt1 = conn.prepareStatement(updateUsers);
                 PreparedStatement stmt2 = conn.prepareStatement(updateMeetings);
                 PreparedStatement stmt3 = conn.prepareStatement(updateContacts);
                 PreparedStatement stmt4 = conn.prepareStatement(updateServerConfig);
                 PreparedStatement stmt5 = conn.prepareStatement(updateUserPreferences)) {

                stmt1.setString(1, newUsername);
                stmt1.setString(2, oldUsername);
                stmt1.executeUpdate();

                stmt2.setString(1, newUsername);
                stmt2.setString(2, oldUsername);
                stmt2.executeUpdate();

                stmt3.setString(1, newUsername);
                stmt3.setString(2, oldUsername);
                stmt3.executeUpdate();

                stmt4.setString(1, newUsername);
                stmt4.setString(2, oldUsername);
                stmt4.executeUpdate();

                stmt5.setString(1, newUsername);
                stmt5.setString(2, oldUsername);
                stmt5.executeUpdate();

                conn.commit();

                // Log the username change
                logSecurityEvent(newUsername, "USERNAME_CHANGED", "User changed username from " + oldUsername + " to " + newUsername);
                return true;

            } catch (SQLException e) {
                conn.rollback();
                System.err.println("❌ updateUsername transaction error: " + e.getMessage());
                return false;
            }
        } catch (SQLException e) {
            System.err.println("❌ updateUsername error: " + e.getMessage());
            return false;
        }
    }

    public static boolean usernameExists(String username) {
        String sql = "SELECT username FROM users WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            return rs.next();
        } catch (SQLException e) {
            System.err.println("❌ usernameExists error: " + e.getMessage());
            return false;
        }
    }

    // NEW: Get all users (for admin purposes)
    public static List<String> getAllUsers() {
        List<String> users = new ArrayList<>();
        String sql = "SELECT username FROM users ORDER BY username";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                users.add(rs.getString("username"));
            }
            System.out.println("✅ Loaded " + users.size() + " users from database");
        } catch (SQLException e) {
            System.err.println("❌ getAllUsers error: " + e.getMessage());
        }
        return users;
    }

    /* ==============================
       SERVER CONFIGURATION MANAGEMENT
     ============================== */
    public static boolean saveServerConfig(String username, String serverIp, String serverPort) {
        String sql = "INSERT INTO server_config (username, server_ip, server_port, last_used) " +
                "VALUES (?, ?, ?, CURRENT_TIMESTAMP) " +
                "ON DUPLICATE KEY UPDATE server_ip = ?, server_port = ?, last_used = CURRENT_TIMESTAMP";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, serverIp);
            stmt.setString(3, serverPort);
            stmt.setString(4, serverIp);
            stmt.setString(5, serverPort);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("❌ saveServerConfig error: " + e.getMessage());
            return false;
        }
    }

    public static ServerConfig getServerConfig(String username) {
        String sql = "SELECT server_ip, server_port FROM server_config WHERE username = ? ORDER BY last_used DESC LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new ServerConfig(
                        rs.getString("server_ip"),
                        rs.getString("server_port")
                );
            }
        } catch (SQLException e) {
            System.err.println("❌ getServerConfig error: " + e.getMessage());
        }
        return null;
    }

    public static List<ServerConfig> getServerHistory(String username) {
        List<ServerConfig> history = new ArrayList<>();
        String sql = "SELECT server_ip, server_port, last_used FROM server_config WHERE username = ? ORDER BY last_used DESC";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                history.add(new ServerConfig(
                        rs.getString("server_ip"),
                        rs.getString("server_port"),
                        rs.getTimestamp("last_used")
                ));
            }
        } catch (SQLException e) {
            System.err.println("❌ getServerHistory error: " + e.getMessage());
        }
        return history;
    }

    public static boolean deleteServerConfig(String username, String serverIp, String serverPort) {
        String sql = "DELETE FROM server_config WHERE username = ? AND server_ip = ? AND server_port = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, serverIp);
            stmt.setString(3, serverPort);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ deleteServerConfig error: " + e.getMessage());
            return false;
        }
    }

    /* ==============================
       USER PREFERENCES
     ============================== */
    public static boolean saveUserPreference(String username, String preferenceKey, String preferenceValue) {
        String sql = "INSERT INTO user_preferences (username, preference_key, preference_value) " +
                "VALUES (?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE preference_value = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, preferenceKey);
            stmt.setString(3, preferenceValue);
            stmt.setString(4, preferenceValue);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("❌ saveUserPreference error: " + e.getMessage());
            return false;
        }
    }

    public static String getUserPreference(String username, String preferenceKey) {
        String sql = "SELECT preference_value FROM user_preferences WHERE username = ? AND preference_key = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, preferenceKey);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getString("preference_value");
            }
        } catch (SQLException e) {
            System.err.println("❌ getUserPreference error: " + e.getMessage());
        }
        return null;
    }

    // NEW: Get all user preferences
    public static List<UserPreference> getAllUserPreferences(String username) {
        List<UserPreference> preferences = new ArrayList<>();
        String sql = "SELECT preference_key, preference_value FROM user_preferences WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                preferences.add(new UserPreference(
                        rs.getString("preference_key"),
                        rs.getString("preference_value")
                ));
            }
        } catch (SQLException e) {
            System.err.println("❌ getAllUserPreferences error: " + e.getMessage());
        }
        return preferences;
    }

    /* ==============================
       MEETING MANAGEMENT - ENHANCED FOR CROSS-DEVICE SYNC
     ============================== */

    /**
     * FIXED: Ensure meeting table structure exists
     */
    private static void ensureMeetingTableStructure() {
        try (Connection conn = getConnection()) {
            // Check if meetings table exists
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet tables = metaData.getTables(null, null, "meetings", null);

            if (!tables.next()) {
                // Create meetings table
                String createSql = "CREATE TABLE meetings (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "username VARCHAR(50) NOT NULL, " +
                        "meeting_id VARCHAR(20), " +
                        "title VARCHAR(255), " +
                        "description TEXT, " +
                        "date DATE, " +
                        "time TIME, " +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ")";
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute(createSql);
                    System.out.println("✅ Created meetings table");
                }
            } else {
                // Check for meeting_id column
                ResultSet columns = metaData.getColumns(null, null, "meetings", "meeting_id");
                if (!columns.next()) {
                    // Add meeting_id column if it doesn't exist
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("ALTER TABLE meetings ADD COLUMN meeting_id VARCHAR(20)");
                        System.out.println("✅ Added meeting_id column to meetings table");
                    }
                }

                // Check for description column
                ResultSet descColumns = metaData.getColumns(null, null, "meetings", "description");
                if (!descColumns.next()) {
                    try (Statement stmt = conn.createStatement()) {
                        stmt.execute("ALTER TABLE meetings ADD COLUMN description TEXT");
                        System.out.println("✅ Added description column to meetings table");
                    }
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ ensureMeetingTableStructure error: " + e.getMessage());
        }
    }

    /**
     * ENHANCED: Store meeting with proper meeting ID - ensures cross-device visibility
     */
    public static boolean saveMeetingWithId(String meetingId, String hostUsername, String title, String description) {
        System.out.println("📝 Saving meeting with ID: " + meetingId + " host: " + hostUsername);

        // First ensure meetings table has the right structure
        ensureMeetingTableStructure();

        // Check if meeting already exists
        if (meetingExists(meetingId)) {
            System.out.println("ℹ️ Meeting already exists in database: " + meetingId);
            // Still ensure participant record exists
            addParticipant(meetingId, hostUsername);
            return true;
        }

        Connection conn = null;
        try {
            conn = getConnection();
            conn.setAutoCommit(false); // Start transaction

            // Try to save with meeting_id column
            String sql = "INSERT INTO meetings (meeting_id, username, title, description, date, time, created_at) " +
                    "VALUES (?, ?, ?, ?, CURDATE(), CURTIME(), CURRENT_TIMESTAMP)";

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, meetingId);
                stmt.setString(2, hostUsername);
                stmt.setString(3, title != null ? title : "Meeting " + meetingId);
                stmt.setString(4, description != null ? description : "Meeting created by " + hostUsername);
                stmt.executeUpdate();
                System.out.println("✅ Meeting saved with ID: " + meetingId + " by host: " + hostUsername);
            }

            // Also add host as participant
            addParticipantTransaction(conn, meetingId, hostUsername);

            conn.commit();
            System.out.println("✅ Transaction committed for meeting: " + meetingId);
            return true;

        } catch (SQLException e) {
            System.err.println("❌ saveMeetingWithId error: " + e.getMessage());
            if (conn != null) {
                try {
                    conn.rollback();
                    System.err.println("⚠️ Transaction rolled back");
                } catch (SQLException ex) {
                    System.err.println("❌ Rollback failed: " + ex.getMessage());
                }
            }

            // Try alternative approach - save to participants table at minimum
            try {
                boolean participantAdded = addParticipant(meetingId, hostUsername);
                if (participantAdded) {
                    System.out.println("✅ At least added participant record for meeting: " + meetingId);
                    return true;
                }
            } catch (Exception e2) {
                System.err.println("❌ Alternative save also failed: " + e2.getMessage());
            }
            return false;
        } finally {
            if (conn != null) {
                try {
                    conn.setAutoCommit(true);
                    conn.close();
                } catch (SQLException e) {
                    System.err.println("❌ Error closing connection: " + e.getMessage());
                }
            }
        }
    }

    /**
     * NEW: Get all active meetings from database (for cross-device sync)
     */
    public static List<MeetingInfo> getAllMeetings() {
        List<MeetingInfo> meetings = new ArrayList<>();
        String sql = "SELECT DISTINCT m.meeting_id, m.username as host, " +
                "(SELECT COUNT(*) FROM meeting_participants WHERE meeting_id = m.meeting_id) as participant_count, " +
                "m.created_at " +
                "FROM meetings m " +
                "WHERE m.meeting_id IS NOT NULL " +
                "ORDER BY m.created_at DESC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String meetingId = rs.getString("meeting_id");
                String host = rs.getString("host");
                int participantCount = rs.getInt("participant_count");
                Timestamp createdAt = rs.getTimestamp("created_at");

                meetings.add(new MeetingInfo(meetingId, host, participantCount, createdAt));
            }
            System.out.println("✅ Loaded " + meetings.size() + " meetings from database");
        } catch (SQLException e) {
            System.err.println("❌ getAllMeetings error: " + e.getMessage());
        }
        return meetings;
    }

    /**
     * NEW: Get meetings created in last N hours (for active meetings)
     */
    public static List<MeetingInfo> getRecentMeetings(int hours) {
        List<MeetingInfo> meetings = new ArrayList<>();
        String sql = "SELECT DISTINCT m.meeting_id, m.username as host, " +
                "(SELECT COUNT(*) FROM meeting_participants WHERE meeting_id = m.meeting_id) as participant_count, " +
                "m.created_at " +
                "FROM meetings m " +
                "WHERE m.meeting_id IS NOT NULL " +
                "AND m.created_at > DATE_SUB(NOW(), INTERVAL ? HOUR) " +
                "ORDER BY m.created_at DESC";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, hours);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String meetingId = rs.getString("meeting_id");
                String host = rs.getString("host");
                int participantCount = rs.getInt("participant_count");
                Timestamp createdAt = rs.getTimestamp("created_at");

                meetings.add(new MeetingInfo(meetingId, host, participantCount, createdAt));
            }
            System.out.println("✅ Loaded " + meetings.size() + " recent meetings from last " + hours + " hours");
        } catch (SQLException e) {
            System.err.println("❌ getRecentMeetings error: " + e.getMessage());
        }
        return meetings;
    }

    /**
     * ENHANCED: Check if meeting exists across all possible tables
     */
    public static boolean meetingExists(String meetingId) {
        System.out.println("🔍 Database checking if meeting exists: " + meetingId);

        if (meetingId == null || meetingId.trim().isEmpty()) {
            System.err.println("❌ Invalid meeting ID (null or empty)");
            return false;
        }

        // First check meeting_participants table (most reliable)
        String sqlParticipants = "SELECT COUNT(*) FROM meeting_participants WHERE meeting_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlParticipants)) {
            stmt.setString(1, meetingId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                System.out.println("✅ Meeting found in participants table: " + meetingId);
                return true;
            }
        } catch (SQLException e) {
            System.err.println("❌ meetingExists (participants) error: " + e.getMessage());
        }

        // Check meetings table with meeting_id column
        String sqlMeetings = "SELECT COUNT(*) FROM meetings WHERE meeting_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlMeetings)) {
            stmt.setString(1, meetingId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                System.out.println("✅ Meeting found in meetings table: " + meetingId);
                return true;
            }
        } catch (SQLException e) {
            System.err.println("❌ meetingExists (meetings) error: " + e.getMessage());
        }

        // Check by title (for backward compatibility)
        String sqlTitle = "SELECT COUNT(*) FROM meetings WHERE title LIKE ? OR title = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlTitle)) {
            stmt.setString(1, "%" + meetingId + "%");
            stmt.setString(2, meetingId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                System.out.println("✅ Meeting found by title: " + meetingId);
                return true;
            }
        } catch (SQLException e) {
            System.err.println("❌ meetingExists (title check) error: " + e.getMessage());
        }

        System.out.println("❌ Meeting not found in database: " + meetingId);
        return false;
    }

    /**
     * ENHANCED: Get meeting host from database with multiple fallbacks
     */
    public static String getMeetingHost(String meetingId) {
        System.out.println("🔍 Getting host for meeting: " + meetingId);

        // Try to get host from meeting_participants (first participant is usually host)
        String sqlParticipants = "SELECT username FROM meeting_participants WHERE meeting_id = ? ORDER BY joined_at ASC LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlParticipants)) {
            stmt.setString(1, meetingId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String host = rs.getString("username");
                System.out.println("✅ Found meeting host in participants: " + host);
                return host;
            }
        } catch (SQLException e) {
            System.err.println("❌ getMeetingHost (participants) error: " + e.getMessage());
        }

        // Check meetings table with meeting_id
        String sqlMeetings = "SELECT username FROM meetings WHERE meeting_id = ? LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlMeetings)) {
            stmt.setString(1, meetingId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String host = rs.getString("username");
                System.out.println("✅ Found meeting host in meetings: " + host);
                return host;
            }
        } catch (SQLException e) {
            System.err.println("❌ getMeetingHost (meetings) error: " + e.getMessage());
        }

        // Try to get from meetings table by title
        String sqlTitle = "SELECT username FROM meetings WHERE title LIKE ? OR title = ? LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sqlTitle)) {
            stmt.setString(1, "%" + meetingId + "%");
            stmt.setString(2, meetingId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                String host = rs.getString("username");
                System.out.println("✅ Found meeting host by title: " + host);
                return host;
            }
        } catch (SQLException e) {
            System.err.println("❌ getMeetingHost (title) error: " + e.getMessage());
        }

        System.out.println("❌ No host found for meeting: " + meetingId);
        return null;
    }

    /**
     * ENHANCED: Get meeting details with all information
     */
    public static MeetingDetails getMeetingDetails(String meetingId) {
        String sql = "SELECT m.meeting_id, m.username as host, m.title, m.description, m.date, m.time, m.created_at, " +
                "(SELECT COUNT(*) FROM meeting_participants WHERE meeting_id = m.meeting_id) as participant_count " +
                "FROM meetings m WHERE m.meeting_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, meetingId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new MeetingDetails(
                        rs.getString("meeting_id"),
                        rs.getString("host"),
                        rs.getString("title"),
                        rs.getString("description"),
                        rs.getString("date"),
                        rs.getString("time"),
                        rs.getTimestamp("created_at"),
                        rs.getInt("participant_count")
                );
            }
        } catch (SQLException e) {
            System.err.println("❌ getMeetingDetails error: " + e.getMessage());
        }
        return null;
    }

    /**
     * ENHANCED: Remove meeting from database with proper cleanup
     */
    public static boolean removeMeeting(String meetingId) {
        // Remove participants first
        String deleteParticipants = "DELETE FROM meeting_participants WHERE meeting_id = ?";
        String deleteChatMessages = "DELETE FROM chat_messages WHERE meeting_id = ?";
        String deleteMeetings = "DELETE FROM meetings WHERE meeting_id = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false); // Start transaction

            try (PreparedStatement stmt1 = conn.prepareStatement(deleteParticipants);
                 PreparedStatement stmt2 = conn.prepareStatement(deleteChatMessages);
                 PreparedStatement stmt3 = conn.prepareStatement(deleteMeetings)) {

                stmt1.setString(1, meetingId);
                int participantsDeleted = stmt1.executeUpdate();

                stmt2.setString(1, meetingId);
                int messagesDeleted = stmt2.executeUpdate();

                stmt3.setString(1, meetingId);
                int meetingsDeleted = stmt3.executeUpdate();

                conn.commit();
                System.out.println("✅ Meeting removed from database: " + meetingId);
                System.out.println("   - Participants deleted: " + participantsDeleted);
                System.out.println("   - Messages deleted: " + messagesDeleted);
                System.out.println("   - Meeting records deleted: " + meetingsDeleted);
                return true;
            } catch (SQLException e) {
                conn.rollback();
                System.err.println("❌ removeMeeting transaction error: " + e.getMessage());
                return false;
            }
        } catch (SQLException e) {
            System.err.println("❌ removeMeeting error: " + e.getMessage());
            return false;
        }
    }

    /**
     * NEW: Update meeting last active time
     */
    public static boolean updateMeetingActivity(String meetingId) {
        String sql = "UPDATE meetings SET last_active = CURRENT_TIMESTAMP WHERE meeting_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, meetingId);
            int updated = stmt.executeUpdate();
            return updated > 0;
        } catch (SQLException e) {
            System.err.println("❌ updateMeetingActivity error: " + e.getMessage());
            return false;
        }
    }

    /**
     * NEW: Clean up old meetings (older than specified hours)
     */
    public static boolean cleanupOldMeetings(int hours) {
        String sql = "DELETE FROM meetings WHERE created_at < DATE_SUB(NOW(), INTERVAL ? HOUR)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, hours);
            int deleted = stmt.executeUpdate();
            System.out.println("✅ Cleaned up " + deleted + " old meetings older than " + hours + " hours");
            return true;
        } catch (SQLException e) {
            System.err.println("❌ cleanupOldMeetings error: " + e.getMessage());
            return false;
        }
    }

    // Original meeting methods with enhancements
    public static boolean saveMeeting(String username, String title, String date, String time) {
        String sql = "INSERT INTO meetings(username, title, date, time) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, title);
            stmt.setString(3, date);
            stmt.setString(4, time);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("❌ saveMeeting error: " + e.getMessage());
            return false;
        }
    }

    public static boolean saveMeetingWithDescription(String username, String title, String date, String time, String description) {
        try (Connection conn = getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns(null, null, "meetings", "description");

            if (columns.next()) {
                String sql = "INSERT INTO meetings(username, title, date, time, description) VALUES (?, ?, ?, ?, ?)";
                try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                    stmt.setString(1, username);
                    stmt.setString(2, title);
                    stmt.setString(3, date);
                    stmt.setString(4, time);
                    stmt.setString(5, description);
                    stmt.executeUpdate();
                    return true;
                }
            } else {
                return saveMeeting(username, title, date, time);
            }
        } catch (SQLException e) {
            System.err.println("❌ saveMeetingWithDescription error: " + e.getMessage());
            return false;
        }
    }

    public static List<ScheduleController.Meeting> getMeetings(String username) {
        List<ScheduleController.Meeting> meetings = new ArrayList<>();
        String sql = "SELECT title, date, time FROM meetings WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                String title = rs.getString("title");
                String date = rs.getString("date");
                String time = rs.getString("time");
                meetings.add(new ScheduleController.Meeting(title, date, time, ""));
            }
        } catch (SQLException e) {
            System.err.println("❌ getMeetings error: " + e.getMessage());
        }
        return meetings;
    }

    public static List<ScheduleController.Meeting> getMeetingsWithDescription(String username) {
        List<ScheduleController.Meeting> meetings = new ArrayList<>();
        try (Connection conn = getConnection()) {
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns(null, null, "meetings", "description");

            String sql;
            if (columns.next()) {
                sql = "SELECT title, date, time, description FROM meetings WHERE username = ?";
            } else {
                sql = "SELECT title, date, time FROM meetings WHERE username = ?";
            }

            try (PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, username);
                ResultSet rs = stmt.executeQuery();
                while (rs.next()) {
                    String title = rs.getString("title");
                    String date = rs.getString("date");
                    String time = rs.getString("time");
                    String description = "";
                    try {
                        description = rs.getString("description");
                        if (description == null) description = "";
                    } catch (SQLException e) {
                        description = "";
                    }
                    meetings.add(new ScheduleController.Meeting(title, date, time, description));
                }
            }
        } catch (SQLException e) {
            System.err.println("❌ getMeetingsWithDescription error: " + e.getMessage());
        }
        return meetings;
    }

    public static boolean deleteMeeting(String username, String title, String date, String time) {
        String sql = "DELETE FROM meetings WHERE username = ? AND title = ? AND date = ? AND time = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, title);
            stmt.setString(3, date);
            stmt.setString(4, time);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ deleteMeeting error: " + e.getMessage());
            return false;
        }
    }

    public static MeetingStatistics getMeetingStatistics(String username) {
        MeetingStatistics stats = new MeetingStatistics();
        String sql = "SELECT COUNT(*) as total_meetings, " +
                "MIN(date) as first_meeting, " +
                "MAX(date) as last_meeting " +
                "FROM meetings WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                stats.setTotalMeetings(rs.getInt("total_meetings"));
                stats.setFirstMeeting(rs.getString("first_meeting"));
                stats.setLastMeeting(rs.getString("last_meeting"));
            }
        } catch (SQLException e) {
            System.err.println("❌ getMeetingStatistics error: " + e.getMessage());
        }
        return stats;
    }

    /* ==============================
       CONTACT MANAGEMENT
     ============================== */
    public static boolean addContact(String username, String name, String email, String phone) {
        String sql = "INSERT INTO contacts(username, contact_name, contact_email, contact_phone) VALUES (?, ?, ?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, name);
            stmt.setString(3, email);
            stmt.setString(4, phone);
            stmt.executeUpdate();
            return true;
        } catch (SQLException e) {
            System.err.println("❌ addContact error: " + e.getMessage());
            return false;
        }
    }

    public static List<Contact> getContacts(String username) {
        List<Contact> contacts = new ArrayList<>();
        String sql = "SELECT id, contact_name, contact_email, contact_phone FROM contacts WHERE username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                int id = rs.getInt("id");
                String name = rs.getString("contact_name");
                String email = rs.getString("contact_email");
                String phone = rs.getString("contact_phone");
                contacts.add(new Contact(id, name, email, phone));
            }
        } catch (SQLException e) {
            System.err.println("❌ getContacts error: " + e.getMessage());
        }
        return contacts;
    }

    public static boolean deleteContact(int contactId) {
        String sql = "DELETE FROM contacts WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, contactId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ deleteContact error: " + e.getMessage());
            return false;
        }
    }

    public static boolean updateContact(int contactId, String name, String email, String phone) {
        String sql = "UPDATE contacts SET contact_name=?, contact_email=?, contact_phone=? WHERE id=?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, name);
            stmt.setString(2, email);
            stmt.setString(3, phone);
            stmt.setInt(4, contactId);
            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            System.err.println("❌ updateContact error: " + e.getMessage());
            return false;
        }
    }

    /* ==============================
       CHAT MANAGEMENT - FIXED SCHEMA
     ============================== */
    public static boolean saveChatMessage(String meetingId, String username, String message, String messageType) {
        createChatTable();

        String sql = "INSERT INTO chat_messages (meeting_id, username, message, message_type, timestamp) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, meetingId);
            stmt.setString(2, username);
            stmt.setString(3, message);
            stmt.setString(4, messageType);
            stmt.executeUpdate();
            System.out.println("✅ Chat message saved: " + username + " - " + message);
            return true;
        } catch (SQLException e) {
            System.err.println("❌ saveChatMessage error: " + e.getMessage());
            return false;
        }
    }

    public static List<ChatMessage> getChatMessages(String meetingId) {
        createChatTable();

        List<ChatMessage> messages = new ArrayList<>();
        String sql = "SELECT username, message, message_type, timestamp FROM chat_messages WHERE meeting_id = ? ORDER BY timestamp ASC";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, meetingId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                messages.add(new ChatMessage(
                        rs.getString("username"),
                        rs.getString("message"),
                        rs.getString("message_type"),
                        rs.getTimestamp("timestamp")
                ));
            }
            System.out.println("✅ Loaded " + messages.size() + " chat messages for meeting: " + meetingId);
        } catch (SQLException e) {
            System.err.println("❌ getChatMessages error: " + e.getMessage());
        }
        return messages;
    }

    private static void createChatTable() {
        String sql = "CREATE TABLE IF NOT EXISTS chat_messages (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "meeting_id VARCHAR(50) NOT NULL, " +
                "username VARCHAR(50) NOT NULL, " +
                "message TEXT NOT NULL, " +
                "message_type VARCHAR(10) NOT NULL, " +
                "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "INDEX idx_meeting_id (meeting_id)" +
                ")";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("✅ Chat messages table created/verified successfully");

            try {
                DatabaseMetaData metaData = conn.getMetaData();
                ResultSet columns = metaData.getColumns(null, null, "chat_messages", "username");
                if (!columns.next()) {
                    String alterSql = "ALTER TABLE chat_messages ADD COLUMN username VARCHAR(50) NOT NULL DEFAULT 'Unknown'";
                    stmt.execute(alterSql);
                    System.out.println("✅ Added username column to existing chat_messages table");
                }
            } catch (SQLException e) {
                System.err.println("⚠️ Error checking/adding username column: " + e.getMessage());
            }

        } catch (SQLException e) {
            System.err.println("❌ createChatTable error: " + e.getMessage());
        }
    }

    public static boolean clearChatMessages(String meetingId) {
        createChatTable();

        String sql = "DELETE FROM chat_messages WHERE meeting_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, meetingId);
            int rowsDeleted = stmt.executeUpdate();
            System.out.println("✅ Cleared " + rowsDeleted + " chat messages for meeting: " + meetingId);
            return rowsDeleted > 0;
        } catch (SQLException e) {
            System.err.println("❌ clearChatMessages error: " + e.getMessage());
            return false;
        }
    }

    /* ==============================
       PARTICIPANT MANAGEMENT - ENHANCED
     ============================== */
    public static boolean addParticipant(String meetingId, String username) {
        createParticipantsTable();

        String sql = "INSERT INTO meeting_participants (meeting_id, username, joined_at) VALUES (?, ?, CURRENT_TIMESTAMP) " +
                "ON DUPLICATE KEY UPDATE joined_at = CURRENT_TIMESTAMP";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, meetingId);
            stmt.setString(2, username);
            stmt.executeUpdate();
            System.out.println("✅ Participant added: " + username + " to meeting: " + meetingId);

            // Also update meeting activity
            updateMeetingActivity(meetingId);

            return true;
        } catch (SQLException e) {
            System.err.println("❌ addParticipant error: " + e.getMessage());
            return false;
        }
    }

    private static boolean addParticipantTransaction(Connection conn, String meetingId, String username) throws SQLException {
        String sql = "INSERT INTO meeting_participants (meeting_id, username, joined_at) VALUES (?, ?, CURRENT_TIMESTAMP) " +
                "ON DUPLICATE KEY UPDATE joined_at = CURRENT_TIMESTAMP";
        try (PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, meetingId);
            stmt.setString(2, username);
            int result = stmt.executeUpdate();
            System.out.println("✅ Participant added in transaction: " + username + " to meeting: " + meetingId);
            return result > 0;
        }
    }

    public static boolean removeParticipant(String meetingId, String username) {
        createParticipantsTable();

        String sql = "DELETE FROM meeting_participants WHERE meeting_id = ? AND username = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, meetingId);
            stmt.setString(2, username);
            boolean result = stmt.executeUpdate() > 0;
            System.out.println("✅ Participant removed: " + username + " from meeting: " + meetingId);

            // Update meeting activity
            updateMeetingActivity(meetingId);

            return result;
        } catch (SQLException e) {
            System.err.println("❌ removeParticipant error: " + e.getMessage());
            return false;
        }
    }

    public static List<String> getParticipants(String meetingId) {
        createParticipantsTable();

        List<String> participants = new ArrayList<>();
        String sql = "SELECT username FROM meeting_participants WHERE meeting_id = ? ORDER BY joined_at ASC";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, meetingId);
            ResultSet rs = stmt.executeQuery();
            while (rs.next()) {
                participants.add(rs.getString("username"));
            }
            System.out.println("✅ Loaded " + participants.size() + " participants for meeting: " + meetingId);
        } catch (SQLException e) {
            System.err.println("❌ getParticipants error: " + e.getMessage());
        }
        return participants;
    }

    public static int getParticipantCount(String meetingId) {
        createParticipantsTable();

        String sql = "SELECT COUNT(*) FROM meeting_participants WHERE meeting_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, meetingId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return rs.getInt(1);
            }
        } catch (SQLException e) {
            System.err.println("❌ getParticipantCount error: " + e.getMessage());
        }
        return 0;
    }

    private static void createParticipantsTable() {
        String sql = "CREATE TABLE IF NOT EXISTS meeting_participants (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "meeting_id VARCHAR(50) NOT NULL, " +
                "username VARCHAR(50) NOT NULL, " +
                "joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE KEY unique_participant (meeting_id, username), " +
                "INDEX idx_meeting_id (meeting_id)" +
                ")";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("✅ Meeting participants table created/verified successfully");
        } catch (SQLException e) {
            System.err.println("❌ createParticipantsTable error: " + e.getMessage());
        }
    }

    /* ==============================
       CLASSES (POJOs) - ENHANCED
     ============================== */
    public static class Contact {
        private int id;
        private String name;
        private String email;
        private String phone;

        public Contact(int id, String name, String email, String phone) {
            this.id = id;
            this.name = name;
            this.email = email;
            this.phone = phone;
        }

        public int getId() { return id; }
        public String getName() { return name; }
        public String getEmail() { return email; }
        public String getPhone() { return phone; }

        @Override
        public String toString() {
            return name + " (" + email + ", " + phone + ")";
        }
    }

    public static class ServerConfig {
        private String serverIp;
        private String serverPort;
        private Timestamp lastUsed;

        public ServerConfig(String serverIp, String serverPort) {
            this.serverIp = serverIp;
            this.serverPort = serverPort;
        }

        public ServerConfig(String serverIp, String serverPort, Timestamp lastUsed) {
            this.serverIp = serverIp;
            this.serverPort = serverPort;
            this.lastUsed = lastUsed;
        }

        public String getServerIp() { return serverIp; }
        public String getServerPort() { return serverPort; }
        public Timestamp getLastUsed() { return lastUsed; }
        public String getServerUrl() { return "ws://" + serverIp + ":" + serverPort; }

        @Override
        public String toString() {
            return serverIp + ":" + serverPort + (lastUsed != null ? " (Last used: " + lastUsed + ")" : "");
        }
    }

    public static class ChatMessage {
        private String username;
        private String message;
        private String messageType;
        private Timestamp timestamp;

        public ChatMessage(String username, String message, String messageType, Timestamp timestamp) {
            this.username = username;
            this.message = message;
            this.messageType = messageType;
            this.timestamp = timestamp;
        }

        public String getUsername() { return username; }
        public String getMessage() { return message; }
        public String getMessageType() { return messageType; }
        public Timestamp getTimestamp() { return timestamp; }

        @Override
        public String toString() {
            return "[" + timestamp + "] " + username + ": " + message;
        }
    }

    // NEW: Meeting Info class for list display
    public static class MeetingInfo {
        private String meetingId;
        private String host;
        private int participantCount;
        private Timestamp createdAt;

        public MeetingInfo(String meetingId, String host, int participantCount, Timestamp createdAt) {
            this.meetingId = meetingId;
            this.host = host;
            this.participantCount = participantCount;
            this.createdAt = createdAt;
        }

        public String getMeetingId() { return meetingId; }
        public String getHost() { return host; }
        public int getParticipantCount() { return participantCount; }
        public Timestamp getCreatedAt() { return createdAt; }

        public String getFormattedTime() {
            if (createdAt == null) return "Unknown";
            return new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm").format(createdAt);
        }

        @Override
        public String toString() {
            return meetingId + " | Host: " + host + " | Participants: " + participantCount + " | Created: " + getFormattedTime();
        }
    }

    // NEW: Meeting Details class for complete meeting information
    public static class MeetingDetails {
        private String meetingId;
        private String host;
        private String title;
        private String description;
        private String date;
        private String time;
        private Timestamp createdAt;
        private int participantCount;

        public MeetingDetails(String meetingId, String host, String title, String description,
                              String date, String time, Timestamp createdAt, int participantCount) {
            this.meetingId = meetingId;
            this.host = host;
            this.title = title;
            this.description = description;
            this.date = date;
            this.time = time;
            this.createdAt = createdAt;
            this.participantCount = participantCount;
        }

        public String getMeetingId() { return meetingId; }
        public String getHost() { return host; }
        public String getTitle() { return title; }
        public String getDescription() { return description; }
        public String getDate() { return date; }
        public String getTime() { return time; }
        public Timestamp getCreatedAt() { return createdAt; }
        public int getParticipantCount() { return participantCount; }
    }

    public static class UserProfile {
        private String username;
        private Timestamp createdAt;

        public UserProfile(String username, Timestamp createdAt) {
            this.username = username;
            this.createdAt = createdAt;
        }

        public String getUsername() { return username; }
        public Timestamp getCreatedAt() { return createdAt; }
    }

    public static class UserPreference {
        private String key;
        private String value;

        public UserPreference(String key, String value) {
            this.key = key;
            this.value = value;
        }

        public String getKey() { return key; }
        public String getValue() { return value; }
    }

    public static class MeetingStatistics {
        private int totalMeetings;
        private String firstMeeting;
        private String lastMeeting;

        public MeetingStatistics() {}

        public int getTotalMeetings() { return totalMeetings; }
        public String getFirstMeeting() { return firstMeeting; }
        public String getLastMeeting() { return lastMeeting; }

        public void setTotalMeetings(int totalMeetings) { this.totalMeetings = totalMeetings; }
        public void setFirstMeeting(String firstMeeting) { this.firstMeeting = firstMeeting; }
        public void setLastMeeting(String lastMeeting) { this.lastMeeting = lastMeeting; }
    }

    public static class UserDetail {
        private String username;
        private Timestamp createdAt;
        private int meetingCount;
        private int contactCount;
        private Timestamp lastActive;

        public UserDetail(String username, Timestamp createdAt, int meetingCount, int contactCount, Timestamp lastActive) {
            this.username = username;
            this.createdAt = createdAt;
            this.meetingCount = meetingCount;
            this.contactCount = contactCount;
            this.lastActive = lastActive;
        }

        public String getUsername() { return username; }
        public Timestamp getCreatedAt() { return createdAt; }
        public int getMeetingCount() { return meetingCount; }
        public int getContactCount() { return contactCount; }
        public Timestamp getLastActive() { return lastActive; }

        public String getStatus() {
            if (lastActive == null) return "Never Active";
            long diff = System.currentTimeMillis() - lastActive.getTime();
            long minutes = diff / (60 * 1000);
            if (minutes < 5) return "Online";
            if (minutes < 60) return "Recently Active";
            if (minutes < 1440) return "Active Today";
            return "Inactive";
        }

        @Override
        public String toString() {
            return username + " | Meetings: " + meetingCount + " | Contacts: " + contactCount + " | " + getStatus();
        }
    }

    /* ==============================
       DATABASE INITIALIZATION - ENHANCED
     ============================== */
    public static void initializeDatabase() {
        // Create tables if they don't exist
        String[] createTables = {
                // Users table (ensure it exists)
                "CREATE TABLE IF NOT EXISTS users (" +
                        "username VARCHAR(50) PRIMARY KEY, " +
                        "password VARCHAR(100) NOT NULL, " +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                        ")",

                // Server config table
                "CREATE TABLE IF NOT EXISTS server_config (" +
                        "username VARCHAR(50) NOT NULL, " +
                        "server_ip VARCHAR(45) NOT NULL, " +
                        "server_port VARCHAR(10) NOT NULL, " +
                        "last_used TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                        "PRIMARY KEY (username, server_ip, server_port)" +
                        ")",

                // User preferences table
                "CREATE TABLE IF NOT EXISTS user_preferences (" +
                        "username VARCHAR(50) NOT NULL, " +
                        "preference_key VARCHAR(50) NOT NULL, " +
                        "preference_value TEXT, " +
                        "PRIMARY KEY (username, preference_key)" +
                        ")",

                // Meetings table with enhanced schema
                "CREATE TABLE IF NOT EXISTS meetings (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "meeting_id VARCHAR(20), " +
                        "username VARCHAR(50) NOT NULL, " +
                        "title VARCHAR(255), " +
                        "description TEXT, " +
                        "date DATE, " +
                        "time TIME, " +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                        "last_active TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                        "INDEX idx_meeting_id (meeting_id), " +
                        "INDEX idx_created_at (created_at)" +
                        ")",

                // Meeting participants table
                "CREATE TABLE IF NOT EXISTS meeting_participants (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "meeting_id VARCHAR(50) NOT NULL, " +
                        "username VARCHAR(50) NOT NULL, " +
                        "joined_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                        "UNIQUE KEY unique_participant (meeting_id, username), " +
                        "INDEX idx_meeting_id (meeting_id)" +
                        ")",

                // Security logs table
                "CREATE TABLE IF NOT EXISTS security_logs (" +
                        "id INT AUTO_INCREMENT PRIMARY KEY, " +
                        "username VARCHAR(50) NOT NULL, " +
                        "event_type VARCHAR(50) NOT NULL, " +
                        "description TEXT, " +
                        "ip_address VARCHAR(45), " +
                        "user_agent TEXT, " +
                        "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                        "INDEX idx_username (username), " +
                        "INDEX idx_event_type (event_type)" +
                        ")"
        };

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {

            // Create main tables
            for (String sql : createTables) {
                try {
                    stmt.execute(sql);
                } catch (SQLException e) {
                    System.err.println("❌ Table creation error: " + e.getMessage());
                }
            }

            // Ensure all columns exist
            ensureMeetingTableColumns(conn);

            // Create chat table
            createChatTable();

            System.out.println("✅ Database tables initialized successfully");

        } catch (SQLException e) {
            System.err.println("❌ Database initialization error: " + e.getMessage());
        }
    }

    private static void ensureMeetingTableColumns(Connection conn) {
        try {
            DatabaseMetaData metaData = conn.getMetaData();

            // Check and add description column
            ResultSet descColumns = metaData.getColumns(null, null, "meetings", "description");
            if (!descColumns.next()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE meetings ADD COLUMN description TEXT");
                    System.out.println("✅ Added description column to meetings table");
                }
            }

            // Check and add meeting_id column
            ResultSet idColumns = metaData.getColumns(null, null, "meetings", "meeting_id");
            if (!idColumns.next()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE meetings ADD COLUMN meeting_id VARCHAR(20)");
                    System.out.println("✅ Added meeting_id column to meetings table");
                }
            }

            // Check and add last_active column
            ResultSet activeColumns = metaData.getColumns(null, null, "meetings", "last_active");
            if (!activeColumns.next()) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("ALTER TABLE meetings ADD COLUMN last_active TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP");
                    System.out.println("✅ Added last_active column to meetings table");
                }
            }

        } catch (SQLException e) {
            System.err.println("❌ Error ensuring meeting table columns: " + e.getMessage());
        }
    }

    // Get single contact by ID
    public static Contact getContactById(int id) {
        String sql = "SELECT * FROM contacts WHERE id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setInt(1, id);
            ResultSet rs = stmt.executeQuery();
            if (rs.next()) {
                return new Contact(
                        rs.getInt("id"),
                        rs.getString("contact_name"),
                        rs.getString("contact_email"),
                        rs.getString("contact_phone")
                );
            }
        } catch (SQLException e) {
            System.err.println("❌ getContactById error: " + e.getMessage());
        }
        return null;
    }
}