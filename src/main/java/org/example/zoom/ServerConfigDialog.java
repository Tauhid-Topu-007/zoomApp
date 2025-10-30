package org.example.zoom;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.List;

public class ServerConfigDialog extends Dialog<Boolean> {

    private TextField ipField;
    private TextField portField;
    private Label statusLabel;
    private Button testButton;
    private boolean connected = false;

    public ServerConfigDialog(Stage owner) {
        setTitle("Server Configuration");
        setHeaderText("Configure WebSocket Server Connection");

        // Set the owner stage
        initOwner(owner);

        // Create the main content
        VBox content = createContent();
        getDialogPane().setContent(content);

        // Add buttons
        ButtonType connectButtonType = new ButtonType("Connect", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        getDialogPane().getButtonTypes().addAll(connectButtonType, cancelButtonType);

        // Enable/disable connect button based on validation
        Button connectButton = (Button) getDialogPane().lookupButton(connectButtonType);
        connectButton.setDisable(true);

        // Add validation
        ipField.textProperty().addListener((observable, oldValue, newValue) -> validateInput(connectButton));
        portField.textProperty().addListener((observable, oldValue, newValue) -> validateInput(connectButton));

        // Set result converter
        setResultConverter(dialogButton -> {
            if (dialogButton == connectButtonType) {
                // Save configuration and connect
                String ip = ipField.getText();
                String port = portField.getText();
                HelloApplication.setServerConfig(ip, port);
                connected = true;
                return true;
            }
            return false;
        });

        // Load saved configuration
        loadSavedConfig();

        // Show available IPs
        showAvailableIPs();
    }

    private VBox createContent() {
        VBox content = new VBox(15);
        content.setPadding(new Insets(20));
        content.setAlignment(Pos.CENTER);

        // Network information
        Label networkInfoLabel = new Label("🌐 Available Network IPs:");
        networkInfoLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        List<String> localIPs = HelloApplication.getLocalIPAddresses();
        VBox ipList = new VBox(5);
        if (localIPs.isEmpty()) {
            ipList.getChildren().add(new Label("No network interfaces found"));
        } else {
            for (String ip : localIPs) {
                Label ipLabel = new Label("• " + ip);
                ipLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-family: monospace;");

                // Make IP clickable to auto-fill
                ipLabel.setOnMouseClicked(e -> {
                    ipField.setText(ip);
                    validateInput(null);
                });
                ipLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-family: monospace; -fx-cursor: hand;");
                ipList.getChildren().add(ipLabel);
            }
        }

        // Server configuration form
        Label formLabel = new Label("Server Configuration:");
        formLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        GridPane form = new GridPane();
        form.setHgap(10);
        form.setVgap(10);
        form.setAlignment(Pos.CENTER);

        // IP Address field
        Label ipLabel = new Label("IP Address:");
        ipField = new TextField();
        ipField.setPromptText("e.g., 192.168.1.100 or localhost");
        ipField.setPrefWidth(200);

        // Port field
        Label portLabel = new Label("Port:");
        portField = new TextField();
        portField.setPromptText("e.g., 8887");
        portField.setPrefWidth(100);

        form.add(ipLabel, 0, 0);
        form.add(ipField, 1, 0);
        form.add(portLabel, 0, 1);
        form.add(portField, 1, 1);

        // Test connection section
        HBox testBox = new HBox(10);
        testBox.setAlignment(Pos.CENTER);

        testButton = new Button("Test Connection");
        testButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        testButton.setOnAction(e -> testConnection());

        statusLabel = new Label("Click 'Test Connection' to verify server");
        statusLabel.setStyle("-fx-text-fill: #7f8c8d;");

        testBox.getChildren().addAll(testButton, statusLabel);

        // Quick connect buttons
        Label quickConnectLabel = new Label("Quick Connect:");
        quickConnectLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2c3e50;");

        HBox quickConnectBox = new HBox(10);
        quickConnectBox.setAlignment(Pos.CENTER);

        Button localhostButton = new Button("Localhost");
        localhostButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white;");
        localhostButton.setOnAction(e -> {
            ipField.setText("localhost");
            portField.setText("8887");
            validateInput(null);
        });

        Button autoDiscoverButton = new Button("Auto Discover");
        autoDiscoverButton.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white;");
        autoDiscoverButton.setOnAction(e -> autoDiscoverServers());

        quickConnectBox.getChildren().addAll(localhostButton, autoDiscoverButton);

        // Add all components to content
        content.getChildren().addAll(
                networkInfoLabel,
                ipList,
                new Separator(),
                formLabel,
                form,
                testBox,
                new Separator(),
                quickConnectLabel,
                quickConnectBox
        );

        return content;
    }

    private void validateInput(Button connectButton) {
        String ip = ipField.getText();
        String port = portField.getText();

        boolean isValid = !ip.isEmpty() && !port.isEmpty() && port.matches("\\d+");

        if (connectButton != null) {
            connectButton.setDisable(!isValid);
        }

        // Visual feedback
        if (ip.isEmpty() || port.isEmpty()) {
            ipField.setStyle("");
            portField.setStyle("");
        } else if (isValid) {
            ipField.setStyle("-fx-border-color: #27ae60; -fx-border-width: 1px;");
            portField.setStyle("-fx-border-color: #27ae60; -fx-border-width: 1px;");
        } else {
            ipField.setStyle("-fx-border-color: #e74c3c; -fx-border-width: 1px;");
            portField.setStyle("-fx-border-color: #e74c3c; -fx-border-width: 1px;");
        }
    }

    private void testConnection() {
        String ip = ipField.getText();
        String port = portField.getText();
        String serverUrl = "ws://" + ip + ":" + port;

        testButton.setDisable(true);
        statusLabel.setText("🔄 Testing connection to " + serverUrl + "...");
        statusLabel.setStyle("-fx-text-fill: #f39c12;");

        new Thread(() -> {
            boolean success = HelloApplication.testConnection(serverUrl);

            javafx.application.Platform.runLater(() -> {
                testButton.setDisable(false);
                if (success) {
                    statusLabel.setText("✅ Connection successful!");
                    statusLabel.setStyle("-fx-text-fill: #27ae60;");
                    connected = true;
                } else {
                    statusLabel.setText("❌ Connection failed - Server not reachable");
                    statusLabel.setStyle("-fx-text-fill: #e74c3c;");
                    connected = false;
                }
            });
        }).start();
    }

    private void autoDiscoverServers() {
        statusLabel.setText("🔍 Discovering servers on network...");
        statusLabel.setStyle("-fx-text-fill: #f39c12;");
        testButton.setDisable(true);

        new Thread(() -> {
            List<String> availableServers = HelloApplication.discoverAvailableServers();

            javafx.application.Platform.runLater(() -> {
                testButton.setDisable(false);
                if (availableServers.isEmpty()) {
                    statusLabel.setText("❌ No servers found on network");
                    statusLabel.setStyle("-fx-text-fill: #e74c3c;");
                } else {
                    // Use the first available server
                    String firstServer = availableServers.get(0);
                    String[] parts = firstServer.split(":");
                    if (parts.length == 2) {
                        ipField.setText(parts[0]);
                        portField.setText(parts[1]);
                        statusLabel.setText("✅ Found server: " + firstServer);
                        statusLabel.setStyle("-fx-text-fill: #27ae60;");
                        validateInput(null);
                    }
                }
            });
        }).start();
    }

    private void loadSavedConfig() {
        String savedIp = HelloApplication.getServerIp();
        String savedPort = HelloApplication.getServerPort();

        if (savedIp != null) ipField.setText(savedIp);
        if (savedPort != null) portField.setText(savedPort);

        validateInput(null);
    }

    private void showAvailableIPs() {
        List<String> localIPs = HelloApplication.getLocalIPAddresses();
        System.out.println("🌐 Available IP addresses for sharing:");
        for (String ip : localIPs) {
            System.out.println("   - " + ip + ":8887");
        }
    }

    public static ServerConfigDialog showDialog(Stage owner) {
        ServerConfigDialog dialog = new ServerConfigDialog(owner);
        dialog.showAndWait();
        return dialog;
    }

    public boolean isConnected() {
        return connected;
    }

    public String getServerUrl() {
        return "ws://" + ipField.getText() + ":" + portField.getText();
    }
}