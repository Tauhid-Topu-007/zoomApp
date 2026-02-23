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
import java.util.UUID;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import javafx.scene.image.Image;

public class HelloApplication extends Application {

    private static MeetingController meetingController;
    private static Stage primaryStage;
    private static String loggedInUser;
    private static String activeMeetingId;
    private static final List<String> activeParticipants = new ArrayList<>();
    private static SimpleWebSocketClient webSocketClient;

    private static WebRTCManager webRTCManager;
    private static boolean webRTCEnabled = false;

    private static String serverIp = "localhost";
    private static String serverPort = "8887";
    private static final int DEFAULT_PORT = 8887;

    private static boolean connectionInitialized = false;
    private static ConnectionStatusListener connectionStatusListener;

    private static final Map<String, MeetingInfo> activeMeetings = new HashMap<>();
    private static boolean isMeetingHost = false;

    private static boolean audioMuted = false;
    private static boolean isDeafened = false;
    private static boolean allMuted = false;
    private static AudioControlsController audioControlsController;

    private static boolean videoOn = false;
    private static boolean isRecording = false;
    private static boolean virtualBackgroundEnabled = false;
    private static VideoControlsController videoControlsController;

    private static volatile boolean stopMonitoring = false;
    private static Thread monitorThread;

    private static volatile boolean stageReady = false;

    // Device identification
    private static String deviceId = UUID.randomUUID().toString().substring(0, 8);
    private static String deviceName = "Unknown";

    // Video streaming tracking
    private static boolean isVideoStreaming = false;
    private static int videoFramesSent = 0;
    private static int videoFramesReceived = 0;

    // Connected devices tracking
    private static final Map<String, DeviceInfo> connectedDevices = new HashMap<>();

    // HTTP client for REST API calls
    private static HttpClient httpClient = HttpClient.newHttpClient();

    public static class DeviceInfo {
        String deviceId;
        String deviceName;
        String username;
        String ipAddress;
        String deviceType;
        long lastSeen;
        boolean isVideoOn;
        boolean isAudioMuted;

        public DeviceInfo(String deviceId, String deviceName, String username, String ipAddress) {
            this.deviceId = deviceId;
            this.deviceName = deviceName;
            this.username = username;
            this.ipAddress = ipAddress;
            this.deviceType = "unknown";
            this.lastSeen = System.currentTimeMillis();
            this.isVideoOn = false;
            this.isAudioMuted = false;
        }

        public void updateLastSeen() {
            this.lastSeen = System.currentTimeMillis();
        }

        public boolean isActive(long timeoutMs) {
            return (System.currentTimeMillis() - lastSeen) < timeoutMs;
        }
    }

    public interface ConnectionStatusListener {
        void onConnectionStatusChanged(boolean connected, String status);
        void onDeviceListChanged(Map<String, DeviceInfo> devices);
    }

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        stageReady = true;

        // Set device name from system properties if available
        ensureDeviceInfo();

        System.out.println("Starting HelloApplication for device: " + deviceName + " (ID: " + deviceId + ")");

        Database.initializeDatabase();

        // Initialize WebRTC manager
        webRTCManager = WebRTCManager.getInstance();

        setRoot("login-view.fxml");
        stage.setTitle("Zoom Project - " + deviceName);
        stage.show();

        System.out.println("Primary stage initialized and ready");
        System.out.println("Make sure Node.js server is running on port " + DEFAULT_PORT);
        System.out.println("Run: node server/server.js");
    }

    /**
     * Ensure device info is properly set before any WebSocket operations
     */
    public static void ensureDeviceInfo() {
        // First check if already set
        if (deviceName != null && !deviceName.equals("Unknown") && deviceName.length() > 0) {
            return;
        }

        // Try to get from system properties
        String propName = System.getProperty("device.name");
        if (propName != null && !propName.isEmpty()) {
            deviceName = propName;
            System.out.println("📱 Device name loaded from system properties: " + deviceName);
        } else {
            // Generate from device ID
            if (deviceId == null || deviceId.isEmpty()) {
                deviceId = System.getProperty("device.id", UUID.randomUUID().toString().substring(0, 8));
            }
            deviceName = "Device-" + deviceId.substring(0, 4);
            System.setProperty("device.name", deviceName);
            System.out.println("📱 Generated device name: " + deviceName);
        }

        // Ensure device ID is set
        if (deviceId == null || deviceId.isEmpty()) {
            deviceId = System.getProperty("device.id", UUID.randomUUID().toString().substring(0, 8));
        }

        // Update connected devices if user is logged in
        if (loggedInUser != null && !connectedDevices.containsKey(deviceId)) {
            DeviceInfo deviceInfo = new DeviceInfo(deviceId, deviceName, loggedInUser, getLocalIPAddress());
            connectedDevices.put(deviceId, deviceInfo);
        }

        System.out.println("✅ Device info ensured: " + deviceName + " (" + deviceId + ")");
    }

    private void initializeWebRTC() {
        try {
            webRTCManager = WebRTCManager.getInstance();
            System.out.println("WebRTC manager initialized");
        } catch (Exception e) {
            System.err.println("Failed to initialize WebRTC: " + e.getMessage());
            webRTCEnabled = false;
        }
    }

    public static WebRTCManager getWebRTCManager() {
        return webRTCManager;
    }

    public static boolean isWebRTCEnabled() {
        return webRTCEnabled && webRTCManager != null;
    }

    public static void enableWebRTC() {
        if (webRTCManager != null) {
            webRTCManager.enableWebRTC();
            webRTCEnabled = true;
            System.out.println("WebRTC enabled");
        }
    }

    public static void disableWebRTC() {
        if (webRTCManager != null) {
            webRTCManager.disableWebRTC();
            webRTCEnabled = false;
            System.out.println("WebRTC disabled");
        }
    }

    public static void startWebRTCSession() {
        if (activeMeetingId != null && webRTCManager != null && webRTCEnabled) {
            webRTCManager.startWebRTCSession(activeMeetingId, loggedInUser);
            System.out.println("WebRTC session started for meeting: " + activeMeetingId);
        } else {
            System.out.println("Cannot start WebRTC session. Check if meeting is active and WebRTC is enabled.");
        }
    }

    public static void stopWebRTCSession() {
        if (webRTCManager != null) {
            webRTCManager.stop();
            System.out.println("WebRTC session stopped");
        }
    }

    public static void stopRecording() {
        if (isRecording) {
            toggleRecording();
        }
    }

    @Override
    public void stop() throws Exception {
        stopWebRTCSession();
        stopConnectionAttempts();
        leaveCurrentMeeting();

        if (webSocketClient != null) {
            webSocketClient.disconnect();
            webSocketClient = null;
        }

        if (videoOn) {
            stopVideo();
        }
        if (isRecording) {
            stopRecording();
        }

        if (loggedInUser != null) {
            Database.saveServerConfig(loggedInUser, serverIp, serverPort);
        }

        connectionInitialized = false;
        stageReady = false;

        super.stop();
    }

    public static void endMeeting(String meetingId) {
        if (activeMeetings.containsKey(meetingId)) {
            stopWebRTCSession();

            if (isWebSocketConnected()) {
                sendWebSocketMessage("MEETING_ENDED", meetingId, loggedInUser, "Meeting ended by host");
            }

            activeMeetings.remove(meetingId);
            activeParticipants.clear();
            connectedDevices.clear();

            Database.removeMeeting(meetingId);

            System.out.println("Meeting ended: " + meetingId);

            if (audioMuted) {
                toggleAudio();
            }
            if (videoOn) {
                toggleVideo();
            }
            if (isRecording) {
                stopRecording();
            }

            notifyControllersMeetingStateChanged(false);

            if (connectionStatusListener != null) {
                Platform.runLater(() -> {
                    connectionStatusListener.onDeviceListChanged(new HashMap<>(connectedDevices));
                });
            }
        }
    }

    public static void leaveCurrentMeeting() {
        if (activeMeetingId != null && loggedInUser != null) {
            stopWebRTCSession();

            removeParticipantFromMeeting(activeMeetingId, loggedInUser);

            if (isWebSocketConnected()) {
                sendWebSocketMessage("USER_LEFT", activeMeetingId, loggedInUser, "left the meeting");
            }

            removeParticipant(loggedInUser);
            connectedDevices.remove(deviceId);

            if (isMeetingHost) {
                endMeeting(activeMeetingId);
            }

            System.out.println("Left meeting: " + activeMeetingId);
        }

        activeMeetingId = null;
        isMeetingHost = false;

        if (audioMuted) {
            toggleAudio();
        }
        if (videoOn) {
            stopVideo();
        }
        if (isRecording) {
            stopRecording();
        }

        notifyControllersMeetingStateChanged(false);

        if (connectionStatusListener != null) {
            Platform.runLater(() -> {
                connectionStatusListener.onDeviceListChanged(new HashMap<>(connectedDevices));
            });
        }
    }

    public static void toggleVideo() {
        ensureDeviceInfo(); // Ensure device info is set

        videoOn = !videoOn;
        isVideoStreaming = videoOn;

        System.out.println("VIDEO TOGGLE for device: " + deviceName);
        System.out.println("New state: " + (videoOn ? "ON" : "OFF"));
        System.out.println("User: " + loggedInUser);
        System.out.println("Is Host: " + isMeetingHost);
        System.out.println("WebSocket: " + isWebSocketConnected());
        System.out.println("Meeting ID: " + activeMeetingId);

        // Update device info
        DeviceInfo deviceInfo = connectedDevices.get(deviceId);
        if (deviceInfo != null) {
            deviceInfo.isVideoOn = videoOn;
            deviceInfo.updateLastSeen();
        }

        if (videoOn) {
            addSystemMessage("You started video streaming");
            System.out.println("Video STARTED - starting streamer...");

            if (activeMeetingId != null && loggedInUser != null) {
                SimpleVideoStreamer.startStreaming(loggedInUser, activeMeetingId);
                System.out.println("SimpleVideoStreamer started");
            } else {
                System.err.println("Cannot start stream - missing meeting ID or username");
            }
        } else {
            addSystemMessage("You stopped video streaming");
            System.out.println("Video STOPPED");
            videoFramesSent = 0;
        }

        if (isWebSocketConnected() && activeMeetingId != null && loggedInUser != null) {
            String status = videoOn ? "VIDEO_STARTED" : "VIDEO_STOPPED";

            // Include device info in the message
            String content = status + "|" + deviceId + "|" + deviceName;

            System.out.println("Sending VIDEO_STATUS: " + status);
            System.out.println("To meeting: " + activeMeetingId);
            System.out.println("From user: " + loggedInUser);
            System.out.println("From device: " + deviceName + " (" + deviceId + ")");

            sendWebSocketMessage("VIDEO_STATUS", activeMeetingId, loggedInUser, content);
        } else {
            System.err.println("Cannot send video status:");
            System.err.println("WebSocket: " + isWebSocketConnected());
            System.err.println("Meeting ID: " + activeMeetingId);
            System.err.println("User: " + loggedInUser);
        }

        if (videoControlsController != null) {
            Platform.runLater(() -> {
                videoControlsController.syncWithGlobalState();
                System.out.println("Updated video controls controller");
            });
        } else {
            System.err.println("Video controls controller is null!");
        }

        Platform.runLater(() -> {
            MeetingController meetingController = MeetingController.getInstance();
            if (meetingController != null) {
                meetingController.updateVideoState(videoOn);
                System.out.println("Updated MeetingController video state");
            }
        });

        // Notify listeners of device list change
        if (connectionStatusListener != null) {
            Platform.runLater(() -> {
                connectionStatusListener.onDeviceListChanged(new HashMap<>(connectedDevices));
            });
        }
    }

    public static void handleVideoStatus(String username, String content) {
        System.out.println("Received video status: " + content + " from user: " + username);

        // Parse content which may include device info
        String[] parts = content.split("\\|");
        String status = parts[0];
        String deviceIdFromMsg = parts.length > 1 ? parts[1] : "unknown";
        String deviceNameFromMsg = parts.length > 2 ? parts[2] : "unknown";

        if (username.equals(loggedInUser)) {
            return;
        }

        // Update device info
        DeviceInfo deviceInfo = connectedDevices.get(deviceIdFromMsg);
        if (deviceInfo != null) {
            deviceInfo.isVideoOn = status.contains("STARTED");
            deviceInfo.updateLastSeen();
        } else {
            // Create new device info if not exists
            deviceInfo = new DeviceInfo(deviceIdFromMsg, deviceNameFromMsg, username, "unknown");
            deviceInfo.isVideoOn = status.contains("STARTED");
            connectedDevices.put(deviceIdFromMsg, deviceInfo);
        }

        Platform.runLater(() -> {
            if (videoControlsController != null) {
                videoControlsController.updateFromServer(username, status);
            }

            if ("VIDEO_STARTED".equals(status)) {
                addSystemMessage(username + " (" + deviceNameFromMsg + ") started their video");
            } else if ("VIDEO_STOPPED".equals(status)) {
                addSystemMessage(username + " (" + deviceNameFromMsg + ") stopped their video");
            }
        });

        if (connectionStatusListener != null) {
            Platform.runLater(() -> {
                connectionStatusListener.onDeviceListChanged(new HashMap<>(connectedDevices));
            });
        }
    }

    public static boolean isVideoOn() {
        return videoOn;
    }

    public static boolean isVideoStreaming() {
        return isVideoStreaming;
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

    public static void setPrimaryStage(Stage stage) {
        primaryStage = stage;
        stageReady = true;
        System.out.println("Primary stage set in HelloApplication: " + (stage != null ? "VALID" : "NULL"));
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    public static boolean isStageReady() {
        return stageReady && primaryStage != null;
    }

    public static void waitForStageReady(Runnable callback) {
        new Thread(() -> {
            int attempts = 0;
            while (!stageReady && attempts < 50) {
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
                System.err.println("Stage never became ready after 5 seconds");
            }
        }).start();
    }

    public static void setRoot(String fxml) throws Exception {
        if (primaryStage == null) {
            System.err.println("Cannot set root: primaryStage is null");
            throw new IllegalStateException("Primary stage is not initialized");
        }

        FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource(fxml));
        Scene scene = new Scene(loader.load());

        Platform.runLater(() -> {
            try {
                primaryStage.setScene(scene);

                primaryStage.setFullScreen("meeting-view.fxml".equals(fxml));

                Object controller = loader.getController();
                if (controller instanceof ChatController) {
                    ((ChatController) controller).setStage(primaryStage);
                } else if (controller instanceof SettingsController) {
                    ((SettingsController) controller).setStage(primaryStage);
                } else if (controller instanceof AudioControlsController) {
                    audioControlsController = (AudioControlsController) controller;
                } else if (controller instanceof VideoControlsController) {
                    videoControlsController = (VideoControlsController) controller;
                    System.out.println("Video controls controller registered");
                } else if (controller instanceof DashboardController) {
                    DashboardController dashboardController = (DashboardController) controller;
                    setConnectionStatusListener(dashboardController);
                    System.out.println("Dashboard controller loaded and registered for connection updates");
                } else if (controller instanceof MeetingController) {
                    meetingController = (MeetingController) controller;
                    meetingController.setStage(primaryStage);
                    System.out.println("Meeting controller registered and stage set");
                }

                System.out.println("Root set to: " + fxml);
            } catch (Exception e) {
                System.err.println("Error setting root: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public static void setRootSafe(String fxml) {
        if (!stageReady || primaryStage == null) {
            System.err.println("Stage not ready, cannot set root to: " + fxml);
            return;
        }

        Platform.runLater(() -> {
            try {
                setRoot(fxml);
            } catch (Exception e) {
                System.err.println("Failed to set root: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    public static void navigateToDashboard() {
        if (!stageReady || primaryStage == null) {
            System.err.println("Stage not ready for navigation, waiting...");
            waitForStageReady(() -> {
                try {
                    setRoot("dashboard-view.fxml");
                    System.out.println("Successfully navigated to dashboard after wait");
                } catch (Exception e) {
                    System.err.println("Failed to navigate to dashboard: " + e.getMessage());
                    e.printStackTrace();
                }
            });
        } else {
            try {
                setRoot("dashboard-view.fxml");
                System.out.println("Successfully navigated to dashboard");
            } catch (Exception e) {
                System.err.println("Failed to navigate to dashboard: " + e.getMessage());
                e.printStackTrace();
            }
        }
    }

    public static void navigateWithFallback(String fxml, Runnable fallback) {
        if (isStageReady()) {
            try {
                setRoot(fxml);
            } catch (Exception e) {
                System.err.println("Navigation failed: " + e.getMessage());
                if (fallback != null) {
                    fallback.run();
                }
            }
        } else {
            System.out.println("Stage not ready, waiting...");
            waitForStageReady(() -> {
                try {
                    setRoot(fxml);
                } catch (Exception e) {
                    System.err.println("Navigation failed after wait: " + e.getMessage());
                    if (fallback != null) {
                        fallback.run();
                    }
                }
            });
        }
    }

    public static void setLoggedInUser(String username) {
        loggedInUser = username;

        // Ensure device info is set
        ensureDeviceInfo();

        // Update device info with username
        DeviceInfo deviceInfo = new DeviceInfo(deviceId, deviceName, username, getLocalIPAddress());
        connectedDevices.put(deviceId, deviceInfo);

        loadUserServerConfig(username);

        if (username != null) {
            initializeWebSocket(username);
        }
    }

    public static String getLoggedInUser() {
        return loggedInUser;
    }

    public static void logout() throws Exception {
        stopWebRTCSession();
        stopConnectionAttempts();
        setConnectionStatusListener(null);
        leaveCurrentMeeting();

        if (webSocketClient != null) {
            webSocketClient.disconnect();
            webSocketClient = null;
        }

        loggedInUser = null;
        activeParticipants.clear();
        activeMeetingId = null;
        isMeetingHost = false;
        connectionInitialized = false;
        connectedDevices.clear();

        audioMuted = false;
        isDeafened = false;
        allMuted = false;
        audioControlsController = null;

        videoOn = false;
        isRecording = false;
        virtualBackgroundEnabled = false;
        videoControlsController = null;

        setRootSafe("login-view.fxml");
    }

    public static void initializeWebSocket(String username) {
        String savedUrl = getCurrentServerUrl();
        System.out.println("Attempting connection to saved server: " + savedUrl + " for device: " + deviceName);

        if (!testConnection(savedUrl)) {
            System.out.println("Saved server connection failed");

            if (savedUrl.contains("localhost")) {
                System.out.println("Localhost failed - suggesting network connection");
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
                    resetConnectionAndRetry("ws://localhost:8887");
                }
            }
        });
    }

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
                        String server = availableServers.get(0);
                        String[] parts = server.split(":");
                        if (parts.length >= 2) {
                            String serverUrl = "ws://" + server;
                            resetConnectionAndRetry(serverUrl);
                            showAlert(Alert.AlertType.INFORMATION, "Connected",
                                    "Connected to: " + server);
                        }
                    } else {
                        showServerSelectionDialog(availableServers);
                    }
                });
            }).start();
        });
    }

    private static void showNoServersFoundDialog(List<String> localIPs) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("No Servers Found");
        alert.setHeaderText("No Zoom servers found on your local network");

        StringBuilder content = new StringBuilder();
        content.append("Please make sure:\n\n");
        content.append("• The Node.js server (server.js) is running on another device\n");
        content.append("• Both devices are on the same WiFi network\n");
        content.append("• Firewall allows port " + DEFAULT_PORT + "\n\n");

        if (!localIPs.isEmpty()) {
            content.append("Your network IP addresses:\n");
            for (String ip : localIPs) {
                content.append(ip).append("\n");
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

    public static void checkServerStatus() {
        String currentUrl = getCurrentServerUrl();
        System.out.println("Checking server status: " + currentUrl);

        if (testConnection(currentUrl)) {
            showAlert(Alert.AlertType.INFORMATION, "Server Status",
                    "Server is running and accessible at:\n" + currentUrl);
        } else {
            if (currentUrl.contains("localhost")) {
                showAlert(Alert.AlertType.ERROR, "Server Status",
                        "Local server is not running\n\n" +
                                "The Node.js WebSocket server needs to be started on this computer " +
                                "or connect to another computer running the server.\n\n" +
                                "Run: node server/server.js");
            } else {
                showAlert(Alert.AlertType.ERROR, "Server Status",
                        "Cannot connect to server at:\n" + currentUrl +
                                "\n\nPlease check if the server is running and accessible.");
            }
        }
    }

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

                resetConnectionAndRetry(serverUrl);
                showAlert(Alert.AlertType.INFORMATION, "Connecting",
                        "Connecting to: " + server);
            }
        });
    }

    private static void loadUserServerConfig(String username) {
        Database.ServerConfig config = Database.getServerConfig(username);
        if (config != null) {
            serverIp = config.getServerIp();
            serverPort = config.getServerPort();
            System.out.println("Loaded user server config: " + config.getServerUrl());
        } else {
            serverIp = "localhost";
            serverPort = "8887";
            System.out.println("Using default server config: ws://localhost:8887");
        }
    }

    private static void initializeWebSocketWithUrl(String serverUrl) {
        try {
            System.out.println("Initializing WebSocket connection to: " + serverUrl + " for device: " + deviceName);

            if (webSocketClient != null) {
                webSocketClient.disconnect();
                webSocketClient = null;
            }

            webSocketClient = new SimpleWebSocketClient(serverUrl, HelloApplication::handleWebSocketMessage);
            if (loggedInUser != null) {
                webSocketClient.setCurrentUser(loggedInUser);
            }

            // Add custom headers for device identification
            webSocketClient.addHeader("Device-ID", deviceId);
            webSocketClient.addHeader("Device-Name", deviceName);

            connectionInitialized = true;
            stopMonitoring = false;

            startConnectionMonitoring();

            // Send device info once connected
            sendDeviceInfo();

        } catch (Exception e) {
            System.err.println("Failed to initialize WebSocket: " + e.getMessage());
            handleConnectionFailure(serverUrl);
        }
    }

    private static void sendDeviceInfo() {
        if (webSocketClient != null && webSocketClient.isConnected() && loggedInUser != null) {
            String deviceType = getDeviceType();
            String content = deviceName + "|" + deviceId + "|" + deviceType + "|" + getLocalIPAddress();
            webSocketClient.sendMessage("DEVICE_INFO", "global", loggedInUser, content);
            System.out.println("Sent device info to server: " + deviceName + " (" + deviceId + ")");
        }
    }

    private static String getDeviceType() {
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) return "Windows";
        if (os.contains("mac")) return "macOS";
        if (os.contains("linux")) return "Linux";
        return "Unknown";
    }

    private static String getLocalIPAddress() {
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
                            return ip;
                        }
                    }
                }
            }
        } catch (SocketException e) {
            System.err.println("Error getting local IP: " + e.getMessage());
        }
        return "unknown";
    }

    private static void startConnectionMonitoring() {
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
            final int MAX_CONSECUTIVE_FAILURES = 2;

            System.out.println("Starting connection monitoring for device: " + deviceName);

            while (!stopMonitoring && connectionInitialized && loggedInUser != null) {
                try {
                    Thread.sleep(5000);

                    boolean currentConnectedState = isWebSocketConnected();

                    if (currentConnectedState != previousConnectedState) {
                        String status = currentConnectedState ? "Connected" : "Disconnected";
                        System.out.println("Connection state changed: " + status + " for device: " + deviceName);

                        if (connectionStatusListener != null) {
                            Platform.runLater(() -> {
                                connectionStatusListener.onConnectionStatusChanged(currentConnectedState, status);
                            });
                        }

                        previousConnectedState = currentConnectedState;

                        if (currentConnectedState) {
                            consecutiveFailures = 0;
                            System.out.println("Connection restored, sending device info");
                            sendDeviceInfo();
                        }
                    }

                    if (!currentConnectedState) {
                        consecutiveFailures++;
                        System.out.println("Attempting to reconnect device " + deviceName + "... (Failure #" + consecutiveFailures + ")");

                        if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                            System.out.println("Too many connection failures (" + consecutiveFailures + "), triggering fallback...");

                            Platform.runLater(() -> {
                                showConnectionFallbackDialog();
                            });

                            consecutiveFailures = 0;
                            Thread.sleep(10000);
                            continue;
                        }

                        try {
                            initializeWebSocketWithUrl(getCurrentServerUrl());
                        } catch (Exception e) {
                            System.err.println("Reconnection failed: " + e.getMessage());
                        }
                    } else if (currentConnectedState) {
                        consecutiveFailures = 0;
                        // Send heartbeat
                        sendHeartbeat();
                    }

                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    System.err.println("Error in connection monitoring: " + e.getMessage());
                }
            }
            System.out.println("Connection monitoring stopped for device: " + deviceName);
        });
        monitorThread.setDaemon(true);
        monitorThread.setName("WebSocket-Monitor-" + deviceName);
        monitorThread.start();
    }

    private static void sendHeartbeat() {
        if (webSocketClient != null && webSocketClient.isConnected() && activeMeetingId != null) {
            webSocketClient.sendMessage("HEARTBEAT", activeMeetingId, loggedInUser, deviceId);

            // Update device last seen
            DeviceInfo deviceInfo = connectedDevices.get(deviceId);
            if (deviceInfo != null) {
                deviceInfo.updateLastSeen();
            }
        }
    }

    private static void showConnectionFallbackDialog() {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Connection Issues - " + deviceName);
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
                    resetConnectionAndRetry("ws://localhost:8887");
                }
            }
        });
    }

    public static void setConnectionStatusListener(ConnectionStatusListener listener) {
        connectionStatusListener = listener;

        if (listener != null) {
            boolean connected = isWebSocketConnected();
            String status = connected ? "Connected" : "Disconnected";
            Platform.runLater(() -> {
                listener.onConnectionStatusChanged(connected, status);
                listener.onDeviceListChanged(new HashMap<>(connectedDevices));
            });
        }
    }

    public static void handleConnectionFailure(String attemptedUrl) {
        System.err.println("Connection failed to: " + attemptedUrl + " for device: " + deviceName);

        Platform.runLater(() -> {
            if (connectionStatusListener != null) {
                connectionStatusListener.onConnectionStatusChanged(false, "Connection failed");
            }

            if (loggedInUser != null && primaryStage != null) {
                showConnectionErrorAndDiscover(attemptedUrl);
            }
        });
    }

    private static void showConnectionErrorAndDiscover(String attemptedUrl) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Connection Failed - " + deviceName);
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

    public static void showManualConnectionDialog() {
        TextInputDialog ipDialog = new TextInputDialog("192.168.1.");
        ipDialog.setTitle("Manual Server Connection - " + deviceName);
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

            connectToServerManual(ip, port);
        }
    }

    public static void connectToServerManual(String ip, String port) {
        if (ip == null || ip.trim().isEmpty()) {
            showAlert(Alert.AlertType.ERROR, "Invalid IP", "Please enter a valid IP address");
            return;
        }

        if (port == null || port.trim().isEmpty()) {
            port = "8887";
        }

        if (!isValidIPAddress(ip) && !ip.equals("localhost")) {
            showAlert(Alert.AlertType.ERROR, "Invalid IP", "Please enter a valid IP address (e.g., 192.168.1.100)");
            return;
        }

        String serverUrl = "ws://" + ip + ":" + port;
        System.out.println("Attempting manual connection to: " + serverUrl + " for device: " + deviceName);

        resetConnectionAndRetry(serverUrl);
    }

    public static void stopConnectionAttempts() {
        System.out.println("Stopping all connection attempts for device: " + deviceName);
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

        System.out.println("All connection attempts stopped for device: " + deviceName);
    }

    public static void resetConnectionAndRetry(String newUrl) {
        System.out.println("Resetting connection and retrying with: " + newUrl + " for device: " + deviceName);
        stopConnectionAttempts();

        String urlWithoutProtocol = newUrl.replace("ws://", "");
        String[] parts = urlWithoutProtocol.split(":");
        if (parts.length >= 2) {
            serverIp = parts[0];
            serverPort = parts[1];

            if (loggedInUser != null) {
                Database.saveServerConfig(loggedInUser, serverIp, serverPort);
            }
        }

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

    public static boolean isValidIPAddress(String ip) {
        String ipPattern = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
        return ip.matches(ipPattern);
    }

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

    public static void setServerConfig(String ip, String port) {
        serverIp = ip;
        serverPort = port;
        System.out.println("Server config updated: " + ip + ":" + port + " for device: " + deviceName);

        if (loggedInUser != null) {
            Database.saveServerConfig(loggedInUser, ip, port);
        }

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

    public static String getDeviceId() {
        return deviceId;
    }

    public static String getDeviceName() {
        return deviceName;
    }

    public static Map<String, DeviceInfo> getConnectedDevices() {
        return new HashMap<>(connectedDevices);
    }

    public static void reinitializeWebSocket(String newUrl) {
        resetConnectionAndRetry(newUrl);
    }

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
            System.err.println("Error getting network interfaces: " + e.getMessage());
        }
        return ips;
    }

    public static List<String> discoverAvailableServers() {
        List<String> availableServers = new ArrayList<>();
        List<String> localIPs = getLocalIPAddresses();

        System.out.println("Starting enhanced network discovery for device: " + deviceName);
        System.out.println("Your local IP addresses: " + localIPs);

        if (testConnection("ws://localhost:8887")) {
            availableServers.add("localhost:8887");
        }

        List<String> ipsToTest = new ArrayList<>();

        for (String localIp : localIPs) {
            if (!ipsToTest.contains(localIp)) {
                ipsToTest.add(localIp);
            }

            if (localIp.contains(".")) {
                String baseIp = localIp.substring(0, localIp.lastIndexOf('.') + 1);
                for (int i = 1; i <= 20; i++) {
                    String testIp = baseIp + i;
                    if (!testIp.equals(localIp) && !ipsToTest.contains(testIp)) {
                        ipsToTest.add(testIp);
                    }
                }
            }
        }

        System.out.println("Testing " + ipsToTest.size() + " IP addresses...");

        List<Thread> threads = new ArrayList<>();
        List<String> discoveredServers = Collections.synchronizedList(new ArrayList<>());

        for (String ip : ipsToTest) {
            Thread thread = new Thread(() -> {
                String testUrl = "ws://" + ip + ":8887";
                if (testConnection(testUrl)) {
                    discoveredServers.add(ip + ":8887");
                    System.out.println("Found server: " + testUrl);
                }
            });
            thread.start();
            threads.add(thread);
        }

        for (Thread thread : threads) {
            try {
                thread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        availableServers.addAll(discoveredServers);
        System.out.println("Discovery complete. Found " + availableServers.size() + " servers: " + availableServers);

        return availableServers;
    }

    public static boolean testConnection(String serverUrl) {
        final AtomicBoolean connectionSuccess = new AtomicBoolean(false);
        final AtomicBoolean receivedDisconnect = new AtomicBoolean(false);
        final Object lock = new Object();
        SimpleWebSocketClient testClient = null;

        try {
            System.out.println("Testing connection to: " + serverUrl + " from device: " + deviceName);

            testClient = new SimpleWebSocketClient(serverUrl, message -> {
                System.out.println("Test connection received: " + message);

                if (message.contains("Connected") || message.contains("Welcome") ||
                        message.contains("WELCOME") || message.contains("connected")) {
                    synchronized (lock) {
                        connectionSuccess.set(true);
                        receivedDisconnect.set(false);
                        lock.notifyAll();
                    }
                }
                else if (message.contains("DISCONNECTED") || message.contains("ERROR") ||
                        message.contains("Failed") || message.contains("disconnected")) {
                    synchronized (lock) {
                        receivedDisconnect.set(true);
                        lock.notifyAll();
                    }
                }
            });

            synchronized (lock) {
                try {
                    lock.wait(2000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            if (testClient != null) {
                testClient.disconnect();
            }

            boolean success = connectionSuccess.get() && !receivedDisconnect.get();

            if (!success && serverUrl.contains("localhost")) {
                System.out.println("Localhost connection failed - server likely not running on this device");
                System.out.println("Run: node server/server.js");
            }

            System.out.println("Connection test result for " + serverUrl + ": " +
                    (success ? "SUCCESS" : "FAILED") +
                    " [Connected: " + connectionSuccess.get() +
                    ", Disconnected: " + receivedDisconnect.get() + "]");

            return success;

        } catch (Exception e) {
            System.err.println("Connection test failed for " + serverUrl + ": " + e.getMessage());
            if (testClient != null) {
                testClient.disconnect();
            }

            if (e.getMessage().contains("Connection refused") && serverUrl.contains("localhost")) {
                System.out.println("Hint: Node.js WebSocket server is not running on this device.");
                System.out.println("Start it with: node server/server.js");
            }
            return false;
        }
    }

    public static void showConnectionGuide() {
        List<String> localIPs = getLocalIPAddresses();

        StringBuilder guide = new StringBuilder();
        guide.append("MULTI-DEVICE CONNECTION GUIDE\n");
        guide.append("================================\n\n");

        if (localIPs.isEmpty()) {
            guide.append("No network interfaces found!\n");
            guide.append("Make sure you're connected to WiFi/Ethernet\n\n");
        } else {
            guide.append("Your Server IP Addresses:\n");
            for (String ip : localIPs) {
                guide.append(ip).append(":").append(DEFAULT_PORT).append("\n");
            }
            guide.append("\n");
        }

        guide.append("CONNECTION STEPS:\n");
        guide.append("1. Run the Node.js server on Server device\n");
        guide.append("   > node server/server.js\n");
        guide.append("2. Note the Server IP address from above\n");
        guide.append("3. On Client device, use 'Manual Connect'\n");
        guide.append("4. Enter Server IP address\n");
        guide.append("5. Click Connect\n\n");

        guide.append("TROUBLESHOOTING:\n");
        guide.append("• Ensure both devices on same WiFi\n");
        guide.append("• Disable VPN temporarily\n");
        guide.append("• Check firewall settings on Server\n");
        guide.append("• Verify Node.js server is running\n");
        guide.append("• Port ").append(DEFAULT_PORT).append(" must be open\n");
        guide.append("• On Windows, run: netsh advfirewall firewall add rule name=\"Zoom WebSocket\" dir=in action=allow protocol=TCP localport=").append(DEFAULT_PORT).append("\n");

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

    public static void sendVideoFrame(String meetingId, String username, String base64Image) {
        if (webSocketClient != null && webSocketClient.isConnected() && isWebSocketConnected()) {
            try {
                // Include device info in video frame
                String message = "VIDEO_FRAME|" + meetingId + "|" + username + "|" + base64Image + "|" + deviceId + "|" + deviceName;
                webSocketClient.send(message);
                videoFramesSent++;
                if (videoFramesSent % 10 == 0) {
                    System.out.println("Sent " + videoFramesSent + " video frames from: " + username + " on device: " + deviceName);
                }
            } catch (Exception e) {
                System.err.println("Failed to send video frame: " + e.getMessage());
            }
        } else {
            System.err.println("WebSocket not connected for video streaming");
        }
    }

    public static void sendVideoFrame(Image image) {
        if (!isWebSocketConnected() || getActiveMeetingId() == null || !isVideoOn()) {
            return;
        }

        try {
            String base64Image = convertImageToBase64(image);
            if (base64Image != null && !base64Image.isEmpty()) {
                sendVideoFrame(getActiveMeetingId(), loggedInUser, base64Image);
            }
        } catch (Exception e) {
            System.err.println("Error sending video frame: " + e.getMessage());
        }
    }

    private static String convertImageToBase64(Image image) {
        try {
            java.awt.image.BufferedImage bufferedImage = convertToBufferedImage(image);
            if (bufferedImage == null) return null;

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(bufferedImage, "jpg", baos);
            byte[] imageBytes = baos.toByteArray();
            return java.util.Base64.getEncoder().encodeToString(imageBytes);

        } catch (Exception e) {
            System.err.println("Error converting image to base64: " + e.getMessage());
            return null;
        }
    }

    private static java.awt.image.BufferedImage convertToBufferedImage(Image image) {
        try {
            int width = (int) image.getWidth();
            int height = (int) image.getHeight();

            java.awt.image.BufferedImage bufferedImage = new java.awt.image.BufferedImage(
                    width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);

            java.awt.Graphics2D g2d = bufferedImage.createGraphics();

            java.awt.Image awtImage = javafx.embed.swing.SwingFXUtils.fromFXImage(image, null);
            g2d.drawImage(awtImage, 0, 0, null);
            g2d.dispose();

            return bufferedImage;
        } catch (Exception e) {
            System.err.println("Error converting to BufferedImage: " + e.getMessage());
            return null;
        }
    }

    private static void handleVideoFrame(String username, String content) {
        try {
            String[] parts = content.split("\\|", -1);
            String base64Image = parts[0];
            String deviceIdFromMsg = parts.length > 1 ? parts[1] : "unknown";
            String deviceNameFromMsg = parts.length > 2 ? parts[2] : "unknown";

            videoFramesReceived++;
            if (videoFramesReceived % 10 == 0) {
                System.out.println("Received " + videoFramesReceived + " video frames from: " + username +
                        " on device: " + deviceNameFromMsg);
            }

            // Update device info
            DeviceInfo deviceInfo = connectedDevices.get(deviceIdFromMsg);
            if (deviceInfo != null) {
                deviceInfo.updateLastSeen();
            } else {
                // Create new device info if not exists
                deviceInfo = new DeviceInfo(deviceIdFromMsg, deviceNameFromMsg, username, "unknown");
                connectedDevices.put(deviceIdFromMsg, deviceInfo);
            }

            Image videoFrame = convertBase64ToImage(base64Image);
            if (videoFrame != null) {
                Platform.runLater(() -> {
                    MeetingController meetingController = MeetingController.getInstance();
                    if (meetingController != null) {
                        meetingController.displayVideoFrame(username, videoFrame);
                    }

                    if (videoControlsController != null) {
                        videoControlsController.displayVideoFrame(videoFrame);
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("Error handling video frame: " + e.getMessage());
        }
    }

    public static Image convertBase64ToImage(String base64Image) {
        try {
            if (base64Image == null || base64Image.isEmpty()) {
                System.err.println("Empty base64 string");
                return null;
            }

            byte[] imageBytes = java.util.Base64.getDecoder().decode(base64Image);
            if (imageBytes == null || imageBytes.length == 0) {
                System.err.println("Decoded bytes are empty");
                return null;
            }

            java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(imageBytes);
            Image image = new Image(bis);

            if (image.isError()) {
                System.err.println("Error creating image from bytes");
                return null;
            }

            return image;

        } catch (Exception e) {
            System.err.println("Error converting base64 to image: " + e.getMessage());
            return null;
        }
    }

    private static void handleWebSocketMessage(String message) {
        System.out.println("HelloApplication received WebSocket message on device: " + deviceName);
        System.out.println("Full message: " + message);

        String[] parts = message.split("\\|", -1);
        if (parts.length >= 4) {
            String type = parts[0];
            String meetingId = parts[1];
            String username = parts[2];
            String content = parts[3];

            // Skip our own messages
            if (username.equals(loggedInUser)) {
                System.out.println("Ignoring own message echo: " + type);
                return;
            }

            // Handle device list messages
            if (type.equals("DEVICE_LIST") && parts.length >= 4) {
                handleDeviceList(content);
                return;
            }

            if (type.equals("DEVICE_CONNECTED") && parts.length >= 5) {
                String deviceInfo = parts[4];
                handleDeviceConnected(username, deviceInfo);
                return;
            }

            if (type.equals("DEVICE_DISCONNECTED") && parts.length >= 5) {
                String deviceInfo = parts[4];
                handleDeviceDisconnected(username, deviceInfo);
                return;
            }

            // Check if this message is for our current meeting
            String currentMeetingId = getActiveMeetingId();
            if (!meetingId.equals(currentMeetingId) && !meetingId.equals("global")) {
                System.out.println("Message not for current meeting. Current: " + currentMeetingId + ", Message: " + meetingId);
                return;
            }

            // Route the message to the appropriate handler
            final String finalMessage = message;
            Platform.runLater(() -> {
                MeetingController meetingController = MeetingController.getInstance();
                if (meetingController != null) {
                    System.out.println("Forwarding message to MeetingController");
                    meetingController.handleWebSocketMessage(finalMessage);
                } else {
                    System.out.println("MeetingController not available, handling locally");
                    switch (type) {
                        case "CHAT_MESSAGE":
                        case "CHAT":
                            addSystemMessage(username + ": " + content);
                            break;
                        case "USER_JOINED":
                            handleUserJoined(username, meetingId, content);
                            break;
                        case "USER_LEFT":
                            handleUserLeft(username, meetingId, content);
                            break;
                        case "VIDEO_STATUS":
                            handleVideoStatus(username, content);
                            break;
                        case "VIDEO_FRAME":
                            handleVideoFrame(username, content);
                            break;
                        case "AUDIO_STATUS":
                            handleAudioStatus(username, content);
                            break;
                        case "AUDIO_CONTROL":
                            handleAudioControl(username, content);
                            break;
                        default:
                            System.out.println("Unhandled message type: " + type);
                    }
                }
            });
        } else {
            System.err.println("Invalid WebSocket message format: " + message);
        }
    }

    private static void handleDeviceList(String content) {
        String[] devices = content.split(";");
        connectedDevices.clear();

        for (String deviceStr : devices) {
            String[] parts = deviceStr.split(",");
            if (parts.length >= 5) {
                String deviceUsername = parts[0];
                String devId = parts[1];
                String ip = parts[2];
                String devType = parts[3];
                long connectTime = Long.parseLong(parts[4]);

                DeviceInfo info = new DeviceInfo(devId, deviceUsername, deviceUsername, ip);
                info.deviceType = devType;
                info.lastSeen = connectTime;
                connectedDevices.put(devId, info);
            }
        }

        System.out.println("Updated device list: " + connectedDevices.size() + " devices connected");

        if (connectionStatusListener != null) {
            Platform.runLater(() -> {
                connectionStatusListener.onDeviceListChanged(new HashMap<>(connectedDevices));
            });
        }
    }

    private static void handleDeviceConnected(String username, String deviceInfo) {
        String[] parts = deviceInfo.split("\\|");
        if (parts.length >= 2) {
            String deviceNameFromMsg = parts[0];
            String deviceIdFromMsg = parts[1];

            DeviceInfo info = new DeviceInfo(deviceIdFromMsg, deviceNameFromMsg, username, "unknown");
            connectedDevices.put(deviceIdFromMsg, info);

            System.out.println("Device connected: " + deviceNameFromMsg + " (" + deviceIdFromMsg + ") as user: " + username);
            addSystemMessage("Device connected: " + deviceNameFromMsg + " (" + username + ")");

            if (connectionStatusListener != null) {
                Platform.runLater(() -> {
                    connectionStatusListener.onDeviceListChanged(new HashMap<>(connectedDevices));
                });
            }
        }
    }

    private static void handleDeviceDisconnected(String username, String deviceInfo) {
        String[] parts = deviceInfo.split("\\|");
        if (parts.length >= 2) {
            String deviceNameFromMsg = parts[0];
            String deviceIdFromMsg = parts[1];

            connectedDevices.remove(deviceIdFromMsg);

            System.out.println("Device disconnected: " + deviceNameFromMsg + " (" + deviceIdFromMsg + ")");
            addSystemMessage("Device disconnected: " + deviceNameFromMsg);

            if (connectionStatusListener != null) {
                Platform.runLater(() -> {
                    connectionStatusListener.onDeviceListChanged(new HashMap<>(connectedDevices));
                });
            }
        }
    }

    private static void handleUserJoined(String username, String meetingId, String content) {
        addParticipantToMeeting(meetingId, username);
        addSystemMessage(username + " joined the meeting");

        // Request device list update
        if (webSocketClient != null && webSocketClient.isConnected()) {
            webSocketClient.send("GET_DEVICE_LIST");
        }
    }

    private static void handleUserLeft(String username, String meetingId, String content) {
        removeParticipantFromMeeting(meetingId, username);
        addSystemMessage(username + " left the meeting");

        // Remove from connected devices if this was a device
        String deviceIdToRemove = null;
        for (Map.Entry<String, DeviceInfo> entry : connectedDevices.entrySet()) {
            if (username.equals(entry.getValue().username)) {
                deviceIdToRemove = entry.getKey();
                break;
            }
        }
        if (deviceIdToRemove != null) {
            connectedDevices.remove(deviceIdToRemove);
        }

        if (connectionStatusListener != null) {
            Platform.runLater(() -> {
                connectionStatusListener.onDeviceListChanged(new HashMap<>(connectedDevices));
            });
        }
    }

    private static void handleAudioStatus(String username, String content) {
        if (content.contains("muted")) {
            addSystemMessage(username + " muted their audio");
        } else if (content.contains("unmuted")) {
            addSystemMessage(username + " unmuted their audio");
        } else if (content.contains("deafened")) {
            addSystemMessage(username + " deafened themselves");
        } else if (content.contains("undeafened")) {
            addSystemMessage(username + " undeafened themselves");
        }
    }

    private static void handleAudioControl(String username, String content) {
        if (isMeetingHost) {
            if (content.equals("MUTE_ALL")) {
                allMuted = true;
                addSystemMessage("Host muted all participants");
            } else if (content.equals("UNMUTE_ALL")) {
                allMuted = false;
                addSystemMessage("Host unmuted all participants");
            }
        }
    }

    public static void setActiveMeetingId(String meetingId) {
        activeMeetingId = meetingId;
        System.out.println("Active meeting set to: " + meetingId + " for device: " + deviceName);

        if (meetingId != null && webRTCEnabled && webRTCManager != null) {
            startWebRTCSession();
        }
    }

    public static String getActiveMeetingId() {
        return activeMeetingId;
    }

    public static void setMeetingHost(boolean host) {
        isMeetingHost = host;
        System.out.println("Meeting host status: " + (host ? "Host" : "Participant") + " for device: " + deviceName);

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

    public static String createNewMeeting() {
        ensureDeviceInfo(); // Ensure device info is set

        String meetingId = generateMeetingId();
        String hostName = getLoggedInUser();
        if (hostName == null || hostName.isEmpty()) {
            hostName = "Host";
        }

        System.out.println("CREATING NEW MEETING: " + meetingId + " by " + hostName + " on device: " + deviceName);

        MeetingInfo meetingInfo = new MeetingInfo(meetingId, hostName);
        activeMeetings.put(meetingId, meetingInfo);

        setActiveMeetingId(meetingId);
        setMeetingHost(true);

        Database.saveMeetingWithId(meetingId, hostName, "Meeting " + meetingId, "Auto-generated meeting");

        addParticipantToMeeting(meetingId, hostName);

        if (isWebSocketConnected()) {
            sendWebSocketMessage("MEETING_CREATED", meetingId, hostName,
                    "New meeting created by " + hostName + "|" + deviceId + "|" + deviceName);
            System.out.println("New meeting created via WebSocket: " + meetingId + " by " + hostName);
        } else {
            System.out.println("New meeting created locally: " + meetingId + " by " + hostName + " (WebSocket offline)");
        }

        if (webRTCEnabled && webRTCManager != null) {
            startWebRTCSession();
        }

        notifyControllersMeetingStateChanged(true);

        System.out.println("MEETING CREATION SUCCESS: " + meetingId + " - Ready for participants to join!");
        return meetingId;
    }

    public static boolean joinMeeting(String meetingId, String participantName) {
        ensureDeviceInfo(); // Ensure device info is set

        System.out.println("Attempting to join meeting: " + meetingId + " as " + participantName + " on device: " + deviceName);

        if (!meetingId.matches("\\d{6}")) {
            System.err.println("Invalid meeting ID format. Must be 6 digits: " + meetingId);
            return false;
        }

        boolean meetingExists = false;

        if (activeMeetings.containsKey(meetingId)) {
            System.out.println("Meeting found in active meetings: " + meetingId);
            meetingExists = true;
        }
        else if (Database.meetingExists(meetingId)) {
            System.out.println("Meeting found in database: " + meetingId);

            String host = Database.getMeetingHost(meetingId);
            if (host != null) {
                MeetingInfo meetingInfo = new MeetingInfo(meetingId, host);
                activeMeetings.put(meetingId, meetingInfo);
                meetingExists = true;
                System.out.println("Recreated meeting from database: " + meetingId);
            }
        }

        if (!meetingExists) {
            System.err.println("Meeting not found: " + meetingId);
            return false;
        }

        // Set the active meeting ID
        setActiveMeetingId(meetingId);

        // Determine if this user is the host
        MeetingInfo meetingInfo = activeMeetings.get(meetingId);
        boolean isHost = meetingInfo != null && participantName.equals(meetingInfo.getHost());
        setMeetingHost(isHost);

        // Add participant to meeting
        addParticipantToMeeting(meetingId, participantName);

        // Send WebSocket notification if connected
        if (isWebSocketConnected()) {
            String content = participantName + " joined the meeting|" + deviceId + "|" + deviceName;
            sendWebSocketMessage("USER_JOINED", meetingId, participantName, content);
            System.out.println("✅ Sent USER_JOINED via WebSocket for meeting: " + meetingId);

            // Also request current participants list
            if (webSocketClient != null) {
                webSocketClient.send("GET_DEVICE_LIST");
            }
        } else {
            System.out.println("⚠️ WebSocket not connected - meeting joined in offline mode");
        }

        // Start WebRTC if enabled
        if (webRTCEnabled && webRTCManager != null) {
            startWebRTCSession();
        }

        notifyControllersMeetingStateChanged(true);

        System.out.println("✅ JOIN SUCCESS: " + participantName + " joined meeting " + meetingId);
        System.out.println("Is host: " + isHost);
        System.out.println("Active participants: " + getMeetingParticipants(meetingId));

        return true;
    }

    // Add this method to validate meeting existence
    public static boolean isValidMeeting(String meetingId) {
        System.out.println("Validating meeting: " + meetingId);

        if (!meetingId.matches("\\d{6}")) {
            System.err.println("Invalid meeting ID format: " + meetingId);
            return false;
        }

        // Check in active meetings first
        if (activeMeetings.containsKey(meetingId)) {
            System.out.println("✅ Meeting found in active meetings: " + meetingId);
            return true;
        }

        // Check in database
        if (Database.meetingExists(meetingId)) {
            System.out.println("✅ Meeting found in database: " + meetingId);

            // Get host from database and recreate meeting info
            String host = Database.getMeetingHost(meetingId);
            if (host != null) {
                MeetingInfo meetingInfo = new MeetingInfo(meetingId, host);
                activeMeetings.put(meetingId, meetingInfo);
                System.out.println("Recreated meeting from database: " + meetingId);
                return true;
            }
        }

        System.err.println("❌ Meeting not found: " + meetingId);
        return false;
    }

    public static void createMeetingForTesting(String meetingId) {
        String hostName = getLoggedInUser();
        if (hostName == null || hostName.isEmpty()) {
            hostName = "TestHost";
        }

        MeetingInfo meetingInfo = new MeetingInfo(meetingId, hostName);
        activeMeetings.put(meetingId, meetingInfo);

        Database.saveMeetingWithId(meetingId, hostName, "Test Meeting " + meetingId, "Quick join test meeting");

        System.out.println("Test meeting created: " + meetingId + " by " + hostName);
    }

    public static MeetingInfo getMeetingInfo(String meetingId) {
        return activeMeetings.get(meetingId);
    }

    public static List<String> getActiveMeetingIds() {
        return new ArrayList<>(activeMeetings.keySet());
    }

    public static boolean isInMeeting() {
        return activeMeetingId != null;
    }

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

    public static boolean ensureWebSocketConnection() {
        if (!isWebSocketConnected() && loggedInUser != null) {
            System.out.println("Attempting to reconnect WebSocket for device: " + deviceName);
            initializeWebSocket(loggedInUser);
            return isWebSocketConnected();
        }
        return isWebSocketConnected();
    }

    public static void addParticipant(String name) {
        if (name != null && !activeParticipants.contains(name)) {
            activeParticipants.add(name);
            System.out.println("Participant added: " + name);
        }
    }

    public static List<String> getActiveParticipants() {
        return new ArrayList<>(activeParticipants);
    }

    public static void clearParticipants() {
        System.out.println("Cleared all participants");
        activeParticipants.clear();
    }

    public static void removeParticipant(String name) {
        if (activeParticipants.remove(name)) {
            System.out.println("Participant removed: " + name);
        }
    }

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

    private static void createMeeting(String meetingId, String host) {
        MeetingInfo meetingInfo = new MeetingInfo(meetingId, host);
        activeMeetings.put(meetingId, meetingInfo);
        System.out.println("Meeting registered: " + meetingId + " hosted by " + host);
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
        System.out.println("Meeting validation for " + meetingId + ": " + status);
    }

    private static String generateMeetingId() {
        return String.valueOf((int) (Math.random() * 900000) + 100000);
    }

    public static Map<String, MeetingInfo> getActiveMeetings() {
        return new HashMap<>(activeMeetings);
    }

    public static boolean isWebSocketConnected() {
        return webSocketClient != null && webSocketClient.isConnected();
    }

    public static String getConnectionStatus() {
        if (isWebSocketConnected()) {
            return "Connected to " + getCurrentServerUrl().replace("ws://", "") + " [" + deviceName + "]";
        } else {
            return "Disconnected from " + getCurrentServerUrl().replace("ws://", "") + " [" + deviceName + "]";
        }
    }

    public static String getConnectionStatusShort() {
        if (isWebSocketConnected()) {
            return "Connected";
        } else {
            return "Disconnected";
        }
    }

    public static void sendWebSocketMessage(String type, String meetingId, String content) {
        ensureDeviceInfo(); // Ensure device info is set

        if (webSocketClient != null && webSocketClient.isConnected() && loggedInUser != null) {
            // Include device info in content if needed
            String enhancedContent = content;
            if (type.equals("VIDEO_STATUS") || type.equals("USER_JOINED") || type.equals("USER_LEFT")) {
                enhancedContent = content + "|" + deviceId + "|" + deviceName;
            }
            webSocketClient.sendMessage(type, meetingId, loggedInUser, enhancedContent);
        } else {
            System.err.println("Cannot send message - WebSocket not connected or user not logged in");
        }
    }

    public static void sendWebSocketMessage(String type, String meetingId, String username, String content) {
        ensureDeviceInfo(); // Ensure device info is set

        if (webSocketClient != null && webSocketClient.isConnected()) {
            System.out.println("=== SENDING WEBSOCKET MESSAGE FROM DEVICE: " + deviceName + " ===");
            System.out.println("Type: " + type);
            System.out.println("Meeting ID: " + meetingId);
            System.out.println("Username: " + username);
            System.out.println("Content: " + content);
            System.out.println("Device: " + deviceName + " (" + deviceId + ")");

            webSocketClient.sendMessage(type, meetingId, username, content);
            System.out.println("Message sent successfully");
        } else {
            System.err.println("Cannot send message - WebSocket not connected");
        }
    }

    public static List<Database.ServerConfig> getServerHistory() {
        if (loggedInUser != null) {
            return Database.getServerHistory(loggedInUser);
        }
        return new ArrayList<>();
    }

    public static void saveUserPreference(String key, String value) {
        if (loggedInUser != null) {
            Database.saveUserPreference(loggedInUser, key, value);
        }
    }

    public static String getUserPreference(String key) {
        if (loggedInUser != null) {
            return Database.getUserPreference(loggedInUser, key);
        }
        return null;
    }

    public static void toggleAudio() {
        ensureDeviceInfo(); // Ensure device info is set

        audioMuted = !audioMuted;
        updateAudioButtonStyles();

        // Update device info
        DeviceInfo deviceInfo = connectedDevices.get(deviceId);
        if (deviceInfo != null) {
            deviceInfo.isAudioMuted = audioMuted;
            deviceInfo.updateLastSeen();
        }

        if (audioMuted) {
            addSystemMessage("You muted your audio");
            if (isWebSocketConnected() && getActiveMeetingId() != null) {
                String content = "muted their audio|" + deviceId + "|" + deviceName;
                sendWebSocketMessage("AUDIO_STATUS", getActiveMeetingId(), loggedInUser, content);
            }
        } else {
            addSystemMessage("You unmuted your audio");
            if (isWebSocketConnected() && getActiveMeetingId() != null) {
                String content = "unmuted their audio|" + deviceId + "|" + deviceName;
                sendWebSocketMessage("AUDIO_STATUS", getActiveMeetingId(), loggedInUser, content);
            }
        }

        if (audioControlsController != null) {
            Platform.runLater(() -> {
                audioControlsController.syncWithGlobalState();
            });
        }

        if (connectionStatusListener != null) {
            Platform.runLater(() -> {
                connectionStatusListener.onDeviceListChanged(new HashMap<>(connectedDevices));
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
                sendWebSocketMessage("AUDIO_CONTROL", getActiveMeetingId(), loggedInUser, "MUTE_ALL");
            }
        } else {
            addSystemMessage("You unmuted all participants");
            if (isWebSocketConnected() && getActiveMeetingId() != null) {
                sendWebSocketMessage("AUDIO_CONTROL", getActiveMeetingId(), loggedInUser, "UNMUTE_ALL");
            }
        }

        if (audioControlsController != null) {
            Platform.runLater(() -> {
                audioControlsController.syncWithGlobalState();
            });
        }
    }

    public static void toggleDeafen() {
        ensureDeviceInfo(); // Ensure device info is set

        isDeafened = !isDeafened;

        if (isDeafened) {
            addSystemMessage("You deafened yourself");
            if (isWebSocketConnected() && getActiveMeetingId() != null) {
                String content = "deafened themselves|" + deviceId + "|" + deviceName;
                sendWebSocketMessage("AUDIO_STATUS", getActiveMeetingId(), loggedInUser, content);
            }
        } else {
            addSystemMessage("You undeafened yourself");
            if (isWebSocketConnected() && getActiveMeetingId() != null) {
                String content = "undeafened themselves|" + deviceId + "|" + deviceName;
                sendWebSocketMessage("AUDIO_STATUS", getActiveMeetingId(), loggedInUser, content);
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
        System.out.println("Audio controls controller registered");
    }

    public static AudioControlsController getAudioControlsController() {
        return audioControlsController;
    }

    public static void toggleRecording() {
        ensureDeviceInfo(); // Ensure device info is set

        isRecording = !isRecording;

        if (isRecording) {
            addSystemMessage("You started recording");
            if (isWebSocketConnected() && getActiveMeetingId() != null) {
                sendWebSocketMessage("VIDEO_CONTROL", getActiveMeetingId(), loggedInUser, "START_RECORDING");
            }
        } else {
            addSystemMessage("You stopped recording");
            if (isWebSocketConnected() && getActiveMeetingId() != null) {
                sendWebSocketMessage("VIDEO_CONTROL", getActiveMeetingId(), loggedInUser, "STOP_RECORDING");
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
        System.out.println("Video controls controller registered");
    }

    public static VideoControlsController getVideoControlsController() {
        return videoControlsController;
    }

    public static void addSystemMessage(String message) {
        System.out.println("System [" + deviceName + "]: " + message);

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

        public static void showNetworkInfo() {
            List<String> localIPs = HelloApplication.getLocalIPAddresses();
            System.out.println("NETWORK CONNECTION GUIDE:");
            System.out.println("=================================");

            if (localIPs.isEmpty()) {
                System.out.println("No network interfaces found!");
                System.out.println("Make sure you're connected to WiFi/Ethernet");
            } else {
                System.out.println("Your computer's IP addresses:");
                for (String ip : localIPs) {
                    System.out.println(ip + ":8887");
                }
                System.out.println("\nOther devices should use:");
                for (String ip : localIPs) {
                    System.out.println("ws://" + ip + ":8887");
                }
            }

            System.out.println("\nTROUBLESHOOTING:");
            System.out.println("1. Make sure all devices are on same WiFi");
            System.out.println("2. Turn off VPN if using one");
            System.out.println("3. Check firewall settings");
            System.out.println("4. Try different IP addresses from the list above");
            System.out.println("5. Ensure Node.js server is running on the host computer");
            System.out.println("6. Run: node server/server.js");

            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Network Information");
                alert.setHeaderText("Your Network Connection Details");

                StringBuilder message = new StringBuilder();
                if (localIPs.isEmpty()) {
                    message.append("No network interfaces found!\n\n");
                    message.append("Make sure you're connected to WiFi/Ethernet");
                } else {
                    message.append("Your computer's IP addresses:\n");
                    for (String ip : localIPs) {
                        message.append(ip).append(":8887\n");
                    }
                    message.append("\nOther devices should connect to:\n");
                    for (String ip : localIPs) {
                        message.append("ws://").append(ip).append(":8887\n");
                    }
                }

                message.append("\nTroubleshooting:\n");
                message.append("• Make sure all devices are on same WiFi\n");
                message.append("• Turn off VPN if using one\n");
                message.append("• Check firewall settings\n");
                message.append("• Ensure Node.js server is running on host computer\n");
                message.append("• Run: node server/server.js");

                alert.setContentText(message.toString());
                alert.showAndWait();
            });
        }

        public static boolean connectToServer(String serverUrl) {
            try {
                System.out.println("Attempting to connect to: " + serverUrl);

                initializeWebSocketConnection(serverUrl);

                Thread.sleep(1000);

                SimpleWebSocketClient client = getWebSocketClient();
                if (client != null && client.isConnected()) {
                    System.out.println("Successfully connected to: " + serverUrl);
                    return true;
                } else {
                    System.out.println("Failed to connect to: " + serverUrl);
                    return false;
                }
            } catch (Exception e) {
                System.err.println("Connection error: " + e.getMessage());
                return false;
            }
        }

        public static void handleLocalhostFailure() {
            Platform.runLater(() -> {
                List<String> localIPs = HelloApplication.getLocalIPAddresses();

                Alert alert = new Alert(Alert.AlertType.WARNING);
                alert.setTitle("Localhost Connection Failed");
                alert.setHeaderText("Cannot connect to localhost:8887");

                StringBuilder content = new StringBuilder();
                content.append("The Node.js WebSocket server is not running on this device.\n\n");

                if (!localIPs.isEmpty()) {
                    content.append("If you're trying to connect to another computer:\n");
                    for (String ip : localIPs) {
                        content.append("Try connecting to: ").append(ip).append(":8887\n");
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

        public static void initializeWebSocketConnection(String serverUrl) {
            try {
                System.out.println("Initializing WebSocket connection to: " + serverUrl + " for device: " + deviceName);

                if (webSocketClient != null) {
                    webSocketClient.disconnect();
                    webSocketClient = null;
                }

                webSocketClient = new SimpleWebSocketClient(serverUrl, HelloApplication::handleWebSocketMessage);

                if (loggedInUser != null) {
                    webSocketClient.setCurrentUser(loggedInUser);
                }

                webSocketClient.setConnectionListener(new SimpleWebSocketClient.ConnectionListener() {
                    @Override
                    public void onConnected() {
                        System.out.println("WebSocket connected to: " + serverUrl + " for device: " + deviceName);
                        connectionInitialized = true;

                        String urlWithoutProtocol = serverUrl.replace("ws://", "");
                        String[] parts = urlWithoutProtocol.split(":");
                        if (parts.length >= 2) {
                            serverIp = parts[0];
                            serverPort = parts[1];

                            if (loggedInUser != null) {
                                Database.saveServerConfig(loggedInUser, serverIp, serverPort);
                            }
                        }

                        // Send device info immediately after connecting
                        sendDeviceInfo();

                        if (connectionStatusListener != null) {
                            Platform.runLater(() -> {
                                connectionStatusListener.onConnectionStatusChanged(true, "Connected to " + serverUrl);
                                connectionStatusListener.onDeviceListChanged(new HashMap<>(connectedDevices));
                            });
                        }
                    }

                    @Override
                    public void onDisconnected() {
                        System.out.println("WebSocket disconnected from: " + serverUrl + " for device: " + deviceName);
                        connectionInitialized = false;

                        if (connectionStatusListener != null) {
                            Platform.runLater(() -> {
                                connectionStatusListener.onConnectionStatusChanged(false, "Disconnected");
                            });
                        }
                    }

                    @Override
                    public void onError(String error) {
                        System.err.println("WebSocket error for device " + deviceName + ": " + error);
                        connectionInitialized = false;

                        if (connectionStatusListener != null) {
                            Platform.runLater(() -> {
                                connectionStatusListener.onConnectionStatusChanged(false, "Error: " + error);
                            });
                        }
                    }
                });

                webSocketClient.connect();

            } catch (Exception e) {
                System.err.println("Failed to initialize WebSocket connection: " + e.getMessage());
                connectionInitialized = false;

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