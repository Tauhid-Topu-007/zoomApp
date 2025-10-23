package org.example.zoom;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import org.example.zoom.websocket.SimpleWebSocketClient;

import java.util.List;

public class SettingsController {

    private String username; // current logged-in user
    private Stage stage;

    // Account Settings
    @FXML private TextField newUsernameField;

    // Password Change Fields
    @FXML private PasswordField currentPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmNewPasswordField;

    // Server Settings
    @FXML private TextField serverIpField;
    @FXML private TextField serverPortField;
    @FXML private Label connectionStatusLabel;
    @FXML private Label currentServerLabel;
    @FXML private Button testConnectionButton;
    @FXML private Button applyServerSettingsButton;

    // Message Labels
    @FXML private Label accountMessageLabel;
    @FXML private Label serverMessageLabel;

    // Server History
    @FXML private VBox serverHistoryContainer;

    // ScrollPane reference
    @FXML private ScrollPane mainScrollPane;

    // Main content container
    @FXML private VBox mainContentContainer;

    public void setUser(String username) {
        this.username = username;
        loadCurrentServerSettings();
        loadServerHistory();
        configureScrollPane();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    public void initialize() {
        configureScrollPane();
        loadCurrentServerSettings();

        // Add input validation for port
        serverPortField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.matches("\\d*")) {
                serverPortField.setText(newValue.replaceAll("[^\\d]", ""));
            }
        });

        // Add real-time password validation
        setupPasswordValidation();

        // Ensure content is visible
        ensureContentVisibility();
    }

    private void configureScrollPane() {
        if (mainScrollPane != null) {
            // CRITICAL FIX: Don't fit to height to allow scrolling
            mainScrollPane.setFitToWidth(true);
            mainScrollPane.setFitToHeight(false); // This is the key fix
            mainScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            mainScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            mainScrollPane.setStyle("-fx-background: transparent; -fx-background-color: transparent;");

            // Remove any padding/margin that might be cutting content
            mainScrollPane.setPadding(new javafx.geometry.Insets(0));
        }

        if (mainContentContainer != null) {
            // Allow the content to grow as needed
            mainContentContainer.setMinHeight(Region.USE_PREF_SIZE);
        }
    }

    private void ensureContentVisibility() {
        // Force layout update to ensure all content is visible
        if (mainScrollPane != null) {
            javafx.application.Platform.runLater(() -> {
                mainScrollPane.layout();
                scrollToTop();
            });
        }
    }

    private void setupPasswordValidation() {
        // Real-time validation for password fields
        newPasswordField.textProperty().addListener((observable, oldValue, newValue) -> {
            validatePasswordFields();
        });

        confirmNewPasswordField.textProperty().addListener((observable, oldValue, newValue) -> {
            validatePasswordFields();
        });

        // Also validate current password field
        currentPasswordField.textProperty().addListener((observable, oldValue, newValue) -> {
            validateCurrentPassword();
        });
    }

    private void validateCurrentPassword() {
        String currentPassword = currentPasswordField.getText();
        if (!currentPassword.isEmpty()) {
            if (Database.authenticateUser(username, currentPassword)) {
                currentPasswordField.setStyle("-fx-border-color: #27ae60; -fx-border-width: 2;");
            } else {
                currentPasswordField.setStyle("-fx-border-color: #e74c3c; -fx-border-width: 2;");
            }
        } else {
            currentPasswordField.setStyle("");
        }
    }

    private void validatePasswordFields() {
        String newPassword = newPasswordField.getText();
        String confirmPassword = confirmNewPasswordField.getText();

        // Validate new password
        if (!newPassword.isEmpty()) {
            if (newPassword.length() < 6) {
                newPasswordField.setStyle("-fx-border-color: #e74c3c; -fx-border-width: 2;");
            } else {
                newPasswordField.setStyle("-fx-border-color: #27ae60; -fx-border-width: 2;");
            }
        } else {
            newPasswordField.setStyle("");
        }

        // Validate password confirmation
        if (!confirmPassword.isEmpty() && !newPassword.isEmpty()) {
            if (newPassword.equals(confirmPassword)) {
                confirmNewPasswordField.setStyle("-fx-border-color: #27ae60; -fx-border-width: 2;");
            } else {
                confirmNewPasswordField.setStyle("-fx-border-color: #e74c3c; -fx-border-width: 2;");
            }
        } else {
            confirmNewPasswordField.setStyle("");
        }
    }

    // ========== ACCOUNT SETTINGS METHODS ==========

    @FXML
    protected void onChangeUsernameClick(ActionEvent event) {
        String newUsername = newUsernameField.getText().trim();

        if (newUsername.isEmpty()) {
            showAccountMessage("❌ Please enter a new username!", true);
            return;
        }

        if (newUsername.equals(username)) {
            showAccountMessage("⚠ New username is the same as current!", true);
            return;
        }

        // Check if username already exists
        if (Database.usernameExists(newUsername)) {
            showAccountMessage("⚠ Username already taken!", true);
            return;
        }

        if (Database.updateUsername(username, newUsername)) {
            showAccountMessage("✅ Username changed successfully!", false);
            HelloApplication.setLoggedInUser(newUsername); // update session
            username = newUsername; // update local
            newUsernameField.clear();
        } else {
            showAccountMessage("❌ Failed to update username!", true);
        }
    }

    @FXML
    protected void onChangePasswordClick(ActionEvent event) {
        String currentPassword = currentPasswordField.getText();
        String newPassword = newPasswordField.getText();
        String confirmPassword = confirmNewPasswordField.getText();

        // Reset all styles first
        resetPasswordFieldStyles();

        // Validate input
        if (currentPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
            showAccountMessage("❌ Please fill all password fields!", true);
            highlightEmptyFields(currentPassword, newPassword, confirmPassword);
            return;
        }

        // Verify current password
        if (!Database.authenticateUser(username, currentPassword)) {
            showAccountMessage("❌ Current password is incorrect!", true);
            currentPasswordField.setStyle("-fx-border-color: #e74c3c; -fx-border-width: 2;");
            return;
        }

        // Check password length
        if (newPassword.length() < 6) {
            showAccountMessage("❌ New password must be at least 6 characters!", true);
            newPasswordField.setStyle("-fx-border-color: #e74c3c; -fx-border-width: 2;");
            return;
        }

        // Check if passwords match
        if (!newPassword.equals(confirmPassword)) {
            showAccountMessage("❌ New passwords do not match!", true);
            newPasswordField.setStyle("-fx-border-color: #e74c3c; -fx-border-width: 2;");
            confirmNewPasswordField.setStyle("-fx-border-color: #e74c3c; -fx-border-width: 2;");
            return;
        }

        // Check if new password is different from current
        if (currentPassword.equals(newPassword)) {
            showAccountMessage("❌ New password must be different from current password!", true);
            newPasswordField.setStyle("-fx-border-color: #e74c3c; -fx-border-width: 2;");
            return;
        }

        // Update password in database
        if (Database.updatePassword(username, newPassword)) {
            showAccountMessage("✅ Password updated successfully!", false);
            clearPasswordFields();
            setPasswordFieldSuccessStyles();
        } else {
            showAccountMessage("❌ Failed to update password! Please try again.", true);
        }
    }

    private void showAccountMessage(String message, boolean isError) {
        accountMessageLabel.setText(message);
        if (isError) {
            accountMessageLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
        } else {
            accountMessageLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
        }
    }

    private void resetPasswordFieldStyles() {
        currentPasswordField.setStyle("");
        newPasswordField.setStyle("");
        confirmNewPasswordField.setStyle("");
    }

    private void setPasswordFieldSuccessStyles() {
        currentPasswordField.setStyle("-fx-border-color: #27ae60; -fx-border-width: 2;");
        newPasswordField.setStyle("-fx-border-color: #27ae60; -fx-border-width: 2;");
        confirmNewPasswordField.setStyle("-fx-border-color: #27ae60; -fx-border-width: 2;");
    }

    private void highlightEmptyFields(String currentPassword, String newPassword, String confirmPassword) {
        if (currentPassword.isEmpty()) {
            currentPasswordField.setStyle("-fx-border-color: #e74c3c; -fx-border-width: 2;");
        }
        if (newPassword.isEmpty()) {
            newPasswordField.setStyle("-fx-border-color: #e74c3c; -fx-border-width: 2;");
        }
        if (confirmPassword.isEmpty()) {
            confirmNewPasswordField.setStyle("-fx-border-color: #e74c3c; -fx-border-width: 2;");
        }
    }

    @FXML
    protected void onClearPasswordFieldsClick(ActionEvent event) {
        clearPasswordFields();
        accountMessageLabel.setText("");
    }

    private void clearPasswordFields() {
        currentPasswordField.clear();
        newPasswordField.clear();
        confirmNewPasswordField.clear();
        resetPasswordFieldStyles();
    }

    @FXML
    protected void onShowPasswordClick(ActionEvent event) {
        togglePasswordVisibility();
    }

    private void togglePasswordVisibility() {
        // Simple implementation - you can enhance this with actual visibility toggle
        showAccountMessage("👁️ Password visibility: Currently showing as dots for security", false);
    }

    // ========== SERVER SETTINGS METHODS ==========

    private void loadCurrentServerSettings() {
        // Load saved settings or defaults
        String savedIp = HelloApplication.getServerIp();
        String savedPort = HelloApplication.getServerPort();

        serverIpField.setText(savedIp);
        serverPortField.setText(savedPort);

        updateConnectionStatus();
    }

    @FXML
    protected void onTestConnectionClick(ActionEvent event) {
        String ip = serverIpField.getText().trim();
        String port = serverPortField.getText().trim();

        if (ip.isEmpty() || port.isEmpty()) {
            showServerMessage("❌ Please enter both IP address and port.", true);
            return;
        }

        testConnectionButton.setDisable(true);
        testConnectionButton.setText("Testing...");
        showServerMessage("Testing connection to " + ip + ":" + port + "...", false);

        // Test connection in a separate thread
        new Thread(() -> {
            boolean success = testWebSocketConnection(ip, port);

            javafx.application.Platform.runLater(() -> {
                if (success) {
                    showServerMessage("✅ Connection successful to " + ip + ":" + port, false);
                } else {
                    showServerMessage("❌ Connection failed to " + ip + ":" + port, true);
                }

                testConnectionButton.setDisable(false);
                testConnectionButton.setText("Test Connection");
            });
        }).start();
    }

    @FXML
    protected void onApplyServerSettingsClick(ActionEvent event) {
        String ip = serverIpField.getText().trim();
        String port = serverPortField.getText().trim();

        if (ip.isEmpty() || port.isEmpty()) {
            showServerMessage("❌ Please enter both IP address and port.", true);
            return;
        }

        // Save settings
        HelloApplication.setServerConfig(ip, port);

        // Reconnect with new settings
        reconnectWithNewSettings(ip, port);

        showServerMessage("✅ Settings applied! Now using: " + ip + ":" + port, false);

        // Reload server history to show the new connection
        loadServerHistory();
    }

    @FXML
    protected void onResetServerSettingsClick(ActionEvent event) {
        serverIpField.setText("localhost");
        serverPortField.setText("8887");
        loadCurrentServerSettings();
        showServerMessage("✅ Settings reset to defaults", false);
    }

    private void showServerMessage(String message, boolean isError) {
        serverMessageLabel.setText(message);
        if (isError) {
            serverMessageLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
        } else {
            serverMessageLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
        }
    }

    private boolean testWebSocketConnection(String ip, String port) {
        try {
            String testUrl = "ws://" + ip + ":" + port;
            System.out.println("Testing connection to: " + testUrl);

            final boolean[] connectionSuccess = {false};
            final Object lock = new Object();

            SimpleWebSocketClient testClient = new SimpleWebSocketClient(testUrl, message -> {
                System.out.println("Test connection message: " + message);
                if (message.contains("Connected") || message.contains("Welcome")) {
                    synchronized (lock) {
                        connectionSuccess[0] = true;
                        lock.notifyAll();
                    }
                }
            });

            // Wait for connection result
            synchronized (lock) {
                lock.wait(5000); // Wait up to 5 seconds
            }

            testClient.disconnect();
            return connectionSuccess[0];

        } catch (Exception e) {
            System.err.println("Connection test failed: " + e.getMessage());
            return false;
        }
    }

    private void reconnectWithNewSettings(String ip, String port) {
        // Disconnect current client
        SimpleWebSocketClient oldClient = HelloApplication.getWebSocketClient();
        if (oldClient != null) {
            oldClient.disconnect();
        }

        // Create new client with new settings
        String newUrl = "ws://" + ip + ":" + port;
        HelloApplication.reinitializeWebSocket(newUrl);

        updateConnectionStatus();
    }

    private void updateConnectionStatus() {
        SimpleWebSocketClient client = HelloApplication.getWebSocketClient();
        if (client != null && client.isConnected()) {
            currentServerLabel.setText("🟢 Connected to: " + HelloApplication.getCurrentServerUrl());
            currentServerLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
        } else {
            currentServerLabel.setText("🔴 Not connected");
            currentServerLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
        }
    }

    // ========== SERVER HISTORY METHODS ==========

    @FXML
    protected void onClearServerHistoryClick(ActionEvent event) {
        // Clear server history from database
        if (username != null) {
            List<Database.ServerConfig> history = HelloApplication.getServerHistory();
            for (Database.ServerConfig config : history) {
                Database.deleteServerConfig(username, config.getServerIp(), config.getServerPort());
            }
            loadServerHistory();
            showServerMessage("✅ Server history cleared!", false);
        }
    }

    private void loadServerHistory() {
        if (serverHistoryContainer != null && username != null) {
            serverHistoryContainer.getChildren().clear();

            List<Database.ServerConfig> history = HelloApplication.getServerHistory();

            if (history.isEmpty()) {
                Label noHistoryLabel = new Label("No server history available");
                noHistoryLabel.setStyle("-fx-text-fill: #bdc3c7; -fx-font-style: italic;");
                serverHistoryContainer.getChildren().add(noHistoryLabel);
            } else {
                for (Database.ServerConfig config : history) {
                    HBox historyItem = createServerHistoryItem(config);
                    serverHistoryContainer.getChildren().add(historyItem);
                }
            }
        }
    }

    private HBox createServerHistoryItem(Database.ServerConfig config) {
        HBox item = new HBox(10);
        item.setStyle("-fx-padding: 8; -fx-background-color: #2c3e50; -fx-background-radius: 5;");

        Label serverLabel = new Label(config.getServerIp() + ":" + config.getServerPort());
        serverLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold;");

        Button useButton = new Button("Use");
        useButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold;");
        useButton.setOnAction(e -> useServerConfig(config));

        Button removeButton = new Button("Remove");
        removeButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;");
        removeButton.setOnAction(e -> removeServerConfig(config));

        item.getChildren().addAll(serverLabel, useButton, removeButton);
        return item;
    }

    private void useServerConfig(Database.ServerConfig config) {
        serverIpField.setText(config.getServerIp());
        serverPortField.setText(config.getServerPort());
        showServerMessage("✅ Server config loaded: " + config.getServerIp() + ":" + config.getServerPort(), false);
    }

    private void removeServerConfig(Database.ServerConfig config) {
        if (username != null) {
            Database.deleteServerConfig(username, config.getServerIp(), config.getServerPort());
            loadServerHistory();
            showServerMessage("✅ Server config removed: " + config.getServerIp() + ":" + config.getServerPort(), false);
        }
    }

    // ========== NAVIGATION METHODS ==========

    @FXML
    protected void onBackClick(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("dashboard-view.fxml"));
            Scene scene = new Scene(loader.load(), 900, 600);

            Stage stage = (Stage) accountMessageLabel.getScene().getWindow();
            stage.setScene(scene);

        } catch (Exception e) {
            e.printStackTrace();
            showAccountMessage("❌ Error returning to dashboard: " + e.getMessage(), true);
        }
    }

    @FXML
    protected void onClearMessagesClick(ActionEvent event) {
        accountMessageLabel.setText("");
        serverMessageLabel.setText("");
    }

    // Method to scroll to top
    public void scrollToTop() {
        if (mainScrollPane != null) {
            mainScrollPane.setVvalue(0);
        }
    }

    // Method to scroll to bottom
    public void scrollToBottom() {
        if (mainScrollPane != null) {
            mainScrollPane.setVvalue(1);
        }
    }
}