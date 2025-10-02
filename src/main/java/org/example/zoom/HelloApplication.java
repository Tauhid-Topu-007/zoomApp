package org.example.zoom;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class HelloApplication extends Application {

    private static Stage primaryStage;
    private static String loggedInUser;   // ✅ persist login until logout
    private static String activeMeetingId; // ✅ store meeting ID

    @Override
    public void start(Stage stage) throws Exception {
        primaryStage = stage;
        setRoot("login-view.fxml");   // start at login
        stage.setTitle("Zoom Project");
        stage.show();
    }

    // ✅ Change scene but keep loggedInUser
    public static void setRoot(String fxml) throws Exception {
        FXMLLoader loader = new FXMLLoader(HelloApplication.class.getResource(fxml));
        Scene scene = new Scene(loader.load());
        primaryStage.setScene(scene);

        // Fullscreen only for meeting
        primaryStage.setFullScreen("meeting-view.fxml".equals(fxml));
    }

    // ✅ Called after successful login
    public static void setLoggedInUser(String username) {
        loggedInUser = username;
    }

    public static String getLoggedInUser() {
        return loggedInUser;
    }

    // ✅ Explicit logout
    public static void logout() throws Exception {
        loggedInUser = null;
        setRoot("login-view.fxml");
    }

    public static Stage getPrimaryStage() {
        return primaryStage;
    }

    // ✅ Meeting ID storage
    public static void setActiveMeetingId(String meetingId) {
        activeMeetingId = meetingId;
    }

    public static String getActiveMeetingId() {
        return activeMeetingId;
    }

    public static void main(String[] args) {
        launch();
    }
}
