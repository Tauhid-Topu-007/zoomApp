package org.example.zoom;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.application.Platform;
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
    private ScrollPane mainScrollPane;

    private SimpleWebSocketClient testClient;

    @FXML
    public void initialize() {
        // Configure scroll pane
        if (mainScrollPane != null) {
            mainScrollPane.setFitToWidth(true);
            mainScrollPane.setFitToHeight(true);
            mainScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            mainScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            mainScrollPane.setStyle("-fx-background: #2c3e50; -fx-border-color: #2c3e50;");
        }

        // Update connection status when the login screen loads
        updateConnectionStatus();

        // Load saved username if remember me was checked
        loadSavedCredentials();

        // Clear any previous error messages
        clearErrorMessage();
    }

    @FXML
    protected void onLoginClick(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        // Clear previous messages
        clearErrorMessage();

        if (username.isEmpty() || password.isEmpty()) {
            showErrorMessage("❌ Please enter both username and password!");
            shakeLoginForm();
            return;
        }

        // Show loading state
        setLoadingState(true);
        messageLabel.setText("🔄 Authenticating...");

        // Run authentication in background thread to prevent UI freezing
        new Thread(() -> {
            boolean isAuthenticated = Database.authenticateUser(username, password);

            Platform.runLater(() -> {
                setLoadingState(false);

                if (isAuthenticated) {
                    handleSuccessfulLogin(username, password);
                } else {
                    handleFailedLogin();
                }
            });
        }).start();
    }

    private void handleSuccessfulLogin(String username, String password) {
        // Save credentials if remember me is checked
        if (rememberMe.isSelected()) {
            saveCredentials(username, password);
        } else {
            clearSavedCredentials();
        }

        showSuccessMessage("✅ Login successful! Connecting to server...");

        try {
            // Initialize WebSocket connection BEFORE setting the logged-in user
            initializeWebSocketConnection(username);

        } catch (Exception e) {
            e.printStackTrace();
            showErrorMessage("❌ Login error: " + e.getMessage());
        }
    }

    private void initializeWebSocketConnection(String username) {
        new Thread(() -> {
            try {
                // Get current server configuration
                String serverUrl = HelloApplication.getCurrentServerUrl();
                System.out.println("🔗 Initializing WebSocket connection to: " + serverUrl);

                // Create and initialize WebSocket client
                SimpleWebSocketClient client = new SimpleWebSocketClient(serverUrl, message -> {
                    System.out.println("📨 WebSocket message during login: " + message);

                    Platform.runLater(() -> {
                        if (message.contains("Connected") || message.contains("Welcome")) {
                            // Connection successful - complete login process
                            completeLoginProcess(username);
                        } else if (message.contains("ERROR") || message.contains("Failed")) {
                            // Connection failed but allow login in offline mode
                            showErrorMessage("⚠️ Server connection failed, but you can continue in offline mode");
                            completeLoginProcess(username);
                        }
                    });
                });

                // Wait for connection with timeout
                Thread.sleep(2000); // Wait 2 seconds for connection

                Platform.runLater(() -> {
                    if (client.isConnected()) {
                        showSuccessMessage("✅ Connected to server! Redirecting...");
                    } else {
                        showErrorMessage("⚠️ Cannot connect to server, but you can continue in offline mode");
                    }
                    // Complete login regardless of connection status
                    completeLoginProcess(username);
                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    showErrorMessage("⚠️ Server connection issue, but you can continue in offline mode");
                    completeLoginProcess(username);
                });
            }
        }).start();
    }

    private void completeLoginProcess(String username) {
        // Store logged in user globally - this maintains the WebSocket connection
        HelloApplication.setLoggedInUser(username);

        // Small delay to show success message
        new Thread(() -> {
            try {
                Thread.sleep(1000); // 1 second delay
                Platform.runLater(() -> {
                    try {
                        // Navigate to dashboard
                        HelloApplication.setRoot("dashboard-view.fxml");
                    } catch (Exception e) {
                        e.printStackTrace();
                        showErrorMessage("❌ Failed to load dashboard: " + e.getMessage());
                    }
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void handleFailedLogin() {
        showErrorMessage("❌ Invalid username or password!");
        shakeLoginForm();
        clearPasswordField();

        // Additional feedback
        highlightInvalidFields();
    }

    private void highlightInvalidFields() {
        // Add temporary red border to indicate error
        String errorStyle = "-fx-border-color: #e74c3c; -fx-border-width: 2px; -fx-border-radius: 5px;";

        usernameField.setStyle(errorStyle);
        passwordField.setStyle(errorStyle);

        // Remove error styling after 3 seconds
        new Thread(() -> {
            try {
                Thread.sleep(3000);
                Platform.runLater(() -> {
                    String normalStyle = "-fx-border-color: #bdc3c7; -fx-border-width: 1px; -fx-border-radius: 5px;";
                    usernameField.setStyle(normalStyle);
                    passwordField.setStyle(normalStyle);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void shakeLoginForm() {
        // Create a simple shake animation for the form
        String shakeAnimation =
                "-fx-translate-x: 0; " +
                        "-fx-effect: dropshadow(three-pass-box, rgba(231,76,60,0.4), 10, 0, 0, 0);";

        // Apply shake effect
        usernameField.setStyle(shakeAnimation);
        passwordField.setStyle(shakeAnimation);

        // Remove effect after animation
        new Thread(() -> {
            try {
                Thread.sleep(500);
                Platform.runLater(() -> {
                    String normalStyle = "-fx-translate-x: 0; -fx-effect: null;";
                    usernameField.setStyle(normalStyle);
                    passwordField.setStyle(normalStyle);
                });
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void setLoadingState(boolean isLoading) {
        if (isLoading) {
            usernameField.setDisable(true);
            passwordField.setDisable(true);
            rememberMe.setDisable(true);
        } else {
            usernameField.setDisable(false);
            passwordField.setDisable(false);
            rememberMe.setDisable(false);
        }
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
            messageLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
        }
    }

    private void showSuccessMessage(String message) {
        if (messageLabel != null) {
            messageLabel.setText(message);
            messageLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
        }
    }

    private void clearPasswordField() {
        passwordField.clear();
    }

    @FXML
    protected void onRegisterClick(ActionEvent event) {
        try {
            HelloApplication.setRoot("register-view.fxml");
        } catch (Exception e) {
            e.printStackTrace();
            showErrorMessage("❌ Failed to open registration!");
        }
    }

    // NEW: Fully functional Forgot Password implementation
    @FXML
    protected void onForgotPasswordClick(ActionEvent event) {
        // Show dialog to enter username for password reset
        TextInputDialog usernameDialog = new TextInputDialog();
        usernameDialog.setTitle("Password Reset");
        usernameDialog.setHeaderText("Reset Your Password");
        usernameDialog.setContentText("Enter your username:");

        usernameDialog.showAndWait().ifPresent(username -> {
            if (username.isEmpty()) {
                showErrorMessage("❌ Please enter a username!");
                return;
            }

            // Check if username exists
            if (!Database.usernameExists(username)) {
                showErrorMessage("❌ Username not found!");
                return;
            }

            // Show security question or direct password reset
            showPasswordResetDialog(username);
        });
    }

    private void showPasswordResetDialog(String username) {
        // Create a custom password reset dialog
        Dialog<ButtonType> resetDialog = new Dialog<>();
        resetDialog.setTitle("Reset Password");
        resetDialog.setHeaderText("Reset Password for: " + username);

        // Create the password fields
        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText("New Password");
        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Confirm New Password");

        VBox content = new VBox(10);
        content.getChildren().addAll(
                new Label("New Password:"),
                newPasswordField,
                new Label("Confirm Password:"),
                confirmPasswordField
        );

        resetDialog.getDialogPane().setContent(content);

        // Add buttons
        ButtonType resetButtonType = new ButtonType("Reset Password", ButtonBar.ButtonData.OK_DONE);
        resetDialog.getDialogPane().getButtonTypes().addAll(resetButtonType, ButtonType.CANCEL);

        // Enable/disable reset button based on validation
        Button resetButton = (Button) resetDialog.getDialogPane().lookupButton(resetButtonType);
        resetButton.setDisable(true);

        // Add validation
        newPasswordField.textProperty().addListener((observable, oldValue, newValue) -> validatePasswords(newPasswordField, confirmPasswordField, resetButton));
        confirmPasswordField.textProperty().addListener((observable, oldValue, newValue) -> validatePasswords(newPasswordField, confirmPasswordField, resetButton));

        // Show dialog and handle result
        resetDialog.showAndWait().ifPresent(result -> {
            if (result == resetButtonType) {
                String newPassword = newPasswordField.getText();
                String confirmPassword = confirmPasswordField.getText();

                if (newPassword.equals(confirmPassword)) {
                    // Update password in database
                    boolean success = Database.updatePassword(username, newPassword);
                    if (success) {
                        showSuccessMessage("✅ Password reset successfully! You can now login with your new password.");

                        // Clear fields
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

        // Visual feedback
        if (!pass2.isEmpty()) {
            if (pass1.equals(pass2)) {
                confirmPassword.setStyle("-fx-border-color: #27ae60; -fx-border-width: 2px;");
            } else {
                confirmPassword.setStyle("-fx-border-color: #e74c3c; -fx-border-width: 2px;");
            }
        } else {
            confirmPassword.setStyle("");
        }
    }

    @FXML
    protected void onServerConfigClick(ActionEvent event) {
        // Show server configuration dialog
        Stage currentStage = (Stage) messageLabel.getScene().getWindow();
        ServerConfigDialog dialog = ServerConfigDialog.showDialog(currentStage);

        if (dialog != null && dialog.isConnected()) {
            updateConnectionStatus();
            showSuccessMessage("✅ Server configuration updated!");
        } else {
            updateConnectionStatus();
            showErrorMessage("⚠️ Server configuration cancelled.");
        }
    }

    @FXML
    protected void onTestConnectionClick(ActionEvent event) {
        showSuccessMessage("🔄 Testing connection...");

        // Test connection without affecting the main WebSocket client
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

    @FXML
    protected void onQuickLocalhostClick(ActionEvent event) {
        // Quick connect to localhost - just update config, don't initialize WebSocket yet
        HelloApplication.setServerConfig("localhost", "8887");
        updateConnectionStatus();
        showSuccessMessage("✅ Localhost configuration set!");

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
                showSuccessMessage("✅ Network configuration set: " + ip);

                // Test the connection
                onTestConnectionClick(event);
            }
        });
    }

    private void updateConnectionStatus() {
        if (connectionStatusLabel != null && serverInfoLabel != null) {
            String serverUrl = HelloApplication.getCurrentServerUrl();
            boolean isConnected = testConnectionQuick();

            if (isConnected) {
                connectionStatusLabel.setText("🟢 Connected");
                connectionStatusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
            } else {
                connectionStatusLabel.setText("🔴 Disconnected");
                connectionStatusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
            }

            serverInfoLabel.setText(serverUrl.replace("ws://", ""));
        }
    }

    private boolean testConnectionQuick() {
        try {
            String testUrl = HelloApplication.getCurrentServerUrl();
            final boolean[] connectionSuccess = {false};
            final Object lock = new Object();

            // Create a temporary test client
            SimpleWebSocketClient testClient = new SimpleWebSocketClient(testUrl, message -> {
                System.out.println("Quick test received: " + message);
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
                    lock.wait(2000); // Wait up to 2 seconds
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }

            // Disconnect the test client
            testClient.disconnect();
            return connectionSuccess[0];

        } catch (Exception e) {
            System.err.println("Quick connection test failed: " + e.getMessage());
            return false;
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

    private void loadSavedCredentials() {
        // Load remembered username from preferences
        String rememberedUser = HelloApplication.getUserPreference("remembered_username");
        if (rememberedUser != null && !rememberedUser.isEmpty()) {
            usernameField.setText(rememberedUser);
            rememberMe.setSelected(true);

            // Auto-focus on password field if username is remembered
            Platform.runLater(() -> passwordField.requestFocus());
        }
    }

    private void saveCredentials(String username, String password) {
        // Save username to preferences
        HelloApplication.saveUserPreference("remembered_username", username);

        // In a secure application, you might want to encrypt and save the password
        // For demo purposes, we're only saving the username
        System.out.println("💾 Saved credentials for: " + username);

        // Show confirmation
        showSuccessMessage("✅ Login credentials saved!");
    }

    private void clearSavedCredentials() {
        // Clear saved credentials from preferences
        HelloApplication.saveUserPreference("remembered_username", "");
        System.out.println("🗑️ Cleared saved credentials");
    }

    // Additional helper methods for better UX
    @FXML
    protected void onUsernameEnter(ActionEvent event) {
        // When user presses Enter in username field, move to password field
        passwordField.requestFocus();
    }

    @FXML
    protected void onPasswordEnter(ActionEvent event) {
        // When user presses Enter in password field, trigger login
        onLoginClick(event);
    }

    // NEW: Handle Remember Me checkbox changes
    @FXML
    protected void onRememberMeChanged(ActionEvent event) {
        if (!rememberMe.isSelected()) {
            // If user unchecks remember me, clear any immediately saved credentials
            clearSavedCredentials();
            showSuccessMessage("🔒 Login credentials will not be saved");
        } else {
            showSuccessMessage("💾 Login credentials will be saved");
        }
    }
}