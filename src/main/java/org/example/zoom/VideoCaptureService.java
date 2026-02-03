package org.example.zoom;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.github.sarxos.webcam.WebcamResolution;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import javafx.embed.swing.SwingFXUtils;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoCaptureService extends Service<Image> {

    private Webcam webcam;
    private WebcamPanel webcamPanel;
    private boolean captureActive = false;
    private final AtomicBoolean streamingEnabled = new AtomicBoolean(false);
    private MeetingController meetingController;

    @Override
    protected Task<Image> createTask() {
        return new Task<Image>() {
            @Override
            protected Image call() throws Exception {
                captureActive = true;

                // Get available webcams
                List<Webcam> webcams = Webcam.getWebcams();
                if (webcams.isEmpty()) {
                    System.out.println("❌ No webcams found");
                    return null;
                }

                // Use first available webcam
                webcam = webcams.get(0);
                webcam.setViewSize(WebcamResolution.VGA.getSize());

                System.out.println("🎥 Using webcam: " + webcam.getName());
                webcam.open();

                while (captureActive && !isCancelled()) {
                    if (webcam.isOpen()) {
                        BufferedImage bufferedImage = webcam.getImage();
                        if (bufferedImage != null) {
                            Image fxImage = convertToFxImage(bufferedImage);
                            updateValue(fxImage);

                            // Send via WebSocket if streaming is enabled
                            if (streamingEnabled.get() && meetingController != null) {
                                sendVideoFrameToServer(fxImage);
                            }
                        }
                    }
                    Thread.sleep(33); // ~30 FPS
                }

                return null;
            }
        };
    }

    private Image convertToFxImage(BufferedImage image) {
        return SwingFXUtils.toFXImage(image, null);
    }

    private void sendVideoFrameToServer(Image fxImage) {
        // This method can be used to send frames to server via WebSocket
        if (meetingController != null) {
            try {
                // Convert to base64 and send via WebSocket
                // Implementation depends on your WebSocket setup
                System.out.println("📤 Video frame ready for streaming");
            } catch (Exception e) {
                System.err.println("❌ Failed to send video frame: " + e.getMessage());
            }
        }
    }

    public void stopCapture() {
        captureActive = false;
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
            System.out.println("🎥 Webcam closed");
        }
        cancel();
    }

    // WebSocket streaming methods
    public void enableStreaming(MeetingController controller) {
        this.meetingController = controller;
        streamingEnabled.set(true);
        System.out.println("✅ Video streaming enabled");
    }

    public void disableStreaming() {
        streamingEnabled.set(false);
        System.out.println("🛑 Video streaming disabled");
    }

    public boolean isStreamingEnabled() {
        return streamingEnabled.get() && meetingController != null;
    }

    // Existing methods remain the same
    public boolean isCameraAvailable() {
        return !Webcam.getWebcams().isEmpty();
    }

    public List<Webcam> getAvailableWebcams() {
        return Webcam.getWebcams();
    }

    public void switchCamera(Webcam newWebcam) {
        stopCapture();
        this.webcam = newWebcam;
        start();
    }

    public Webcam getCurrentWebcam() {
        return webcam;
    }

    public boolean isCaptureActive() {
        return captureActive;
    }

    public boolean isServiceRunning() {
        return super.isRunning();
    }

    // New method to set meeting controller
    public void setMeetingController(MeetingController controller) {
        this.meetingController = controller;
    }
}