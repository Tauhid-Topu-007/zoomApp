package org.example.zoom;

import javafx.fxml.FXML;
import javafx.event.ActionEvent;
import javafx.scene.canvas.Canvas;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.animation.PauseTransition;
import javafx.util.Duration;
import javafx.stage.StageStyle;
import javafx.scene.input.MouseEvent;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.stage.Screen;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.media.AudioClip;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.github.sarxos.webcam.WebcamResolution;
import javax.sound.sampled.*;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.ArrayList;

public class MeetingController {

    @FXML private Button audioButton;
    @FXML private Button videoButton;
    @FXML private Button shareButton;
    @FXML private Button recordButton;
    @FXML private Button leaveButton;
    @FXML private Button participantsButton;
    @FXML private Button chatButton;
    @FXML private ListView<String> participantsList;
    @FXML private StackPane videoArea;
    @FXML private VBox chatBox;
    @FXML private ScrollPane chatScroll;
    @FXML private TextField chatInput;
    @FXML private Label meetingIdLabel;
    @FXML private Label hostLabel;
    @FXML private Label timerLabel;
    @FXML private Label participantsCountLabel;

    // Window control buttons
    @FXML private Button minimizeButton;
    @FXML private Button maximizeButton;
    @FXML private Button closeButton;
    @FXML private HBox titleBar;

    // Panels
    @FXML private VBox participantsPanel;
    @FXML private VBox chatPanel;

    // Audio and Video controls containers
    @FXML private VBox audioControlsContainer;
    @FXML private VBox videoControlsContainer;

    // Control toggle buttons
    @FXML private Button audioControlsButton;
    @FXML private Button videoControlsButton;

    // Video display
    @FXML private ImageView videoDisplay;

    private boolean audioMuted = false;
    private boolean videoOn = false;
    private boolean recording = false;
    private boolean participantsVisible = true;
    private boolean chatVisible = true;
    private boolean audioControlsVisible = true;
    private boolean videoControlsVisible = true;
    private File currentRecordingFile;
    private Stage stage;
    private MediaPlayer currentMediaPlayer;

    // Audio and Video controllers
    private AudioControlsController audioControlsController;
    private VideoControlsController videoControlsController;

    // Real camera and microphone
    private Webcam webcam;
    private TargetDataLine microphone;
    private AudioFormat audioFormat;
    private boolean cameraAvailable = false;
    private boolean microphoneAvailable = false;

    // Camera capture thread
    private Thread cameraThread;
    private volatile boolean cameraRunning = false;

    // Audio capture thread
    private Thread audioThread;
    private volatile boolean audioRunning = false;

    // For window dragging
    private double xOffset = 0;
    private double yOffset = 0;
    private boolean isMaximized = false;

    // Store original window size and position for restore
    private double originalX, originalY, originalWidth, originalHeight;

    // Meeting timer
    private javafx.animation.Timeline meetingTimer;
    private int meetingSeconds = 0;

    @FXML
    public void initialize() {
        try {
            System.out.println("🎯 Initializing Meeting Controller...");

            // Initialize hardware
            initializeHardware();

            // Load audio controls with null safety
            if (audioControlsContainer != null) {
                try {
                    FXMLLoader audioLoader = new FXMLLoader(getClass().getResource("audio-controls.fxml"));
                    VBox audioControls = audioLoader.load();
                    audioControlsController = audioLoader.getController();
                    audioControlsContainer.getChildren().add(audioControls);
                    System.out.println("✅ Audio controls loaded successfully");
                } catch (IOException e) {
                    System.err.println("❌ Failed to load audio controls: " + e.getMessage());
                    setupFallbackAudioControls();
                }
            } else {
                System.err.println("⚠️ Audio controls container not found in FXML");
                setupFallbackAudioControls();
            }

            // Load video controls with null safety
            if (videoControlsContainer != null) {
                try {
                    FXMLLoader videoLoader = new FXMLLoader(getClass().getResource("video-controls.fxml"));
                    VBox videoControls = videoLoader.load();
                    videoControlsController = videoLoader.getController();
                    videoControlsContainer.getChildren().add(videoControls);
                    System.out.println("✅ Video controls loaded successfully");
                } catch (IOException e) {
                    System.err.println("❌ Failed to load video controls: " + e.getMessage());
                    setupFallbackVideoControls();
                }
            } else {
                System.err.println("⚠️ Video controls container not found in FXML");
                setupFallbackVideoControls();
            }

        } catch (Exception e) {
            System.err.println("❌ Critical error in meeting controller initialization: " + e.getMessage());
            e.printStackTrace();
            setupAllFallbackControls();
        }

        updateParticipantsList();
        setupChat();
        updateMeetingInfo();
        updateButtonStyles();
        startMeetingTimer();

        // Get the stage from the scene
        Platform.runLater(() -> {
            Stage currentStage = (Stage) (chatBox != null ? chatBox.getScene().getWindow() : null);
            if (currentStage != null) {
                setStage(currentStage);
            }

            // Notify controllers about meeting state
            if (audioControlsController != null) {
                audioControlsController.onMeetingStateChanged(true);
            }
            if (videoControlsController != null) {
                videoControlsController.onMeetingStateChanged(true);
            }
        });
    }

    /**
     * Initialize camera and microphone hardware
     */
    private void initializeHardware() {
        System.out.println("🔧 Initializing hardware...");

        // Initialize camera
        try {
            System.out.println("📷 Looking for cameras...");
            List<Webcam> webcams = Webcam.getWebcams();
            System.out.println("📷 Found " + webcams.size() + " cameras");

            if (!webcams.isEmpty()) {
                webcam = webcams.get(0);
                System.out.println("📷 Selected camera: " + webcam.getName());

                // Try different resolutions
                try {
                    webcam.setViewSize(WebcamResolution.VGA.getSize());
                    System.out.println("📷 Set resolution to VGA");
                } catch (Exception e) {
                    try {
                        webcam.setViewSize(WebcamResolution.QVGA.getSize());
                        System.out.println("📷 Set resolution to QVGA");
                    } catch (Exception e2) {
                        System.out.println("📷 Using default resolution");
                    }
                }

                cameraAvailable = true;
                System.out.println("✅ Camera initialized successfully: " + webcam.getName());
            } else {
                System.out.println("❌ No cameras found");
                cameraAvailable = false;
            }
        } catch (Exception e) {
            System.err.println("❌ Error initializing camera: " + e.getMessage());
            cameraAvailable = false;
        }

        // Initialize microphone
        try {
            System.out.println("🎤 Initializing microphone...");
            audioFormat = new AudioFormat(16000, 16, 1, true, true);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);

            if (!AudioSystem.isLineSupported(info)) {
                System.out.println("❌ Microphone line not supported");
                microphoneAvailable = false;
                return;
            }

            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphoneAvailable = true;
            System.out.println("✅ Microphone initialized successfully");
        } catch (Exception e) {
            System.err.println("❌ Error initializing microphone: " + e.getMessage());
            microphoneAvailable = false;
        }

        System.out.println("🔧 Hardware initialization complete - Camera: " + cameraAvailable + ", Microphone: " + microphoneAvailable);
    }

    /**
     * Start camera capture
     */
    private void startCamera() {
        if (!cameraAvailable || webcam == null) {
            System.err.println("❌ Cannot start camera: No camera available");
            showCameraError();
            return;
        }

        try {
            System.out.println("📷 Starting camera...");

            if (!webcam.isOpen()) {
                webcam.open();
                System.out.println("📷 Camera opened successfully");
            }

            cameraRunning = true;
            cameraThread = new Thread(() -> {
                System.out.println("📷 Camera thread started");
                while (cameraRunning && webcam.isOpen()) {
                    try {
                        java.awt.image.BufferedImage awtImage = webcam.getImage();
                        if (awtImage != null) {
                            Image fxImage = convertToFxImage(awtImage);
                            if (fxImage != null) {
                                Platform.runLater(() -> {
                                    // Update main video display
                                    if (videoDisplay != null) {
                                        videoDisplay.setImage(fxImage);
                                    }
                                    // Update video controls preview
                                    if (videoControlsController != null) {
                                        videoControlsController.updateVideoPreview(fxImage);
                                    }
                                });
                            }
                        }
                        Thread.sleep(33); // ~30 FPS
                    } catch (Exception e) {
                        System.err.println("❌ Camera capture error: " + e.getMessage());
                        break;
                    }
                }
                System.out.println("📷 Camera thread stopped");
            });
            cameraThread.setDaemon(true);
            cameraThread.start();

            System.out.println("✅ Camera started successfully");

        } catch (Exception e) {
            System.err.println("❌ Failed to start camera: " + e.getMessage());
            e.printStackTrace();
            showCameraError();
        }
    }

    /**
     * Stop camera capture
     */
    private void stopCamera() {
        System.out.println("📷 Stopping camera...");
        cameraRunning = false;

        if (cameraThread != null && cameraThread.isAlive()) {
            try {
                cameraThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (webcam != null && webcam.isOpen()) {
            webcam.close();
            System.out.println("📷 Camera closed");
        }

        Platform.runLater(() -> {
            if (videoDisplay != null) {
                videoDisplay.setImage(null);
            }
            // Reset video controls to simulated camera
            if (videoControlsController != null) {
                videoControlsController.resetToSimulatedCamera();
            }
        });

        System.out.println("✅ Camera stopped successfully");
    }

    /**
     * Start microphone capture
     */
    private void startMicrophone() {
        if (!microphoneAvailable || microphone == null) {
            System.err.println("❌ Cannot start microphone: No microphone available");
            return;
        }

        try {
            System.out.println("🎤 Starting microphone...");
            microphone.open(audioFormat);
            microphone.start();

            audioRunning = true;
            audioThread = new Thread(() -> {
                System.out.println("🎤 Microphone thread started");
                byte[] buffer = new byte[4096];
                while (audioRunning && microphone.isOpen()) {
                    try {
                        int bytesRead = microphone.read(buffer, 0, buffer.length);
                        if (bytesRead > 0 && !audioMuted) {
                            // Here you would send audio data to other participants
                            // For now, we just capture but don't process when muted
                            processAudioData(buffer, bytesRead);
                        }
                    } catch (Exception e) {
                        System.err.println("❌ Microphone capture error: " + e.getMessage());
                        break;
                    }
                }
                System.out.println("🎤 Microphone thread stopped");
            });
            audioThread.setDaemon(true);
            audioThread.start();

            System.out.println("✅ Microphone started successfully");

        } catch (Exception e) {
            System.err.println("❌ Failed to start microphone: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Stop microphone capture
     */
    private void stopMicrophone() {
        System.out.println("🎤 Stopping microphone...");
        audioRunning = false;

        if (audioThread != null && audioThread.isAlive()) {
            try {
                audioThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (microphone != null && microphone.isOpen()) {
            microphone.stop();
            microphone.close();
            System.out.println("🎤 Microphone closed");
        }

        System.out.println("✅ Microphone stopped successfully");
    }

    /**
     * Process captured audio data (placeholder for real audio streaming)
     */
    private void processAudioData(byte[] audioData, int length) {
        // In a real application, you would:
        // 1. Encode the audio data
        // 2. Send it to other participants via WebSocket/RTP
        // 3. Other participants would decode and play it

        // For now, we just demonstrate that audio is being captured
        if (System.currentTimeMillis() % 5000 < 100) { // Log every 5 seconds
            System.out.println("🎤 Audio data captured: " + length + " bytes");
        }
    }

    /**
     * Convert AWT BufferedImage to JavaFX Image
     */
    private Image convertToFxImage(java.awt.image.BufferedImage awtImage) {
        try {
            java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(awtImage, "jpg", out);
            java.io.ByteArrayInputStream in = new java.io.ByteArrayInputStream(out.toByteArray());
            return new Image(in);
        } catch (Exception e) {
            System.err.println("❌ Failed to convert image: " + e.getMessage());
            return null;
        }
    }

    private void showCameraError() {
        Platform.runLater(() -> {
            if (videoDisplay != null) {
                // Create error placeholder
                Canvas canvas = new Canvas(320, 240);
                javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();
                gc.setFill(javafx.scene.paint.Color.LIGHTGRAY);
                gc.fillRect(0, 0, 320, 240);
                gc.setFill(javafx.scene.paint.Color.RED);
                gc.fillText("Camera Error", 120, 120);
                gc.fillText("No camera available", 110, 140);

                javafx.scene.image.WritableImage writableImage = new javafx.scene.image.WritableImage(320, 240);
                canvas.snapshot(null, writableImage);
                videoDisplay.setImage(writableImage);
            }
        });
    }

    // Add these methods for fallback controls
    private void setupFallbackAudioControls() {
        System.out.println("🔄 Setting up fallback audio controls");
        if (audioControlsContainer != null) {
            // Create simple audio controls
            HBox fallbackAudioControls = new HBox(10);
            fallbackAudioControls.setAlignment(Pos.CENTER_LEFT);
            fallbackAudioControls.setStyle("-fx-padding: 10; -fx-background-color: #2c3e50; -fx-border-radius: 5;");

            Button muteButton = new Button("🎤 Mute");
            muteButton.setOnAction(e -> toggleAudio());
            muteButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");

            Button deafenButton = new Button("🔇 Deafen");
            deafenButton.setOnAction(e -> toggleDeafen());
            deafenButton.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white;");

            fallbackAudioControls.getChildren().addAll(muteButton, deafenButton);
            audioControlsContainer.getChildren().add(fallbackAudioControls);
        }
    }

    private void setupFallbackVideoControls() {
        System.out.println("🔄 Setting up fallback video controls");
        if (videoControlsContainer != null) {
            // Create simple video controls
            HBox fallbackVideoControls = new HBox(10);
            fallbackVideoControls.setAlignment(Pos.CENTER_LEFT);
            fallbackVideoControls.setStyle("-fx-padding: 10; -fx-background-color: #2c3e50; -fx-border-radius: 5;");

            Button videoToggle = new Button("📹 Start Video");
            videoToggle.setOnAction(e -> toggleVideo());
            videoToggle.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white;");

            Button recordButton = new Button("⏺ Record");
            recordButton.setOnAction(e -> onToggleRecording());
            recordButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");

            fallbackVideoControls.getChildren().addAll(videoToggle, recordButton);
            videoControlsContainer.getChildren().add(fallbackVideoControls);
        }
    }

    private void setupAllFallbackControls() {
        System.out.println("🔄 Setting up all fallback controls");
        // Ensure basic controls are visible and functional
        if (audioButton != null) {
            audioButton.setVisible(true);
            audioButton.setDisable(false);
        }
        if (videoButton != null) {
            videoButton.setVisible(true);
            videoButton.setDisable(false);
        }
        if (recordButton != null) {
            recordButton.setVisible(true);
            recordButton.setDisable(!HelloApplication.isMeetingHost());
        }
    }

    // Add these methods for toggling controls visibility
    @FXML
    protected void toggleAudioControls() {
        if (audioControlsContainer != null) {
            audioControlsVisible = !audioControlsVisible;
            audioControlsContainer.setVisible(audioControlsVisible);
            audioControlsContainer.setManaged(audioControlsVisible);
            System.out.println("🔊 Audio controls " + (audioControlsVisible ? "shown" : "hidden"));

            // Update button text
            if (audioControlsButton != null) {
                if (audioControlsVisible) {
                    audioControlsButton.setText("🔊 Hide Audio Controls");
                    audioControlsButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
                } else {
                    audioControlsButton.setText("🔊 Show Audio Controls");
                    audioControlsButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white;");
                }
            }
        }
    }

    @FXML
    protected void toggleVideoControls() {
        if (videoControlsContainer != null) {
            videoControlsVisible = !videoControlsVisible;
            videoControlsContainer.setVisible(videoControlsVisible);
            videoControlsContainer.setManaged(videoControlsVisible);
            System.out.println("🎥 Video controls " + (videoControlsVisible ? "shown" : "hidden"));

            // Update button text
            if (videoControlsButton != null) {
                if (videoControlsVisible) {
                    videoControlsButton.setText("🎥 Hide Video Controls");
                    videoControlsButton.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white;");
                } else {
                    videoControlsButton.setText("🎥 Show Video Controls");
                    videoControlsButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white;");
                }
            }
        }
    }

    // Add the missing toggleDeafen method
    @FXML
    protected void toggleDeafen() {
        // Use centralized deafen control from HelloApplication
        HelloApplication.toggleDeafen();

        boolean isDeafened = HelloApplication.isDeafened();
        if (isDeafened) {
            addSystemMessage("You deafened yourself");
        } else {
            addSystemMessage("You undeafened yourself");
        }

        updateButtonStyles();
    }

    private void setupFallbackControls() {
        System.out.println("🔄 Using fallback audio/video controls");
        // Ensure basic controls are visible
        if (audioButton != null) audioButton.setVisible(true);
        if (videoButton != null) videoButton.setVisible(true);
        if (recordButton != null) recordButton.setVisible(true);
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        if (stage != null) {
            setupWindowControls();
            setupTitleBarDragging();
        }
    }

    private void setupWindowControls() {
        if (stage == null) return;

        // Store original window size and position
        originalX = stage.getX();
        originalY = stage.getY();
        originalWidth = stage.getWidth();
        originalHeight = stage.getHeight();

        // Update maximize button text based on initial state
        updateMaximizeButton();
    }

    private void setupTitleBarDragging() {
        if (titleBar == null) return;

        titleBar.setOnMousePressed((MouseEvent event) -> {
            if (stage != null) {
                xOffset = event.getSceneX();
                yOffset = event.getSceneY();
            }
        });

        titleBar.setOnMouseDragged((MouseEvent event) -> {
            if (stage != null && !isMaximized) {
                stage.setX(event.getScreenX() - xOffset);
                stage.setY(event.getScreenY() - yOffset);
            }
        });

        // Double click to maximize/restore
        titleBar.setOnMouseClicked((MouseEvent event) -> {
            if (event.getClickCount() == 2) {
                toggleMaximize();
            }
        });
    }

    @FXML
    private void onMinimizeButton() {
        if (stage != null) {
            stage.setIconified(true);
        }
    }

    @FXML
    private void onMaximizeButton() {
        toggleMaximize();
    }

    @FXML
    private void onCloseButton() {
        try {
            onLeaveClick();
        } catch (Exception e) {
            e.printStackTrace();
            if (stage != null) {
                stage.close();
            }
        }
    }

    private void toggleMaximize() {
        if (stage != null) {
            if (isMaximized) {
                // Restore to original size and position
                stage.setMaximized(false);
                stage.setX(originalX);
                stage.setY(originalY);
                stage.setWidth(originalWidth);
                stage.setHeight(originalHeight);
                isMaximized = false;
            } else {
                // Store current position and size before maximizing
                originalX = stage.getX();
                originalY = stage.getY();
                originalWidth = stage.getWidth();
                originalHeight = stage.getHeight();

                // Get screen bounds and maximize
                Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
                stage.setX(screenBounds.getMinX());
                stage.setY(screenBounds.getMinY());
                stage.setWidth(screenBounds.getWidth());
                stage.setHeight(screenBounds.getHeight());

                isMaximized = true;
            }
            updateMaximizeButton();
        }
    }

    private void updateMaximizeButton() {
        if (maximizeButton != null) {
            if (isMaximized) {
                maximizeButton.setText("🗗");
                maximizeButton.setTooltip(new Tooltip("Restore Down"));
            } else {
                maximizeButton.setText("🗖");
                maximizeButton.setTooltip(new Tooltip("Maximize"));
            }
        }
    }

    private void updateMeetingInfo() {
        String meetingId = HelloApplication.getActiveMeetingId();
        if (meetingId != null) {
            meetingIdLabel.setText("Meeting ID: " + meetingId);
        }

        if (HelloApplication.isMeetingHost()) {
            hostLabel.setText("👑 Host");
            hostLabel.setStyle("-fx-text-fill: #e67e22; -fx-font-weight: bold;");
            if (recordButton != null) recordButton.setDisable(false);

            // Notify controllers about host status
            if (audioControlsController != null) {
                audioControlsController.onHostStatusChanged(true);
            }
            if (videoControlsController != null) {
                videoControlsController.onHostStatusChanged(true);
            }
        } else {
            hostLabel.setText("👤 Participant");
            hostLabel.setStyle("-fx-text-fill: #3498db; -fx-font-weight: bold;");
            if (recordButton != null) recordButton.setDisable(true);

            // Notify controllers about host status
            if (audioControlsController != null) {
                audioControlsController.onHostStatusChanged(false);
            }
            if (videoControlsController != null) {
                videoControlsController.onHostStatusChanged(false);
            }
        }
    }

    private void updateParticipantsList() {
        if (participantsList == null) return;

        participantsList.getItems().clear();
        participantsList.getItems().addAll(HelloApplication.getActiveParticipants());

        // Update participants count
        int count = HelloApplication.getActiveParticipants().size();
        if (participantsCountLabel != null) {
            participantsCountLabel.setText("Participants: " + count);
        }

        // Add host indicator
        if (!participantsList.getItems().isEmpty() && HelloApplication.isMeetingHost()) {
            String firstParticipant = participantsList.getItems().get(0);
            if (!firstParticipant.contains("👑")) {
                participantsList.getItems().set(0, "👑 " + firstParticipant + " (Host)");
            }
        }
    }

    private void setupChat() {
        if (chatInput == null) return;

        chatInput.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ENTER -> onSendChat();
            }
        });

        // Add welcome message to chat
        addSystemMessage("Welcome to the meeting! Meeting ID: " + HelloApplication.getActiveMeetingId());

        if (HelloApplication.isMeetingHost()) {
            addSystemMessage("You are the host of this meeting. You can start recordings.");
        }
    }

    private void startMeetingTimer() {
        meetingTimer = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(Duration.seconds(1), e -> updateTimer())
        );
        meetingTimer.setCycleCount(javafx.animation.Animation.INDEFINITE);
        meetingTimer.play();
    }

    private void updateTimer() {
        meetingSeconds++;
        int hours = meetingSeconds / 3600;
        int minutes = (meetingSeconds % 3600) / 60;
        int seconds = meetingSeconds % 60;

        if (timerLabel != null) {
            if (hours > 0) {
                timerLabel.setText(String.format("Time: %02d:%02d:%02d", hours, minutes, seconds));
            } else {
                timerLabel.setText(String.format("Time: %02d:%02d", minutes, seconds));
            }
        }
    }

    private void updateButtonStyles() {
        // Update audio button (fallback)
        if (audioButton != null) {
            if (audioMuted) {
                audioButton.setText("🔇 Unmute");
                audioButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 5;");
            } else {
                audioButton.setText("🎤 Mute");
                audioButton.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-background-radius: 5;");
            }
        }

        // Update video button (fallback)
        if (videoButton != null) {
            if (videoOn) {
                videoButton.setText("📹 Stop Video");
                videoButton.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-background-radius: 5;");
            } else {
                videoButton.setText("📹 Start Video");
                videoButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 5;");
            }
        }

        // Update record button
        if (recordButton != null) {
            if (recording) {
                recordButton.setText("🔴 Stop Recording");
                recordButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 5;");
            } else {
                recordButton.setText("⏺ Start Recording");
                recordButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-background-radius: 5;");
            }
        }

        // Update panel toggle buttons
        if (participantsButton != null) {
            if (participantsVisible) {
                participantsButton.setText("👥 Hide Participants");
                participantsButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-background-radius: 5;");
            } else {
                participantsButton.setText("👥 Show Participants");
                participantsButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-background-radius: 5;");
            }
        }

        if (chatButton != null) {
            if (chatVisible) {
                chatButton.setText("💬 Hide Chat");
                chatButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-background-radius: 5;");
            } else {
                chatButton.setText("💬 Show Chat");
                chatButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-background-radius: 5;");
            }
        }

        // Update audio controls toggle button
        if (audioControlsButton != null) {
            if (audioControlsVisible) {
                audioControlsButton.setText("🔊 Hide Audio Controls");
                audioControlsButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-background-radius: 5;");
            } else {
                audioControlsButton.setText("🔊 Show Audio Controls");
                audioControlsButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-background-radius: 5;");
            }
        }

        // Update video controls toggle button
        if (videoControlsButton != null) {
            if (videoControlsVisible) {
                videoControlsButton.setText("🎥 Hide Video Controls");
                videoControlsButton.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-background-radius: 5;");
            } else {
                videoControlsButton.setText("🎥 Show Video Controls");
                videoControlsButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-background-radius: 5;");
            }
        }

        // Style other buttons
        if (shareButton != null) {
            shareButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-background-radius: 5;");
        }
        if (leaveButton != null) {
            leaveButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5;");
        }

        // Add hover effects for window controls
        setupWindowControlHoverEffects();
    }

    private void setupWindowControlHoverEffects() {
        if (minimizeButton == null || maximizeButton == null || closeButton == null) return;

        // Minimize button hover
        minimizeButton.setOnMouseEntered(e -> minimizeButton.setStyle("-fx-background-color: #5a6c7d; -fx-text-fill: white; -fx-border-color: transparent;"));
        minimizeButton.setOnMouseExited(e -> minimizeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-border-color: transparent;"));

        // Maximize button hover
        maximizeButton.setOnMouseEntered(e -> maximizeButton.setStyle("-fx-background-color: #5a6c7d; -fx-text-fill: white; -fx-border-color: transparent;"));
        maximizeButton.setOnMouseExited(e -> maximizeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-border-color: transparent;"));

        // Close button hover
        closeButton.setOnMouseEntered(e -> closeButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-border-color: transparent;"));
        closeButton.setOnMouseExited(e -> closeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-border-color: transparent;"));
    }

    // ---------------- Panel Controls ----------------
    @FXML
    protected void toggleParticipantsPanel() {
        participantsVisible = !participantsVisible;
        if (participantsPanel != null) {
            participantsPanel.setVisible(participantsVisible);
            participantsPanel.setManaged(participantsVisible);
        }
        updateButtonStyles();
    }

    @FXML
    protected void toggleChatPanel() {
        chatVisible = !chatVisible;
        if (chatPanel != null) {
            chatPanel.setVisible(chatVisible);
            chatPanel.setManaged(chatVisible);
        }
        updateButtonStyles();
    }

    // ---------------- REAL Audio / Video Controls ----------------
    @FXML
    protected void toggleAudio() {
        if (audioMuted) {
            // Unmute - start capturing audio
            audioMuted = false;
            startMicrophone();
            addSystemMessage("You unmuted your audio");
            System.out.println("🎤 Audio UNMUTED - microphone started");
        } else {
            // Mute - stop capturing audio
            audioMuted = true;
            stopMicrophone();
            addSystemMessage("You muted your audio");
            System.out.println("🎤 Audio MUTED - microphone stopped");
        }

        updateButtonStyles();

        // Send status via WebSocket
        if (HelloApplication.getActiveMeetingId() != null) {
            String status = audioMuted ? "muted their audio" : "unmuted their audio";
            HelloApplication.sendWebSocketMessage("AUDIO_STATUS", HelloApplication.getActiveMeetingId(), status);
        }
    }

    @FXML
    protected void toggleVideo() {
        if (!videoOn) {
            // Start video - turn on camera
            videoOn = true;
            startCamera();
            addSystemMessage("You started your video");
            System.out.println("📷 Video STARTED - camera activated");
        } else {
            // Stop video - turn off camera
            videoOn = false;
            stopCamera();
            addSystemMessage("You stopped your video");
            System.out.println("📷 Video STOPPED - camera deactivated");
        }

        updateButtonStyles();

        // Send status via WebSocket
        if (HelloApplication.getActiveMeetingId() != null) {
            String status = videoOn ? "started video" : "stopped video";
            HelloApplication.sendWebSocketMessage("VIDEO_STATUS", HelloApplication.getActiveMeetingId(), status);
        }
    }

    // ---------------- Recording ----------------
    @FXML
    protected void onToggleRecording() {
        if (!HelloApplication.isMeetingHost()) {
            showAlert("Permission Denied", "Only the meeting host can start recordings.");
            return;
        }

        if (!recording) {
            startRecording();
        } else {
            stopRecording();
        }
    }

    private void startRecording() {
        try {
            String fileName = "Meeting_" + HelloApplication.getActiveMeetingId() + "_" +
                    new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date()) + ".txt";
            File dir = new File("recordings");
            if (!dir.exists()) dir.mkdirs();

            currentRecordingFile = new File(dir, fileName);
            FileWriter writer = new FileWriter(currentRecordingFile);
            writer.write("Meeting Recording - " + new Date() + "\n");
            writer.write("Meeting ID: " + HelloApplication.getActiveMeetingId() + "\n");
            writer.write("Host: " + HelloApplication.getLoggedInUser() + "\n");
            writer.write("Participants: " + String.join(", ", HelloApplication.getActiveParticipants()) + "\n");
            writer.write("--- Recording Start ---\n");
            writer.close();

            recording = true;
            updateButtonStyles();
            addSystemMessage("🔴 Recording started...");

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Recording Error", "Failed to start recording!");
        }
    }

    private void stopRecording() {
        try {
            FileWriter writer = new FileWriter(currentRecordingFile, true);
            writer.write("--- Recording End ---\n");
            writer.write("Recording stopped at: " + new Date() + "\n");
            writer.close();

            recording = false;
            updateButtonStyles();
            addSystemMessage("✅ Recording saved: " + currentRecordingFile.getName());

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Recording Error", "Failed to stop recording!");
        }
    }

    // ---------------- Chat ----------------
    @FXML
    protected void onSendChat() {
        if (chatInput == null) return;

        String msg = chatInput.getText().trim();
        if (!msg.isEmpty()) {
            String username = HelloApplication.getLoggedInUser();
            if (username == null) username = "Me";

            addUserMessage(username + ": " + msg);
            chatInput.clear();

            // Send via WebSocket if connected
            if (HelloApplication.isWebSocketConnected() && HelloApplication.getActiveMeetingId() != null) {
                HelloApplication.sendWebSocketMessage("CHAT", HelloApplication.getActiveMeetingId(), msg);
            }
        }
    }

    @FXML
    protected void onSendFile() {
        if (stage == null) stage = (Stage) chatBox.getScene().getWindow();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Send");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Files", "*.*"),
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"),
                new FileChooser.ExtensionFilter("Audio", "*.mp3", "*.wav"),
                new FileChooser.ExtensionFilter("Video", "*.mp4", "*.mov", "*.avi")
        );

        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            try {
                addSystemMessage("📎 File shared: " + file.getName());

                if (isImage(file)) {
                    displayImage(file);
                } else if (isAudio(file) || isVideo(file)) {
                    displayMedia(file);
                } else {
                    displayFileLink(file);
                }

                scrollToBottom();
            } catch (Exception ex) {
                ex.printStackTrace();
                showAlert("Error", "Failed to share file!");
            }
        }
    }

    @FXML
    protected void onClearChat() {
        if (chatBox != null) {
            chatBox.getChildren().clear();
            addSystemMessage("Chat cleared");
        }
    }

    private void displayImage(File file) {
        ImageView imgView = new ImageView(new Image(file.toURI().toString()));
        imgView.setFitWidth(200);
        imgView.setPreserveRatio(true);
        imgView.setStyle("-fx-border-color: #bdc3c7; -fx-border-radius: 5;");
        imgView.setOnMouseClicked(e -> downloadFile(file));

        VBox imageContainer = new VBox(5);
        imageContainer.getChildren().addAll(
                new Label("🖼 " + file.getName()),
                imgView
        );
        if (chatBox != null) {
            chatBox.getChildren().add(imageContainer);
        }
    }

    private void displayMedia(File file) {
        try {
            Media media = new Media(file.toURI().toString());
            MediaPlayer player = new MediaPlayer(media);
            MediaView mediaView = new MediaView(player);
            mediaView.setFitWidth(250);
            mediaView.setPreserveRatio(true);
            mediaView.setStyle("-fx-border-color: #bdc3c7; -fx-border-radius: 5;");
            mediaView.setOnMouseClicked(e -> downloadFile(file));

            // Stop previous media player
            if (currentMediaPlayer != null) {
                currentMediaPlayer.stop();
            }
            currentMediaPlayer = player;

            VBox mediaContainer = new VBox(5);
            mediaContainer.getChildren().addAll(
                    new Label(isVideo(file) ? "🎥 " + file.getName() : "🎵 " + file.getName()),
                    mediaView
            );
            if (chatBox != null) {
                chatBox.getChildren().add(mediaContainer);
            }

        } catch (Exception e) {
            displayFileLink(file);
        }
    }

    private void displayFileLink(File file) {
        Hyperlink fileLink = new Hyperlink("📎 " + file.getName() + " (" + getFileSize(file) + ")");
        fileLink.setStyle("-fx-text-fill: #3498db; -fx-border-color: transparent;");
        fileLink.setOnAction(e -> downloadFile(file));
        if (chatBox != null) {
            chatBox.getChildren().add(fileLink);
        }
    }

    private String getFileSize(File file) {
        long bytes = file.length();
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private void addUserMessage(String text) {
        addChatMessage(text, "#3498db", "white", "-fx-alignment: center-right;");
    }

    private void addSystemMessage(String text) {
        addChatMessage(text, "#2c3e50", "white", "-fx-alignment: center; -fx-font-style: italic;");
    }

    private void addChatMessage(String text, String bgColor, String textColor, String additionalStyle) {
        if (chatBox == null) return;

        Label messageLabel = new Label(text);
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(280);
        messageLabel.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: %s; -fx-padding: 8 12; -fx-background-radius: 12; %s",
                bgColor, textColor, additionalStyle));
        chatBox.getChildren().add(messageLabel);
        scrollToBottom();
    }

    private void scrollToBottom() {
        if (chatScroll == null) return;

        chatScroll.applyCss();
        chatScroll.layout();
        chatScroll.setVvalue(1.0);
    }

    // ---------------- Helpers ----------------
    private boolean isImage(File f) {
        String n = f.getName().toLowerCase();
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".gif");
    }

    private boolean isAudio(File f) {
        String n = f.getName().toLowerCase();
        return n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".m4a");
    }

    private boolean isVideo(File f) {
        String n = f.getName().toLowerCase();
        return n.endsWith(".mp4") || n.endsWith(".mov") || n.endsWith(".avi") || n.endsWith(".m4v");
    }

    private void downloadFile(File sourceFile) {
        if (stage == null) stage = (Stage) chatBox.getScene().getWindow();
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save File As");
        fileChooser.setInitialFileName(sourceFile.getName());
        File dest = fileChooser.showSaveDialog(stage);

        if (dest != null) {
            try {
                Files.copy(sourceFile.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                showAlert("Download Complete", "File downloaded successfully to:\n" + dest.getAbsolutePath());
            } catch (IOException e) {
                showAlert("Download Error", "Failed to download file!");
            }
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // ---------------- Navigation ----------------
    @FXML
    protected void onViewRecordings() throws Exception {
        HelloApplication.setRoot("recordings-view.fxml");
    }

    @FXML
    protected void onLeaveClick() throws Exception {
        // Stop media player if playing
        if (currentMediaPlayer != null) {
            currentMediaPlayer.stop();
        }

        // Stop recording if active
        if (recording) {
            stopRecording();
        }

        // Stop meeting timer
        if (meetingTimer != null) {
            meetingTimer.stop();
        }

        // Cleanup audio and video resources
        cleanupAudioVideoResources();

        // Notify controllers about meeting end
        if (audioControlsController != null) {
            audioControlsController.onMeetingStateChanged(false);
        }
        if (videoControlsController != null) {
            videoControlsController.onMeetingStateChanged(false);
        }

        // Leave meeting
        HelloApplication.leaveCurrentMeeting();
        if (stage != null) {
            stage.setMaximized(false);
        }
        HelloApplication.getPrimaryStage().setFullScreen(false);
        HelloApplication.setRoot("dashboard-view.fxml");
    }

    /**
     * Cleanup audio and video resources to release camera and microphone
     */
    private void cleanupAudioVideoResources() {
        System.out.println("🧹 Cleaning up audio/video resources...");

        // Stop camera and microphone
        stopCamera();
        stopMicrophone();

        // Cleanup audio resources
        if (audioControlsController != null) {
            try {
                // Call cleanup method if it exists
                audioControlsController.getClass().getMethod("cleanup").invoke(audioControlsController);
                System.out.println("✅ Audio resources cleaned up");
            } catch (Exception e) {
                System.out.println("⚠️ Audio cleanup not implemented or failed: " + e.getMessage());
            }
        }

        // Cleanup video resources
        if (videoControlsController != null) {
            try {
                // Call cleanup method if it exists
                videoControlsController.getClass().getMethod("cleanup").invoke(videoControlsController);
                System.out.println("✅ Video resources cleaned up");
            } catch (Exception e) {
                System.out.println("⚠️ Video cleanup not implemented or failed: " + e.getMessage());
            }
        }

        // Additional cleanup for any direct camera/microphone access
        cleanupDirectHardwareAccess();
    }

    /**
     * Direct hardware cleanup for camera and microphone
     */
    private void cleanupDirectHardwareAccess() {
        try {
            // Force stop any webcam access
            Class<?> webcamClass = null;
            try {
                webcamClass = Class.forName("com.github.sarxos.webcam.Webcam");
                java.lang.reflect.Method getWebcamsMethod = webcamClass.getMethod("getWebcams");
                java.util.List<?> webcams = (java.util.List<?>) getWebcamsMethod.invoke(null);

                for (Object webcam : webcams) {
                    java.lang.reflect.Method isOpenMethod = webcam.getClass().getMethod("isOpen");
                    java.lang.reflect.Method closeMethod = webcam.getClass().getMethod("close");

                    if ((Boolean) isOpenMethod.invoke(webcam)) {
                        closeMethod.invoke(webcam);
                        System.out.println("📷 Camera closed: " + webcam.getClass().getMethod("getName").invoke(webcam));
                    }
                }
            } catch (ClassNotFoundException e) {
                // Webcam library not available, skip
            }

            // Audio line cleanup
            try {
                Class<?> audioSystemClass = Class.forName("javax.sound.sampled.AudioSystem");
                java.lang.reflect.Method getLineMethod = audioSystemClass.getMethod("getLine",
                        Class.forName("javax.sound.sampled.Line$Info"));
                // Additional audio cleanup would go here
            } catch (ClassNotFoundException e) {
                // Audio classes not available, skip
            }

        } catch (Exception e) {
            System.err.println("❌ Error during hardware cleanup: " + e.getMessage());
        }
    }

    @FXML
    protected void onShareScreen(ActionEvent event) {
        try {
            addSystemMessage("🖥 Opening screen share...");
            HelloApplication.setRoot("share-screen-view.fxml");
        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Screen Share", "Failed to open screen sharing: " + e.getMessage());
        }
    }

    @FXML
    protected void onCopyMeetingId() {
        String meetingId = HelloApplication.getActiveMeetingId();
        if (meetingId != null) {
            javafx.scene.input.Clipboard clipboard = javafx.scene.input.Clipboard.getSystemClipboard();
            javafx.scene.input.ClipboardContent content = new javafx.scene.input.ClipboardContent();
            content.putString(meetingId);
            clipboard.setContent(content);
            addSystemMessage("Meeting ID copied to clipboard: " + meetingId);
        }
    }

    // Method to handle WebSocket messages (for chat integration)
    public void handleWebSocketMessage(String message) {
        // Parse message: "TYPE|MEETING_ID|USERNAME|CONTENT"
        String[] parts = message.split("\\|", 4);
        if (parts.length >= 4) {
            String type = parts[0];
            String meetingId = parts[1];
            String username = parts[2];
            String content = parts[3];

            if ("CHAT".equals(type) && meetingId.equals(HelloApplication.getActiveMeetingId())) {
                // Add chat message from other user
                Platform.runLater(() -> {
                    addUserMessage(username + ": " + content);
                });
            } else if ("USER_JOINED".equals(type)) {
                // Update participants list
                Platform.runLater(() -> {
                    updateParticipantsList();
                    addSystemMessage(username + " joined the meeting");
                });
            } else if ("USER_LEFT".equals(type)) {
                // Update participants list
                Platform.runLater(() -> {
                    updateParticipantsList();
                    addSystemMessage(username + " left the meeting");
                });
            } else if ("AUDIO_STATUS".equals(type)) {
                // Handle audio status updates from other participants
                Platform.runLater(() -> {
                    addSystemMessage(username + " " + content);
                });
            } else if ("VIDEO_STATUS".equals(type)) {
                // Handle video status updates from other participants
                Platform.runLater(() -> {
                    addSystemMessage(username + " " + content);
                });
            }
        }
    }

    /**
     * Getter for audio controls controller
     */
    public AudioControlsController getAudioControlsController() {
        return audioControlsController;
    }

    /**
     * Getter for video controls controller
     */
    public VideoControlsController getVideoControlsController() {
        return videoControlsController;
    }
}