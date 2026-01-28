package org.example.zoom;

import javafx.fxml.FXML;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.animation.PauseTransition;
import javafx.stage.Window;
import javafx.util.Duration;
import javafx.scene.input.MouseEvent;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.media.AudioClip;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.scene.image.WritableImage;
import javafx.util.Pair;

import com.github.sarxos.webcam.Webcam;
import com.github.sarxos.webcam.WebcamPanel;
import com.github.sarxos.webcam.WebcamResolution;
import javax.sound.sampled.*;
import javafx.scene.paint.Color;
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
import java.util.Optional;
import javafx.embed.swing.SwingFXUtils;
import org.example.zoom.webrtc.WebRTCClient;

import javax.imageio.ImageIO;
import java.util.Base64;

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

    // Screen size controls
    @FXML private MenuButton screenSizeButton;
    @FXML private MenuItem screenSizeSmall;
    @FXML private MenuItem screenSizeMedium;
    @FXML private MenuItem screenSizeLarge;
    @FXML private MenuItem screenSizeFull;
    @FXML private MenuItem screenSizeCustom;
    @FXML private Button toggleFullscreenButton;

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

    // Placeholder for camera feed
    @FXML private StackPane videoPlaceholder;

    // Video streaming quality
    private enum VideoQuality {
        LOW(160, 120, 5),      // 5 FPS
        MEDIUM(320, 240, 10),   // 10 FPS
        HIGH(640, 480, 15);     // 15 FPS

        final int width;
        final int height;
        final int fps;

        VideoQuality(int width, int height, int fps) {
            this.width = width;
            this.height = height;
            this.fps = fps;
        }
    }

    private VideoQuality currentVideoQuality = VideoQuality.MEDIUM;

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
    private boolean isDisplayingVideo = false;

    // Audio and Video controllers
    private AudioControlsController audioControlsController;
    private VideoControlsController videoControlsController;

    // Advanced MP4 Recording controller
    private MP4RecordingController mp4RecordingController;

    // Real camera and microphone
    private Webcam webcam;
    private TargetDataLine microphone;
    private AudioFormat audioFormat;
    private boolean cameraAvailable = false;
    private boolean microphoneAvailable = false;

    // Camera capture thread
    private Thread cameraThread;
    private volatile boolean cameraRunning = false;
    private volatile boolean streamingEnabled = false;

    // Audio capture thread
    private Thread audioThread;
    private volatile boolean audioRunning = false;

    // For window dragging
    private double xOffset = 0;
    private double yOffset = 0;
    private boolean isMaximized = false;
    private boolean isFullscreen = false;

    // Store original window size and position for restore
    private double originalX, originalY, originalWidth, originalHeight;

    // Meeting timer
    private javafx.animation.Timeline meetingTimer;
    private int meetingSeconds = 0;

    // Singleton instance
    private static MeetingController instance;

    // Participant tracking
    private List<String> currentParticipants = new ArrayList<>();

    // Active video streams from participants
    private List<String> activeVideoStreams = new ArrayList<>();

    // File transfer
    private FileTransferHandler fileTransferHandler;

    private org.example.zoom.webrtc.WebRTCClient webRTCClient;
    private boolean webRTCActive = false;

    // Screen size presets
    private enum ScreenSize {
        SMALL(800, 600, "Small (800x600)"),
        MEDIUM(1024, 768, "Medium (1024x768)"),
        LARGE(1280, 1024, "Large (1280x1024)"),
        HD(1920, 1080, "HD (1920x1080)"),
        CUSTOM(0, 0, "Custom...");

        final int width;
        final int height;
        final String displayName;

        ScreenSize(int width, int height, String displayName) {
            this.width = width;
            this.height = height;
            this.displayName = displayName;
        }
    }

    private ScreenSize currentScreenSize = ScreenSize.MEDIUM;

    @FXML
    public void initialize() {
        instance = this;

        System.out.println("🎬 MeetingController INITIALIZING...");
        System.out.println("🎬 User: " + HelloApplication.getLoggedInUser());
        System.out.println("🎬 Meeting ID: " + HelloApplication.getActiveMeetingId());

        // Initialize file transfer handler
        fileTransferHandler = new FileTransferHandler(this);

        // Create visible video placeholder
        createVideoPlaceholder();

        // Load controllers
        initializeAudioVideoControllers();

        // Setup UI
        setupScrollableChat();
        setupScrollableParticipants();
        initializeParticipantTracking();

        updateParticipantsList();
        setupChat();
        updateMeetingInfo();
        updateButtonStyles();
        startMeetingTimer();

        // Initialize screen size controls
        setupScreenSizeControls();

        System.out.println("✅ MeetingController initialized successfully");
    }

    /**
     * Create visible video placeholder
     */
    private void createVideoPlaceholder() {
        Platform.runLater(() -> {
            if (videoArea != null) {
                // Create placeholder canvas
                Canvas canvas = new Canvas(640, 480);
                GraphicsContext gc = canvas.getGraphicsContext2D();

                // Gradient background matching theme
                gc.setFill(Color.rgb(30, 40, 50)); // Dark blue-gray matching theme
                gc.fillRect(0, 0, 640, 480);

                // Border
                gc.setStroke(Color.rgb(60, 80, 100)); // Matching border color
                gc.setLineWidth(2);
                gc.strokeRect(10, 10, 620, 460);

                // Text
                gc.setFill(Color.WHITE);
                gc.setFont(new javafx.scene.text.Font(20));
                gc.fillText("VIDEO STREAM", 240, 200);

                gc.setFill(Color.LIGHTGRAY);
                gc.setFont(new javafx.scene.text.Font(14));
                gc.fillText("Click 'Start Video' to begin streaming", 200, 240);

                // Convert to image
                WritableImage placeholderImage = canvas.snapshot(null, null);

                if (videoDisplay != null) {
                    videoDisplay.setImage(placeholderImage);
                    videoDisplay.setVisible(true);
                }

                System.out.println("✅ Created video placeholder");
            }
        });
    }

    /**
     * Setup screen size controls
     */
    private void setupScreenSizeControls() {
        if (screenSizeButton != null) {
            screenSizeButton.setText("📐 " + currentScreenSize.displayName);

            // Set up screen size menu items
            if (screenSizeSmall != null) {
                screenSizeSmall.setOnAction(e -> setScreenSize(ScreenSize.SMALL));
            }
            if (screenSizeMedium != null) {
                screenSizeMedium.setOnAction(e -> setScreenSize(ScreenSize.MEDIUM));
            }
            if (screenSizeLarge != null) {
                screenSizeLarge.setOnAction(e -> setScreenSize(ScreenSize.LARGE));
            }
            if (screenSizeFull != null) {
                screenSizeFull.setOnAction(e -> setScreenSize(ScreenSize.HD));
            }
            if (screenSizeCustom != null) {
                screenSizeCustom.setOnAction(e -> showCustomSizeDialog());
            }
        }

        if (toggleFullscreenButton != null) {
            toggleFullscreenButton.setOnAction(e -> toggleFullscreen());
        }
    }

    /**
     * Set screen size
     */
    private void setScreenSize(ScreenSize size) {
        if (stage == null) {
            stage = (Stage) titleBar.getScene().getWindow();
        }

        if (stage != null) {
            // Exit fullscreen if changing size
            if (isFullscreen) {
                toggleFullscreen();
            }

            // Store current size if going to custom
            if (size == ScreenSize.CUSTOM) {
                showCustomSizeDialog();
                return;
            }

            // Set new size
            stage.setWidth(size.width);
            stage.setHeight(size.height);
            currentScreenSize = size;

            // Update button text
            if (screenSizeButton != null) {
                screenSizeButton.setText("📐 " + size.displayName);
            }

            // Center window on screen
            centerWindowOnScreen();

            // Update video display size
            updateVideoDisplaySize();

            System.out.println("✅ Screen size set to: " + size.displayName);
            addSystemMessage("Screen size changed to " + size.displayName);
        }
    }

    /**
     * Show custom size dialog
     */
    private void showCustomSizeDialog() {
        if (stage == null) return;

        // Create custom dialog
        Dialog<Pair<Integer, Integer>> dialog = new Dialog<>();
        dialog.setTitle("Custom Screen Size");
        dialog.setHeaderText("Enter custom width and height");

        // Set the button types
        ButtonType setButtonType = new ButtonType("Set", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(setButtonType, ButtonType.CANCEL);

        // Create the width and height fields
        GridPane grid = new GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new javafx.geometry.Insets(20, 150, 10, 10));

        TextField widthField = new TextField(String.valueOf((int)stage.getWidth()));
        widthField.setPromptText("Width");
        TextField heightField = new TextField(String.valueOf((int)stage.getHeight()));
        heightField.setPromptText("Height");

        grid.add(new Label("Width:"), 0, 0);
        grid.add(widthField, 1, 0);
        grid.add(new Label("Height:"), 0, 1);
        grid.add(heightField, 1, 1);

        dialog.getDialogPane().setContent(grid);

        // Request focus on width field
        Platform.runLater(() -> widthField.requestFocus());

        // Convert result to width/height pair
        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == setButtonType) {
                try {
                    int width = Integer.parseInt(widthField.getText());
                    int height = Integer.parseInt(heightField.getText());

                    // Validate dimensions
                    if (width >= 800 && height >= 600 && width <= 3840 && height <= 2160) {
                        return new Pair<>(width, height);
                    } else {
                        showAlert("Invalid Size", "Width must be 800-3840, Height must be 600-2160");
                    }
                } catch (NumberFormatException e) {
                    showAlert("Invalid Input", "Please enter valid numbers");
                }
            }
            return null;
        });

        Optional<Pair<Integer, Integer>> result = dialog.showAndWait();
        result.ifPresent(dimensions -> {
            // Exit fullscreen if active
            if (isFullscreen) {
                toggleFullscreen();
            }

            // Set custom size
            stage.setWidth(dimensions.getKey());
            stage.setHeight(dimensions.getValue());

            currentScreenSize = ScreenSize.CUSTOM;
            if (screenSizeButton != null) {
                screenSizeButton.setText("📐 Custom (" + dimensions.getKey() + "x" + dimensions.getValue() + ")");
            }

            centerWindowOnScreen();
            updateVideoDisplaySize();

            System.out.println("✅ Custom screen size set: " + dimensions.getKey() + "x" + dimensions.getValue());
            addSystemMessage("Screen size set to custom: " + dimensions.getKey() + "x" + dimensions.getValue());
        });
    }

    /**
     * Toggle fullscreen mode
     */
    @FXML
    private void toggleFullscreen() {
        if (stage == null) {
            stage = (Stage) titleBar.getScene().getWindow();
        }

        if (stage != null) {
            isFullscreen = !isFullscreen;
            stage.setFullScreen(isFullscreen);

            // Update button text
            if (toggleFullscreenButton != null) {
                if (isFullscreen) {
                    toggleFullscreenButton.setText("⛶ Exit Fullscreen");
                    toggleFullscreenButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
                } else {
                    toggleFullscreenButton.setText("⛶ Fullscreen");
                    toggleFullscreenButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
                }
            }

            System.out.println("✅ Fullscreen " + (isFullscreen ? "enabled" : "disabled"));
            addSystemMessage("Fullscreen " + (isFullscreen ? "enabled" : "disabled"));
        }
    }

    /**
     * Center window on screen
     */
    private void centerWindowOnScreen() {
        if (stage == null) return;

        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        stage.setX((screenBounds.getWidth() - stage.getWidth()) / 2);
        stage.setY((screenBounds.getHeight() - stage.getHeight()) / 2);
    }

    /**
     * Update video display size based on window size
     */
    private void updateVideoDisplaySize() {
        if (videoArea == null || videoDisplay == null) return;

        Platform.runLater(() -> {
            double newWidth = videoArea.getWidth() * 0.9;
            double newHeight = videoArea.getHeight() * 0.8;

            // Ensure minimum size
            newWidth = Math.max(newWidth, 320);
            newHeight = Math.max(newHeight, 240);

            videoDisplay.setFitWidth(newWidth);
            videoDisplay.setFitHeight(newHeight);

            System.out.println("✅ Video display resized to: " + newWidth + "x" + newHeight);
        });
    }

    /**
     * Handle window resize events
     */
    private void setupWindowResizeHandling() {
        if (stage == null) return;

        stage.widthProperty().addListener((obs, oldVal, newVal) -> {
            updateVideoDisplaySize();
            updateUIForNewSize(newVal.doubleValue(), stage.getHeight());
        });

        stage.heightProperty().addListener((obs, oldVal, newVal) -> {
            updateVideoDisplaySize();
            updateUIForNewSize(stage.getWidth(), newVal.doubleValue());
        });
    }

    /**
     * Update UI based on new window size
     */
    private void updateUIForNewSize(double width, double height) {
        Platform.runLater(() -> {
            // Adjust panel sizes based on window size
            if (width < 1000) {
                // Compact mode
                if (participantsPanel != null) {
                    participantsPanel.setPrefWidth(200);
                    participantsPanel.setMaxWidth(200);
                }
                if (chatPanel != null) {
                    chatPanel.setPrefWidth(280);
                    chatPanel.setMaxWidth(280);
                }
            } else {
                // Normal mode
                if (participantsPanel != null) {
                    participantsPanel.setPrefWidth(250);
                    participantsPanel.setMaxWidth(250);
                }
                if (chatPanel != null) {
                    chatPanel.setPrefWidth(320);
                    chatPanel.setMaxWidth(320);
                }
            }

            // Update current screen size display
            if (!isFullscreen && screenSizeButton != null) {
                currentScreenSize = getClosestPreset((int)width, (int)height);
                screenSizeButton.setText("📐 " + currentScreenSize.displayName);
            }
        });
    }

    /**
     * Get closest preset for current size
     */
    private ScreenSize getClosestPreset(int width, int height) {
        ScreenSize closest = ScreenSize.MEDIUM;
        int minDiff = Integer.MAX_VALUE;

        for (ScreenSize size : ScreenSize.values()) {
            if (size == ScreenSize.CUSTOM) continue;

            int diff = Math.abs(width - size.width) + Math.abs(height - size.height);
            if (diff < minDiff) {
                minDiff = diff;
                closest = size;
            }
        }

        return closest;
    }

    /**
     * Enable dragging for popup windows
     */
    private void enablePopupDragging(Parent root, Stage popupStage) {
        final double[] xOffset = new double[1];
        final double[] yOffset = new double[1];

        // Try to find a title bar
        HBox titleBar = (HBox) root.lookup("#titleBar");

        if (titleBar != null) {
            // Use title bar for dragging
            titleBar.setOnMousePressed(event -> {
                xOffset[0] = event.getSceneX();
                yOffset[0] = event.getSceneY();
            });

            titleBar.setOnMouseDragged(event -> {
                popupStage.setX(event.getScreenX() - xOffset[0]);
                popupStage.setY(event.getScreenY() - yOffset[0]);
            });
        } else {
            // Make entire window draggable
            root.setOnMousePressed(event -> {
                xOffset[0] = event.getSceneX();
                yOffset[0] = event.getSceneY();
            });

            root.setOnMouseDragged(event -> {
                popupStage.setX(event.getScreenX() - xOffset[0]);
                popupStage.setY(event.getScreenY() - yOffset[0]);
            });
        }
    }

    private void initializeWebRTC() {
        String username = HelloApplication.getLoggedInUser();

        webRTCClient = new WebRTCClient(username, new WebRTCClient.WebRTCCallbacks() {
            @Override
            public void onLocalVideoFrame(Image frame) {
                Platform.runLater(() -> {
                    if (videoDisplay != null) {
                        videoDisplay.setImage(frame);
                        videoDisplay.setVisible(true);
                    }
                });
            }

            @Override
            public void onRemoteVideoFrame(String peerId, Image frame) {
                Platform.runLater(() -> {
                    // Display remote video
                    displayVideoFrame(peerId, frame);
                });
            }

            @Override
            public void onAudioStateChanged(boolean enabled) {
                audioMuted = !enabled;
                updateButtonStyles();
            }

            @Override
            public void onVideoStateChanged(boolean enabled) {
                videoOn = enabled;
                updateButtonStyles();
            }

            @Override
            public void onConnectionStateChanged(String state) {
                Platform.runLater(() -> {
                    addSystemMessage("WebRTC: " + state);
                });
            }

            @Override
            public void onError(String error) {
                Platform.runLater(() -> {
                    addSystemMessage("WebRTC Error: " + error);
                });
            }
        });
    }

    /**
     * Initialize audio and video controllers
     */
    private void initializeAudioVideoControllers() {
        System.out.println("🔊🎥 Initializing audio/video controllers...");

        try {
            // Initialize audio controls
            if (audioControlsContainer != null) {
                try {
                    FXMLLoader audioLoader = new FXMLLoader(getClass().getResource("audio-controls.fxml"));
                    audioLoader.load();
                    audioControlsController = audioLoader.getController();
                    if (audioControlsController != null) {
                        audioControlsController.setMeetingController(this);
                        System.out.println("✅ Audio controls controller initialized");
                    } else {
                        System.err.println("❌ Audio controls controller is null");
                    }
                } catch (IOException e) {
                    System.err.println("❌ Failed to load audio controls: " + e.getMessage());
                    // Create fallback audio controller
                    setupFallbackAudioControls();
                }
            }

            // Initialize video controls
            if (videoControlsContainer != null) {
                try {
                    FXMLLoader videoLoader = new FXMLLoader(getClass().getResource("video-controls.fxml"));
                    videoLoader.load();
                    videoControlsController = videoLoader.getController();

                    if (videoControlsController != null) {
                        // Try to call setMeetingController method
                        try {
                            // Check if the method exists
                            java.lang.reflect.Method method = videoControlsController.getClass()
                                    .getMethod("setMeetingController", MeetingController.class);
                            // Invoke the method
                            method.invoke(videoControlsController, this);
                            System.out.println("✅ Video controls controller initialized and connected");
                        } catch (NoSuchMethodException e) {
                            System.err.println("❌ VideoControlsController doesn't have setMeetingController method");
                            // Try to set it using direct access if possible
                            try {
                                videoControlsController.setMeetingController(this);
                            } catch (Exception e2) {
                                System.err.println("❌ Could not set meeting controller: " + e2.getMessage());
                            }
                        } catch (java.lang.IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
                            System.err.println("❌ Error invoking setMeetingController: " + e.getMessage());
                            // Try direct assignment
                            try {
                                videoControlsController.setMeetingController(this);
                            } catch (Exception e2) {
                                System.err.println("❌ Direct assignment also failed: " + e2.getMessage());
                            }
                        }
                    } else {
                        System.err.println("❌ Video controls controller is null");
                        // Create a new instance if possible
                        try {
                            videoControlsController = new VideoControlsController();
                            videoControlsController.setMeetingController(this);
                            System.out.println("✅ Created new VideoControlsController instance");
                        } catch (Exception e) {
                            System.err.println("❌ Could not create VideoControlsController: " + e.getMessage());
                        }
                    }
                } catch (IOException e) {
                    System.err.println("❌ Failed to load video controls: " + e.getMessage());
                    // Create fallback video controller
                    setupFallbackVideoControls();

                    // Try to create a basic controller anyway
                    try {
                        videoControlsController = new VideoControlsController();
                        videoControlsController.setMeetingController(this);
                        System.out.println("✅ Created fallback VideoControlsController");
                    } catch (Exception e2) {
                        System.err.println("❌ Could not create fallback controller: " + e2.getMessage());
                    }
                }
            }

            // Initialize hardware after controllers are loaded
            initializeHardware();

            // Update UI state based on current audio/video status
            updateButtonStyles();

        } catch (Exception e) {
            System.err.println("❌ Error in initializeAudioVideoControllers: " + e.getMessage());
            e.printStackTrace();

            // Setup fallback controls if FXML loading fails
            setupFallbackControls();
        }
    }

    /**
     * Initialize participant tracking from database
     */
    private void initializeParticipantTracking() {
        String meetingId = HelloApplication.getActiveMeetingId();
        if (meetingId != null) {
            // Load existing participants from database
            List<String> participants = Database.getParticipants(meetingId);
            currentParticipants.clear();
            currentParticipants.addAll(participants);

            System.out.println("👥 Loaded " + participants.size() + " participants from database: " + participants);

            // Add host if not already in list
            String host = HelloApplication.getLoggedInUser();
            if (host != null && !currentParticipants.contains(host)) {
                currentParticipants.add(host);
                System.out.println("👑 Added host to participants: " + host);
            }
        }
    }

    /**
     * Safely get stage from any available component
     */
    private Stage getStageFromAnyComponent() {
        // Try multiple components to find one with a scene
        Node[] components = {chatBox, videoArea, titleBar, participantsList, chatInput, meetingIdLabel};

        for (Node component : components) {
            if (component != null) {
                Scene scene = component.getScene();
                if (scene != null) {
                    Window window = scene.getWindow();
                    if (window instanceof Stage) {
                        System.out.println("✅ Found stage from: " + component.getClass().getSimpleName());
                        return (Stage) window;
                    }
                }
            }
        }

        // Last resort: try the primary stage
        try {
            Stage primaryStage = HelloApplication.getPrimaryStage();
            if (primaryStage != null) {
                System.out.println("✅ Using primary stage as fallback");
                return primaryStage;
            }
        } catch (Exception e) {
            System.err.println("❌ Could not get primary stage: " + e.getMessage());
        }

        System.err.println("❌ Could not find stage from any component");
        return null;
    }

    /**
     * Setup scrollable chat area
     */
    private void setupScrollableChat() {
        if (chatBox != null) {
            // Make chat box scrollable with proper constraints
            chatBox.setMaxHeight(Double.MAX_VALUE);
            VBox.setVgrow(chatBox, Priority.ALWAYS);

            // Ensure scroll pane works properly
            if (chatScroll != null) {
                chatScroll.setFitToWidth(true);
                chatScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
                chatScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                chatScroll.setPrefViewportHeight(400); // Set preferred height

                // Auto-scroll to bottom when new messages are added
                chatBox.heightProperty().addListener((observable, oldValue, newValue) -> {
                    Platform.runLater(() -> {
                        chatScroll.setVvalue(1.0);
                    });
                });
            }
        }
    }

    /**
     * Setup scrollable participants list
     */
    private void setupScrollableParticipants() {
        if (participantsList != null) {
            participantsList.setMaxHeight(Double.MAX_VALUE);
            participantsList.setPrefHeight(300); // Set preferred height
            participantsList.setPlaceholder(new Label("No participants in meeting"));

            // Make list view scrollable with better styling
            participantsList.setCellFactory(param -> new ListCell<String>() {
                @Override
                protected void updateItem(String item, boolean empty) {
                    super.updateItem(item, empty);
                    if (empty || item == null) {
                        setText(null);
                        setGraphic(null);
                        setStyle("");
                    } else {
                        setText(item);
                        setStyle("-fx-padding: 10px; -fx-font-size: 13px; -fx-border-color: #bdc3c7; -fx-border-width: 0 0 1 0; -fx-text-fill: #2c3e50;");
                        setPrefHeight(40);
                    }
                }
            });
        }
    }

    /**
     * Initialize camera and microphone hardware - FIXED VERSION
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

                // Set default resolution but DON'T open the camera yet
                // We'll open it when we actually start streaming
                try {
                    webcam.setViewSize(new java.awt.Dimension(currentVideoQuality.width, currentVideoQuality.height));
                    System.out.println("📷 Default resolution set to " + currentVideoQuality.width + "x" + currentVideoQuality.height);
                } catch (Exception e) {
                    System.out.println("⚠️ Could not set default resolution: " + e.getMessage());
                    // Camera might not support this resolution, we'll handle it when opening
                }

                cameraAvailable = true;
                System.out.println("✅ Camera initialized (not opened yet): " + webcam.getName());
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
     * Enhanced participant list update with real-time tracking
     */
    private void updateParticipantsList() {
        if (participantsList == null) return;

        String meetingId = HelloApplication.getActiveMeetingId();
        if (meetingId == null) return;

        // Get actual participants from database AND current tracking
        List<String> databaseParticipants = Database.getParticipants(meetingId);

        // Merge database participants with current tracking
        for (String participant : databaseParticipants) {
            if (!currentParticipants.contains(participant)) {
                currentParticipants.add(participant);
            }
        }

        participantsList.getItems().clear();

        if (!currentParticipants.isEmpty()) {
            // Add host indicator to the actual host
            String hostUsername = HelloApplication.getLoggedInUser();
            for (String participant : currentParticipants) {
                String displayName = participant;
                if (participant.equals(hostUsername) && HelloApplication.isMeetingHost()) {
                    displayName = "👑 " + participant + " (Host)";
                } else if (activeVideoStreams.contains(participant)) {
                    displayName = "📹 " + participant;
                } else {
                    displayName = "👤 " + participant;
                }
                participantsList.getItems().add(displayName);
            }
        }

        // Update participants count
        int count = currentParticipants.size();
        if (participantsCountLabel != null) {
            participantsCountLabel.setText("Participants: " + count);
        }

        System.out.println("👥 Updated participants list: " + currentParticipants);
    }

    /**
     * Add participant with proper tracking
     */
    public void addParticipant(String username) {
        if (username != null && !currentParticipants.contains(username)) {
            currentParticipants.add(username);

            String meetingId = HelloApplication.getActiveMeetingId();
            if (meetingId != null) {
                // Add to database
                Database.addParticipant(meetingId, username);
            }

            Platform.runLater(() -> {
                updateParticipantsList();
                addSystemMessage(username + " joined the meeting");
            });

            System.out.println("✅ Participant added: " + username);
        }
    }

    /**
     * Remove participant with proper tracking
     */
    public void removeParticipant(String username) {
        if (currentParticipants.remove(username)) {
            String meetingId = HelloApplication.getActiveMeetingId();
            if (meetingId != null) {
                // Remove from database
                Database.removeParticipant(meetingId, username);
            }

            // Remove from active video streams
            activeVideoStreams.remove(username);

            Platform.runLater(() -> {
                updateParticipantsList();
                addSystemMessage(username + " left the meeting");

                // Clear their video if displayed
                if (isDisplayingVideoFromUser(username)) {
                    clearVideoFromUser(username);
                }
            });

            System.out.println("✅ Participant removed: " + username);
        }
    }

    private void setupChat() {
        if (chatInput == null) return;

        chatInput.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ENTER -> onSendChat();
            }
        });

        // Load previous chat messages from database
        loadChatHistory();

        // Add welcome message to chat
        addSystemMessage("Welcome to the meeting! Meeting ID: " + HelloApplication.getActiveMeetingId());

        if (HelloApplication.isMeetingHost()) {
            addSystemMessage("You are the host of this meeting. You can start recordings.");
        }
    }

    /**
     * Load chat history from database for this meeting
     */
    private void loadChatHistory() {
        String meetingId = HelloApplication.getActiveMeetingId();
        if (meetingId != null) return;

        System.out.println("📖 Loading chat history for meeting: " + meetingId);
        List<Database.ChatMessage> chatHistory = Database.getChatMessages(meetingId);

        if (chatHistory.isEmpty()) {
            System.out.println("💬 No previous chat history found");
        } else {
            System.out.println("💬 Loading " + chatHistory.size() + " previous chat messages");
        }

        for (Database.ChatMessage chatMessage : chatHistory) {
            if ("SYSTEM".equals(chatMessage.getMessageType())) {
                addSystemMessage(chatMessage.getMessage());
            } else {
                addUserMessage(chatMessage.getUsername() + ": " + chatMessage.getMessage());
            }
        }

        // Scroll to bottom after loading history
        scrollToBottom();
    }

    @FXML
    protected void onSendChat() {
        if (chatInput == null) return;

        String msg = chatInput.getText().trim();
        if (!msg.isEmpty()) {
            String username = HelloApplication.getLoggedInUser();
            if (username == null) username = "Me";

            // Use actual username for the message
            addUserMessage(username + ": " + msg);
            chatInput.clear();

            // Save to database with correct username
            String meetingId = HelloApplication.getActiveMeetingId();
            if (meetingId != null) {
                boolean saved = Database.saveChatMessage(meetingId, username, msg, "USER");
                if (saved) {
                    System.out.println("✅ Chat message saved to database for user: " + username);
                } else {
                    System.err.println("❌ Failed to save chat message to database");
                }
            }

            // Send via WebSocket with correct username
            if (HelloApplication.isWebSocketConnected() && HelloApplication.getActiveMeetingId() != null) {
                HelloApplication.sendWebSocketMessage("CHAT", HelloApplication.getActiveMeetingId(), username, msg);
                System.out.println("📤 Sent chat message via WebSocket: " + msg);
            }
        }
    }

    private void addUserMessage(String text) {
        addChatMessage(text, "#3498db", "white", "-fx-alignment: center-left; -fx-background-insets: 5;");
    }

    /**
     * Changed from private to public to allow access from MP4RecordingController
     */
    public void addSystemMessage(String text) {
        addChatMessage("💬 " + text, "#2c3e50", "white", "-fx-alignment: center; -fx-font-style: italic; -fx-background-insets: 5;");

        // Save system message to database
        String meetingId = HelloApplication.getActiveMeetingId();
        String username = HelloApplication.getLoggedInUser();
        if (meetingId != null && username != null) {
            Database.saveChatMessage(meetingId, username, text, "SYSTEM");
        }
    }

    private void addChatMessage(String text, String bgColor, String textColor, String additionalStyle) {
        if (chatBox == null) return;

        Label messageLabel = new Label(text);
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(380); // Increased max width for better readability
        messageLabel.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: %s; -fx-padding: 10 15; -fx-background-radius: 15; -fx-font-size: 13px; %s",
                bgColor, textColor, additionalStyle));

        // Add some spacing between messages
        VBox.setMargin(messageLabel, new javafx.geometry.Insets(2, 5, 2, 5));

        chatBox.getChildren().add(messageLabel);
        scrollToBottom();
    }

    private void scrollToBottom() {
        if (chatScroll == null) return;

        Platform.runLater(() -> {
            chatScroll.applyCss();
            chatScroll.layout();
            chatScroll.setVvalue(1.0);
        });
    }

    // ---------------- REAL Audio / Video Controls ----------------
    @FXML
    protected void toggleAudio() {
        if (webRTCActive && webRTCClient != null) {
            audioMuted = !audioMuted;
            webRTCClient.toggleAudio(!audioMuted);
        } else {
            // Fallback to existing audio control
            HelloApplication.toggleAudio();
            audioMuted = HelloApplication.isAudioMuted();
        }
        updateButtonStyles();
    }

    @FXML
    protected void toggleVideo() {
        System.out.println("🎬 MeetingController.toggleVideo() called");

        if (webRTCActive && webRTCClient != null) {
            if (!videoOn) {
                // Start WebRTC video
                webRTCClient.startLocalStream();
                videoOn = true;
            } else {
                // Stop WebRTC video
                webRTCClient.stopLocalStream();
                videoOn = false;
            }
        } else {
            // Use existing implementation
            if (!videoOn) {
                startRealVideoStreaming();
            } else {
                stopRealVideoStreaming();
            }
            videoOn = !videoOn;
        }

        updateButtonStyles();
    }

    public void handleWebRTCSignaling(String message) {
        String[] parts = message.split("\\|", 5);
        if (parts.length >= 5) {
            String type = parts[0];
            String targetPeer = parts[1];
            String fromPeer = parts[2];
            String sdp = parts[3];
            String sdpType = parts[4];

            if (webRTCClient != null) {
                webRTCClient.handleSignalingMessage(fromPeer, sdpType, sdp);
            }
        }
    }

    /**
     * Enhanced video streaming to multiple clients - FIXED VERSION
     */
    private void startRealVideoStreaming() {
        System.out.println("🎬 Starting REAL video streaming to multiple clients...");

        try {
            // Initialize hardware if not already done
            if (!cameraAvailable) {
                initializeHardware();
            }

            if (!cameraAvailable || webcam == null) {
                System.err.println("❌ No camera available for streaming");
                addSystemMessage("❌ Camera not available for streaming");
                return;
            }

            // Check if webcam is already open - if so, close it first
            if (webcam.isOpen()) {
                System.out.println("⚠️ Webcam is already open, closing it to change resolution...");
                webcam.close();
            }

            // Set video quality BEFORE opening the camera
            System.out.println("🎬 Setting video quality to: " + currentVideoQuality.name() +
                    " (" + currentVideoQuality.width + "x" + currentVideoQuality.height + ")");
            webcam.setViewSize(new java.awt.Dimension(currentVideoQuality.width, currentVideoQuality.height));

            // Start camera
            startCamera();

            // Enable streaming
            streamingEnabled = true;

            // Add to active streams
            String username = HelloApplication.getLoggedInUser();
            if (!activeVideoStreams.contains(username)) {
                activeVideoStreams.add(username);
                updateParticipantsList();
            }

            // Notify all participants via WebSocket
            String meetingId = HelloApplication.getActiveMeetingId();
            if (HelloApplication.isWebSocketConnected() && meetingId != null) {
                HelloApplication.sendWebSocketMessage(
                        "VIDEO_STATUS",
                        meetingId,
                        username,
                        "VIDEO_STARTED|" + currentVideoQuality.name()
                );
                System.out.println("✅ Sent VIDEO_STARTED notification to all participants");
            }

            // Show local video preview
            Platform.runLater(() -> {
                if (videoDisplay != null) {
                    Canvas canvas = new Canvas(currentVideoQuality.width, currentVideoQuality.height);
                    GraphicsContext gc = canvas.getGraphicsContext2D();
                    gc.setFill(Color.rgb(30, 40, 50));
                    gc.fillRect(0, 0, currentVideoQuality.width, currentVideoQuality.height);
                    gc.setFill(Color.WHITE);
                    gc.setFont(new javafx.scene.text.Font(16));
                    gc.fillText("📹 YOUR CAMERA", currentVideoQuality.width/2 - 50, currentVideoQuality.height/2);
                    gc.setFont(new javafx.scene.text.Font(12));
                    gc.fillText("Streaming to all participants...", currentVideoQuality.width/2 - 60, currentVideoQuality.height/2 + 20);

                    WritableImage placeholder = canvas.snapshot(null, null);
                    videoDisplay.setImage(placeholder);
                    videoDisplay.setVisible(true);
                }

                if (videoPlaceholder != null) {
                    videoPlaceholder.setVisible(false);
                }
            });

            addSystemMessage("🎥 You started video streaming to all participants");
            System.out.println("✅ REAL video streaming started to multiple clients");

        } catch (Exception e) {
            System.err.println("❌ Failed to start video streaming: " + e.getMessage());
            e.printStackTrace();
            addSystemMessage("❌ Failed to start video streaming: " + e.getMessage());

            // Try to recover by reinitializing hardware
            try {
                System.out.println("🔄 Attempting to recover camera...");
                initializeHardware();
            } catch (Exception ex) {
                System.err.println("❌ Camera recovery failed: " + ex.getMessage());
            }
        }
    }

    /**
     * Stop REAL video streaming
     */
    private void stopRealVideoStreaming() {
        System.out.println("🎬 Stopping REAL video streaming...");

        // Stop camera
        stopCamera();

        // Disable streaming
        streamingEnabled = false;

        // Remove from active streams
        String username = HelloApplication.getLoggedInUser();
        activeVideoStreams.remove(username);
        updateParticipantsList();

        // Notify all participants via WebSocket
        String meetingId = HelloApplication.getActiveMeetingId();
        if (HelloApplication.isWebSocketConnected() && meetingId != null) {
            HelloApplication.sendWebSocketMessage(
                    "VIDEO_STATUS",
                    meetingId,
                    username,
                    "VIDEO_STOPPED"
            );
            System.out.println("✅ Sent VIDEO_STOPPED notification to all participants");
        }

        // Show placeholder again
        Platform.runLater(() -> {
            if (videoDisplay != null) {
                videoDisplay.setImage(null);
                videoDisplay.setVisible(false);
            }
            if (videoPlaceholder != null) {
                videoPlaceholder.setVisible(true);
            }
        });

        addSystemMessage("🎥 You stopped video streaming");
        System.out.println("✅ REAL video streaming stopped");
    }

    /**
     * Enhanced camera capture with multi-client streaming - FIXED VERSION
     */
    private void startCamera() {
        if (!cameraAvailable || webcam == null) {
            System.err.println("❌ Cannot start camera: No camera available");
            showCameraError();
            return;
        }

        try {
            System.out.println("📷 Starting multi-client camera streaming...");

            // Ensure webcam is not already open from a previous session
            if (webcam.isOpen()) {
                System.out.println("⚠️ Webcam was already open, closing first...");
                webcam.close();
                Thread.sleep(100); // Small delay
            }

            // Open the webcam with the current resolution
            System.out.println("📷 Opening camera with resolution: " +
                    currentVideoQuality.width + "x" + currentVideoQuality.height);
            webcam.open();
            System.out.println("📷 Camera opened successfully");

            cameraRunning = true;
            cameraThread = new Thread(() -> {
                System.out.println("📷 Camera streaming thread started");
                int frameCount = 0;
                long lastFrameTime = System.currentTimeMillis();
                int frameInterval = 1000 / currentVideoQuality.fps; // ms per frame

                while (cameraRunning && webcam.isOpen()) {
                    try {
                        long currentTime = System.currentTimeMillis();
                        long timeSinceLastFrame = currentTime - lastFrameTime;

                        if (timeSinceLastFrame >= frameInterval) {
                            lastFrameTime = currentTime;

                            java.awt.image.BufferedImage awtImage = webcam.getImage();
                            if (awtImage != null) {
                                // Convert to JavaFX Image for local display
                                Image fxImage = SwingFXUtils.toFXImage(awtImage, null);

                                Platform.runLater(() -> {
                                    // Update main video display
                                    if (videoDisplay != null) {
                                        videoDisplay.setImage(fxImage);
                                    }
                                    // Update video controls preview
                                    if (videoControlsController != null) {
                                        videoControlsController.displayVideoFrame(fxImage);
                                    }
                                });

                                // 🔥 MULTI-CLIENT VIDEO STREAMING
                                if (streamingEnabled && HelloApplication.isWebSocketConnected() &&
                                        HelloApplication.getActiveMeetingId() != null) {

                                    // Convert image to compressed Base64
                                    String base64Frame = compressAndConvertImage(awtImage);
                                    if (base64Frame != null && !base64Frame.isEmpty()) {
                                        String username = HelloApplication.getLoggedInUser();
                                        String meetingId = HelloApplication.getActiveMeetingId();

                                        // Send via WebSocket to all participants
                                        HelloApplication.sendWebSocketMessage(
                                                "VIDEO_FRAME",
                                                meetingId,
                                                username,
                                                base64Frame
                                        );

                                        // Log every 15 frames
                                        if (frameCount % 15 == 0) {
                                            System.out.println("📤 Sent frame #" + frameCount +
                                                    " to all participants (" +
                                                    base64Frame.length() + " bytes)");
                                        }
                                        frameCount++;
                                    }
                                }
                            }
                        }

                        // Sleep to maintain FPS
                        Thread.sleep(10);
                    } catch (Exception e) {
                        System.err.println("❌ Camera capture error: " + e.getMessage());
                        break;
                    }
                }
                System.out.println("📷 Camera streaming thread stopped");
            });
            cameraThread.setDaemon(true);
            cameraThread.start();

            System.out.println("✅ Camera started with multi-client streaming");

        } catch (Exception e) {
            System.err.println("❌ Failed to start camera: " + e.getMessage());
            e.printStackTrace();
            showCameraError();
        }
    }

    /**
     * Enhanced image compression and conversion
     */
    private String compressAndConvertImage(java.awt.image.BufferedImage awtImage) {
        try {
            // Scale image based on quality setting
            int targetWidth = currentVideoQuality.width;
            int targetHeight = currentVideoQuality.height;

            // Create scaled image
            java.awt.Image scaledImage = awtImage.getScaledInstance(
                    targetWidth, targetHeight, java.awt.Image.SCALE_SMOOTH);

            java.awt.image.BufferedImage bufferedScaledImage = new java.awt.image.BufferedImage(
                    targetWidth, targetHeight, java.awt.image.BufferedImage.TYPE_INT_RGB);

            java.awt.Graphics2D g2d = bufferedScaledImage.createGraphics();
            g2d.drawImage(scaledImage, 0, 0, null);
            g2d.dispose();

            // Convert to JPEG with quality compression
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(bufferedScaledImage, "jpg", baos);
            byte[] imageBytes = baos.toByteArray();

            // Convert to Base64
            String base64 = java.util.Base64.getEncoder().encodeToString(imageBytes);
            return base64;

        } catch (Exception e) {
            System.err.println("❌ Error converting to base64: " + e.getMessage());
            return null;
        }
    }

    /**
     * Display video frame from other participants
     */
    public void displayVideoFrame(String username, Image videoFrame) {
        Platform.runLater(() -> {
            try {
                System.out.println("\n🎬 ========== DISPLAY VIDEO ==========");
                System.out.println("🎬 From: " + username);
                System.out.println("🎬 Frame size: " + videoFrame.getWidth() + "x" + videoFrame.getHeight());

                // Always display the video
                if (videoDisplay != null) {
                    videoDisplay.setImage(videoFrame);
                    videoDisplay.setVisible(true);
                    videoDisplay.setFitWidth(640);
                    videoDisplay.setFitHeight(480);
                    videoDisplay.setPreserveRatio(true);
                    videoDisplay.setSmooth(true);

                    System.out.println("✅ Updated video display");
                }

                // Hide placeholder
                if (videoPlaceholder != null) {
                    videoPlaceholder.setVisible(false);
                    System.out.println("✅ Hid video placeholder");
                }

                // Show overlay
                String overlayText = username.equals(HelloApplication.getLoggedInUser()) ?
                        "📹 You (Live)" : "📹 " + username + " (Live)";
                showSimpleOverlay(overlayText);

                // Mark as displaying video from this user
                isDisplayingVideo = true;

                // Update active streams if not already in list
                if (!activeVideoStreams.contains(username) && !username.equals(HelloApplication.getLoggedInUser())) {
                    activeVideoStreams.add(username);
                    updateParticipantsList();
                }

                System.out.println("🎬 ================================\n");

            } catch (Exception e) {
                System.err.println("❌ Error in displayVideoFrame: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    /**
     * Check if currently displaying video from specific user
     */
    private boolean isDisplayingVideoFromUser(String username) {
        // This is a simplified check
        // In a real implementation, you'd track which user's video is currently displayed
        return isDisplayingVideo;
    }

    /**
     * Simple overlay method
     */
    private void showSimpleOverlay(String text) {
        Platform.runLater(() -> {
            if (videoArea != null) {
                // Remove old overlays
                videoArea.getChildren().removeIf(node -> node instanceof Label);

                Label overlay = new Label(text);
                overlay.setStyle("-fx-background-color: rgba(0,0,0,0.7); " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 14px; " +
                        "-fx-padding: 5px 10px; " +
                        "-fx-background-radius: 10px;");

                StackPane.setAlignment(overlay, javafx.geometry.Pos.TOP_CENTER);
                StackPane.setMargin(overlay, new javafx.geometry.Insets(10, 0, 0, 0));

                videoArea.getChildren().add(overlay);
            }
        });
    }

    /**
     * Stop camera capture - ENHANCED VERSION
     */
    private void stopCamera() {
        System.out.println("📷 Stopping camera...");
        cameraRunning = false;
        streamingEnabled = false;

        // Stop camera thread
        if (cameraThread != null && cameraThread.isAlive()) {
            try {
                cameraThread.join(1000);
                cameraThread = null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Close webcam if open
        if (webcam != null) {
            try {
                if (webcam.isOpen()) {
                    webcam.close();
                    System.out.println("📷 Camera closed");
                }
            } catch (Exception e) {
                System.err.println("❌ Error closing camera: " + e.getMessage());
            }
        }

        Platform.runLater(() -> {
            if (videoDisplay != null) {
                videoDisplay.setImage(null);
                videoDisplay.setVisible(false);
            }
            // Show placeholder again when camera stops
            if (videoPlaceholder != null) {
                videoPlaceholder.setVisible(true);
                videoPlaceholder.setManaged(true);
            }
            // Reset video controls to simulated camera
            if (videoControlsController != null) {
                videoControlsController.resetToSimulatedCamera();
            }
            // Clear overlay
            if (videoArea != null) {
                videoArea.getChildren().removeIf(node -> node instanceof Label);
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

    public void handleWebSocketMessage(String message) {
        try {
            System.out.println("\n📨 ========== RECEIVED MESSAGE ==========");
            System.out.println("📨 Full: " + message);

            String[] parts = message.split("\\|", 4);
            if (parts.length >= 4) {
                String type = parts[0];
                String meetingId = parts[1];
                String username = parts[2];
                String content = parts[3];

                System.out.println("📨 Type: " + type);
                System.out.println("📨 Meeting: " + meetingId);
                System.out.println("📨 From: " + username);
                System.out.println("📨 Content length: " + content.length());

                // Check meeting ID
                String currentMeetingId = HelloApplication.getActiveMeetingId();
                if (!meetingId.equals(currentMeetingId)) {
                    System.out.println("⚠️ Ignoring - wrong meeting ID");
                    return;
                }

                Platform.runLater(() -> {
                    try {
                        if ("WEBRTC_SIGNAL".equals(type)) {
                            System.out.println("🎬 WebRTC signaling from: " + username);
                            handleWebRTCSignaling(content);
                        } else if ("VIDEO_FRAME".equals(type)) {
                            // ... existing code ...
                        } else if ("CHAT".equals(type)) {
                            // ... existing code ...
                        }
                    } catch (Exception e) {
                        System.err.println("❌ Error in Platform.runLater: " + e.getMessage());
                    }
                });
            }
        } catch (Exception e) {
            System.err.println("❌ Error in handleWebSocketMessage: " + e.getMessage());
        }
    }

    private void cleanupAudioVideoResources() {
        System.out.println("🧹 Cleaning up audio/video resources...");

        // Cleanup WebRTC
        if (webRTCClient != null) {
            webRTCClient.dispose();
            webRTCClient = null;
        }

        // ... existing cleanup code ...
    }

    /**
     * Handle file transfer messages
     */
    private void handleFileTransferMessage(String username, String content) {
        try {
            String[] parts = content.split("\\|", 4);
            if (parts.length >= 4) {
                String action = parts[0];
                String fileId = parts[1];
                String fileName = parts[2];
                String data = parts[3];

                switch (action) {
                    case "REQUEST":
                        // Another user wants to send us a file
                        handleFileTransferRequest(username, fileId, fileName, data);
                        break;
                    case "DATA":
                        // File data chunk
                        handleFileDataChunk(username, fileId, fileName, data);
                        break;
                    case "COMPLETE":
                        // File transfer complete
                        handleFileTransferComplete(username, fileId, fileName);
                        break;
                    case "ERROR":
                        // File transfer error
                        handleFileTransferError(username, fileId, fileName, data);
                        break;
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error handling file transfer message: " + e.getMessage());
        }
    }

    /**
     * Handle file transfer request
     */
    private void handleFileTransferRequest(String sender, String fileId, String fileName, String fileSizeStr) {
        Platform.runLater(() -> {
            try {
                long fileSize = Long.parseLong(fileSizeStr);

                // Ask user if they want to accept the file
                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("File Transfer Request");
                alert.setHeaderText("Incoming File from " + sender);
                alert.setContentText(String.format(
                        "File: %s\nSize: %s\nDo you want to accept this file?",
                        fileName, formatFileSize(fileSize)
                ));

                Optional<ButtonType> result = alert.showAndWait();
                if (result.isPresent() && result.get() == ButtonType.OK) {
                    // Accept the file
                    fileTransferHandler.acceptFileTransfer(fileId, sender, fileName, fileSize);
                    addSystemMessage("✅ Accepted file transfer from " + sender + ": " + fileName);
                } else {
                    // Reject the file
                    fileTransferHandler.rejectFileTransfer(fileId, sender);
                    addSystemMessage("❌ Rejected file transfer from " + sender + ": " + fileName);
                }
            } catch (Exception e) {
                System.err.println("❌ Error handling file transfer request: " + e.getMessage());
            }
        });
    }

    /**
     * Handle file data chunk
     */
    private void handleFileDataChunk(String sender, String fileId, String fileName, String chunkData) {
        try {
            boolean success = fileTransferHandler.receiveFileChunk(fileId, sender, chunkData);
            if (success) {
                // Show progress periodically
                if (System.currentTimeMillis() % 5000 < 100) {
                    int progress = fileTransferHandler.getTransferProgress(fileId);
                    Platform.runLater(() -> {
                        addSystemMessage("📥 Receiving " + fileName + ": " + progress + "%");
                    });
                }
            }
        } catch (Exception e) {
            System.err.println("❌ Error handling file data chunk: " + e.getMessage());
        }
    }

    /**
     * Handle file transfer completion
     */
    private void handleFileTransferComplete(String sender, String fileId, String fileName) {
        Platform.runLater(() -> {
            try {
                File receivedFile = fileTransferHandler.completeFileTransfer(fileId);
                if (receivedFile != null && receivedFile.exists()) {
                    addSystemMessage("✅ File received from " + sender + ": " + fileName);
                    // Display the file in chat
                    displayReceivedFileInChat(fileName, receivedFile);
                } else {
                    addSystemMessage("❌ Failed to receive file from " + sender + ": " + fileName);
                }
            } catch (Exception e) {
                System.err.println("❌ Error completing file transfer: " + e.getMessage());
            }
        });
    }

    /**
     * Handle file transfer error
     */
    private void handleFileTransferError(String sender, String fileId, String fileName, String error) {
        Platform.runLater(() -> {
            addSystemMessage("❌ File transfer failed from " + sender + ": " + fileName + " (" + error + ")");
            fileTransferHandler.cancelFileTransfer(fileId);
        });
    }

    /**
     * Display received file in chat
     */
    private void displayReceivedFileInChat(String fileName, File file) {
        Platform.runLater(() -> {
            if (isImage(file)) {
                displayImage(file);
            } else if (isAudio(file) || isVideo(file)) {
                displayMedia(file);
            } else {
                displayFileLink(file);
            }
            scrollToBottom();
        });
    }

    /**
     * Format file size for display
     */
    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024.0));
        return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
    }

    /**
     * Create placeholder video when real video fails
     */
    private void createPlaceholderVideo(String username) {
        Platform.runLater(() -> {
            Canvas canvas = new Canvas(640, 480);
            GraphicsContext gc = canvas.getGraphicsContext2D();
            gc.setFill(Color.rgb(40, 40, 60));
            gc.fillRect(0, 0, 640, 480);
            gc.setFill(Color.YELLOW);
            gc.fillText("🎥 " + username + "'s Video Stream", 220, 220);
            gc.fillText("Connecting...", 280, 250);

            WritableImage placeholder = canvas.snapshot(null, null);
            displayVideoFrame(username, placeholder);
        });
    }

    /**
     * Simple Base64 to Image conversion
     */
    private Image convertBase64ToImageSimple(String base64) {
        try {
            System.out.println("🔧 Converting Base64 to Image...");
            System.out.println("🔧 Base64 length: " + base64.length());

            // Decode Base64
            byte[] bytes = java.util.Base64.getDecoder().decode(base64);
            System.out.println("🔧 Decoded bytes: " + bytes.length);

            // Create Image from bytes
            java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(bytes);
            Image image = new Image(bis);

            if (image.isError()) {
                System.err.println("❌ Image has error");
                Throwable error = image.getException();
                if (error != null) {
                    System.err.println("❌ Image error: " + error.getMessage());
                }
                return null;
            }

            System.out.println("✅ Created image: " + image.getWidth() + "x" + image.getHeight());
            return image;

        } catch (Exception e) {
            System.err.println("❌ Simple conversion error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Show waiting message
     */
    private void showWaitingMessage(String text) {
        Platform.runLater(() -> {
            if (videoArea != null) {
                // Remove old messages
                videoArea.getChildren().removeIf(node ->
                        node instanceof Label && ((Label) node).getText().contains("streaming"));

                Label waitingLabel = new Label("⏳ " + text);
                waitingLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16px;");

                StackPane.setAlignment(waitingLabel, javafx.geometry.Pos.CENTER);
                videoArea.getChildren().add(waitingLabel);
            }
        });
    }

    /**
     * Clear video display
     */
    private void clearVideoDisplay() {
        Platform.runLater(() -> {
            if (videoDisplay != null) {
                videoDisplay.setImage(null);
                videoDisplay.setVisible(false);
            }
            if (videoPlaceholder != null) {
                videoPlaceholder.setVisible(true);
            }

            // Clear overlays
            if (videoArea != null) {
                videoArea.getChildren().removeIf(node -> node instanceof Label);
            }

            isDisplayingVideo = false;
        });
    }

    /**
     * Clear video from a specific user
     */
    private void clearVideoFromUser(String username) {
        Platform.runLater(() -> {
            if (!username.equals(HelloApplication.getLoggedInUser())) {
                // Clear main video display
                if (videoDisplay != null) {
                    videoDisplay.setImage(null);
                    videoDisplay.setVisible(false);
                }

                // Show placeholder again
                if (videoPlaceholder != null) {
                    videoPlaceholder.setVisible(true);
                    videoPlaceholder.setManaged(true);
                }

                // Clear waiting indicators
                if (videoArea != null) {
                    videoArea.getChildren().removeIf(node -> node instanceof Label);
                }

                System.out.println("✅ Cleared video from: " + username);
            }
        });
    }

    // Add this method to MeetingController
    public void updateVideoState(boolean videoOn) {
        this.videoOn = videoOn;
        updateButtonStyles();
    }

    /**
     * Show/hide host video indicator - ADDED METHOD
     */
    public void showHostVideoIndicator(boolean show) {
        Platform.runLater(() -> {
            if (show) {
                // Just show a temporary placeholder until video frames arrive
                addSystemMessage("🎥 Host is now sharing video - waiting for stream...");

                // The actual video will be displayed via displayVideoFrame method
                if (videoDisplay != null) {
                    videoDisplay.setVisible(true);
                }
                if (videoPlaceholder != null) {
                    videoPlaceholder.setVisible(false);
                }
            } else {
                // Clear video when host stops
                if (videoDisplay != null) {
                    videoDisplay.setImage(null);
                    videoDisplay.setVisible(false);
                }
                if (videoPlaceholder != null) {
                    videoPlaceholder.setVisible(true);
                }
                addSystemMessage("Host stopped sharing video");
            }
        });
    }

    public void setStage(Stage stage) {
        this.stage = stage;
        if (stage != null) {
            setupWindowControls();
            setupTitleBarDragging();
            setupWindowResizeHandling();
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
        System.out.println("🗔 Minimize button clicked - Direct approach");

        try {
            // Direct approach: get window from any component
            Node source = (Node) minimizeButton;
            Stage currentStage = (Stage) source.getScene().getWindow();

            if (currentStage != null) {
                System.out.println("✅ Minimizing stage directly: " + currentStage.getTitle());
                currentStage.setIconified(true);
            } else {
                System.err.println("❌ Could not get stage from button scene");
            }
        } catch (Exception e) {
            System.err.println("❌ Error minimizing window: " + e.getMessage());
            e.printStackTrace();

            // Last resort: try all possible components
            tryMinimizeAllApproaches();
        }
    }

    private void tryMinimizeAllApproaches() {
        System.out.println("🔄 Trying all minimize approaches...");

        // Try approach 1: Use stored stage
        if (stage != null) {
            stage.setIconified(true);
            System.out.println("✅ Minimized using stored stage");
            return;
        }

        // Try approach 2: Get from any available component
        Node[] components = {chatBox, videoArea, titleBar, minimizeButton};
        for (Node component : components) {
            if (component != null && component.getScene() != null) {
                Stage currentStage = (Stage) component.getScene().getWindow();
                if (currentStage != null) {
                    currentStage.setIconified(true);
                    System.out.println("✅ Minimized using component: " + component.getClass().getSimpleName());
                    return;
                }
            }
        }

        System.err.println("❌ All minimize approaches failed");
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

        if (hours > 0) {
            timerLabel.setText(String.format("Time: %02d:%02d:%02d", hours, minutes, seconds));
        } else {
            timerLabel.setText(String.format("Time: %02d:%02d", minutes, seconds));
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

        // Update record button - FIXED: Proper recording state detection
        if (recordButton != null) {
            boolean isRecordingActive = false;

            // Check advanced recording first
            if (mp4RecordingController != null) {
                isRecordingActive = mp4RecordingController.isRecording();
            }
            // Then check basic recording
            if (!isRecordingActive) {
                isRecordingActive = recording;
            }

            if (isRecordingActive) {
                recordButton.setText("Stop Recording");
                recordButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 5; -fx-font-weight: bold;");
                recordButton.setTooltip(new Tooltip("Click to stop recording"));
            } else {
                recordButton.setText("⏺ Start Recording");
                recordButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-background-radius: 5;");
                recordButton.setTooltip(new Tooltip("Click to start recording (Host only)"));
            }

            // Disable if not host
            recordButton.setDisable(!HelloApplication.isMeetingHost());
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

        // Update screen size button
        if (screenSizeButton != null) {
            if (isFullscreen) {
                screenSizeButton.setText("⛶ Fullscreen");
                screenSizeButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
            } else {
                screenSizeButton.setText("📐 " + currentScreenSize.displayName);
                screenSizeButton.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white;");
            }
        }

        // Update fullscreen toggle button
        if (toggleFullscreenButton != null) {
            if (isFullscreen) {
                toggleFullscreenButton.setText("⛶ Exit Fullscreen");
                toggleFullscreenButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
            } else {
                toggleFullscreenButton.setText("⛶ Fullscreen");
                toggleFullscreenButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
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

    // ---------------- ADVANCED MP4 RECORDING ----------------
    @FXML
    protected void onToggleRecording() {
        if (!HelloApplication.isMeetingHost()) {
            showAlert("Permission Denied", "Only the meeting host can start recordings.");
            return;
        }

        System.out.println("🎬 Recording toggle clicked - Current state:");
        System.out.println("  - Basic recording: " + recording);
        System.out.println("  - Advanced recording active: " + (mp4RecordingController != null));
        System.out.println("  - Advanced recording running: " + (mp4RecordingController != null && mp4RecordingController.isRecording()));

        // Check if advanced recording is already active and running
        if (mp4RecordingController != null && mp4RecordingController.isRecording()) {
            System.out.println("🛑 Stopping advanced recording...");
            // Stop advanced recording through the controller
            mp4RecordingController.onStopRecording();
            mp4RecordingController = null;
            updateButtonStyles();
            addSystemMessage("✅ Advanced recording stopped");
            return;
        }

        // Check if basic recording is active
        if (recording) {
            System.out.println("🛑 Stopping basic recording...");
            stopBasicRecording();
            return;
        }

        // If no recording is active, start a new one
        System.out.println("🎬 Starting new recording...");

        // Ask user which type of recording they want
        ChoiceDialog<String> dialog = new ChoiceDialog<>("Advanced MP4", "Basic Text", "Advanced MP4");
        dialog.setTitle("Recording Type");
        dialog.setHeaderText("Choose Recording Type");
        dialog.setContentText("Select recording type:");

        Optional<String> result = dialog.showAndWait();
        if (result.isPresent()) {
            if ("Advanced MP4".equals(result.get())) {
                openAdvancedRecordingControls();
            } else {
                startBasicRecording();
            }
        }
    }

    @FXML
    protected void onToggleWebRTC() {
        webRTCActive = !webRTCActive;

        if (webRTCActive) {
            // Initialize WebRTC
            initializeWebRTC();
            addSystemMessage("🌐 WebRTC enabled - Using P2P video/audio");
        } else {
            // Cleanup WebRTC
            if (webRTCClient != null) {
                webRTCClient.dispose();
                webRTCClient = null;
            }
            addSystemMessage("🌐 WebRTC disabled - Using server relay");
        }
    }

    /**
     * Opens the advanced MP4 recording controls window - UPDATED WITH DRAGGING
     */
    private void openAdvancedRecordingControls() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("recording-controls.fxml"));
            VBox recordingControls = loader.load();

            // Get the MP4 recording controller
            mp4RecordingController = loader.getController();

            Stage recordingStage = new Stage();
            recordingStage.setTitle("Advanced Meeting Recording - MP4");

            // Remove window decorations for custom title bar
            recordingStage.initStyle(StageStyle.UNDECORATED);

            recordingStage.setScene(new Scene(recordingControls, 550, 700));
            recordingStage.setResizable(false);

            // Set the meeting stage as owner
            if (stage != null) {
                recordingStage.initOwner(stage);
            }

            // PASS THE STAGE TO THE CONTROLLER - THIS IS CRITICAL!
            mp4RecordingController.setStage(recordingStage);

            // ENABLE DRAGGING FOR THE POPUP
            enablePopupDragging(recordingControls, recordingStage);

            // Close handler to cleanup resources
            recordingStage.setOnCloseRequest(e -> {
                System.out.println("📹 Recording window closed");
                if (mp4RecordingController != null && mp4RecordingController.isRecording()) {
                    mp4RecordingController.onStopRecording();
                }
                mp4RecordingController = null;
                updateButtonStyles(); // Update main UI
            });

            // Center the window on screen
            recordingStage.centerOnScreen();
            recordingStage.show();

            // Update main UI button
            updateButtonStyles();

        } catch (IOException e) {
            e.printStackTrace();
            // Fallback to basic recording
            showAlert("Advanced Recording", "Advanced recording features not available. Using basic recording.");
            startBasicRecording();
        }
    }

    /**
     * Fallback to basic text recording
     */
    private void toggleBasicRecording() {
        if (!recording) {
            startBasicRecording();
        } else {
            stopBasicRecording();
        }
    }

    private void startBasicRecording() {
        try {
            String fileName = "Meeting_" + HelloApplication.getActiveMeetingId() + "_" +
                    new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(new Date()) + ".txt";
            File dir = new File("recordings");
            if (!dir.exists()) {
                boolean created = dir.mkdirs();
                System.out.println("📁 Recordings directory created: " + created);
            }

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
            addSystemMessage("Basic recording started...");
            System.out.println("✅ Basic recording started: " + currentRecordingFile.getAbsolutePath());

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Recording Error", "Failed to start recording: " + e.getMessage());
        }
    }

    private void stopBasicRecording() {
        try {
            if (currentRecordingFile != null && currentRecordingFile.exists()) {
                FileWriter writer = new FileWriter(currentRecordingFile, true);
                writer.write("--- Recording End ---\n");
                writer.write("Recording stopped at: " + new Date() + "\n");
                writer.write("Meeting duration: " + formatMeetingTime() + "\n");
                writer.close();

                recording = false;
                updateButtonStyles();
                addSystemMessage("✅ Basic recording saved: " + currentRecordingFile.getName());
                System.out.println("✅ Basic recording stopped: " + currentRecordingFile.getAbsolutePath());

                // Reset the file reference
                currentRecordingFile = null;
            } else {
                System.err.println("❌ No active recording file found to stop");
                recording = false;
                updateButtonStyles();
            }

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Recording Error", "Failed to stop recording: " + e.getMessage());
            recording = false;
            updateButtonStyles();
        }
    }

    private String formatMeetingTime() {
        int hours = meetingSeconds / 3600;
        int minutes = (meetingSeconds % 3600) / 60;
        int seconds = meetingSeconds % 60;

        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        } else {
            return String.format("%02d:%02d", minutes, seconds);
        }
    }

    // ---------------- ENHANCED FILE TRANSFER ----------------
    @FXML
    protected void onSendFile() {
        if (stage == null) stage = (Stage) chatBox.getScene().getWindow();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Send");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Files", "*.*"),
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif"),
                new FileChooser.ExtensionFilter("Audio", "*.mp3", "*.wav"),
                new FileChooser.ExtensionFilter("Video", "*.mp4", "*.mov", "*.avi"),
                new FileChooser.ExtensionFilter("Documents", "*.pdf", "*.doc", "*.docx", "*.txt")
        );

        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            try {
                // Check file size (limit to 100MB for now)
                long fileSize = file.length();
                long maxSize = 100 * 1024 * 1024; // 100MB

                if (fileSize > maxSize) {
                    showAlert("File Too Large", "File size exceeds 100MB limit. Please select a smaller file.");
                    return;
                }

                String username = HelloApplication.getLoggedInUser();
                String meetingId = HelloApplication.getActiveMeetingId();

                if (meetingId == null) {
                    showAlert("Error", "No active meeting found.");
                    return;
                }

                // Start file transfer
                boolean transferStarted = fileTransferHandler.startFileTransfer(file, meetingId, username);

                if (transferStarted) {
                    addSystemMessage("📤 Started sending file: " + file.getName() + " (" + formatFileSize(fileSize) + ")");
                } else {
                    showAlert("Transfer Error", "Failed to start file transfer.");
                }

            } catch (Exception ex) {
                ex.printStackTrace();
                showAlert("Error", "Failed to share file: " + ex.getMessage());
            }
        }
    }

    @FXML
    protected void onClearChat() {
        if (chatBox != null) {
            Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
            confirmation.setTitle("Clear Chat");
            confirmation.setHeaderText("Clear Chat History");
            confirmation.setContentText("Are you sure you want to clear all chat messages? This action cannot be undone.");

            Optional<ButtonType> result = confirmation.showAndWait();
            if (result.isPresent() && result.get() == ButtonType.OK) {
                chatBox.getChildren().clear();
                addSystemMessage("Chat history cleared");

                // Clear chat from database
                String meetingId = HelloApplication.getActiveMeetingId();
                if (meetingId != null) {
                    boolean cleared = Database.clearChatMessages(meetingId);
                    if (cleared) {
                        System.out.println("✅ Chat cleared from database");
                    } else {
                        System.err.println("❌ Failed to clear chat from database");
                    }
                }
            }
        }
    }

    private void displayImage(File file) {
        try {
            ImageView imgView = new ImageView(new Image(file.toURI().toString()));
            imgView.setFitWidth(200);
            imgView.setPreserveRatio(true);
            imgView.setStyle("-fx-border-color: #bdc3c7; -fx-border-radius: 5;");
            imgView.setOnMouseClicked(e -> downloadFile(file));

            VBox imageContainer = new VBox(5);
            imageContainer.getChildren().addAll(
                    new Label("🖼 " + file.getName() + " (" + formatFileSize(file.length()) + ")"),
                    imgView
            );
            if (chatBox != null) {
                chatBox.getChildren().add(imageContainer);
            }
        } catch (Exception e) {
            System.err.println("❌ Error displaying image: " + e.getMessage());
            displayFileLink(file);
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

            // Add controls
            HBox controls = new HBox(5);
            controls.setAlignment(Pos.CENTER);

            Button playButton = new Button("▶");
            playButton.setOnAction(e -> player.play());

            Button pauseButton = new Button("⏸");
            pauseButton.setOnAction(e -> player.pause());

            Button stopButton = new Button("⏹");
            stopButton.setOnAction(e -> player.stop());

            controls.getChildren().addAll(playButton, pauseButton, stopButton);

            // Stop previous media player
            if (currentMediaPlayer != null) {
                currentMediaPlayer.stop();
            }
            currentMediaPlayer = player;

            VBox mediaContainer = new VBox(5);
            mediaContainer.getChildren().addAll(
                    new Label(isVideo(file) ? "🎥 " + file.getName() : "🎵 " + file.getName()),
                    mediaView,
                    controls
            );
            if (chatBox != null) {
                chatBox.getChildren().add(mediaContainer);
            }

        } catch (Exception e) {
            System.err.println("❌ Error displaying media: " + e.getMessage());
            displayFileLink(file);
        }
    }

    private void displayFileLink(File file) {
        Hyperlink fileLink = new Hyperlink("📎 " + file.getName() + " (" + formatFileSize(file.length()) + ")");
        fileLink.setStyle("-fx-text-fill: #3498db; -fx-border-color: transparent;");
        fileLink.setOnAction(e -> downloadFile(file));
        if (chatBox != null) {
            chatBox.getChildren().add(fileLink);
        }
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
        System.out.println("🚪 Leaving meeting...");

        // Stop media player if playing
        if (currentMediaPlayer != null) {
            currentMediaPlayer.stop();
            currentMediaPlayer = null;
        }

        // Stop basic recording if active
        if (recording) {
            System.out.println("🛑 Stopping basic recording before leaving...");
            stopBasicRecording();
        }

        // Stop advanced recording if active
        if (mp4RecordingController != null && mp4RecordingController.isRecording()) {
            System.out.println("🛑 Stopping advanced recording before leaving...");
            mp4RecordingController.onStopRecording();
            mp4RecordingController = null;
        }

        // Stop meeting timer
        if (meetingTimer != null) {
            meetingTimer.stop();
            meetingTimer = null;
        }

        // Cleanup audio and video resources
        cleanupAudioVideoResources();

        // Cleanup file transfer handler
        if (fileTransferHandler != null) {
            fileTransferHandler.cleanup();
        }

        // Notify controllers about meeting end
        if (audioControlsController != null) {
            audioControlsController.onMeetingStateChanged(false);
        }
        if (videoControlsController != null) {
            videoControlsController.onMeetingStateChanged(false);
        }

        // Notify all participants that we're leaving
        String username = HelloApplication.getLoggedInUser();
        String meetingId = HelloApplication.getActiveMeetingId();

        if (HelloApplication.isWebSocketConnected() && meetingId != null) {
            HelloApplication.sendWebSocketMessage(
                    "USER_LEFT",
                    meetingId,
                    username,
                    "left the meeting"
            );
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

    /**
     * Getter for audio controls controller
     */
    public AudioControlsController getAudioControlsController() {
        return audioControlsController;
    }

    /**
     * Connect to a specific server URL with retry logic
     * This method is called from MeetingInfo.connectToServer()
     */
    public static boolean connectToServer(String serverUrl) {
        try {
            System.out.println("🔗 Attempting to connect to: " + serverUrl);

            HelloApplication.MeetingInfo.initializeWebSocketConnection(serverUrl);

            // Wait for connection to establish (with timeout)
            int attempts = 0;
            while (attempts < 10 && !HelloApplication.isWebSocketConnected()) {
                Thread.sleep(500);
                attempts++;
            }

            if (HelloApplication.isWebSocketConnected()) {
                System.out.println("✅ Successfully connected to: " + serverUrl);
                return true;
            } else {
                System.out.println("❌ Failed to connect to: " + serverUrl);
                return false;
            }

        } catch (Exception e) {
            System.err.println("❌ Connection error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Getter for video controls controller
     */
    public VideoControlsController getVideoControlsController() {
        return videoControlsController;
    }

    /**
     * Singleton instance getter
     */
    public static MeetingController getInstance() {
        return instance;
    }

    /**
     * Set video quality
     */
    public void setVideoQuality(VideoQuality quality) {
        this.currentVideoQuality = quality;
        if (webcam != null && webcam.isOpen()) {
            try {
                webcam.setViewSize(new java.awt.Dimension(quality.width, quality.height));
                System.out.println("✅ Video quality changed to: " + quality.name());
            } catch (Exception e) {
                System.err.println("❌ Failed to change video quality: " + e.getMessage());
            }
        }
    }

    /**
     * Get current video quality
     */
    public VideoQuality getCurrentVideoQuality() {
        return currentVideoQuality;
    }

    // ---------------- Helpers for file type checking ----------------
    private boolean isImage(File f) {
        String n = f.getName().toLowerCase();
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".gif") || n.endsWith(".bmp");
    }

    private boolean isAudio(File f) {
        String n = f.getName().toLowerCase();
        return n.endsWith(".mp3") || n.endsWith(".wav") || n.endsWith(".m4a") || n.endsWith(".aac") || n.endsWith(".flac");
    }

    private boolean isVideo(File f) {
        String n = f.getName().toLowerCase();
        return n.endsWith(".mp4") || n.endsWith(".mov") || n.endsWith(".avi") || n.endsWith(".m4v") || n.endsWith(".mkv");
    }
}