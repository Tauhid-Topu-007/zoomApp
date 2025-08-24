package org.example.zoom;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public class LoginController {
    @FXML
    private TextField usernameField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Label messageLabel;

    @FXML
    protected void onLoginClick(ActionEvent event) {
        String username = usernameField.getText();
        String password = passwordField.getText();

        if (username.equals("Topu") && password.equals("1234")) {
            messageLabel.setText("✅ Login successful!");

            try {
                // Load Dashboard FXML
                FXMLLoader loader = new FXMLLoader(getClass().getResource("dashboard-view.fxml"));
                Scene dashboardScene = new Scene(loader.load(), 900, 600);

                // Pass username to dashboard (optional)
                DashboardController controller = loader.getController();
                controller.setUser(username);

                // Switch scene
                Stage stage = (Stage) messageLabel.getScene().getWindow();
                stage.setScene(dashboardScene);

            } catch (Exception e) {
                e.printStackTrace();
                messageLabel.setText("❌ Failed to load dashboard!");
            }

        } else {
            messageLabel.setText("❌ Invalid username or password!");
        }
    }

    @FXML
    protected void onRegisterClick(ActionEvent event) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("register-view.fxml"));
            Scene scene = new Scene(loader.load(), 500, 550);
            Stage stage = (Stage) messageLabel.getScene().getWindow();
            stage.setScene(scene);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
