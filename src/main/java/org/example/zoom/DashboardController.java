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

    // Main Zoom functionalities with popups
    @FXML
    protected void onNewMeetingClick() {
        showPopup("New Meeting", "🟢 Starting a new meeting...");
    }

    @FXML
    protected void onJoinClick() {
        showPopup("Join Meeting", "🔵 Joining a meeting...");
    }

    @FXML
    protected void onScheduleClick() {
        showPopup("Schedule Meeting", "📅 Scheduling a meeting...");
    }

    @FXML
    protected void onShareScreenClick() {
        showPopup("Share Screen", "🖥️ Sharing screen...");
    }

    // Additional functionalities with popups
    @FXML
    protected void onContactsClick() {
        showPopup("Contacts", "👥 Opening contacts...");
    }

    @FXML
    protected void onChatClick() {
        showPopup("Chat", "💬 Opening chat...");
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

    // Helper method to show popup dialogs
    private void showPopup(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
