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
    private static String SERVER_IP = "192.168.1.107";
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
        System.setProperty("server.ip", SERVER_IP);
        System.setProperty("server.port", String.valueOf(SERVER_PORT));

        System.out.println("Initializing Client Device: " + DEVICE_NAME + " (ID: " + DEVICE_ID + ")");

        // Initialize database
        Database.initializeDatabase();

        // First show the configuration dialog without showing the main stage
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

        // Now load and show the main stage
        showMainStage(primaryStage);
    }

    private boolean showInitialConfigurationDialog() {
        // Create configuration dialog
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Client Configuration - " + DEVICE_NAME);
        dialog.setHeaderText("Configure Server Connection for " + DEVICE_NAME);

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
        grid.setPrefWidth(450);

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

        // Server IP field
        TextField ipField = new TextField();
        ipField.setText(SERVER_IP);
        ipField.setPromptText("e.g., 192.168.1.107");
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
        statusArea.setPrefRowCount(5);
        statusArea.setText("Enter server details and click Test Connection");
        statusArea.setStyle("-fx-text-fill: #666666;");
        grid.add(statusArea, 0, 5, 2, 1);

        dialog.getDialogPane().setContent(grid);

        // Handle test connection button
        Button testButton = (Button) dialog.getDialogPane().lookupButton(testButtonType);
        testButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();

            String ip = ipField.getText().trim();
            String portStr = portField.getText().trim();

            if (ip.isEmpty() || portStr.isEmpty()) {
                statusArea.setText("Please enter both IP and port");
                statusArea.setStyle("-fx-text-fill: red;");
                return;
            }

            try {
                int port = Integer.parseInt(portStr);
                statusArea.setText("Testing connection to " + ip + ":" + port + "...");
                statusArea.setStyle("-fx-text-fill: blue;");

                // Run test in background
                new Thread(() -> {
                    boolean success = testConnection(ip, port);
                    String deviceList = "";
                    if (success) {
                        deviceList = getConnectedDevices(ip, port);
                    }
                    final String finalDeviceList = deviceList;
                    Platform.runLater(() -> {
                        if (success) {
                            statusArea.setText("✓ Connection successful!\n\nConnected Devices:\n" + finalDeviceList);
                            statusArea.setStyle("-fx-text-fill: green;");
                        } else {
                            statusArea.setText("✗ Connection failed!\n\n" + getTroubleshootingInfo());
                            statusArea.setStyle("-fx-text-fill: red;");
                        }
                    });
                }).start();

            } catch (NumberFormatException ex) {
                statusArea.setText("Invalid port number");
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
            if (!ip.isEmpty()) {
                if (isValidIPAddress(ip) || ip.equals("localhost")) {
                    SERVER_IP = ip;
                } else {
                    showAlert(Alert.AlertType.ERROR, "Invalid IP",
                            "Please enter a valid IP address.\nUsing default: " + SERVER_IP);
                }
            }

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

            return true;
        }

        return false;
    }

    private String getConnectedDevices(String ip, int port) {
        StringBuilder result = new StringBuilder();
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), 2000);
            // In a real implementation, you would query the server for connected devices
            result.append("  • Server is reachable\n");
            result.append("  • Port ").append(port).append(" is open\n");
            result.append("  • Ready to connect\n");
        } catch (Exception e) {
            result.append("  • Could not get device list\n");
        }
        return result.toString();
    }

    private void showMainStage(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("login-view.fxml"));
            Scene scene = new Scene(loader.load());

            // Store loader in scene user data for later access
            scene.setUserData(loader);

            // Configure stage
            primaryStage.setTitle("Zoom Client - " + DEVICE_NAME + " [ID: " + DEVICE_ID + "] [Connecting to: " + SERVER_IP + ":" + SERVER_PORT + "]");
            primaryStage.setScene(scene);
            primaryStage.setWidth(900);
            primaryStage.setHeight(700);

            // Position the stage based on device number to avoid overlap
            positionStage(primaryStage);

            // Make sure stage is movable
            primaryStage.setResizable(true);

            // Set to HelloApplication
            HelloApplication.setPrimaryStage(primaryStage);

            // Show the stage
            primaryStage.show();

            System.out.println("Stage initialized and shown for: " + DEVICE_NAME);
            System.out.println("Stage position: X=" + primaryStage.getX() + ", Y=" + primaryStage.getY());

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

                // Auto-login after a short delay with device-specific username
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

            // Position windows in a grid to avoid overlap
            int cols = 3; // 3 columns
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
            // Default positioning if calculation fails
            stage.setX(100);
            stage.setY(100);
        }
    }

    private boolean testServerConnection() {
        System.out.println("Testing connection to " + SERVER_IP + ":" + SERVER_PORT + "...");

        // Try socket connection
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(SERVER_IP, SERVER_PORT), 3000);
            System.out.println("✓ Socket connection successful");
            return true;
        } catch (Exception e) {
            System.err.println("✗ Socket connection failed: " + e.getMessage());

            // Try ping as fallback
            try {
                InetAddress address = InetAddress.getByName(SERVER_IP);
                if (address.isReachable(3000)) {
                    System.out.println("✓ Ping successful but port " + SERVER_PORT + " is not responding");
                    System.out.println("  Make sure the server application is running on that port");
                    return false;
                }
            } catch (Exception ex) {
                System.err.println("✗ Ping failed: " + ex.getMessage());
            }

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
        dialog.setHeaderText("Cannot connect to server: " + SERVER_IP + ":" + SERVER_PORT);

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
                        "   • Is the server application running on %s:%d?\n" +
                        "   • Check firewall settings on server\n\n" +
                        "2. CHECK NETWORK:\n" +
                        "   • Are all devices on same network?\n" +
                        "   • Can you ping the server? Try: ping %s\n\n" +
                        "3. CHECK FIREWALL:\n" +
                        "   • Windows: Allow port %d in firewall\n" +
                        "   • Command: netsh advfirewall firewall add rule name=\"Zoom\" dir=in action=allow protocol=TCP localport=%d\n\n" +
                        "4. VERIFY CONNECTION:\n" +
                        "   • Try: telnet %s %d\n" +
                        "   • Use 'Test Connection' button in config\n\n" +
                        "5. MULTI-DEVICE SETUP:\n" +
                        "   • Maximum clients: Unlimited\n" +
                        "   • Make sure server IP is correct\n" +
                        "   • All clients must use same server IP and port\n",
                SERVER_IP, SERVER_PORT, SERVER_IP, SERVER_PORT, SERVER_PORT, SERVER_IP, SERVER_PORT
        );
    }

    private String scanForServers() {
        StringBuilder result = new StringBuilder();
        result.append("Network Scan Results:\n");
        result.append("=====================\n\n");

        try {
            String localIP = java.net.InetAddress.getLocalHost().getHostAddress();
            String networkPrefix = localIP.substring(0, localIP.lastIndexOf('.') + 1);

            result.append("Your IP: ").append(localIP).append("\n");
            result.append("Network: ").append(networkPrefix).append("x\n");
            result.append("Scanning for servers...\n\n");

            int foundServers = 0;
            result.append("Active hosts:\n");

            for (int i = 1; i <= 254; i++) { // Scan entire subnet
                String testIP = networkPrefix + i;
                try {
                    InetAddress addr = InetAddress.getByName(testIP);
                    if (addr.isReachable(200)) {
                        result.append("  ✓ ").append(testIP).append(" - reachable");

                        // Test common ports
                        for (int port : new int[]{8887, 8888, 8889, 8890, 9000, 9001}) {
                            try (Socket s = new Socket()) {
                                s.connect(new InetSocketAddress(testIP, port), 100);
                                result.append(" [PORT ").append(port).append(" OPEN - Possible Zoom Server]");
                                foundServers++;
                                break;
                            } catch (Exception e) {
                                // Port not open
                            }
                        }
                        result.append("\n");
                    }
                } catch (Exception e) {
                    // Ignore timeout
                }
            }

            if (foundServers == 0) {
                result.append("\nNo Zoom servers found on the network.\n");
                result.append("Make sure the host device is running and firewall is configured.\n");
            } else {
                result.append("\nFound ").append(foundServers).append(" potential server(s).\n");
                result.append("Use one of the IPs above to connect.\n");
            }

        } catch (Exception e) {
            result.append("Error scanning: ").append(e.getMessage());
        }

        return result.toString();
    }

    private boolean isValidIPAddress(String ip) {
        String ipPattern = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
        return ip.matches(ipPattern) || ip.equals("localhost");
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
        // Check if instance number is provided
        if (args.length > 0) {
            try {
                int instance = Integer.parseInt(args[0]);
                System.setProperty("device.instance", String.valueOf(instance));
            } catch (NumberFormatException e) {
                // Ignore
            }
        }

        System.out.println("Starting " + DEVICE_NAME + " - CLIENT DEVICE");
        launch(args);
    }
}