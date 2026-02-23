package org.example.zoom;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Optional;
import java.util.UUID;

public class Device2_Client extends Application {

    private static String DEVICE_NAME = "Client-Device";
    private static String SERVER_IP = ""; // Start empty to force user entry
    private static int SERVER_PORT = 8887;
    private static String DEVICE_ID = UUID.randomUUID().toString().substring(0, 8);

    private Stage primaryStage;
    private boolean configurationComplete = false;
    private static int deviceCounter = 1;

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;

        // Generate unique device name if multiple instances
        if (System.getProperty("device.instance") != null) {
            DEVICE_NAME = "Client-Device-" + System.getProperty("device.instance");
        } else {
            DEVICE_NAME = "Client-Device-" + deviceCounter++;
        }

        // Set initial system properties
        System.setProperty("device.name", DEVICE_NAME);
        System.setProperty("device.id", DEVICE_ID);

        System.out.println("Initializing Client Device: " + DEVICE_NAME + " (ID: " + DEVICE_ID + ")");
        System.out.println("IMPORTANT: You need to enter the SERVER's IP address (where server.js is running)");
        System.out.println("Server IP should be something like: 192.168.0.113 (from your server logs)");

        // Initialize database
        Database.initializeDatabase();

        // First show the configuration dialog
        boolean configured = showInitialConfigurationDialog();

        if (!configured) {
            System.out.println("Configuration cancelled. Exiting...");
            Platform.exit();
            return;
        }

        // Update system properties with new values
        System.setProperty("device.name", DEVICE_NAME);
        System.setProperty("device.id", DEVICE_ID);
        System.setProperty("server.ip", SERVER_IP);
        System.setProperty("server.port", String.valueOf(SERVER_PORT));

        System.out.println("Configured to connect to server at: " + SERVER_IP + ":" + SERVER_PORT);

        // Now load and show the main stage
        showMainStage(primaryStage);
    }

    private boolean showInitialConfigurationDialog() {
        // Create configuration dialog
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Client Configuration - " + DEVICE_NAME);
        dialog.setHeaderText("Connect to Zoom Server\n\nEnter the SERVER's IP address (where server.js is running)");

        // Set dialog to be movable and resizable
        dialog.setResizable(true);

        // Set the button types
        ButtonType connectButtonType = new ButtonType("Connect", ButtonBar.ButtonData.OK_DONE);
        ButtonType testButtonType = new ButtonType("Test Connection", ButtonBar.ButtonData.OTHER);
        ButtonType scanButtonType = new ButtonType("Scan Network", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(connectButtonType, testButtonType, scanButtonType, ButtonType.CANCEL);

        // Create grid for input fields
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 20, 10, 20));
        grid.setPrefWidth(500);

        // Device name field
        TextField deviceNameField = new TextField();
        deviceNameField.setText(DEVICE_NAME);
        deviceNameField.setPrefWidth(300);
        grid.add(new Label("Device Name:"), 0, 0);
        grid.add(deviceNameField, 1, 0);

        // Device ID (read-only)
        TextField deviceIdField = new TextField();
        deviceIdField.setText(DEVICE_ID);
        deviceIdField.setEditable(false);
        deviceIdField.setStyle("-fx-background-color: #f0f0f0;");
        grid.add(new Label("Device ID:"), 0, 1);
        grid.add(deviceIdField, 1, 1);

        // Server IP field - IMPORTANT: This should be the SERVER's IP
        TextField ipField = new TextField();
        ipField.setPromptText("e.g., 192.168.0.113 (SERVER's IP)");
        ipField.setText(""); // Start empty
        grid.add(new Label("Server IP:"), 0, 2);
        grid.add(ipField, 1, 2);

        // Server port field
        TextField portField = new TextField();
        portField.setText(String.valueOf(SERVER_PORT));
        portField.setPromptText("e.g., 8887");
        grid.add(new Label("Server Port:"), 0, 3);
        grid.add(portField, 1, 3);

        // Auto-reconnect option
        CheckBox autoReconnectCheck = new CheckBox("Auto-reconnect on disconnect");
        autoReconnectCheck.setSelected(true);
        grid.add(autoReconnectCheck, 1, 4);

        // Status area
        TextArea statusArea = new TextArea();
        statusArea.setEditable(false);
        statusArea.setPrefRowCount(6);
        statusArea.setWrapText(true);
        statusArea.setText("Enter the SERVER IP address shown in the server logs\n\n" +
                "From your server logs, the IP is: 192.168.0.113");
        statusArea.setStyle("-fx-text-fill: #666666;");
        grid.add(statusArea, 0, 5, 2, 1);

        // Add helpful tips
        Label tipLabel = new Label("💡 IMPORTANT: The server IP is NOT your IP. It's the IP where server.js is running!");
        tipLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 12px;");
        grid.add(tipLabel, 0, 6, 2, 1);

        dialog.getDialogPane().setContent(grid);

        // Handle test connection button
        Button testButton = (Button) dialog.getDialogPane().lookupButton(testButtonType);
        testButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();

            String ip = ipField.getText().trim();
            String portStr = portField.getText().trim();

            if (ip.isEmpty()) {
                statusArea.setText("❌ Please enter the SERVER IP address");
                statusArea.setStyle("-fx-text-fill: red;");
                return;
            }

            if (portStr.isEmpty()) {
                statusArea.setText("❌ Please enter a port number");
                statusArea.setStyle("-fx-text-fill: red;");
                return;
            }

            try {
                int port = Integer.parseInt(portStr);
                statusArea.setText("Testing connection to SERVER at " + ip + ":" + port + "...");
                statusArea.setStyle("-fx-text-fill: blue;");

                // Run test in background
                new Thread(() -> {
                    boolean success = testConnection(ip, port);
                    Platform.runLater(() -> {
                        if (success) {
                            statusArea.setText("✅ Connection successful!\n\n" +
                                    "Server at " + ip + ":" + port + " is reachable.\n\n" +
                                    "You can now click Connect to proceed.");
                            statusArea.setStyle("-fx-text-fill: green;");
                        } else {
                            statusArea.setText("❌ Connection failed!\n\n" +
                                    "Cannot reach server at " + ip + ":" + port + "\n\n" +
                                    "Make sure:\n" +
                                    "• The server is running on that computer\n" +
                                    "• You're using the correct IP (check server logs)\n" +
                                    "• Firewall allows port " + port + "\n" +
                                    "• Both devices are on the same network");
                            statusArea.setStyle("-fx-text-fill: red;");
                        }
                    });
                }).start();

            } catch (NumberFormatException ex) {
                statusArea.setText("❌ Invalid port number");
                statusArea.setStyle("-fx-text-fill: red;");
            }
        });

        // Handle scan button
        Button scanButton = (Button) dialog.getDialogPane().lookupButton(scanButtonType);
        scanButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            statusArea.setText("Scanning network for servers...");
            statusArea.setStyle("-fx-text-fill: blue;");

            new Thread(() -> {
                String scanResult = scanForServers();
                Platform.runLater(() -> {
                    statusArea.setText(scanResult);
                    statusArea.setStyle("-fx-text-fill: black;");

                    // Try to extract server IP from scan
                    String foundIP = extractServerIP(scanResult);
                    if (foundIP != null) {
                        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                        alert.setTitle("Server Found");
                        alert.setHeaderText("Found potential server at: " + foundIP);
                        alert.setContentText("Would you like to use this IP?");

                        Optional<ButtonType> result = alert.showAndWait();
                        if (result.isPresent() && result.get() == ButtonType.OK) {
                            ipField.setText(foundIP);
                        }
                    }
                });
            }).start();
        });

        // Position the dialog in the center of the screen
        dialog.setOnShown(e -> {
            Stage dialogStage = (Stage) dialog.getDialogPane().getScene().getWindow();
            dialogStage.setX((java.awt.Toolkit.getDefaultToolkit().getScreenSize().getWidth() - dialogStage.getWidth()) / 2);
            dialogStage.setY((java.awt.Toolkit.getDefaultToolkit().getScreenSize().getHeight() - dialogStage.getHeight()) / 2);
        });

        // Request focus on the IP field by default
        Platform.runLater(ipField::requestFocus);

        // Show dialog and wait for response
        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent() && result.get() == connectButtonType) {
            // Update values from fields
            DEVICE_NAME = deviceNameField.getText().trim();
            if (DEVICE_NAME.isEmpty()) {
                DEVICE_NAME = "Client-Device-" + deviceCounter;
            }

            String ip = ipField.getText().trim();
            if (ip.isEmpty()) {
                showAlert(Alert.AlertType.ERROR, "IP Required",
                        "Please enter the SERVER IP address.\n\n" +
                                "From your server logs, the IP is: 192.168.0.113");
                return false;
            }

            if (!isValidIPAddress(ip) && !ip.equals("localhost")) {
                showAlert(Alert.AlertType.ERROR, "Invalid IP",
                        "Please enter a valid IP address.\n\n" +
                                "Example: 192.168.0.113 (from your server logs)");
                return false;
            }

            // Store the server IP
            SERVER_IP = ip;

            String portStr = portField.getText().trim();
            if (!portStr.isEmpty()) {
                try {
                    int port = Integer.parseInt(portStr);
                    if (port >= 1024 && port <= 65535) {
                        SERVER_PORT = port;
                    } else {
                        showAlert(Alert.AlertType.ERROR, "Invalid Port",
                                "Port must be between 1024 and 65535.\nUsing default: " + SERVER_PORT);
                    }
                } catch (NumberFormatException ex) {
                    showAlert(Alert.AlertType.ERROR, "Invalid Port",
                            "Please enter a valid port number.\nUsing default: " + SERVER_PORT);
                }
            }

            // Save auto-reconnect setting
            System.setProperty("auto.reconnect", String.valueOf(autoReconnectCheck.isSelected()));

            // Test the connection one more time before proceeding
            if (!testConnection(SERVER_IP, SERVER_PORT)) {
                Alert confirmAlert = new Alert(Alert.AlertType.CONFIRMATION);
                confirmAlert.setTitle("Connection Test Failed");
                confirmAlert.setHeaderText("Cannot connect to server at " + SERVER_IP + ":" + SERVER_PORT);
                confirmAlert.setContentText(
                        "Make sure:\n" +
                                "• Server is running on " + SERVER_IP + "\n" +
                                "• You can ping " + SERVER_IP + "\n" +
                                "• Firewall allows port " + SERVER_PORT + "\n\n" +
                                "Do you want to continue anyway?");

                Optional<ButtonType> confirmResult = confirmAlert.showAndWait();
                if (!confirmResult.isPresent() || confirmResult.get() != ButtonType.OK) {
                    return false;
                }
            }

            System.out.println("✅ Client configured to connect to server: " + SERVER_IP + ":" + SERVER_PORT);
            return true;
        }

        return false;
    }

    private String extractServerIP(String scanResult) {
        // Look for IP addresses in the scan result
        String[] lines = scanResult.split("\n");
        for (String line : lines) {
            if (line.contains("Found server:") || line.contains("✅") || line.contains("PORT OPEN")) {
                // Extract IP using regex
                java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})");
                java.util.regex.Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    return matcher.group(1);
                }
            }
        }
        return null;
    }

    private void showMainStage(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("login-view.fxml"));
            Scene scene = new Scene(loader.load());

            // Store loader in scene user data for later access
            scene.setUserData(loader);

            // Configure stage with the actual server IP
            String displayTitle = String.format("Zoom Client - %s [ID: %s] [Connecting to SERVER: %s:%d]",
                    DEVICE_NAME, DEVICE_ID, SERVER_IP, SERVER_PORT);

            primaryStage.setTitle(displayTitle);
            primaryStage.setScene(scene);
            primaryStage.setWidth(900);
            primaryStage.setHeight(700);

            // Position the stage
            positionStage(primaryStage);

            // Make sure stage is movable
            primaryStage.setResizable(true);

            // Set to HelloApplication
            HelloApplication.setPrimaryStage(primaryStage);

            // Show the stage
            primaryStage.show();

            System.out.println("Stage initialized and shown for: " + DEVICE_NAME);
            System.out.println("Connecting to SERVER at: " + SERVER_IP + ":" + SERVER_PORT);
            System.out.println("Stage position: X=" + primaryStage.getX() + ", Y=" + primaryStage.getY());

            // Set server config in HelloApplication
            HelloApplication.setServerConfig(SERVER_IP, String.valueOf(SERVER_PORT));

            // Test connection before proceeding
            new Thread(() -> {
                if (!testServerConnection()) {
                    boolean continueAnyway = showConnectionErrorDialog();
                    if (!continueAnyway) {
                        System.out.println("Connection test failed. Exiting...");
                        Platform.runLater(() -> Platform.exit());
                        return;
                    }
                }

                // Auto-login after a short delay
                try {
                    Thread.sleep(2000);
                    Platform.runLater(() -> {
                        try {
                            Object controller = loader.getController();
                            if (controller instanceof LoginController) {
                                String username = "User" + DEVICE_NAME.replaceAll("[^0-9]", "");
                                if (username.equals("User")) {
                                    username = "ClientUser" + (int)(Math.random() * 1000);
                                }
                                ((LoginController) controller).simulateAutoLogin(username);
                                System.out.println("Auto-login initiated for user: " + username);
                            }
                        } catch (Exception e) {
                            System.err.println("Auto-login failed: " + e.getMessage());
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();

        } catch (Exception e) {
            System.err.println("Error loading main stage: " + e.getMessage());
            e.printStackTrace();
            showAlert(Alert.AlertType.ERROR, "Error", "Failed to load application: " + e.getMessage());
        }
    }

    private void positionStage(Stage stage) {
        try {
            // Get device number from name
            String numStr = DEVICE_NAME.replaceAll("[^0-9]", "");
            int deviceNum = 1;
            if (!numStr.isEmpty()) {
                deviceNum = Integer.parseInt(numStr);
            }

            javafx.geometry.Rectangle2D screenBounds = javafx.stage.Screen.getPrimary().getVisualBounds();

            // Position windows in a grid
            int cols = 3;
            int row = (deviceNum - 1) / cols;
            int col = (deviceNum - 1) % cols;

            double x = 50 + (col * 350);
            double y = 50 + (row * 300);

            // Ensure within screen bounds
            if (x + stage.getWidth() > screenBounds.getWidth()) {
                x = screenBounds.getWidth() - stage.getWidth() - 50;
            }
            if (y + stage.getHeight() > screenBounds.getHeight()) {
                y = screenBounds.getHeight() - stage.getHeight() - 50;
            }

            stage.setX(x);
            stage.setY(y);

            System.out.println("Positioned " + DEVICE_NAME + " at (" + x + ", " + y + ")");
        } catch (Exception e) {
            stage.setX(100);
            stage.setY(100);
        }
    }

    private boolean testServerConnection() {
        System.out.println("Testing connection to SERVER at " + SERVER_IP + ":" + SERVER_PORT + "...");

        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(SERVER_IP, SERVER_PORT), 3000);
            System.out.println("✓ Connection to server successful");
            return true;
        } catch (Exception e) {
            System.err.println("✗ Connection to server failed: " + e.getMessage());
            return false;
        }
    }

    private boolean testConnection(String ip, int port) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), 2000);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean showConnectionErrorDialog() {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Connection Error - " + DEVICE_NAME);
        dialog.setHeaderText("Cannot connect to SERVER at: " + SERVER_IP + ":" + SERVER_PORT);

        ButtonType retryButton = new ButtonType("Retry", ButtonBar.ButtonData.OK_DONE);
        ButtonType reconfigureButton = new ButtonType("Reconfigure", ButtonBar.ButtonData.OTHER);
        ButtonType continueButton = new ButtonType("Continue Anyway", ButtonBar.ButtonData.OTHER);
        ButtonType exitButton = new ButtonType("Exit", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.getDialogPane().getButtonTypes().addAll(retryButton, reconfigureButton, continueButton, exitButton);

        VBox content = new VBox(10);
        content.setPadding(new javafx.geometry.Insets(20));

        Label infoLabel = new Label("Connection troubleshooting:");
        infoLabel.setStyle("-fx-font-weight: bold;");

        TextArea infoArea = new TextArea();
        infoArea.setEditable(false);
        infoArea.setPrefRowCount(8);
        infoArea.setPrefWidth(500);
        infoArea.setWrapText(true);
        infoArea.setText(getTroubleshootingInfo());

        Label deviceLabel = new Label("Device: " + DEVICE_NAME + " (ID: " + DEVICE_ID + ")");
        deviceLabel.setStyle("-fx-text-fill: #666666;");

        content.getChildren().addAll(deviceLabel, infoLabel, infoArea);
        dialog.getDialogPane().setContent(content);

        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent()) {
            if (result.get() == retryButton) {
                return testServerConnection();
            } else if (result.get() == reconfigureButton) {
                Platform.runLater(() -> {
                    try {
                        start(new Stage());
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
                return false;
            } else if (result.get() == continueButton) {
                return true;
            }
        }

        return false;
    }

    private String getTroubleshootingInfo() {
        return String.format(
                "TROUBLESHOOTING INFORMATION\n" +
                        "===========================\n\n" +
                        "1. CHECK SERVER:\n" +
                        "   • Is the Node.js server running on %s:%d?\n" +
                        "   • Command: node server/server.js\n" +
                        "   • Look for: '✅ WebSocket server is listening'\n\n" +
                        "2. CORRECT IP:\n" +
                        "   • From your server logs, the IP is: 192.168.0.113\n" +
                        "   • You entered: %s\n" +
                        "   • Make sure you're using the SERVER's IP, not your own\n\n" +
                        "3. CHECK NETWORK:\n" +
                        "   • Are both devices on the same network?\n" +
                        "   • Can you ping %s? Try: ping %s\n\n" +
                        "4. CHECK FIREWALL:\n" +
                        "   • On the SERVER, run: netsh advfirewall firewall add rule name=\"Zoom\" dir=in action=allow protocol=TCP localport=%d\n\n" +
                        "5. VERIFY CONNECTION:\n" +
                        "   • Try: telnet %s %d\n",
                SERVER_IP, SERVER_PORT, SERVER_IP, SERVER_IP, SERVER_IP, SERVER_PORT, SERVER_IP, SERVER_PORT
        );
    }

    private String scanForServers() {
        StringBuilder result = new StringBuilder();
        result.append("🔍 NETWORK SCAN RESULTS\n");
        result.append("======================\n\n");

        try {
            String localIP = java.net.InetAddress.getLocalHost().getHostAddress();
            String networkPrefix = localIP.substring(0, localIP.lastIndexOf('.') + 1);

            result.append("Your IP: ").append(localIP).append("\n");
            result.append("Network: ").append(networkPrefix).append("x\n");
            result.append("Scanning for servers...\n\n");

            int foundServers = 0;
            result.append("Active hosts:\n");

            // Scan the local subnet
            for (int i = 1; i <= 254; i++) {
                String testIP = networkPrefix + i;
                try {
                    InetAddress addr = InetAddress.getByName(testIP);
                    if (addr.isReachable(200)) {
                        result.append("  ✓ ").append(testIP).append(" - reachable");

                        // Test port 8887
                        try (Socket s = new Socket()) {
                            s.connect(new InetSocketAddress(testIP, 8887), 100);
                            result.append(" [PORT 8887 OPEN - Zoom Server!]");
                            foundServers++;
                        } catch (Exception e) {
                            // Port not open
                        }
                        result.append("\n");
                    }
                } catch (Exception e) {
                    // Ignore timeout
                }
            }

            if (foundServers == 0) {
                result.append("\n❌ No Zoom servers found on the network.\n");
                result.append("Make sure:\n");
                result.append("• Node.js server is running on another computer\n");
                result.append("• You're using the correct IP: 192.168.0.113\n");
                result.append("• Firewall allows port 8887 on the server\n");
            } else {
                result.append("\n✅ Found ").append(foundServers).append(" potential server(s).\n");
                result.append("Use the IP address 192.168.0.113 to connect.\n");
            }

        } catch (Exception e) {
            result.append("Error scanning: ").append(e.getMessage());
        }

        return result.toString();
    }

    private boolean isValidIPAddress(String ip) {
        if (ip == null || ip.isEmpty()) return false;
        if (ip.equals("localhost")) return true;

        String ipPattern = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
        return ip.matches(ipPattern);
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    @Override
    public void stop() throws Exception {
        System.out.println("Client application stopped: " + DEVICE_NAME + " (ID: " + DEVICE_ID + ")");
        super.stop();
    }

    public static void main(String[] args) {
        if (args.length > 0) {
            try {
                int instance = Integer.parseInt(args[0]);
                System.setProperty("device.instance", String.valueOf(instance));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        System.out.println("\n" + "=".repeat(60));
        System.out.println("Starting " + DEVICE_NAME + " - CLIENT DEVICE");
        System.out.println("=".repeat(60));
        System.out.println("\nIMPORTANT: You need to connect to the SERVER's IP address");
        System.out.println("From your server logs, the server IP is: 192.168.0.113");
        System.out.println("Make sure to enter this IP in the configuration dialog.\n");

        launch(args);
    }
}