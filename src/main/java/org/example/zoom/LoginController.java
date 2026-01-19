package org.example.zoom;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.application.Platform;
import org.example.zoom.websocket.SimpleWebSocketClient;

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
    private VBox loginForm;

    private SimpleWebSocketClient testClient;
    private boolean autoLoginInProgress = false;

    @FXML
    public void initialize() {
        System.out.println("🎯 LoginController initialized");

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

        // Check for auto-login parameter
        checkForAutoLogin();

        // Set up enter key handlers
        setupEnterKeyHandlers();

        // Apply initial styles
        applyInitialStyles();
    }

    private void setupEnterKeyHandlers() {
        // Username field: Enter moves to password field
        if (usernameField != null) {
            usernameField.setOnAction(this::onUsernameEnter);
        }

        // Password field: Enter triggers login
        if (passwordField != null) {
            passwordField.setOnAction(this::onPasswordEnter);
        }
    }

    private void applyInitialStyles() {
        // Apply consistent styling to form elements
        String fieldStyle = "-fx-background-color: #34495e; -fx-text-fill: white; -fx-border-color: #7f8c8d; -fx-border-radius: 5; -fx-padding: 10;";
        String buttonStyle = "-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold; -fx-border-radius: 5; -fx-padding: 10 20;";

        if (usernameField != null) usernameField.setStyle(fieldStyle);
        if (passwordField != null) passwordField.setStyle(fieldStyle);
        if (loginButton != null) loginButton.setStyle(buttonStyle);
        if (registerButton != null) registerButton.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-font-weight: bold; -fx-border-radius: 5; -fx-padding: 10 20;");
    }

    // NEW: Auto-login method for multi-device testing
    public void simulateAutoLogin(String username) {
        System.out.println("🎯 Auto-login triggered for: " + username);
        autoLoginInProgress = true;

        try {
            Platform.runLater(() -> {
                // Set the username field
                if (usernameField != null) {
                    usernameField.setText(username);
                }

                // Set a default password for testing
                if (passwordField != null) {
                    passwordField.setText("password123"); // Default password for testing
                }

                // Auto-check remember me for testing
                if (rememberMe != null) {
                    rememberMe.setSelected(true);
                }

                // Show auto-login status
                showSuccessMessage("🔄 Auto-login in progress...");

                // Small delay to show the message, then trigger login
                new Thread(() -> {
                    try {
                        Thread.sleep(1500); // 1.5 second delay for visibility
                        Platform.runLater(() -> {
                            // Trigger login programmatically
                            if (loginButton != null && !loginButton.isDisabled()) {
                                System.out.println("🎯 Firing login button for auto-login");
                                loginButton.fire();
                            } else {
                                // Fallback: call onLoginClick directly
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

        } catch (Exception e) {
            System.err.println("❌ Auto-login failed: " + e.getMessage());
            e.printStackTrace();
            autoLoginInProgress = false;
            showErrorMessage("❌ Auto-login failed!");
        }
    }

    // NEW: Check for auto-login on startup
    private void checkForAutoLogin() {
        // Check if this is an auto-login instance
        String deviceName = System.getProperty("device.name");
        if (deviceName != null && !deviceName.isEmpty()) {
            System.out.println("🔍 Auto-login detected for device: " + deviceName);

            // Determine username based on device name
            String username = getAutoLoginUsername(deviceName);

            // Auto-login after a short delay to ensure UI is fully loaded
            Platform.runLater(() -> {
                new Thread(() -> {
                    try {
                        Thread.sleep(2000); // Wait 2 seconds for UI to fully load
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

    // NEW: Get username based on device name
    private String getAutoLoginUsername(String deviceName) {
        if (deviceName == null) return "test_user";

        switch (deviceName.toLowerCase()) {
            case "host-device-1":
            case "device1_host":
                return "host_user";
            case "client-device-2":
            case "device2_client":
                return "client_user";
            case "device-3":
                return "user3";
            case "device-4":
                return "user4";
            default:
                return "test_user";
        }
    }

    @FXML
    protected void onLoginClick(ActionEvent event) {
        System.out.println("🔐 Login button clicked");

        // If auto-login is in progress, show different message
        if (autoLoginInProgress) {
            showSuccessMessage("🔄 Auto-login in progress...");
        }

        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        // Clear previous messages
        clearErrorMessage();

        // Validation
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

        // Show loading state
        setLoadingState(true);

        if (autoLoginInProgress) {
            messageLabel.setText("🔄 Auto-login in progress...");
        } else {
            messageLabel.setText("🔄 Authenticating...");
        }

        // Run authentication in background thread to prevent UI freezing
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

        // Save credentials if remember me is checked
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
            // Initialize WebSocket connection
            initializeWebSocketConnection(username);

        } catch (Exception e) {
            System.err.println("❌ Login process error: " + e.getMessage());
            e.printStackTrace();
            showErrorMessage("❌ Login error: " + e.getMessage());
            autoLoginInProgress = false;
        }
    }

    private void initializeWebSocketConnection(String username) {
        System.out.println("🔗 Initializing WebSocket connection for: " + username);

        new Thread(() -> {
            try {
                // Get current server configuration
                String serverUrl = HelloApplication.getCurrentServerUrl();
                System.out.println("🔗 Connecting to: " + serverUrl);

                // Create and initialize WebSocket client
                SimpleWebSocketClient client = new SimpleWebSocketClient(serverUrl, message -> {
                    System.out.println("📨 WebSocket message during login: " + message);

                    Platform.runLater(() -> {
                        if (message.contains("Connected") || message.contains("Welcome") || message.contains("WELCOME")) {
                            // Connection successful
                            System.out.println("✅ WebSocket connection confirmed");
                            if (autoLoginInProgress) {
                                showSuccessMessage("✅ Connected! Redirecting...");
                            } else {
                                showSuccessMessage("✅ Connected to server! Redirecting...");
                            }
                        } else if (message.contains("ERROR") || message.contains("Failed") || message.contains("DISCONNECTED")) {
                            // Connection failed but allow login in offline mode
                            System.out.println("⚠️ WebSocket connection issues");
                            if (autoLoginInProgress) {
                                showErrorMessage("⚠️ Server connection failed, continuing in offline mode");
                            } else {
                                showErrorMessage("⚠️ Server connection failed, but you can continue in offline mode");
                            }
                        }
                    });
                });

                // Wait for connection with timeout
                Thread.sleep(2500); // Wait 2.5 seconds for connection

                Platform.runLater(() -> {
                    boolean isConnected = client.isConnected();
                    System.out.println("🔗 WebSocket connection status: " + (isConnected ? "CONNECTED" : "DISCONNECTED"));

                    if (!isConnected) {
                        if (autoLoginInProgress) {
                            showErrorMessage("⚠️ Cannot connect to server, continuing in offline mode");
                        } else {
                            showErrorMessage("⚠️ Cannot connect to server, but you can continue in offline mode");
                        }
                    }

                    // Complete login regardless of connection status
                    completeLoginProcess(username);
                });

            } catch (Exception e) {
                System.err.println("❌ WebSocket initialization error: " + e.getMessage());
                Platform.runLater(() -> {
                    if (autoLoginInProgress) {
                        showErrorMessage("⚠️ Server connection issue, continuing in offline mode");
                    } else {
                        showErrorMessage("⚠️ Server connection issue, but you can continue in offline mode");
                    }
                    completeLoginProcess(username);
                });
            }
        }).start();
    }

    private void completeLoginProcess(String username) {
        System.out.println("🎯 Completing login process for: " + username);

        // Store logged in user globally
        HelloApplication.setLoggedInUser(username);

        // Small delay to show success message before redirecting
        new Thread(() -> {
            try {
                Thread.sleep(1500); // 1.5 second delay to show success message

                Platform.runLater(() -> {
                    try {
                        // Reset auto-login flag
                        autoLoginInProgress = false;

                        System.out.println("🚀 Redirecting to dashboard...");

                        // Use the new navigation method that handles stage readiness
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

        // Reset auto-login flag on failure
        autoLoginInProgress = false;

        showErrorMessage("❌ Invalid username or password!");
        shakeLoginForm();
        clearPasswordField();

        // Additional feedback
        highlightInvalidFields();
    }

    private void highlightInvalidFields() {
        // Add temporary red border to indicate error
        String errorStyle = "-fx-border-color: #e74c3c; -fx-border-width: 2px; -fx-border-radius: 5px;";

        Platform.runLater(() -> {
            usernameField.setStyle(errorStyle);
            passwordField.setStyle(errorStyle);

            // Remove error styling after 3 seconds
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
        System.out.println("🎯 Shaking login form");

        // Create a simple shake animation for the form
        Platform.runLater(() -> {
            String shakeStyle = "-fx-border-color: #e74c3c; -fx-border-width: 2px; -fx-effect: dropshadow(three-pass-box, rgba(231,76,60,0.6), 10, 0, 0, 0);";

            usernameField.setStyle(shakeStyle);
            passwordField.setStyle(shakeStyle);

            // Remove effect after animation
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

    // NEW: Fully functional Forgot Password implementation
    @FXML
    protected void onForgotPasswordClick(ActionEvent event) {
        System.out.println("🔑 Forgot password clicked");

        // Show dialog to enter username for password reset
        TextInputDialog usernameDialog = new TextInputDialog();
        usernameDialog.setTitle("Password Reset");
        usernameDialog.setHeaderText("Reset Your Password");
        usernameDialog.setContentText("Enter your username:");
        usernameDialog.getDialogPane().setStyle("-fx-background-color: #2c3e50;");

        Optional<String> result = usernameDialog.showAndWait();
        if (result.isPresent()) {
            String username = result.get().trim();

            if (username.isEmpty()) {
                showErrorMessage("❌ Please enter a username!");
                return;
            }

            // Check if username exists
            if (!Database.usernameExists(username)) {
                showErrorMessage("❌ Username not found!");
                return;
            }

            // Show password reset dialog
            showPasswordResetDialog(username);
        }
    }

    private void showPasswordResetDialog(String username) {
        // Create a custom password reset dialog
        Dialog<ButtonType> resetDialog = new Dialog<>();
        resetDialog.setTitle("Reset Password");
        resetDialog.setHeaderText("Reset Password for: " + username);
        resetDialog.getDialogPane().setStyle("-fx-background-color: #2c3e50;");

        // Create the password fields
        PasswordField newPasswordField = new PasswordField();
        newPasswordField.setPromptText("New Password");
        newPasswordField.setStyle("-fx-background-color: #34495e; -fx-text-fill: white;");

        PasswordField confirmPasswordField = new PasswordField();
        confirmPasswordField.setPromptText("Confirm New Password");
        confirmPasswordField.setStyle("-fx-background-color: #34495e; -fx-text-fill: white;");

        VBox content = new VBox(10);
        content.setStyle("-fx-background-color: #2c3e50; -fx-padding: 20;");
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
        newPasswordField.textProperty().addListener((observable, oldValue, newValue) ->
                validatePasswords(newPasswordField, confirmPasswordField, resetButton));
        confirmPasswordField.textProperty().addListener((observable, oldValue, newValue) ->
                validatePasswords(newPasswordField, confirmPasswordField, resetButton));

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
                confirmPassword.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; -fx-border-color: #27ae60; -fx-border-width: 2px;");
            } else {
                confirmPassword.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; -fx-border-color: #e74c3c; -fx-border-width: 2px;");
            }
        } else {
            confirmPassword.setStyle("-fx-background-color: #34495e; -fx-text-fill: white; -fx-border-color: #7f8c8d;");
        }
    }

    @FXML
    protected void onServerConfigClick(ActionEvent event) {
        System.out.println("⚙️ Server config clicked");

        // Show server configuration dialog
        Stage currentStage = (Stage) messageLabel.getScene().getWindow();
        try {
            ServerConfigDialog dialog = ServerConfigDialog.showDialog(currentStage);

            if (dialog != null && dialog.isConnected()) {
                updateConnectionStatus();
                showSuccessMessage("✅ Server configuration updated!");
            } else {
                updateConnectionStatus();
                showErrorMessage("⚠️ Server configuration cancelled.");
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
        System.out.println("🔗 Quick localhost connection");

        // Quick connect to localhost
        HelloApplication.setServerConfig("localhost", "8887");
        updateConnectionStatus();
        showSuccessMessage("✅ Localhost configuration set!");

        // Test the connection
        onTestConnectionClick(event);
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

    private void updateConnectionStatus() {
        if (connectionStatusLabel != null && serverInfoLabel != null) {
            String serverUrl = HelloApplication.getCurrentServerUrl();
            boolean isConnected = testConnectionQuick();

            Platform.runLater(() -> {
                if (isConnected) {
                    connectionStatusLabel.setText("🟢 Connected");
                    connectionStatusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold; -fx-font-size: 14px;");
                } else {
                    connectionStatusLabel.setText("Disconnected");
                    connectionStatusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold; -fx-font-size: 14px;");
                }

                serverInfoLabel.setText(serverUrl.replace("ws://", ""));
                serverInfoLabel.setStyle("-fx-text-fill: #bdc3c7; -fx-font-size: 12px;");
            });
        }
    }

    private boolean testConnectionQuick() {
        try {
            String testUrl = HelloApplication.getCurrentServerUrl();
            final boolean[] connectionSuccess = {false};
            final Object lock = new Object();

            System.out.println("🔍 Quick connection test to: " + testUrl);

            // Create a temporary test client
            SimpleWebSocketClient testClient = new SimpleWebSocketClient(testUrl, message -> {
                System.out.println("🔍 Quick test received: " + message);
                if (message.contains("Connected") || message.contains("Welcome") || message.contains("WELCOME")) {
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

            System.out.println("🔍 Quick test result: " + (connectionSuccess[0] ? "SUCCESS" : "FAILED"));
            return connectionSuccess[0];

        } catch (Exception e) {
            System.err.println("🔍 Quick connection test failed: " + e.getMessage());
            return false;
        }
    }

    private boolean testConnectionWithoutLogin() {
        try {
            String testUrl = HelloApplication.getCurrentServerUrl();
            System.out.println("🔍 Testing connection to: " + testUrl);

            final boolean[] connectionSuccess = {false};
            final Object lock = new Object();

            // Create a temporary test client
            SimpleWebSocketClient testClient = new SimpleWebSocketClient(testUrl, message -> {
                System.out.println("🔍 Test connection received: " + message);
                if (message.contains("Connected") || message.contains("Welcome") || message.contains("WELCOME")) {
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

            boolean result = connectionSuccess[0];
            System.out.println("🔍 Connection test result: " + (result ? "SUCCESS" : "FAILED"));
            return result;

        } catch (Exception e) {
            System.err.println("🔍 Connection test failed: " + e.getMessage());
            return false;
        }
    }

    private void loadSavedCredentials() {
        // Load remembered username from preferences
        String rememberedUser = HelloApplication.getUserPreference("remembered_username");
        if (rememberedUser != null && !rememberedUser.isEmpty()) {
            Platform.runLater(() -> {
                usernameField.setText(rememberedUser);
                rememberMe.setSelected(true);

                // Auto-focus on password field if username is remembered
                passwordField.requestFocus();

                System.out.println("💾 Loaded saved credentials for: " + rememberedUser);
            });
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
        System.out.println("↵ Username field enter pressed");
        if (passwordField != null) {
            passwordField.requestFocus();
        }
    }

    @FXML
    protected void onPasswordEnter(ActionEvent event) {
        // When user presses Enter in password field, trigger login
        System.out.println("↵ Password field enter pressed");
        onLoginClick(event);
    }

    // NEW: Handle Remember Me checkbox changes
    @FXML
    protected void onRememberMeChanged(ActionEvent event) {
        System.out.println("💾 Remember me changed: " + rememberMe.isSelected());

        if (!rememberMe.isSelected()) {
            // If user unchecks remember me, clear any immediately saved credentials
            clearSavedCredentials();
            showSuccessMessage("🔒 Login credentials will not be saved");
        } else {
            showSuccessMessage("💾 Login credentials will be saved");
        }
    }

    // NEW: Manual trigger for auto-login (for testing)
    public void triggerAutoLogin(String username) {
        System.out.println("🎯 Manual auto-login triggered for: " + username);
        simulateAutoLogin(username);
    }

    // NEW: Cleanup method
    public void cleanup() {
        System.out.println("🧹 Cleaning up LoginController");
        if (testClient != null) {
            testClient.disconnect();
            testClient = null;
        }
        autoLoginInProgress = false;
    }

    // NEW: Get current auto-login status
    public boolean isAutoLoginInProgress() {
        return autoLoginInProgress;
    }

    // NEW: Cancel auto-login
    public void cancelAutoLogin() {
        System.out.println("🚫 Cancelling auto-login");
        autoLoginInProgress = false;
        setLoadingState(false);
        clearErrorMessage();
        showErrorMessage("❌ Auto-login cancelled");
    }
}