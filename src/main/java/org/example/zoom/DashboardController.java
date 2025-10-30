package org.example.zoom;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.stage.Stage;
import org.example.zoom.websocket.SimpleWebSocketClient;

public class DashboardController implements HelloApplication.ConnectionStatusListener {

    @FXML
    private Label welcomeLabel;

    @FXML
    private Label connectionInfoLabel;

    @FXML
    public void initialize() {
        // Always pull the logged-in user from HelloApplication
        String user = HelloApplication.getLoggedInUser();
        if (user != null) {
            welcomeLabel.setText("Welcome, " + user + " 👋");

            // Register as connection status listener
            HelloApplication.setConnectionStatusListener(this);

            // Update connection info immediately
            updateConnectionInfo();
        }
    }

    // Make this method public so HelloApplication can call it
    public void updateConnectionInfo() {
        if (connectionInfoLabel != null) {
            String status = getConnectionStatus();
            String serverUrl = HelloApplication.getCurrentServerUrl();

            connectionInfoLabel.setText(status + " | " + serverUrl.replace("ws://", ""));

            if (isWebSocketConnected()) {
                connectionInfoLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 12; -fx-font-weight: bold;");
            } else {
                connectionInfoLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 12; -fx-font-weight: bold;");
            }
        }
    }

    // Implement ConnectionStatusListener interface
    @Override
    public void onConnectionStatusChanged(boolean connected, String status) {
        // Update the connection info when status changes
        updateConnectionInfo();

        // Optional: Show notification for connection changes
        if (connected) {
            System.out.println("✅ Connection established from dashboard");
        } else {
            System.out.println("❌ Connection lost from dashboard");
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
            updateConnectionInfo();
        } else if (message.startsWith("ERROR")) {
            showPopup("Connection Error", message);
        } else if (message.startsWith("DISCONNECTED")) {
            System.out.println("❌ " + message);
            updateConnectionInfo();
        }
        // Other message types will be handled by specific controllers
    }

    @FXML
    protected void onNewMeetingClick() throws Exception {
        // Ensure WebSocket is connected before creating meeting
        HelloApplication.ensureWebSocketConnection();

        // Generate a unique meeting ID using HelloApplication's system
        String meetingId = HelloApplication.createNewMeeting();

        // Join the meeting via WebSocket
        SimpleWebSocketClient client = HelloApplication.getWebSocketClient();
        if (client != null && client.isConnected()) {
            String username = HelloApplication.getLoggedInUser();
            client.sendMessage("MEETING_CREATED", meetingId, username, "created and joined meeting");
        } else {
            showPopup("Connection Issue", "Not connected to server. Some features may not work.");
        }

        HelloApplication.setRoot("new-meeting-view.fxml");
    }

    @FXML
    protected void onJoinClick() throws Exception {
        // Ensure WebSocket is connected before joining
        HelloApplication.ensureWebSocketConnection();
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
    protected void onSettingsClick() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("settings-view.fxml"));
            Scene scene = new Scene(loader.load(), 600, 500);

            SettingsController controller = loader.getController();
            controller.setUser(HelloApplication.getLoggedInUser());
            controller.setStage((Stage) welcomeLabel.getScene().getWindow());

            Stage settingsStage = new Stage();
            settingsStage.setTitle("Server Settings");
            settingsStage.setScene(scene);
            settingsStage.initOwner((Stage) welcomeLabel.getScene().getWindow());
            settingsStage.showAndWait();

            // Update connection info after settings might have changed
            updateConnectionInfo();

        } catch (Exception e) {
            e.printStackTrace();
            showPopup("Error", "❌ Failed to open Settings!");
        }
    }

    @FXML
    protected void onRecordingsClick() throws Exception {
        HelloApplication.setRoot("recordings-view.fxml");
    }

    @FXML
    protected void onQuickConnectClick() {
        Stage currentStage = (Stage) welcomeLabel.getScene().getWindow();
        ServerConfigDialog dialog = ServerConfigDialog.showDialog(currentStage);

        if (dialog != null && dialog.isConnected()) {
            updateConnectionInfo();
            showPopup("Success", "Connected to: " + dialog.getServerUrl());
        }
    }

    @FXML
    protected void onTestConnectionClick() {
        // Test the current connection
        SimpleWebSocketClient client = HelloApplication.getWebSocketClient();
        if (client != null && client.isConnected()) {
            showPopup("Connection Status", "🟢 Connected to server: " + HelloApplication.getCurrentServerUrl());
        } else {
            showPopup("Connection Status", "🔴 Not connected to server. Please check your connection settings.");
        }
        updateConnectionInfo();
    }

    @FXML
    protected void onLogoutClick() throws Exception {
        // Remove connection listener before logout
        HelloApplication.setConnectionStatusListener(null);

        // Disconnect WebSocket before logging out
        SimpleWebSocketClient client = HelloApplication.getWebSocketClient();
        if (client != null) {
            client.disconnect();
        }
        HelloApplication.logout();
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