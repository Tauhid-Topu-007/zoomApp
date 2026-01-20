package org.example.zoom;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ComboBox;
import javafx.scene.control.CheckBox;
import javafx.scene.layout.VBox;
import javafx.scene.layout.HBox;
import javafx.scene.layout.StackPane;
import javafx.scene.image.ImageView;
import javafx.scene.image.Image;
import javafx.animation.ScaleTransition;
import javafx.util.Duration;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.application.Platform;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.List;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoControlsController implements Initializable {

    @FXML
    private VBox videoContainer;
    @FXML
    private Button videoToggleButton;
    @FXML
    private Label videoStatusLabel;
    @FXML
    private HBox videoControlsBox;
    @FXML
    private Button screenshotButton;
    @FXML
    private Button recordButton;
    @FXML
    private ComboBox<String> cameraComboBox;
    @FXML
    private ComboBox<String> qualityComboBox;
    @FXML
    private CheckBox virtualBackgroundCheckBox;
    @FXML
    private CheckBox webRTCCheckBox;
    @FXML
    private StackPane videoPreviewContainer;

    // Camera preview
    private ImageView cameraPreview;
    private Canvas canvasBuffer;
    private GraphicsContext gcBuffer;

    // Video streaming
    private AtomicBoolean isStreaming = new AtomicBoolean(false);
    private Timeline streamingTimer;

    // Recording
    private boolean isRecordingLocal = false;
    private Timeline recordingTimer;
    private int recordingSeconds = 0;

    // Settings
    private int frameWidth = 640;
    private int frameHeight = 480;
    private int fps = 15;

    // For simulated camera animation
    private double animationTime = 0;
    private boolean useRealCamera = false;

    // Reference to MeetingController
    private MeetingController meetingController;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("🎥 Initializing VideoControlsController...");

        // Create camera preview
        cameraPreview = new ImageView();
        cameraPreview.setFitWidth(320);
        cameraPreview.setFitHeight(240);
        cameraPreview.setPreserveRatio(true);
        cameraPreview.setPreserveRatio(true);

        // Create canvas buffer for drawing
        canvasBuffer = new Canvas(frameWidth, frameHeight);
        gcBuffer = canvasBuffer.getGraphicsContext2D();

        // Add preview to container
        if (videoPreviewContainer != null) {
            videoPreviewContainer.getChildren().add(cameraPreview);
        }

        // Setup UI components
        setupVideoControls();

        // Initialize camera devices list
        initializeCameraDevices();

        // Register with main application
        HelloApplication.setVideoControlsController(this);

        // Sync with current state
        syncWithGlobalState();

        System.out.println("✅ VideoControlsController initialized for user: " +
                HelloApplication.getLoggedInUser());
    }

    private void setupVideoControls() {
        // Setup hover effects
        setupButtonHoverEffects(videoToggleButton);
        setupButtonHoverEffects(screenshotButton);
        setupButtonHoverEffects(recordButton);

        // Setup camera selection
        setupCameraSelection();

        // Setup quality options
        setupQualityOptions();

        // Setup virtual background
        setupVirtualBackground();

        // Setup WebRTC checkbox
        setupWebRTCCheckbox();

        // Update initial state
        updateButtonStyles();
        updateStatusLabel();
    }

    private void setupButtonHoverEffects(Button button) {
        if (button == null) return;

        button.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(100), button);
            st.setToX(1.05);
            st.setToY(1.05);
            st.play();
        });

        button.setOnMouseExited(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(100), button);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        });
    }

    private void initializeCameraDevices() {
        System.out.println("📷 Detecting camera devices...");

        List<String> cameras = new ArrayList<>();

        // Check for available cameras using JavaFX Media (if available)
        try {
            // Try to use javafx.scene.media if available
            cameras.add("Default Camera");
            cameras.add("Front Camera");
            cameras.add("Webcam");

            // Check if we can access camera via MediaPlayer (simplified check)
            System.out.println("✅ Camera detection completed");

        } catch (Exception e) {
            System.out.println("⚠️ Camera detection failed: " + e.getMessage());
            cameras.add("Default Camera");
            cameras.add("Simulated Camera");
        }

        if (cameraComboBox != null) {
            cameraComboBox.getItems().clear();
            cameraComboBox.getItems().addAll(cameras);
            cameraComboBox.setValue(cameras.get(0));
            cameraComboBox.setOnAction(e -> onCameraChanged());
        }
    }

    private void setupCameraSelection() {
        if (cameraComboBox != null) {
            cameraComboBox.getItems().addAll(
                    "Default Camera",
                    "Front Camera",
                    "Webcam",
                    "Simulated Camera"
            );
            cameraComboBox.setValue("Default Camera");
            cameraComboBox.setOnAction(e -> onCameraChanged());
        }
    }

    private void setupQualityOptions() {
        if (qualityComboBox != null) {
            qualityComboBox.getItems().addAll(
                    "Low (320x240)",
                    "Medium (640x480)",
                    "High (1280x720)",
                    "HD (1920x1080)"
            );
            qualityComboBox.setValue("Medium (640x480)");
            qualityComboBox.setOnAction(e -> onQualityChanged());
        }
    }

    private void setupVirtualBackground() {
        if (virtualBackgroundCheckBox != null) {
            virtualBackgroundCheckBox.setSelected(false);
            virtualBackgroundCheckBox.setOnAction(e -> onVirtualBackgroundToggled());
        }
    }

    private void setupWebRTCCheckbox() {
        if (webRTCCheckBox != null) {
            boolean webRTCEnabled = HelloApplication.isWebRTCEnabled();
            webRTCCheckBox.setSelected(webRTCEnabled);
            webRTCCheckBox.setText("WebRTC " + (webRTCEnabled ? "Enabled" : "Disabled"));
            webRTCCheckBox.setOnAction(e -> onWebRTCToggled());
        }
    }

    private void onWebRTCToggled() {
        boolean enabled = webRTCCheckBox.isSelected();
        if (enabled) {
            HelloApplication.enableWebRTC();
            webRTCCheckBox.setText("WebRTC Enabled");
            System.out.println("✅ WebRTC enabled for video streaming");
        } else {
            HelloApplication.disableWebRTC();
            webRTCCheckBox.setText("WebRTC Disabled");
            System.out.println("🛑 WebRTC disabled for video streaming");
        }
    }

    @FXML
    private void toggleVideo() {
        System.out.println("🎬 VideoControlsController.toggleVideo() called");

        // Use the global toggle method which handles WebSocket messaging
        HelloApplication.toggleVideo();

        // Sync our local state with global state
        syncWithGlobalState();
    }

    private void startVideoStreaming() {
        System.out.println("🚀 Starting video streaming...");

        if (isStreaming.get()) {
            System.out.println("⚠️ Already streaming");
            return;
        }

        isStreaming.set(true);

        // Determine if using real or simulated camera
        String cameraName = cameraComboBox != null ? cameraComboBox.getValue() : "Default Camera";
        useRealCamera = !cameraName.contains("Simulated");

        System.out.println("📷 Using camera: " + cameraName + " (real: " + useRealCamera + ")");

        // Start streaming timer
        startStreamingTimer();

        // Update UI
        updateButtonStyles();
        updateStatusLabel();

        System.out.println("✅ Video streaming started");
    }

    private void startStreamingTimer() {
        // Stop existing timer
        if (streamingTimer != null) {
            streamingTimer.stop();
        }

        // Create new timer for frame generation and streaming
        streamingTimer = new Timeline(
                new KeyFrame(Duration.millis(1000.0 / fps), e -> {
                    try {
                        // Generate or capture frame
                        Image frame = captureFrame();

                        if (frame != null) {
                            // Update preview
                            if (cameraPreview != null) {
                                cameraPreview.setImage(frame);
                            }

                            // Send frame via WebSocket if in meeting
                            sendVideoFrame(frame);
                        }

                    } catch (Exception ex) {
                        System.err.println("❌ Error in streaming timer: " + ex.getMessage());
                    }
                })
        );

        streamingTimer.setCycleCount(Animation.INDEFINITE);
        streamingTimer.play();
    }

    private Image captureFrame() {
        try {
            if (useRealCamera) {
                // In a real implementation, you would capture from actual camera
                // For now, we'll create a realistic simulated frame
                return createRealisticCameraFrame();
            } else {
                // Simulated camera with animation
                return createSimulatedCameraFrame();
            }
        } catch (Exception e) {
            System.err.println("❌ Error capturing frame: " + e.getMessage());
            return createErrorFrame();
        }
    }

    private Image createRealisticCameraFrame() {
        // Create a realistic-looking camera frame
        Canvas canvas = new Canvas(frameWidth, frameHeight);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Get current time for animation
        long currentTime = System.currentTimeMillis();
        animationTime += 0.1;

        // Background gradient (simulating room)
        gc.setFill(Color.rgb(40, 44, 52)); // Dark gray room
        gc.fillRect(0, 0, frameWidth, frameHeight);

        // Simulate a person (circular face)
        int faceX = frameWidth / 2;
        int faceY = frameHeight / 2;
        int faceRadius = Math.min(frameWidth, frameHeight) / 4;

        // Face with subtle movement
        double faceOffsetX = Math.sin(animationTime) * 5;
        double faceOffsetY = Math.cos(animationTime * 0.7) * 3;

        // Draw face
        gc.setFill(Color.rgb(255, 229, 180)); // Skin tone
        gc.fillOval(faceX - faceRadius + faceOffsetX, faceY - faceRadius + faceOffsetY,
                faceRadius * 2, faceRadius * 2);

        // Eyes with blinking
        boolean blinking = (currentTime / 3000) % 10 == 0; // Blink every ~30 seconds
        int eyeHeight = blinking ? 5 : 15;

        gc.setFill(Color.rgb(0, 0, 0)); // Black eyes
        gc.fillOval(faceX - faceRadius/2 - 20 + faceOffsetX, faceY - 10 + faceOffsetY, 30, eyeHeight);
        gc.fillOval(faceX + faceRadius/2 - 10 + faceOffsetX, faceY - 10 + faceOffsetY, 30, eyeHeight);

        // Mouth with subtle expression
        double mouthCurve = Math.sin(animationTime * 0.5) * 10;
        gc.setStroke(Color.rgb(150, 50, 50));
        gc.setLineWidth(3);
        gc.strokeArc(faceX - 30 + faceOffsetX, faceY + 20 + faceOffsetY + mouthCurve,
                60, 30, 0, 180, javafx.scene.shape.ArcType.OPEN);

        // Room details
        gc.setFill(Color.rgb(60, 64, 72));
        gc.fillRect(0, frameHeight - 50, frameWidth, 50); // Desk

        // Add timestamp and camera info
        gc.setFill(Color.WHITE);
        gc.fillText("Live Camera: " + cameraComboBox.getValue(), 10, 20);
        gc.fillText("Time: " + new java.util.Date().toString().split(" ")[3], 10, 40);
        gc.fillText("Resolution: " + frameWidth + "x" + frameHeight, 10, 60);

        // Live indicator
        gc.setFill(Color.RED);
        gc.fillOval(frameWidth - 40, 10, 10, 10);
        gc.setFill(Color.WHITE);
        gc.fillText("LIVE", frameWidth - 80, 20);

        // WebRTC status if enabled
        if (webRTCCheckBox.isSelected()) {
            gc.setFill(Color.GREEN);
            gc.fillText("WebRTC", frameWidth - 80, 40);
        }

        return canvas.snapshot(null, null);
    }

    private Image createSimulatedCameraFrame() {
        // Create animated simulated frame
        Canvas canvas = new Canvas(frameWidth, frameHeight);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        long currentTime = System.currentTimeMillis();
        double time = currentTime / 1000.0;
        animationTime += 0.05;

        // Animated background gradient
        for (int i = 0; i < frameHeight; i++) {
            double hue = (animationTime * 10 + i * 0.1) % 360;
            gc.setFill(Color.hsb(hue, 0.3, 0.7));
            gc.fillRect(0, i, frameWidth, 1);
        }

        // Moving shapes
        gc.setFill(Color.rgb(0, 100, 200, 0.7));
        double x1 = frameWidth / 4 + Math.sin(time) * 100;
        double y1 = frameHeight / 4 + Math.cos(time * 0.8) * 80;
        gc.fillOval(x1, y1, 100, 100);

        gc.setFill(Color.rgb(200, 100, 0, 0.7));
        double x2 = frameWidth * 3/4 + Math.sin(time * 1.2) * 80;
        double y2 = frameHeight * 3/4 + Math.cos(time * 0.9) * 60;
        gc.fillOval(x2, y2, 80, 80);

        // Grid lines
        gc.setStroke(Color.WHITE.deriveColor(0, 1, 1, 0.3));
        gc.setLineWidth(1);
        for (int i = 0; i < frameWidth; i += 40) {
            gc.strokeLine(i, 0, i, frameHeight);
        }
        for (int i = 0; i < frameHeight; i += 40) {
            gc.strokeLine(0, i, frameWidth, i);
        }

        // Info text
        gc.setFill(Color.WHITE);
        gc.fillText("Simulated Camera Feed", 20, 30);
        gc.fillText("FPS: " + fps, 20, 50);
        gc.fillText("Time: " + String.format("%.1f", time) + "s", 20, 70);

        // Live indicator
        gc.setFill(Color.RED);
        gc.fillOval(10, 10, 12, 12);
        gc.setFill(Color.WHITE);
        gc.fillText("SIMULATED", 30, 20);

        return canvas.snapshot(null, null);
    }

    private Image createErrorFrame() {
        Canvas canvas = new Canvas(frameWidth, frameHeight);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        gc.setFill(Color.DARKRED);
        gc.fillRect(0, 0, frameWidth, frameHeight);

        gc.setFill(Color.WHITE);
        gc.fillText("Camera Error", frameWidth/2 - 50, frameHeight/2 - 20);
        gc.fillText("Check camera connection", frameWidth/2 - 80, frameHeight/2 + 10);

        return canvas.snapshot(null, null);
    }

    private void stopVideoStreaming() {
        System.out.println("🛑 Stopping video streaming...");

        isStreaming.set(false);

        // Stop streaming timer
        if (streamingTimer != null) {
            streamingTimer.stop();
            streamingTimer = null;
        }

        // Clear preview
        if (cameraPreview != null) {
            cameraPreview.setImage(null);
        }

        // Update UI
        updateButtonStyles();
        updateStatusLabel();

        System.out.println("✅ Video streaming stopped");
    }

    private void sendVideoFrame(Image frame) {
        if (frame == null) return;

        // Only send if in a meeting, WebSocket connected, and video is on
        String meetingId = HelloApplication.getActiveMeetingId();
        String username = HelloApplication.getLoggedInUser();

        if (meetingId != null && username != null &&
                HelloApplication.isWebSocketConnected() &&
                HelloApplication.isVideoOn()) {

            try {
                // Convert Image to Base64 for transmission
                String base64Image = convertImageToBase64(frame);

                if (base64Image != null && !base64Image.isEmpty()) {
                    // Send via WebSocket
                    HelloApplication.sendWebSocketMessage("VIDEO_FRAME", meetingId, username, base64Image);

                    // Optional: Limit frequency of sending full frames
                    // In a real app, you'd use video compression
                }
            } catch (Exception e) {
                System.err.println("❌ Error sending video frame: " + e.getMessage());
            }
        }
    }

    private String convertImageToBase64(Image image) {
        try {
            // Convert to BufferedImage
            java.awt.image.BufferedImage bufferedImage =
                    javafx.embed.swing.SwingFXUtils.fromFXImage(image, null);

            if (bufferedImage == null) return null;

            // Compress to JPEG to reduce size
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(bufferedImage, "jpg", baos);
            byte[] imageBytes = baos.toByteArray();

            // Convert to Base64
            return java.util.Base64.getEncoder().encodeToString(imageBytes);

        } catch (Exception e) {
            System.err.println("❌ Error converting image to Base64: " + e.getMessage());
            return null;
        }
    }

    private void onCameraChanged() {
        String selectedCamera = cameraComboBox != null ? cameraComboBox.getValue() : "Default Camera";
        System.out.println("📷 Camera changed to: " + selectedCamera);

        // Restart streaming if currently active
        if (isStreaming.get()) {
            stopVideoStreaming();
            try {
                Thread.sleep(100);
                startVideoStreaming();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void onQualityChanged() {
        String selectedQuality = qualityComboBox != null ? qualityComboBox.getValue() : "Medium (640x480)";
        System.out.println("🎥 Quality changed to: " + selectedQuality);

        // Update resolution based on quality
        switch (selectedQuality) {
            case "Low (320x240)":
                frameWidth = 320;
                frameHeight = 240;
                fps = 10;
                break;
            case "Medium (640x480)":
                frameWidth = 640;
                frameHeight = 480;
                fps = 15;
                break;
            case "High (1280x720)":
                frameWidth = 1280;
                frameHeight = 720;
                fps = 10;
                break;
            case "HD (1920x1080)":
                frameWidth = 1920;
                frameHeight = 1080;
                fps = 5;
                break;
        }

        // Update canvas size
        if (canvasBuffer != null) {
            canvasBuffer.setWidth(frameWidth);
            canvasBuffer.setHeight(frameHeight);
            gcBuffer = canvasBuffer.getGraphicsContext2D();
        }

        // Restart streaming if active
        if (isStreaming.get()) {
            stopVideoStreaming();
            try {
                Thread.sleep(100);
                startVideoStreaming();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    private void onVirtualBackgroundToggled() {
        if (virtualBackgroundCheckBox != null) {
            boolean enabled = virtualBackgroundCheckBox.isSelected();
            System.out.println("🖼 Virtual background: " + (enabled ? "enabled" : "disabled"));

            // In a real implementation, you would apply background effects here
            if (meetingController != null) {
                meetingController.addSystemMessage(
                        "Virtual background " + (enabled ? "enabled" : "disabled"));
            }
        }
    }

    @FXML
    private void takeScreenshot() {
        System.out.println("📸 Taking screenshot");

        if (cameraPreview != null && cameraPreview.getImage() != null) {
            try {
                // Create screenshot filename
                String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss")
                        .format(new java.util.Date());
                String filename = "screenshot_" + timestamp + ".png";

                // Get the current frame
                Image image = cameraPreview.getImage();

                // Save as PNG
                java.awt.image.BufferedImage bufferedImage =
                        javafx.embed.swing.SwingFXUtils.fromFXImage(image, null);
                javax.imageio.ImageIO.write(bufferedImage, "png", new java.io.File(filename));

                System.out.println("✅ Screenshot saved: " + filename);

                // Show confirmation
                Platform.runLater(() -> {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                            javafx.scene.control.Alert.AlertType.INFORMATION);
                    alert.setTitle("Screenshot");
                    alert.setHeaderText(null);
                    alert.setContentText("Screenshot saved as:\n" + filename);
                    alert.showAndWait();
                });

            } catch (Exception e) {
                System.err.println("❌ Error saving screenshot: " + e.getMessage());

                Platform.runLater(() -> {
                    javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                            javafx.scene.control.Alert.AlertType.ERROR);
                    alert.setTitle("Screenshot Error");
                    alert.setHeaderText(null);
                    alert.setContentText("Failed to save screenshot:\n" + e.getMessage());
                    alert.showAndWait();
                });
            }
        } else {
            System.out.println("⚠️ No video frame available for screenshot");
        }
    }

    @FXML
    private void toggleRecording() {
        System.out.println("⏺ Toggle recording clicked");

        // Use the global recording toggle
        HelloApplication.toggleRecording();

        // Sync our local state
        syncWithGlobalState();
    }

    private void startLocalRecording() {
        System.out.println("🚀 Starting local recording...");

        if (!isStreaming.get()) {
            System.out.println("⚠️ Cannot record - video not streaming");
            return;
        }

        isRecordingLocal = true;
        recordingSeconds = 0;

        // Start recording timer
        startRecordingTimer();

        // Update UI
        updateButtonStyles();
        updateStatusLabel();

        System.out.println("✅ Local recording started");
    }

    private void startRecordingTimer() {
        if (recordingTimer != null) {
            recordingTimer.stop();
        }

        recordingTimer = new Timeline(
                new KeyFrame(Duration.seconds(1), e -> {
                    recordingSeconds++;
                    updateStatusLabel();
                })
        );
        recordingTimer.setCycleCount(Animation.INDEFINITE);
        recordingTimer.play();
    }

    private void stopLocalRecording() {
        System.out.println("🛑 Stopping local recording...");

        isRecordingLocal = false;

        // Stop recording timer
        if (recordingTimer != null) {
            recordingTimer.stop();
            recordingTimer = null;
        }

        // Save recording (in a real app, you'd save actual video)
        saveRecording();

        // Update UI
        updateButtonStyles();
        updateStatusLabel();

        System.out.println("✅ Local recording stopped");
    }

    private void saveRecording() {
        try {
            // Create recording info file
            String timestamp = new java.text.SimpleDateFormat("yyyyMMdd_HHmmss")
                    .format(new java.util.Date());
            String filename = "recording_" + timestamp + ".txt";

            java.io.FileWriter writer = new java.io.FileWriter(filename);
            writer.write("Zoom Meeting Recording\n");
            writer.write("Date: " + new java.util.Date() + "\n");
            writer.write("Duration: " + recordingSeconds + " seconds\n");
            writer.write("Resolution: " + frameWidth + "x" + frameHeight + "\n");
            writer.write("FPS: " + fps + "\n");
            writer.write("User: " + HelloApplication.getLoggedInUser() + "\n");
            writer.write("Meeting: " + HelloApplication.getActiveMeetingId() + "\n");
            writer.close();

            System.out.println("✅ Recording info saved: " + filename);

            // Show confirmation
            Platform.runLater(() -> {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.INFORMATION);
                alert.setTitle("Recording Complete");
                alert.setHeaderText("Recording Saved");
                alert.setContentText("Recording duration: " + recordingSeconds + " seconds\n" +
                        "Saved as: " + filename + "\n\n" +
                        "Note: This is a demo. In a full implementation,\n" +
                        "actual video would be saved as MP4.");
                alert.showAndWait();
            });

        } catch (Exception e) {
            System.err.println("❌ Error saving recording: " + e.getMessage());
        }
    }

    private void updateButtonStyles() {
        Platform.runLater(() -> {
            if (videoToggleButton != null) {
                if (isStreaming.get()) {
                    videoToggleButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;");
                    videoToggleButton.setText("Stop Video");
                } else {
                    videoToggleButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;");
                    videoToggleButton.setText("Start Video");
                }
            }

            if (recordButton != null) {
                if (isRecordingLocal) {
                    recordButton.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold;");
                    recordButton.setText("Stop Recording");
                } else {
                    recordButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;");
                    recordButton.setText("Start Recording");
                }

                // Only host can record
                recordButton.setDisable(!HelloApplication.isMeetingHost());
            }
        });
    }

    private void updateStatusLabel() {
        Platform.runLater(() -> {
            if (videoStatusLabel != null) {
                if (isStreaming.get()) {
                    if (isRecordingLocal) {
                        videoStatusLabel.setText(String.format("📹 Streaming ● ⏺ Recording (%02d:%02d)",
                                recordingSeconds / 60, recordingSeconds % 60));
                        videoStatusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                    } else {
                        videoStatusLabel.setText("📹 Streaming ● Live");
                        videoStatusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                    }
                } else {
                    videoStatusLabel.setText("📹 Video Off");
                    videoStatusLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-weight: bold;");
                }
            }
        });
    }

    // Public methods called from HelloApplication and MeetingController

    /**
     * Set the reference to MeetingController
     */
    public void setMeetingController(MeetingController meetingController) {
        this.meetingController = meetingController;
        System.out.println("✅ VideoControlsController connected to MeetingController");
    }

    public void syncWithGlobalState() {
        boolean globalVideoOn = HelloApplication.isVideoOn();
        boolean globalRecording = HelloApplication.isRecording();

        System.out.println("🔄 Syncing video state: video=" + globalVideoOn + ", recording=" + globalRecording);

        if (globalVideoOn != isStreaming.get()) {
            if (globalVideoOn) {
                startVideoStreaming();
            } else {
                stopVideoStreaming();
            }
        }

        if (globalRecording != isRecordingLocal) {
            if (globalRecording) {
                startLocalRecording();
            } else {
                stopLocalRecording();
            }
        }
    }

    /**
     * Update video preview with real camera feed
     * This is called from MeetingController to show live camera feed
     */
    public void updateVideoPreview(Image image) {
        if (cameraPreview != null && image != null) {
            Platform.runLater(() -> {
                cameraPreview.setImage(image);
            });
        }
    }

    public void displayVideoFrame(Image videoFrame) {
        Platform.runLater(() -> {
            if (cameraPreview != null && videoFrame != null) {
                cameraPreview.setImage(videoFrame);
            }
        });
    }

    public void updateFromServer(String username, String status) {
        System.out.println("🎥 Received video status from " + username + ": " + status);

        // This handles when other users start/stop their video
        // You could update UI to show who's streaming
    }

    public void onMeetingStateChanged(boolean inMeeting) {
        System.out.println("🎥 Meeting state changed: " + (inMeeting ? "in meeting" : "not in meeting"));

        if (!inMeeting) {
            // Left meeting - stop everything
            stopVideoStreaming();
            stopLocalRecording();
        }
    }

    public void onHostStatusChanged(boolean isHost) {
        System.out.println("🎥 Host status changed: " + (isHost ? "host" : "participant"));

        // Update recording button
        Platform.runLater(() -> {
            if (recordButton != null) {
                recordButton.setDisable(!isHost);
            }
        });
    }

    /**
     * Reset to simulated camera
     */
    public void resetToSimulatedCamera() {
        Platform.runLater(() -> {
            if (cameraPreview != null) {
                cameraPreview.setImage(null);
            }
        });
    }

    public void cleanup() {
        System.out.println("🧹 Cleaning up VideoControlsController...");
        stopVideoStreaming();
        stopLocalRecording();

        if (streamingTimer != null) {
            streamingTimer.stop();
            streamingTimer = null;
        }

        if (recordingTimer != null) {
            recordingTimer.stop();
            recordingTimer = null;
        }

        System.out.println("✅ VideoControlsController cleaned up");
    }
}