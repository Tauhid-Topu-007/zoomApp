package org.example.zoom;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.util.Optional;

public class Device2_Client extends Application {

    private static String DEVICE_NAME = "Client-Device-2";
    private static String SERVER_IP = "192.168.1.107";
    private static int SERVER_PORT = 8887;

    private Stage primaryStage;
    private boolean configurationComplete = false;

    @Override
    public void start(Stage primaryStage) throws Exception {
        this.primaryStage = primaryStage;

        // Set initial system properties
        System.setProperty("device.name", DEVICE_NAME);
        System.setProperty("server.ip", SERVER_IP);
        System.setProperty("server.port", String.valueOf(SERVER_PORT));

        System.out.println("Initializing Client Device...");

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
        System.setProperty("server.ip", SERVER_IP);
        System.setProperty("server.port", String.valueOf(SERVER_PORT));

        // Now load and show the main stage
        showMainStage(primaryStage);
    }

    private boolean showInitialConfigurationDialog() {
        // Create configuration dialog without owner first
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Client Configuration");
        dialog.setHeaderText("Configure Server Connection");

        // Set dialog to be movable and resizable
        dialog.setResizable(true);

        // Set the button types
        ButtonType connectButtonType = new ButtonType("Connect", ButtonBar.ButtonData.OK_DONE);
        ButtonType testButtonType = new ButtonType("Test Connection", ButtonBar.ButtonData.OTHER);
        dialog.getDialogPane().getButtonTypes().addAll(connectButtonType, testButtonType, ButtonType.CANCEL);

        // Create grid for input fields
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 20, 10, 20));
        grid.setPrefWidth(400);

        // Device name field
        TextField deviceNameField = new TextField();
        deviceNameField.setText(DEVICE_NAME);
        deviceNameField.setPrefWidth(250);
        grid.add(new Label("Device Name:"), 0, 0);
        grid.add(deviceNameField, 1, 0);

        // Server IP field
        TextField ipField = new TextField();
        ipField.setText(SERVER_IP);
        ipField.setPromptText("e.g., 192.168.1.107");
        grid.add(new Label("Server IP:"), 0, 1);
        grid.add(ipField, 1, 1);

        // Server port field
        TextField portField = new TextField();
        portField.setText(String.valueOf(SERVER_PORT));
        portField.setPromptText("e.g., 8887");
        grid.add(new Label("Server Port:"), 0, 2);
        grid.add(portField, 1, 2);

        // Network scan button
        Button scanButton = new Button("Scan Network");
        scanButton.setMaxWidth(Double.MAX_VALUE);
        scanButton.setOnAction(e -> {
            String scanResult = scanForServers();
            showAlert(Alert.AlertType.INFORMATION, "Network Scan Results", scanResult);
        });
        grid.add(scanButton, 1, 3);

        // Status label
        Label statusLabel = new Label("Enter server details and click Test");
        statusLabel.setStyle("-fx-text-fill: #666666;");
        grid.add(statusLabel, 0, 4, 2, 1);

        dialog.getDialogPane().setContent(grid);

        // Handle test connection button
        Button testButton = (Button) dialog.getDialogPane().lookupButton(testButtonType);
        testButton.addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();

            String ip = ipField.getText().trim();
            String portStr = portField.getText().trim();

            if (ip.isEmpty() || portStr.isEmpty()) {
                statusLabel.setText("Please enter both IP and port");
                statusLabel.setStyle("-fx-text-fill: red;");
                return;
            }

            try {
                int port = Integer.parseInt(portStr);
                statusLabel.setText("Testing connection to " + ip + ":" + port + "...");
                statusLabel.setStyle("-fx-text-fill: blue;");

                // Run test in background
                new Thread(() -> {
                    boolean success = testConnection(ip, port);
                    Platform.runLater(() -> {
                        if (success) {
                            statusLabel.setText("✓ Connection successful!");
                            statusLabel.setStyle("-fx-text-fill: green;");
                        } else {
                            statusLabel.setText("✗ Connection failed!");
                            statusLabel.setStyle("-fx-text-fill: red;");
                        }
                    });
                }).start();

            } catch (NumberFormatException ex) {
                statusLabel.setText("Invalid port number");
                statusLabel.setStyle("-fx-text-fill: red;");
            }
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
                DEVICE_NAME = "Client-Device-2";
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

            return true;
        }

        return false;
    }

    private void showMainStage(Stage primaryStage) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("login-view.fxml"));
            Scene scene = new Scene(loader.load());

            // Store loader in scene user data for later access
            scene.setUserData(loader);

            // Configure stage
            primaryStage.setTitle("Zoom Client - " + DEVICE_NAME + " [Connecting to: " + SERVER_IP + ":" + SERVER_PORT + "]");
            primaryStage.setScene(scene);
            primaryStage.setWidth(900);
            primaryStage.setHeight(700);

            // Position the stage on the right side of the screen
            javafx.geometry.Rectangle2D screenBounds = javafx.stage.Screen.getPrimary().getVisualBounds();
            primaryStage.setX(screenBounds.getWidth() - 950); // 900 width + 50 margin
            primaryStage.setY(100);

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

                // Auto-login after a short delay
                try {
                    Thread.sleep(3000);
                    Platform.runLater(() -> {
                        try {
                            Object controller = loader.getController();
                            if (controller instanceof LoginController) {
                                ((LoginController) controller).simulateAutoLogin("Tanvir");
                                System.out.println("Auto-login initiated for user: Tanvir");
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
        dialog.setTitle("Connection Error");
        dialog.setHeaderText("Cannot connect to server: " + SERVER_IP + ":" + SERVER_PORT);

        // Center the dialog
        dialog.setOnShown(e -> {
            Stage dialogStage = (Stage) dialog.getDialogPane().getScene().getWindow();
            dialogStage.setX((java.awt.Toolkit.getDefaultToolkit().getScreenSize().getWidth() - dialogStage.getWidth()) / 2);
            dialogStage.setY((java.awt.Toolkit.getDefaultToolkit().getScreenSize().getHeight() - dialogStage.getHeight()) / 2);
        });

        ButtonType retryButton = new ButtonType("Retry", ButtonBar.ButtonData.OK_DONE);
        ButtonType reconfigureButton = new ButtonType("Reconfigure", ButtonBar.ButtonData.OTHER);
        ButtonType continueButton = new ButtonType("Continue Anyway", ButtonBar.ButtonData.OTHER);
        ButtonType exitButton = new ButtonType("Exit", ButtonBar.ButtonData.CANCEL_CLOSE);

        dialog.getDialogPane().getButtonTypes().addAll(retryButton, reconfigureButton, continueButton, exitButton);

        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20));

        TextArea infoArea = new TextArea();
        infoArea.setEditable(false);
        infoArea.setPrefRowCount(8);
        infoArea.setPrefWidth(500);
        infoArea.setText(getTroubleshootingInfo());
        grid.add(infoArea, 0, 0);

        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent()) {
            if (result.get() == retryButton) {
                return testServerConnection();
            } else if (result.get() == reconfigureButton) {
                // This will cause the app to restart configuration
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
        return
                "TROUBLESHOOTING INFORMATION\n" +
                        "===========================\n\n" +
                        "1. CHECK SERVER:\n" +
                        "   • Is the server application running?\n" +
                        "   • Is the server using the correct port?\n" +
                        "   • Check firewall settings on server\n\n" +
                        "2. CHECK NETWORK:\n" +
                        "   • Are both devices on same network?\n" +
                        "   • Can you ping the server? Try: ping " + SERVER_IP + "\n\n" +
                        "3. CHECK FIREWALL:\n" +
                        "   • Windows: Allow port " + SERVER_PORT + " in firewall\n" +
                        "   • Command: netsh advfirewall firewall add rule name=\"Zoom\" dir=in action=allow protocol=TCP localport=" + SERVER_PORT + "\n\n" +
                        "4. VERIFY CONNECTION:\n" +
                        "   • Try: telnet " + SERVER_IP + " " + SERVER_PORT + "\n" +
                        "   • Use 'Test Connection' button in config\n\n" +
                        "5. ALTERNATIVES:\n" +
                        "   • Try different IP (check server's actual IP)\n" +
                        "   • Try different port\n" +
                        "   • Disable VPN temporarily\n";
    }

    private String scanForServers() {
        StringBuilder result = new StringBuilder();
        result.append("Network Scan Results:\n\n");

        try {
            String localIP = java.net.InetAddress.getLocalHost().getHostAddress();
            String networkPrefix = localIP.substring(0, localIP.lastIndexOf('.') + 1);

            result.append("Your IP: ").append(localIP).append("\n");
            result.append("Scanning network: ").append(networkPrefix).append("x\n\n");

            int foundServers = 0;
            for (int i = 1; i <= 10; i++) { // Scan first 10 IPs for speed
                String testIP = networkPrefix + i;
                try {
                    InetAddress addr = InetAddress.getByName(testIP);
                    if (addr.isReachable(500)) {
                        result.append("✓ ").append(testIP).append(" - reachable");

                        // Test common ports
                        for (int port : new int[]{8887, 8888, 8889, 8890}) {
                            try (Socket s = new Socket()) {
                                s.connect(new InetSocketAddress(testIP, port), 200);
                                result.append(" [PORT ").append(port).append(" OPEN]");
                                foundServers++;
                                break;
                            } catch (Exception e) {
                                // Port not open
                            }
                        }
                        result.append("\n");
                    }
                } catch (Exception e) {
                    // Ignore
                }
            }

            if (foundServers == 0) {
                result.append("\nNo servers found with open ports.\n");
                result.append("Make sure server is running and firewall is configured.");
            }

        } catch (Exception e) {
            result.append("Error scanning: ").append(e.getMessage());
        }

        return result.toString();
    }

    private boolean isValidIPAddress(String ip) {
        String ipPattern = "^((25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)$";
        return ip.matches(ipPattern);
    }

    private void showAlert(Alert.AlertType type, String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);

            // Center the alert
            Stage alertStage = (Stage) alert.getDialogPane().getScene().getWindow();
            alertStage.setX((java.awt.Toolkit.getDefaultToolkit().getScreenSize().getWidth() - alertStage.getWidth()) / 2);
            alertStage.setY((java.awt.Toolkit.getDefaultToolkit().getScreenSize().getHeight() - alertStage.getHeight()) / 2);

            alert.showAndWait();
        });
    }

    @Override
    public void stop() throws Exception {
        System.out.println("Client application stopped");
        super.stop();
    }

    public static void main(String[] args) {
        System.out.println("Starting Device 2 - CLIENT DEVICE (Manual Configuration)");
        launch(args);
    }
}