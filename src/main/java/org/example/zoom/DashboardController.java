package org.example.zoom;

import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;

public class DashboardController {

    @FXML
    private Label welcomeLabel;

    // Called from LoginController
    public void setUser(String username) {
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
    protected void onShareScreenClick() throws Exception{
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
        showPopup("Settings", "⚙ Opening settings...");
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
