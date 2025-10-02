package org.example.zoom;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class HelloApplication extends Application {

    private static Stage primaryStage;
    private static String loggedInUser;   // persist login until logout
    private static String activeMeetingId; // store meeting ID
    private static final List<String> activeParticipants = new ArrayList<>();

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        setRoot("login-view.fxml");   // start at login
        stage.setTitle("Zoom Project");
        stage.show();
    }

    public static void setRoot(String fxml) throws Exception {
        FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource(fxml));
        Scene scene = new Scene(loader.load());
        primaryStage.setScene(scene);

        // Fullscreen only for meeting
        primaryStage.setFullScreen("meeting-view.fxml".equals(fxml));
    }

    public static void setLoggedInUser(String username) {
        loggedInUser = username;
    }

    public static String getLoggedInUser() {
        return loggedInUser;
    }

    public static void logout() throws Exception {
        loggedInUser = null;
        activeParticipants.clear();
        activeMeetingId = null;
        setRoot("login-view.fxml");
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    // Meeting ID storage
    public static void setActiveMeetingId(String meetingId) {
        activeMeetingId = meetingId;
    }

    public static String getActiveMeetingId() {
        return activeMeetingId;
    }

    // ---------------- Participants ----------------
    public static void addParticipant(String name) {
        if (!activeParticipants.contains(name)) {
            activeParticipants.add(name);
        }
    }

    public static List<String> getActiveParticipants() {
        return new ArrayList<>(activeParticipants);
    }

    public static void clearParticipants() {
        activeParticipants.clear();
    }

    public static void main(String[] args) {
        launch();
    }
}
