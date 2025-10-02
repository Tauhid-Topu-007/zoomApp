package org.example.zoom;

import javafx.event.ActionEvent;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

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

        if (Database.authenticateUser(username, password)) {
            messageLabel.setText("✅ Login successful!");

            try {
                // Store logged in user globally
                HelloApplication.setLoggedInUser(username);

                // Navigate to dashboard
                HelloApplication.setRoot("dashboard-view.fxml");

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
            HelloApplication.setRoot("register-view.fxml");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
