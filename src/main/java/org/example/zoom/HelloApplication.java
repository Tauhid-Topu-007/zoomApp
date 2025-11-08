package org.example.zoom;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.zoom.websocket.SimpleWebSocketClient;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.scene.control.ChoiceDialog;
import javafx.scene.control.TextInputDialog;

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

    // Meeting management - ENHANCED with proper meeting storage
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
            boolean previousConnectedState = isWebSocketConnected();

            while (connectionInitialized && loggedInUser != null) {
                try {
                    Thread.sleep(5000); // Check every 5 seconds

                    boolean currentConnectedState = isWebSocketConnected();

                    // Only notify if connection state actually changed
                    if (currentConnectedState != previousConnectedState) {
                        String status = currentConnectedState ? "🟢 Connected" : "🔴 Disconnected";

                        // Notify listener if connection status changed
                        if (connectionStatusListener != null) {
                            Platform.runLater(() -> {
                                connectionStatusListener.onConnectionStatusChanged(currentConnectedState, status);
                            });
                        }

                        previousConnectedState = currentConnectedState;
                    }

                    // Attempt reconnection if disconnected
                    if (!currentConnectedState && webSocketClient != null) {
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

            // Auto-trigger network discovery on connection failure
            if (loggedInUser != null && primaryStage != null) {
                showConnectionErrorAndDiscover(attemptedUrl);
            }
        });
    }

    // NEW: Show connection error and trigger network discovery
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

    // NEW: Manual connection dialog
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

    // NEW: Manual connection method
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

        // Test connection first
        showAlert(Alert.AlertType.INFORMATION, "Connecting", "Attempting to connect to: " + serverUrl);
        initializeWebSocketWithUrl(serverUrl);
    }

    // NEW: IP validation method
    public static boolean isValidIPAddress(String ip) {
        String ipPattern = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
        return ip.matches(ipPattern);
    }

    // NEW: Enhanced network discovery and connection method
    public static void discoverAndConnectToServer() {
        Platform.runLater(() -> {
            // Show searching dialog
            Alert searchingAlert = new Alert(Alert.AlertType.INFORMATION);
            searchingAlert.setTitle("Network Discovery");
            searchingAlert.setHeaderText("Searching for available servers...");
            searchingAlert.setContentText("Please wait while we scan your network for Zoom servers.");
            searchingAlert.show();

            // Run discovery in background thread
            new Thread(() -> {
                List<String> availableServers = discoverAvailableServers();
                List<String> localIPs = getLocalIPAddresses();

                Platform.runLater(() -> {
                    searchingAlert.close();

                    if (!availableServers.isEmpty()) {
                        // Create display names with descriptions but store clean URLs
                        List<String> displayNames = new ArrayList<>();
                        Map<String, String> serverMap = new HashMap<>(); // Display name -> Clean URL

                        for (String server : availableServers) {
                            String cleanUrl = "ws://" + server;
                            String displayName = server;
                            if (localIPs.contains(server.split(":")[0])) {
                                displayName = server + " (Your Computer)";
                            }
                            displayNames.add(displayName);
                            serverMap.put(displayName, cleanUrl);
                        }

                        // Let user choose from available servers
                        ChoiceDialog<String> dialog = new ChoiceDialog<>(displayNames.get(0), displayNames);
                        dialog.setTitle("Network Discovery");
                        dialog.setHeaderText("Available Servers Found");
                        dialog.setContentText("Choose a server to connect:");

                        Optional<String> result = dialog.showAndWait();
                        result.ifPresent(selectedDisplayName -> {
                            String cleanUrl = serverMap.get(selectedDisplayName);
                            // Extract IP and port from clean URL
                            String urlWithoutProtocol = cleanUrl.replace("ws://", "");
                            String[] parts = urlWithoutProtocol.split(":");
                            if (parts.length >= 2) {
                                String ip = parts[0];
                                String port = parts[1];
                                setServerConfig(ip, port);

                                showAlert(Alert.AlertType.INFORMATION, "Connecting",
                                        "Connecting to: " + cleanUrl);
                            }
                        });
                    } else {
                        Alert noServersAlert = new Alert(Alert.AlertType.WARNING);
                        noServersAlert.setTitle("No Servers Found");
                        noServersAlert.setHeaderText("No available servers found");
                        noServersAlert.setContentText("Please check:\n" +
                                "1. Server is running on host machine\n" +
                                "2. Both devices are on same network\n" +
                                "3. Firewall allows port 8887\n" +
                                "4. Try manual configuration");

                        noServersAlert.showAndWait();

                        // Show manual configuration dialog
                        showManualConnectionDialog();
                    }
                });
            }).start();
        });
    }

    // NEW: Show connection guide
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

    // NEW: Alert helper method
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

    // NEW: Enhanced network discovery method
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
                for (int i = 1; i <= 50; i++) { // Test first 50 IPs for broader discovery
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
                thread.join(1500); // Wait max 1.5 seconds per thread
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        availableServers.addAll(discoveredServers);
        System.out.println("🎯 Discovery complete. Found " + availableServers.size() + " servers: " + availableServers);

        return availableServers;
    }

    // NEW: Test connection to a specific server with better timeout handling
    public static boolean testConnection(String serverUrl) {
        try {
            final boolean[] connectionSuccess = {false};
            final Object lock = new Object();

            SimpleWebSocketClient testClient = new SimpleWebSocketClient(serverUrl, message -> {
                System.out.println("🔗 Test connection received: " + message);
                if (message.contains("Connected") || message.contains("Welcome") || message.contains("SYSTEM")) {
                    synchronized (lock) {
                        connectionSuccess[0] = true;
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

            testClient.disconnect();
            return connectionSuccess[0];

        } catch (Exception e) {
            System.err.println("❌ Connection test failed for " + serverUrl + ": " + e.getMessage());
            return false;
        }
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

    // ==================== MEETING MANAGEMENT - ENHANCED CONNECTION ====================

    /**
     * Set the active meeting ID and store it globally for JoinController access
     */
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
     * Leave current meeting
     */
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

    /**
     * End a meeting (host only)
     */
    public static void endMeeting(String meetingId) {
        if (activeMeetings.containsKey(meetingId)) {
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
                toggleRecording(); // Stop recording when meeting ends
            }

            // Notify controllers about meeting state change
            notifyControllersMeetingStateChanged(false);
        }
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