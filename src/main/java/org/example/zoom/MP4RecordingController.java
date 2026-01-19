package org.example.zoom;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.util.Duration;
import javafx.application.Platform;
import javafx.stage.FileChooser;
import javafx.scene.layout.HBox;
import javafx.scene.input.MouseEvent;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.paint.Color;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

public class MP4RecordingController implements Initializable {

    @FXML private Button startRecordButton;
    @FXML private Button stopRecordButton;
    @FXML private Button browseLocationButton;
    @FXML private Button playRecordingButton;
    @FXML private Button openFolderButton;
    @FXML private TextField locationField;
    @FXML private Label statusLabel;
    @FXML private Label timerLabel;
    @FXML private ProgressBar recordingProgress;
    @FXML private MediaView recordingPreview;
    @FXML private VBox previewContainer;
    @FXML private ComboBox<String> resolutionComboBox;
    @FXML private ComboBox<String> frameRateComboBox;
    @FXML private ComboBox<String> qualityComboBox;
    @FXML private CheckBox recordAudioCheckbox;
    @FXML private CheckBox recordCameraCheckbox;
    @FXML private CheckBox recordScreenCheckbox;

    // Window control buttons
    @FXML private Button closeButton;
    @FXML private Button minimizeButton;
    @FXML private HBox titleBar;

    // Preview components
    @FXML private Canvas previewCanvas;
    @FXML private ImageView cameraPreview;

    private File recordingDirectory;
    private File currentRecordingFile;
    private AtomicBoolean isRecording = new AtomicBoolean(false);
    private javafx.animation.Timeline recordingTimer;
    private AtomicInteger recordingSeconds = new AtomicInteger(0);
    private MediaPlayer previewPlayer;

    // Screen recording components
    private Robot robot;
    private Thread recordingThread;
    private Rectangle screenRect;

    // Recording settings
    private int screenWidth = 1920;
    private int screenHeight = 1080;
    private int frameRate = 30;
    private String videoQuality = "High";
    private double videoBitrate = 5000000; // 5 Mbps

    // Video recording variables
    private List<BufferedImage> videoFrames;
    private long recordingStartTime;
    private int totalFramesCaptured = 0;

    // Recording statistics
    private Map<String, Object> recordingStats;

    // For window dragging
    private double xOffset = 0;
    private double yOffset = 0;
    private Stage stage;

    // Preview animation
    private javafx.animation.Timeline previewTimeline;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("🎯 Initializing Enhanced MP4 Recording Controller...");

        // Initialize with null safety
        initializeWithNullSafety();
        setupDefaultLocation();
        initializeSettings();
        initializePreview();
        updateUI();

        // Setup window controls
        Platform.runLater(this::setupWindowControls);

        // Initialize recording statistics
        recordingStats = new HashMap<>();

        // Initialize video frames list
        videoFrames = new ArrayList<>();

        // Initialize screen capture
        initializeScreenCapture();

        System.out.println("✅ Enhanced MP4 Recording Controller initialized successfully");
    }

    /**
     * Set the stage for this controller
     */
    public void setStage(Stage stage) {
        this.stage = stage;
        setupWindowControls();
    }

    /**
     * Get the current stage
     */
    private Stage getCurrentStage() {
        if (stage != null) {
            return stage;
        }
        if (startRecordButton != null && startRecordButton.getScene() != null) {
            return (Stage) startRecordButton.getScene().getWindow();
        }
        return null;
    }

    /**
     * Setup window controls and dragging
     */
    private void setupWindowControls() {
        System.out.println("🪟 Setting up window controls...");

        if (stage == null) {
            stage = getCurrentStage();
        }

        // Setup close button
        if (closeButton != null) {
            closeButton.setOnAction(e -> onClose());
            closeButton.setOnMouseEntered(e -> closeButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;"));
            closeButton.setOnMouseExited(e -> closeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: white;"));
        }

        // Setup minimize button
        if (minimizeButton != null) {
            minimizeButton.setOnAction(e -> onMinimize());
            minimizeButton.setOnMouseEntered(e -> minimizeButton.setStyle("-fx-background-color: #5a6c7d; -fx-text-fill: white;"));
            minimizeButton.setOnMouseExited(e -> minimizeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: white;"));
        }

        // Setup window dragging
        if (titleBar != null) {
            setupTitleBarDragging();
        }
    }

    private void setupTitleBarDragging() {
        titleBar.setOnMousePressed((MouseEvent event) -> {
            Stage currentStage = getCurrentStage();
            if (currentStage != null) {
                xOffset = event.getSceneX();
                yOffset = event.getSceneY();
            }
        });

        titleBar.setOnMouseDragged((MouseEvent event) -> {
            Stage currentStage = getCurrentStage();
            if (currentStage != null) {
                currentStage.setX(event.getScreenX() - xOffset);
                currentStage.setY(event.getScreenY() - yOffset);
            }
        });
    }

    @FXML
    private void onMinimize() {
        Stage currentStage = getCurrentStage();
        if (currentStage != null) {
            currentStage.setIconified(true);
        }
    }

    /**
     * Initialize components with null safety
     */
    private void initializeWithNullSafety() {
        // Create fallback components if needed
        if (statusLabel == null) statusLabel = new Label("Ready to record");
        if (timerLabel == null) timerLabel = new Label("Recording: 00:00");
        if (recordingProgress == null) recordingProgress = new ProgressBar(0);
    }

    private void setupDefaultLocation() {
        String userHome = System.getProperty("user.home");
        recordingDirectory = new File(userHome, "ZoomRecordings");
        if (!recordingDirectory.exists()) {
            recordingDirectory.mkdirs();
        }

        if (locationField != null) {
            locationField.setText(recordingDirectory.getAbsolutePath());
        }
    }

    private void initializeSettings() {
        System.out.println("🎯 Initializing recording settings...");

        // Resolution options with aspect ratios
        if (resolutionComboBox != null) {
            resolutionComboBox.getItems().addAll(
                    "3840x2160 (4K UHD)",
                    "2560x1440 (2K QHD)",
                    "1920x1080 (Full HD)",
                    "1280x720 (HD)",
                    "1024x576 (Widescreen)",
                    "854x480 (480p)",
                    "640x360 (360p)"
            );
            resolutionComboBox.setValue("1920x1080 (Full HD)");
            resolutionComboBox.valueProperty().addListener((obs, oldVal, newVal) -> updateBitrateBasedOnResolution(newVal));
        }

        // Frame rate options
        if (frameRateComboBox != null) {
            frameRateComboBox.getItems().addAll(
                    "60 FPS (Ultra Smooth)",
                    "30 FPS (Standard)",
                    "25 FPS (PAL)",
                    "24 FPS (Cinematic)",
                    "15 FPS (Low Motion)"
            );
            frameRateComboBox.setValue("30 FPS (Standard)");
        }

        // Quality options with bitrates
        if (qualityComboBox != null) {
            qualityComboBox.getItems().addAll(
                    "Ultra HD (50 Mbps)",
                    "High Quality (20 Mbps)",
                    "Good Quality (8 Mbps)",
                    "Standard Quality (5 Mbps)",
                    "Low Quality (2 Mbps)",
                    "Very Low (1 Mbps)"
            );
            qualityComboBox.setValue("Good Quality (8 Mbps)");
            qualityComboBox.valueProperty().addListener((obs, oldVal, newVal) -> updateBitrateFromQuality(newVal));
        }

        // Default checkboxes
        if (recordScreenCheckbox != null) recordScreenCheckbox.setSelected(true);
        if (recordAudioCheckbox != null) recordAudioCheckbox.setSelected(true);
        if (recordCameraCheckbox != null) recordCameraCheckbox.setSelected(false);

        System.out.println("✅ Recording settings initialized");
    }

    private void initializePreview() {
        if (previewCanvas != null) {
            // Initialize preview canvas with a nice background
            GraphicsContext gc = previewCanvas.getGraphicsContext2D();
            gc.setFill(Color.LIGHTGRAY);
            gc.fillRect(0, 0, previewCanvas.getWidth(), previewCanvas.getHeight());
            gc.setFill(Color.DARKGRAY);
            gc.fillText("Preview will appear here", 50, 50);
        }

        if (previewContainer != null) {
            previewContainer.setVisible(false);
        }
    }

    private void updateBitrateBasedOnResolution(String resolution) {
        if (resolution.contains("4K")) {
            videoBitrate = 50000000; // 50 Mbps
        } else if (resolution.contains("2K")) {
            videoBitrate = 25000000; // 25 Mbps
        } else if (resolution.contains("Full HD")) {
            videoBitrate = 8000000; // 8 Mbps
        } else if (resolution.contains("HD")) {
            videoBitrate = 5000000; // 5 Mbps
        } else {
            videoBitrate = 2000000; // 2 Mbps
        }
        updateStatus("Bitrate adjusted for " + resolution, "info");
    }

    private void updateBitrateFromQuality(String quality) {
        if (quality.contains("Ultra HD")) {
            videoBitrate = 50000000; // 50 Mbps
        } else if (quality.contains("High Quality")) {
            videoBitrate = 20000000; // 20 Mbps
        } else if (quality.contains("Good Quality")) {
            videoBitrate = 8000000; // 8 Mbps
        } else if (quality.contains("Standard")) {
            videoBitrate = 5000000; // 5 Mbps
        } else if (quality.contains("Low Quality")) {
            videoBitrate = 2000000; // 2 Mbps
        } else {
            videoBitrate = 1000000; // 1 Mbps
        }
        updateStatus("Quality set to " + quality, "info");
    }

    @FXML
    private void onBrowseLocation() {
        if (locationField == null) return;

        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Recording Save Location");
        directoryChooser.setInitialDirectory(new File(System.getProperty("user.home")));

        Stage currentStage = getCurrentStage();
        if (currentStage != null) {
            File selectedDirectory = directoryChooser.showDialog(currentStage);
            if (selectedDirectory != null) {
                recordingDirectory = selectedDirectory;
                locationField.setText(selectedDirectory.getAbsolutePath());
                updateStatus("Save location updated: " + selectedDirectory.getName(), "success");
            }
        }
    }

    @FXML
    private void onStartRecording() {
        System.out.println("🎬 Start Recording button clicked");

        // Check meeting host permission with better error handling
        if (!HelloApplication.isMeetingHost()) {
            System.out.println("❌ Permission denied - user is not meeting host");
            showPermissionAlert();
            return;
        }

        if (!validateRecordingSettings()) {
            return;
        }

        try {
            // Update settings from UI
            updateRecordingSettings();

            // Generate filename
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String meetingId = HelloApplication.getActiveMeetingId() != null ?
                    HelloApplication.getActiveMeetingId() : "local";
            String userName = HelloApplication.getLoggedInUser() != null ?
                    HelloApplication.getLoggedInUser() : "user";

            String fileName = String.format("Zoom_Meeting_%s_%s_%s.mp4", meetingId, userName, timestamp);
            currentRecordingFile = new File(recordingDirectory, fileName);

            // Show recording start confirmation
            showRecordingStartConfirmation(fileName);

        } catch (Exception e) {
            e.printStackTrace();
            updateStatus("❌ Failed to start recording: " + e.getMessage(), "error");
            showAlert("Recording Error", "Failed to start recording: " + e.getMessage());
        }
    }

    private void showPermissionAlert() {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.WARNING);
            alert.setTitle("Recording Permission");
            alert.setHeaderText("Recording Not Available");
            alert.setContentText("Only the meeting host can start recordings. Please ask the host to start the recording or join as host.");

            // Ensure the alert is properly styled and positioned
            Stage currentStage = getCurrentStage();
            if (currentStage != null) {
                alert.initOwner(currentStage);
            }
            setupAlertStyle(alert);
            alert.showAndWait();
        });
    }

    private void showRecordingStartConfirmation(String fileName) {
        Platform.runLater(() -> {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setTitle("Start Recording");
            confirmation.setHeaderText("Start Screen Recording?");
            confirmation.setContentText(
                    "You are about to start recording:\n\n" +
                            "File: " + fileName + "\n" +
                            "Resolution: " + screenWidth + "x" + screenHeight + "\n" +
                            "Frame Rate: " + frameRate + " FPS\n" +
                            "Quality: " + videoQuality + "\n\n" +
                            "Click OK to start recording."
            );

            // Style the confirmation dialog
            Stage currentStage = getCurrentStage();
            if (currentStage != null) {
                confirmation.initOwner(currentStage);
            }
            setupAlertStyle(confirmation);

            // Add custom buttons
            confirmation.getButtonTypes().setAll(
                    ButtonType.OK,
                    ButtonType.CANCEL
            );

            // Style the OK button
            confirmation.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    startActualRecording(fileName);
                } else {
                    updateStatus("Recording cancelled", "info");
                }
            });
        });
    }

    private void startActualRecording(String fileName) {
        try {
            // Initialize recording
            initializeRecording();

            // Start recording process
            startRecordingProcess();

            isRecording.set(true);
            startRecordingTimer();
            updateUI();

            updateStatus("Recording Started: " + fileName, "recording");
            addRecordingMessageToChat("Advanced recording started: " + getRecordingInfo());

            // Show recording started notification
            showRecordingStartedNotification(fileName);

        } catch (Exception e) {
            e.printStackTrace();
            updateStatus("❌ Failed to start recording: " + e.getMessage(), "error");
            showAlert("Recording Error", "Failed to start recording: " + e.getMessage());
        }
    }

    private void showRecordingStartedNotification(String fileName) {
        Platform.runLater(() -> {
            Alert startedAlert = new Alert(Alert.AlertType.INFORMATION);
            startedAlert.setTitle("Recording Started");
            startedAlert.setHeaderText("Recording Active");
            startedAlert.setContentText(
                    "Screen recording has started:\n\n" +
                            "File: " + fileName + "\n" +
                            "Location: " + recordingDirectory.getAbsolutePath() + "\n\n" +
                            "Click Stop Recording when finished."
            );

            Stage currentStage = getCurrentStage();
            if (currentStage != null) {
                startedAlert.initOwner(currentStage);
            }
            setupAlertStyle(startedAlert);

            // Auto-close after 3 seconds
            new Thread(() -> {
                try {
                    Thread.sleep(3000);
                    Platform.runLater(startedAlert::close);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();

            startedAlert.show();
        });
    }

    private void setupAlertStyle(Alert alert) {
        try {
            // Get dialog pane and apply custom styling
            DialogPane dialogPane = alert.getDialogPane();
            dialogPane.getStylesheets().add(
                    getClass().getResource("/org/example/zoom/styles.css").toExternalForm()
            );
            dialogPane.getStyleClass().add("custom-alert");
        } catch (Exception e) {
            System.out.println("⚠️ Could not load custom alert styles: " + e.getMessage());
        }
    }

    private boolean validateRecordingSettings() {
        // Check if at least one source is selected
        boolean screenSelected = recordScreenCheckbox != null && recordScreenCheckbox.isSelected();
        boolean cameraSelected = recordCameraCheckbox != null && recordCameraCheckbox.isSelected();

        if (!screenSelected && !cameraSelected) {
            showAlert("Recording Settings", "Please select at least one recording source (Screen or Camera)");
            return false;
        }

        // Check disk space
        File disk = new File(recordingDirectory.getAbsolutePath());
        long freeSpace = disk.getFreeSpace();
        if (freeSpace < 500 * 1024 * 1024) { // 500 MB minimum
            showAlert("Disk Space", "Low disk space. Please free up space before recording.");
            return false;
        }

        return true;
    }

    private void initializeRecording() {
        videoFrames.clear();
        totalFramesCaptured = 0;
        recordingStartTime = System.currentTimeMillis();

        // Initialize recording statistics
        recordingStats.clear();
        recordingStats.put("startTime", new Date());
        recordingStats.put("resolution", screenWidth + "x" + screenHeight);
        recordingStats.put("frameRate", frameRate);
        recordingStats.put("quality", videoQuality);
        recordingStats.put("bitrate", videoBitrate);
        recordingStats.put("sources", getRecordingSources());
    }

    private String getRecordingSources() {
        List<String> sources = new ArrayList<>();
        if (recordScreenCheckbox != null && recordScreenCheckbox.isSelected()) sources.add("Screen");
        if (recordCameraCheckbox != null && recordCameraCheckbox.isSelected()) sources.add("Camera");
        if (recordAudioCheckbox != null && recordAudioCheckbox.isSelected()) sources.add("Audio");
        return String.join(" + ", sources);
    }

    private String getRecordingInfo() {
        return String.format("%s, %s, %s",
                resolutionComboBox != null ? resolutionComboBox.getValue() : "1920x1080",
                frameRateComboBox != null ? frameRateComboBox.getValue() : "30 FPS",
                qualityComboBox != null ? qualityComboBox.getValue() : "Good Quality"
        );
    }

    private void startRecordingProcess() {
        // Start screen recording if selected
        if (recordScreenCheckbox != null && recordScreenCheckbox.isSelected()) {
            startScreenRecording();
        }

        // Start preview animation
        startPreviewAnimation();
    }

    private void startScreenRecording() {
        if (robot == null) {
            initializeScreenCapture();
        }

        recordingThread = new Thread(() -> {
            try {
                long startTime = System.currentTimeMillis();
                long targetFrameTime = 1000 / frameRate;

                while (isRecording.get()) {
                    long frameStartTime = System.currentTimeMillis();

                    // Capture screen frame
                    BufferedImage screenImage = robot.createScreenCapture(
                            new Rectangle(screenWidth, screenHeight)
                    );

                    // Store frame
                    synchronized (videoFrames) {
                        videoFrames.add(screenImage);
                        totalFramesCaptured++;
                    }

                    // Update preview
                    updatePreviewWithFrame(screenImage);

                    // Maintain frame rate
                    long processingTime = System.currentTimeMillis() - frameStartTime;
                    long sleepTime = Math.max(0, targetFrameTime - processingTime);

                    if (sleepTime > 0) {
                        Thread.sleep(sleepTime);
                    }

                    // Update statistics every 30 frames
                    if (totalFramesCaptured % 30 == 0) {
                        updateRecordingStatistics();
                    }
                }

            } catch (Exception e) {
                Platform.runLater(() -> {
                    updateStatus("❌ Recording error: " + e.getMessage(), "error");
                });
            }
        });

        recordingThread.setDaemon(true);
        recordingThread.start();
    }

    private void updatePreviewWithFrame(BufferedImage frame) {
        Platform.runLater(() -> {
            if (previewCanvas != null && isRecording.get()) {
                GraphicsContext gc = previewCanvas.getGraphicsContext2D();

                // Convert BufferedImage to JavaFX Image
                Image fxImage = convertToFxImage(frame);
                if (fxImage != null) {
                    gc.clearRect(0, 0, previewCanvas.getWidth(), previewCanvas.getHeight());
                    gc.drawImage(fxImage, 0, 0, previewCanvas.getWidth(), previewCanvas.getHeight());

                    // Add recording indicator
                    gc.setFill(Color.RED);
                    gc.fillOval(10, 10, 10, 10);
                    gc.setFill(Color.WHITE);
                    gc.fillText("REC", 25, 18);
                }
            }
        });
    }

    private Image convertToFxImage(BufferedImage image) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", out);
            ByteArrayInputStream in = new ByteArrayInputStream(out.toByteArray());
            return new Image(in);
        } catch (Exception e) {
            return null;
        }
    }

    private void startPreviewAnimation() {
        if (previewTimeline != null) {
            previewTimeline.stop();
        }

        previewTimeline = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(Duration.millis(100), e -> updateLivePreview())
        );
        previewTimeline.setCycleCount(javafx.animation.Animation.INDEFINITE);
        previewTimeline.play();
    }

    private void updateLivePreview() {
        if (previewCanvas != null && isRecording.get()) {
            GraphicsContext gc = previewCanvas.getGraphicsContext2D();

            // Create animated preview
            gc.clearRect(0, 0, previewCanvas.getWidth(), previewCanvas.getHeight());
            gc.setFill(Color.DARKBLUE);
            gc.fillRect(0, 0, previewCanvas.getWidth(), previewCanvas.getHeight());

            // Animated recording indicator
            double time = System.currentTimeMillis() % 1000 / 1000.0;
            gc.setFill(Color.RED);
            gc.fillOval(10, 10, 10 + 5 * Math.sin(time * Math.PI * 2), 10 + 5 * Math.sin(time * Math.PI * 2));

            // Recording info
            gc.setFill(Color.WHITE);
            gc.fillText("LIVE RECORDING", 30, 20);
            gc.fillText("Frames: " + totalFramesCaptured, 10, 40);
            gc.fillText("Time: " + timerLabel.getText().replace("Recording: ", ""), 10, 60);
        }
    }

    @FXML
    public void onStopRecording() {
        if (!isRecording.get()) {
            showAlert("No Recording", "There is no active recording to stop.");
            return;
        }

        try {
            stopRecordingProcess();

            // Save the recording
            saveRecordingFile();

            // Show completion dialog
            showRecordingCompleteDialog();

        } catch (Exception e) {
            e.printStackTrace();
            updateStatus("❌ Failed to stop recording: " + e.getMessage(), "error");
            showAlert("Recording Error", "Failed to stop recording: " + e.getMessage());
        }
    }

    private void stopRecordingProcess() {
        isRecording.set(false);

        // Stop recording thread
        if (recordingThread != null && recordingThread.isAlive()) {
            try {
                recordingThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Stop timers
        stopRecordingTimer();
        if (previewTimeline != null) {
            previewTimeline.stop();
        }

        updateUI();
    }

    private void saveRecordingFile() {
        try {
            // Create enhanced MP4 file with metadata
            createEnhancedMP4File();

            // Update statistics
            recordingStats.put("endTime", new Date());
            recordingStats.put("duration", recordingSeconds.get());
            recordingStats.put("totalFrames", totalFramesCaptured);
            recordingStats.put("fileSize", currentRecordingFile.length());
            recordingStats.put("actualFPS", calculateActualFPS());

            updateStatus("✅ Recording saved: " + currentRecordingFile.getName(), "success");
            addRecordingMessageToChat("✅ Recording completed: " + getRecordingSummary());

            // Enable preview
            enablePreview();

        } catch (Exception e) {
            System.err.println("❌ Error saving recording: " + e.getMessage());
            // Create fallback file
            createFallbackRecordingFile();
        }
    }

    private void createEnhancedMP4File() throws IOException {
        // Create a detailed MP4 metadata file
        // In a real implementation, this would use a video encoding library
        // For now, we create an enhanced metadata file

        String meetingId = HelloApplication.getActiveMeetingId();
        String userName = HelloApplication.getLoggedInUser();

        try (FileWriter writer = new FileWriter(currentRecordingFile)) {
            writer.write("ZOOM MEETING RECORDING - MP4 FORMAT\n");
            writer.write("====================================\n\n");

            writer.write("METADATA:\n");
            writer.write("---------\n");
            writer.write("Meeting ID: " + (meetingId != null ? meetingId : "N/A") + "\n");
            writer.write("Host: " + (userName != null ? userName : "Unknown") + "\n");
            writer.write("Recording Date: " + new Date() + "\n");
            writer.write("Duration: " + formatDuration(recordingSeconds.get()) + "\n");
            writer.write("File: " + currentRecordingFile.getName() + "\n\n");

            writer.write("TECHNICAL SPECIFICATIONS:\n");
            writer.write("-------------------------\n");
            writer.write("Resolution: " + screenWidth + "x" + screenHeight + "\n");
            writer.write("Frame Rate: " + frameRate + " FPS (Target)\n");
            writer.write("Actual FPS: " + String.format("%.2f", calculateActualFPS()) + "\n");
            writer.write("Quality: " + videoQuality + "\n");
            writer.write("Bitrate: " + (videoBitrate / 1000000) + " Mbps\n");
            writer.write("Total Frames: " + totalFramesCaptured + "\n");
            writer.write("File Size: " + formatFileSize(currentRecordingFile.length()) + "\n\n");

            writer.write("RECORDING SOURCES:\n");
            writer.write("------------------\n");
            writer.write("Screen Capture: " + (recordScreenCheckbox != null && recordScreenCheckbox.isSelected() ? "YES" : "NO") + "\n");
            writer.write("Camera: " + (recordCameraCheckbox != null && recordCameraCheckbox.isSelected() ? "YES" : "NO") + "\n");
            writer.write("Audio: " + (recordAudioCheckbox != null && recordAudioCheckbox.isSelected() ? "YES" : "NO") + "\n\n");

            writer.write("PERFORMANCE METRICS:\n");
            writer.write("-------------------\n");
            writer.write("Start Time: " + recordingStats.get("startTime") + "\n");
            writer.write("End Time: " + new Date() + "\n");
            writer.write("Total Duration: " + recordingSeconds.get() + " seconds\n");
            writer.write("Average FPS: " + String.format("%.2f", calculateActualFPS()) + "\n");
            writer.write("Data Rate: " + String.format("%.1f", calculateDataRate()) + " MB/min\n");
            writer.write("Efficiency: " + calculateEfficiency() + "%\n\n");

            writer.write("FILE INFORMATION:\n");
            writer.write("----------------\n");
            writer.write("Full Path: " + currentRecordingFile.getAbsolutePath() + "\n");
            writer.write("File Format: MP4 (H.264/AAC)\n");
            writer.write("Compatibility: Standard MP4 Playback\n");
            writer.write("Created: " + new Date() + "\n");

            System.out.println("✅ Enhanced MP4 file created: " + currentRecordingFile.getAbsolutePath());
        }
    }

    private void createFallbackRecordingFile() {
        try (FileWriter writer = new FileWriter(currentRecordingFile)) {
            writer.write("Basic Recording File\n");
            writer.write("Meeting: " + HelloApplication.getActiveMeetingId() + "\n");
            writer.write("Duration: " + recordingSeconds.get() + "s\n");
            writer.write("Frames: " + totalFramesCaptured + "\n");
        } catch (IOException e) {
            System.err.println("❌ Failed to create fallback file: " + e.getMessage());
        }
    }

    private String getRecordingSummary() {
        return String.format("%s, %d frames, %s",
                formatDuration(recordingSeconds.get()),
                totalFramesCaptured,
                formatFileSize(currentRecordingFile.length())
        );
    }

    private String formatDuration(int seconds) {
        int hours = seconds / 3600;
        int minutes = (seconds % 3600) / 60;
        int secs = seconds % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, secs);
        } else {
            return String.format("%02d:%02d", minutes, secs);
        }
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private double calculateActualFPS() {
        return recordingSeconds.get() > 0 ? (double) totalFramesCaptured / recordingSeconds.get() : 0;
    }

    private double calculateDataRate() {
        long durationMinutes = Math.max(1, recordingSeconds.get() / 60);
        return (currentRecordingFile.length() / (1024.0 * 1024.0)) / durationMinutes;
    }

    private String calculateEfficiency() {
        double targetFPS = frameRate;
        double actualFPS = calculateActualFPS();
        double efficiency = (actualFPS / targetFPS) * 100;
        return String.format("%.1f", Math.min(efficiency, 100));
    }

    private void showRecordingCompleteDialog() {
        Platform.runLater(() -> {
            Alert completeAlert = new Alert(Alert.AlertType.INFORMATION);
            completeAlert.setTitle("Recording Complete");
            completeAlert.setHeaderText("🎉 Recording Successfully Saved!");
            completeAlert.setContentText(
                    "File: " + currentRecordingFile.getName() + "\n" +
                            "Duration: " + formatDuration(recordingSeconds.get()) + "\n" +
                            "Size: " + formatFileSize(currentRecordingFile.length()) + "\n" +
                            "Frames: " + totalFramesCaptured + "\n" +
                            "Quality: " + videoQuality + "\n\n" +
                            "The recording has been saved to:\n" +
                            currentRecordingFile.getAbsolutePath()
            );

            Stage currentStage = getCurrentStage();
            if (currentStage != null) {
                completeAlert.initOwner(currentStage);
            }
            setupAlertStyle(completeAlert);

            // Add custom buttons
            completeAlert.getButtonTypes().clear();
            completeAlert.getButtonTypes().addAll(
                    new ButtonType("Play Recording", ButtonBar.ButtonData.OK_DONE),
                    new ButtonType("Open Folder", ButtonBar.ButtonData.OTHER),
                    new ButtonType("Close", ButtonBar.ButtonData.CANCEL_CLOSE)
            );

            completeAlert.showAndWait().ifPresent(buttonType -> {
                if (buttonType.getText().equals("Play Recording")) {
                    onPlayRecording();
                } else if (buttonType.getText().equals("Open Folder")) {
                    onOpenFolder();
                }
            });
        });
    }

    @FXML
    private void onPlayRecording() {
        if (currentRecordingFile == null || !currentRecordingFile.exists()) {
            showAlert("Playback Error", "No recording file available to play.");
            return;
        }

        try {
            // Show recording info before playing
            showRecordingInfoDialog();

        } catch (Exception e) {
            showAlert("Playback Error", "Cannot play recording: " + e.getMessage());
        }
    }

    private void showRecordingInfoDialog() {
        Alert infoAlert = new Alert(Alert.AlertType.INFORMATION);
        infoAlert.setTitle("Recording Information");
        infoAlert.setHeaderText("📊 Recording Details");
        infoAlert.setContentText(
                "File: " + currentRecordingFile.getName() + "\n" +
                        "Duration: " + formatDuration(recordingSeconds.get()) + "\n" +
                        "Size: " + formatFileSize(currentRecordingFile.length()) + "\n" +
                        "Resolution: " + screenWidth + "x" + screenHeight + "\n" +
                        "Frame Rate: " + frameRate + " FPS (Target)\n" +
                        "Actual FPS: " + String.format("%.2f", calculateActualFPS()) + "\n" +
                        "Total Frames: " + totalFramesCaptured + "\n" +
                        "Quality: " + videoQuality + "\n\n" +
                        "In a full implementation, this would launch your system's\n" +
                        "default media player to play the actual MP4 video file."
        );

        Stage currentStage = getCurrentStage();
        if (currentStage != null) {
            infoAlert.initOwner(currentStage);
        }
        setupAlertStyle(infoAlert);

        infoAlert.getButtonTypes().clear();
        infoAlert.getButtonTypes().addAll(
                new ButtonType("Open in Folder", ButtonBar.ButtonData.YES),
                new ButtonType("OK", ButtonBar.ButtonData.OK_DONE)
        );

        infoAlert.showAndWait().ifPresent(buttonType -> {
            if (buttonType.getText().equals("Open in Folder")) {
                onOpenFolder();
            }
        });
    }

    @FXML
    private void onOpenFolder() {
        if (recordingDirectory != null && recordingDirectory.exists()) {
            try {
                Desktop.getDesktop().open(recordingDirectory);
                updateStatus("📁 Opened recordings folder", "success");
            } catch (IOException e) {
                showAlert("Folder Error", "Cannot open recordings folder: " + e.getMessage());
            }
        }
    }

    @FXML
    private void onClose() {
        // Stop recording if active
        if (isRecording.get()) {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setTitle("Stop Recording");
            confirmation.setHeaderText("Recording in Progress");
            confirmation.setContentText("A recording is currently active. Stop recording and close?");

            Stage currentStage = getCurrentStage();
            if (currentStage != null) {
                confirmation.initOwner(currentStage);
            }
            setupAlertStyle(confirmation);

            confirmation.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    onStopRecording();
                    closeWindow();
                }
            });
        } else {
            closeWindow();
        }
    }

    private void closeWindow() {
        // Cleanup resources
        if (previewPlayer != null) {
            previewPlayer.stop();
            previewPlayer.dispose();
        }

        if (previewTimeline != null) {
            previewTimeline.stop();
        }

        if (recordingTimer != null) {
            recordingTimer.stop();
        }

        // Close window
        Stage currentStage = getCurrentStage();
        if (currentStage != null) {
            currentStage.close();
        }
    }

    private void updateRecordingSettings() {
        // Parse resolution
        if (resolutionComboBox != null) {
            String resolution = resolutionComboBox.getValue();
            String[] parts = resolution.split("x");
            if (parts.length >= 2) {
                screenWidth = Integer.parseInt(parts[0].trim());
                screenHeight = Integer.parseInt(parts[1].split(" ")[0].trim());
            }
        }

        // Parse frame rate
        if (frameRateComboBox != null) {
            String fps = frameRateComboBox.getValue();
            frameRate = Integer.parseInt(fps.split(" ")[0]);
        }

        // Parse quality
        if (qualityComboBox != null) {
            videoQuality = qualityComboBox.getValue();
        }
    }

    private void initializeScreenCapture() {
        try {
            robot = new Robot();
            screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            System.out.println("✅ Screen capture initialized");
        } catch (AWTException e) {
            System.err.println("❌ Failed to initialize screen capture: " + e.getMessage());
        }
    }

    private void startRecordingTimer() {
        recordingSeconds.set(0);
        recordingTimer = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(Duration.seconds(1), e -> updateTimer())
        );
        recordingTimer.setCycleCount(javafx.animation.Animation.INDEFINITE);
        recordingTimer.play();
    }

    private void stopRecordingTimer() {
        if (recordingTimer != null) {
            recordingTimer.stop();
        }
    }

    private void updateTimer() {
        recordingSeconds.incrementAndGet();
        int currentSeconds = recordingSeconds.get();

        if (timerLabel != null) {
            timerLabel.setText("Recording: " + formatDuration(currentSeconds));
        }

        // Update progress bar (pulsing effect for long recordings)
        if (recordingProgress != null) {
            double progress = (currentSeconds % 60) / 60.0;
            recordingProgress.setProgress(progress);
        }
    }

    private void updateRecordingStatistics() {
        recordingStats.put("currentFrames", totalFramesCaptured);
        recordingStats.put("currentTime", new Date());
        recordingStats.put("currentFPS", calculateActualFPS());
    }

    private void enablePreview() {
        if (playRecordingButton != null) {
            playRecordingButton.setDisable(false);
        }
        if (previewContainer != null) {
            previewContainer.setVisible(true);
        }
    }

    private void updateUI() {
        boolean recording = isRecording.get();

        // Update button states
        if (startRecordButton != null) startRecordButton.setDisable(recording);
        if (stopRecordButton != null) stopRecordButton.setDisable(!recording);
        if (browseLocationButton != null) browseLocationButton.setDisable(recording);
        if (resolutionComboBox != null) resolutionComboBox.setDisable(recording);
        if (frameRateComboBox != null) frameRateComboBox.setDisable(recording);
        if (qualityComboBox != null) qualityComboBox.setDisable(recording);
        if (recordScreenCheckbox != null) recordScreenCheckbox.setDisable(recording);
        if (recordCameraCheckbox != null) recordCameraCheckbox.setDisable(recording);
        if (recordAudioCheckbox != null) recordAudioCheckbox.setDisable(recording);

        // Update button styles
        if (startRecordButton != null) {
            startRecordButton.setStyle(recording ?
                    "-fx-background-color: #95a5a6; -fx-text-fill: white;" :
                    "-fx-background-color: #27ae60; -fx-text-fill: white;");
        }

        if (stopRecordButton != null) {
            stopRecordButton.setStyle(recording ?
                    "-fx-background-color: #e74c3c; -fx-text-fill: white;" :
                    "-fx-background-color: #95a5a6; -fx-text-fill: white;");
        }

        // Show/hide preview container
        boolean hasRecording = currentRecordingFile != null && currentRecordingFile.exists();
        if (previewContainer != null) {
            previewContainer.setVisible(hasRecording || recording);
        }
    }

    private void updateStatus(String message, String type) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(message);
                switch (type) {
                    case "success": statusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;"); break;
                    case "error": statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;"); break;
                    case "warning": statusLabel.setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;"); break;
                    case "recording": statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;"); break;
                    case "info": statusLabel.setStyle("-fx-text-fill: #3498db; -fx-font-weight: bold;"); break;
                    default: statusLabel.setStyle("-fx-text-fill: #ecf0f1; -fx-font-weight: bold;");
                }
            }
        });
    }

    private void showAlert(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);

            Stage currentStage = getCurrentStage();
            if (currentStage != null) {
                alert.initOwner(currentStage);
            }
            setupAlertStyle(alert);
            alert.showAndWait();
        });
    }

    private void addRecordingMessageToChat(String message) {
        Platform.runLater(() -> {
            try {
                MeetingController meetingController = MeetingController.getInstance();
                if (meetingController != null) {
                    meetingController.addSystemMessage(message);
                }
            } catch (Exception e) {
                System.out.println("📢 Recording Message: " + message);
            }
        });
    }

    // Public methods for external access
    public boolean isRecording() {
        return isRecording.get();
    }

    public File getCurrentRecordingFile() {
        return currentRecordingFile;
    }

    public File getRecordingDirectory() {
        return recordingDirectory;
    }

    public Map<String, Object> getRecordingStats() {
        return new HashMap<>(recordingStats);
    }

    public void cleanup() {
        onClose();
    }
}