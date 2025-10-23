package org.example.zoom;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

public class RegisterController {
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private Label messageLabel;

    @FXML
    public void initialize() {
        // Clear messages when user starts typing
        setupFieldListeners();
    }

    private void setupFieldListeners() {
        usernameField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.isEmpty()) {
                clearMessage();
            }
        });

        passwordField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.isEmpty()) {
                clearMessage();
            }
        });

        confirmPasswordField.textProperty().addListener((observable, oldValue, newValue) -> {
            if (!newValue.isEmpty()) {
                clearMessage();
            }
        });

        // Allow pressing Enter to register
        usernameField.setOnAction(this::onRegisterClick);
        passwordField.setOnAction(this::onRegisterClick);
        confirmPasswordField.setOnAction(this::onRegisterClick);
    }

    private void clearMessage() {
        if (!messageLabel.getText().isEmpty()) {
            messageLabel.setText("");
        }
    }

    @FXML
    protected void onRegisterClick(ActionEvent event) {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        // Validation
        if (username.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
            showError("❌ Please fill all fields!");
            return;
        }

        if (username.length() < 3) {
            showError("❌ Username must be at least 3 characters!");
            return;
        }

        if (username.length() > 20) {
            showError("❌ Username must be less than 20 characters!");
            return;
        }

        if (password.length() < 6) {
            showError("❌ Password must be at least 6 characters!");
            return;
        }

        if (!password.equals(confirmPassword)) {
            showError("❌ Passwords do not match!");
            return;
        }

        // Check for common password patterns
        if (isWeakPassword(password)) {
            showError("⚠️ Password is too weak. Use letters, numbers, and special characters!");
            return;
        }

        // Attempt registration
        if (Database.registerUser(username, password)) {
            showSuccess("✅ Account created successfully! Redirecting to login...");

            // Auto-redirect to login after success
            PauseTransition pause = new PauseTransition(Duration.seconds(2));
            pause.setOnFinished(e -> navigateToLogin());
            pause.play();

        } else {
            showError("❌ Registration failed! Username may already exist.");
        }
    }

    private boolean isWeakPassword(String password) {
        // Check if password is too simple
        if (password.matches("[a-zA-Z]+") || password.matches("[0-9]+")) {
            return true;
        }

        // Check for common weak passwords
        String[] weakPasswords = {"password", "123456", "qwerty", "admin", "letmein"};
        for (String weak : weakPasswords) {
            if (password.equalsIgnoreCase(weak)) {
                return true;
            }
        }

        return false;
    }

    private void showError(String message) {
        messageLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
        messageLabel.setText(message);
    }

    private void showSuccess(String message) {
        messageLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
        messageLabel.setText(message);

        // Clear fields on success
        passwordField.clear();
        confirmPasswordField.clear();
    }

    @FXML
    protected void onBackClick(ActionEvent event) {
        navigateToLogin();
    }

    private void navigateToLogin() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("login-view.fxml"));
            Scene scene = new Scene(loader.load(), 500, 550);
            Stage stage = (Stage) messageLabel.getScene().getWindow();
            stage.setScene(scene);
            stage.setTitle("Zoom Clone - Login");
        } catch (Exception e) {
            e.printStackTrace();
            showError("❌ Error navigating to login page!");
        }
    }

    @FXML
    protected void onEnterKeyPressed(ActionEvent event) {
        onRegisterClick(event);
    }
}