package org.example.zoom;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.zoom.websocket.SimpleWebSocketClient;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.HashMap;
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

    // Connection status tracking
    private static boolean connectionInitialized = false;
    private static ConnectionStatusListener connectionStatusListener;

    // Meeting management
    private static final Map<String, MeetingInfo> activeMeetings = new HashMap<>();
    private static boolean isMeetingHost = false;

    // Audio controls management
    private static boolean audioMuted = false;
    private static boolean isDeafened = false;
    private static boolean allMuted = false;
    private static AudioControlsController audioControlsController;

    // Video controls management
    private static boolean videoOn = false;
    private static boolean isRecording = false;
    private static boolean virtualBackgroundEnabled = false;
    private static VideoControlsController videoControlsController;

    // Interface for connection status updates
    public interface ConnectionStatusListener {
        void onConnectionStatusChanged(boolean connected, String status);
    }

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
        } else if (controller instanceof AudioControlsController) {
            audioControlsController = (AudioControlsController) controller;
        } else if (controller instanceof VideoControlsController) {
            videoControlsController = (VideoControlsController) controller;
        } else if (controller instanceof DashboardController) {
            // DashboardController now implements ConnectionStatusListener
            DashboardController dashboardController = (DashboardController) controller;
            // Register as connection status listener
            setConnectionStatusListener(dashboardController);
            System.out.println("✅ Dashboard controller loaded and registered for connection updates");
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
        // Remove connection listener before logout
        setConnectionStatusListener(null);

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
        connectionInitialized = false;

        // Reset audio state
        audioMuted = false;
        isDeafened = false;
        allMuted = false;
        audioControlsController = null;

        // Reset video state
        videoOn = false;
        isRecording = false;
        virtualBackgroundEnabled = false;
        videoControlsController = null;

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

    // WebSocket Client Management - IMPROVED
    private static void initializeWebSocket(String username) {
        String url = getCurrentServerUrl();
        initializeWebSocketWithUrl(url);
    }

    // New method to initialize with specific URL - IMPROVED
    private static void initializeWebSocketWithUrl(String serverUrl) {
        try {
            System.out.println("🎯 Initializing WebSocket connection to: " + serverUrl);

            // Disconnect existing client if any
            if (webSocketClient != null) {
                webSocketClient.disconnect();
                webSocketClient = null;
            }

            webSocketClient = new SimpleWebSocketClient(serverUrl, HelloApplication::handleWebSocketMessage);
            if (loggedInUser != null) {
                webSocketClient.setCurrentUser(loggedInUser);
            }

            connectionInitialized = true;

            // Start connection monitoring
            startConnectionMonitoring();

        } catch (Exception e) {
            System.err.println("❌ Failed to initialize WebSocket: " + e.getMessage());
            handleConnectionFailure(serverUrl);
        }
    }

    // Start monitoring connection status
    private static void startConnectionMonitoring() {
        Thread monitorThread = new Thread(() -> {
            while (connectionInitialized && loggedInUser != null) {
                try {
                    Thread.sleep(3000); // Check every 3 seconds

                    boolean connected = isWebSocketConnected();
                    String status = connected ? "🟢 Connected" : "🔴 Disconnected";

                    // Notify listener if connection status changed
                    if (connectionStatusListener != null) {
                        Platform.runLater(() -> {
                            connectionStatusListener.onConnectionStatusChanged(connected, status);
                        });
                    }

                    // Attempt reconnection if disconnected
                    if (!connected && webSocketClient != null) {
                        System.out.println("🔄 Attempting to reconnect...");
                        try {
                            // Create new connection
                            initializeWebSocketWithUrl(getCurrentServerUrl());
                        } catch (Exception e) {
                            System.err.println("❌ Reconnection failed: " + e.getMessage());
                        }
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        });
        monitorThread.setDaemon(true);
        monitorThread.start();
    }

    // Set connection status listener
    public static void setConnectionStatusListener(ConnectionStatusListener listener) {
        connectionStatusListener = listener;

        // Immediately notify of current status
        if (listener != null) {
            boolean connected = isWebSocketConnected();
            String status = connected ? "🟢 Connected" : "🔴 Disconnected";
            Platform.runLater(() -> {
                listener.onConnectionStatusChanged(connected, status);
            });
        }
    }

    // Handle connection failures gracefully
    public static void handleConnectionFailure(String attemptedUrl) {
        System.err.println("❌ Connection failed to: " + attemptedUrl);

        Platform.runLater(() -> {
            // Notify listener about connection failure
            if (connectionStatusListener != null) {
                connectionStatusListener.onConnectionStatusChanged(false, "🔴 Connection failed");
            }

            // Show connection dialog to user if they're logged in
            if (loggedInUser != null && primaryStage != null) {
                ServerConfigDialog dialog = ServerConfigDialog.showDialog(primaryStage);
                if (dialog != null && dialog.isConnected()) {
                    System.out.println("✅ User configured new server: " + dialog.getServerUrl());
                    // Reinitialize with new server
                    initializeWebSocketWithUrl(dialog.getServerUrl());
                }
            }
        });
    }

    public static void setWebSocketClient(SimpleWebSocketClient client) {
        webSocketClient = client;
    }

    public static SimpleWebSocketClient getWebSocketClient() {
        return webSocketClient;
    }

    // Server configuration methods with persistence - IMPROVED
    public static void setServerConfig(String ip, String port) {
        serverIp = ip;
        serverPort = port;
        System.out.println("🎯 Server config updated: " + ip + ":" + port);

        // Save to database if user is logged in
        if (loggedInUser != null) {
            Database.saveServerConfig(loggedInUser, ip, port);
        }

        // Reinitialize WebSocket with new configuration
        if (loggedInUser != null) {
            initializeWebSocketWithUrl(getCurrentServerUrl());
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
        initializeWebSocketWithUrl(newUrl);
    }

    // NEW: Get all local IP addresses for network discovery
    public static List<String> getLocalIPAddresses() {
        List<String> ips = new ArrayList<>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (iface.isLoopback() || !iface.isUp()) continue;

                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress addr = addresses.nextElement();
                    if (addr instanceof Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (!ip.startsWith("127.") && !ip.startsWith("169.254.")) {
                            ips.add(ip);
                        }
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("❌ Error getting network interfaces: " + e.getMessage());
        }
        return ips;
    }

    // NEW: Test connection to a specific server
    public static boolean testConnection(String serverUrl) {
        try {
            final boolean[] connectionSuccess = {false};
            final Object lock = new Object();

            SimpleWebSocketClient testClient = new SimpleWebSocketClient(serverUrl, message -> {
                if (message.contains("Connected") || message.contains("Welcome")) {
                    synchronized (lock) {
                        connectionSuccess[0] = true;
                        lock.notifyAll();
                    }
                }
            });

            // Wait for connection result
            synchronized (lock) {
                try {
                    lock.wait(3000); // Wait up to 3 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            testClient.disconnect();
            return connectionSuccess[0];

        } catch (Exception e) {
            System.err.println("❌ Connection test failed for " + serverUrl + ": " + e.getMessage());
            return false;
        }
    }

    // NEW: Find available servers on the network
    public static List<String> discoverAvailableServers() {
        List<String> availableServers = new ArrayList<>();
        List<String> localIPs = getLocalIPAddresses();

        System.out.println("🔍 Discovering available servers...");

        // Always check localhost first
        if (testConnection("ws://localhost:8887")) {
            availableServers.add("localhost:8887");
        }

        // Check common IP ranges
        for (String localIp : localIPs) {
            String baseIp = localIp.substring(0, localIp.lastIndexOf('.') + 1);

            // Test a few common IPs in the same subnet
            for (int i = 1; i <= 10; i++) {
                String testIp = baseIp + i;
                String testUrl = "ws://" + testIp + ":8887";

                if (!testIp.equals(localIp) && testConnection(testUrl)) {
                    availableServers.add(testIp + ":8887");
                    System.out.println("✅ Found server at: " + testUrl);
                }
            }
        }

        return availableServers;
    }

    // Handle incoming WebSocket messages
    private static void handleWebSocketMessage(String message) {
        System.out.println("📨 Application received: " + message);

        // Handle connection status messages first
        if (message.contains("Connected") || message.contains("Welcome")) {
            System.out.println("✅ WebSocket connection confirmed");
            if (connectionStatusListener != null) {
                Platform.runLater(() -> {
                    connectionStatusListener.onConnectionStatusChanged(true, "🟢 Connected");
                });
            }
        } else if (message.contains("ERROR") || message.contains("Failed")) {
            System.out.println("❌ WebSocket error received");
            if (connectionStatusListener != null) {
                Platform.runLater(() -> {
                    connectionStatusListener.onConnectionStatusChanged(false, "🔴 Connection error");
                });
            }
        }

        // Parse message: "TYPE|MEETING_ID|USERNAME|CONTENT"
        String[] parts = message.split("\\|", 4);
        if (parts.length >= 4) {
            String type = parts[0];
            String meetingId = parts[1];
            String username = parts[2];
            String content = parts[3];

            switch (type) {
                case "USER_JOINED":
                    addParticipantToMeeting(meetingId, username);
                    updateMeetingParticipants(meetingId, username, true);
                    break;
                case "USER_LEFT":
                    removeParticipantFromMeeting(meetingId, username);
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
                    System.out.println("🔗 Connection status: " + content);
                    break;
                case "AUDIO_STATUS":
                    handleAudioStatusMessage(username, content);
                    break;
                case "VIDEO_STATUS":
                    handleVideoStatusMessage(username, content);
                    break;
                case "AUDIO_CONTROL":
                    handleAudioControlMessage(username, content);
                    break;
                case "VIDEO_CONTROL":
                    handleVideoControlMessage(username, content);
                    break;
            }
        }

        // Forward message to current controller if it's a ChatController
        forwardMessageToCurrentController(message);
    }

    // Handle audio status messages from other users
    private static void handleAudioStatusMessage(String username, String status) {
        System.out.println("🔊 Audio status from " + username + ": " + status);

        // Update UI if audio controls are active
        if (audioControlsController != null) {
            Platform.runLater(() -> {
                audioControlsController.updateFromServer(status);
            });
        }

        // Add system message for audio status changes
        addSystemMessage(username + " " + status);
    }

    // Handle video status messages from other users
    private static void handleVideoStatusMessage(String username, String status) {
        System.out.println("🎥 Video status from " + username + ": " + status);

        // Update UI if video controls are active
        if (videoControlsController != null) {
            Platform.runLater(() -> {
                videoControlsController.updateFromServer(status);
            });
        }

        // Add system message for video status changes
        addSystemMessage(username + " " + status);
    }

    // Handle audio control messages (mute all, deafen, etc.)
    private static void handleAudioControlMessage(String username, String command) {
        System.out.println("🔊 Audio control from " + username + ": " + command);

        if ("MUTE_ALL".equals(command)) {
            allMuted = true;
            addSystemMessage("Host muted all participants");

            if (audioControlsController != null) {
                Platform.runLater(() -> {
                    audioControlsController.syncWithGlobalState();
                });
            }
        } else if ("UNMUTE_ALL".equals(command)) {
            allMuted = false;
            addSystemMessage("Host unmuted all participants");

            if (audioControlsController != null) {
                Platform.runLater(() -> {
                    audioControlsController.syncWithGlobalState();
                });
            }
        } else if ("DEAFEN_ALL".equals(command)) {
            addSystemMessage("Host deafened all participants");
        }
    }

    // Handle video control messages (record, etc.)
    private static void handleVideoControlMessage(String username, String command) {
        System.out.println("🎥 Video control from " + username + ": " + command);

        if ("START_RECORDING".equals(command)) {
            addSystemMessage("Host started recording the meeting");
        } else if ("STOP_RECORDING".equals(command)) {
            addSystemMessage("Host stopped recording the meeting");
        }
    }

    private static void forwardMessageToCurrentController(String message) {
        try {
            // Get the current controller and forward the message if it's a ChatController
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

        // Notify audio and video controllers about host status change
        if (audioControlsController != null) {
            Platform.runLater(() -> {
                audioControlsController.onHostStatusChanged(host);
            });
        }
        if (videoControlsController != null) {
            Platform.runLater(() -> {
                videoControlsController.onHostStatusChanged(host);
            });
        }
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

        // Add host as first participant to database
        addParticipantToMeeting(meetingId, hostName);

        // Notify via WebSocket
        if (isWebSocketConnected()) {
            sendWebSocketMessage("MEETING_CREATED", meetingId, "New meeting created by " + hostName);
            System.out.println("🎯 New meeting created via WebSocket: " + meetingId + " by " + hostName);
        } else {
            System.out.println("🎯 New meeting created locally: " + meetingId + " by " + hostName + " (WebSocket offline)");
        }

        // Notify controllers about meeting state change
        notifyControllersMeetingStateChanged(true);

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

        // Add participant to database
        addParticipantToMeeting(meetingId, participantName);

        // Notify via WebSocket
        if (isWebSocketConnected()) {
            sendWebSocketMessage("USER_JOINED", meetingId, participantName + " joined the meeting");
            System.out.println("✅ Joined meeting via WebSocket: " + meetingId + " as " + participantName);
        } else {
            System.out.println("✅ Joined meeting locally: " + meetingId + " as " + participantName + " (WebSocket offline)");
        }

        // Notify controllers about meeting state change
        notifyControllersMeetingStateChanged(true);

        return true;
    }

    // Validate meeting existence
    public static boolean isValidMeeting(String meetingId) {
        // Check if meeting exists in our active meetings
        if (activeMeetings.containsKey(meetingId)) {
            return true;
        }

        // For demo purposes, accept any 6-digit meeting ID
        return meetingId != null && meetingId.matches("\\d{6}");
    }

    // Create a test meeting for quick join functionality
    public static void createMeetingForTesting(String meetingId) {
        MeetingInfo meetingInfo = new MeetingInfo(meetingId, "TestHost");
        activeMeetings.put(meetingId, meetingInfo);
        System.out.println("🎯 Test meeting created: " + meetingId);
    }

    // Leave current meeting
    public static void leaveCurrentMeeting() {
        if (activeMeetingId != null && loggedInUser != null) {
            // Remove from database participants
            removeParticipantFromMeeting(activeMeetingId, loggedInUser);

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

        // Stop audio and video when leaving meeting
        if (audioMuted) {
            toggleAudio(); // Unmute when leaving
        }
        if (videoOn) {
            toggleVideo(); // Stop video when leaving
        }
        if (isRecording) {
            toggleRecording(); // Stop recording when leaving
        }

        // Notify controllers about meeting state change
        notifyControllersMeetingStateChanged(false);
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

            // Stop audio and video for all participants
            if (audioMuted) {
                toggleAudio(); // Unmute when meeting ends
            }
            if (videoOn) {
                toggleVideo(); // Stop video when meeting ends
            }
            if (isRecording) {
                toggleRecording(); // Stop recording when meeting ends
            }

            // Notify controllers about meeting state change
            notifyControllersMeetingStateChanged(false);
        }
    }

    // Notify controllers about meeting state changes
    private static void notifyControllersMeetingStateChanged(boolean inMeeting) {
        if (audioControlsController != null) {
            Platform.runLater(() -> {
                audioControlsController.onMeetingStateChanged(inMeeting);
            });
        }
        if (videoControlsController != null) {
            Platform.runLater(() -> {
                videoControlsController.onMeetingStateChanged(inMeeting);
            });
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

    // Database participant management
    public static void addParticipantToMeeting(String meetingId, String username) {
        if (meetingId != null && username != null) {
            Database.addParticipant(meetingId, username);
            addParticipant(username);
        }
    }

    public static void removeParticipantFromMeeting(String meetingId, String username) {
        if (meetingId != null && username != null) {
            Database.removeParticipant(meetingId, username);
            removeParticipant(username);
        }
    }

    public static List<String> getMeetingParticipants(String meetingId) {
        if (meetingId != null) {
            return Database.getParticipants(meetingId);
        }
        return new ArrayList<>();
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
    }

    // Utility methods
    private static String generateMeetingId() {
        return String.valueOf((int) (Math.random() * 900000) + 100000);
    }

    public static Map<String, MeetingInfo> getActiveMeetings() {
        return new HashMap<>(activeMeetings);
    }

    // WebSocket utility methods - IMPROVED
    public static boolean isWebSocketConnected() {
        return webSocketClient != null && webSocketClient.isConnected();
    }

    public static String getConnectionStatus() {
        if (isWebSocketConnected()) {
            return "🟢 Connected to " + getCurrentServerUrl().replace("ws://", "");
        } else {
            return "🔴 Disconnected from " + getCurrentServerUrl().replace("ws://", "");
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

    // ==================== AUDIO CONTROLS MANAGEMENT ====================
    public static void toggleAudio() {
        audioMuted = !audioMuted;
        updateAudioButtonStyles();

        if (audioMuted) {
            addSystemMessage("You muted your audio");
            if (isWebSocketConnected() && getActiveMeetingId() != null) {
                sendWebSocketMessage("AUDIO_STATUS", getActiveMeetingId(), "muted their audio");
            }
        } else {
            addSystemMessage("You unmuted your audio");
            if (isWebSocketConnected() && getActiveMeetingId() != null) {
                sendWebSocketMessage("AUDIO_STATUS", getActiveMeetingId(), "unmuted their audio");
            }
        }

        if (audioControlsController != null) {
            Platform.runLater(() -> {
                audioControlsController.syncWithGlobalState();
            });
        }
    }

    public static void muteAllParticipants() {
        if (!isMeetingHost) {
            addSystemMessage("Only the host can mute all participants");
            return;
        }

        allMuted = !allMuted;

        if (allMuted) {
            addSystemMessage("You muted all participants");
            if (isWebSocketConnected() && getActiveMeetingId() != null) {
                sendWebSocketMessage("AUDIO_CONTROL", getActiveMeetingId(), "MUTE_ALL");
            }
        } else {
            addSystemMessage("You unmuted all participants");
            if (isWebSocketConnected() && getActiveMeetingId() != null) {
                sendWebSocketMessage("AUDIO_CONTROL", getActiveMeetingId(), "UNMUTE_ALL");
            }
        }

        if (audioControlsController != null) {
            Platform.runLater(() -> {
                audioControlsController.syncWithGlobalState();
            });
        }
    }

    public static void toggleDeafen() {
        isDeafened = !isDeafened;

        if (isDeafened) {
            addSystemMessage("You deafened yourself");
            if (isWebSocketConnected() && getActiveMeetingId() != null) {
                sendWebSocketMessage("AUDIO_STATUS", getActiveMeetingId(), "deafened themselves");
            }
        } else {
            addSystemMessage("You undeafened yourself");
            if (isWebSocketConnected() && getActiveMeetingId() != null) {
                sendWebSocketMessage("AUDIO_STATUS", getActiveMeetingId(), "undeafened themselves");
            }
        }

        if (audioControlsController != null) {
            Platform.runLater(() -> {
                audioControlsController.syncWithGlobalState();
            });
        }
    }

    private static void updateAudioButtonStyles() {
        if (audioControlsController != null) {
            Platform.runLater(() -> {
                audioControlsController.syncWithGlobalState();
            });
        }
    }

    public static boolean isAudioMuted() {
        return audioMuted;
    }

    public static boolean isDeafened() {
        return isDeafened;
    }

    public static boolean isAllMuted() {
        return allMuted;
    }

    public static void muteAudio() {
        if (!audioMuted) {
            toggleAudio();
        }
    }

    public static void unmuteAudio() {
        if (audioMuted) {
            toggleAudio();
        }
    }

    public static void setAudioControlsController(AudioControlsController controller) {
        audioControlsController = controller;
        System.out.println("🔊 Audio controls controller registered");
    }

    public static AudioControlsController getAudioControlsController() {
        return audioControlsController;
    }

    // ==================== VIDEO CONTROLS MANAGEMENT ====================
    public static void toggleVideo() {
        videoOn = !videoOn;

        if (videoOn) {
            addSystemMessage("You started your video");
            if (isWebSocketConnected() && getActiveMeetingId() != null) {
                sendWebSocketMessage("VIDEO_STATUS", getActiveMeetingId(), "started video");
            }
        } else {
            addSystemMessage("You stopped your video");
            if (isWebSocketConnected() && getActiveMeetingId() != null) {
                sendWebSocketMessage("VIDEO_STATUS", getActiveMeetingId(), "stopped video");
            }
        }

        if (videoControlsController != null) {
            Platform.runLater(() -> {
                videoControlsController.syncWithGlobalState();
            });
        }
    }

    public static void toggleRecording() {
        isRecording = !isRecording;

        if (isRecording) {
            addSystemMessage("You started recording");
            if (isWebSocketConnected() && getActiveMeetingId() != null) {
                sendWebSocketMessage("VIDEO_STATUS", getActiveMeetingId(), "started recording");
            }
        } else {
            addSystemMessage("You stopped recording");
            if (isWebSocketConnected() && getActiveMeetingId() != null) {
                sendWebSocketMessage("VIDEO_STATUS", getActiveMeetingId(), "stopped recording");
            }
        }

        if (videoControlsController != null) {
            Platform.runLater(() -> {
                videoControlsController.syncWithGlobalState();
            });
        }
    }

    public static boolean isVideoOn() {
        return videoOn;
    }

    public static boolean isRecording() {
        return isRecording;
    }

    public static boolean isVirtualBackgroundEnabled() {
        return virtualBackgroundEnabled;
    }

    public static void setVirtualBackgroundEnabled(boolean enabled) {
        virtualBackgroundEnabled = enabled;

        if (enabled) {
            addSystemMessage("Virtual background enabled");
        } else {
            addSystemMessage("Virtual background disabled");
        }
    }

    public static void startVideo() {
        if (!videoOn) {
            toggleVideo();
        }
    }

    public static void stopVideo() {
        if (videoOn) {
            toggleVideo();
        }
    }

    public static void startRecording() {
        if (!isRecording) {
            toggleRecording();
        }
    }

    public static void stopRecording() {
        if (isRecording) {
            toggleRecording();
        }
    }

    public static void setVideoControlsController(VideoControlsController controller) {
        videoControlsController = controller;
        System.out.println("🎥 Video controls controller registered");
    }

    public static VideoControlsController getVideoControlsController() {
        return videoControlsController;
    }

    /**
     * Add system message to chat/log
     */
    public static void addSystemMessage(String message) {
        System.out.println("🔊 System: " + message);
    }

    @Override
    public void stop() throws Exception {
        // Leave current meeting if any
        leaveCurrentMeeting();

        // Cleanup when application closes
        if (webSocketClient != null) {
            webSocketClient.disconnect();
        }

        // Stop video and recording if active
        if (videoOn) {
            stopVideo();
        }
        if (isRecording) {
            stopRecording();
        }

        // Save current server config if user is logged in
        if (loggedInUser != null) {
            Database.saveServerConfig(loggedInUser, serverIp, serverPort);
        }

        connectionInitialized = false;

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