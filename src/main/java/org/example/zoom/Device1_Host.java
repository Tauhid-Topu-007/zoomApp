package org.example.zoom;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.stage.Stage;
import org.example.zoom.websocket.SimpleNativeWebSocketServer;

public class Device1_Host extends Application {

    private static final String DEVICE_NAME = "Host-Device-1";
    private static final String SERVER_IP = "192.168.1.107";

    @Override
    public void start(Stage primaryStage) throws Exception {
        System.out.println("Starting Simple WebSocket Server...");
        SimpleNativeWebSocketServer server = SimpleNativeWebSocketServer.getInstance();
        server.start();

        Thread.sleep(500);

        int actualPort = server.getPort();

        if (!server.isRunning() || actualPort == -1) {
            System.err.println("CRITICAL: Failed to start WebSocket server!");
            System.err.println("Please check if ports 8887-8895 are available.");
            Platform.exit();
            return;
        }

        System.setProperty("device.name", DEVICE_NAME);
        System.setProperty("server.ip", "localhost");
        System.setProperty("server.port", String.valueOf(actualPort));

        System.out.println("Starting " + DEVICE_NAME);
        System.out.println("Server IP for clients: " + SERVER_IP);
        System.out.println("Actual Server Port: " + actualPort);
        System.out.println("Database: " + Database.URL);
        System.out.println("Server Status: " + (server.isRunning() ? "RUNNING" : "STOPPED"));

        Database.initializeDatabase();

        FXMLLoader loader = new FXMLLoader(getClass().getResource("login-view.fxml"));
        Scene scene = new Scene(loader.load());

        primaryStage.setTitle("Zoom Host - " + DEVICE_NAME + " [Port: " + actualPort + "]");
        primaryStage.setScene(scene);
        primaryStage.setWidth(900);
        primaryStage.setHeight(700);
        primaryStage.setX(100);
        primaryStage.setY(100);

        HelloApplication.setPrimaryStage(primaryStage);

        primaryStage.show();

        System.out.println("Stage initialized and shown for: " + DEVICE_NAME);
        System.out.println("Server client count: " + server.getClientCount());

        primaryStage.setOnCloseRequest(event -> {
            System.out.println("Application closing...");
            server.stop();
        });

        Platform.runLater(() -> {
            new Thread(() -> {
                try {
                    Thread.sleep(1500);
                    Platform.runLater(() -> {
                        try {
                            Object controller = loader.getController();
                            if (controller instanceof LoginController) {
                                ((LoginController) controller).simulateAutoLogin("Topu");
                                System.out.println("Auto-login initiated for user: Topu");
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

    @Override
    public void stop() throws Exception {
        SimpleNativeWebSocketServer server = SimpleNativeWebSocketServer.getInstance();
        if (server != null && server.isRunning()) {
            System.out.println("Stopping WebSocket server...");
            server.stop();
        }
        super.stop();
        System.out.println("Application stopped successfully");
    }

    public static void main(String[] args) {
        System.out.println("Starting Device 1 - HOST DEVICE");
        System.out.println("Device Name: " + DEVICE_NAME);
        System.out.println("Server IP (for clients): " + SERVER_IP);
        System.out.println("Database: " + Database.URL);
        System.out.println("Note: Port will be auto-selected from 8887-8895");

        try {
            launch(args);
        } catch (Exception e) {
            System.err.println("Fatal error starting application: " + e.getMessage());
            e.printStackTrace();
        }
    }
}