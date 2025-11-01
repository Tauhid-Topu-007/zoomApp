package org.example.zoom;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Database {

    // ✅ Updated connection info for zoom_user
    private static final String URL =
            "jdbc:mysql://localhost:3306/zoom_app?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String USER = "zoom_user";      // ✅ new MySQL user
    private static final String PASSWORD = "zoom123";    // ✅ new MySQL password

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
       USER MANAGEMENT
     ============================== */
    public static boolean registerUser(String username, String password) {
        String sql = "INSERT INTO users(username, password) VALUES (?, ?)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, username);
            stmt.setString(2, password);
            stmt.executeUpdate();
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
            return rs.next();
        } catch (SQLException e) {
            System.err.println("❌ authenticateUser error: " + e.getMessage());
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
       MEETING MANAGEMENT - ENHANCED
     ============================== */

    // NEW: Store meeting with proper meeting ID - THIS IS THE KEY FIX
    public static boolean saveMeetingWithId(String meetingId, String hostUsername, String title, String description) {
        // First ensure meetings table has meeting_id column
        addMeetingIdColumnToMeetings();

        String sql = "INSERT INTO meetings (meeting_id, username, title, description, date, time) VALUES (?, ?, ?, ?, CURDATE(), CURTIME())";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, meetingId);
            stmt.setString(2, hostUsername);
            stmt.setString(3, title);
            stmt.setString(4, description);
            stmt.executeUpdate();
            System.out.println("✅ Meeting saved with ID: " + meetingId + " by host: " + hostUsername);
            return true;
        } catch (SQLException e) {
            System.err.println("❌ saveMeetingWithId error: " + e.getMessage());
            return false;
        }
    }

    // NEW: Enhanced meeting existence check
    public static boolean meetingExists(String meetingId) {
        System.out.println("🔍 Checking if meeting exists: " + meetingId);

        // First check meeting_participants table (most reliable)
        String sql = "SELECT COUNT(*) FROM meeting_participants WHERE meeting_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, meetingId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                System.out.println("✅ Meeting found in participants: " + meetingId);
                return true;
            }
        } catch (SQLException e) {
            System.err.println("❌ meetingExists (participants) error: " + e.getMessage());
        }

        // Also check meetings table with meeting_id column
        String sql2 = "SELECT COUNT(*) FROM meetings WHERE meeting_id = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql2)) {
            stmt.setString(1, meetingId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                System.out.println("✅ Meeting found in meetings table: " + meetingId);
                return true;
            }
        } catch (SQLException e) {
            System.err.println("❌ meetingExists (meetings) error: " + e.getMessage());
        }

        // For backward compatibility, also check title as meeting ID
        String sql3 = "SELECT COUNT(*) FROM meetings WHERE title = ?";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql3)) {
            stmt.setString(1, meetingId);
            ResultSet rs = stmt.executeQuery();
            if (rs.next() && rs.getInt(1) > 0) {
                System.out.println("✅ Meeting found by title: " + meetingId);
                return true;
            }
        } catch (SQLException e) {
            System.err.println("❌ meetingExists (title) error: " + e.getMessage());
        }

        System.out.println("❌ Meeting not found in database: " + meetingId);
        return false;
    }

    // NEW: Get meeting host from database
    public static String getMeetingHost(String meetingId) {
        // Try to get host from meeting_participants (first participant is usually host)
        String sql = "SELECT username FROM meeting_participants WHERE meeting_id = ? ORDER BY joined_at ASC LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
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

        // Alternative: Check meetings table with meeting_id
        String sql2 = "SELECT username FROM meetings WHERE meeting_id = ? LIMIT 1";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql2)) {
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

        System.out.println("❌ No host found for meeting: " + meetingId);
        return null;
    }

    // NEW: Remove meeting from database
    public static boolean removeMeeting(String meetingId) {
        // Remove participants first
        String deleteParticipants = "DELETE FROM meeting_participants WHERE meeting_id = ?";
        String deleteChatMessages = "DELETE FROM chat_messages WHERE meeting_id = ?";

        try (Connection conn = getConnection()) {
            conn.setAutoCommit(false); // Start transaction

            try (PreparedStatement stmt1 = conn.prepareStatement(deleteParticipants);
                 PreparedStatement stmt2 = conn.prepareStatement(deleteChatMessages)) {

                stmt1.setString(1, meetingId);
                stmt1.executeUpdate();

                stmt2.setString(1, meetingId);
                stmt2.executeUpdate();

                conn.commit();
                System.out.println("✅ Meeting removed from database: " + meetingId);
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

    // Updated to support description field in database
    public static boolean saveMeetingWithDescription(String username, String title, String date, String time, String description) {
        // First check if description column exists, if not, use the basic version
        try (Connection conn = getConnection()) {
            // Check if description column exists
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns(null, null, "meetings", "description");

            if (columns.next()) {
                // Column exists, use the enhanced version
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
                // Column doesn't exist, use basic version
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
                // FIXED: Create Meeting with empty description for database compatibility
                meetings.add(new ScheduleController.Meeting(title, date, time, ""));
            }
        } catch (SQLException e) {
            System.err.println("❌ getMeetings error: " + e.getMessage());
        }
        return meetings;
    }

    // Enhanced version that tries to get description if available
    public static List<ScheduleController.Meeting> getMeetingsWithDescription(String username) {
        List<ScheduleController.Meeting> meetings = new ArrayList<>();
        try (Connection conn = getConnection()) {
            // Check if description column exists
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns(null, null, "meetings", "description");

            String sql;
            if (columns.next()) {
                // Column exists, include description
                sql = "SELECT title, date, time, description FROM meetings WHERE username = ?";
            } else {
                // Column doesn't exist, use basic query
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
                        // Description column doesn't exist or is null
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

    // NEW: Get meeting statistics
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
        // First ensure the table exists with correct schema
        createChatTable();

        String sql = "INSERT INTO chat_messages (meeting_id, username, message, message_type, timestamp) VALUES (?, ?, ?, ?, CURRENT_TIMESTAMP)";
        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, meetingId);
            stmt.setString(2, username);
            stmt.setString(3, message);
            stmt.setString(4, messageType); // "USER" or "SYSTEM"
            stmt.executeUpdate();
            System.out.println("✅ Chat message saved: " + username + " - " + message);
            return true;
        } catch (SQLException e) {
            System.err.println("❌ saveChatMessage error: " + e.getMessage());
            return false;
        }
    }

    public static List<ChatMessage> getChatMessages(String meetingId) {
        // First ensure the table exists with correct schema
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

    /**
     * FIXED: Corrected chat table schema with username column
     */
    private static void createChatTable() {
        String sql = "CREATE TABLE IF NOT EXISTS chat_messages (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "meeting_id VARCHAR(50) NOT NULL, " +
                "username VARCHAR(50) NOT NULL, " +  // ✅ FIXED: Added username column
                "message TEXT NOT NULL, " +
                "message_type VARCHAR(10) NOT NULL, " + // USER or SYSTEM
                "timestamp TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "INDEX idx_meeting_id (meeting_id)" +
                ")";
        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
            System.out.println("✅ Chat messages table created/verified successfully");

            // Check if we need to alter table to add username column (for existing tables)
            try {
                DatabaseMetaData metaData = conn.getMetaData();
                ResultSet columns = metaData.getColumns(null, null, "chat_messages", "username");
                if (!columns.next()) {
                    // username column doesn't exist, add it
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
        createChatTable(); // Ensure table exists

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
       PARTICIPANT MANAGEMENT
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
            return true;
        } catch (SQLException e) {
            System.err.println("❌ addParticipant error: " + e.getMessage());
            return false;
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
       CLASSES (POJOs)
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

    // NEW: User Profile class
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

    // NEW: User Preference class
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

    // NEW: Meeting Statistics class
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

    /* ==============================
       DATABASE INITIALIZATION - ENHANCED
     ============================== */
    public static void initializeDatabase() {
        // Create tables if they don't exist
        String[] createTables = {
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

            // Add description column to meetings table if it doesn't exist
            addDescriptionColumnToMeetings();

            // Add meeting_id column to meetings table if it doesn't exist
            addMeetingIdColumnToMeetings();

            // Create chat table with correct schema
            createChatTable();

            System.out.println("✅ Database tables initialized successfully");

        } catch (SQLException e) {
            System.err.println("❌ Database initialization error: " + e.getMessage());
        }
    }

    // Helper method to safely add description column to meetings table
    private static void addDescriptionColumnToMeetings() {
        try (Connection conn = getConnection()) {
            // Check if description column already exists
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns(null, null, "meetings", "description");

            if (!columns.next()) {
                // Column doesn't exist, add it
                try (Statement stmt = conn.createStatement()) {
                    String sql = "ALTER TABLE meetings ADD COLUMN description TEXT";
                    stmt.execute(sql);
                    System.out.println("✅ Added description column to meetings table");
                }
            } else {
                System.out.println("✅ Description column already exists in meetings table");
            }
        } catch (SQLException e) {
            System.err.println("❌ Error adding description column: " + e.getMessage());
        }
    }

    // Helper method to add meeting_id column to meetings table
    private static void addMeetingIdColumnToMeetings() {
        try (Connection conn = getConnection()) {
            // Check if meeting_id column already exists
            DatabaseMetaData metaData = conn.getMetaData();
            ResultSet columns = metaData.getColumns(null, null, "meetings", "meeting_id");

            if (!columns.next()) {
                // Column doesn't exist, add it
                try (Statement stmt = conn.createStatement()) {
                    String sql = "ALTER TABLE meetings ADD COLUMN meeting_id VARCHAR(20)";
                    stmt.execute(sql);
                    System.out.println("✅ Added meeting_id column to meetings table");
                }
            } else {
                System.out.println("✅ meeting_id column already exists in meetings table");
            }
        } catch (SQLException e) {
            System.err.println("❌ Error adding meeting_id column: " + e.getMessage());
        }
    }

    /* ==============================
       TESTING
     ============================== */
    public static void main(String[] args) {
        // Initialize database
        initializeDatabase();

        // Test server config
        if (saveServerConfig("testuser", "192.168.1.100", "8887")) {
            System.out.println("✅ Server config saved!");
        }

        // Get server config
        ServerConfig config = getServerConfig("testuser");
        if (config != null) {
            System.out.println("📡 Server config: " + config);
        }

        // Test contact save
        if (addContact("testuser", "Alice", "alice@example.com", "123456789")) {
            System.out.println("✅ Contact saved!");
        }

        // Fetch contacts
        List<Contact> contacts = getContacts("testuser");
        contacts.forEach(c -> System.out.println("📌 " + c));

        // Test meeting with description
        if (saveMeetingWithDescription("testuser", "Test Meeting", "2024-01-01", "14:30", "This is a test meeting description")) {
            System.out.println("✅ Meeting with description saved!");
        }

        // Test new meeting with ID method
        if (saveMeetingWithId("123456", "testuser", "New Meeting Test", "Test meeting with ID")) {
            System.out.println("✅ Meeting with ID saved successfully!");
        }

        // Fetch meetings with description
        List<ScheduleController.Meeting> meetings = getMeetingsWithDescription("testuser");
        meetings.forEach(m -> System.out.println("📅 " + m.getTitle() + " - " + m.getDescription()));

        // Test chat functionality
        if (saveChatMessage("TEST123", "testuser", "Hello everyone!", "USER")) {
            System.out.println("✅ Chat message saved!");
        }

        // Fetch chat messages
        List<ChatMessage> chatMessages = getChatMessages("TEST123");
        chatMessages.forEach(m -> System.out.println("💬 " + m));

        // Test participant functionality
        if (addParticipant("TEST123", "participant1")) {
            System.out.println("✅ Participant added!");
        }

        // Fetch participants
        List<String> participants = getParticipants("TEST123");
        participants.forEach(p -> System.out.println("👥 " + p));

        // Test password reset functionality
        if (resetPassword("testuser", "newpassword123")) {
            System.out.println("✅ Password reset successful!");
        }

        // Test user profile
        UserProfile profile = getUserProfile("testuser");
        if (profile != null) {
            System.out.println("👤 User profile: " + profile.getUsername() + " created at " + profile.getCreatedAt());
        }

        // Test meeting statistics
        MeetingStatistics stats = getMeetingStatistics("testuser");
        System.out.println("📊 Meeting stats: " + stats.getTotalMeetings() + " total meetings");

        // Test new meeting existence methods
        System.out.println("\n=== Testing New Meeting Methods ===");
        boolean exists = meetingExists("TEST123");
        System.out.println("✅ Meeting exists: " + exists);

        boolean exists2 = meetingExists("123456");
        System.out.println("✅ Meeting with ID exists: " + exists2);

        String host = getMeetingHost("TEST123");
        System.out.println("✅ Meeting host: " + host);

        // Test remove meeting
        boolean removed = removeMeeting("TEST123");
        System.out.println("✅ Meeting removed: " + removed);
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