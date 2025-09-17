package org.example.zoom;

import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public class DashboardController {

    @FXML private Label welcomeLabel;
    private String currentUser;

    public void setUser(String username) {
        currentUser = username;
        welcomeLabel.setText("Welcome, " + username + " 👋");
    }

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
        FXMLLoader loader = new FXMLLoader(getClass().getResource("schedule-view.fxml"));
        Scene scene = new Scene(loader.load(), 900, 600);
        ScheduleController controller = loader.getController();
        controller.setUser(currentUser);

        Stage stage = (Stage) welcomeLabel.getScene().getWindow();
        stage.setScene(scene);
    }

    @FXML
    protected void onShareScreenClick() throws Exception {
        HelloApplication.setRoot("share-screen-view.fxml");
    }

    @FXML
    protected void onContactsClick() throws Exception {
        HelloApplication.setRoot("contacts-view.fxml");
    }

    @FXML
    protected void onChatClick() throws Exception { HelloApplication.setRoot("chat-view.fxml"); }

    @FXML
    protected void onRecordingsClick() { showPopup("Recordings", "🎥 Viewing recordings..."); }

    @FXML
    protected void onSettingsClick() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("settings-view.fxml"));
            Scene scene = new Scene(loader.load(), 500, 400);
            SettingsController controller = loader.getController();
            controller.setUser(currentUser);

            Stage stage = (Stage) welcomeLabel.getScene().getWindow();
            stage.setScene(scene);

        } catch (Exception e) {
            e.printStackTrace();
            showPopup("Error", "❌ Failed to open Settings!");
        }
    }

    @FXML
    protected void onLogoutClick() throws Exception { HelloApplication.setRoot("login-view.fxml"); }

    private void showPopup(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
