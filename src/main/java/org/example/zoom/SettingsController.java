package org.example.zoom;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;

public class SettingsController {

    private String username; // current logged-in user

    @FXML
    private PasswordField oldPasswordField;
    @FXML
    private PasswordField newPasswordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private Label messageLabel;

    // called from DashboardController when loading settings
    public void setUser(String username) {
        this.username = username;
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

            DashboardController controller = loader.getController();
            controller.setUser(username);

            Stage stage = (Stage) messageLabel.getScene().getWindow();
            stage.setScene(scene);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
