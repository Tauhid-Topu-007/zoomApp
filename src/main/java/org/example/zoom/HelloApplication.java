package org.example.zoom;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.zoom.websocket.SimpleWebSocketClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class HelloApplication extends Application {

    private static Stage primaryStage;
    private static String loggedInUser;
    private static String activeMeetingId;
    private static final List<String> activeParticipants = new ArrayList<>();
    private static SimpleWebSocketClient webSocketClient;

    // New fields for server configuration with persistence
    private static String serverIp = "localhost";
    private static String serverPort = "8887";

    // Meeting management
    private static final Map<String, MeetingInfo> activeMeetings = new HashMap<>();
    private static boolean isMeetingHost = false;

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
        // Leave current meeting if any
        leaveCurrentMeeting();

        // Disconnect WebSocket before logout
        if (webSocketClient != null) {
            webSocketClient.disconnect();
            webSocketClient = null;
        }

        loggedInUser = null;
        activeParticipants.clear();
        activeMeetingId = null;
        isMeetingHost = false;
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
                    updateMeetingParticipants(meetingId, username, true);
                    break;
                case "USER_LEFT":
                    activeParticipants.remove(username);
                    updateMeetingParticipants(meetingId, username, false);
                    break;
                case "MEETING_CREATED":
                    createMeeting(meetingId, username);
                    break;
                case "MEETING_ENDED":
                    endMeeting(meetingId);
                    break;
                case "MEETING_VALIDATION":
                    handleMeetingValidation(meetingId, content);
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

    // Meeting Management Methods
    public static void setActiveMeetingId(String meetingId) {
        activeMeetingId = meetingId;
        System.out.println("🎯 Active meeting set to: " + meetingId);
    }

    public static String getActiveMeetingId() {
        return activeMeetingId;
    }

    public static void setMeetingHost(boolean host) {
        isMeetingHost = host;
        System.out.println("🎯 Meeting host status: " + (host ? "Host" : "Participant"));
    }

    public static boolean isMeetingHost() {
        return isMeetingHost;
    }

    // Create a new meeting with proper validation
    public static String createNewMeeting() {
        String meetingId = generateMeetingId();

        // Create meeting info
        String hostName = getLoggedInUser();
        if (hostName == null || hostName.isEmpty()) {
            hostName = "Host";
        }

        MeetingInfo meetingInfo = new MeetingInfo(meetingId, hostName);
        activeMeetings.put(meetingId, meetingInfo);

        setActiveMeetingId(meetingId);
        setMeetingHost(true);

        // Add host as first participant
        addParticipant(hostName);

        // Notify via WebSocket
        if (isWebSocketConnected()) {
            sendWebSocketMessage("MEETING_CREATED", meetingId, "New meeting created by " + hostName);
            System.out.println("🎯 New meeting created via WebSocket: " + meetingId + " by " + hostName);
        } else {
            System.out.println("🎯 New meeting created locally: " + meetingId + " by " + hostName + " (WebSocket offline)");
        }

        return meetingId;
    }

    // Join an existing meeting with validation
    public static boolean joinMeeting(String meetingId, String participantName) {
        if (!isValidMeeting(meetingId)) {
            System.err.println("❌ Invalid meeting ID: " + meetingId);
            return false;
        }

        setActiveMeetingId(meetingId);
        setMeetingHost(false);

        // Add participant
        addParticipant(participantName);

        // Notify via WebSocket
        if (isWebSocketConnected()) {
            sendWebSocketMessage("USER_JOINED", meetingId, participantName + " joined the meeting");
            System.out.println("✅ Joined meeting via WebSocket: " + meetingId + " as " + participantName);
        } else {
            System.out.println("✅ Joined meeting locally: " + meetingId + " as " + participantName + " (WebSocket offline)");
        }

        return true;
    }

    // Validate meeting existence
    public static boolean isValidMeeting(String meetingId) {
        // Check if meeting exists in our active meetings
        if (activeMeetings.containsKey(meetingId)) {
            return true;
        }

        // For demo purposes, accept any 6-digit meeting ID
        // In a real application, you would check against a server/database
        return meetingId != null && meetingId.matches("\\d{6}");
    }

    // Create a test meeting for quick join functionality
    public static void createMeetingForTesting(String meetingId) {
        // Create meeting info for testing
        MeetingInfo meetingInfo = new MeetingInfo(meetingId, "TestHost");
        activeMeetings.put(meetingId, meetingInfo);
        System.out.println("🎯 Test meeting created: " + meetingId);
    }

    // Leave current meeting
    public static void leaveCurrentMeeting() {
        if (activeMeetingId != null && loggedInUser != null) {
            // Notify via WebSocket
            if (isWebSocketConnected()) {
                sendWebSocketMessage("USER_LEFT", activeMeetingId, loggedInUser + " left the meeting");
            }

            // Remove from participants
            removeParticipant(loggedInUser);

            // If host leaves, end the meeting
            if (isMeetingHost) {
                endMeeting(activeMeetingId);
            }

            System.out.println("🚪 Left meeting: " + activeMeetingId);
        }

        // Reset meeting state
        activeMeetingId = null;
        isMeetingHost = false;
    }

    // End a meeting (host only)
    public static void endMeeting(String meetingId) {
        if (activeMeetings.containsKey(meetingId)) {
            // Notify all participants
            if (isWebSocketConnected()) {
                sendWebSocketMessage("MEETING_ENDED", meetingId, "Meeting ended by host");
            }

            // Clear participants and remove meeting
            activeMeetings.remove(meetingId);
            activeParticipants.clear();

            System.out.println("🔚 Meeting ended: " + meetingId);
        }
    }

    // Enhanced WebSocket connection check and reconnect
    public static boolean ensureWebSocketConnection() {
        if (!isWebSocketConnected() && loggedInUser != null) {
            System.out.println("🔄 Attempting to reconnect WebSocket...");
            initializeWebSocket(loggedInUser);
            return isWebSocketConnected();
        }
        return isWebSocketConnected();
    }

    // Participants management
    public static void addParticipant(String name) {
        if (name != null && !activeParticipants.contains(name)) {
            activeParticipants.add(name);
            System.out.println("👥 Participant added: " + name);
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
        }
    }

    // Meeting info management
    private static void createMeeting(String meetingId, String host) {
        MeetingInfo meetingInfo = new MeetingInfo(meetingId, host);
        activeMeetings.put(meetingId, meetingInfo);
        System.out.println("📋 Meeting registered: " + meetingId + " hosted by " + host);
    }

    private static void updateMeetingParticipants(String meetingId, String username, boolean joined) {
        MeetingInfo meetingInfo = activeMeetings.get(meetingId);
        if (meetingInfo != null) {
            if (joined) {
                meetingInfo.addParticipant(username);
            } else {
                meetingInfo.removeParticipant(username);
            }
        }
    }

    private static void handleMeetingValidation(String meetingId, String status) {
        System.out.println("🔍 Meeting validation for " + meetingId + ": " + status);
        // Handle validation responses from server
    }

    // Utility methods
    private static String generateMeetingId() {
        return String.valueOf((int) (Math.random() * 900000) + 100000);
    }

    public static Map<String, MeetingInfo> getActiveMeetings() {
        return new HashMap<>(activeMeetings);
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

    // Send message via WebSocket - FIXED METHOD SIGNATURE
    public static void sendWebSocketMessage(String type, String meetingId, String content) {
        if (webSocketClient != null && webSocketClient.isConnected() && loggedInUser != null) {
            webSocketClient.sendMessage(type, meetingId, loggedInUser, content);
        } else {
            System.err.println("❌ Cannot send message - WebSocket not connected or user not logged in");
        }
    }

    // Alternative method for sending messages with custom username
    public static void sendWebSocketMessage(String type, String meetingId, String username, String content) {
        if (webSocketClient != null && webSocketClient.isConnected()) {
            webSocketClient.sendMessage(type, meetingId, username, content);
        } else {
            System.err.println("❌ Cannot send message - WebSocket not connected");
        }
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
        // Leave current meeting if any
        leaveCurrentMeeting();

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

    // Inner class for meeting information
    public static class MeetingInfo {
        private String meetingId;
        private String host;
        private List<String> participants;
        private long createdTime;

        public MeetingInfo(String meetingId, String host) {
            this.meetingId = meetingId;
            this.host = host;
            this.participants = new ArrayList<>();
            this.createdTime = System.currentTimeMillis();

            // Add host as first participant
            if (host != null && !host.isEmpty()) {
                participants.add(host);
            }
        }

        public String getMeetingId() { return meetingId; }
        public String getHost() { return host; }
        public List<String> getParticipants() { return new ArrayList<>(participants); }
        public long getCreatedTime() { return createdTime; }

        public void addParticipant(String participant) {
            if (participant != null && !participants.contains(participant)) {
                participants.add(participant);
            }
        }

        public void removeParticipant(String participant) {
            participants.remove(participant);
        }

        public int getParticipantCount() {
            return participants.size();
        }

        public boolean isParticipant(String username) {
            return participants.contains(username);
        }

        @Override
        public String toString() {
            return "MeetingInfo{" +
                    "meetingId='" + meetingId + '\'' +
                    ", host='" + host + '\'' +
                    ", participants=" + participants +
                    ", participantCount=" + getParticipantCount() +
                    '}';
        }
    }
}