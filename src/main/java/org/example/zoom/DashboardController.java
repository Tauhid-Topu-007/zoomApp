package org.example.zoom;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.stage.Stage;
import org.example.zoom.websocket.SimpleWebSocketClient;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class DashboardController implements HelloApplication.ConnectionStatusListener {

    @FXML
    private Label welcomeLabel;

    @FXML
    private Label connectionInfoLabel;

    @FXML
    private ScrollPane mainScrollPane;

    private boolean wasConnected = false;
    private AtomicBoolean alertInProgress = new AtomicBoolean(false);
    private long lastConnectionChangeTime = 0;
    private static final long MIN_ALERT_INTERVAL = 3000;

    @FXML
    public void initialize() {
        // Configure scroll pane
        if (mainScrollPane != null) {
            mainScrollPane.setFitToWidth(true);
            mainScrollPane.setFitToHeight(true);
            mainScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            mainScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            mainScrollPane.setStyle("-fx-background: #2c3e50; -fx-border-color: #2c3e50;");
        }

        // Always pull the logged-in user from HelloApplication
        String user = HelloApplication.getLoggedInUser();
        if (user != null) {
            welcomeLabel.setText("Welcome, " + user + "!");

            // Register as connection status listener
            HelloApplication.setConnectionStatusListener(this);

            // Update connection info immediately
            updateConnectionInfo();

            // Set initial connection state
            wasConnected = HelloApplication.isWebSocketConnected();

            // Auto-connect to Node.js server if not connected
            if (!HelloApplication.isWebSocketConnected()) {
                Platform.runLater(() -> {
                    new Thread(() -> {
                        try {
                            Thread.sleep(1500); // Wait for UI to initialize
                            Platform.runLater(this::autoConnectToServer);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                    }).start();
                });
            }
        }
    }

    /**
     * Auto-connect to server on startup
     */
    private void autoConnectToServer() {
        System.out.println("🔄 Auto-connecting to server...");

        if (isNodeJSServerRunning()) {
            System.out.println("✅ Node.js server detected, connecting...");
            HelloApplication.discoverAndConnectToServer(); // Use existing method
        } else {
            showPopup("Server Not Running",
                    "🚨 Node.js WebSocket Server is not running!\n\n" +
                            "To start the server:\n" +
                            "1. Open terminal in your 'server' folder\n" +
                            "2. Run: node server.js\n" +
                            "3. Look for 'SERVER STARTED' message\n" +
                            "4. Then click 'Quick Connect'\n\n" +
                            "💡 Keep the server terminal open!");
        }
    }

    public void updateConnectionInfo() {
        if (connectionInfoLabel != null) {
            String status = HelloApplication.getConnectionStatusShort();
            String serverUrl = HelloApplication.getCurrentServerUrl();

            connectionInfoLabel.setText(status + " | " + serverUrl.replace("ws://", ""));

            if (HelloApplication.isWebSocketConnected()) {
                connectionInfoLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 12px; -fx-padding: 5; -fx-background-color: #34495e; -fx-background-radius: 5;");
            } else {
                connectionInfoLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 12px; -fx-padding: 5; -fx-background-color: #34495e; -fx-background-radius: 5;");
            }
        }
    }

    @Override
    public void onConnectionStatusChanged(boolean connected, String status) {
        System.out.println("🔗 Connection status changed: " + connected + " - " + status);
        updateConnectionInfo();
        handleConnectionStateChange(connected, status);
    }

    private void handleConnectionStateChange(boolean connected, String status) {
        long currentTime = System.currentTimeMillis();

        if (alertInProgress.get() || (currentTime - lastConnectionChangeTime < MIN_ALERT_INTERVAL)) {
            return;
        }

        if (connected && !wasConnected) {
            showConnectionSuccessPopup();
            lastConnectionChangeTime = currentTime;
        } else if (!connected && wasConnected) {
            showConnectionLostPopup();
            lastConnectionChangeTime = currentTime;
        }

        wasConnected = connected;
    }

    private void showConnectionSuccessPopup() {
        if (!alertInProgress.compareAndSet(false, true)) return;

        Platform.runLater(() -> {
            try {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Connection Established");
                alert.setHeaderText("✅ Successfully connected to server!");
                alert.setContentText("You are now connected to: " + HelloApplication.getCurrentServerUrl());
                alert.setOnHidden(e -> alertInProgress.set(false));
                alert.showAndWait();
            } catch (Exception e) {
                alertInProgress.set(false);
            }
        });
    }

    private void showConnectionLostPopup() {
        if (!alertInProgress.compareAndSet(false, true)) return;

        Platform.runLater(() -> {
            try {
                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Connection Lost");
                alert.setHeaderText("Disconnected from server");
                alert.setContentText("Connection to the server has been lost. Reconnecting...");
                alert.setOnHidden(e -> alertInProgress.set(false));
                alert.showAndWait();
            } catch (Exception e) {
                alertInProgress.set(false);
            }
        });
    }

    @FXML
    protected void onNetworkDiagnosticClick() {
        new Thread(() -> {
            boolean serverRunning = isNodeJSServerRunning();
            List<String> servers = HelloApplication.discoverAvailableServers();

            Platform.runLater(() -> {
                StringBuilder message = new StringBuilder();
                message.append("🔍 Network Diagnostic Results:\n\n");
                message.append("Node.js Server: ").append(serverRunning ? "✅ RUNNING" : "❌ NOT RUNNING").append("\n\n");

                if (servers.isEmpty()) {
                    message.append("❌ No servers found\n\n");
                    message.append("To fix:\n");
                    message.append("1. Start server: 'node server.js'\n");
                    message.append("2. Use 'Manual Connect' with ws://localhost:8887");
                } else {
                    message.append("✅ Found ").append(servers.size()).append(" server(s):\n");
                    servers.forEach(server -> message.append("• ").append(server).append("\n"));
                    message.append("\nClick 'Quick Connect' to connect");
                }

                showPopup("Network Diagnostic", message.toString());
            });
        }).start();
    }

    /**
     * Check if Node.js server is running
     */
    private boolean isNodeJSServerRunning() {
        try (java.net.Socket socket = new java.net.Socket("localhost", 8887)) {
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    @FXML
    protected void onNewMeetingClick() throws Exception {
        // Check WebSocket connection before creating meeting
        if (!HelloApplication.isWebSocketConnected()) {
            showPopup("Connection Required",
                    "You need to be connected to the Node.js server to create a meeting.\n\n" +
                            "To fix this:\n" +
                            "1. Make sure Node.js server is running\n" +
                            "2. Use 'Quick Connect' or 'Manual Connect'\n" +
                            "3. Try connecting to: ws://localhost:8887");
            return;
        }

        // Ensure WebSocket is connected before creating meeting
        HelloApplication.ensureWebSocketConnection();

        // Generate a unique meeting ID using HelloApplication's system
        String meetingId = HelloApplication.createNewMeeting();

        // Join the meeting via WebSocket
        SimpleWebSocketClient client = HelloApplication.getWebSocketClient();
        if (client != null && client.isConnected()) {
            String username = HelloApplication.getLoggedInUser();
            client.sendMessage("MEETING_CREATED", meetingId, username, "created and joined meeting");
            System.out.println("📨 Sent MEETING_CREATED to Node.js server");
        }

        HelloApplication.setRoot("new-meeting-view.fxml");
    }

    @FXML
    protected void onJoinClick() throws Exception {
        // Check WebSocket connection before joining
        if (!HelloApplication.isWebSocketConnected()) {
            showPopup("Connection Required",
                    "You need to be connected to the Node.js server to join a meeting.\n\n" +
                            "Make sure:\n" +
                            "• Node.js server is running\n" +
                            "• You're connected via WebSocket\n" +
                            "• Server URL: ws://localhost:8887");
            return;
        }

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
        if (!HelloApplication.isWebSocketConnected()) {
            showPopup("Connection Required",
                    "You need to be connected to the Node.js server to share screen.\n\n" +
                            "Server must be running and connected via WebSocket.");
            return;
        }

        SimpleWebSocketClient client = HelloApplication.getWebSocketClient();
        if (client != null && client.isConnected()) {
            HelloApplication.setRoot("share-screen-view.fxml");
        } else {
            showPopup("Connection Required", "Please check your connection to the Node.js server.");
        }
    }

    @FXML
    protected void onContactsClick() throws Exception {
        HelloApplication.setRoot("contacts-view.fxml");
    }

    @FXML
    protected void onChatClick() throws Exception {
        try {
            // Check connection first
            if (!HelloApplication.isWebSocketConnected()) {
                showPopup("Connection Required", "Connect to Node.js server first to use chat.");
                return;
            }

            FXMLLoader loader = new FXMLLoader(getClass().getResource("chat-view.fxml"));
            Scene scene = new Scene(loader.load(), 800, 600);

            // Get the chat controller and pass the WebSocket client
            ChatController controller = loader.getController();

            // Initialize chat with WebSocket client
            if (controller != null) {
                SimpleWebSocketClient client = HelloApplication.getWebSocketClient();
                controller.setWebSocketClient(client);
                controller.setCurrentUser(HelloApplication.getLoggedInUser());
                System.out.println("💬 Chat connected to Node.js server");
            }

            Stage stage = (Stage) welcomeLabel.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Zoom Chat - " + HelloApplication.getLoggedInUser());

        } catch (Exception e) {
            e.printStackTrace();
            showPopup("Error", "Failed to open Chat! " + e.getMessage());
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
            showPopup("Error", "Failed to open Settings!");
        }
    }

    @FXML
    protected void onRecordingsClick() throws Exception {
        HelloApplication.setRoot("recordings-view.fxml");
    }

    @FXML
    protected void onQuickConnectClick() {
        // Enhanced Quick Connect that prioritizes Node.js server
        showPopup("Quick Connect",
                "🔗 Connecting to Node.js WebSocket Server...\n\n" +
                        "This will automatically find and connect to your\n" +
                        "Node.js server running on port 8887.");

        // Use the enhanced network discovery method
        HelloApplication.discoverAndConnectToServer();
    }

    @FXML
    protected void onManualConnectClick() {
        // Show manual connection dialog - it should handle the connection
        HelloApplication.showManualConnectionDialog();
    }

    @FXML
    protected void onConnectionGuideClick() {
        showConnectionGuide();
    }

    private void showConnectionGuide() {
        showPopup("Node.js Server Connection Guide",
                "🚀 HOW TO CONNECT TO NODE.JS SERVER:\n\n" +
                        "1. Start Node.js Server:\n" +
                        "   • Open terminal in server folder\n" +
                        "   • Run: node server.js\n" +
                        "   • Look for 'SERVER STARTED' message\n\n" +
                        "2. Connect Java Client:\n" +
                        "   • Use 'Quick Connect' (automatic)\n" +
                        "   • Or 'Manual Connect' with: ws://localhost:8887\n\n" +
                        "3. Verify Connection:\n" +
                        "   • Green connection status = ✅ Connected\n" +
                        "   • Server console shows new connections\n\n" +
                        "💡 Tip: Keep the Node.js server terminal open!");
    }

    @FXML
    protected void onTestConnectionClick() {
        // Test the current connection
        SimpleWebSocketClient client = HelloApplication.getWebSocketClient();
        if (client != null && client.isConnected()) {
            showPopup("Connection Status",
                    "✅ Connected to Node.js Server!\n\n" +
                            "Server: " + HelloApplication.getCurrentServerUrl() + "\n" +
                            "Status: Active and Ready\n" +
                            "You can now create/join meetings!");
        } else {
            showPopup("Connection Status",
                    "❌ Not connected to Node.js server.\n\n" +
                            "To fix this:\n" +
                            "1. Start Node.js server: 'node server.js'\n" +
                            "2. Use 'Quick Connect' or 'Manual Connect'\n" +
                            "3. Ensure port 8887 is available");
        }
        updateConnectionInfo();
    }

    @FXML
    protected void onStartServerClick() {
        showPopup("Start Node.js Server",
                "🚀 START NODE.JS SERVER:\n\n" +
                        "1. Open terminal/command prompt\n" +
                        "2. Navigate to server folder:\n" +
                        "   cd path/to/your/server/folder\n" +
                        "3. Start the server:\n" +
                        "   node server.js\n\n" +
                        "✅ Look for this message:\n" +
                        "   'ZOOM WEB SOCKET SERVER STARTED'\n\n" +
                        "Then use 'Quick Connect' in the Java client!");
    }

    @FXML
    protected void onLogoutClick() throws Exception {
        // Remove connection listener before logout
        HelloApplication.setConnectionStatusListener(null);

        // Disconnect WebSocket before logging out
        SimpleWebSocketClient client = HelloApplication.getWebSocketClient();
        if (client != null) {
            client.disconnect();
            System.out.println("Disconnected from Node.js server");
        }
        HelloApplication.logout();
    }

    private void showPopup(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    // Clean up method to be called when controller is being destroyed
    public void cleanup() {
        // Remove the connection listener
        HelloApplication.setConnectionStatusListener(null);
        alertInProgress.set(false);
    }
}