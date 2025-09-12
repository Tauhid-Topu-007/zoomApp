package org.example.zoom;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class DashboardController {

    @FXML
    private Label welcomeLabel;

    private String currentUser; // store logged-in user

    // Called from LoginController
    public void setUser(String username) {
        this.currentUser = username;
        welcomeLabel.setText("Welcome, " + username + " 👋");
    }

    // Main Zoom functionalities
    @FXML
    protected void onNewMeetingClick() throws Exception {
        HelloApplication.setRoot("new-meeting-view.fxml");
    }

    @FXML
    protected void onJoinClick() throws Exception {
        HelloApplication.setRoot("join-view.fxml");
    }

    @FXML
    protected void onScheduleClick() throws Exception {
        HelloApplication.setRoot("schedule-view.fxml"); // 🔹 open Schedule page
    }

    @FXML
    protected void onShareScreenClick() throws Exception {
        HelloApplication.setRoot("share-screen-view.fxml");
    }

    // Additional functionalities
    @FXML
    protected void onContactsClick() {
        showPopup("Contacts", "👥 Opening contacts...");
    }

    @FXML
    protected void onChatClick() throws Exception {
        HelloApplication.setRoot("chat-view.fxml");
    }

    @FXML
    protected void onRecordingsClick() {
        showPopup("Recordings", "🎥 Viewing recordings...");
    }

    @FXML
    protected void onSettingsClick() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("settings-view.fxml"));
            Scene scene = new Scene(loader.load(), 500, 400);

            // Pass username to SettingsController
            SettingsController controller = loader.getController();
            controller.setUser(currentUser);

            // Switch scene
            Stage stage = (Stage) welcomeLabel.getScene().getWindow();
            stage.setScene(scene);

        } catch (Exception e) {
            e.printStackTrace();
            showPopup("Error", "❌ Failed to open Settings!");
        }
    }

    @FXML
    protected void onLogoutClick() throws Exception {
        HelloApplication.setRoot("login-view.fxml");
    }

    // Helper method for simple alerts
    private void showPopup(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
