package org.example.zoom;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.zoom.webrtc.WebRTCManager;
import org.example.zoom.websocket.SimpleWebSocketClient;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.TextInputDialog;
import javafx.scene.control.ButtonBar;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Collections;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import javafx.scene.image.Image;

public class HelloApplication extends Application {

    private static Stage primaryStage;
    private static String loggedInUser;
    private static String activeMeetingId;
    private static final List<String> activeParticipants = new ArrayList<>();
    private static SimpleWebSocketClient webSocketClient;

    // NEW: WebRTC Manager for real-time communication
    private static WebRTCManager webRTCManager;
    private static boolean webRTCEnabled = false;

    // New fields for server configuration with persistence
    private static String serverIp = "localhost";
    private static String serverPort = "8887";

    // Connection status tracking
    private static boolean connectionInitialized = false;
    private static ConnectionStatusListener connectionStatusListener;

    // Meeting management - ENHANCED with proper meeting storage
    private static final Map<String, MeetingInfo> activeMeetings = new HashMap<>();
    private static boolean isMeetingHost = false;

    // Audio controls management
    private static boolean audioMuted = false;
    private static boolean isDeafened = false;
    private static boolean allMuted = false;
    private static AudioControlsController audioControlsController;

    // Video controls management - FIXED: Initialize properly
    private static boolean videoOn = false;
    private static boolean isRecording = false;
    private static boolean virtualBackgroundEnabled = false;
    private static VideoControlsController videoControlsController;

    // Connection monitoring control
    private static volatile boolean stopMonitoring = false;
    private static Thread monitorThread;

    // Stage management
    private static volatile boolean stageReady = false;

    // WebRTC Configuration
    private static boolean webRTCEnabledField = true;
    private static String stunServer = "stun:stun.l.google.com:19302";
    private static String turnServer = ""; // Leave empty if not using TURN

    // Interface for connection status updates
    public interface ConnectionStatusListener {
        void onConnectionStatusChanged(boolean connected, String status);
    }

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        stageReady = true;

        // Initialize database tables
        Database.initializeDatabase();

        // Initialize WebRTC Manager (simplified, no JSON)
        webRTCManager = WebRTCManager.getInstance();

        setRoot("login-view.fxml");
        stage.setTitle("Zoom Project with WebRTC");
        stage.show();

        System.out.println("✅ Primary stage initialized and ready");
    }

// ==================== WEBRTC INITIALIZATION ====================

    private void initializeWebRTC() {
        try {
            // Get WebRTC configuration from server or use defaults
            String signalingServer = getCurrentServerUrl().replace("ws://", "http://");

            // Get the singleton instance - DO NOT try to create a new instance
            webRTCManager = WebRTCManager.getInstance();

            System.out.println("✅ WebRTC manager initialized with signaling server: " + signalingServer);

            // Set up WebRTC callbacks
            webRTCManager.setStatusConsumer(message -> {
                System.out.println("📡 WebRTC Status: " + message);

                // Forward WebRTC signaling messages via WebSocket
                if (webSocketClient != null && webSocketClient.isConnected()) {
                    webSocketClient.sendMessage("WEBRTC_SIGNAL",
                            activeMeetingId != null ? activeMeetingId : "global",
                            loggedInUser,
                            message);
                }
            });

            webRTCManager.setVideoFrameConsumer(videoFrame -> {
                // Display received video frames
                Platform.runLater(() -> {
                    if (videoControlsController != null) {
                        videoControlsController.displayVideoFrame(videoFrame);
                    }
                });
            });

            System.out.println("✅ WebRTC manager initialized");
        } catch (Exception e) {
            System.err.println("❌ Failed to initialize WebRTC: " + e.getMessage());
            webRTCEnabled = false;
        }
    }

    public static WebRTCManager getWebRTCManager() {
        return webRTCManager;
    }

    public static boolean isWebRTCEnabled() {
        return webRTCEnabled && webRTCManager != null;
    }

    // WebRTC management methods
    public static void enableWebRTC() {
        if (webRTCManager != null) {
            webRTCManager.enableWebRTC();
            webRTCEnabled = true;
            System.out.println("✅ WebRTC enabled");
        }
    }

    public static void disableWebRTC() {
        if (webRTCManager != null) {
            webRTCManager.disableWebRTC();
            webRTCEnabled = false;
            System.out.println("🛑 WebRTC disabled");
        }
    }

    public static void startWebRTCSession() {
        if (activeMeetingId != null && webRTCManager != null && webRTCEnabled) {
            webRTCManager.startWebRTCSession(activeMeetingId, loggedInUser);
            System.out.println("🚀 WebRTC session started for meeting: " + activeMeetingId);
        } else {
            System.out.println("Cannot start WebRTC session. Check if meeting is active and WebRTC is enabled.");
        }
    }

    public static void stopWebRTCSession() {
        if (webRTCManager != null) {
            webRTCManager.stop();
            System.out.println("🛑 WebRTC session stopped");
        }
    }

    // Add this method in the VIDEO CONTROLS MANAGEMENT section (around line 600-650)
    public static void stopRecording() {
        if (isRecording) {
            toggleRecording(); // This will stop the recording
        }
    }

    // Also update the stop() method to use stopRecording() instead of toggleRecording():
    @Override
    public void stop() throws Exception {
        // Stop WebRTC session first
        stopWebRTCSession();
        // Stop video streaming
        SimpleVideoStreamer.stopStreaming();

        // Stop connection monitoring
        stopConnectionAttempts();

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
            stopRecording(); // Changed from toggleRecording() to stopRecording()
        }

        // Save current server config if user is logged in
        if (loggedInUser != null) {
            Database.saveServerConfig(loggedInUser, serverIp, serverPort);
        }

        connectionInitialized = false;
        stageReady = false;

        super.stop();
    }

    // Also update the endMeeting() method:
    /**
     * End a meeting (host only)
     */
    public static void endMeeting(String meetingId) {
        if (activeMeetings.containsKey(meetingId)) {
            // Stop WebRTC session first
            stopWebRTCSession();

            // Notify all participants
            if (isWebSocketConnected()) {
                sendWebSocketMessage("MEETING_ENDED", meetingId, "Meeting ended by host");
            }

            // Clear participants and remove meeting
            activeMeetings.remove(meetingId);
            activeParticipants.clear();

            // Remove from database
            Database.removeMeeting(meetingId);

            System.out.println("🔚 Meeting ended: " + meetingId);

            // Stop audio and video for all participants
            if (audioMuted) {
                toggleAudio(); // Unmute when meeting ends
            }
            if (videoOn) {
                toggleVideo(); // Stop video when meeting ends
            }
            if (isRecording) {
                stopRecording(); // Changed from toggleRecording() to stopRecording()
            }

            // Notify controllers about meeting state change
            notifyControllersMeetingStateChanged(false);
        }
    }

    // Also update the leaveCurrentMeeting() method:
    /**
     * Leave current meeting
     */
    public static void leaveCurrentMeeting() {
        if (activeMeetingId != null && loggedInUser != null) {
            // Stop WebRTC session first
            stopWebRTCSession();

            // Stop streaming first
            SimpleVideoStreamer.stopStreaming();

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
            stopRecording(); // Changed from toggleRecording() to stopRecording()
        }

        // Notify controllers about meeting state change
        notifyControllersMeetingStateChanged(false);
    }

    // ==================== VIDEO CONTROLS MANAGEMENT - FIXED ====================

    /**
     * FIXED: Video toggle method that works for both host and client
     */
    public static void toggleVideo() {
        videoOn = !videoOn;

        System.out.println("\n🎬 ========== VIDEO TOGGLE ==========");
        System.out.println("🎬 New state: " + (videoOn ? "ON" : "OFF"));
        System.out.println("🎬 User: " + loggedInUser);
        System.out.println("🎬 Is Host: " + isMeetingHost);
        System.out.println("🎬 WebSocket: " + isWebSocketConnected());
        System.out.println("🎬 Meeting ID: " + activeMeetingId);
        System.out.println("🎬 ================================\n");

        if (videoOn) {
            addSystemMessage("🎥 You started video streaming");
            System.out.println("✅ Video STARTED - starting streamer...");

            // Start simple streaming
            if (activeMeetingId != null && loggedInUser != null) {
                SimpleVideoStreamer.startStreaming(loggedInUser, activeMeetingId);
                System.out.println("✅ SimpleVideoStreamer started");
            } else {
                System.err.println("❌ Cannot start stream - missing meeting ID or username");
            }
        } else {
            addSystemMessage("🎥 You stopped video streaming");
            System.out.println("🛑 Video STOPPED");

            // Stop streaming
            SimpleVideoStreamer.stopStreaming();
        }

        // Send video status via WebSocket
        if (isWebSocketConnected() && activeMeetingId != null && loggedInUser != null) {
            String status = videoOn ? "VIDEO_STARTED" : "VIDEO_STOPPED";

            System.out.println("📤 Sending VIDEO_STATUS: " + status);
            System.out.println("📤 To meeting: " + activeMeetingId);
            System.out.println("📤 From user: " + loggedInUser);

            sendWebSocketMessage("VIDEO_STATUS", activeMeetingId, loggedInUser, status);
        } else {
            System.err.println("❌ Cannot send video status:");
            System.err.println("   WebSocket: " + isWebSocketConnected());
            System.err.println("   Meeting ID: " + activeMeetingId);
            System.err.println("   User: " + loggedInUser);
        }

        // Update video controls controller
        if (videoControlsController != null) {
            Platform.runLater(() -> {
                videoControlsController.syncWithGlobalState();
                System.out.println("✅ Updated video controls controller");
            });
        } else {
            System.err.println("❌ Video controls controller is null!");
        }

        // Also update MeetingController if available
        Platform.runLater(() -> {
            MeetingController meetingController = MeetingController.getInstance();
            if (meetingController != null) {
                meetingController.updateVideoState(videoOn);
                System.out.println("✅ Updated MeetingController video state");
            }
        });
    }

    /**
     * FIXED: Handle incoming video status messages
     */
    public static void handleVideoStatus(String username, String status) {
        System.out.println("🎥 Received video status: " + status + " from user: " + username);

        // Don't update our own status from received messages
        if (username.equals(loggedInUser)) {
            return;
        }

        Platform.runLater(() -> {
            if (videoControlsController != null) {
                videoControlsController.updateFromServer(username, status);
            }

            // Add system message about other users' video status
            if ("VIDEO_STARTED".equals(status)) {
                addSystemMessage(username + " started their video");
            } else if ("VIDEO_STOPPED".equals(status)) {
                addSystemMessage(username + " stopped their video");
            }
        });
    }

    public static boolean isVideoOn() {
        return videoOn;
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

    // ==================== STAGE MANAGEMENT METHODS ====================

    /**
     * Set primary stage explicitly (for Device classes)
     */
    public static void setPrimaryStage(Stage stage) {
        primaryStage = stage;
        stageReady = true;
        System.out.println("✅ Primary stage set in HelloApplication: " + (stage != null ? "VALID" : "NULL"));
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static boolean isStageReady() {
        return stageReady && primaryStage != null;
    }

    // NEW: Wait for stage to be ready
    public static void waitForStageReady(Runnable callback) {
        new Thread(() -> {
            int attempts = 0;
            while (!stageReady && attempts < 50) { // Wait up to 5 seconds
                try {
                    Thread.sleep(100);
                    attempts++;
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }

            if (stageReady) {
                Platform.runLater(callback);
            } else {
                System.err.println("❌ Stage never became ready after 5 seconds");
            }
        }).start();
    }

    // ==================== NAVIGATION METHODS ====================

    public static void setRoot(String fxml) throws Exception {
        if (primaryStage == null) {
            System.err.println("❌ Cannot set root: primaryStage is null");
            throw new IllegalStateException("Primary stage is not initialized");
        }

        FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource(fxml));
        Scene scene = new Scene(loader.load());

        Platform.runLater(() -> {
            try {
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
                    System.out.println("✅ Video controls controller registered");
                } else if (controller instanceof DashboardController) {
                    // DashboardController now implements ConnectionStatusListener
                    DashboardController dashboardController = (DashboardController) controller;
                    // Register as connection status listener
                    setConnectionStatusListener(dashboardController);
                    System.out.println("✅ Dashboard controller loaded and registered for connection updates");
                }

                System.out.println("✅ Root set to: " + fxml);
            } catch (Exception e) {
                System.err.println("❌ Error setting root: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    // NEW: Safe method to set root with stage validation
    public static void setRootSafe(String fxml) {
        if (!stageReady || primaryStage == null) {
            System.err.println("❌ Stage not ready, cannot set root to: " + fxml);
            return;
        }

        Platform.runLater(() -> {
            try {
                setRoot(fxml);
            } catch (Exception e) {
                System.err.println("❌ Failed to set root: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Navigate to dashboard with proper stage readiness checks
     */
    public static void navigateToDashboard() {
        if (!stageReady || primaryStage == null) {
            System.err.println("❌ Stage not ready for navigation, waiting...");
            waitForStageReady(() -> {
                try {
                    setRoot("dashboard-view.fxml");
                    System.out.println("✅ Successfully navigated to dashboard after wait");
                } catch (Exception e) {
                    System.err.println("❌ Failed to navigate to dashboard: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        } else {
            try {
                setRoot("dashboard-view.fxml");
                System.out.println("✅ Successfully navigated to dashboard");
            } catch (Exception e) {
                System.err.println("❌ Failed to navigate to dashboard: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    /**
     * Navigate with fallback mechanism
     */
    public static void navigateWithFallback(String fxml, Runnable fallback) {
        if (isStageReady()) {
            try {
                setRoot(fxml);
            } catch (Exception e) {
                System.err.println("❌ Navigation failed: " + e.getMessage());
                if (fallback != null) {
                    fallback.run();
                }
            }
        } else {
            System.out.println("⏳ Stage not ready, waiting...");
            waitForStageReady(() -> {
                try {
                    setRoot(fxml);
                } catch (Exception e) {
                    System.err.println("❌ Navigation failed after wait: " + e.getMessage());
                    if (fallback != null) {
                        fallback.run();
                    }
                }
            });
        }
    }

    // ==================== USER MANAGEMENT ====================

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
        // Stop WebRTC session
        stopWebRTCSession();

        // Stop connection monitoring
        stopConnectionAttempts();

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

        // Use safe method to set root
        setRootSafe("login-view.fxml");
    }

    // ==================== CONNECTION MANAGEMENT ====================

    public static void initializeWebSocket(String username) {
        String savedUrl = getCurrentServerUrl();
        System.out.println("🎯 Attempting connection to saved server: " + savedUrl);

        // Test the saved connection first
        if (!testConnection(savedUrl)) {
            System.out.println("❌ Saved server connection failed");

            // Special handling for localhost failures
            if (savedUrl.contains("localhost")) {
                System.out.println("🔄 Localhost failed - suggesting network connection");
                Platform.runLater(() -> {
                    MeetingInfo.handleLocalhostFailure();
                });
            } else {
                Platform.runLater(() -> {
                    showServerConnectionDialog();
                });
            }
        } else {
            initializeWebSocketWithUrl(savedUrl);
        }
    }

    // Show connection dialog to user
    public static void showServerConnectionDialog() {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Server Connection Required");
            alert.setHeaderText("Cannot Connect to Server");
            alert.setContentText("Unable to connect to the saved server.\n\n" +
                    "Please choose an option:");

            ButtonType discoverButton = new ButtonType("Discover Servers");
            ButtonType manualButton = new ButtonType("Enter Server IP");
            ButtonType localhostButton = new ButtonType("Try Localhost");
            ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

            alert.getButtonTypes().setAll(discoverButton, manualButton, localhostButton, cancelButton);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent()) {
                if (result.get() == discoverButton) {
                    discoverAndConnectToServer();
                } else if (result.get() == manualButton) {
                    showManualConnectionDialog();
                } else if (result.get() == localhostButton) {
                    // Try localhost
                    resetConnectionAndRetry("ws://localhost:8887");
                }
            }
        });
    }

    // Enhanced network discovery that shows results
    public static void discoverAndConnectToServer() {
        Platform.runLater(() -> {
            Alert searchingAlert = new Alert(Alert.AlertType.INFORMATION);
            searchingAlert.setTitle("Network Discovery");
            searchingAlert.setHeaderText("Searching for available servers...");
            searchingAlert.setContentText("Scanning your network for Zoom servers.\n\nNote: This will NOT find servers on different networks.");
            searchingAlert.show();

            new Thread(() -> {
                List<String> availableServers = discoverAvailableServers();
                List<String> localIPs = getLocalIPAddresses();

                Platform.runLater(() -> {
                    searchingAlert.close();

                    if (availableServers.isEmpty()) {
                        showNoServersFoundDialog(localIPs);
                    } else if (availableServers.size() == 1) {
                        // Auto-connect if only one server found
                        String server = availableServers.get(0);
                        String[] parts = server.split(":");
                        if (parts.length >= 2) {
                            String serverUrl = "ws://" + server;
                            resetConnectionAndRetry(serverUrl);
                            showAlert(Alert.AlertType.INFORMATION, "Connected",
                                    "Connected to: " + server);
                        }
                    } else {
                        // Let user choose from multiple servers
                        showServerSelectionDialog(availableServers);
                    }
                });
            }).start();
        });
    }

    // ENHANCED: No servers found dialog with better guidance
    private static void showNoServersFoundDialog(List<String> localIPs) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("No Servers Found");
        alert.setHeaderText("No Zoom servers found on your local network");

        StringBuilder content = new StringBuilder();
        content.append("Please make sure:\n\n");
        content.append("• The Zoom server is running on another device\n");
        content.append("• Both devices are on the same WiFi network\n");
        content.append("• Firewall allows port 8887\n\n");

        if (!localIPs.isEmpty()) {
            content.append("Your network IP addresses:\n");
            for (String ip : localIPs) {
                content.append("📍 ").append(ip).append("\n");
            }
            content.append("\n");
        }

        content.append("Would you like to:");

        alert.setContentText(content.toString());

        ButtonType manualButton = new ButtonType("Enter Server IP Manually");
        ButtonType guideButton = new ButtonType("View Connection Guide");
        ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

        alert.getButtonTypes().setAll(manualButton, guideButton, cancelButton);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == manualButton) {
                showManualConnectionDialog();
            } else if (result.get() == guideButton) {
                showConnectionGuide();
            }
        }
    }

    // NEW: Quick server status check
    public static void checkServerStatus() {
        String currentUrl = getCurrentServerUrl();
        System.out.println("🔍 Checking server status: " + currentUrl);

        if (testConnection(currentUrl)) {
            showAlert(Alert.AlertType.INFORMATION, "Server Status",
                    "✅ Server is running and accessible at:\n" + currentUrl);
        } else {
            if (currentUrl.contains("localhost")) {
                showAlert(Alert.AlertType.ERROR, "Server Status",
                        "❌ Local server is not running\n\n" +
                                "The WebSocket server needs to be started on this computer " +
                                "or connect to another computer running the server.");
            } else {
                showAlert(Alert.AlertType.ERROR, "Server Status",
                        "❌ Cannot connect to server at:\n" + currentUrl +
                                "\n\nPlease check if the server is running and accessible.");
            }
        }
    }

    // Server selection dialog
    private static void showServerSelectionDialog(List<String> servers) {
        ChoiceDialog<String> dialog = new ChoiceDialog<>(servers.get(0), servers);
        dialog.setTitle("Select Server");
        dialog.setHeaderText("Multiple servers found");
        dialog.setContentText("Choose a server to connect:");

        Optional<String> result = dialog.showAndWait();
        result.ifPresent(server -> {
            String[] parts = server.split(":");
            if (parts.length >= 2) {
                String ip = parts[0];
                String port = parts[1];
                String serverUrl = "ws://" + ip + ":" + port;

                // Use reset instead of direct set
                resetConnectionAndRetry(serverUrl);
                showAlert(Alert.AlertType.INFORMATION, "Connecting",
                        "Connecting to: " + server);
            }
        });
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
            stopMonitoring = false;

            // Start connection monitoring
            startConnectionMonitoring();

        } catch (Exception e) {
            System.err.println("❌ Failed to initialize WebSocket: " + e.getMessage());
            handleConnectionFailure(serverUrl);
        }
    }

    // Improved connection monitoring with fallback logic
    private static void startConnectionMonitoring() {
        // Stop any existing monitoring
        stopMonitoring = true;
        if (monitorThread != null && monitorThread.isAlive()) {
            try {
                monitorThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        stopMonitoring = false;
        monitorThread = new Thread(() -> {
            boolean previousConnectedState = isWebSocketConnected();
            int consecutiveFailures = 0;
            final int MAX_CONSECUTIVE_FAILURES = 2; // Reduced to 2 failures before fallback

            System.out.println("🔍 Starting connection monitoring...");

            while (!stopMonitoring && connectionInitialized && loggedInUser != null) {
                try {
                    Thread.sleep(5000); // Check every 5 seconds

                    boolean currentConnectedState = isWebSocketConnected();

                    // Only notify if connection state actually changed
                    if (currentConnectedState != previousConnectedState) {
                        String status = currentConnectedState ? "🟢 Connected" : "Disconnected";
                        System.out.println("🔗 Connection state changed: " + status);

                        // Notify listener if connection status changed
                        if (connectionStatusListener != null) {
                            Platform.runLater(() -> {
                                connectionStatusListener.onConnectionStatusChanged(currentConnectedState, status);
                            });
                        }

                        previousConnectedState = currentConnectedState;

                        // Reset failure counter on successful connection
                        if (currentConnectedState) {
                            consecutiveFailures = 0;
                            System.out.println("✅ Connection restored, reset failure counter");
                        }
                    }

                    // Attempt reconnection if disconnected
                    if (!currentConnectedState) {
                        consecutiveFailures++;
                        System.out.println("🔄 Attempting to reconnect... (Failure #" + consecutiveFailures + ")");

                        // If too many consecutive failures, trigger fallback
                        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                            System.out.println("❌ Too many connection failures (" + consecutiveFailures + "), triggering fallback...");

                            Platform.runLater(() -> {
                                showConnectionFallbackDialog();
                            });

                            // Reset counter and wait longer before next attempt
                            consecutiveFailures = 0;
                            Thread.sleep(10000); // Wait 10 seconds before next attempt
                            continue;
                        }

                        try {
                            // Create new connection
                            initializeWebSocketWithUrl(getCurrentServerUrl());
                        } catch (Exception e) {
                            System.err.println("❌ Reconnection failed: " + e.getMessage());
                        }
                    } else if (currentConnectedState) {
                        // Reset counter if connected
                        consecutiveFailures = 0;
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("❌ Error in connection monitoring: " + e.getMessage());
                }
            }
            System.out.println("🛑 Connection monitoring stopped");
        });
        monitorThread.setDaemon(true);
        monitorThread.setName("WebSocket-Monitor");
        monitorThread.start();
    }

    // NEW: Show fallback dialog when connection repeatedly fails
    private static void showConnectionFallbackDialog() {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Connection Issues");
            alert.setHeaderText("Cannot connect to server: " + getCurrentServerUrl());
            alert.setContentText("Multiple connection attempts failed.\n\n" +
                    "Please choose an option:");

            ButtonType discoverButton = new ButtonType("Discover Servers");
            ButtonType manualButton = new ButtonType("Enter Server IP");
            ButtonType localhostButton = new ButtonType("Try Localhost");
            ButtonType cancelButton = new ButtonType("Continue Retrying", ButtonBar.ButtonData.CANCEL_CLOSE);

            alert.getButtonTypes().setAll(discoverButton, manualButton, localhostButton, cancelButton);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent()) {
                if (result.get() == discoverButton) {
                    discoverAndConnectToServer();
                } else if (result.get() == manualButton) {
                    showManualConnectionDialog();
                } else if (result.get() == localhostButton) {
                    // Try localhost instead
                    resetConnectionAndRetry("ws://localhost:8887");
                }
                // If user chooses "Continue Retrying", just continue with normal retry logic
            }
        });
    }

    // Set connection status listener
    public static void setConnectionStatusListener(ConnectionStatusListener listener) {
        connectionStatusListener = listener;

        // Immediately notify of current status
        if (listener != null) {
            boolean connected = isWebSocketConnected();
            String status = connected ? "🟢 Connected" : "Disconnected";
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
                connectionStatusListener.onConnectionStatusChanged(false, "Connection failed");
            }

            // Auto-trigger network discovery on connection failure
            if (loggedInUser != null && primaryStage != null) {
                showConnectionErrorAndDiscover(attemptedUrl);
            }
        });
    }

    // Show connection error and trigger network discovery
    private static void showConnectionErrorAndDiscover(String attemptedUrl) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Connection Failed");
        alert.setHeaderText("Cannot connect to server");
        alert.setContentText("Failed to connect to: " + attemptedUrl +
                "\n\nWould you like to search for available servers on your network?");

        ButtonType discoverButton = new ButtonType("Discover Servers");
        ButtonType manualButton = new ButtonType("Manual Configuration");
        ButtonType cancelButton = new ButtonType("Cancel");

        alert.getButtonTypes().setAll(discoverButton, manualButton, cancelButton);

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent()) {
            if (result.get() == discoverButton) {
                discoverAndConnectToServer();
            } else if (result.get() == manualButton) {
                showManualConnectionDialog();
            }
        }
    }

    // Manual connection dialog
    public static void showManualConnectionDialog() {
        TextInputDialog ipDialog = new TextInputDialog("192.168.1.");
        ipDialog.setTitle("Manual Server Connection");
        ipDialog.setHeaderText("Connect to Server on Different Device");
        ipDialog.setContentText("Enter server IP address:");

        Optional<String> ipResult = ipDialog.showAndWait();
        if (ipResult.isPresent() && !ipResult.get().trim().isEmpty()) {
            String ip = ipResult.get().trim();

            TextInputDialog portDialog = new TextInputDialog("8887");
            portDialog.setTitle("Server Port");
            portDialog.setHeaderText("Server Port Configuration");
            portDialog.setContentText("Enter server port:");

            Optional<String> portResult = portDialog.showAndWait();
            String port = portResult.isPresent() ? portResult.get().trim() : "8887";

            if (port.isEmpty()) {
                port = "8887";
            }

            // Validate and connect
            connectToServerManual(ip, port);
        }
    }

    // Manual connection method - UPDATED
    public static void connectToServerManual(String ip, String port) {
        if (ip == null || ip.trim().isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Invalid IP", "Please enter a valid IP address");
            return;
        }

        if (port == null || port.trim().isEmpty()) {
            port = "8887";
        }

        // Validate IP format
        if (!isValidIPAddress(ip) && !ip.equals("localhost")) {
            showAlert(Alert.AlertType.ERROR, "Invalid IP", "Please enter a valid IP address (e.g., 192.168.1.100)");
            return;
        }

        String serverUrl = "ws://" + ip + ":" + port;
        System.out.println("🔗 Attempting manual connection to: " + serverUrl);

        // Stop current attempts and start fresh
        resetConnectionAndRetry(serverUrl);
    }

    // NEW: Stop all connection attempts and monitoring
    public static void stopConnectionAttempts() {
        System.out.println("🛑 Stopping all connection attempts...");
        stopMonitoring = true;
        connectionInitialized = false;

        if (webSocketClient != null) {
            webSocketClient.disconnect();
            webSocketClient = null;
        }

        if (monitorThread != null && monitorThread.isAlive()) {
            try {
                monitorThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        System.out.println("🛑 All connection attempts stopped");
    }

    // NEW: Call this when switching to manual connection or discovery
    public static void resetConnectionAndRetry(String newUrl) {
        System.out.println("🔄 Resetting connection and retrying with: " + newUrl);
        stopConnectionAttempts();

        // Extract IP and port from URL
        String urlWithoutProtocol = newUrl.replace("ws://", "");
        String[] parts = urlWithoutProtocol.split(":");
        if (parts.length >= 2) {
            serverIp = parts[0];
            serverPort = parts[1];

            // Save to database if user is logged in
            if (loggedInUser != null) {
                Database.saveServerConfig(loggedInUser, serverIp, serverPort);
            }
        }

        // Small delay before new connection attempt
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                Platform.runLater(() -> {
                    initializeWebSocketWithUrl(newUrl);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    // IP validation method
    public static boolean isValidIPAddress(String ip) {
        String ipPattern = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
        return ip.matches(ipPattern);
    }

    // Alert helper method
    public static void showAlert(Alert.AlertType type, String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
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
            resetConnectionAndRetry(getCurrentServerUrl());
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
        resetConnectionAndRetry(newUrl);
    }

    // Get all local IP addresses for network discovery
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

    // Enhanced network discovery method
    public static List<String> discoverAvailableServers() {
        List<String> availableServers = new ArrayList<>();
        List<String> localIPs = getLocalIPAddresses();

        System.out.println("🔍 Starting enhanced network discovery...");
        System.out.println("🌐 Your local IP addresses: " + localIPs);

        // Always check localhost first
        if (testConnection("ws://localhost:8887")) {
            availableServers.add("localhost:8887");
        }

        // Check all local IPs and common network ranges
        List<String> ipsToTest = new ArrayList<>();

        for (String localIp : localIPs) {
            // Add the local IP itself
            if (!ipsToTest.contains(localIp)) {
                ipsToTest.add(localIp);
            }

            // Add common IP ranges in the same subnet
            if (localIp.contains(".")) {
                String baseIp = localIp.substring(0, localIp.lastIndexOf('.') + 1);
                for (int i = 1; i <= 20; i++) { // Reduced to 20 IPs for faster discovery
                    String testIp = baseIp + i;
                    if (!testIp.equals(localIp) && !ipsToTest.contains(testIp)) {
                        ipsToTest.add(testIp);
                    }
                }
            }
        }

        System.out.println("🔍 Testing " + ipsToTest.size() + " IP addresses...");

        // Test all IPs in parallel for faster discovery
        List<Thread> threads = new ArrayList<>();
        List<String> discoveredServers = Collections.synchronizedList(new ArrayList<>());

        for (String ip : ipsToTest) {
            Thread thread = new Thread(() -> {
                String testUrl = "ws://" + ip + ":8887";
                if (testConnection(testUrl)) {
                    discoveredServers.add(ip + ":8887");
                    System.out.println("✅ Found server: " + testUrl);
                }
            });
            thread.start();
            threads.add(thread);
        }

        // Wait for all threads to complete
        for (Thread thread : threads) {
            try {
                thread.join(1000); // Reduced to 1 second per thread
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        availableServers.addAll(discoveredServers);
        System.out.println("🎯 Discovery complete. Found " + availableServers.size() + " servers: " + availableServers);

        return availableServers;
    }

    // FIXED: Test connection to a specific server with proper message filtering
    public static boolean testConnection(String serverUrl) {
        final AtomicBoolean connectionSuccess = new AtomicBoolean(false);
        final AtomicBoolean receivedDisconnect = new AtomicBoolean(false);
        final Object lock = new Object();
        SimpleWebSocketClient testClient = null;

        try {
            System.out.println("🔍 Testing connection to: " + serverUrl);

            testClient = new SimpleWebSocketClient(serverUrl, message -> {
                System.out.println("🔗 Test connection received: " + message);

                // Check for successful connection messages
                if (message.contains("Connected") || message.contains("Welcome") ||
                        message.contains("WELCOME") || message.contains("connected")) {
                    synchronized (lock) {
                        connectionSuccess.set(true);
                        receivedDisconnect.set(false);
                        lock.notifyAll();
                    }
                }
                // Check for disconnect or error messages
                else if (message.contains("DISCONNECTED") || message.contains("ERROR") ||
                        message.contains("Failed") || message.contains("disconnected")) {
                    synchronized (lock) {
                        receivedDisconnect.set(true);
                        lock.notifyAll();
                    }
                }
            });

            // Wait for connection result with timeout
            synchronized (lock) {
                try {
                    lock.wait(2000); // Wait up to 2 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // Clean up test client
            if (testClient != null) {
                testClient.disconnect();
            }

            boolean success = connectionSuccess.get() && !receivedDisconnect.get();

            // Special handling for localhost failure
            if (!success && serverUrl.contains("localhost")) {
                System.out.println("❌ Localhost connection failed - server likely not running on this device");
            }

            System.out.println("🔍 Connection test result for " + serverUrl + ": " +
                    (success ? "SUCCESS" : "FAILED") +
                    " [Connected: " + connectionSuccess.get() +
                    ", Disconnected: " + receivedDisconnect.get() + "]");

            return success;

        } catch (Exception e) {
            System.err.println("❌ Connection test failed for " + serverUrl + ": " + e.getMessage());
            if (testClient != null) {
                testClient.disconnect();
            }

            // Special message for localhost connection refused
            if (e.getMessage().contains("Connection refused") && serverUrl.contains("localhost")) {
                System.out.println("💡 Hint: WebSocket server is not running on this device. Connect to another computer's IP.");
            }
            return false;
        }
    }

    // Show connection guide
    public static void showConnectionGuide() {
        List<String> localIPs = getLocalIPAddresses();

        StringBuilder guide = new StringBuilder();
        guide.append("🌐 MULTI-DEVICE CONNECTION GUIDE\n");
        guide.append("================================\n\n");

        if (localIPs.isEmpty()) {
            guide.append("❌ No network interfaces found!\n");
            guide.append("   Make sure you're connected to WiFi/Ethernet\n\n");
        } else {
            guide.append("✅ Your Server IP Addresses:\n");
            for (String ip : localIPs) {
                guide.append("   📍 ").append(ip).append(":8887\n");
            }
            guide.append("\n");
        }

        guide.append("📋 CONNECTION STEPS:\n");
        guide.append("1. Run the application on Server device\n");
        guide.append("2. Note the Server IP address from above\n");
        guide.append("3. On Client device, use 'Manual Connect'\n");
        guide.append("4. Enter Server IP address\n");
        guide.append("5. Click Connect\n\n");

        guide.append("🔧 TROUBLESHOOTING:\n");
        guide.append("• Ensure both devices on same WiFi\n");
        guide.append("• Disable VPN temporarily\n");
        guide.append("• Check firewall settings on Server\n");
        guide.append("• Verify Server application is running\n");

        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Multi-Device Connection Guide");
            alert.setHeaderText("How to Connect Different Devices");
            alert.setContentText(guide.toString());
            alert.setWidth(500);
            alert.setHeight(600);
            alert.showAndWait();
        });
    }

    // FIXED: sendVideoFrame method - changed isOpen() to isConnected()
    public static void sendVideoFrame(String meetingId, String username, String base64Image) {
        if (webSocketClient != null && webSocketClient.isConnected() && isWebSocketConnected()) {
            try {
                // Create video frame message
                String message = "VIDEO_FRAME|" + meetingId + "|" + username + "|" + base64Image;
                webSocketClient.send(message);
                System.out.println("📤 Sent video frame from: " + username + " (" + base64Image.length() + " chars)");
            } catch (Exception e) {
                System.err.println("❌ Failed to send video frame: " + e.getMessage());
            }
        } else {
            System.err.println("❌ WebSocket not connected for video streaming");
        }
    }

    /**
     * Send video frame to all participants
     */
    public static void sendVideoFrame(Image image) {
        if (!isWebSocketConnected() || getActiveMeetingId() == null || !isVideoOn()) {
            return;
        }

        try {
            // Convert Image to base64 for WebSocket transmission
            String base64Image = convertImageToBase64(image);
            if (base64Image != null && !base64Image.isEmpty()) {
                sendWebSocketMessage("VIDEO_FRAME", getActiveMeetingId(), base64Image);
            }
        } catch (Exception e) {
            System.err.println("❌ Error sending video frame: " + e.getMessage());
        }
    }

    /**
     * Convert JavaFX Image to Base64 string
     */
    private static String convertImageToBase64(Image image) {
        try {
            // Convert Image to BufferedImage first
            java.awt.image.BufferedImage bufferedImage = convertToBufferedImage(image);
            if (bufferedImage == null) return null;

            // Convert to base64
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(bufferedImage, "jpg", baos);
            byte[] imageBytes = baos.toByteArray();
            return java.util.Base64.getEncoder().encodeToString(imageBytes);

        } catch (Exception e) {
            System.err.println("❌ Error converting image to base64: " + e.getMessage());
            return null;
        }
    }

    /**
     * Convert JavaFX Image to BufferedImage
     */
    private static java.awt.image.BufferedImage convertToBufferedImage(Image image) {
        try {
            int width = (int) image.getWidth();
            int height = (int) image.getHeight();

            java.awt.image.BufferedImage bufferedImage = new java.awt.image.BufferedImage(
                    width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);

            java.awt.Graphics2D g2d = bufferedImage.createGraphics();

            // Convert JavaFX Image to AWT Image
            java.awt.Image awtImage = javafx.embed.swing.SwingFXUtils.fromFXImage(image, null);
            g2d.drawImage(awtImage, 0, 0, null);
            g2d.dispose();

            return bufferedImage;
        } catch (Exception e) {
            System.err.println("❌ Error converting to BufferedImage: " + e.getMessage());
            return null;
        }
    }

    /**
     * Handle incoming video frames from other participants
     */
    private static void handleVideoFrame(String username, String base64Image) {
        try {
            // Convert base64 back to Image
            Image videoFrame = convertBase64ToImage(base64Image);
            if (videoFrame != null) {
                Platform.runLater(() -> {
                    // Update the UI with the received video frame
                    MeetingController meetingController = MeetingController.getInstance();
                    if (meetingController != null) {
                        meetingController.displayVideoFrame(username, videoFrame);
                    }

                    // Also update video controls preview
                    if (videoControlsController != null) {
                        videoControlsController.displayVideoFrame(videoFrame);
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("❌ Error handling video frame: " + e.getMessage());
        }
    }

    /**
     * Convert Base64 string to JavaFX Image
     */
    public static Image convertBase64ToImage(String base64Image) {
        try {
            if (base64Image == null || base64Image.isEmpty()) {
                System.err.println("❌ Empty base64 string");
                return null;
            }

            System.out.println("📸 Converting Base64 to Image: " + base64Image.length() + " chars");

            // Decode base64
            byte[] imageBytes = java.util.Base64.getDecoder().decode(base64Image);
            if (imageBytes == null || imageBytes.length == 0) {
                System.err.println("❌ Decoded bytes are empty");
                return null;
            }

            System.out.println("📸 Decoded bytes: " + imageBytes.length + " bytes");

            // Create Image from bytes
            java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(imageBytes);
            Image image = new Image(bis);

            if (image.isError()) {
                System.err.println("❌ Error creating image from bytes");
                return null;
            }

            System.out.println("✅ Created Image: " + image.getWidth() + "x" + image.getHeight());
            return image;

        } catch (Exception e) {
            System.err.println("❌ Error converting base64 to image: " + e.getMessage());
            e.printStackTrace();
            return null;
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

            // Check if message is for current meeting
            if (!meetingId.equals(getActiveMeetingId()) && !meetingId.equals("global")) {
                System.out.println(" Message not for current meeting. Current: " + getActiveMeetingId() + ", Message: " + meetingId);
                return;
            }

            switch (type) {
                case "VIDEO_STATUS":
                    handleVideoStatus(username, content);
                    break;

                case "VIDEO_FRAME":
                    System.out.println("🎥 Received VIDEO_FRAME from: " + username +
                            " (content length: " + content.length() + ")");

                    // Handle the video frame IMMEDIATELY
                    handleVideoFrameDirectly(username, content);
                    break;

                case "CHAT":
                    Platform.runLater(() -> {
                        MeetingController meetingController = MeetingController.getInstance();
                        if (meetingController != null) {
                            meetingController.handleWebSocketMessage(message);
                        }
                    });
                    break;

                default:
                    // Forward other messages to MeetingController
                    Platform.runLater(() -> {
                        MeetingController meetingController = MeetingController.getInstance();
                        if (meetingController != null) {
                            meetingController.handleWebSocketMessage(message);
                        }
                    });
                    break;
            }
        } else {
            System.err.println("❌ Invalid message format: " + message);
        }
    }

    /**
     * Handle video frames directly without going through MeetingController.getInstance()
     */
    private static void handleVideoFrameDirectly(String username, String base64Image) {
        try {
            System.out.println("🎥 Converting base64 to image...");

            // Convert base64 back to Image
            Image videoFrame = convertBase64ToImage(base64Image);

            if (videoFrame == null) {
                System.err.println("❌ Failed to convert base64 to image");
                return;
            }

            System.out.println("✅ Image created: " + videoFrame.getWidth() + "x" + videoFrame.getHeight());

            Platform.runLater(() -> {
                // Update the UI with the received video frame
                MeetingController meetingController = MeetingController.getInstance();
                if (meetingController != null) {
                    System.out.println("🎥 Found MeetingController, displaying frame");
                    meetingController.displayVideoFrame(username, videoFrame);
                } else {
                    System.err.println("❌ MeetingController is null! Can't display video");
                    // Try to update video controls directly
                    if (videoControlsController != null) {
                        videoControlsController.displayVideoFrame(videoFrame);
                        System.out.println("✅ Updated video controls directly");
                    }
                }
            });

        } catch (Exception e) {
            System.err.println("❌ Error handling video frame: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ==================== MEETING MANAGEMENT - ENHANCED CONNECTION ====================

    /**
     * Set the active meeting ID and store it globally for JoinController access
     */
    public static void setActiveMeetingId(String meetingId) {
        activeMeetingId = meetingId;
        System.out.println("🎯 Active meeting set to: " + meetingId);

        // Start WebRTC session when joining a meeting
        if (meetingId != null && webRTCEnabled && webRTCManager != null) {
            startWebRTCSession();
        }
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

    /**
     * Create a new meeting with proper validation and storage - FIXED CONNECTION
     */
    public static String createNewMeeting() {
        String meetingId = generateMeetingId();
        String hostName = getLoggedInUser();
        if (hostName == null || hostName.isEmpty()) {
            hostName = "Host";
        }

        System.out.println("🎯 CREATING NEW MEETING: " + meetingId + " by " + hostName);

        // Create meeting info
        MeetingInfo meetingInfo = new MeetingInfo(meetingId, hostName);
        activeMeetings.put(meetingId, meetingInfo);

        setActiveMeetingId(meetingId);
        setMeetingHost(true);

        // Store meeting in database with proper meeting ID - THIS IS THE KEY FIX
        Database.saveMeetingWithId(meetingId, hostName, "Meeting " + meetingId, "Auto-generated meeting");

        // Add host as first participant to database
        addParticipantToMeeting(meetingId, hostName);

        // Notify via WebSocket
        if (isWebSocketConnected()) {
            sendWebSocketMessage("MEETING_CREATED", meetingId, "New meeting created by " + hostName);
            System.out.println("🎯 New meeting created via WebSocket: " + meetingId + " by " + hostName);
        } else {
            System.out.println("🎯 New meeting created locally: " + meetingId + " by " + hostName + " (WebSocket offline)");
        }

        // Start WebRTC session for the new meeting
        if (webRTCEnabled && webRTCManager != null) {
            startWebRTCSession();
        }

        // Notify controllers about meeting state change
        notifyControllersMeetingStateChanged(true);

        System.out.println("✅ MEETING CREATION SUCCESS: " + meetingId + " - Ready for participants to join!");
        System.out.println("🔍 Active meetings now: " + activeMeetings.keySet());
        return meetingId;
    }

    /**
     * Join an existing meeting with exact ID validation - FIXED CONNECTION
     */
    public static boolean joinMeeting(String meetingId, String participantName) {
        System.out.println("🔍 Attempting to join meeting: " + meetingId + " as " + participantName);

        // Validate meeting ID format first
        if (!meetingId.matches("\\d{6}")) {
            System.err.println("❌ Invalid meeting ID format. Must be 6 digits: " + meetingId);
            return false;
        }

        // Check if meeting exists in active meetings OR database
        boolean meetingExists = false;

        // Check active meetings first (in-memory)
        if (activeMeetings.containsKey(meetingId)) {
            System.out.println("✅ Meeting found in active meetings: " + meetingId);
            meetingExists = true;
        }
        // Check database if not found in active meetings
        else if (Database.meetingExists(meetingId)) {
            System.out.println("✅ Meeting found in database: " + meetingId);

            // Create meeting info if it exists in database but not in active meetings
            String host = Database.getMeetingHost(meetingId);
            if (host != null) {
                MeetingInfo meetingInfo = new MeetingInfo(meetingId, host);
                activeMeetings.put(meetingId, meetingInfo);
                meetingExists = true;
                System.out.println("✅ Recreated meeting from database: " + meetingId);
            }
        }

        if (!meetingExists) {
            System.err.println("❌ Meeting not found: " + meetingId);
            System.err.println("🔍 Available meetings: " + activeMeetings.keySet());
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

        // Start WebRTC session when joining
        if (webRTCEnabled && webRTCManager != null) {
            startWebRTCSession();
        }

        // Notify controllers about meeting state change
        notifyControllersMeetingStateChanged(true);

        System.out.println("✅ JOIN SUCCESS: " + participantName + " joined meeting " + meetingId);
        return true;
    }

    /**
     * Validate meeting existence - ENHANCED with multiple checks
     */
    public static boolean isValidMeeting(String meetingId) {
        System.out.println("🔍 Validating meeting: " + meetingId);

        // Check format
        if (!meetingId.matches("\\d{6}")) {
            System.err.println("❌ Invalid meeting ID format: " + meetingId);
            return false;
        }

        // First check if meeting exists in our active meetings
        if (activeMeetings.containsKey(meetingId)) {
            System.out.println("✅ Meeting found in active meetings: " + meetingId);
            return true;
        }

        // Check if meeting exists in database
        if (Database.meetingExists(meetingId)) {
            System.out.println("✅ Meeting found in database: " + meetingId);

            // Create meeting info if it exists in database but not in active meetings
            String host = Database.getMeetingHost(meetingId);
            if (host != null) {
                MeetingInfo meetingInfo = new MeetingInfo(meetingId, host);
                activeMeetings.put(meetingId, meetingInfo);
                return true;
            }
        }

        System.err.println("❌ Meeting not found: " + meetingId);
        System.err.println("🔍 Currently active meetings: " + activeMeetings.keySet());
        return false;
    }

    /**
     * Create a test meeting for quick join functionality
     */
    public static void createMeetingForTesting(String meetingId) {
        String hostName = getLoggedInUser();
        if (hostName == null || hostName.isEmpty()) {
            hostName = "TestHost";
        }

        MeetingInfo meetingInfo = new MeetingInfo(meetingId, hostName);
        activeMeetings.put(meetingId, meetingInfo);

        // Also store in database
        Database.saveMeetingWithId(meetingId, hostName, "Test Meeting " + meetingId, "Quick join test meeting");

        System.out.println("🎯 Test meeting created: " + meetingId + " by " + hostName);
    }

    /**
     * Get meeting information by ID
     */
    public static MeetingInfo getMeetingInfo(String meetingId) {
        return activeMeetings.get(meetingId);
    }

    /**
     * Get all active meetings
     */
    public static List<String> getActiveMeetingIds() {
        return new ArrayList<>(activeMeetings.keySet());
    }

    /**
     * Check if user is in any meeting
     */
    public static boolean isInMeeting() {
        return activeMeetingId != null;
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
            return "Connected to " + getCurrentServerUrl().replace("ws://", "");
        } else {
            return "Disconnected from " + getCurrentServerUrl().replace("ws://", "");
        }
    }

    public static String getConnectionStatusShort() {
        if (isWebSocketConnected()) {
            return "Connected";
        } else {
            return "Disconnected";
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

    // ==================== VIDEO CONTROLS MANAGEMENT - FIXED ====================

    public static void toggleRecording() {
        isRecording = !isRecording;

        if (isRecording) {
            addSystemMessage("You started recording");
            if (isWebSocketConnected() && getActiveMeetingId() != null) {
                sendWebSocketMessage("VIDEO_CONTROL", getActiveMeetingId(), "START_RECORDING");
            }
        } else {
            addSystemMessage("You stopped recording");
            if (isWebSocketConnected() && getActiveMeetingId() != null) {
                sendWebSocketMessage("VIDEO_CONTROL", getActiveMeetingId(), "STOP_RECORDING");
            }
        }

        if (videoControlsController != null) {
            Platform.runLater(() -> {
                videoControlsController.syncWithGlobalState();
            });
        }
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

    public static void setVideoControlsController(VideoControlsController controller) {
        videoControlsController = controller;
        System.out.println("🎥 Video controls controller registered");
    }

    public static VideoControlsController getVideoControlsController() {
        return videoControlsController;
    }

    /**
     * FIXED: Add system message to chat/log using MeetingController singleton
     */
    public static void addSystemMessage(String message) {
        System.out.println("💬 System: " + message);

        // Also pass to MeetingController using singleton pattern
        Platform.runLater(() -> {
            MeetingController meetingController = MeetingController.getInstance();
            if (meetingController != null) {
                meetingController.addSystemMessage(message);
            }
        });
    }

    public static void main(String[] args) {
        launch(args);
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

        // NEW: Show comprehensive network information
        public static void showNetworkInfo() {
            List<String> localIPs = HelloApplication.getLocalIPAddresses();
            System.out.println("🌐 NETWORK CONNECTION GUIDE:");
            System.out.println("=================================");

            if (localIPs.isEmpty()) {
                System.out.println("❌ No network interfaces found!");
                System.out.println("   Make sure you're connected to WiFi/Ethernet");
            } else {
                System.out.println("✅ Your computer's IP addresses:");
                for (String ip : localIPs) {
                    System.out.println("   📍 " + ip + ":8887");
                }
                System.out.println("\n🔗 Other devices should use:");
                for (String ip : localIPs) {
                    System.out.println("   ws://" + ip + ":8887");
                }
            }

            System.out.println("\n🔧 TROUBLESHOOTING:");
            System.out.println("   1. Make sure all devices are on same WiFi");
            System.out.println("   2. Turn off VPN if using one");
            System.out.println("   3. Check firewall settings");
            System.out.println("   4. Try different IP addresses from the list above");
            System.out.println("   5. Ensure server is running on the host computer");

            // Also show in a popup for the user
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Network Information");
                alert.setHeaderText("Your Network Connection Details");

                StringBuilder message = new StringBuilder();
                if (localIPs.isEmpty()) {
                    message.append("❌ No network interfaces found!\n\n");
                    message.append("Make sure you're connected to WiFi/Ethernet");
                } else {
                    message.append("✅ Your computer's IP addresses:\n");
                    for (String ip : localIPs) {
                        message.append("📍 ").append(ip).append(":8887\n");
                    }
                    message.append("\n🔗 Other devices should connect to:\n");
                    for (String ip : localIPs) {
                        message.append("ws://").append(ip).append(":8887\n");
                    }
                }

                message.append("\n🔧 Troubleshooting:\n");
                message.append("• Make sure all devices are on same WiFi\n");
                message.append("• Turn off VPN if using one\n");
                message.append("• Check firewall settings\n");
                message.append("• Ensure server is running on host computer");

                alert.setContentText(message.toString());
                alert.showAndWait();
            });
        }

        // Add this method to your HelloApplication class
        public static boolean connectToServer(String serverUrl) {
            try {
                System.out.println("🔗 Attempting to connect to: " + serverUrl);

                // Initialize WebSocket connection
                initializeWebSocketConnection(serverUrl);

                // Wait a moment for connection to establish
                Thread.sleep(1000);

                // Check if connection was successful
                SimpleWebSocketClient client = getWebSocketClient();
                if (client != null && client.isConnected()) {
                    System.out.println("✅ Successfully connected to: " + serverUrl);
                    return true;
                } else {
                    System.out.println("❌ Failed to connect to: " + serverUrl);
                    return false;
                }
            } catch (Exception e) {
                System.err.println("❌ Connection error: " + e.getMessage());
                return false;
            }
        }

        // NEW: Handle localhost connection failure and suggest alternatives
        public static void handleLocalhostFailure() {
            Platform.runLater(() -> {
                List<String> localIPs = HelloApplication.getLocalIPAddresses();

                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Localhost Connection Failed");
                alert.setHeaderText("Cannot connect to localhost:8887");

                StringBuilder content = new StringBuilder();
                content.append("The WebSocket server is not running on this device.\n\n");

                if (!localIPs.isEmpty()) {
                    content.append("If you're trying to connect to another computer:\n");
                    for (String ip : localIPs) {
                        content.append("• Try connecting to: ").append(ip).append(":8887\n");
                    }
                    content.append("\n");
                }

                content.append("Please choose an option:");

                alert.setContentText(content.toString());

                ButtonType discoverButton = new ButtonType("Discover Servers");
                ButtonType manualButton = new ButtonType("Enter Server IP");
                ButtonType cancelButton = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);

                alert.getButtonTypes().setAll(discoverButton, manualButton, cancelButton);

                Optional<ButtonType> result = alert.showAndWait();
                if (result.isPresent()) {
                    if (result.get() == discoverButton) {
                        HelloApplication.discoverAndConnectToServer();
                    } else if (result.get() == manualButton) {
                        HelloApplication.showManualConnectionDialog();
                    }
                }
            });
        }

        /**
         * Initialize WebSocket connection to a specific server URL
         * This is the missing method that was causing the compilation error
         */
        public static void initializeWebSocketConnection(String serverUrl) {
            try {
                System.out.println("🔗 Initializing WebSocket connection to: " + serverUrl);

                // Close existing connection if any
                if (webSocketClient != null) {
                    webSocketClient.disconnect();
                    webSocketClient = null;
                }

                // Create new WebSocket client
                webSocketClient = new SimpleWebSocketClient(serverUrl, HelloApplication::handleWebSocketMessage);

                // Set current user if logged in
                if (loggedInUser != null) {
                    webSocketClient.setCurrentUser(loggedInUser);
                }

                // Set connection listeners
                webSocketClient.setConnectionListener(new SimpleWebSocketClient.ConnectionListener() {
                    @Override
                    public void onConnected() {
                        System.out.println("✅ WebSocket connected to: " + serverUrl);
                        connectionInitialized = true;

                        // Extract and save server configuration
                        String urlWithoutProtocol = serverUrl.replace("ws://", "");
                        String[] parts = urlWithoutProtocol.split(":");
                        if (parts.length >= 2) {
                            serverIp = parts[0];
                            serverPort = parts[1];

                            // Save to database if user is logged in
                            if (loggedInUser != null) {
                                Database.saveServerConfig(loggedInUser, serverIp, serverPort);
                            }
                        }

                        // Notify connection status listener
                        if (connectionStatusListener != null) {
                            Platform.runLater(() -> {
                                connectionStatusListener.onConnectionStatusChanged(true, "Connected to " + serverUrl);
                            });
                        }
                    }

                    @Override
                    public void onDisconnected() {
                        System.out.println("WebSocket disconnected from: " + serverUrl);
                        connectionInitialized = false;

                        // Notify connection status listener
                        if (connectionStatusListener != null) {
                            Platform.runLater(() -> {
                                connectionStatusListener.onConnectionStatusChanged(false, "Disconnected");
                            });
                        }
                    }

                    @Override
                    public void onError(String error) {
                        System.err.println("❌ WebSocket error: " + error);
                        connectionInitialized = false;

                        // Notify connection status listener
                        if (connectionStatusListener != null) {
                            Platform.runLater(() -> {
                                connectionStatusListener.onConnectionStatusChanged(false, "Error: " + error);
                            });
                        }
                    }
                });

                // Connect to the server
                webSocketClient.connect();

            } catch (Exception e) {
                System.err.println("❌ Failed to initialize WebSocket connection: " + e.getMessage());
                connectionInitialized = false;

                // Notify about connection failure
                if (connectionStatusListener != null) {
                    Platform.runLater(() -> {
                        connectionStatusListener.onConnectionStatusChanged(false, "Connection failed: " + e.getMessage());
                    });
                }
            }
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