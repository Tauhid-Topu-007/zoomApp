package org.example.zoom;

import javafx.fxml.FXML;
import javafx.event.ActionEvent;
import javafx.scene.Node;
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

    // Singleton instance
    private static MeetingController instance;

    // Participant tracking
    private List<String> currentParticipants = new ArrayList<>();

    @FXML
    public void initialize() {
        instance = this;

        System.out.println("🎬 MeetingController INITIALIZING...");
        System.out.println("🎬 User: " + HelloApplication.getLoggedInUser());
        System.out.println("🎬 Meeting ID: " + HelloApplication.getActiveMeetingId());

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

                // Gradient background
                gc.setFill(Color.rgb(20, 20, 40));
                gc.fillRect(0, 0, 640, 480);

                // Border
                gc.setStroke(Color.rgb(100, 100, 150));
                gc.setLineWidth(2);
                gc.strokeRect(10, 10, 620, 460);

                // Text
                gc.setFill(Color.WHITE);
                gc.setFont(new javafx.scene.text.Font(20));
                gc.fillText("VIDEO STREAM", 240, 200);

                gc.setFill(Color.LIGHTGRAY);
                gc.setFont(new javafx.scene.text.Font(14));
                gc.fillText("Click 'Start Video' to begin streaming", 200, 240);
                gc.fillText("or wait for host to start streaming", 220, 260);

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
     * Initialize audio and video controllers
     */
    private void initializeAudioVideoControllers() {
        System.out.println("🔊🎥 Initializing audio/video controllers...");

        try {
            // Initialize audio controls
            if (audioControlsContainer != null) {
                FXMLLoader audioLoader = new FXMLLoader(getClass().getResource("audio-controls.fxml"));
                audioLoader.load();
                audioControlsController = audioLoader.getController();
                audioControlsController.setMeetingController(this);
                System.out.println("✅ Audio controls controller initialized");
            }

            // Initialize video controls
            if (videoControlsContainer != null) {
                FXMLLoader videoLoader = new FXMLLoader(getClass().getResource("video-controls.fxml"));
                videoLoader.load();
                videoControlsController = videoLoader.getController();
                videoControlsController.setMeetingController(this);
                System.out.println("✅ Video controls controller initialized");
            }

            // Initialize hardware after controllers are loaded
            initializeHardware();

            // Update UI state based on current audio/video status
            updateButtonStyles();

        } catch (IOException e) {
            System.err.println("❌ Failed to load audio/video controllers: " + e.getMessage());
            e.printStackTrace();

            // Setup fallback controls if FXML loading fails
            setupFallbackControls();
        }
    }

    /**
     * NEW: Initialize participant tracking from database
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
     * FIXED: Safely get stage from any available component
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
     * Setup video placeholder with visible content
     */
    private void setupVideoPlaceholder() {
        if (videoPlaceholder != null) {
            // Create a visible placeholder
            Canvas canvas = new Canvas(640, 480);
            GraphicsContext gc = canvas.getGraphicsContext2D();
            gc.setFill(Color.rgb(30, 30, 40));
            gc.fillRect(0, 0, 640, 480);
            gc.setFill(Color.WHITE);
            gc.fillText("VIDEO AREA", 280, 240);
            gc.fillText("Click 'Start Video' to begin", 250, 260);

            WritableImage placeholderImage = canvas.snapshot(null, null);

            if (videoDisplay != null) {
                videoDisplay.setImage(placeholderImage);
                videoDisplay.setVisible(true);
            }
        }
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
                        setStyle("-fx-padding: 10px; -fx-font-size: 13px; -fx-border-color: #ecf0f1; -fx-border-width: 0 0 1 0;");
                        setPrefHeight(40);
                    }
                }
            });
        }
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
     * FIXED: Enhanced participant list update with real-time tracking
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
                if (participant.equals(hostUsername) && HelloApplication.isMeetingHost()) {
                    participantsList.getItems().add("👑 " + participant + " (Host)");
                } else {
                    participantsList.getItems().add("👤 " + participant);
                }
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
     * FIXED: Add participant with proper tracking
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
     * FIXED: Remove participant with proper tracking
     */
    public void removeParticipant(String username) {
        if (currentParticipants.remove(username)) {
            String meetingId = HelloApplication.getActiveMeetingId();
            if (meetingId != null) {
                // Remove from database
                Database.removeParticipant(meetingId, username);
            }

            Platform.runLater(() -> {
                updateParticipantsList();
                addSystemMessage(username + " left the meeting");
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
        if (meetingId == null) return;

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
    protected void onTestRealVideo() {
        System.out.println("🎬 Testing REAL video streaming...");

        // Create a test video frame
        Canvas canvas = new Canvas(320, 240);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Animated background
        double time = System.currentTimeMillis() % 10000 / 10000.0;
        gc.setFill(Color.hsb(time * 360, 0.8, 0.9));
        gc.fillRect(0, 0, 320, 240);

        gc.setFill(Color.WHITE);
        gc.fillText("🎥 TEST VIDEO STREAM", 80, 100);
        gc.fillText("From: " + HelloApplication.getLoggedInUser(), 80, 130);
        gc.fillText("Time: " + new java.util.Date(), 60, 160);
        gc.fillText("Meeting: " + HelloApplication.getActiveMeetingId(), 60, 190);

        // Animated circle
        double x = 160 + Math.sin(time * Math.PI * 2) * 100;
        double y = 120 + Math.cos(time * Math.PI * 2) * 60;
        gc.setFill(Color.RED);
        gc.fillOval(x, y, 40, 40);

        WritableImage testImage = canvas.snapshot(null, null);

        // Display locally
        displayVideoFrame(HelloApplication.getLoggedInUser(), testImage);

        // Convert to Base64 and send
        try {
            java.awt.image.BufferedImage awtImage = SwingFXUtils.fromFXImage(testImage, null);
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(awtImage, "png", baos);
            String base64 = java.util.Base64.getEncoder().encodeToString(baos.toByteArray());

            // Send test video frame
            if (HelloApplication.isWebSocketConnected() && HelloApplication.getActiveMeetingId() != null) {
                HelloApplication.sendVideoFrame(
                        HelloApplication.getActiveMeetingId(),
                        HelloApplication.getLoggedInUser(),
                        base64
                );
                addSystemMessage("✅ Test video stream sent!");
            }
        } catch (Exception e) {
            System.err.println("❌ Error sending test video: " + e.getMessage());
        }
    }

    @FXML
    protected void onTestVideoButton() {
        System.out.println("🎬 TEST VIDEO BUTTON CLICKED");

        // Create a simple test image
        Canvas canvas = new Canvas(320, 240);
        GraphicsContext gc = canvas.getGraphicsContext2D();

        // Create different colors based on user
        String username = HelloApplication.getLoggedInUser();
        Color bgColor = username.contains("host") ? Color.RED : Color.BLUE;

        gc.setFill(bgColor);
        gc.fillRect(0, 0, 320, 240);
        gc.setFill(Color.WHITE);
        gc.fillText("TEST VIDEO", 120, 100);
        gc.fillText("From: " + username, 100, 130);
        gc.fillText("Time: " + new java.util.Date(), 80, 160);
        gc.fillText("Meeting: " + HelloApplication.getActiveMeetingId(), 80, 190);

        WritableImage testImage = canvas.snapshot(null, null);

        // Display locally
        displayVideoFrame(username, testImage);

        // Send to others via WebSocket
        if (HelloApplication.isWebSocketConnected() && HelloApplication.getActiveMeetingId() != null) {
            try {
                // Convert to Base64
                java.awt.image.BufferedImage buffered = SwingFXUtils.fromFXImage(testImage, null);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                ImageIO.write(buffered, "png", baos);
                String base64 = java.util.Base64.getEncoder().encodeToString(baos.toByteArray());

                HelloApplication.sendWebSocketMessage(
                        "VIDEO_FRAME",
                        HelloApplication.getActiveMeetingId(),
                        username,
                        base64
                );

                addSystemMessage("✅ Test video sent to everyone!");
                System.out.println("✅ Test video sent (" + base64.length() + " chars)");

            } catch (Exception e) {
                addSystemMessage("❌ Failed to send test video: " + e.getMessage());
                System.err.println("❌ Error: " + e.getMessage());
            }
        } else {
            addSystemMessage("❌ Not connected to WebSocket!");
            System.err.println("❌ WebSocket: " + HelloApplication.isWebSocketConnected());
            System.err.println("❌ Meeting ID: " + HelloApplication.getActiveMeetingId());
        }
    }

    @FXML
    protected void onDebugVideo() {
        System.out.println("🐛 DEBUG: Manual video test");

        // Create test image
        Canvas canvas = new Canvas(320, 240);
        GraphicsContext gc = canvas.getGraphicsContext2D();
        gc.setFill(Color.PURPLE);
        gc.fillRect(0, 0, 320, 240);
        gc.setFill(Color.WHITE);
        gc.fillText("DEBUG VIDEO", 120, 120);
        gc.fillText(new java.util.Date().toString(), 80, 140);

        WritableImage testImage = canvas.snapshot(null, null);

        // Display locally
        displayVideoFrame("DEBUG", testImage);

        // Send to others
        if (HelloApplication.isWebSocketConnected() && HelloApplication.getActiveMeetingId() != null) {
            // Convert to Base64
            try {
                java.awt.image.BufferedImage buffered = SwingFXUtils.fromFXImage(testImage, null);
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                javax.imageio.ImageIO.write(buffered, "png", baos);
                String base64 = java.util.Base64.getEncoder().encodeToString(baos.toByteArray());

                HelloApplication.sendWebSocketMessage(
                        "VIDEO_FRAME",
                        HelloApplication.getActiveMeetingId(),
                        HelloApplication.getLoggedInUser(),
                        base64
                );

                addSystemMessage("✅ Debug video sent!");

            } catch (Exception e) {
                addSystemMessage("❌ Debug failed: " + e.getMessage());
            }
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
     * FIXED: Changed from private to public to allow access from MP4RecordingController
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
        // Use centralized audio control
        HelloApplication.toggleAudio();

        // Update local state
        audioMuted = HelloApplication.isAudioMuted();
        updateButtonStyles();
    }

    @FXML
    protected void toggleVideo() {
        System.out.println("🎬 MeetingController.toggleVideo() called");

        if (!videoOn) {
            // START VIDEO
            startRealVideoStreaming();
        } else {
            // STOP VIDEO
            stopRealVideoStreaming();
        }

        // Update local state
        videoOn = HelloApplication.isVideoOn();
        updateButtonStyles();
    }


    /**
     * Convert AWT BufferedImage to Base64 string for streaming
     */
    private String convertImageToBase64(java.awt.image.BufferedImage awtImage) {
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(awtImage, "jpg", baos);
            byte[] imageBytes = baos.toByteArray();
            return java.util.Base64.getEncoder().encodeToString(imageBytes);
        } catch (Exception e) {
            System.err.println("❌ Error converting image to base64: " + e.getMessage());
            return null;
        }
    }

    /**
     * Start REAL video streaming with camera
     */
    private void startRealVideoStreaming() {
        System.out.println("🎬 Starting REAL video streaming...");

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

            // Start camera
            startCamera();

            // Notify everyone via WebSocket
            String meetingId = HelloApplication.getActiveMeetingId();
            String username = HelloApplication.getLoggedInUser();

            if (HelloApplication.isWebSocketConnected() && meetingId != null) {
                HelloApplication.sendWebSocketMessage(
                        "VIDEO_STATUS",
                        meetingId,
                        username,
                        "VIDEO_STARTED"
                );
                System.out.println("✅ Sent VIDEO_STARTED notification");
            }

            // Show local video
            Platform.runLater(() -> {
                if (videoDisplay != null) {
                    Canvas canvas = new Canvas(640, 480);
                    GraphicsContext gc = canvas.getGraphicsContext2D();
                    gc.setFill(Color.BLUE);
                    gc.fillRect(0, 0, 640, 480);
                    gc.setFill(Color.WHITE);
                    gc.fillText("📹 YOUR CAMERA", 240, 240);
                    gc.fillText("Streaming to meeting...", 220, 260);

                    WritableImage placeholder = canvas.snapshot(null, null);
                    videoDisplay.setImage(placeholder);
                    videoDisplay.setVisible(true);
                }

                if (videoPlaceholder != null) {
                    videoPlaceholder.setVisible(false);
                }
            });

            addSystemMessage("🎥 You started video streaming to meeting");
            System.out.println("✅ REAL video streaming started");

        } catch (Exception e) {
            System.err.println("❌ Failed to start video streaming: " + e.getMessage());
            e.printStackTrace();
            addSystemMessage("❌ Failed to start video streaming");
        }
    }

    /**
     * Stop REAL video streaming
     */
    private void stopRealVideoStreaming() {
        System.out.println("🎬 Stopping REAL video streaming...");

        // Stop camera
        stopCamera();

        // Notify everyone via WebSocket
        String meetingId = HelloApplication.getActiveMeetingId();
        String username = HelloApplication.getLoggedInUser();

        if (HelloApplication.isWebSocketConnected() && meetingId != null) {
            HelloApplication.sendWebSocketMessage(
                    "VIDEO_STATUS",
                    meetingId,
                    username,
                    "VIDEO_STOPPED"
            );
            System.out.println("✅ Sent VIDEO_STOPPED notification");
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
     * Start camera capture with ACTUAL video streaming
     */
    private void startCamera() {
        if (!cameraAvailable || webcam == null) {
            System.err.println("❌ Cannot start camera: No camera available");
            showCameraError();
            return;
        }

        try {
            System.out.println("📷 Starting ACTUAL camera streaming...");

            if (!webcam.isOpen()) {
                webcam.open();
                System.out.println("📷 Camera opened successfully");
                webcam.setViewSize(new java.awt.Dimension(640, 480));
            }

            cameraRunning = true;
            cameraThread = new Thread(() -> {
                System.out.println("📷 Camera streaming thread started");
                int frameCount = 0;

                while (cameraRunning && webcam.isOpen()) {
                    try {
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
                                    videoControlsController.updateVideoPreview(fxImage);
                                }
                            });

                            // 🔥 ACTUAL VIDEO STREAMING: Send frame to all participants
                            if (HelloApplication.isWebSocketConnected() &&
                                    HelloApplication.getActiveMeetingId() != null &&
                                    HelloApplication.isVideoOn()) {

                                // Convert image to Base64 for streaming
                                String base64Frame = convertAwtImageToBase64Simple(awtImage);
                                if (base64Frame != null && !base64Frame.isEmpty()) {
                                    String username = HelloApplication.getLoggedInUser();
                                    String meetingId = HelloApplication.getActiveMeetingId();

                                    // Send via WebSocket
                                    HelloApplication.sendVideoFrame(meetingId, username, base64Frame);

                                    // Log every 10 frames
                                    if (frameCount % 10 == 0) {
                                        System.out.println("📤 Sent frame #" + frameCount +
                                                " (" + base64Frame.length() + " chars)");
                                    }
                                    frameCount++;
                                }
                            }
                        }
                        Thread.sleep(100); // ~10 FPS for streaming
                    } catch (Exception e) {
                        System.err.println("❌ Camera capture error: " + e.getMessage());
                        break;
                    }
                }
                System.out.println("📷 Camera streaming thread stopped");
            });
            cameraThread.setDaemon(true);
            cameraThread.start();

            System.out.println("✅ Camera started with ACTUAL streaming");

        } catch (Exception e) {
            System.err.println("❌ Failed to start camera: " + e.getMessage());
            e.printStackTrace();
            showCameraError();
        }
    }

    /**
     * SIMPLE method to convert AWT BufferedImage to Base64
     */
    private String convertAwtImageToBase64Simple(java.awt.image.BufferedImage awtImage) {
        try {
            // Create a smaller image for streaming (320x240)
            int newWidth = 320;
            int newHeight = 240;

            java.awt.Image scaledImage = awtImage.getScaledInstance(newWidth, newHeight, java.awt.Image.SCALE_SMOOTH);
            java.awt.image.BufferedImage bufferedScaledImage = new java.awt.image.BufferedImage(
                    newWidth, newHeight, java.awt.image.BufferedImage.TYPE_INT_RGB);

            java.awt.Graphics2D g2d = bufferedScaledImage.createGraphics();
            g2d.drawImage(scaledImage, 0, 0, null);
            g2d.dispose();

            // Convert to JPEG with compression
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
     * SIMPLE method to convert AWT BufferedImage to Base64
     */
    private String convertAwtImageToBase64(java.awt.image.BufferedImage awtImage) {
        try {
            // Create a smaller image to reduce bandwidth
            int newWidth = 320; // Reduced size
            int newHeight = 240;

            java.awt.Image scaledImage = awtImage.getScaledInstance(newWidth, newHeight, java.awt.Image.SCALE_SMOOTH);
            java.awt.image.BufferedImage bufferedScaledImage = new java.awt.image.BufferedImage(
                    newWidth, newHeight, java.awt.image.BufferedImage.TYPE_INT_RGB);

            java.awt.Graphics2D g2d = bufferedScaledImage.createGraphics();
            g2d.drawImage(scaledImage, 0, 0, null);
            g2d.dispose();

            // Convert to JPG
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(bufferedScaledImage, "jpg", baos);
            byte[] imageBytes = baos.toByteArray();

            // Convert to Base64
            String base64 = java.util.Base64.getEncoder().encodeToString(imageBytes);
            return base64;

        } catch (Exception e) {
            System.err.println("❌ Error converting AWT image to base64: " + e.getMessage());
            return null;
        }
    }

    /**
     * WORKING: Display video frame from other participants
     */
    public void displayVideoFrame(String username, Image videoFrame) {
        Platform.runLater(() -> {
            try {
                System.out.println("\n🎬 ========== DISPLAY VIDEO ==========");
                System.out.println("🎬 From: " + username);
                System.out.println("🎬 Our user: " + HelloApplication.getLoggedInUser());
                System.out.println("🎬 Is remote: " + !username.equals(HelloApplication.getLoggedInUser()));

                if (videoFrame == null) {
                    System.err.println("❌ Video frame is NULL");
                    return;
                }

                System.out.println("🎬 Frame size: " + videoFrame.getWidth() + "x" + videoFrame.getHeight());

                // Always display the video, even if it's our own
                if (videoDisplay != null) {
                    videoDisplay.setImage(videoFrame);
                    videoDisplay.setVisible(true);
                    videoDisplay.setFitWidth(640);
                    videoDisplay.setFitHeight(480);
                    videoDisplay.setPreserveRatio(true);

                    System.out.println("✅ Updated video display");
                }

                // Hide placeholder
                if (videoPlaceholder != null) {
                    videoPlaceholder.setVisible(false);
                    System.out.println("✅ Hid video placeholder");
                }

                // Show overlay
                String overlayText = username.equals(HelloApplication.getLoggedInUser()) ?
                        "📹 You (Broadcasting)" : "📹 " + username;
                showSimpleOverlay(overlayText);

                System.out.println("🎬 ================================\n");

            } catch (Exception e) {
                System.err.println("❌ Error in displayVideoFrame: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }


    /**
     * Simple overlay method
     */
    private void showSimpleOverlay(String text) {
        Platform.runLater(() -> {
            if (videoArea != null) {
                // Remove old overlays
                videoArea.getChildren().removeIf(node -> node instanceof Label);

                Label overlay = new Label("▶ " + text);
                overlay.setStyle("-fx-background-color: rgba(0,0,0,0.7); " +
                        "-fx-text-fill: white; " +
                        "-fx-font-size: 14px; " +
                        "-fx-padding: 5px 10px;");

                StackPane.setAlignment(overlay, javafx.geometry.Pos.TOP_CENTER);
                StackPane.setMargin(overlay, new javafx.geometry.Insets(10, 0, 0, 0));

                videoArea.getChildren().add(overlay);
            }
        });
    }


    private int frameCount = 0;

    private void showVideoOverlay(String text) {
        if (videoArea != null) {
            // Clear previous overlays
            videoArea.getChildren().removeIf(node -> node instanceof Label);

            // Create overlay label
            Label overlay = new Label(text);
            overlay.setStyle("-fx-background-color: rgba(0, 0, 0, 0.7); " +
                    "-fx-text-fill: white; " +
                    "-fx-font-size: 14px; " +
                    "-fx-padding: 5px 10px; " +
                    "-fx-background-radius: 5px;");
            overlay.setAlignment(javafx.geometry.Pos.CENTER);

            // Position at bottom of video area
            StackPane.setAlignment(overlay, javafx.geometry.Pos.BOTTOM_CENTER);
            StackPane.setMargin(overlay, new javafx.geometry.Insets(0, 0, 10, 0));

            videoArea.getChildren().add(overlay);

            // Remove overlay after 3 seconds
            PauseTransition pause = new PauseTransition(javafx.util.Duration.seconds(3));
            pause.setOnFinished(e -> videoArea.getChildren().remove(overlay));
            pause.play();
        }
    }

    /**
     * Show participant video in a grid (for host view)
     */
    private void showParticipantVideo(String username, Image videoFrame) {
        // This would show participant videos in a grid layout
        // For now, just show in a small preview
        if (videoControlsController != null) {
            videoControlsController.displayVideoFrame(videoFrame);
        }

        addSystemMessage("🎥 " + username + " is sharing video");
    }



    /**
     * Compress Base64 image string (simple downscaling)
     */
    private String compressBase64Image(String base64Image) {
        try {
            // Decode base64
            byte[] imageBytes = java.util.Base64.getDecoder().decode(base64Image);
            java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(imageBytes);
            java.awt.image.BufferedImage originalImage = javax.imageio.ImageIO.read(bis);

            if (originalImage == null) return base64Image;

            // Downscale to reduce size
            int newWidth = originalImage.getWidth() / 2;
            int newHeight = originalImage.getHeight() / 2;

            java.awt.Image scaledImage = originalImage.getScaledInstance(newWidth, newHeight, java.awt.Image.SCALE_SMOOTH);
            java.awt.image.BufferedImage bufferedScaledImage = new java.awt.image.BufferedImage(
                    newWidth, newHeight, java.awt.image.BufferedImage.TYPE_INT_RGB);

            java.awt.Graphics2D g2d = bufferedScaledImage.createGraphics();
            g2d.drawImage(scaledImage, 0, 0, null);
            g2d.dispose();

            // Re-encode to base64
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(bufferedScaledImage, "jpg", baos);
            byte[] compressedBytes = baos.toByteArray();

            return java.util.Base64.getEncoder().encodeToString(compressedBytes);

        } catch (Exception e) {
            System.err.println("❌ Error compressing image: " + e.getMessage());
            return base64Image; // Return original if compression fails
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
            return javafx.embed.swing.SwingFXUtils.toFXImage(awtImage, null);
        } catch (Exception e) {
            System.err.println("❌ Failed to convert image: " + e.getMessage());
            return null;
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
                    System.out.println("⚠️ Current: " + currentMeetingId + ", Received: " + meetingId);
                    return;
                }

                Platform.runLater(() -> {
                    try {
                        if ("VIDEO_FRAME".equals(type)) {
                            System.out.println("🎬 Processing VIDEO_FRAME from: " + username);

                            // Convert Base64 to Image
                            Image videoFrame = convertBase64ToImageSimple(content);
                            if (videoFrame != null) {
                                // Display the video frame
                                displayVideoFrame(username, videoFrame);
                                System.out.println("✅ Video frame displayed from: " + username);
                            } else {
                                System.err.println("❌ Failed to convert base64 to image");
                                // Create a placeholder
                                createPlaceholderVideo(username);
                            }

                        } else if ("VIDEO_STATUS".equals(type)) {
                            System.out.println("🎬 Video status update from " + username + ": " + content);

                            if ("VIDEO_STARTED".equals(content)) {
                                addSystemMessage("🎬 " + username + " started video streaming");
                                showWaitingMessage("⏳ " + username + " is streaming...");
                            } else if ("VIDEO_STOPPED".equals(content)) {
                                addSystemMessage(username + " stopped streaming");
                                if (!username.equals(HelloApplication.getLoggedInUser())) {
                                    clearVideoDisplay();
                                }
                            }

                        } else if ("CHAT".equals(type)) {
                            addUserMessage(username + ": " + content);
                        }

                    } catch (Exception e) {
                        System.err.println("❌ Error in Platform.runLater: " + e.getMessage());
                        e.printStackTrace();
                    }
                });

            } else {
                System.err.println("❌ Invalid message format");
            }

            System.out.println("📨 ====================================\n");

        } catch (Exception e) {
            System.err.println("❌ Error in handleWebSocketMessage: " + e.getMessage());
            e.printStackTrace();
        }
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
            System.out.println("🔧 First 50 chars: " + (base64.length() > 50 ? base64.substring(0, 50) : base64));

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
     * Show waiting indicator for incoming video
     */
    private void showVideoWaitingIndicator(String username) {
        Platform.runLater(() -> {
            if (videoArea != null) {
                // Clear previous waiting indicators
                videoArea.getChildren().removeIf(node -> node instanceof Label &&
                        ((Label) node).getText().contains("Waiting for"));

                Label waitingLabel = new Label("⏳ Waiting for " + username + "'s video...");
                waitingLabel.setStyle("-fx-text-fill: white; " +
                        "-fx-font-size: 14px; " +
                        "-fx-background-color: rgba(0, 0, 0, 0.7); " +
                        "-fx-padding: 10px; " +
                        "-fx-background-radius: 5px;");

                StackPane.setAlignment(waitingLabel, javafx.geometry.Pos.CENTER);
                videoArea.getChildren().add(waitingLabel);
            }
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
     * Show/hide host video indicator
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

    // ... (rest of your existing methods remain the same)

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
                recordButton.setText("🔴 Stop Recording");
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

    /**
     * Opens the advanced MP4 recording controls window
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
            addSystemMessage("🔴 Basic recording started...");
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
                String username = HelloApplication.getLoggedInUser();
                String fileMessage = "📎 File shared: " + file.getName();
                addSystemMessage(fileMessage);

                // Save file share to database
                String meetingId = HelloApplication.getActiveMeetingId();
                if (meetingId != null) {
                    Database.saveChatMessage(meetingId, username, fileMessage, "SYSTEM");
                }

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
}