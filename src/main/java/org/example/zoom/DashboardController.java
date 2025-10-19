package org.example.zoom;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.example.zoom.websocket.SimpleWebSocketClient; // Changed import

public class DashboardController {

    @FXML
    private Label welcomeLabel;

    @FXML
    public void initialize() {
        // Always pull the logged-in user from HelloApplication
        String user = HelloApplication.getLoggedInUser();
        if (user != null) {
            welcomeLabel.setText("Welcome, " + user + " 👋");
            // WebSocket is now initialized automatically in HelloApplication.setLoggedInUser()
            // So we don't need to initialize it here anymore
            checkWebSocketStatus();
        }
    }

    private void checkWebSocketStatus() {
        if (HelloApplication.isWebSocketConnected()) {
            System.out.println("✅ WebSocket connected successfully");
        } else {
            System.out.println("⚠️ WebSocket not connected - using local mode");
        }
    }

    private void handleWebSocketMessage(String message) {
        System.out.println("📨 WebSocket message: " + message);

        // Handle different message types
        if (message.startsWith("CONNECTED")) {
            System.out.println("✅ " + message);
        } else if (message.startsWith("ERROR")) {
            showPopup("Connection Error", message);
        } else if (message.startsWith("DISCONNECTED")) {
            System.out.println("❌ " + message);
        }
        // Other message types will be handled by specific controllers
    }

    @FXML
    protected void onNewMeetingClick() throws Exception {
        // Generate a unique meeting ID
        String meetingId = generateMeetingId();
        HelloApplication.setActiveMeetingId(meetingId);

        // Join the meeting via WebSocket
        SimpleWebSocketClient client = HelloApplication.getWebSocketClient();
        if (client != null && client.isConnected()) {
            String username = HelloApplication.getLoggedInUser();
            client.sendMessage("JOIN_MEETING", meetingId, username, "created and joined meeting");
        } else {
            showPopup("Connection Issue", "Not connected to server. Some features may not work.");
        }

        HelloApplication.setRoot("new-meeting-view.fxml");
    }

    @FXML
    protected void onJoinClick() throws Exception {
        HelloApplication.setRoot("join-view.fxml");
    }

    @FXML
    protected void onScheduleClick() throws Exception {
        FXMLLoader loader = new FXMLLoader(getClass().getResource("schedule-view.fxml"));
        Scene scene = new Scene(loader.load(), 900, 600);

        // Pass current user into ScheduleController
        ScheduleController controller = loader.getController();
        controller.setUser(HelloApplication.getLoggedInUser());

        Stage stage = (Stage) welcomeLabel.getScene().getWindow();
        stage.setScene(scene);
    }

    @FXML
    protected void onShareScreenClick() throws Exception {
        // Check WebSocket connection before sharing screen
        SimpleWebSocketClient client = HelloApplication.getWebSocketClient();
        if (client != null && client.isConnected()) {
            HelloApplication.setRoot("share-screen-view.fxml");
        } else {
            showPopup("Connection Required", "Please check your internet connection. Screen sharing requires active server connection.");
        }
    }

    @FXML
    protected void onContactsClick() throws Exception {
        HelloApplication.setRoot("contacts-view.fxml");
    }

    @FXML
    protected void onChatClick() throws Exception {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("chat-view.fxml"));
            Scene scene = new Scene(loader.load(), 800, 600);

            // Get the chat controller and pass the WebSocket client
            ChatController controller = loader.getController();

            // Initialize chat with WebSocket client
            if (controller != null) {
                SimpleWebSocketClient client = HelloApplication.getWebSocketClient();
                controller.setWebSocketClient(client);
                controller.setCurrentUser(HelloApplication.getLoggedInUser());
            }

            Stage stage = (Stage) welcomeLabel.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Zoom Chat - " + HelloApplication.getLoggedInUser());

        } catch (Exception e) {
            e.printStackTrace();
            showPopup("Error", "❌ Failed to open Chat!");
        }
    }

    @FXML
    protected void onRecordingsClick() throws Exception {
        HelloApplication.setRoot("recordings-view.fxml");
    }

    @FXML
    protected void onSettingsClick() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("settings-view.fxml"));
            Scene scene = new Scene(loader.load(), 500, 400);

            SettingsController controller = loader.getController();
            controller.setUser(HelloApplication.getLoggedInUser());

            Stage stage = (Stage) welcomeLabel.getScene().getWindow();
            stage.setScene(scene);

        } catch (Exception e) {
            e.printStackTrace();
            showPopup("Error", "❌ Failed to open Settings!");
        }
    }

    @FXML
    protected void onLogoutClick() throws Exception {
        // Disconnect WebSocket before logging out
        SimpleWebSocketClient client = HelloApplication.getWebSocketClient();
        if (client != null) {
            client.disconnect();
        }
        HelloApplication.logout();
    }

    private String generateMeetingId() {
        // Generate a 6-digit meeting ID
        return String.valueOf((int) (Math.random() * 900000) + 100000);
    }

    private void showPopup(String title, String message) {
        javafx.application.Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // Method to check WebSocket connection status
    public boolean isWebSocketConnected() {
        SimpleWebSocketClient client = HelloApplication.getWebSocketClient();
        return client != null && client.isConnected();
    }

    // Method to get connection status message
    public String getConnectionStatus() {
        if (isWebSocketConnected()) {
            return "🟢 Connected";
        } else {
            return "🔴 Disconnected";
        }
    }
}