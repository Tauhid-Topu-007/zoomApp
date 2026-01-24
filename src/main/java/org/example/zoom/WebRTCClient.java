package org.example.zoom.webrtc;

import javafx.application.Platform;
import javafx.scene.image.Image;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class WebRTCClient {

    private final String username;
    private WebRTCCallbacks callbacks;
    private ExecutorService executor = Executors.newSingleThreadExecutor();
    private boolean videoOn = false;
    private boolean audioOn = false;

    public interface WebRTCCallbacks {
        void onLocalVideoFrame(Image frame);
        void onRemoteVideoFrame(String peerId, Image frame);
        void onAudioStateChanged(boolean enabled);
        void onVideoStateChanged(boolean enabled);
        void onConnectionStateChanged(String state);
        void onError(String error);
    }

    public WebRTCClient(String username, WebRTCCallbacks callbacks) {
        this.username = username;
        this.callbacks = callbacks;
    }

    public void startLocalStream() {
        executor.execute(() -> {
            try {
                videoOn = true;
                audioOn = true;

                Platform.runLater(() -> {
                    callbacks.onVideoStateChanged(true);
                    callbacks.onAudioStateChanged(true);
                    callbacks.onConnectionStateChanged("Connected");
                });

                // Simulate video frames
                new Thread(() -> {
                    while (videoOn) {
                        try {
                            Thread.sleep(100); // 10 FPS
                            Platform.runLater(() -> {
                                // Create a simulated video frame
                                callbacks.onLocalVideoFrame(createSimulatedFrame());
                            });
                        } catch (InterruptedException e) {
                            break;
                        }
                    }
                }).start();

            } catch (Exception e) {
                Platform.runLater(() -> callbacks.onError("Start stream error: " + e.getMessage()));
            }
        });
    }

    private Image createSimulatedFrame() {
        // Create a simple simulated frame
        javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(640, 480);
        javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();

        // Draw background
        gc.setFill(javafx.scene.paint.Color.LIGHTBLUE);
        gc.fillRect(0, 0, 640, 480);

        // Draw user info
        gc.setFill(javafx.scene.paint.Color.BLACK);
        gc.fillText("User: " + username, 10, 20);
        gc.fillText("WebRTC Simulated Stream", 10, 40);
        gc.fillText("Time: " + System.currentTimeMillis(), 10, 60);

        // Draw WebRTC indicator
        gc.setFill(javafx.scene.paint.Color.GREEN);
        gc.fillOval(580, 10, 10, 10);

        return canvas.snapshot(null, null);
    }

    public void stopLocalStream() {
        videoOn = false;
        audioOn = false;

        Platform.runLater(() -> {
            callbacks.onVideoStateChanged(false);
            callbacks.onAudioStateChanged(false);
            callbacks.onConnectionStateChanged("Disconnected");
        });
    }

    public void toggleAudio(boolean enabled) {
        audioOn = enabled;
        Platform.runLater(() -> callbacks.onAudioStateChanged(enabled));
    }

    public void toggleVideo(boolean enabled) {
        videoOn = enabled;
        Platform.runLater(() -> callbacks.onVideoStateChanged(enabled));
    }

    public void handleSignalingMessage(String fromPeerId, String type, String sdp) {
        System.out.println("WebRTC Signaling from " + fromPeerId + ": " + type);
    }

    public void dispose() {
        stopLocalStream();
        executor.shutdown();
    }
}