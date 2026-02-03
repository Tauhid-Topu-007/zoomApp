package org.example.zoom;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Device2_Client extends Application {

    private static final String DEVICE_NAME = "Client-Device-2";
    private static final String SERVER_IP = "192.168.1.107";
    private static final int SERVER_PORT = 8887;

    @Override
    public void start(Stage primaryStage) throws Exception {
        System.setProperty("device.name", DEVICE_NAME);
        System.setProperty("server.ip", SERVER_IP);
        System.setProperty("server.port", String.valueOf(SERVER_PORT));

        System.out.println("Starting " + DEVICE_NAME);
        System.out.println("Connecting to server: " + SERVER_IP + ":" + SERVER_PORT);
        System.out.println("Database: " + Database.URL);

        Database.initializeDatabase();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("login-view.fxml"));
        Scene scene = new Scene(loader.load());

        primaryStage.setTitle("Zoom Client - " + DEVICE_NAME);
        primaryStage.setScene(scene);
        primaryStage.setWidth(900);
        primaryStage.setHeight(700);
        primaryStage.setX(1050);
        primaryStage.setY(100);

        HelloApplication.setPrimaryStage(primaryStage);

        primaryStage.show();

        System.out.println("Stage initialized and shown for: " + DEVICE_NAME);

        Platform.runLater(() -> {
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    Platform.runLater(() -> {
                        try {
                            Object controller = loader.getController();
                            if (controller instanceof LoginController) {
                                ((LoginController) controller).simulateAutoLogin("Tanvir");
                            }
                        } catch (Exception e) {
                            System.err.println("Auto-login failed: " + e.getMessage());
                            e.printStackTrace();
                        }
                    });
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        });
    }

    public static void main(String[] args) {
        System.out.println("Starting Device 2 - CLIENT DEVICE");
        System.out.println("Device Name: " + DEVICE_NAME);
        System.out.println("Server IP: " + SERVER_IP);
        System.out.println("Port: " + SERVER_PORT);
        System.out.println("Database: " + Database.URL);
        launch(args);
    }
}