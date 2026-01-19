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
    private CheckBox webRTCCheckBox;
    @FXML
    private StackPane videoPreviewContainer;

    // Separate ImageView for real camera feed
    private ImageView realCameraPreview;
    private ImageView simulatedCameraPreview;

    private boolean videoOn = false;
    private boolean isRecording = false;
    private boolean virtualBackgroundEnabled = false;
    private boolean webRTCEnabled = false;

    // Camera simulation
    private Timeline cameraSimulationTimer;
    private ObjectProperty<Image> currentVideoFrame;

    // Reference to MeetingController
    private MeetingController meetingController;

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

        System.out.println("🎥 VideoControlsController initialized successfully for user: " +
                HelloApplication.getLoggedInUser());
    }

    /**
     * Set the reference to MeetingController
     */
    public void setMeetingController(MeetingController meetingController) {
        this.meetingController = meetingController;
        System.out.println("✅ VideoControlsController connected to MeetingController");
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

        // Setup WebRTC checkbox
        setupWebRTCCheckbox();

        // Update status label
        updateStatusLabel();

        // Sync with current global state
        syncWithGlobalState();
    }

    private void setupWebRTCCheckbox() {
        if (webRTCCheckBox != null) {
            webRTCCheckBox.setSelected(HelloApplication.isWebRTCEnabled());
            webRTCCheckBox.setText("WebRTC " + (HelloApplication.isWebRTCEnabled() ? "Enabled" : "Disabled"));
            webRTCCheckBox.setOnAction(e -> onWebRTCToggled());
        }
    }

    private void onWebRTCToggled() {
        if (webRTCCheckBox.isSelected()) {
            HelloApplication.enableWebRTC();
            webRTCCheckBox.setText("WebRTC Enabled");
            System.out.println("✅ WebRTC enabled for video");
        } else {
            HelloApplication.disableWebRTC();
            webRTCCheckBox.setText("WebRTC Disabled");
            System.out.println("🛑 WebRTC disabled for video");
        }
    }

    public void displayVideoFrame(Image videoFrame) {
        Platform.runLater(() -> {
            if (realCameraPreview != null && videoFrame != null) {
                realCameraPreview.setImage(videoFrame);
                realCameraPreview.setVisible(true);

                // Show preview label
                if (videoStatusLabel != null) {
                    videoStatusLabel.setText("🟢 Live Preview");
                }
            }
        });
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
        System.out.println("🎬 VideoControlsController.toggleVideo() called");

        // Use the centralized control
        HelloApplication.toggleVideo();

        // Sync state
        syncWithGlobalState();
    }

    private void startVideo() {
        try {
            videoOn = true;
            updateButtonStyles();
            updateStatusLabel();

            // Start camera simulation
            startCameraSimulation();

            System.out.println("🎥 Video started for user: " + HelloApplication.getLoggedInUser());

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

        System.out.println("🎥 Video stopped for user: " + HelloApplication.getLoggedInUser());
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

            // Draw WebRTC status if enabled
            if (webRTCEnabled) {
                gc.setFill(Color.GREEN);
                gc.fillText("WebRTC", 250, 20);
            }

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
            System.out.println("📸 Screenshot taken (simulated)");
            animateButton(screenshotButton);

            Platform.runLater(() -> {
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
            if (meetingController != null) {
                meetingController.addSystemMessage("Switched to camera: " + selectedCamera);
            }
        }
    }

    private void onQualityChanged() {
        if (qualityComboBox != null) {
            String selectedQuality = qualityComboBox.getValue();
            System.out.println("🎥 Video quality changed to: " + selectedQuality);
            if (meetingController != null) {
                meetingController.addSystemMessage("Video quality set to: " + selectedQuality);
            }
        }
    }

    private void onVirtualBackgroundToggled() {
        if (virtualBackgroundCheckBox != null) {
            virtualBackgroundEnabled = virtualBackgroundCheckBox.isSelected();
            String status = virtualBackgroundEnabled ? "enabled" : "disabled";
            System.out.println("🖼 Virtual background: " + status);
            if (meetingController != null) {
                meetingController.addSystemMessage("Virtual background " + status);
            }
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

            // Disable recording button if not host
            recordButton.setDisable(!HelloApplication.isMeetingHost());
        }
    }

    private void updateStatusLabel() {
        if (videoStatusLabel != null) {
            if (videoOn) {
                if (isRecording) {
                    videoStatusLabel.setText("Video: On ● Recording");
                    videoStatusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                } else {
                    videoStatusLabel.setText("Video: On ● Live 🟢");
                    videoStatusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                }
            } else {
                videoStatusLabel.setText("Video: Off");
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

    // Method to sync with global state
    public void syncWithGlobalState() {
        videoOn = HelloApplication.isVideoOn();

        // Update button
        if (videoToggleButton != null) {
            if (videoOn) {
                videoToggleButton.setText("Stop Video");
                videoToggleButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
            } else {
                videoToggleButton.setText("Start Video");
                videoToggleButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
            }
        }

        // Update status
        if (videoStatusLabel != null) {
            videoStatusLabel.setText(videoOn ? "Streaming" : "Offline");
        }
    }

    /**
     * FIXED: Handle video status updates from other users
     */
    public void updateFromServer(String username, String videoStatus) {
        System.out.println("🎥 VideoControlsController: Received video status from " + username + ": " + videoStatus);

        // Don't update our own status
        if (username.equals(HelloApplication.getLoggedInUser())) {
            return;
        }

        Platform.runLater(() -> {
            switch (videoStatus) {
                case "VIDEO_STARTED":
                    if (meetingController != null) {
                        meetingController.addSystemMessage(username + " started their video");
                    }

                    // Update visual indicator for other users
                    if (HelloApplication.isMeetingHost()) {
                        // Host can show participant video indicators
                        showParticipantVideoIndicator(username, true);
                    } else {
                        // Client receiving host video
                        showHostVideoIndicator(true);
                    }
                    break;

                case "VIDEO_STOPPED":
                    if (meetingController != null) {
                        meetingController.addSystemMessage(username + " stopped their video");
                    }

                    if (HelloApplication.isMeetingHost()) {
                        showParticipantVideoIndicator(username, false);
                    } else {
                        showHostVideoIndicator(false);
                    }
                    break;
            }
        });
    }

    /**
     * Show host video indicator on client devices - IMPROVED
     */
    private void showHostVideoIndicator(boolean show) {
        Platform.runLater(() -> {
            if (show) {
                // Create a visual indicator that host is streaming video
                Canvas canvas = new Canvas(200, 150);
                GraphicsContext gc = canvas.getGraphicsContext2D();
                gc.setFill(Color.DARKBLUE);
                gc.fillRect(0, 0, 200, 150);
                gc.setFill(Color.WHITE);
                gc.fillText("HOST VIDEO", 60, 70);
                gc.fillText("WAITING FOR STREAM...", 40, 90);

                Image hostVideoImage = canvas.snapshot(null, null);

                // Show in the main video display area
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

                if (meetingController != null) {
                    meetingController.showHostVideoIndicator(false);
                }
            }
        });
    }

    /**
     * Show/hide participant video indicator (for host)
     */
    private void showParticipantVideoIndicator(String username, boolean show) {
        System.out.println("🎥 Participant " + username + " video: " + (show ? "ON" : "OFF"));
        // In a real implementation, you would update UI to show/hide participant video
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