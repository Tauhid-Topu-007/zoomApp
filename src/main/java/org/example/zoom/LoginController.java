package org.example.zoom;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.application.Platform;
import org.example.zoom.websocket.SimpleWebSocketClient;
import java.util.UUID;
import java.util.Optional;

public class LoginController {
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label messageLabel;
    @FXML
    private Label connectionStatusLabel;
    @FXML
    private Label serverInfoLabel;
    @FXML
    private Label deviceInfoLabel;
    @FXML
    private CheckBox rememberMe;
    @FXML
    private ScrollPane mainScrollPane;
    @FXML
    private Button loginButton;
    @FXML
    private Button registerButton;
    @FXML
    private Button forgotPasswordButton;
    @FXML
    private Button serverConfigButton;
    @FXML
    private Button testConnectionButton;
    @FXML
    private Button showDevicesButton;
    @FXML
    private ListView<String> connectedDevicesList;
    @FXML
    private VBox loginForm;

    private SimpleWebSocketClient testClient;
    private boolean autoLoginInProgress = false;
    private Thread connectionMonitorThread;
    private volatile boolean stopMonitor = false;

    @FXML
    public void initialize() {
        System.out.println("🎯 LoginController initialized for device: " + System.getProperty("device.name", "Unknown"));

        // Configure scroll pane
        if (mainScrollPane != null) {
            mainScrollPane.setFitToWidth(true);
            mainScrollPane.setFitToHeight(true);
            mainScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            mainScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            mainScrollPane.setStyle("-fx-background: #2c3e50; -fx-border-color: #2c3e50;");
        }

        // Configure devices list
        if (connectedDevicesList != null) {
            connectedDevicesList.setVisible(false);
            connectedDevicesList.setManaged(false);
            connectedDevicesList.setStyle("-fx-background-color: #34495e; -fx-text-fill: white;");

            // Add double-click handler to show device details
            connectedDevicesList.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    String selected = connectedDevicesList.getSelectionModel().getSelectedItem();
                    if (selected != null && !selected.startsWith("📡") && !selected.startsWith("\n")) {
                        showDeviceDetails(selected);
                    }
                }
            });
        }

        // Update connection status when the login screen loads
        updateConnectionStatus();
        updateDeviceInfo();

        // Load saved username if remember me was checked
        loadSavedCredentials();

        // Clear any previous error messages
        clearErrorMessage();

        // Check for auto-login parameter
        checkForAutoLogin();

        // Set up enter key handlers
        setupEnterKeyHandlers();

        // Apply initial styles
        applyInitialStyles();

        // Start periodic connection check
        startConnectionMonitor();

        // Register with HelloApplication for connection status updates
        HelloApplication.setConnectionStatusListener(new HelloApplication.ConnectionStatusListener() {
            @Override
            public void onConnectionStatusChanged(boolean connected, String status) {
                Platform.runLater(() -> {
                    updateConnectionStatus();
                });
            }

            @Override
            public void onDeviceListChanged(java.util.Map<String, HelloApplication.DeviceInfo> devices) {
                Platform.runLater(() -> {
                    if (connectedDevicesList != null && connectedDevicesList.isVisible()) {
                        updateDeviceList(devices);
                    }
                });
            }

            @Override
            public void onMeetingListChanged(java.util.Map<String, HelloApplication.MeetingInfo> meetings) {
                // Login screen doesn't need to show meetings, but we must implement the method
                System.out.println("📅 Meeting list updated on login screen: " + meetings.size() + " meetings available");
                // Optionally update a label or status if you want to show meeting count
                Platform.runLater(() -> {
                    if (meetings.size() > 0 && messageLabel != null) {
                        // You could optionally show a message that meetings are available
                        // but don't override error/success messages
                        if (!messageLabel.getText().contains("❌") && !messageLabel.getText().contains("✅")) {
                            messageLabel.setText("📅 " + meetings.size() + " meeting(s) available");
                            messageLabel.setStyle("-fx-text-fill: #f39c12; -fx-font-size: 12px;");
                        }
                    }
                });
            }
        });
    }

    private void setupEnterKeyHandlers() {
        if (usernameField != null) {
            usernameField.setOnAction(this::onUsernameEnter);
        }
        if (passwordField != null) {
            passwordField.setOnAction(this::onPasswordEnter);
        }
    }

    private void applyInitialStyles() {
        String fieldStyle = "-fx-background-color: #34495e; -fx-text-fill: white; -fx-border-color: #7f8c8d; -fx-border-radius: 5; -fx-padding: 10;";
        String buttonStyle = "-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold; -fx-border-radius: 5; -fx-padding: 10 20;";

        if (usernameField != null) usernameField.setStyle(fieldStyle);
        if (passwordField != null) passwordField.setStyle(fieldStyle);
        if (loginButton != null) loginButton.setStyle(buttonStyle);
        if (registerButton != null) registerButton.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold; -fx-border-radius: 5; -fx-padding: 10 20;");
        if (showDevicesButton != null) showDevicesButton.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-font-weight: bold; -fx-border-radius: 5; -fx-padding: 8 15;");
    }

    private void updateDeviceInfo() {
        if (deviceInfoLabel != null) {
            String deviceName = System.getProperty("device.name", "Unknown");
            String deviceId = System.getProperty("device.id", "N/A");
            String shortId = deviceId.length() > 8 ? deviceId.substring(0, 8) : deviceId;
            deviceInfoLabel.setText("📱 " + deviceName + " [ID: " + shortId + "]");
            deviceInfoLabel.setStyle("-fx-text-fill: #3498db; -fx-font-size: 12px; -fx-font-weight: bold;");
        }
    }

    private void startConnectionMonitor() {
        stopMonitor = false;
        connectionMonitorThread = new Thread(() -> {
            while (!stopMonitor) {
                try {
                    Thread.sleep(10000);
                    Platform.runLater(() -> {
                        updateConnectionStatus();
                        if (HelloApplication.isWebSocketConnected() && showDevicesButton != null) {
                            // Request device list from server
                            SimpleWebSocketClient client = HelloApplication.getWebSocketClient();
                            if (client != null && client.isConnected()) {
                                client.send("GET_DEVICE_LIST");
                            }

                            // Refresh devices list if visible
                            if (connectedDevicesList != null && connectedDevicesList.isVisible()) {
                                refreshConnectedDevices();
                            }
                        }
                    });
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        connectionMonitorThread.setDaemon(true);
        connectionMonitorThread.start();
    }

    private void updateDeviceList(java.util.Map<String, HelloApplication.DeviceInfo> devices) {
        if (connectedDevicesList == null) return;

        connectedDevicesList.getItems().clear();

        if (devices.isEmpty()) {
            connectedDevicesList.getItems().add("📡 No other devices connected");
            return;
        }

        // Add header
        connectedDevicesList.getItems().add("=== Connected Devices (" + devices.size() + ") ===");

        // Add each device
        for (HelloApplication.DeviceInfo device : devices.values()) {
            String status = device.isVideoOn ? "🎥" : "🎤";
            String audioStatus = device.isAudioMuted ? "🔇" : "🔊";
            String deviceEntry = String.format("  %s %s %s - %s (%s)",
                    status, audioStatus, device.deviceName, device.username, device.ipAddress);
            connectedDevicesList.getItems().add(deviceEntry);
        }

        connectedDevicesList.getItems().add("\nTotal: " + devices.size() + " device(s) connected");
    }

    private void showDeviceDetails(String deviceInfo) {
        // Parse device info and show details dialog
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Device Details");
        alert.setHeaderText("Device Information");
        alert.setContentText("Selected device: " + deviceInfo);
        alert.showAndWait();
    }

    @FXML
    protected void onQuickNetworkClick(ActionEvent event) {
        System.out.println("🌐 Quick network connection");

        // Show a prompt to enter network IP
        TextInputDialog dialog = new TextInputDialog("192.168.1.");
        dialog.setTitle("Network Server");
        dialog.setHeaderText("Enter Network Server IP");
        dialog.setContentText("IP Address:");
        dialog.getDialogPane().setStyle("-fx-background-color: #2c3e50;");

        dialog.showAndWait().ifPresent(ip -> {
            if (!ip.isEmpty() && !ip.equals("192.168.1.")) {
                HelloApplication.setServerConfig(ip, "8887");
                updateConnectionStatus();
                showSuccessMessage("✅ Network configuration set: " + ip);

                // Test the connection
                onTestConnectionClick(event);
            } else {
                showErrorMessage("❌ Please enter a valid IP address!");
            }
        });
    }

    @FXML
    protected void onQuickLocalhostClick(ActionEvent event) {
        System.out.println("🔗 Quick localhost connection");

        // Quick connect to localhost
        HelloApplication.setServerConfig("localhost", "8887");
        updateConnectionStatus();
        showSuccessMessage("✅ Localhost configuration set!");

        // Test the connection
        onTestConnectionClick(event);
    }

    @FXML
    protected void onShowDevicesClick(ActionEvent event) {
        if (connectedDevicesList == null) return;

        boolean isVisible = connectedDevicesList.isVisible();
        connectedDevicesList.setVisible(!isVisible);
        connectedDevicesList.setManaged(!isVisible);

        if (!isVisible) {
            refreshConnectedDevices();
            showDevicesButton.setText("Hide Devices");
            showDevicesButton.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-weight: bold; -fx-border-radius: 5; -fx-padding: 8 15;");
        } else {
            showDevicesButton.setText("Show Connected Devices");
            showDevicesButton.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-font-weight: bold; -fx-border-radius: 5; -fx-padding: 8 15;");
        }
    }

    private void refreshConnectedDevices() {
        if (connectedDevicesList == null) return;

        connectedDevicesList.getItems().clear();
        connectedDevicesList.getItems().add("📡 Fetching connected devices...");

        // Request device list from server
        SimpleWebSocketClient client = HelloApplication.getWebSocketClient();
        if (client != null && client.isConnected()) {
            client.send("GET_DEVICE_LIST");

            // Also get from HelloApplication's cache
            java.util.Map<String, HelloApplication.DeviceInfo> devices = HelloApplication.getConnectedDevices();
            if (!devices.isEmpty()) {
                updateDeviceList(devices);
            }
        } else {
            // Fallback to simulation if not connected
            simulateDeviceList();
        }
    }

    private void simulateDeviceList() {
        new Thread(() -> {
            try {
                Thread.sleep(1000);
                Platform.runLater(() -> {
                    connectedDevicesList.getItems().clear();

                    // Add header
                    connectedDevicesList.getItems().add("=== Connected Devices (Simulated) ===");

                    // Add current device
                    String currentDevice = System.getProperty("device.name", "This Device");
                    String currentId = System.getProperty("device.id", "N/A").substring(0, 8);
                    connectedDevicesList.getItems().add("  ▶ " + currentDevice + " (You) - ID: " + currentId);

                    // Check if host is running
                    if (HelloApplication.testConnection(HelloApplication.getCurrentServerUrl())) {
                        connectedDevicesList.getItems().add("  ○ Host-Device-1 (Host)");
                        connectedDevicesList.getItems().add("  ○ Client-Device-2");
                        connectedDevicesList.getItems().add("  ○ Client-Device-3");
                        connectedDevicesList.getItems().add("\nTotal: 4 devices connected (including you)");
                    } else {
                        connectedDevicesList.getItems().add("\n⚠️ Server not reachable");
                        connectedDevicesList.getItems().add("Connect to server to see real devices");
                    }
                });
            } catch (InterruptedException e) {
                // Ignore
            }
        }).start();
    }

    public void simulateAutoLogin(String username) {
        System.out.println("🎯 Auto-login triggered for: " + username + " on device: " +
                System.getProperty("device.name", "Unknown"));
        autoLoginInProgress = true;

        Platform.runLater(() -> {
            if (usernameField != null) {
                usernameField.setText(username);
            }
            if (passwordField != null) {
                passwordField.setText("password123");
            }
            if (rememberMe != null) {
                rememberMe.setSelected(true);
            }

            showSuccessMessage("🔄 Auto-login in progress...");

            new Thread(() -> {
                try {
                    Thread.sleep(1500);
                    Platform.runLater(() -> {
                        if (loginButton != null && !loginButton.isDisabled()) {
                            System.out.println("🎯 Firing login button for auto-login");
                            loginButton.fire();
                        } else {
                            System.out.println("🎯 Calling login directly for auto-login");
                            onLoginClick(new ActionEvent());
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    autoLoginInProgress = false;
                }
            }).start();
        });
    }

    private void checkForAutoLogin() {
        String deviceName = System.getProperty("device.name");
        if (deviceName != null && !deviceName.isEmpty()) {
            System.out.println("🔍 Auto-login detected for device: " + deviceName);
            String username = getAutoLoginUsername(deviceName);

            Platform.runLater(() -> {
                new Thread(() -> {
                    try {
                        Thread.sleep(2000);
                        Platform.runLater(() -> {
                            System.out.println("🚀 Starting auto-login for: " + username);
                            simulateAutoLogin(username);
                        });
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                }).start();
            });
        } else {
            System.out.println("🔍 No auto-login configuration detected");
        }
    }

    private String getAutoLoginUsername(String deviceName) {
        if (deviceName == null) return "test_user";

        switch (deviceName.toLowerCase()) {
            case "host-device-1":
                return "HostUser";
            case "client-device-2":
                return "ClientUser2";
            case "client-device-3":
                return "ClientUser3";
            case "client-device-4":
                return "ClientUser4";
            default:
                return "User" + deviceName.replaceAll("[^0-9]", "");
        }
    }

    @FXML
    protected void onLoginClick(ActionEvent event) {
        System.out.println("🔐 Login button clicked on device: " + System.getProperty("device.name", "Unknown"));

        if (autoLoginInProgress) {
            showSuccessMessage("🔄 Auto-login in progress...");
        }

        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        clearErrorMessage();

        if (username.isEmpty() || password.isEmpty()) {
            showErrorMessage("❌ Please enter both username and password!");
            shakeLoginForm();
            return;
        }

        if (username.length() < 3) {
            showErrorMessage("❌ Username must be at least 3 characters!");
            shakeLoginForm();
            return;
        }

        if (password.length() < 3) {
            showErrorMessage("❌ Password must be at least 3 characters!");
            shakeLoginForm();
            return;
        }

        setLoadingState(true);

        if (autoLoginInProgress) {
            messageLabel.setText("🔄 Auto-login in progress...");
        } else {
            messageLabel.setText("🔄 Authenticating...");
        }

        new Thread(() -> {
            try {
                boolean isAuthenticated = Database.authenticateUser(username, password);
                System.out.println("🔐 Authentication result for " + username + ": " + isAuthenticated);

                Platform.runLater(() -> {
                    setLoadingState(false);

                    if (isAuthenticated) {
                        handleSuccessfulLogin(username, password);
                    } else {
                        handleFailedLogin();
                    }
                });
            } catch (Exception e) {
                System.err.println("❌ Authentication error: " + e.getMessage());
                Platform.runLater(() -> {
                    setLoadingState(false);
                    showErrorMessage("❌ Authentication error: " + e.getMessage());
                    autoLoginInProgress = false;
                });
            }
        }).start();
    }

    private void handleSuccessfulLogin(String username, String password) {
        System.out.println("✅ Login successful for: " + username);

        if (rememberMe.isSelected()) {
            saveCredentials(username, password);
        } else {
            clearSavedCredentials();
        }

        if (autoLoginInProgress) {
            showSuccessMessage("✅ Auto-login successful! Connecting...");
        } else {
            showSuccessMessage("✅ Login successful! Connecting to server...");
        }

        try {
            initializeWebSocketConnection(username);
        } catch (Exception e) {
            System.err.println("❌ Login process error: " + e.getMessage());
            e.printStackTrace();
            showErrorMessage("❌ Login error: " + e.getMessage());
            autoLoginInProgress = false;
        }
    }

    private void initializeWebSocketConnection(String username) {
        System.out.println("Initializing WebSocket connection for: " + username);

        new Thread(() -> {
            try {
                String serverUrl = HelloApplication.getCurrentServerUrl();
                String deviceId = System.getProperty("device.id", UUID.randomUUID().toString());
                String deviceName = System.getProperty("device.name", "Unknown");

                System.out.println("Connecting to: " + serverUrl + " as device: " + deviceName);

                final SimpleWebSocketClient[] clientHolder = new SimpleWebSocketClient[1];

                clientHolder[0] = new SimpleWebSocketClient(serverUrl, message -> {
                    System.out.println("WebSocket message during login: " + message);

                    Platform.runLater(() -> {
                        if (message.contains("Connected") || message.contains("Welcome") || message.contains("WELCOME")) {
                            System.out.println("WebSocket connection confirmed for device: " + deviceName);
                            if (autoLoginInProgress) {
                                showSuccessMessage("Connected! Redirecting...");
                            } else {
                                showSuccessMessage("Connected to server! Redirecting...");
                            }

                            HelloApplication.setWebSocketClient(clientHolder[0]);
                            clientHolder[0].setCurrentUser(username);

                            // Send device info to server
                            clientHolder[0].send("DEVICE_INFO|global|" + username + "|" + deviceName + "|" + deviceId);

                        } else if (message.contains("ERROR") || message.contains("Failed") || message.contains("DISCONNECTED")) {
                            System.out.println("WebSocket connection issues for device: " + deviceName);
                            if (autoLoginInProgress) {
                                showErrorMessage("Server connection failed, continuing in offline mode");
                            } else {
                                showErrorMessage("Server connection failed, but you can continue in offline mode");
                            }
                        }
                    });
                });

                clientHolder[0].setConnectionListener(new SimpleWebSocketClient.ConnectionListener() {
                    @Override
                    public void onConnected() {
                        System.out.println("WebSocket connected successfully for device: " + deviceName);
                    }

                    @Override
                    public void onDisconnected() {
                        System.out.println("WebSocket disconnected for device: " + deviceName);
                        Platform.runLater(() -> {
                            updateConnectionStatus();
                            if (Boolean.parseBoolean(System.getProperty("auto.reconnect", "true"))) {
                                attemptReconnect(clientHolder[0], username, deviceName, deviceId);
                            }
                        });
                    }

                    @Override
                    public void onError(String error) {
                        System.err.println("WebSocket error for device " + deviceName + ": " + error);
                    }
                });

                // Add custom headers for device identification
                clientHolder[0].addHeader("Device-ID", deviceId);
                clientHolder[0].addHeader("Device-Name", deviceName);

                clientHolder[0].connect();

                Thread.sleep(2500);

                Platform.runLater(() -> {
                    boolean isConnected = clientHolder[0].isConnected();
                    System.out.println("WebSocket connection status for " + deviceName + ": " +
                            (isConnected ? "CONNECTED" : "DISCONNECTED"));

                    if (!isConnected) {
                        if (autoLoginInProgress) {
                            showErrorMessage("Cannot connect to server, continuing in offline mode");
                        } else {
                            showErrorMessage("Cannot connect to server, but you can continue in offline mode");
                        }
                    } else {
                        HelloApplication.setWebSocketClient(clientHolder[0]);
                        clientHolder[0].setCurrentUser(username);
                    }

                    completeLoginProcess(username);
                });

            } catch (Exception e) {
                System.err.println("WebSocket initialization error: " + e.getMessage());
                Platform.runLater(() -> {
                    if (autoLoginInProgress) {
                        showErrorMessage("Server connection issue, continuing in offline mode");
                    } else {
                        showErrorMessage("Server connection issue, but you can continue in offline mode");
                    }
                    completeLoginProcess(username);
                });
            }
        }).start();
    }

    private void attemptReconnect(SimpleWebSocketClient client, String username, String deviceName, String deviceId) {
        new Thread(() -> {
            int attempts = 0;
            int maxAttempts = 5;

            while (attempts < maxAttempts && !client.isConnected()) {
                try {
                    attempts++;
                    System.out.println("Reconnection attempt " + attempts + " for device: " + deviceName);
                    Thread.sleep(5000); // Wait 5 seconds between attempts

                    client.reconnect();
                    Thread.sleep(2000);

                    if (client.isConnected()) {
                        System.out.println("Reconnection successful for device: " + deviceName);
                        client.send("DEVICE_INFO|global|" + username + "|" + deviceName + "|" + deviceId);
                        Platform.runLater(() -> {
                            showSuccessMessage("✅ Reconnected to server!");
                            updateConnectionStatus();
                        });
                        break;
                    }
                } catch (InterruptedException e) {
                    break;
                }
            }

            if (!client.isConnected()) {
                Platform.runLater(() -> {
                    showErrorMessage("❌ Could not reconnect to server after " + maxAttempts + " attempts");
                });
            }
        }).start();
    }

    private void completeLoginProcess(String username) {
        System.out.println("🎯 Completing login process for: " + username);

        HelloApplication.setLoggedInUser(username);

        new Thread(() -> {
            try {
                Thread.sleep(1500);

                Platform.runLater(() -> {
                    try {
                        autoLoginInProgress = false;
                        stopMonitor = true; // Stop connection monitor before navigation
                        if (connectionMonitorThread != null) {
                            connectionMonitorThread.interrupt();
                        }
                        System.out.println("🚀 Redirecting to dashboard for user: " + username);
                        HelloApplication.navigateToDashboard();
                    } catch (Exception e) {
                        System.err.println("❌ Failed to load dashboard: " + e.getMessage());
                        showErrorMessage("❌ Failed to load dashboard. Please try restarting the application.");
                        autoLoginInProgress = false;
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                autoLoginInProgress = false;
            }
        }).start();
    }

    private void handleFailedLogin() {
        System.out.println("❌ Login failed");
        autoLoginInProgress = false;
        showErrorMessage("❌ Invalid username or password!");
        shakeLoginForm();
        clearPasswordField();
        highlightInvalidFields();
    }

    private void highlightInvalidFields() {
        String errorStyle = "-fx-border-color: #e74c3c; -fx-border-width: 2px; -fx-border-radius: 5px;";

        Platform.runLater(() -> {
            usernameField.setStyle(errorStyle);
            passwordField.setStyle(errorStyle);

            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    Platform.runLater(() -> {
                        String normalStyle = "-fx-background-color: #34495e; -fx-text-fill: white; -fx-border-color: #7f8c8d; -fx-border-radius: 5; -fx-padding: 10;";
                        usernameField.setStyle(normalStyle);
                        passwordField.setStyle(normalStyle);
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        });
    }

    private void shakeLoginForm() {
        Platform.runLater(() -> {
            String shakeStyle = "-fx-border-color: #e74c3c; -fx-border-width: 2px; -fx-effect: dropshadow(three-pass-box, rgba(231,76,60,0.6), 10, 0, 0, 0);";

            usernameField.setStyle(shakeStyle);
            passwordField.setStyle(shakeStyle);

            new Thread(() -> {
                try {
                    Thread.sleep(600);
                    Platform.runLater(() -> {
                        String normalStyle = "-fx-background-color: #34495e; -fx-text-fill: white; -fx-border-color: #7f8c8d; -fx-border-radius: 5; -fx-padding: 10;";
                        usernameField.setStyle(normalStyle);
                        passwordField.setStyle(normalStyle);
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        });
    }

    private void setLoadingState(boolean isLoading) {
        Platform.runLater(() -> {
            if (isLoading) {
                usernameField.setDisable(true);
                passwordField.setDisable(true);
                rememberMe.setDisable(true);
                if (loginButton != null) {
                    loginButton.setDisable(true);
                    loginButton.setText("Logging in...");
                }
                if (registerButton != null) registerButton.setDisable(true);
                if (forgotPasswordButton != null) forgotPasswordButton.setDisable(true);
                if (serverConfigButton != null) serverConfigButton.setDisable(true);
                if (testConnectionButton != null) testConnectionButton.setDisable(true);
                if (showDevicesButton != null) showDevicesButton.setDisable(true);
            } else {
                usernameField.setDisable(false);
                passwordField.setDisable(false);
                rememberMe.setDisable(false);
                if (loginButton != null) {
                    loginButton.setDisable(false);
                    loginButton.setText("Login");
                }
                if (registerButton != null) registerButton.setDisable(false);
                if (forgotPasswordButton != null) forgotPasswordButton.setDisable(false);
                if (serverConfigButton != null) serverConfigButton.setDisable(false);
                if (testConnectionButton != null) testConnectionButton.setDisable(false);
                if (showDevicesButton != null) showDevicesButton.setDisable(false);
            }
        });
    }

    private void clearErrorMessage() {
        if (messageLabel != null) {
            messageLabel.setText("");
            messageLabel.setStyle("-fx-text-fill: transparent;");
        }
    }

    private void showErrorMessage(String message) {
        if (messageLabel != null) {
            messageLabel.setText(message);
            messageLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 14px;");
        }
        System.err.println("❌ " + message);
    }

    private void showSuccessMessage(String message) {
        if (messageLabel != null) {
            messageLabel.setText(message);
            messageLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold; -fx-font-size: 14px;");
        }
        System.out.println("✅ " + message);
    }

    private void clearPasswordField() {
        Platform.runLater(() -> {
            if (passwordField != null) {
                passwordField.clear();
            }
        });
    }

    @FXML
    protected void onRegisterClick(ActionEvent event) {
        System.out.println("📝 Register button clicked");
        try {
            HelloApplication.setRoot("register-view.fxml");
        } catch (Exception e) {
            System.err.println("❌ Failed to open registration: " + e.getMessage());
            e.printStackTrace();
            showErrorMessage("❌ Failed to open registration!");
        }
    }

    @FXML
    protected void onForgotPasswordClick(ActionEvent event) {
        System.out.println("🔑 Forgot password clicked");

        TextInputDialog usernameDialog = new TextInputDialog();
        usernameDialog.setTitle("Password Reset");
        usernameDialog.setHeaderText("Reset Your Password");
        usernameDialog.setContentText("Enter your username:");

        Optional<String> result = usernameDialog.showAndWait();
        if (result.isPresent()) {
            String username = result.get().trim();

            if (username.isEmpty()) {
                showErrorMessage("❌ Please enter a username!");
                return;
            }

            if (!Database.usernameExists(username)) {
                showErrorMessage("❌ Username not found!");
                return;
            }

            showPasswordResetDialog(username);
        }
    }

    private void showPasswordResetDialog(String username) {
        Dialog<ButtonType> resetDialog = new Dialog<>();
        resetDialog.setTitle("Reset Password");
        resetDialog.setHeaderText("Reset Password for: " + username);

        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText("New Password");

        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Confirm New Password");

        VBox content = new VBox(10);
        content.setPadding(new javafx.geometry.Insets(20));
        content.getChildren().addAll(
                new Label("New Password:"), newPasswordField,
                new Label("Confirm Password:"), confirmPasswordField
        );

        resetDialog.getDialogPane().setContent(content);

        ButtonType resetButtonType = new ButtonType("Reset Password", ButtonBar.ButtonData.OK_DONE);
        resetDialog.getDialogPane().getButtonTypes().addAll(resetButtonType, ButtonType.CANCEL);

        Button resetButton = (Button) resetDialog.getDialogPane().lookupButton(resetButtonType);
        resetButton.setDisable(true);

        newPasswordField.textProperty().addListener((obs, old, newVal) ->
                validatePasswords(newPasswordField, confirmPasswordField, resetButton));
        confirmPasswordField.textProperty().addListener((obs, old, newVal) ->
                validatePasswords(newPasswordField, confirmPasswordField, resetButton));

        resetDialog.showAndWait().ifPresent(result -> {
            if (result == resetButtonType) {
                String newPassword = newPasswordField.getText();
                String confirmPassword = confirmPasswordField.getText();

                if (newPassword.equals(confirmPassword)) {
                    boolean success = Database.updatePassword(username, newPassword);
                    if (success) {
                        showSuccessMessage("✅ Password reset successfully!");
                        passwordField.clear();
                    } else {
                        showErrorMessage("❌ Failed to reset password. Please try again.");
                    }
                } else {
                    showErrorMessage("❌ Passwords do not match!");
                }
            }
        });
    }

    private void validatePasswords(PasswordField newPassword, PasswordField confirmPassword, Button resetButton) {
        String pass1 = newPassword.getText();
        String pass2 = confirmPassword.getText();

        boolean isValid = !pass1.isEmpty() && !pass2.isEmpty() && pass1.equals(pass2) && pass1.length() >= 3;
        resetButton.setDisable(!isValid);
    }

    @FXML
    protected void onServerConfigClick(ActionEvent event) {
        System.out.println("⚙️ Server config clicked");
        try {
            // Show server configuration dialog
            TextInputDialog ipDialog = new TextInputDialog(HelloApplication.getServerIp());
            ipDialog.setTitle("Server Configuration");
            ipDialog.setHeaderText("Configure Server Connection");
            ipDialog.setContentText("Enter Server IP:");

            Optional<String> ipResult = ipDialog.showAndWait();
            if (ipResult.isPresent()) {
                String ip = ipResult.get().trim();

                TextInputDialog portDialog = new TextInputDialog(HelloApplication.getServerPort());
                portDialog.setTitle("Server Port");
                portDialog.setHeaderText("Configure Server Port");
                portDialog.setContentText("Enter Server Port:");

                Optional<String> portResult = portDialog.showAndWait();
                if (portResult.isPresent()) {
                    String port = portResult.get().trim();
                    HelloApplication.setServerConfig(ip, port);
                    showSuccessMessage("✅ Server configuration updated!");
                    updateConnectionStatus();
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Server config error: " + e.getMessage());
            showErrorMessage("❌ Failed to open server configuration!");
        }
    }

    @FXML
    protected void onTestConnectionClick(ActionEvent event) {
        System.out.println("🔍 Testing connection...");
        showSuccessMessage("🔄 Testing connection...");

        new Thread(() -> {
            boolean success = testConnectionWithoutLogin();

            Platform.runLater(() -> {
                if (success) {
                    showSuccessMessage("✅ Connection test successful!");
                } else {
                    showErrorMessage("❌ Connection test failed!");
                }
                updateConnectionStatus();
            });
        }).start();
    }

    private void updateConnectionStatus() {
        if (connectionStatusLabel != null && serverInfoLabel != null) {
            String serverUrl = HelloApplication.getCurrentServerUrl();
            boolean isConnected = HelloApplication.isWebSocketConnected();

            Platform.runLater(() -> {
                if (isConnected) {
                    connectionStatusLabel.setText("🟢 Connected");
                    connectionStatusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold; -fx-font-size: 14px;");
                } else {
                    connectionStatusLabel.setText("🔴 Disconnected");
                    connectionStatusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 14px;");
                }

                serverInfoLabel.setText(serverUrl.replace("ws://", ""));
                serverInfoLabel.setStyle("-fx-text-fill: #bdc3c7; -fx-font-size: 12px;");
            });
        }
    }

    private boolean isConnectedToServer() {
        return HelloApplication.isWebSocketConnected();
    }

    private boolean testConnectionWithoutLogin() {
        try {
            String testUrl = HelloApplication.getCurrentServerUrl();
            System.out.println("🔍 Testing connection to: " + testUrl);

            final boolean[] connectionSuccess = {false};
            final Object lock = new Object();

            SimpleWebSocketClient testClient = new SimpleWebSocketClient(testUrl, message -> {
                System.out.println("🔍 Test connection received: " + message);
                if (message.contains("Connected") || message.contains("Welcome") || message.contains("WELCOME")) {
                    synchronized (lock) {
                        connectionSuccess[0] = true;
                        lock.notifyAll();
                    }
                }
            });

            testClient.connect();

            synchronized (lock) {
                try {
                    lock.wait(3000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            testClient.disconnect();
            return connectionSuccess[0];

        } catch (Exception e) {
            System.err.println("🔍 Connection test failed: " + e.getMessage());
            return false;
        }
    }

    private void loadSavedCredentials() {
        String rememberedUser = HelloApplication.getUserPreference("remembered_username");
        if (rememberedUser != null && !rememberedUser.isEmpty()) {
            Platform.runLater(() -> {
                usernameField.setText(rememberedUser);
                rememberMe.setSelected(true);
                passwordField.requestFocus();
                System.out.println("💾 Loaded saved credentials for: " + rememberedUser);
            });
        }
    }

    private void saveCredentials(String username, String password) {
        HelloApplication.saveUserPreference("remembered_username", username);
        System.out.println("💾 Saved credentials for: " + username);
    }

    private void clearSavedCredentials() {
        HelloApplication.saveUserPreference("remembered_username", "");
        System.out.println("🗑️ Cleared saved credentials");
    }

    @FXML
    protected void onUsernameEnter(ActionEvent event) {
        System.out.println("↵ Username field enter pressed");
        if (passwordField != null) {
            passwordField.requestFocus();
        }
    }

    @FXML
    protected void onPasswordEnter(ActionEvent event) {
        System.out.println("↵ Password field enter pressed");
        onLoginClick(event);
    }

    @FXML
    protected void onRememberMeChanged(ActionEvent event) {
        System.out.println("💾 Remember me changed: " + rememberMe.isSelected());

        if (!rememberMe.isSelected()) {
            clearSavedCredentials();
            showSuccessMessage("🔒 Login credentials will not be saved");
        } else {
            showSuccessMessage("💾 Login credentials will be saved");
        }
    }

    public void triggerAutoLogin(String username) {
        System.out.println("🎯 Manual auto-login triggered for: " + username);
        simulateAutoLogin(username);
    }

    public void cleanup() {
        System.out.println("🧹 Cleaning up LoginController");
        stopMonitor = true;
        if (connectionMonitorThread != null) {
            connectionMonitorThread.interrupt();
        }
        if (testClient != null) {
            testClient.disconnect();
            testClient = null;
        }
        autoLoginInProgress = false;
    }

    public boolean isAutoLoginInProgress() {
        return autoLoginInProgress;
    }

    public void cancelAutoLogin() {
        System.out.println("🚫 Cancelling auto-login");
        autoLoginInProgress = false;
        setLoadingState(false);
        clearErrorMessage();
        showErrorMessage("❌ Auto-login cancelled");
    }
}