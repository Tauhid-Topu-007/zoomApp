package org.example.zoom;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.stage.Stage;
import org.example.zoom.websocket.SimpleNativeWebSocketServer;

import java.util.Optional;

public class Device1_Host extends Application {

    private static String DEVICE_NAME = "Host-Device-1";
    private static String SERVER_IP = "192.168.1.107";
    private static int SERVER_PORT = 8887;

    @Override
    public void start(Stage primaryStage) throws Exception {
        // First show the main stage
        System.setProperty("device.name", DEVICE_NAME);
        System.setProperty("server.ip", SERVER_IP);
        System.setProperty("server.port", String.valueOf(SERVER_PORT));

        System.out.println("Initializing Host Device...");

        Database.initializeDatabase();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("login-view.fxml"));
        Scene scene = new Scene(loader.load());

        primaryStage.setTitle("Zoom Host - " + DEVICE_NAME + " [Starting...]");
        primaryStage.setScene(scene);
        primaryStage.setWidth(900);
        primaryStage.setHeight(700);
        primaryStage.setX(100);
        primaryStage.setY(100);

        HelloApplication.setPrimaryStage(primaryStage);

        // Show the stage first
        primaryStage.show();

        System.out.println("Stage initialized and shown for: " + DEVICE_NAME);

        // Now show configuration dialog after stage is shown
        Platform.runLater(() -> {
            if (!showConfigurationDialog(primaryStage)) {
                System.out.println("Configuration cancelled. Exiting...");
                Platform.exit();
                return;
            }

            // Start server after configuration
            startServer(primaryStage);
        });
    }

    private boolean showConfigurationDialog(Stage ownerStage) {
        // Create configuration dialog
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Server Configuration");
        dialog.setHeaderText("Configure Server Settings");
        dialog.initOwner(ownerStage);

        // Set the button types
        ButtonType startButtonType = new ButtonType("Start Server", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(startButtonType, ButtonType.CANCEL);

        // Create grid for input fields
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        // Device name field
        TextField deviceNameField = new TextField();
        deviceNameField.setText(DEVICE_NAME);
        grid.add(new Label("Device Name:"), 0, 0);
        grid.add(deviceNameField, 1, 0);

        // IP address field
        TextField ipField = new TextField();
        ipField.setText(SERVER_IP);
        ipField.setPromptText("e.g., 192.168.1.107");
        grid.add(new Label("Server IP:"), 0, 1);
        grid.add(ipField, 1, 1);

        // Port field
        TextField portField = new TextField();
        portField.setText(String.valueOf(SERVER_PORT));
        portField.setPromptText("e.g., 8887");
        grid.add(new Label("Port:"), 0, 2);
        grid.add(portField, 1, 2);

        // Network scan button
        Button scanButton = new Button("Scan Network");
        scanButton.setOnAction(e -> {
            String networkInfo = scanLocalNetwork();
            showAlert(Alert.AlertType.INFORMATION, "Network Scan Results", networkInfo);
        });
        grid.add(scanButton, 1, 3);

        // Add help text
        Label helpLabel = new Label("Note: If the specified port is busy, the server will auto-select another port.");
        helpLabel.setStyle("-fx-text-fill: #666666; -fx-font-size: 10px;");
        grid.add(helpLabel, 0, 4, 2, 1);

        dialog.getDialogPane().setContent(grid);

        // Request focus on the IP field by default
        Platform.runLater(ipField::requestFocus);

        // Show dialog and wait for response
        Optional<ButtonType> result = dialog.showAndWait();

        if (result.isPresent() && result.get() == startButtonType) {
            // Update values from fields
            DEVICE_NAME = deviceNameField.getText().trim();
            if (DEVICE_NAME.isEmpty()) {
                DEVICE_NAME = "Host-Device-1";
            }

            String ip = ipField.getText().trim();
            if (!ip.isEmpty()) {
                if (isValidIPAddress(ip) || ip.equals("localhost") || ip.equals("0.0.0.0")) {
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

    private void startServer(Stage primaryStage) {
        System.out.println("Starting Simple WebSocket Server...");
        System.out.println("Configured IP: " + SERVER_IP);
        System.out.println("Configured Port: " + SERVER_PORT);

        SimpleNativeWebSocketServer server = SimpleNativeWebSocketServer.getInstance();

        // Configure server with manual port
        boolean serverStarted = server.start(SERVER_PORT);

        if (!serverStarted) {
            // Try fallback ports if specified port is unavailable
            System.err.println("Port " + SERVER_PORT + " is unavailable, trying fallback ports...");
            serverStarted = server.start();
        }

        int actualPort = server.getPort();

        if (!server.isRunning() || actualPort == -1) {
            System.err.println("CRITICAL: Failed to start WebSocket server!");
            showAlert(Alert.AlertType.ERROR, "Server Error",
                    "Failed to start WebSocket server!\n\n" +
                            "Please check if ports 8887-8895 are available.\n" +
                            "Make sure no other instance is running.");
            return;
        }

        System.setProperty("server.port", String.valueOf(actualPort));

        // Update stage title
        primaryStage.setTitle("Zoom Host - " + DEVICE_NAME + " [IP: " + SERVER_IP + " | Port: " + actualPort + "]");

        System.out.println("\n" + "=".repeat(60));
        System.out.println("Server started successfully!");
        System.out.println("Server IP for clients: " + SERVER_IP);
        System.out.println("Requested Port: " + SERVER_PORT);
        System.out.println("Actual Server Port: " + actualPort);
        System.out.println("Database: " + Database.URL);
        System.out.println("Server Status: " + (server.isRunning() ? "RUNNING" : "STOPPED"));
        System.out.println("=".repeat(60));

        System.out.println("Server client count: " + server.getClientCount());

        // Show connection info
        showConnectionInfo(actualPort);

        primaryStage.setOnCloseRequest(event -> {
            System.out.println("Application closing...");
            server.stop();
        });

        // Auto-login after a short delay
        new Thread(() -> {
            try {
                Thread.sleep(1500);
                Platform.runLater(() -> {
                    try {
                        // Get the current scene's controller
                        Scene scene = primaryStage.getScene();
                        if (scene != null) {
                            FXMLLoader loader = (FXMLLoader) scene.getUserData();
                            if (loader != null) {
                                Object controller = loader.getController();
                                if (controller instanceof LoginController) {
                                    ((LoginController) controller).simulateAutoLogin("Topu");
                                    System.out.println("Auto-login initiated for user: Topu");
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("Auto-login failed: " + e.getMessage());
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void showConnectionInfo(int actualPort) {
        String info =
                "SERVER CONNECTION INFORMATION\n" +
                        "===============================\n\n" +
                        "For clients to connect, use:\n\n" +
                        "IP Address: " + SERVER_IP + "\n" +
                        "Port: " + actualPort + "\n\n" +
                        "Connection URL: ws://" + SERVER_IP + ":" + actualPort + "\n\n" +
                        "Make sure:\n" +
                        "• Firewall allows port " + actualPort + "\n" +
                        "• All devices are on same network\n" +
                        "• Network profile is set to Private\n";

        showAlert(Alert.AlertType.INFORMATION, "Server Ready - Connection Info", info);
    }

    private String scanLocalNetwork() {
        StringBuilder result = new StringBuilder();
        result.append("Network Interface Information:\n\n");

        try {
            java.net.NetworkInterface.getNetworkInterfaces().asIterator()
                    .forEachRemaining(ni -> {
                        try {
                            if (!ni.isLoopback() && ni.isUp()) {
                                result.append("Interface: ").append(ni.getDisplayName()).append("\n");
                                ni.getInterfaceAddresses().forEach(addr -> {
                                    if (addr.getAddress() instanceof java.net.Inet4Address) {
                                        result.append("  IPv4: ").append(addr.getAddress().getHostAddress()).append("\n");
                                    }
                                });
                                result.append("\n");
                            }
                        } catch (Exception e) {
                            // Ignore
                        }
                    });
        } catch (Exception e) {
            result.append("Error scanning network: ").append(e.getMessage());
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
            alert.showAndWait();
        });
    }

    @Override
    public void stop() throws Exception {
        SimpleNativeWebSocketServer server = SimpleNativeWebSocketServer.getInstance();
        if (server != null && server.isRunning()) {
            System.out.println("Stopping WebSocket server...");
            server.stop();
        }
        super.stop();
        System.out.println("Application stopped successfully");
    }

    public static void main(String[] args) {
        System.out.println("Starting Device 1 - HOST DEVICE (Manual Configuration)");
        launch(args);
    }
}