package org.example.zoom;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import org.example.zoom.websocket.SimpleWebSocketClient;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class ServerConfigDialog {

    @FXML
    private TextField serverIpField;

    @FXML
    private TextField serverPortField;

    @FXML
    private Label statusLabel;

    @FXML
    private Button testButton;

    @FXML
    private Button connectButton;

    @FXML
    private Button cancelButton;

    private Stage stage;
    private boolean connected = false;
    private String finalServerUrl;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    public void initialize() {
        // Load current settings
        serverIpField.setText(HelloApplication.getServerIp());
        serverPortField.setText(HelloApplication.getServerPort());

        // Add input validation
        serverPortField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                serverPortField.setText(newValue.replaceAll("[^\\d]", ""));
            }
        });

        // Enable/disable connect button based on input
        serverIpField.textProperty().addListener((obs, oldVal, newVal) -> updateConnectButton());
        serverPortField.textProperty().addListener((obs, oldVal, newVal) -> updateConnectButton());

        updateConnectButton();
    }

    private void updateConnectButton() {
        boolean hasInput = !serverIpField.getText().trim().isEmpty() &&
                !serverPortField.getText().trim().isEmpty();
        connectButton.setDisable(!hasInput);
    }

    @FXML
    protected void onTestConnection() {
        String ip = serverIpField.getText().trim();
        String port = serverPortField.getText().trim();

        if (ip.isEmpty() || port.isEmpty()) {
            statusLabel.setText("❌ Please enter both IP and port");
            statusLabel.setStyle("-fx-text-fill: #e74c3c;");
            return;
        }

        testButton.setDisable(true);
        testButton.setText("Testing...");
        statusLabel.setText("Testing connection...");
        statusLabel.setStyle("-fx-text-fill: #f39c12;");

        new Thread(() -> {
            boolean success = testWebSocketConnection(ip, port);

            javafx.application.Platform.runLater(() -> {
                if (success) {
                    statusLabel.setText("✅ Connection successful!");
                    statusLabel.setStyle("-fx-text-fill: #27ae60;");
                    connectButton.setDisable(false);
                } else {
                    statusLabel.setText("❌ Connection failed!");
                    statusLabel.setStyle("-fx-text-fill: #e74c3c;");
                }

                testButton.setDisable(false);
                testButton.setText("Test Connection");
            });
        }).start();
    }

    @FXML
    protected void onConnect() {
        String ip = serverIpField.getText().trim();
        String port = serverPortField.getText().trim();

        if (ip.isEmpty() || port.isEmpty()) {
            statusLabel.setText("❌ Please enter both IP and port");
            statusLabel.setStyle("-fx-text-fill: #e74c3c;");
            return;
        }

        connectButton.setDisable(true);
        connectButton.setText("Connecting...");
        statusLabel.setText("Connecting to server...");
        statusLabel.setStyle("-fx-text-fill: #f39c12;");

        new Thread(() -> {
            boolean success = testWebSocketConnection(ip, port);

            javafx.application.Platform.runLater(() -> {
                if (success) {
                    // Save the configuration
                    HelloApplication.setServerConfig(ip, port);

                    // Reconnect with new settings
                    String newUrl = "ws://" + ip + ":" + port;
                    HelloApplication.reinitializeWebSocket(newUrl);

                    statusLabel.setText("✅ Connected successfully!");
                    statusLabel.setStyle("-fx-text-fill: #27ae60;");
                    connected = true;
                    finalServerUrl = newUrl;

                    // Close dialog after short delay
                    new Thread(() -> {
                        try {
                            Thread.sleep(1000);
                            javafx.application.Platform.runLater(() -> stage.close());
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }).start();
                } else {
                    statusLabel.setText("❌ Failed to connect!");
                    statusLabel.setStyle("-fx-text-fill: #e74c3c;");
                    connectButton.setDisable(false);
                    connectButton.setText("Connect");
                }
            });
        }).start();
    }

    @FXML
    protected void onCancel() {
        connected = false;
        stage.close();
    }

    private boolean testWebSocketConnection(String ip, String port) {
        try {
            String testUrl = "ws://" + ip + ":" + port;
            System.out.println("Testing connection to: " + testUrl);

            final boolean[] connectionSuccess = {false};
            final CountDownLatch latch = new CountDownLatch(1);

            SimpleWebSocketClient testClient = new SimpleWebSocketClient(testUrl, message -> {
                System.out.println("Test connection message: " + message);
                if (message.contains("Connected") || message.contains("Welcome")) {
                    connectionSuccess[0] = true;
                    latch.countDown();
                }
            });

            // Wait for connection result with timeout
            boolean connected = latch.await(5, TimeUnit.SECONDS);
            testClient.disconnect();

            return connectionSuccess[0];

        } catch (Exception e) {
            System.err.println("Connection test failed: " + e.getMessage());
            return false;
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public String getServerUrl() {
        return finalServerUrl;
    }

    // Static method to show the dialog
    public static ServerConfigDialog showDialog(Stage owner) {
        try {
            FXMLLoader loader = new FXMLLoader(ServerConfigDialog.class.getResource("server-config-dialog.fxml"));
            Scene scene = new Scene(loader.load(), 400, 300);

            Stage dialogStage = new Stage();
            dialogStage.setTitle("Server Configuration");
            dialogStage.initModality(Modality.WINDOW_MODAL);
            dialogStage.initOwner(owner);
            dialogStage.initStyle(StageStyle.UTILITY);
            dialogStage.setResizable(false);
            dialogStage.setScene(scene);

            ServerConfigDialog controller = loader.getController();
            controller.setStage(dialogStage);

            dialogStage.showAndWait();
            return controller;

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
}