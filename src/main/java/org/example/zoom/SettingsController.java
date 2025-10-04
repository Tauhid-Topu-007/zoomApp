package org.example.zoom;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class SettingsController {

    private String username; // current logged-in user

    @FXML private TextField newUsernameField;
    @FXML private PasswordField oldPasswordField;
    @FXML private PasswordField newPasswordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private Label messageLabel;

    public void setUser(String username) {
        this.username = username;
    }

    @FXML
    protected void onChangeUsernameClick(ActionEvent event) {
        String newUsername = newUsernameField.getText().trim();

        if (newUsername.isEmpty()) {
            messageLabel.setText("❌ Please enter a new username!");
            return;
        }

        // Check if username already exists
        if (Database.usernameExists(newUsername)) {
            messageLabel.setText("⚠ Username already taken!");
            return;
        }

        if (Database.updateUsername(username, newUsername)) {
            messageLabel.setText("✅ Username changed successfully!");
            HelloApplication.setLoggedInUser(newUsername); // update session
            username = newUsername; // update local
        } else {
            messageLabel.setText("❌ Failed to update username!");
        }
    }

    @FXML
    protected void onChangePasswordClick(ActionEvent event) {
        String oldPassword = oldPasswordField.getText();
        String newPassword = newPasswordField.getText();
        String confirmPassword = confirmPasswordField.getText();

        if (oldPassword.isEmpty() || newPassword.isEmpty() || confirmPassword.isEmpty()) {
            messageLabel.setText("❌ Please fill all fields!");
            return;
        }

        if (!Database.authenticateUser(username, oldPassword)) {
            messageLabel.setText("❌ Old password is incorrect!");
            return;
        }

        if (!newPassword.equals(confirmPassword)) {
            messageLabel.setText("❌ New passwords do not match!");
            return;
        }

        if (Database.updatePassword(username, newPassword)) {
            messageLabel.setText("✅ Password updated successfully!");
        } else {
            messageLabel.setText("❌ Failed to update password!");
        }
    }

    @FXML
    protected void onBackClick(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("dashboard-view.fxml"));
            Scene scene = new Scene(loader.load(), 900, 600);

            Stage stage = (Stage) messageLabel.getScene().getWindow();
            stage.setScene(scene);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
