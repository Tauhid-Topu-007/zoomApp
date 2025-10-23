package org.example.zoom;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.stage.Stage;
import org.example.zoom.websocket.SimpleWebSocketClient;

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
    private CheckBox rememberMe;

    @FXML
    public void initialize() {
        // Update connection status when the login screen loads
        updateConnectionStatus();

        // Load saved username if remember me was checked
        loadSavedCredentials();
    }

    @FXML
    protected void onLoginClick(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.isEmpty() || password.isEmpty()) {
            messageLabel.setText("❌ Please enter both username and password!");
            return;
        }

        // Don't test connection before login - let it handle connection after login
        // This fixes the issue where WebSocket tries to connect before user is set

        if (Database.authenticateUser(username, password)) {
            // Save credentials if remember me is checked
            if (rememberMe.isSelected()) {
                saveCredentials(username, password);
            } else {
                clearSavedCredentials();
            }

            messageLabel.setText("✅ Login successful!");

            try {
                // Store logged in user globally - this will initialize WebSocket
                HelloApplication.setLoggedInUser(username);

                // Navigate to dashboard
                HelloApplication.setRoot("dashboard-view.fxml");

            } catch (Exception e) {
                e.printStackTrace();
                messageLabel.setText("❌ Failed to load dashboard: " + e.getMessage());
            }

        } else {
            messageLabel.setText("❌ Invalid username or password!");
        }
    }

    @FXML
    protected void onRegisterClick(ActionEvent event) {
        try {
            HelloApplication.setRoot("register-view.fxml");
        } catch (Exception e) {
            e.printStackTrace();
            messageLabel.setText("❌ Failed to open registration!");
        }
    }

    @FXML
    protected void onServerConfigClick(ActionEvent event) {
        // Show server configuration dialog
        Stage currentStage = (Stage) messageLabel.getScene().getWindow();
        ServerConfigDialog dialog = ServerConfigDialog.showDialog(currentStage);

        if (dialog != null && dialog.isConnected()) {
            updateConnectionStatus();
            messageLabel.setText("✅ Server configuration updated!");
        } else {
            updateConnectionStatus();
            messageLabel.setText("⚠️ Server configuration cancelled.");
        }
    }

    @FXML
    protected void onTestConnectionClick(ActionEvent event) {
        messageLabel.setText("🔄 Testing connection...");

        // Test connection without affecting the main WebSocket client
        new Thread(() -> {
            boolean success = testConnectionWithoutLogin();

            javafx.application.Platform.runLater(() -> {
                if (success) {
                    messageLabel.setText("✅ Connection test successful!");
                } else {
                    messageLabel.setText("❌ Connection test failed!");
                }
                updateConnectionStatus();
            });
        }).start();
    }

    @FXML
    protected void onQuickLocalhostClick(ActionEvent event) {
        // Quick connect to localhost - just update config, don't initialize WebSocket yet
        HelloApplication.setServerConfig("localhost", "8887");
        updateConnectionStatus();
        messageLabel.setText("✅ Localhost configuration set!");

        // Test the connection
        onTestConnectionClick(event);
    }

    @FXML
    protected void onQuickNetworkClick(ActionEvent event) {
        // Show a prompt to enter network IP
        TextInputDialog dialog = new TextInputDialog("192.168.1.");
        dialog.setTitle("Network Server");
        dialog.setHeaderText("Enter Network Server IP");
        dialog.setContentText("IP Address:");

        dialog.showAndWait().ifPresent(ip -> {
            if (!ip.isEmpty()) {
                HelloApplication.setServerConfig(ip, "8887");
                updateConnectionStatus();
                messageLabel.setText("✅ Network configuration set: " + ip);

                // Test the connection
                onTestConnectionClick(event);
            }
        });
    }

    private void updateConnectionStatus() {
        if (connectionStatusLabel != null && serverInfoLabel != null) {
            String serverUrl = HelloApplication.getCurrentServerUrl();

            // For login screen, we can't use HelloApplication.isWebSocketConnected()
            // because WebSocket isn't initialized until after login
            // So we'll just show the configured server
            connectionStatusLabel.setText("⚙️ Configured");
            connectionStatusLabel.setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;");

            serverInfoLabel.setText(serverUrl.replace("ws://", ""));
        }
    }

    private boolean testConnectionWithoutLogin() {
        try {
            String testUrl = HelloApplication.getCurrentServerUrl();
            System.out.println("Testing connection to: " + testUrl);

            final boolean[] connectionSuccess = {false};
            final Object lock = new Object();

            // Create a temporary test client
            SimpleWebSocketClient testClient = new SimpleWebSocketClient(testUrl, message -> {
                System.out.println("Test connection received: " + message);
                if (message.contains("Connected") || message.contains("Welcome")) {
                    synchronized (lock) {
                        connectionSuccess[0] = true;
                        lock.notifyAll();
                    }
                }
            });

            // Wait for connection result
            synchronized (lock) {
                try {
                    lock.wait(3000); // Wait up to 3 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // Disconnect the test client
            testClient.disconnect();
            return connectionSuccess[0];

        } catch (Exception e) {
            System.err.println("Connection test failed: " + e.getMessage());
            return false;
        }
    }

    private boolean testCurrentConnection() {
        // This method is not safe to use before login
        // Use testConnectionWithoutLogin() instead
        return false;
    }

    private void loadSavedCredentials() {
        // In a real app, load from secure storage
        // For demo, we'll just clear the fields
        usernameField.setText("");
        passwordField.setText("");

        // Try to load remembered username
        String rememberedUser = HelloApplication.getUserPreference("remembered_username");
        if (rememberedUser != null && !rememberedUser.isEmpty()) {
            usernameField.setText(rememberedUser);
            rememberMe.setSelected(true);
        }
    }

    private void saveCredentials(String username, String password) {
        // In a real app, save to secure storage
        System.out.println("💾 Saving credentials for: " + username);

        if (rememberMe.isSelected()) {
            HelloApplication.saveUserPreference("remembered_username", username);
        } else {
            HelloApplication.saveUserPreference("remembered_username", "");
        }
    }

    private void clearSavedCredentials() {
        // In a real app, clear from secure storage
        System.out.println("🗑️ Clearing saved credentials");
        HelloApplication.saveUserPreference("remembered_username", "");
    }
}