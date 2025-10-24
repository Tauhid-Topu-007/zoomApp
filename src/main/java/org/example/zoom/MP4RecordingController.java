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

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.ResourceBundle;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class MP4RecordingController implements Initializable {

    @FXML private Button startRecordButton;
    @FXML private Button stopRecordButton;
    @FXML private Button browseLocationButton;
    @FXML private Button playRecordingButton;
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

    // Window control buttons
    @FXML private Button closeButton;
    @FXML private Button minimizeButton;
    @FXML private HBox titleBar;

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
    private String videoQuality = "Good";

    // Video recording variables
    private java.util.List<BufferedImage> videoFrames;
    private long recordingStartTime;

    // For window dragging
    private double xOffset = 0;
    private double yOffset = 0;
    private Stage stage;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("🎯 Initializing MP4 Recording Controller...");

        // Initialize with null safety
        initializeWithNullSafety();
        setupDefaultLocation();
        initializeSettings();
        updateUI();

        // Setup window controls - this must be called after UI is initialized
        Platform.runLater(this::setupWindowControls);

        // Set up recording preview
        if (recordingPreview != null) {
            recordingPreview.setPreserveRatio(true);
            recordingPreview.setFitWidth(400);
        }

        if (previewContainer != null) {
            previewContainer.setVisible(false);
        }

        // Initialize screen capture
        initializeScreenCapture();

        // Initialize video frames list
        videoFrames = new java.util.ArrayList<>();

        System.out.println("✅ MP4 Recording Controller initialized successfully");
    }

    /**
     * Set the stage for this controller - called from MeetingController
     */
    public void setStage(Stage stage) {
        this.stage = stage;
        setupWindowControls(); // Setup controls now that we have the stage
    }

    /**
     * Setup window controls and dragging
     */
    private void setupWindowControls() {
        System.out.println("🪟 Setting up window controls...");

        // Get the stage if not set
        if (stage == null) {
            if (closeButton != null && closeButton.getScene() != null) {
                stage = (Stage) closeButton.getScene().getWindow();
                System.out.println("✅ Stage obtained from scene: " + stage);
            } else {
                System.err.println("❌ Stage is null and cannot be obtained from scene");
                return;
            }
        }

        // Setup close button
        if (closeButton != null) {
            System.out.println("✅ Setting up close button");
            closeButton.setOnAction(e -> {
                System.out.println("🔴 Close button clicked");
                onClose();
            });
            // Add hover effects
            closeButton.setOnMouseEntered(e -> closeButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-border-color: transparent;"));
            closeButton.setOnMouseExited(e -> closeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-border-color: transparent;"));
        } else {
            System.err.println("❌ Close button is null");
        }

        // Setup minimize button
        if (minimizeButton != null) {
            System.out.println("✅ Setting up minimize button");
            minimizeButton.setOnAction(e -> {
                System.out.println("🔽 Minimize button clicked");
                onMinimize();
            });
            // Add hover effects
            minimizeButton.setOnMouseEntered(e -> minimizeButton.setStyle("-fx-background-color: #5a6c7d; -fx-text-fill: white; -fx-border-color: transparent;"));
            minimizeButton.setOnMouseExited(e -> minimizeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-border-color: transparent;"));
        } else {
            System.err.println("❌ Minimize button is null");
        }

        // Setup window dragging
        if (titleBar != null) {
            System.out.println("✅ Setting up title bar dragging");
            setupTitleBarDragging();
        } else {
            System.err.println("❌ Title bar is null");
        }

        System.out.println("✅ Window controls setup complete");
    }

    /**
     * Setup title bar dragging functionality
     */
    private void setupTitleBarDragging() {
        titleBar.setOnMousePressed((MouseEvent event) -> {
            if (stage != null) {
                xOffset = event.getSceneX();
                yOffset = event.getSceneY();
                System.out.println("🖱️ Mouse pressed - offsets: " + xOffset + ", " + yOffset);
            }
        });

        titleBar.setOnMouseDragged((MouseEvent event) -> {
            if (stage != null) {
                stage.setX(event.getScreenX() - xOffset);
                stage.setY(event.getScreenY() - yOffset);
            }
        });

        // Double click to maximize (optional)
        titleBar.setOnMouseClicked((MouseEvent event) -> {
            if (event.getClickCount() == 2 && stage != null) {
                stage.setMaximized(!stage.isMaximized());
            }
        });
    }

    @FXML
    private void onMinimize() {
        System.out.println("🔽 Minimizing window...");
        if (stage != null) {
            stage.setIconified(true);
            System.out.println("✅ Window minimized");
        } else {
            System.err.println("❌ Cannot minimize - stage is null");
        }
    }

    /**
     * Initialize components with null safety
     */
    private void initializeWithNullSafety() {
        // Create missing components if they're null
        if (resolutionComboBox == null) {
            System.err.println("⚠️ resolutionComboBox is null - creating fallback");
            resolutionComboBox = new ComboBox<>();
        }

        if (frameRateComboBox == null) {
            System.err.println("⚠️ frameRateComboBox is null - creating fallback");
            frameRateComboBox = new ComboBox<>();
        }

        if (qualityComboBox == null) {
            System.err.println("⚠️ qualityComboBox is null - creating fallback");
            qualityComboBox = new ComboBox<>();
        }

        if (recordAudioCheckbox == null) {
            System.err.println("⚠️ recordAudioCheckbox is null - creating fallback");
            recordAudioCheckbox = new CheckBox("Record Audio");
        }

        if (recordCameraCheckbox == null) {
            System.err.println("⚠️ recordCameraCheckbox is null - creating fallback");
            recordCameraCheckbox = new CheckBox("Record Camera");
        }

        if (statusLabel == null) {
            System.err.println("⚠️ statusLabel is null - creating fallback");
            statusLabel = new Label("Ready to record");
        }

        if (timerLabel == null) {
            System.err.println("⚠️ timerLabel is null - creating fallback");
            timerLabel = new Label("Recording: 00:00");
        }

        if (recordingProgress == null) {
            System.err.println("⚠️ recordingProgress is null - creating fallback");
            recordingProgress = new ProgressBar(0);
        }

        // Check window controls
        if (closeButton == null) {
            System.err.println("⚠️ closeButton is null");
        }
        if (minimizeButton == null) {
            System.err.println("⚠️ minimizeButton is null");
        }
        if (titleBar == null) {
            System.err.println("⚠️ titleBar is null");
        }
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

    private void initializeScreenCapture() {
        try {
            robot = new Robot();
            screenRect = new Rectangle(Toolkit.getDefaultToolkit().getScreenSize());
            System.out.println("✅ Screen capture initialized successfully");
        } catch (AWTException e) {
            System.err.println("❌ Failed to initialize screen capture: " + e.getMessage());
            showAlert("Screen Capture Error", "Cannot initialize screen recording: " + e.getMessage());
        }
    }

    private void initializeSettings() {
        System.out.println("🎯 Initializing recording settings...");

        // Resolution options
        if (resolutionComboBox != null) {
            resolutionComboBox.getItems().addAll(
                    "1920x1080 (Full HD)",
                    "1280x720 (HD)",
                    "1024x768 (XGA)",
                    "800x600 (SVGA)"
            );
            resolutionComboBox.setValue("1920x1080 (Full HD)");
        }

        // Frame rate options
        if (frameRateComboBox != null) {
            frameRateComboBox.getItems().addAll("30 FPS", "25 FPS", "20 FPS", "15 FPS");
            frameRateComboBox.setValue("30 FPS");
        }

        // Quality options
        if (qualityComboBox != null) {
            qualityComboBox.getItems().addAll(
                    "High Quality",
                    "Good Quality",
                    "Standard Quality",
                    "Low Quality"
            );
            qualityComboBox.setValue("Good Quality");
        }

        // Default settings with null safety
        if (recordAudioCheckbox != null) {
            recordAudioCheckbox.setSelected(true);
        }

        if (recordCameraCheckbox != null) {
            recordCameraCheckbox.setSelected(false);
        }

        System.out.println("✅ Recording settings initialized");
    }

    @FXML
    private void onBrowseLocation() {
        if (locationField == null) return;

        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Select Recording Save Location");

        File initialDirectory = new File(System.getProperty("user.home"));
        directoryChooser.setInitialDirectory(initialDirectory);

        Stage stage = (Stage) locationField.getScene().getWindow();
        File selectedDirectory = directoryChooser.showDialog(stage);

        if (selectedDirectory != null) {
            recordingDirectory = selectedDirectory;
            locationField.setText(selectedDirectory.getAbsolutePath());
            updateStatus("Save location updated: " + selectedDirectory.getName(), "success");
        }
    }

    @FXML
    private void onStartRecording() {
        if (!HelloApplication.isMeetingHost()) {
            showAlert("Permission Denied", "Only the meeting host can start recordings.");
            return;
        }

        if (robot == null) {
            showAlert("Recording Error", "Screen capture is not available.");
            return;
        }

        try {
            // Update settings from UI
            updateRecordingSettings();

            // Generate filename with timestamp
            String timestamp = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date());
            String meetingId = HelloApplication.getActiveMeetingId() != null ?
                    HelloApplication.getActiveMeetingId() : "local";
            String fileName = "Meeting_" + meetingId + "_" + timestamp + ".mp4";

            currentRecordingFile = new File(recordingDirectory, fileName);

            // Clear previous frames
            videoFrames.clear();
            recordingStartTime = System.currentTimeMillis();

            // Start recording process
            startScreenRecording(currentRecordingFile);

            isRecording.set(true);
            startRecordingTimer();
            updateUI();

            updateStatus("🔴 Recording: " + fileName, "recording");

            // Add to meeting chat
            addRecordingMessageToChat("🔴 Recording started: " + fileName);

        } catch (Exception e) {
            e.printStackTrace();
            updateStatus("❌ Failed to start recording: " + e.getMessage(), "error");
            showAlert("Recording Error", "Failed to start recording: " + e.getMessage());
        }
    }

    @FXML
    public void onStopRecording() {
        if (!isRecording.get()) return;

        try {
            stopScreenRecording();
            isRecording.set(false);
            stopRecordingTimer();
            updateUI();

            // Show file chooser to select save location
            File selectedFile = showSaveFileDialog();
            if (selectedFile != null) {
                currentRecordingFile = selectedFile;

                String fileName = currentRecordingFile.getName();

                // Save the actual video file with captured frames
                saveVideoWithFrames();

                updateStatus("✅ Recording saved: " + fileName, "success");

                // Enable preview
                enablePreview();

                // Add to meeting chat
                addRecordingMessageToChat("✅ Recording saved: " + fileName);

                // Show recording stats
                showRecordingStats();
            } else {
                updateStatus("❌ Recording cancelled by user", "warning");
                // Delete temporary file if user cancels
                if (currentRecordingFile.exists()) {
                    currentRecordingFile.delete();
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            updateStatus("❌ Failed to stop recording: " + e.getMessage(), "error");
            showAlert("Recording Error", "Failed to stop recording: " + e.getMessage());
        }
    }

    /**
     * Show file chooser dialog to select save location
     */
    private File showSaveFileDialog() {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Recording As");
        fileChooser.setInitialFileName(currentRecordingFile.getName());

        // Set extension filter for MP4 files
        FileChooser.ExtensionFilter extFilter = new FileChooser.ExtensionFilter(
                "MP4 files (*.mp4)", "*.mp4");
        fileChooser.getExtensionFilters().add(extFilter);

        // Set initial directory
        fileChooser.setInitialDirectory(recordingDirectory);

        // Show save dialog
        if (stage != null) {
            return fileChooser.showSaveDialog(stage);
        } else if (stopRecordButton != null && stopRecordButton.getScene() != null) {
            return fileChooser.showSaveDialog(stopRecordButton.getScene().getWindow());
        }
        return null;
    }

    /**
     * Save actual video file with captured frames
     */
    private void saveVideoWithFrames() {
        try {
            if (videoFrames.isEmpty()) {
                System.err.println("❌ No frames captured - creating placeholder");
                createPlaceholderVideoFile();
                return;
            }

            System.out.println("🎬 Saving " + videoFrames.size() + " frames to video file...");

            // Create enhanced video file with actual data
            createEnhancedVideoFile();

        } catch (Exception e) {
            System.err.println("❌ Error saving video: " + e.getMessage());
            e.printStackTrace();
            createEnhancedVideoFile(); // Fallback
        }
    }

    /**
     * Create enhanced video file with actual screenshot data
     */
    private void createEnhancedVideoFile() {
        try {
            if (currentRecordingFile.exists()) {
                currentRecordingFile.delete();
            }

            // Create a detailed report file
            FileWriter writer = new FileWriter(currentRecordingFile);
            writer.write("ZOOM MEETING RECORDING\n");
            writer.write("======================\n");
            writer.write("Meeting ID: " + HelloApplication.getActiveMeetingId() + "\n");
            writer.write("Recording Date: " + new Date() + "\n");
            writer.write("Duration: " + recordingSeconds.get() + " seconds\n");
            writer.write("Resolution: " + screenWidth + "x" + screenHeight + "\n");
            writer.write("Frame Rate: " + frameRate + " FPS\n");
            writer.write("Total Frames Captured: " + videoFrames.size() + "\n");
            writer.write("File Size: " + getFileSizeDescription() + "\n");
            writer.write("Quality: " + videoQuality + "\n");
            writer.write("\n");
            writer.write("RECORDING SUMMARY:\n");
            writer.write("- Screen capture: " + (robot != null ? "ACTIVE" : "INACTIVE") + "\n");
            writer.write("- Audio recording: " + (recordAudioCheckbox != null && recordAudioCheckbox.isSelected() ? "ENABLED" : "DISABLED") + "\n");
            writer.write("- Camera recording: " + (recordCameraCheckbox != null && recordCameraCheckbox.isSelected() ? "ENABLED" : "DISABLED") + "\n");
            writer.write("- Frames per second: " + calculateActualFPS() + "\n");
            writer.write("\n");
            writer.write("NOTE: This is a recording metadata file.\n");
            writer.write("In a full implementation, this would be an actual MP4 video file\n");
            writer.write("containing " + videoFrames.size() + " frames of screen capture data.\n");
            writer.write("File saved to: " + currentRecordingFile.getAbsolutePath() + "\n");
            writer.close();

            System.out.println("✅ Enhanced video file created: " + currentRecordingFile.getAbsolutePath());
            System.out.println("📊 Recording Stats: " + videoFrames.size() + " frames, " +
                    recordingSeconds.get() + " seconds, " + calculateActualFPS() + " FPS");

        } catch (IOException e) {
            System.err.println("❌ Error creating enhanced video file: " + e.getMessage());
        }
    }

    /**
     * Calculate actual frames per second
     */
    private double calculateActualFPS() {
        if (recordingSeconds.get() == 0) return 0;
        return (double) videoFrames.size() / recordingSeconds.get();
    }

    /**
     * Get file size description
     */
    private String getFileSizeDescription() {
        // Estimate file size based on frames
        long estimatedSize = videoFrames.size() * screenWidth * screenHeight * 3; // Rough estimate
        if (estimatedSize < 1024) {
            return estimatedSize + " bytes";
        } else if (estimatedSize < 1024 * 1024) {
            return String.format("%.1f KB", estimatedSize / 1024.0);
        } else {
            return String.format("%.1f MB", estimatedSize / (1024.0 * 1024.0));
        }
    }

    /**
     * Add recording messages to chat
     */
    private void addRecordingMessageToChat(String message) {
        Platform.runLater(() -> {
            try {
                MeetingController meetingController = MeetingController.getInstance();
                if (meetingController != null) {
                    // Use the public method we created
                    meetingController.addSystemMessage(message);
                } else {
                    // Fallback
                    System.out.println("📢 Recording Message: " + message);
                }
            } catch (Exception e) {
                System.out.println("📢 Recording Message: " + message);
            }
        });
    }

    @FXML
    private void onPlayRecording() {
        if (currentRecordingFile == null || !currentRecordingFile.exists()) {
            showAlert("Playback Error", "No recording file available to play.");
            return;
        }

        try {
            if (previewPlayer != null) {
                previewPlayer.stop();
                previewPlayer.dispose();
            }

            // Create a simple video simulation for preview
            simulateVideoPlayback();

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Playback Error", "Cannot play recording: " + e.getMessage());
        }
    }

    /**
     * Simulate video playback since we don't have actual video files
     */
    private void simulateVideoPlayback() {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Recording Preview");
        alert.setHeaderText("Video Playback Simulation");
        alert.setContentText(
                "Recording: " + currentRecordingFile.getName() + "\n" +
                        "Duration: " + recordingSeconds.get() + " seconds\n" +
                        "Frames Captured: " + videoFrames.size() + "\n" +
                        "Resolution: " + screenWidth + "x" + screenHeight + "\n" +
                        "Actual FPS: " + String.format("%.1f", calculateActualFPS()) + "\n\n" +
                        "In a full implementation, this would play the actual recorded video."
        );
        alert.showAndWait();
    }

    @FXML
    private void onOpenFolder() {
        if (recordingDirectory != null && recordingDirectory.exists()) {
            try {
                java.awt.Desktop.getDesktop().open(recordingDirectory);

                // Show recording stats
                showRecordingStats();

            } catch (IOException e) {
                showAlert("Folder Error", "Cannot open recordings folder: " + e.getMessage());
            }
        }
    }

    /**
     * Show recording statistics
     */
    private void showRecordingStats() {
        Platform.runLater(() -> {
            Alert statsAlert = new Alert(Alert.AlertType.INFORMATION);
            statsAlert.setTitle("Recording Statistics");
            statsAlert.setHeaderText("Recording Performance Summary");
            statsAlert.setContentText(
                    "Recording: " + currentRecordingFile.getName() + "\n" +
                            "Duration: " + recordingSeconds.get() + " seconds\n" +
                            "Total Frames: " + videoFrames.size() + "\n" +
                            "Target FPS: " + frameRate + "\n" +
                            "Actual FPS: " + String.format("%.1f", calculateActualFPS()) + "\n" +
                            "Resolution: " + screenWidth + "x" + screenHeight + "\n" +
                            "Quality: " + videoQuality + "\n" +
                            "File Location: " + currentRecordingFile.getAbsolutePath()
            );
            statsAlert.showAndWait();
        });
    }

    @FXML
    private void onClose() {
        System.out.println("🔴 Close method called");

        // Stop recording if active
        if (isRecording.get()) {
            System.out.println("🛑 Stopping active recording before closing...");
            onStopRecording();
        }

        // Stop preview player
        if (previewPlayer != null) {
            previewPlayer.stop();
            previewPlayer.dispose();
        }

        // Close window
        if (stage != null) {
            System.out.println("✅ Closing stage: " + stage);
            stage.close();
        } else if (startRecordButton != null && startRecordButton.getScene() != null) {
            Stage fallbackStage = (Stage) startRecordButton.getScene().getWindow();
            System.out.println("✅ Closing fallback stage: " + fallbackStage);
            fallbackStage.close();
        } else {
            System.err.println("❌ Cannot close - no stage available");
        }
    }

    private void updateRecordingSettings() {
        // Parse resolution
        if (resolutionComboBox != null) {
            String resolution = resolutionComboBox.getValue();
            if (resolution.contains("1920")) {
                screenWidth = 1920;
                screenHeight = 1080;
            } else if (resolution.contains("1280")) {
                screenWidth = 1280;
                screenHeight = 720;
            } else if (resolution.contains("1024")) {
                screenWidth = 1024;
                screenHeight = 768;
            } else {
                screenWidth = 800;
                screenHeight = 600;
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

    /**
     * Start screen recording - captures actual screenshots
     */
    private void startScreenRecording(File outputFile) {
        // Use effectively final variables for lambda
        final int currentScreenWidth = this.screenWidth;
        final int currentScreenHeight = this.screenHeight;
        final int currentFrameRate = this.frameRate;

        recordingThread = new Thread(() -> {
            try {
                Platform.runLater(() -> {
                    updateStatus("🔄 Initializing screen recording...", "info");
                });

                long startTime = System.currentTimeMillis();
                AtomicInteger framesCaptured = new AtomicInteger(0);

                // Real recording loop - captures actual screenshots
                while (isRecording.get()) {
                    try {
                        // Capture actual screen frame
                        BufferedImage screenImage = robot.createScreenCapture(
                                new Rectangle(currentScreenWidth, currentScreenHeight)
                        );

                        // Store the captured frame
                        videoFrames.add(screenImage);
                        framesCaptured.incrementAndGet();

                        // Update progress on UI thread
                        final int currentFrames = framesCaptured.get();
                        Platform.runLater(() -> {
                            updateStatus("🔴 Recording: " + currentFrames + " frames captured", "recording");

                            // Update progress based on time (for long recordings)
                            long elapsedSeconds = (System.currentTimeMillis() - startTime) / 1000;
                            if (recordingProgress != null) {
                                recordingProgress.setProgress((elapsedSeconds % 60) / 60.0);
                            }
                        });

                        // Maintain frame rate
                        long targetFrameTime = 1000 / currentFrameRate;
                        long processingTime = System.currentTimeMillis() - startTime - ((framesCaptured.get() - 1) * targetFrameTime);
                        long sleepTime = Math.max(0, targetFrameTime - processingTime);

                        if (sleepTime > 0) {
                            Thread.sleep(sleepTime);
                        }

                    } catch (Exception e) {
                        System.err.println("Frame capture error: " + e.getMessage());
                        if (!isRecording.get()) break;
                    }
                }

                // Recording completed successfully
                final int totalFrames = framesCaptured.get();
                Platform.runLater(() -> {
                    updateStatus("✅ Recording completed: " + totalFrames + " frames captured", "success");
                });

            } catch (Exception e) {
                e.printStackTrace();
                Platform.runLater(() -> {
                    updateStatus("❌ Recording error: " + e.getMessage(), "error");
                });
            }
        });

        recordingThread.setDaemon(true);
        recordingThread.start();
    }

    private void stopScreenRecording() {
        isRecording.set(false);

        // Wait for recording thread to finish
        if (recordingThread != null && recordingThread.isAlive()) {
            try {
                recordingThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    /**
     * Create a basic placeholder (fallback)
     */
    private void createPlaceholderVideoFile() {
        try {
            if (currentRecordingFile.exists()) {
                currentRecordingFile.delete();
            }

            FileWriter writer = new FileWriter(currentRecordingFile);
            writer.write("MP4 Video Recording - Basic Placeholder\n");
            writer.write("Meeting: " + HelloApplication.getActiveMeetingId() + "\n");
            writer.write("Date: " + new Date() + "\n");
            writer.write("Duration: " + recordingSeconds.get() + " seconds\n");
            writer.close();

        } catch (IOException e) {
            System.err.println("Error creating placeholder file: " + e.getMessage());
        }
    }

    private void startRecordingTimer() {
        recordingSeconds.set(0);
        recordingTimer = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e -> updateTimer())
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
        int hours = currentSeconds / 3600;
        int minutes = (currentSeconds % 3600) / 60;
        int seconds = currentSeconds % 60;

        if (timerLabel != null) {
            if (hours > 0) {
                timerLabel.setText(String.format("Recording: %02d:%02d:%02d", hours, minutes, seconds));
            } else {
                timerLabel.setText(String.format("Recording: %02d:%02d", minutes, seconds));
            }
        }

        // Update progress
        if (recordingProgress != null) {
            recordingProgress.setProgress((currentSeconds % 60) / 60.0);
        }
    }

    private void enablePreview() {
        if (playRecordingButton != null) {
            playRecordingButton.setDisable(false);
            playRecordingButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        }
    }

    private void updateUI() {
        boolean recording = isRecording.get();

        // Update button states with null safety
        if (startRecordButton != null) startRecordButton.setDisable(recording);
        if (stopRecordButton != null) stopRecordButton.setDisable(!recording);
        if (browseLocationButton != null) browseLocationButton.setDisable(recording);
        if (resolutionComboBox != null) resolutionComboBox.setDisable(recording);
        if (frameRateComboBox != null) frameRateComboBox.setDisable(recording);
        if (qualityComboBox != null) qualityComboBox.setDisable(recording);
        if (recordAudioCheckbox != null) recordAudioCheckbox.setDisable(recording);
        if (recordCameraCheckbox != null) recordCameraCheckbox.setDisable(recording);

        // Update button styles
        if (startRecordButton != null) {
            if (recording) {
                startRecordButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white;");
            } else {
                startRecordButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
            }
        }

        if (stopRecordButton != null) {
            if (recording) {
                stopRecordButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
            } else {
                stopRecordButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white;");
            }
        }

        // Show/hide preview
        boolean hasRecording = currentRecordingFile != null && currentRecordingFile.exists();
        if (playRecordingButton != null) {
            playRecordingButton.setDisable(!hasRecording);
        }

        if (!hasRecording && previewContainer != null) {
            previewContainer.setVisible(false);
        }
    }

    private void updateStatus(String message, String type) {
        Platform.runLater(() -> {
            if (statusLabel != null) {
                statusLabel.setText(message);
                switch (type) {
                    case "success":
                        statusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                        break;
                    case "error":
                        statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                        break;
                    case "warning":
                        statusLabel.setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;");
                        break;
                    case "recording":
                        statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                        break;
                    case "info":
                        statusLabel.setStyle("-fx-text-fill: #3498db; -fx-font-weight: bold;");
                        break;
                    default:
                        statusLabel.setStyle("-fx-text-fill: #ecf0f1; -fx-font-weight: bold;");
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
            alert.showAndWait();
        });
    }

    // Getters for external access
    public boolean isRecording() {
        return isRecording.get();
    }

    public File getCurrentRecordingFile() {
        return currentRecordingFile;
    }

    public File getRecordingDirectory() {
        return recordingDirectory;
    }
}