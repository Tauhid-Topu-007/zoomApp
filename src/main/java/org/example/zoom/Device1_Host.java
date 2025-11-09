package org.example.zoom;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;

public class Device1_Host extends Application {

    private static final String DEVICE_NAME = "Host-Device-1";
    private static final String SERVER_IP = "192.168.1.107"; // Change to your actual IP
    private static final int SERVER_PORT = 8887;

    @Override
    public void start(Stage primaryStage) throws Exception {
        // Set device-specific configuration
        System.setProperty("device.name", DEVICE_NAME);
        System.setProperty("server.ip", SERVER_IP);
        System.setProperty("server.port", String.valueOf(SERVER_PORT));

        System.out.println("🚀 Starting " + DEVICE_NAME);
        System.out.println("📍 Connecting to database: " + Database.URL);
        System.out.println("👤 Database user: " + Database.USER);

        // Initialize database for this device
        Database.initializeDatabase();

        // Load and show the login screen
        FXMLLoader loader = new FXMLLoader(getClass().getResource("login-view.fxml"));
        Scene scene = new Scene(loader.load());

        primaryStage.setTitle("Zoom Host - " + DEVICE_NAME);
        primaryStage.setScene(scene);
        primaryStage.setWidth(900);
        primaryStage.setHeight(700);
        primaryStage.setX(100); // Position on left side
        primaryStage.setY(100);

        // CRITICAL: Set the primary stage in HelloApplication BEFORE showing
        HelloApplication.setPrimaryStage(primaryStage);

        primaryStage.show();

        System.out.println("✅ Stage initialized and shown for: " + DEVICE_NAME);

        // Auto-login for testing after short delay
        Platform.runLater(() -> {
            new Thread(() -> {
                try {
                    Thread.sleep(2000); // Wait 2 seconds for UI to load
                    Platform.runLater(() -> {
                        try {
                            // Get the controller and simulate auto-login
                            Object controller = loader.getController();
                            if (controller instanceof LoginController) {
                                ((LoginController) controller).simulateAutoLogin("host_user");
                            }
                        } catch (Exception e) {
                            System.err.println("❌ Auto-login failed: " + e.getMessage());
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
        System.out.println("🎯 =========================================");
        System.out.println("🎯 Starting Device 1 - HOST DEVICE");
        System.out.println("🎯 =========================================");
        System.out.println("📍 Device Name: " + DEVICE_NAME);
        System.out.println("🌐 Server IP: " + SERVER_IP);
        System.out.println("🔌 Port: " + SERVER_PORT);
        System.out.println("💾 Database: " + Database.URL);
        System.out.println("👤 DB User: " + Database.USER);
        launch(args);
    }
}