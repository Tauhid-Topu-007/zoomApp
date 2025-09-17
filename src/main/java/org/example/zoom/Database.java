package org.example.zoom;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class Database {

    private static final String URL =
            "jdbc:mysql://localhost:3306/zoom_app?useSSL=false&serverTimezone=UTC&allowPublicKeyRetrieval=true";
    private static final String USER = "root";
    private static final String PASSWORD = "2015";

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver"); // load MySQL driver
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    // central connection method
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

    public static boolean updatePassword(String username, String newPassword) {
        String sql = "UPDATE users SET password = ? WHERE username = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            stmt.setString(1, newPassword);
            stmt.setString(2, username);
            return stmt.executeUpdate() > 0;

        } catch (SQLException e) {
            System.err.println("❌ updatePassword error: " + e.getMessage());
            return false;
        }
    }

    /* ==============================
       MEETING MANAGEMENT
     ============================== */
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
                meetings.add(new ScheduleController.Meeting(title, date, time));
            }

        } catch (SQLException e) {
            System.err.println("❌ getMeetings error: " + e.getMessage());
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
       CONTACT CLASS (POJO)
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

    /* ==============================
       TESTING
     ============================== */
    public static void main(String[] args) {
        // Test contact save
        if (addContact("testuser", "Alice", "alice@example.com", "123456789")) {
            System.out.println("✅ Contact saved!");
        }

        // Fetch contacts
        List<Contact> contacts = getContacts("testuser");
        contacts.forEach(c -> System.out.println("📌 " + c));
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
