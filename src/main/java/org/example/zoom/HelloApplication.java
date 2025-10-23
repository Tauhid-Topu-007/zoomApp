package org.example.zoom;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.zoom.websocket.SimpleWebSocketClient;

import java.util.ArrayList;
import java.util.List;

public class HelloApplication extends Application {

    private static Stage primaryStage;
    private static String loggedInUser;
    private static String activeMeetingId;
    private static final List<String> activeParticipants = new ArrayList<>();
    private static SimpleWebSocketClient webSocketClient;

    // New fields for server configuration with persistence
    private static String serverIp = "localhost";
    private static String serverPort = "8887";

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;

        // Initialize database tables
        Database.initializeDatabase();

        setRoot("login-view.fxml");
        stage.setTitle("Zoom Project");
        stage.show();
    }

    public static void setRoot(String fxml) throws Exception {
        FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource(fxml));
        Scene scene = new Scene(loader.load());
        primaryStage.setScene(scene);

        // Fullscreen only for meeting
        primaryStage.setFullScreen("meeting-view.fxml".equals(fxml));

        // Pass stage reference to controllers that need it
        Object controller = loader.getController();
        if (controller instanceof ChatController) {
            ((ChatController) controller).setStage(primaryStage);
        } else if (controller instanceof SettingsController) {
            ((SettingsController) controller).setStage(primaryStage);
        }
    }

    public static void setLoggedInUser(String username) {
        loggedInUser = username;

        // Load user's saved server configuration
        loadUserServerConfig(username);

        // Initialize WebSocket when user logs in
        if (username != null) {
            initializeWebSocket(username);
        }
    }

    public static String getLoggedInUser() {
        return loggedInUser;
    }

    public static void logout() throws Exception {
        // Disconnect WebSocket before logout
        if (webSocketClient != null) {
            webSocketClient.disconnect();
            webSocketClient = null;
        }

        loggedInUser = null;
        activeParticipants.clear();
        activeMeetingId = null;
        setRoot("login-view.fxml");
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    // Load user's saved server configuration from database
    private static void loadUserServerConfig(String username) {
        Database.ServerConfig config = Database.getServerConfig(username);
        if (config != null) {
            serverIp = config.getServerIp();
            serverPort = config.getServerPort();
            System.out.println("🎯 Loaded user server config: " + config.getServerUrl());
        } else {
            // Use default configuration
            serverIp = "localhost";
            serverPort = "8887";
            System.out.println("🎯 Using default server config: ws://localhost:8887");
        }
    }

    // WebSocket Client Management
    private static void initializeWebSocket(String username) {
        String url = getCurrentServerUrl();
        initializeWebSocketWithUrl(url);
    }

    // New method to initialize with specific URL
    private static void initializeWebSocketWithUrl(String serverUrl) {
        try {
            System.out.println("🎯 Initializing WebSocket connection to: " + serverUrl);

            webSocketClient = new SimpleWebSocketClient(serverUrl, HelloApplication::handleWebSocketMessage);
            if (loggedInUser != null) {
                webSocketClient.setCurrentUser(loggedInUser);
            }

        } catch (Exception e) {
            System.err.println("❌ Failed to initialize WebSocket: " + e.getMessage());
            handleConnectionFailure(serverUrl);
        }
    }

    // Handle connection failures gracefully
    public static void handleConnectionFailure(String attemptedUrl) {
        System.err.println("❌ Connection failed to: " + attemptedUrl);

        // Try fallback to localhost if not already trying localhost
        if (!attemptedUrl.contains("localhost")) {
            System.out.println("🔄 Falling back to localhost...");
            initializeWebSocketWithUrl("ws://localhost:8887");
        } else {
            // Show connection dialog to user
            javafx.application.Platform.runLater(() -> {
                Stage primaryStage = getPrimaryStage();
                if (primaryStage != null && loggedInUser != null) {
                    ServerConfigDialog dialog = ServerConfigDialog.showDialog(primaryStage);
                    if (dialog != null && dialog.isConnected()) {
                        System.out.println("✅ User configured new server: " + dialog.getServerUrl());
                    }
                }
            });
        }
    }

    public static void setWebSocketClient(SimpleWebSocketClient client) {
        webSocketClient = client;
    }

    public static SimpleWebSocketClient getWebSocketClient() {
        return webSocketClient;
    }

    // Server configuration methods with persistence
    public static void setServerConfig(String ip, String port) {
        serverIp = ip;
        serverPort = port;
        System.out.println("🎯 Server config updated: " + ip + ":" + port);

        // Save to database if user is logged in
        if (loggedInUser != null) {
            Database.saveServerConfig(loggedInUser, ip, port);
        }
    }

    public static String getCurrentServerUrl() {
        return "ws://" + serverIp + ":" + serverPort;
    }

    public static String getServerIp() {
        return serverIp;
    }

    public static String getServerPort() {
        return serverPort;
    }

    public static void reinitializeWebSocket(String newUrl) {
        // Disconnect existing client
        if (webSocketClient != null) {
            webSocketClient.disconnect();
            webSocketClient = null;
        }

        // Create new client
        if (loggedInUser != null) {
            initializeWebSocketWithUrl(newUrl);
        }
    }

    // Handle incoming WebSocket messages
    private static void handleWebSocketMessage(String message) {
        System.out.println("📨 Application received: " + message);

        // Parse message: "TYPE|MEETING_ID|USERNAME|CONTENT"
        String[] parts = message.split("\\|", 4);
        if (parts.length >= 4) {
            String type = parts[0];
            String meetingId = parts[1];
            String username = parts[2];
            String content = parts[3];

            switch (type) {
                case "USER_JOINED":
                    addParticipant(username);
                    break;
                case "USER_LEFT":
                    activeParticipants.remove(username);
                    break;
                case "MEETING_CREATED":
                    setActiveMeetingId(meetingId);
                    break;
                case "CONNECTION_STATUS":
                    // Handle connection status updates
                    System.out.println("🔗 Connection status: " + content);
                    break;
            }
        }

        // Forward message to current controller if it's a ChatController
        forwardMessageToCurrentController(message);
    }

    private static void forwardMessageToCurrentController(String message) {
        try {
            // Get the current controller and forward the message if it's a ChatController
            // This is a simplified approach - in a real app you'd use a more robust event system
            Scene currentScene = primaryStage.getScene();
            if (currentScene != null && currentScene.getUserData() instanceof ChatController) {
                ChatController chatController = (ChatController) currentScene.getUserData();
                chatController.handleWebSocketMessage(message);
            }
        } catch (Exception e) {
            // Ignore - controller might not be ready or might not be a ChatController
        }
    }

    // Meeting ID storage
    public static void setActiveMeetingId(String meetingId) {
        activeMeetingId = meetingId;
        System.out.println("🎯 Active meeting set to: " + meetingId);
    }

    public static String getActiveMeetingId() {
        return activeMeetingId;
    }

    // Participants management
    public static void addParticipant(String name) {
        if (!activeParticipants.contains(name)) {
            activeParticipants.add(name);
            System.out.println("👥 Participant added: " + name);

            // Notify via WebSocket if connected
            if (webSocketClient != null && webSocketClient.isConnected()) {
                webSocketClient.sendMessage("USER_JOINED", getActiveMeetingId(), name, "joined the meeting");
            }
        }
    }

    public static List<String> getActiveParticipants() {
        return new ArrayList<>(activeParticipants);
    }

    public static void clearParticipants() {
        System.out.println("👥 Cleared all participants");
        activeParticipants.clear();
    }

    public static void removeParticipant(String name) {
        if (activeParticipants.remove(name)) {
            System.out.println("👥 Participant removed: " + name);

            // Notify via WebSocket if connected
            if (webSocketClient != null && webSocketClient.isConnected()) {
                webSocketClient.sendMessage("USER_LEFT", getActiveMeetingId(), name, "left the meeting");
            }
        }
    }

    // WebSocket utility methods
    public static boolean isWebSocketConnected() {
        return webSocketClient != null && webSocketClient.isConnected();
    }

    public static String getConnectionStatus() {
        if (isWebSocketConnected()) {
            return "🟢 Connected to " + getCurrentServerUrl().replace("ws://", "");
        } else {
            return "🔴 Disconnected";
        }
    }

    public static String getConnectionStatusShort() {
        if (isWebSocketConnected()) {
            return "🟢 Connected";
        } else {
            return "🔴 Disconnected";
        }
    }

    // Send message via WebSocket
    public static void sendWebSocketMessage(String type, String meetingId, String content) {
        if (webSocketClient != null && webSocketClient.isConnected() && loggedInUser != null) {
            webSocketClient.sendMessage(type, meetingId, loggedInUser, content);
        } else {
            System.err.println("❌ Cannot send message - WebSocket not connected or user not logged in");
        }
    }

    // Create a new meeting with WebSocket notification
    public static String createNewMeeting() {
        String meetingId = generateMeetingId();
        setActiveMeetingId(meetingId);

        // Notify via WebSocket
        sendWebSocketMessage("MEETING_CREATED", meetingId, "New meeting created");

        System.out.println("🎯 New meeting created: " + meetingId);
        return meetingId;
    }

    private static String generateMeetingId() {
        return String.valueOf((int) (Math.random() * 900000) + 100000);
    }

    // Get server history for current user
    public static List<Database.ServerConfig> getServerHistory() {
        if (loggedInUser != null) {
            return Database.getServerHistory(loggedInUser);
        }
        return new ArrayList<>();
    }

    // Save user preference
    public static void saveUserPreference(String key, String value) {
        if (loggedInUser != null) {
            Database.saveUserPreference(loggedInUser, key, value);
        }
    }

    // Get user preference
    public static String getUserPreference(String key) {
        if (loggedInUser != null) {
            return Database.getUserPreference(loggedInUser, key);
        }
        return null;
    }

    @Override
    public void stop() throws Exception {
        // Cleanup when application closes
        if (webSocketClient != null) {
            webSocketClient.disconnect();
        }

        // Save current server config if user is logged in
        if (loggedInUser != null) {
            Database.saveServerConfig(loggedInUser, serverIp, serverPort);
        }

        super.stop();
    }

    public static void main(String[] args) {
        launch();
    }
}