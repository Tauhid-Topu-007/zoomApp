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

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        System.out.println("🎯 Initializing MP4 Recording Controller...");

        // Initialize with null safety
        initializeWithNullSafety();
        setupDefaultLocation();
        initializeSettings();
        updateUI();

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
        System.out.println("✅ MP4 Recording Controller initialized successfully");
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

            String fileName = currentRecordingFile.getName();
            updateStatus("✅ Recording saved: " + fileName, "success");

            // Create a placeholder video file
            createPlaceholderVideoFile();

            // Enable preview
            enablePreview();

            // Add to meeting chat
            addRecordingMessageToChat("✅ Recording saved: " + fileName);

        } catch (Exception e) {
            e.printStackTrace();
            updateStatus("❌ Failed to stop recording: " + e.getMessage(), "error");
            showAlert("Recording Error", "Failed to stop recording: " + e.getMessage());
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

            // For demo purposes, we'll use a sample video or the actual file if it exists
            File videoFile = getDemoVideoFile();

            Media media = new Media(videoFile.toURI().toString());
            previewPlayer = new MediaPlayer(media);

            if (recordingPreview != null) {
                recordingPreview.setMediaPlayer(previewPlayer);
            }

            previewPlayer.setOnReady(() -> {
                if (previewContainer != null) {
                    previewContainer.setVisible(true);
                }
                previewPlayer.play();
            });

            previewPlayer.setOnError(() -> {
                updateStatus("❌ Cannot play recording: " + previewPlayer.getError().getMessage(), "error");
            });

            previewPlayer.setOnEndOfMedia(() -> {
                previewPlayer.seek(Duration.ZERO);
            });

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Playback Error", "Cannot play recording: " + e.getMessage());
        }
    }

    @FXML
    private void onOpenFolder() {
        if (recordingDirectory != null && recordingDirectory.exists()) {
            try {
                java.awt.Desktop.getDesktop().open(recordingDirectory);
            } catch (IOException e) {
                showAlert("Folder Error", "Cannot open recordings folder: " + e.getMessage());
            }
        }
    }

    @FXML
    private void onClose() {
        // Stop recording if active
        if (isRecording.get()) {
            onStopRecording();
        }

        // Stop preview player
        if (previewPlayer != null) {
            previewPlayer.stop();
            previewPlayer.dispose();
        }

        // Close window
        if (startRecordButton != null && startRecordButton.getScene() != null) {
            Stage stage = (Stage) startRecordButton.getScene().getWindow();
            stage.close();
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
     * Start screen recording
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

                // Create a placeholder file to simulate recording
                createPlaceholderVideoFile();

                Platform.runLater(() -> {
                    updateStatus("🔴 Recording in progress...", "recording");
                });

                long startTime = System.currentTimeMillis();
                AtomicInteger framesCaptured = new AtomicInteger(0);

                // Simulated recording loop
                while (isRecording.get()) {
                    try {
                        // Capture screen frame (simulated)
                        BufferedImage screenImage = robot.createScreenCapture(
                                new Rectangle(currentScreenWidth, currentScreenHeight)
                        );

                        framesCaptured.incrementAndGet();

                        // Update progress on UI thread
                        final int currentFrames = framesCaptured.get();
                        Platform.runLater(() -> {
                            updateStatus("🔴 Recording: " + currentFrames + " frames captured", "recording");
                        });

                        // Maintain frame rate
                        long sleepTime = Math.max(0, (1000 / currentFrameRate) - 10);
                        Thread.sleep(sleepTime);

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
     * Create a placeholder video file for demonstration
     */
    private void createPlaceholderVideoFile() {
        try {
            if (currentRecordingFile.exists()) {
                currentRecordingFile.delete();
            }

            FileWriter writer = new FileWriter(currentRecordingFile);
            writer.write("MP4 Video Recording - Demo File\n");
            writer.write("Meeting: " + HelloApplication.getActiveMeetingId() + "\n");
            writer.write("Date: " + new Date() + "\n");
            writer.write("Duration: " + recordingSeconds.get() + " seconds\n");
            writer.write("Resolution: " + screenWidth + "x" + screenHeight + "\n");
            writer.write("Frame Rate: " + frameRate + " FPS\n");
            writer.write("Quality: " + videoQuality + "\n");
            writer.write("This is a placeholder file. In a real implementation, this would be an actual MP4 video file.\n");
            writer.close();

            // Rename to .mp4 extension
            File mp4File = new File(currentRecordingFile.getParent(),
                    currentRecordingFile.getName().replace(".txt", ".mp4"));
            currentRecordingFile.renameTo(mp4File);
            currentRecordingFile = mp4File;

        } catch (IOException e) {
            System.err.println("Error creating placeholder file: " + e.getMessage());
        }
    }

    /**
     * Get a demo video file for playback demonstration
     */
    private File getDemoVideoFile() {
        // Try to use the recorded file if it exists
        if (currentRecordingFile.exists() && currentRecordingFile.length() > 0) {
            return currentRecordingFile;
        }

        // For demo purposes, you can include a sample video in your resources
        File sampleVideo = new File("sample.mp4");
        if (sampleVideo.exists()) {
            return sampleVideo;
        }

        return currentRecordingFile;
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
            recordingProgress.setProgress((currentSeconds % 600) / 600.0);
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