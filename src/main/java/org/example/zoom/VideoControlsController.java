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
    private StackPane videoPreviewContainer;

    // Separate ImageView for real camera feed
    private ImageView realCameraPreview;
    private ImageView simulatedCameraPreview;

    private boolean videoOn = false;
    private boolean isRecording = false;
    private boolean virtualBackgroundEnabled = false;

    // Camera simulation
    private Timeline cameraSimulationTimer;
    private ObjectProperty<Image> currentVideoFrame;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        // Create ImageViews
        realCameraPreview = new ImageView();
        simulatedCameraPreview = new ImageView();

        // Initialize the currentVideoFrame property
        currentVideoFrame = new SimpleObjectProperty<>();

        // Setup the preview container
        setupVideoPreview();
        setupVideoControls();
        updateButtonStyles();

        // Initialize camera simulation
        initializeCameraSimulation();

        // Register this controller with the main application
        HelloApplication.setVideoControlsController(this);

        System.out.println("🎥 VideoControlsController initialized successfully");
    }

    private void setupVideoPreview() {
        if (videoPreviewContainer != null) {
            // Configure ImageViews
            realCameraPreview.setFitWidth(200);
            realCameraPreview.setFitHeight(150);
            realCameraPreview.setPreserveRatio(true);
            realCameraPreview.setVisible(false); // Hidden by default

            simulatedCameraPreview.setFitWidth(200);
            simulatedCameraPreview.setFitHeight(150);
            simulatedCameraPreview.setPreserveRatio(true);
            simulatedCameraPreview.setVisible(true); // Show simulated by default

            // Add both ImageViews to container
            videoPreviewContainer.getChildren().addAll(realCameraPreview, simulatedCameraPreview);

            // Bind simulated camera to the property
            simulatedCameraPreview.imageProperty().bind(currentVideoFrame);
        } else {
            System.err.println("❌ Video preview container is null");
        }
    }

    private void initializeCameraSimulation() {
        // Set initial placeholder image
        currentVideoFrame.set(createPlaceholderImage());
    }

    private void setupVideoControls() {
        // Add hover effects
        setupButtonHoverEffects(videoToggleButton);
        setupButtonHoverEffects(screenshotButton);
        setupButtonHoverEffects(recordButton);

        // Set initial styles
        if (videoToggleButton != null) {
            videoToggleButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;");
        }
        if (screenshotButton != null) {
            screenshotButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold;");
        }
        if (recordButton != null) {
            recordButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;");
        }

        // Setup camera selection
        setupCameraSelection();

        // Setup quality options
        setupQualityOptions();

        // Setup virtual background
        setupVirtualBackground();

        // Update status label
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

    private void setupCameraSelection() {
        if (cameraComboBox == null) return;

        cameraComboBox.getItems().clear();

        // Simulate available cameras
        List<String> cameras = new ArrayList<>();
        cameras.add("Default Camera");
        cameras.add("Front Camera");
        cameras.add("External Webcam");

        cameraComboBox.getItems().addAll(cameras);
        cameraComboBox.setValue(cameras.get(0));

        cameraComboBox.setOnAction(e -> onCameraChanged());
    }

    private void setupQualityOptions() {
        if (qualityComboBox == null) return;

        qualityComboBox.getItems().addAll(
                "Low (480p)",
                "Medium (720p)",
                "High (1080p)",
                "Ultra (4K)"
        );
        qualityComboBox.setValue("High (1080p)");
        qualityComboBox.setOnAction(e -> onQualityChanged());
    }

    private void setupVirtualBackground() {
        if (virtualBackgroundCheckBox == null) return;

        virtualBackgroundCheckBox.setSelected(false);
        virtualBackgroundCheckBox.setOnAction(e -> onVirtualBackgroundToggled());
    }

    @FXML
    protected void toggleVideo() {
        if (!videoOn) {
            // Start video
            startVideo();
        } else {
            // Stop video
            stopVideo();
        }
    }

    private void startVideo() {
        try {
            videoOn = true;
            updateButtonStyles();
            updateStatusLabel();

            // Start camera simulation
            startCameraSimulation();

            // Send video status via WebSocket
            if (HelloApplication.getActiveMeetingId() != null) {
                String username = HelloApplication.getLoggedInUser();
                if (HelloApplication.isMeetingHost()) {
                    // Host-specific video message
                    HelloApplication.sendWebSocketMessage("VIDEO_STATUS",
                            HelloApplication.getActiveMeetingId(), "HOST_VIDEO_STARTED");
                } else {
                    // Participant video message
                    HelloApplication.sendWebSocketMessage("VIDEO_STATUS",
                            HelloApplication.getActiveMeetingId(), username + " started video");
                }
            }

            System.out.println("🎥 Video started and notified participants");

        } catch (Exception e) {
            System.err.println("❌ Failed to start video: " + e.getMessage());
            showCameraError();
        }
    }

    private void stopVideo() {
        videoOn = false;
        updateButtonStyles();
        updateStatusLabel();

        // Stop camera simulation
        stopCameraSimulation();

        // Set placeholder image
        currentVideoFrame.set(createPlaceholderImage());

        // Send video status via WebSocket
        if (HelloApplication.getActiveMeetingId() != null) {
            String username = HelloApplication.getLoggedInUser();
            if (HelloApplication.isMeetingHost()) {
                // Host-specific video message
                HelloApplication.sendWebSocketMessage("VIDEO_STATUS",
                        HelloApplication.getActiveMeetingId(), "HOST_VIDEO_STOPPED");
            } else {
                // Participant video message
                HelloApplication.sendWebSocketMessage("VIDEO_STATUS",
                        HelloApplication.getActiveMeetingId(), username + " stopped video");
            }
        }

        System.out.println("🎥 Video stopped and notified participants");
    }

    private void startCameraSimulation() {
        // Simulate live camera feed with animated frames
        cameraSimulationTimer = new Timeline(
                new KeyFrame(Duration.millis(100), e -> updateCameraFrame())
        );
        cameraSimulationTimer.setCycleCount(Animation.INDEFINITE);
        cameraSimulationTimer.play();
    }

    private void stopCameraSimulation() {
        if (cameraSimulationTimer != null) {
            cameraSimulationTimer.stop();
            cameraSimulationTimer = null;
        }
    }

    private void updateCameraFrame() {
        if (videoOn) {
            // Create a simulated camera frame with timestamp
            Image frame = createSimulatedCameraFrame();
            currentVideoFrame.set(frame);
        }
    }

    private Image createSimulatedCameraFrame() {
        try {
            // Create a canvas for the camera frame
            Canvas canvas = new Canvas(320, 240);
            GraphicsContext gc = canvas.getGraphicsContext2D();

            // Draw background with subtle animation
            double time = System.currentTimeMillis() % 5000 / 5000.0;
            Color bgColor = Color.hsb(time * 360, 0.1, 0.9);
            gc.setFill(bgColor);
            gc.fillRect(0, 0, 320, 240);

            // Draw camera frame border
            gc.setStroke(Color.DARKGRAY);
            gc.setLineWidth(2);
            gc.strokeRect(2, 2, 316, 236);

            // Draw live indicator
            gc.setFill(Color.RED);
            gc.fillOval(10, 10, 12, 12);
            gc.setFill(Color.WHITE);
            gc.fillText("LIVE", 25, 18);

            // Draw timestamp
            gc.setFill(Color.BLACK);
            String timestamp = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
            gc.fillText("Camera Feed: " + timestamp, 80, 30);

            // Draw some moving elements to simulate live video
            double x = 50 + Math.sin(time * Math.PI * 2) * 100;
            double y = 120 + Math.cos(time * Math.PI * 2) * 50;

            gc.setFill(Color.BLUE);
            gc.fillOval(x, y, 40, 40);

            // Draw quality indicator
            String quality = qualityComboBox != null ? qualityComboBox.getValue() : "High (1080p)";
            gc.fillText("Quality: " + quality, 100, 220);

            return canvas.snapshot(null, null);

        } catch (Exception e) {
            System.err.println("❌ Failed to create camera frame: " + e.getMessage());
            return createPlaceholderImage();
        }
    }

    private Image createPlaceholderImage() {
        try {
            Canvas canvas = new Canvas(320, 240);
            GraphicsContext gc = canvas.getGraphicsContext2D();

            gc.setFill(Color.LIGHTGRAY);
            gc.fillRect(0, 0, 320, 240);

            gc.setFill(Color.DARKGRAY);
            gc.fillText("Camera Off", 120, 120);
            gc.fillText("Click 'Start Video'", 110, 140);

            return canvas.snapshot(null, null);
        } catch (Exception e) {
            System.err.println("❌ Failed to create placeholder image: " + e.getMessage());
            return null;
        }
    }

    private void showCameraError() {
        if (videoStatusLabel != null) {
            videoStatusLabel.setText("Video: Camera Error ❌");
            videoStatusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
        }
    }

    @FXML
    protected void takeScreenshot() {
        if (videoOn && currentVideoFrame.get() != null) {
            // In a real implementation, you would save the current frame
            System.out.println("📸 Screenshot taken (simulated)");
            animateButton(screenshotButton);

            // Show success message
            javafx.application.Platform.runLater(() -> {
                javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                        javafx.scene.control.Alert.AlertType.INFORMATION);
                alert.setTitle("Screenshot");
                alert.setHeaderText(null);
                alert.setContentText("Screenshot captured successfully!\nThis would save the current camera frame.");
                alert.showAndWait();
            });
        } else {
            System.out.println("❌ Cannot take screenshot - video is off");
        }
    }

    @FXML
    protected void toggleRecording() {
        // Use the centralized recording control from HelloApplication
        HelloApplication.toggleRecording();

        // Update local state
        isRecording = HelloApplication.isRecording();
        updateRecordButton();
        updateStatusLabel();

        animateButton(recordButton);
    }

    private void onCameraChanged() {
        if (cameraComboBox != null) {
            String selectedCamera = cameraComboBox.getValue();
            System.out.println("📷 Camera changed to: " + selectedCamera);
            addSystemMessage("Switched to camera: " + selectedCamera);
        }
    }

    private void onQualityChanged() {
        if (qualityComboBox != null) {
            String selectedQuality = qualityComboBox.getValue();
            System.out.println("🎥 Video quality changed to: " + selectedQuality);
            addSystemMessage("Video quality set to: " + selectedQuality);
        }
    }

    private void onVirtualBackgroundToggled() {
        if (virtualBackgroundCheckBox != null) {
            virtualBackgroundEnabled = virtualBackgroundCheckBox.isSelected();
            String status = virtualBackgroundEnabled ? "enabled" : "disabled";
            System.out.println("🖼 Virtual background: " + status);
            addSystemMessage("Virtual background " + status);
        }
    }

    public void updateButtonStyles() {
        if (videoToggleButton != null) {
            if (videoOn) {
                videoToggleButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;");
                videoToggleButton.setText("Stop Video");
            } else {
                videoToggleButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;");
                videoToggleButton.setText("Start Video");
            }
        }
    }

    private void updateRecordButton() {
        if (recordButton != null) {
            if (isRecording) {
                recordButton.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold;");
                recordButton.setText("Stop Recording");
            } else {
                recordButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;");
                recordButton.setText("Start Recording");
            }
        }
    }

    private void updateStatusLabel() {
        if (videoStatusLabel != null) {
            if (videoOn) {
                if (isRecording) {
                    videoStatusLabel.setText("Video: On ● Recording 🔴");
                    videoStatusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                } else {
                    videoStatusLabel.setText("Video: On ● Live 🟢");
                    videoStatusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                }
            } else {
                videoStatusLabel.setText("Video: Off 🔴");
                videoStatusLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-weight: bold;");
            }
        }
    }

    private void animateButton(Button button) {
        if (button == null) return;

        ScaleTransition st = new ScaleTransition(Duration.millis(150), button);
        st.setFromX(1.0);
        st.setFromY(1.0);
        st.setToX(1.1);
        st.setToY(1.1);
        st.setAutoReverse(true);
        st.setCycleCount(2);
        st.play();
    }

    private void addSystemMessage(String message) {
        System.out.println("🎥 " + message);
    }

    // Method to sync with global state
    public void syncWithGlobalState() {
        videoOn = HelloApplication.isVideoOn();
        isRecording = HelloApplication.isRecording();
        virtualBackgroundEnabled = HelloApplication.isVirtualBackgroundEnabled();

        updateButtonStyles();
        updateRecordButton();
        updateStatusLabel();
    }

    // Method to update from server messages - REMOVED DUPLICATE
    public void updateFromServer(String videoStatus) {
        System.out.println("VideoControlsController: Received video status - " + videoStatus);

        Platform.runLater(() -> {
            switch (videoStatus) {
                case "HOST_VIDEO_STARTED":
                    if (!HelloApplication.isMeetingHost()) {
                        System.out.println("🎥 Host started video - showing indicator");
                        // Show host video indicator
                        showHostVideoIndicator(true);
                    }
                    break;

                case "HOST_VIDEO_STOPPED":
                    if (!HelloApplication.isMeetingHost()) {
                        System.out.println("🎥 Host stopped video - hiding indicator");
                        // Hide host video indicator
                        showHostVideoIndicator(false);
                    }
                    break;

                default:
                    if (videoStatus.contains("started video")) {
                        System.out.println("Another participant started their video");
                    } else if (videoStatus.contains("stopped video")) {
                        System.out.println("Another participant stopped their video");
                    }
                    break;
            }
        });
    }

    /**
     * Show host video indicator on client devices
     */
    private void showHostVideoIndicator(boolean show) {
        if (show) {
            // Create a visual indicator that host is streaming video
            Canvas canvas = new Canvas(200, 150);
            GraphicsContext gc = canvas.getGraphicsContext2D();
            gc.setFill(Color.DARKRED);
            gc.fillRect(0, 0, 200, 150);
            gc.setFill(Color.WHITE);
            gc.fillText("HOST VIDEO", 60, 70);
            gc.fillText("LIVE STREAM", 55, 90);

            Image hostVideoImage = canvas.snapshot(null, null);

            // Show in the main video display area
            MeetingController meetingController = MeetingController.getInstance();
            if (meetingController != null) {
                meetingController.showHostVideoIndicator(true);
            }

            // Also update the preview in video controls
            if (realCameraPreview != null) {
                realCameraPreview.setImage(hostVideoImage);
                realCameraPreview.setVisible(true);
                simulatedCameraPreview.setVisible(false);
            }
        } else {
            // Hide host video indicator
            if (realCameraPreview != null) {
                realCameraPreview.setImage(null);
                realCameraPreview.setVisible(false);
                simulatedCameraPreview.setVisible(true);
            }

            MeetingController meetingController = MeetingController.getInstance();
            if (meetingController != null) {
                meetingController.showHostVideoIndicator(false);
            }
        }
    }

    // NEW: Host control methods
    public void onHostStartedRecording() {
        System.out.println("🎥 Host started recording - updating UI");
        isRecording = true;
        updateRecordButton();
        updateStatusLabel();
    }

    public void onHostStoppedRecording() {
        System.out.println("🎥 Host stopped recording - updating UI");
        isRecording = false;
        updateRecordButton();
        updateStatusLabel();
    }

    // Method called when meeting host status changes
    public void onHostStatusChanged(boolean isHost) {
        System.out.println("VideoControls: Host status changed - " + (isHost ? "Host" : "Participant"));

        // Enable/disable recording button based on host status
        if (recordButton != null) {
            recordButton.setDisable(!isHost);
        }
    }

    // Method called when joining/leaving a meeting
    public void onMeetingStateChanged(boolean inMeeting) {
        if (inMeeting) {
            // Reset video states when joining a new meeting
            videoOn = false;
            isRecording = false;
            virtualBackgroundEnabled = false;

            updateButtonStyles();
            updateRecordButton();
            updateStatusLabel();

            System.out.println("VideoControls: Joined meeting - video controls activated");
        } else {
            // Meeting ended or left - stop video and recording
            if (videoOn) {
                stopVideo();
            }
            if (isRecording) {
                toggleRecording(); // Stop recording
            }

            if (videoStatusLabel != null) {
                videoStatusLabel.setText("Video: Not in Meeting");
                videoStatusLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-weight: bold;");
            }

            System.out.println("VideoControls: Left meeting - video controls deactivated");
        }
    }

    /**
     * Update video preview with real camera feed
     */
    public void updateVideoPreview(Image image) {
        if (realCameraPreview != null && image != null) {
            Platform.runLater(() -> {
                realCameraPreview.setImage(image);
                // Show real camera and hide simulated one
                realCameraPreview.setVisible(true);
                if (simulatedCameraPreview != null) {
                    simulatedCameraPreview.setVisible(false);
                }
            });
        }
    }

    /**
     * Get the real camera preview ImageView
     */
    public ImageView getRealCameraPreview() {
        return realCameraPreview;
    }

    /**
     * Reset to simulated camera
     */
    public void resetToSimulatedCamera() {
        Platform.runLater(() -> {
            if (realCameraPreview != null) {
                realCameraPreview.setVisible(false);
                realCameraPreview.setImage(null);
            }
            if (simulatedCameraPreview != null) {
                simulatedCameraPreview.setVisible(true);
            }
        });
    }

    // Cleanup method
    public void cleanup() {
        stopCameraSimulation();
        System.out.println("✅ Video controls cleaned up");
    }
}