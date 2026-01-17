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
import org.example.zoom.webrtc.WebRTCManager;

import java.awt.image.BufferedImage;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoCaptureService extends Service<Image> {

    private Webcam webcam;
    private WebcamPanel webcamPanel;
    private boolean captureActive = false;
    private WebRTCManager webRTCManager;
    private final AtomicBoolean webRTCEnabled = new AtomicBoolean(false);

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

                            // Send via WebRTC if enabled
                            if (webRTCEnabled.get() && webRTCManager != null) {
                                webRTCManager.sendVideoFrame(fxImage);
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

    public void stopCapture() {
        captureActive = false;
        if (webcam != null && webcam.isOpen()) {
            webcam.close();
            System.out.println("🎥 Webcam closed");
        }
        cancel();
    }

    // WebRTC integration methods
    public void enableWebRTC(WebRTCManager manager) {
        this.webRTCManager = manager;
        webRTCEnabled.set(true);
        System.out.println("✅ WebRTC enabled for video streaming");
    }

    public void disableWebRTC() {
        webRTCEnabled.set(false);
        System.out.println("🛑 WebRTC disabled for video streaming");
    }

    public boolean isWebRTCEnabled() {
        return webRTCEnabled.get() && webRTCManager != null;
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
}