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

public class VideoCaptureService extends Service<Image> {

    private Webcam webcam;
    private WebcamPanel webcamPanel;
    private boolean captureActive = false; // Renamed from isRunning

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
                        }
                    }
                    Thread.sleep(33); // ~30 FPS
                }

                return null;
            }
        };
    }

    private Image convertToFxImage(BufferedImage image) {
        // Use the static method directly
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

    // Use a different method name to avoid conflict with parent class
    public boolean isCaptureActive() {
        return captureActive;
    }

    // Helper method to check if service is running (uses parent's method)
    public boolean isServiceRunning() {
        return super.isRunning();
    }
}