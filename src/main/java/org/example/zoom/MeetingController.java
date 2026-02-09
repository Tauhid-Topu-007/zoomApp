package org.example.zoom;

import javafx.fxml.FXML;
import javafx.event.ActionEvent;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.animation.PauseTransition;
import javafx.scene.input.MouseEvent;
import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.text.Font;
import javafx.scene.image.WritableImage;
import javafx.util.Pair;

import com.github.sarxos.webcam.Webcam;
import javax.sound.sampled.*;
import javafx.scene.paint.Color;

import java.io.*;
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
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

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

    @FXML private Button minimizeButton;
    @FXML private Button maximizeButton;
    @FXML private Button closeButton;
    @FXML private HBox titleBar;

    @FXML private MenuButton screenSizeButton;
    @FXML private MenuItem screenSizeSmall;
    @FXML private MenuItem screenSizeMedium;
    @FXML private MenuItem screenSizeLarge;
    @FXML private MenuItem screenSizeFull;
    @FXML private MenuItem screenSizeCustom;
    @FXML private Button toggleFullscreenButton;

    @FXML private VBox participantsPanel;
    @FXML private VBox chatPanel;

    @FXML private VBox audioControlsContainer;
    @FXML private VBox videoControlsContainer;

    @FXML private Button audioControlsButton;
    @FXML private Button videoControlsButton;

    @FXML private ImageView videoDisplay;
    @FXML private StackPane videoPlaceholder;

    @FXML private Label chatStatusLabel;
    @FXML private Button sendChatButton;
    @FXML private Button fileButton;
    @FXML private Button clearChatButton;
    @FXML private Button downloadButton;

    private enum VideoQuality {
        LOW(160, 120, 5),
        MEDIUM(320, 240, 10),
        HIGH(640, 480, 15);

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

    private AudioControlsController audioControlsController;
    private VideoControlsController videoControlsController;
    private MP4RecordingController mp4RecordingController;

    private Webcam webcam;
    private TargetDataLine microphone;
    private AudioFormat audioFormat;
    private boolean cameraAvailable = false;
    private boolean microphoneAvailable = false;

    private Thread cameraThread;
    private volatile boolean cameraRunning = false;
    private volatile boolean streamingEnabled = false;

    private Thread audioThread;
    private volatile boolean audioRunning = false;

    private double xOffset = 0;
    private double yOffset = 0;
    private boolean isMaximized = false;
    private boolean isFullscreen = false;

    private double originalX, originalY, originalWidth, originalHeight;

    private javafx.animation.Timeline meetingTimer;
    private int meetingSeconds = 0;

    private static MeetingController instance;

    private List<String> currentParticipants = new ArrayList<>();
    private List<String> activeVideoStreams = new ArrayList<>();
    private FileTransferHandler fileTransferHandler;

    // Chat-specific fields
    private String lastMessageId = "";
    private String currentUser;
    private org.example.zoom.websocket.SimpleWebSocketClient webSocketClient;
    private Map<String, FileTransferInfo> activeFileTransfers = new HashMap<>();
    private String downloadsFolder = "meeting_downloads";

    // Video streaming fields
    private String currentVideoHost = null;
    private long lastVideoFrameTime = 0;
    private static final long VIDEO_FRAME_TIMEOUT = 5000; // 5 seconds timeout
    private String displayedVideoUser = null;

    // Video frame counter for debugging
    private AtomicInteger framesReceived = new AtomicInteger(0);
    private AtomicInteger framesSent = new AtomicInteger(0);

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

        // Get current user
        currentUser = HelloApplication.getLoggedInUser();
        if (currentUser == null) {
            currentUser = "Unknown User";
        }

        System.out.println("=== MEETING CONTROLLER INITIALIZE ===");
        System.out.println("User: " + currentUser);
        System.out.println("Meeting ID: " + HelloApplication.getActiveMeetingId());
        System.out.println("Is Host: " + HelloApplication.isMeetingHost());
        System.out.println("WebSocket Connected: " + HelloApplication.isWebSocketConnected());

        fileTransferHandler = new FileTransferHandler(this);
        createVideoPlaceholder();
        initializeAudioVideoControllers();
        setupScrollableChat();
        setupScrollableParticipants();
        initializeParticipantTracking();
        updateParticipantsList();
        setupChat();
        updateMeetingInfo();
        updateButtonStyles();
        startMeetingTimer();
        setupScreenSizeControls();

        // Initialize chat WebSocket connection
        initializeChatWebSocketConnection();

        // Create downloads folder
        createDownloadsFolder();

        System.out.println("MeetingController initialized successfully");
    }

    private void initializeChatWebSocketConnection() {
        // Get WebSocket client from HelloApplication
        webSocketClient = HelloApplication.getWebSocketClient();

        if (webSocketClient != null) {
            // Set message handler for chat messages
            try {
                webSocketClient.getClass().getMethod("setMessageHandler", java.util.function.Consumer.class)
                        .invoke(webSocketClient, (java.util.function.Consumer<String>) this::handleChatWebSocketMessage);
                System.out.println("Chat message handler set on WebSocket client");
            } catch (Exception e) {
                System.out.println("Could not set message handler: " + e.getMessage());
                // Try alternative approach
                webSocketClient.setMessageHandler(this::handleChatWebSocketMessage);
            }

            // Update chat UI status
            updateChatConnectionUI();
            addSystemMessage("Chat connected to server");

            // Join meeting notification
            String meetingId = HelloApplication.getActiveMeetingId();
            if (meetingId != null && webSocketClient.isConnected()) {
                webSocketClient.sendMessage("USER_JOINED", meetingId, currentUser, "joined the meeting");
                System.out.println("Sent USER_JOINED notification");
            }
        } else {
            addSystemMessage("Chat not connected to server - messages will be local only");
            if (chatStatusLabel != null) {
                chatStatusLabel.setText("Disconnected");
                chatStatusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
            }
        }

        // Create downloads folder for meeting
        createDownloadsFolder();
    }

    private void createDownloadsFolder() {
        File folder = new File(downloadsFolder);
        if (!folder.exists()) {
            boolean created = folder.mkdirs();
            System.out.println("Created meeting downloads folder: " + created + " at " + folder.getAbsolutePath());
        }
    }

    private void updateChatConnectionUI() {
        Platform.runLater(() -> {
            boolean isConnected = webSocketClient != null && webSocketClient.isConnected();

            if (chatStatusLabel != null) {
                if (isConnected) {
                    chatStatusLabel.setText("Connected");
                    chatStatusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                } else {
                    chatStatusLabel.setText("Disconnected");
                    chatStatusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                }
            }

            if (sendChatButton != null) {
                sendChatButton.setDisable(!isConnected);
                if (!isConnected) {
                    sendChatButton.setTooltip(new Tooltip("Not connected to server"));
                } else {
                    sendChatButton.setTooltip(null);
                }
            }

            if (fileButton != null) {
                fileButton.setDisable(!isConnected);
                if (!isConnected) {
                    fileButton.setTooltip(new Tooltip("Not connected to server"));
                } else {
                    fileButton.setTooltip(null);
                }
            }
        });
    }

    private void handleChatWebSocketMessage(String message) {
        System.out.println("=== MeetingController chat received ===");
        System.out.println("Full message: " + message);
        System.out.println("Message length: " + message.length());

        String[] parts = message.split("\\|", 4);
        System.out.println("Parts count: " + parts.length);
        for (int i = 0; i < parts.length; i++) {
            System.out.println("Part " + i + " (" + parts[i].length() + " chars): " +
                    (parts[i].length() > 50 ? parts[i].substring(0, 50) + "..." : parts[i]));
        }

        if (parts.length >= 4) {
            String type = parts[0];
            String meetingId = parts[1];
            String username = parts[2];
            String content = parts[3];

            System.out.println("Parsed: Type=" + type + ", Meeting=" + meetingId +
                    ", User=" + username + ", Content length=" + content.length());

            // Skip if not for our meeting
            String currentMeetingId = HelloApplication.getActiveMeetingId();
            if (!meetingId.equals(currentMeetingId) && !meetingId.equals("global")) {
                System.out.println("Skipping - not our meeting. Current: " + currentMeetingId + ", Received: " + meetingId);
                return;
            }

            if (username.equals(currentUser) && isOwnMessageEcho(content, type)) {
                System.out.println("Skipping own message echo: " + content.substring(0, Math.min(50, content.length())));
                return;
            }

            Platform.runLater(() -> {
                try {
                    System.out.println("Processing message type: " + type);

                    switch (type) {
                        case "CHAT_MESSAGE":
                        case "CHAT":
                            handleIncomingChatMessage(username, content);
                            break;

                        case "FILE_TRANSFER":
                            System.out.println("Processing FILE_TRANSFER message");
                            handleIncomingFileTransfer(username, content);
                            break;

                        case "FILE_SHARE": // Legacy support
                            System.out.println("Processing FILE_SHARE (legacy)");
                            String fileName = content.replace("Shared file: ", "");
                            addFileMessage(fileName, 0, username.equals(currentUser), "Shared");
                            break;

                        case "SYSTEM":
                            addSystemMessage(content);
                            break;

                        case "USER_JOINED":
                            addSystemMessage(username + " joined the meeting");
                            addParticipant(username);
                            break;

                        case "USER_LEFT":
                            addSystemMessage(username + " left the meeting");
                            removeParticipant(username);
                            break;

                        case "CONNECTED":
                            addSystemMessage(content);
                            updateChatConnectionUI();
                            break;

                        case "DISCONNECTED":
                            addSystemMessage("Disconnected: " + content);
                            updateChatConnectionUI();
                            break;

                        case "VIDEO_FRAME":
                            System.out.println("Received VIDEO_FRAME from " + username);
                            int frameCount = framesReceived.incrementAndGet();
                            if (frameCount % 10 == 0) {
                                System.out.println("Total frames received: " + frameCount + " from " + username);
                            }
                            handleVideoFrameFromServer(username, content);
                            break;

                        case "VIDEO_STATUS":
                            System.out.println("Received VIDEO_STATUS from " + username + ": " + content);
                            handleVideoStatus(username, content);
                            break;

                        default:
                            // For any other message type, treat as chat
                            if (!type.startsWith("VIDEO_") && !type.startsWith("AUDIO_")) {
                                handleIncomingChatMessage(username, content);
                            }
                            break;
                    }
                } catch (Exception e) {
                    System.err.println("Error handling message: " + e.getMessage());
                    e.printStackTrace();
                    addSystemMessage("Error handling message: " + e.getMessage());
                }
            });
        } else {
            System.err.println("Invalid message format. Expected at least 4 parts, got " + parts.length);
            Platform.runLater(() -> addSystemMessage("Invalid message format received"));
        }
    }

    private void handleVideoStatus(String username, String content) {
        Platform.runLater(() -> {
            String[] statusParts = content.split("\\|");
            String action = statusParts[0];

            switch (action) {
                case "VIDEO_STARTED":
                    // A user started video streaming
                    if (!activeVideoStreams.contains(username)) {
                        activeVideoStreams.add(username);
                        updateParticipantsList();

                        if (username.equals(currentUser)) {
                            // Our own video started
                            showVideoOverlay("Your camera is live");
                            addSystemMessage("You started video streaming");
                        } else {
                            // Someone else's video started
                            if (currentVideoHost == null) {
                                currentVideoHost = username;
                                addSystemMessage(username + " started video streaming");
                                showVideoOverlay("Waiting for video from " + username + "...");
                            } else if (currentVideoHost.equals(username)) {
                                addSystemMessage(username + " resumed video streaming");
                                showVideoOverlay("Resuming video from " + username + "...");
                            } else {
                                addSystemMessage(username + " also started video streaming (multiple streams available)");
                            }
                        }
                    }
                    break;

                case "VIDEO_STOPPED":
                    // A user stopped video streaming
                    activeVideoStreams.remove(username);
                    updateParticipantsList();

                    if (username.equals(currentUser)) {
                        // Our own video stopped
                        showVideoOverlay("Your camera is off");
                        if (currentVideoHost != null && currentVideoHost.equals(username)) {
                            currentVideoHost = null;
                        }
                        addSystemMessage("You stopped video streaming");
                    } else {
                        // Someone else's video stopped
                        if (currentVideoHost != null && currentVideoHost.equals(username)) {
                            addSystemMessage(username + " stopped video streaming");
                            currentVideoHost = null;

                            // If there are other active streams, switch to the first one
                            if (!activeVideoStreams.isEmpty()) {
                                for (String streamUser : activeVideoStreams) {
                                    if (!streamUser.equals(currentUser)) {
                                        currentVideoHost = streamUser;
                                        addSystemMessage("Switching to " + streamUser + "'s video");
                                        showVideoOverlay("Waiting for video from " + streamUser + "...");
                                        break;
                                    }
                                }
                            } else {
                                // No active streams, show placeholder
                                clearVideoDisplay();
                                showVideoOverlay("No active video streams");
                            }
                        } else {
                            addSystemMessage(username + " stopped video streaming");
                        }
                    }
                    break;

                case "VIDEO_QUALITY":
                    // Video quality changed
                    if (statusParts.length > 1) {
                        String quality = statusParts[1];
                        addSystemMessage(username + " changed video quality to " + quality);
                    }
                    break;
            }
        });
    }

    private boolean isOwnMessageEcho(String content, String type) {
        if (type.equals("CHAT_MESSAGE") || type.equals("CHAT")) {
            return content.contains(lastMessageId) ||
                    (chatInput != null && content.equals(chatInput.getText().trim()));
        }
        return false;
    }

    @FXML
    protected void onSendChat() {
        if (chatInput == null) return;

        String msg = chatInput.getText().trim();
        if (!msg.isEmpty()) {
            String username = currentUser;
            String messageId = System.currentTimeMillis() + "_" + username;
            lastMessageId = messageId;

            System.out.println("=== SENDING CHAT MESSAGE FROM MEETING ===");
            System.out.println("From: " + username);
            System.out.println("Message: " + msg);

            // Display locally immediately
            addUserMessage(username + ": " + msg);
            chatInput.clear();

            String meetingId = HelloApplication.getActiveMeetingId();
            if (meetingId != null) {
                // Save to database
                boolean saved = Database.saveChatMessage(meetingId, username, msg, "USER");
                if (saved) {
                    System.out.println("Chat message saved to database for user: " + username);
                } else {
                    System.err.println("Failed to save chat message to database");
                }

                // Send via WebSocket to all participants
                if (webSocketClient != null && webSocketClient.isConnected()) {
                    System.out.println("WebSocket is connected, sending message...");
                    webSocketClient.sendMessage("CHAT_MESSAGE", meetingId, username, msg);
                    System.out.println("Chat message sent via WebSocket");
                } else {
                    System.err.println("WebSocket NOT connected - chat message not sent to other participants");
                    addSystemMessage("Warning: Chat message saved locally but not sent to other participants (no connection)");
                }
            } else {
                System.err.println("No active meeting ID");
            }
        }
    }

    @FXML
    protected void onSendFileInMeeting() {
        if (stage == null) {
            stage = (Stage) chatBox.getScene().getWindow();
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select a File to Send");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("All Files", "*.*"),
                new FileChooser.ExtensionFilter("Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"),
                new FileChooser.ExtensionFilter("PDF Documents", "*.pdf"),
                new FileChooser.ExtensionFilter("Audio Files", "*.mp3", "*.wav", "*.m4a", "*.aac", "*.flac"),
                new FileChooser.ExtensionFilter("Video Files", "*.mp4", "*.mov", "*.avi", "*.mkv", "*.wmv"),
                new FileChooser.ExtensionFilter("Documents", "*.doc", "*.docx", "*.txt", "*.rtf")
        );
        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            try {
                String fileName = file.getName();
                long fileSize = file.length();
                String fileId = UUID.randomUUID().toString();

                // Check file size (limit to 50MB for demo)
                long maxSize = 50 * 1024 * 1024; // 50MB
                if (fileSize > maxSize) {
                    showAlert("File Too Large",
                            "File size (" + formatFileSize(fileSize) + ") exceeds 50MB limit.",
                            Alert.AlertType.ERROR);
                    return;
                }

                // Read file bytes
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                String base64Data = Base64.getEncoder().encodeToString(fileBytes);

                // Send file in a single message (for simplicity)
                // Format: fileId|fileName|fileSize|base64Data
                String fileMessage = fileId + "|" + fileName + "|" + fileSize + "|" + base64Data;

                System.out.println("Sending file message:");
                System.out.println("File ID: " + fileId);
                System.out.println("File Name: " + fileName);
                System.out.println("File Size: " + fileSize);
                System.out.println("Base64 Data Length: " + base64Data.length());

                String meetingId = HelloApplication.getActiveMeetingId();
                if (webSocketClient != null && webSocketClient.isConnected() && meetingId != null) {
                    webSocketClient.sendMessage("FILE_TRANSFER", meetingId, currentUser, fileMessage);

                    addFileMessage(fileName, fileSize, true, "Sent");
                    addSystemMessage("File sent: " + fileName + " (" + formatFileSize(fileSize) + ")");

                    // Display file in chat
                    displayFileInChat(fileName, fileSize, true, file);
                } else {
                    addFileMessage(fileName, fileSize, true, "Cannot send - not connected");
                    addSystemMessage("File sharing requires server connection");

                    // Still display locally
                    displayFileInChat(fileName, fileSize, true, file);
                }

            } catch (Exception e) {
                e.printStackTrace();
                showAlert("Error", "Failed to send file: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    private void handleIncomingFileTransfer(String username, String content) {
        try {
            System.out.println("Processing file transfer from: " + username);
            System.out.println("Content length: " + content.length());

            String[] fileParts = content.split("\\|", 4);
            if (fileParts.length >= 4) {
                String fileId = fileParts[0];
                String fileName = fileParts[1];
                String fileSizeStr = fileParts[2];
                String base64Data = fileParts[3];

                System.out.println("Parsed: fileId=" + fileId + ", fileName=" + fileName +
                        ", fileSizeStr=" + fileSizeStr + ", dataLength=" + base64Data.length());

                try {
                    long fileSize = Long.parseLong(fileSizeStr);

                    // Ask user if they want to save the file
                    Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                    alert.setTitle("Incoming File");
                    alert.setHeaderText("File from " + username);
                    alert.setContentText("File: " + fileName + "\n" +
                            "Size: " + formatFileSize(fileSize) + "\n" +
                            "Type: " + getFileType(fileName) + "\n" +
                            "Do you want to save this file?");

                    ButtonType yesButton = new ButtonType("Save", ButtonBar.ButtonData.YES);
                    ButtonType noButton = new ButtonType("Cancel", ButtonBar.ButtonData.NO);
                    alert.getButtonTypes().setAll(yesButton, noButton);

                    alert.showAndWait().ifPresent(response -> {
                        if (response == yesButton) {
                            // Save the file
                            saveReceivedFile(username, fileName, fileSize, base64Data);
                        } else {
                            addSystemMessage("Cancelled receiving file: " + fileName);
                        }
                    });

                } catch (NumberFormatException e) {
                    System.err.println("Invalid file size format: " + fileSizeStr);
                    System.err.println("Full content was: " + content);
                    addSystemMessage("Error: Invalid file format received from " + username);

                    // Try to handle it as a simple file message instead
                    handleSimpleFileMessage(username, content);
                }
            } else {
                System.err.println("Invalid file transfer format. Expected 4 parts, got " + fileParts.length);
                System.err.println("Content: " + content);

                // Try to handle it as a legacy format or simple message
                if (content.contains("Shared file: ")) {
                    // Legacy format
                    String fileName = content.replace("Shared file: ", "");
                    addFileMessage(fileName, 0, username.equals(currentUser), "Shared");
                } else {
                    handleSimpleFileMessage(username, content);
                }
            }
        } catch (Exception e) {
            System.err.println("Error in handleIncomingFileTransfer: " + e.getMessage());
            e.printStackTrace();
            addSystemMessage("Error processing file from " + username);
        }
    }

    private void handleSimpleFileMessage(String username, String content) {
        // Try to extract filename from content
        String fileName = extractFileName(content);
        if (fileName != null) {
            addFileMessage(fileName, 0, username.equals(currentUser), "Received");
            addSystemMessage("Received file from " + username + ": " + fileName);
        } else {
            // If we can't parse it as a file, treat it as a chat message
            handleIncomingChatMessage(username, content);
        }
    }

    private String extractFileName(String content) {
        // Try to extract filename from various formats
        if (content.contains("|")) {
            String[] parts = content.split("\\|");
            if (parts.length > 1) {
                // Look for a part that looks like a filename
                for (String part : parts) {
                    if (part.contains(".") &&
                            (part.endsWith(".jpg") || part.endsWith(".jpeg") ||
                                    part.endsWith(".png") || part.endsWith(".gif") ||
                                    part.endsWith(".pdf") || part.endsWith(".mp3") ||
                                    part.endsWith(".mp4") || part.endsWith(".doc") ||
                                    part.endsWith(".docx") || part.endsWith(".txt"))) {
                        return part;
                    }
                }
            }
        }

        // Check if the whole content looks like a filename
        if (content.contains(".") && content.length() < 255) {
            String[] commonExts = {".jpg", ".jpeg", ".png", ".gif", ".bmp",
                    ".pdf", ".mp3", ".wav", ".mp4", ".avi",
                    ".doc", ".docx", ".txt", ".rtf", ".zip"};
            for (String ext : commonExts) {
                if (content.endsWith(ext)) {
                    return content;
                }
            }
        }

        return null;
    }

    private void saveReceivedFile(String sender, String fileName, long fileSize, String base64Data) {
        try {
            // Decode base64 data
            byte[] fileBytes = Base64.getDecoder().decode(base64Data);

            // Generate unique filename to avoid conflicts
            String uniqueFileName = getUniqueFileName(fileName);
            File outputFile = new File(downloadsFolder, uniqueFileName);

            // Write file to disk
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(fileBytes);
            }

            addFileMessage(fileName, fileSize, false, "Saved");
            addSystemMessage("File saved: " + outputFile.getAbsolutePath());

            // Display the file in chat
            displayFileInChat(fileName, fileSize, false, outputFile);

            // Ask if user wants to open the file
            Alert openAlert = new Alert(Alert.AlertType.CONFIRMATION);
            openAlert.setTitle("File Saved");
            openAlert.setHeaderText("File saved successfully");
            openAlert.setContentText("File: " + fileName + "\n" +
                    "Saved to: " + outputFile.getAbsolutePath() + "\n" +
                    "Do you want to open the file?");

            openAlert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    try {
                        java.awt.Desktop.getDesktop().open(outputFile);
                    } catch (Exception e) {
                        showAlert("Error", "Could not open file: " + e.getMessage(), Alert.AlertType.ERROR);
                    }
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            addSystemMessage("Failed to save file: " + fileName);
            showAlert("Error", "Failed to save file: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void displayFileInChat(String fileName, long fileSize, boolean isOwnFile, File file) {
        Platform.runLater(() -> {
            try {
                if (isImageFile(fileName)) {
                    displayImageInChat(fileName, fileSize, isOwnFile, file);
                } else if (isPDFFile(fileName)) {
                    displayPDFInChat(fileName, fileSize, isOwnFile, file);
                } else if (isAudioFile(fileName)) {
                    displayAudioInChat(fileName, fileSize, isOwnFile, file);
                } else if (isVideoFile(fileName)) {
                    displayVideoInChat(fileName, fileSize, isOwnFile, file);
                } else {
                    displayGenericFileInChat(fileName, fileSize, isOwnFile, file);
                }
                scrollToBottom();
            } catch (Exception e) {
                e.printStackTrace();
                displayGenericFileInChat(fileName, fileSize, isOwnFile, file);
            }
        });
    }

    private void displayImageInChat(String fileName, long fileSize, boolean isOwnFile, File file) {
        try {
            ImageView imageView = new ImageView(new Image(file.toURI().toString()));
            imageView.setFitWidth(200);
            imageView.setPreserveRatio(true);
            imageView.setStyle("-fx-border-color: #bdc3c7; -fx-border-radius: 5; -fx-cursor: hand;");
            imageView.setOnMouseClicked(e -> openFile(file));

            VBox imageContainer = new VBox(5);
            imageContainer.setStyle("-fx-padding: 5; -fx-background-color: " +
                    (isOwnFile ? "#e3f2fd" : "#e8f5e9") + "; -fx-border-radius: 5;");

            Label header = new Label((isOwnFile ? "You sent: " : "Received: ") + fileName +
                    " (" + formatFileSize(fileSize) + ")");
            header.setStyle("-fx-font-weight: bold; -fx-text-fill: " +
                    (isOwnFile ? "#3498db" : "#2ecc71") + ";");

            HBox buttonBox = new HBox(5);
            Button downloadBtn = new Button("Download");
            downloadBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
            downloadBtn.setOnAction(e -> downloadFile(file));

            Button openBtn = new Button("Open");
            openBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
            openBtn.setOnAction(e -> openFile(file));

            buttonBox.getChildren().addAll(downloadBtn, openBtn);
            imageContainer.getChildren().addAll(header, imageView, buttonBox);

            chatBox.getChildren().add(imageContainer);
        } catch (Exception e) {
            displayGenericFileInChat(fileName, fileSize, isOwnFile, file);
        }
    }

    private void displayPDFInChat(String fileName, long fileSize, boolean isOwnFile, File file) {
        VBox pdfContainer = new VBox(5);
        pdfContainer.setStyle("-fx-padding: 10; -fx-background-color: " +
                (isOwnFile ? "#fff3e0" : "#fff8e1") + "; -fx-border-radius: 5;");

        Label header = new Label((isOwnFile ? "You sent: " : "Received: ") + "PDF Document");
        header.setStyle("-fx-font-weight: bold; -fx-font-size: 14; -fx-text-fill: " +
                (isOwnFile ? "#f57c00" : "#ff8f00") + ";");

        Label nameLabel = new Label(fileName);
        Label sizeLabel = new Label("Size: " + formatFileSize(fileSize));

        HBox buttonBox = new HBox(10);
        Button downloadBtn = new Button("Download PDF");
        downloadBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
        downloadBtn.setOnAction(e -> downloadFile(file));

        Button openBtn = new Button("Open PDF");
        openBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        openBtn.setOnAction(e -> openFile(file));

        buttonBox.getChildren().addAll(downloadBtn, openBtn);
        pdfContainer.getChildren().addAll(header, nameLabel, sizeLabel, buttonBox);

        chatBox.getChildren().add(pdfContainer);
    }

    private void displayAudioInChat(String fileName, long fileSize, boolean isOwnFile, File file) {
        VBox audioContainer = new VBox(5);
        audioContainer.setStyle("-fx-padding: 10; -fx-background-color: " +
                (isOwnFile ? "#f3e5f5" : "#fce4ec") + "; -fx-border-radius: 5;");

        Label header = new Label((isOwnFile ? "You sent: " : "Received: ") + "Audio File");
        header.setStyle("-fx-font-weight: bold; -fx-font-size: 14; -fx-text-fill: " +
                (isOwnFile ? "#8e24aa" : "#c2185b") + ";");

        Label nameLabel = new Label(fileName);
        Label sizeLabel = new Label("Size: " + formatFileSize(fileSize));

        HBox buttonBox = new HBox(10);
        Button playBtn = new Button("Play");
        playBtn.setStyle("-fx-background-color: #9b59b6; -fx-text-fill: white;");
        playBtn.setOnAction(e -> playAudio(file));

        Button downloadBtn = new Button("Download");
        downloadBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
        downloadBtn.setOnAction(e -> downloadFile(file));

        buttonBox.getChildren().addAll(playBtn, downloadBtn);
        audioContainer.getChildren().addAll(header, nameLabel, sizeLabel, buttonBox);

        chatBox.getChildren().add(audioContainer);
    }

    private void displayVideoInChat(String fileName, long fileSize, boolean isOwnFile, File file) {
        VBox videoContainer = new VBox(5);
        videoContainer.setStyle("-fx-padding: 10; -fx-background-color: " +
                (isOwnFile ? "#e8f5e9" : "#f1f8e9") + "; -fx-border-radius: 5;");

        Label header = new Label((isOwnFile ? "You sent: " : "Received: ") + "Video File");
        header.setStyle("-fx-font-weight: bold; -fx-font-size: 14; -fx-text-fill: " +
                (isOwnFile ? "#388e3c" : "#689f38") + ";");

        Label nameLabel = new Label(fileName);
        Label sizeLabel = new Label("Size: " + formatFileSize(fileSize));

        HBox buttonBox = new HBox(10);
        Button playBtn = new Button("Play Video");
        playBtn.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
        playBtn.setOnAction(e -> playVideo(file));

        Button downloadBtn = new Button("Download");
        downloadBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
        downloadBtn.setOnAction(e -> downloadFile(file));

        buttonBox.getChildren().addAll(playBtn, downloadBtn);
        videoContainer.getChildren().addAll(header, nameLabel, sizeLabel, buttonBox);

        chatBox.getChildren().add(videoContainer);
    }

    private void displayGenericFileInChat(String fileName, long fileSize, boolean isOwnFile, File file) {
        HBox fileContainer = new HBox(10);
        fileContainer.setStyle("-fx-padding: 10; -fx-background-color: " +
                (isOwnFile ? "#f5f5f5" : "#eeeeee") + "; -fx-border-radius: 5; -fx-alignment: center-left;");

        ImageView icon = new ImageView();
        String fileType = getFileType(fileName).toLowerCase();

        // Set appropriate icon based on file type
        if (fileType.contains("word") || fileType.contains("doc")) {
            icon.setImage(new Image("file:src/main/resources/org/example/zoom/word_icon.png", 32, 32, true, true));
        } else if (fileType.contains("text")) {
            icon.setImage(new Image("file:src/main/resources/org/example/zoom/text_icon.png", 32, 32, true, true));
        } else {
            icon.setImage(new Image("file:src/main/resources/org/example/zoom/file_icon.png", 32, 32, true, true));
        }

        VBox infoBox = new VBox(2);
        Label nameLabel = new Label(fileName);
        nameLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: " +
                (isOwnFile ? "#3498db" : "#2ecc71") + ";");

        Label sizeLabel = new Label(formatFileSize(fileSize) + " • " + getFileType(fileName));
        sizeLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-size: 12;");

        infoBox.getChildren().addAll(nameLabel, sizeLabel);

        HBox buttonBox = new HBox(5);
        Button downloadBtn = new Button("Download");
        downloadBtn.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-size: 12;");
        downloadBtn.setOnAction(e -> downloadFile(file));

        Button openBtn = new Button("Open");
        openBtn.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-size: 12;");
        openBtn.setOnAction(e -> openFile(file));

        buttonBox.getChildren().addAll(downloadBtn, openBtn);

        fileContainer.getChildren().addAll(icon, infoBox, new Region(), buttonBox);
        HBox.setHgrow(new Region(), Priority.ALWAYS);

        chatBox.getChildren().add(fileContainer);
    }

    private void playAudio(File audioFile) {
        try {
            Media media = new Media(audioFile.toURI().toString());
            MediaPlayer player = new MediaPlayer(media);

            // Create audio player dialog
            Dialog<Void> audioDialog = new Dialog<>();
            audioDialog.setTitle("Audio Player");
            audioDialog.setHeaderText("Playing: " + audioFile.getName());

            VBox content = new VBox(10);
            content.setStyle("-fx-padding: 20;");

            Label timeLabel = new Label("00:00 / 00:00");
            Slider progressSlider = new Slider();
            progressSlider.setMin(0);
            progressSlider.setMax(100);

            HBox controls = new HBox(10);
            Button playPauseBtn = new Button("Pause");
            Button stopBtn = new Button("Stop");
            Button closeBtn = new Button("Close");

            controls.getChildren().addAll(playPauseBtn, stopBtn, closeBtn);
            controls.setAlignment(Pos.CENTER);

            content.getChildren().addAll(timeLabel, progressSlider, controls);
            audioDialog.getDialogPane().setContent(content);
            audioDialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

            // Set up event handlers
            playPauseBtn.setOnAction(e -> {
                if (playPauseBtn.getText().equals("Pause")) {
                    player.pause();
                    playPauseBtn.setText("Play");
                } else {
                    player.play();
                    playPauseBtn.setText("Pause");
                }
            });

            stopBtn.setOnAction(e -> player.stop());
            closeBtn.setOnAction(e -> {
                player.stop();
                audioDialog.close();
            });

            // Update progress
            player.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                if (!progressSlider.isValueChanging()) {
                    double progress = (newTime.toSeconds() / player.getTotalDuration().toSeconds()) * 100;
                    progressSlider.setValue(progress);
                    timeLabel.setText(String.format("%02d:%02d / %02d:%02d",
                            (int)newTime.toMinutes(), (int)newTime.toSeconds() % 60,
                            (int)player.getTotalDuration().toMinutes(),
                            (int)player.getTotalDuration().toSeconds() % 60));
                }
            });

            progressSlider.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
                if (!isChanging) {
                    player.seek(player.getTotalDuration().multiply(progressSlider.getValue() / 100.0));
                }
            });

            player.play();
            audioDialog.show();

        } catch (Exception e) {
            showAlert("Playback Error", "Could not play audio file: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void playVideo(File videoFile) {
        try {
            Media media = new Media(videoFile.toURI().toString());
            MediaPlayer player = new MediaPlayer(media);
            MediaView mediaView = new MediaView(player);
            mediaView.setFitWidth(400);
            mediaView.setPreserveRatio(true);

            Stage videoStage = new Stage();
            videoStage.setTitle("Video Player - " + videoFile.getName());

            VBox root = new VBox(10);
            root.setStyle("-fx-padding: 10; -fx-background-color: #2c3e50;");

            HBox controls = new HBox(10);
            controls.setAlignment(Pos.CENTER);

            Button playPauseBtn = new Button("Pause");
            Button stopBtn = new Button("Stop");
            Slider volumeSlider = new Slider(0, 1, 0.5);
            volumeSlider.setPrefWidth(100);
            Label timeLabel = new Label("00:00 / 00:00");

            controls.getChildren().addAll(playPauseBtn, stopBtn, new Label("Volume:"), volumeSlider, timeLabel);

            Slider progressSlider = new Slider();
            progressSlider.setMin(0);
            progressSlider.setMax(100);

            root.getChildren().addAll(mediaView, progressSlider, controls);

            Scene scene = new Scene(root, 420, 350);
            videoStage.setScene(scene);

            // Set up event handlers
            playPauseBtn.setOnAction(e -> {
                if (playPauseBtn.getText().equals("Pause")) {
                    player.pause();
                    playPauseBtn.setText("Play");
                } else {
                    player.play();
                    playPauseBtn.setText("Pause");
                }
            });

            stopBtn.setOnAction(e -> player.stop());
            volumeSlider.valueProperty().bindBidirectional(player.volumeProperty());

            player.currentTimeProperty().addListener((obs, oldTime, newTime) -> {
                if (!progressSlider.isValueChanging()) {
                    double progress = (newTime.toSeconds() / player.getTotalDuration().toSeconds()) * 100;
                    progressSlider.setValue(progress);
                    timeLabel.setText(String.format("%02d:%02d / %02d:%02d",
                            (int)newTime.toMinutes(), (int)newTime.toSeconds() % 60,
                            (int)player.getTotalDuration().toMinutes(),
                            (int)player.getTotalDuration().toSeconds() % 60));
                }
            });

            progressSlider.valueChangingProperty().addListener((obs, wasChanging, isChanging) -> {
                if (!isChanging) {
                    player.seek(player.getTotalDuration().multiply(progressSlider.getValue() / 100.0));
                }
            });

            videoStage.setOnCloseRequest(e -> player.stop());
            player.play();
            videoStage.show();

        } catch (Exception e) {
            showAlert("Playback Error", "Could not play video file: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    @FXML
    protected void onDownloadAllFiles() {
        if (stage == null) {
            stage = (Stage) chatBox.getScene().getWindow();
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select Folder to Save All Files");
        fileChooser.setInitialFileName("meeting_files");

        // For folder selection, we'll use directory chooser if available
        try {
            java.lang.reflect.Method method = FileChooser.class.getMethod("setInitialDirectory", File.class);
            method.invoke(fileChooser, new File(downloadsFolder));
        } catch (Exception e) {
            // Method not available, continue
        }

        File selectedDir = fileChooser.showSaveDialog(stage);
        if (selectedDir != null) {
            try {
                File targetDir;
                if (selectedDir.isFile()) {
                    targetDir = selectedDir.getParentFile();
                } else {
                    targetDir = selectedDir;
                }

                // Create meeting-specific subfolder
                String meetingId = HelloApplication.getActiveMeetingId();
                String folderName = "MeetingFiles_" + (meetingId != null ? meetingId : new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date()));
                File meetingFolder = new File(targetDir, folderName);

                if (!meetingFolder.exists()) {
                    meetingFolder.mkdirs();
                }

                // Copy all files from downloads folder
                File sourceDir = new File(downloadsFolder);
                int fileCount = 0;
                if (sourceDir.exists() && sourceDir.isDirectory()) {
                    File[] files = sourceDir.listFiles();
                    if (files != null) {
                        for (File file : files) {
                            if (file.isFile()) {
                                Files.copy(file.toPath(),
                                        new File(meetingFolder, file.getName()).toPath(),
                                        StandardCopyOption.REPLACE_EXISTING);
                                fileCount++;
                            }
                        }
                    }
                }

                showAlert("Download Complete",
                        "Successfully downloaded " + fileCount + " files to:\n" + meetingFolder.getAbsolutePath(),
                        Alert.AlertType.INFORMATION);

            } catch (Exception e) {
                e.printStackTrace();
                showAlert("Download Error", "Failed to download files: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    private void downloadFile(File file) {
        if (stage == null) {
            stage = (Stage) chatBox.getScene().getWindow();
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save File As");
        fileChooser.setInitialFileName(file.getName());

        // Set initial directory to downloads folder
        try {
            java.lang.reflect.Method method = FileChooser.class.getMethod("setInitialDirectory", File.class);
            method.invoke(fileChooser, new File(System.getProperty("user.home"), "Downloads"));
        } catch (Exception e) {
            // Method not available, continue
        }

        File dest = fileChooser.showSaveDialog(stage);
        if (dest != null) {
            try {
                Files.copy(file.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
                showAlert("Download Complete",
                        "File downloaded successfully to:\n" + dest.getAbsolutePath(),
                        Alert.AlertType.INFORMATION);
            } catch (IOException e) {
                showAlert("Download Error", "Failed to download file: " + e.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    private void openFile(File file) {
        try {
            if (file.exists()) {
                java.awt.Desktop.getDesktop().open(file);
            } else {
                showAlert("File Not Found", "The file does not exist: " + file.getAbsolutePath(), Alert.AlertType.WARNING);
            }
        } catch (Exception e) {
            showAlert("Open Error", "Could not open file: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private String getUniqueFileName(String originalName) {
        File file = new File(downloadsFolder, originalName);
        if (!file.exists()) {
            return originalName;
        }

        // Add timestamp to filename
        String nameWithoutExt = originalName;
        String extension = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0) {
            nameWithoutExt = originalName.substring(0, dotIndex);
            extension = originalName.substring(dotIndex);
        }

        String timestamp = "_" + System.currentTimeMillis();
        return nameWithoutExt + timestamp + extension;
    }

    private void addFileMessage(String fileName, long fileSize, boolean isOwnFile, String status) {
        Platform.runLater(() -> {
            if (chatBox != null) {
                String fileInfo = (isOwnFile ? "You sent: " : "Received: ") + fileName;
                if (fileSize > 0) {
                    fileInfo += " (" + formatFileSize(fileSize) + ")";
                }
                fileInfo += " - " + status;

                Label fileLabel = new Label(fileInfo);
                if (isOwnFile) {
                    fileLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #3498db; -fx-background-color: #e3f2fd; -fx-padding: 5 10; -fx-border-radius: 5;");
                } else {
                    fileLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #2ecc71; -fx-background-color: #e8f5e9; -fx-padding: 5 10; -fx-border-radius: 5;");
                }

                // Make it clickable to open the file
                fileLabel.setOnMouseClicked(event -> {
                    if (!isOwnFile && status.equals("Saved")) {
                        openSavedFile(fileName);
                    }
                });

                chatBox.getChildren().add(fileLabel);
                scrollToBottom();
            }
        });
    }

    private void openSavedFile(String fileName) {
        try {
            // Find the file in downloads folder
            File folder = new File(downloadsFolder);
            File[] files = folder.listFiles((dir, name) -> name.startsWith(fileName.replaceFirst("[.][^.]+$", "")));

            if (files != null && files.length > 0) {
                // Open the most recent file with this name
                File fileToOpen = files[0];
                for (File file : files) {
                    if (file.lastModified() > fileToOpen.lastModified()) {
                        fileToOpen = file;
                    }
                }

                java.awt.Desktop.getDesktop().open(fileToOpen);
            } else {
                showAlert("File Not Found",
                        "Could not find saved file: " + fileName + "\n" +
                                "Check the downloads folder: " + folder.getAbsolutePath(),
                        Alert.AlertType.WARNING);
            }
        } catch (Exception e) {
            showAlert("Error", "Could not open file: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024.0));
        return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
    }

    private String getFileType(String fileName) {
        String ext = getFileExtension(fileName).toLowerCase();
        switch (ext) {
            case "jpg": case "jpeg": case "png": case "gif": case "bmp":
                return "Image";
            case "pdf":
                return "PDF Document";
            case "mp3": case "wav": case "m4a": case "aac": case "flac":
                return "Audio";
            case "mp4": case "mov": case "avi": case "mkv": case "wmv":
                return "Video";
            case "doc": case "docx":
                return "Word Document";
            case "txt": case "rtf":
                return "Text File";
            default:
                return "File";
        }
    }

    private String getFileExtension(String fileName) {
        int dotIndex = fileName.lastIndexOf('.');
        return dotIndex > 0 ? fileName.substring(dotIndex + 1) : "";
    }

    private boolean isImageFile(String fileName) {
        String ext = getFileExtension(fileName).toLowerCase();
        return ext.equals("jpg") || ext.equals("jpeg") || ext.equals("png") ||
                ext.equals("gif") || ext.equals("bmp");
    }

    private boolean isPDFFile(String fileName) {
        return getFileExtension(fileName).toLowerCase().equals("pdf");
    }

    private boolean isAudioFile(String fileName) {
        String ext = getFileExtension(fileName).toLowerCase();
        return ext.equals("mp3") || ext.equals("wav") || ext.equals("m4a") ||
                ext.equals("aac") || ext.equals("flac");
    }

    private boolean isVideoFile(String fileName) {
        String ext = getFileExtension(fileName).toLowerCase();
        return ext.equals("mp4") || ext.equals("mov") || ext.equals("avi") ||
                ext.equals("mkv") || ext.equals("wmv");
    }

    // ============ ORIGINAL MEETING CONTROLLER METHODS ============

    private void createVideoPlaceholder() {
        Platform.runLater(() -> {
            if (videoArea != null) {
                Canvas canvas = new Canvas(640, 480);
                GraphicsContext gc = canvas.getGraphicsContext2D();

                gc.setFill(Color.rgb(30, 40, 50));
                gc.fillRect(0, 0, 640, 480);

                gc.setStroke(Color.rgb(60, 80, 100));
                gc.setLineWidth(2);
                gc.strokeRect(10, 10, 620, 460);

                gc.setFill(Color.WHITE);
                gc.setFont(new javafx.scene.text.Font(20));
                gc.fillText("VIDEO STREAM", 240, 200);

                gc.setFill(Color.LIGHTGRAY);
                gc.setFont(new javafx.scene.text.Font(14));
                gc.fillText("Click 'Start Video' to begin streaming", 200, 240);

                WritableImage placeholderImage = canvas.snapshot(null, null);

                if (videoDisplay != null) {
                    videoDisplay.setImage(placeholderImage);
                    videoDisplay.setVisible(true);
                }

                System.out.println("Created video placeholder");
            }
        });
    }

    private void setupScreenSizeControls() {
        if (screenSizeButton != null) {
            screenSizeButton.setText(currentScreenSize.displayName);

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

    private void setScreenSize(ScreenSize size) {
        if (stage == null) {
            stage = (Stage) titleBar.getScene().getWindow();
        }

        if (stage != null) {
            if (isFullscreen) {
                toggleFullscreen();
            }

            if (size == ScreenSize.CUSTOM) {
                showCustomSizeDialog();
                return;
            }

            stage.setWidth(size.width);
            stage.setHeight(size.height);
            currentScreenSize = size;

            if (screenSizeButton != null) {
                screenSizeButton.setText(size.displayName);
            }

            centerWindowOnScreen();

            updateVideoDisplaySize();

            System.out.println("Screen size set to: " + size.displayName);
            addSystemMessage("Screen size changed to " + size.displayName);
        }
    }

    private void showCustomSizeDialog() {
        if (stage == null) return;

        Dialog<Pair<Integer, Integer>> dialog = new Dialog<>();
        dialog.setTitle("Custom Screen Size");
        dialog.setHeaderText("Enter custom width and height");

        ButtonType setButtonType = new ButtonType("Set", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(setButtonType, ButtonType.CANCEL);

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

        Platform.runLater(() -> widthField.requestFocus());

        dialog.setResultConverter(dialogButton -> {
            if (dialogButton == setButtonType) {
                try {
                    int width = Integer.parseInt(widthField.getText());
                    int height = Integer.parseInt(heightField.getText());

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
            if (isFullscreen) {
                toggleFullscreen();
            }

            stage.setWidth(dimensions.getKey());
            stage.setHeight(dimensions.getValue());

            currentScreenSize = ScreenSize.CUSTOM;
            if (screenSizeButton != null) {
                screenSizeButton.setText("Custom (" + dimensions.getKey() + "x" + dimensions.getValue() + ")");
            }

            centerWindowOnScreen();
            updateVideoDisplaySize();

            System.out.println("Custom screen size set: " + dimensions.getKey() + "x" + dimensions.getValue());
            addSystemMessage("Screen size set to custom: " + dimensions.getKey() + "x" + dimensions.getValue());
        });
    }

    @FXML
    private void toggleFullscreen() {
        if (stage == null) {
            stage = (Stage) titleBar.getScene().getWindow();
        }

        if (stage != null) {
            isFullscreen = !isFullscreen;
            stage.setFullScreen(isFullscreen);

            if (toggleFullscreenButton != null) {
                if (isFullscreen) {
                    toggleFullscreenButton.setText("Exit Fullscreen");
                } else {
                    toggleFullscreenButton.setText("Fullscreen");
                }
            }

            System.out.println("Fullscreen " + (isFullscreen ? "enabled" : "disabled"));
            addSystemMessage("Fullscreen " + (isFullscreen ? "enabled" : "disabled"));
        }
    }

    private void centerWindowOnScreen() {
        if (stage == null) return;

        Rectangle2D screenBounds = Screen.getPrimary().getVisualBounds();
        stage.setX((screenBounds.getWidth() - stage.getWidth()) / 2);
        stage.setY((screenBounds.getHeight() - stage.getHeight()) / 2);
    }

    private void updateVideoDisplaySize() {
        if (videoArea == null || videoDisplay == null) return;

        Platform.runLater(() -> {
            double newWidth = videoArea.getWidth() * 0.9;
            double newHeight = videoArea.getHeight() * 0.8;

            newWidth = Math.max(newWidth, 320);
            newHeight = Math.max(newHeight, 240);

            videoDisplay.setFitWidth(newWidth);
            videoDisplay.setFitHeight(newHeight);

            System.out.println("Video display resized to: " + newWidth + "x" + newHeight);
        });
    }

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

    private void updateUIForNewSize(double width, double height) {
        Platform.runLater(() -> {
            if (width < 1000) {
                if (participantsPanel != null) {
                    participantsPanel.setPrefWidth(200);
                    participantsPanel.setMaxWidth(200);
                }
                if (chatPanel != null) {
                    chatPanel.setPrefWidth(280);
                    chatPanel.setMaxWidth(280);
                }
            } else {
                if (participantsPanel != null) {
                    participantsPanel.setPrefWidth(250);
                    participantsPanel.setMaxWidth(250);
                }
                if (chatPanel != null) {
                    chatPanel.setPrefWidth(320);
                    chatPanel.setMaxWidth(320);
                }
            }

            if (!isFullscreen && screenSizeButton != null) {
                currentScreenSize = getClosestPreset((int)width, (int)height);
                screenSizeButton.setText(currentScreenSize.displayName);
            }
        });
    }

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

    private void enablePopupDragging(Parent root, Stage popupStage) {
        final double[] xOffset = new double[1];
        final double[] yOffset = new double[1];

        HBox titleBar = (HBox) root.lookup("#titleBar");

        if (titleBar != null) {
            titleBar.setOnMousePressed(event -> {
                xOffset[0] = event.getSceneX();
                yOffset[0] = event.getSceneY();
            });

            titleBar.setOnMouseDragged(event -> {
                popupStage.setX(event.getScreenX() - xOffset[0]);
                popupStage.setY(event.getScreenY() - yOffset[0]);
            });
        } else {
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

    private void initializeAudioVideoControllers() {
        System.out.println("Initializing audio/video controllers...");

        try {
            if (audioControlsContainer != null) {
                try {
                    FXMLLoader audioLoader = new FXMLLoader(getClass().getResource("audio-controls.fxml"));
                    audioLoader.load();
                    audioControlsController = audioLoader.getController();
                    if (audioControlsController != null) {
                        audioControlsController.setMeetingController(this);
                        System.out.println("Audio controls controller initialized");
                    } else {
                        System.err.println("Audio controls controller is null");
                    }
                } catch (IOException e) {
                    System.err.println("Failed to load audio controls: " + e.getMessage());
                    setupFallbackAudioControls();
                }
            }

            if (videoControlsContainer != null) {
                try {
                    FXMLLoader videoLoader = new FXMLLoader(getClass().getResource("video-controls.fxml"));
                    videoLoader.load();
                    videoControlsController = videoLoader.getController();

                    if (videoControlsController != null) {
                        try {
                            java.lang.reflect.Method method = videoControlsController.getClass()
                                    .getMethod("setMeetingController", MeetingController.class);
                            method.invoke(videoControlsController, this);
                            System.out.println("Video controls controller initialized and connected");
                        } catch (NoSuchMethodException e) {
                            System.err.println("VideoControlsController doesn't have setMeetingController method");
                            try {
                                videoControlsController.setMeetingController(this);
                            } catch (Exception e2) {
                                System.err.println("Could not set meeting controller: " + e2.getMessage());
                            }
                        } catch (java.lang.IllegalAccessException | java.lang.reflect.InvocationTargetException e) {
                            System.err.println("Error invoking setMeetingController: " + e.getMessage());
                            try {
                                videoControlsController.setMeetingController(this);
                            } catch (Exception e2) {
                                System.err.println("Direct assignment also failed: " + e2.getMessage());
                            }
                        }
                    } else {
                        System.err.println("Video controls controller is null");
                        try {
                            videoControlsController = new VideoControlsController();
                            videoControlsController.setMeetingController(this);
                            System.out.println("Created new VideoControlsController instance");
                        } catch (Exception e) {
                            System.err.println("Could not create VideoControlsController: " + e.getMessage());
                        }
                    }
                } catch (IOException e) {
                    System.err.println("Failed to load video controls: " + e.getMessage());
                    setupFallbackVideoControls();

                    try {
                        videoControlsController = new VideoControlsController();
                        videoControlsController.setMeetingController(this);
                        System.out.println("Created fallback VideoControlsController");
                    } catch (Exception e2) {
                        System.err.println("Could not create fallback controller: " + e2.getMessage());
                    }
                }
            }

            initializeHardware();

            updateButtonStyles();

        } catch (Exception e) {
            System.err.println("Error in initializeAudioVideoControllers: " + e.getMessage());
            e.printStackTrace();

            setupFallbackControls();
        }
    }

    private void initializeParticipantTracking() {
        String meetingId = HelloApplication.getActiveMeetingId();
        if (meetingId != null) {
            List<String> participants = Database.getParticipants(meetingId);
            currentParticipants.clear();
            currentParticipants.addAll(participants);

            System.out.println("Loaded " + participants.size() + " participants from database: " + participants);

            String host = HelloApplication.getLoggedInUser();
            if (host != null && !currentParticipants.contains(host)) {
                currentParticipants.add(host);
                System.out.println("Added host to participants: " + host);
            }
        }
    }

    private Stage getStageFromAnyComponent() {
        Node[] components = {chatBox, videoArea, titleBar, participantsList, chatInput, meetingIdLabel};

        for (Node component : components) {
            if (component != null) {
                Scene scene = component.getScene();
                if (scene != null) {
                    Window window = scene.getWindow();
                    if (window instanceof Stage) {
                        System.out.println("Found stage from: " + component.getClass().getSimpleName());
                        return (Stage) window;
                    }
                }
            }
        }

        try {
            Stage primaryStage = HelloApplication.getPrimaryStage();
            if (primaryStage != null) {
                System.out.println("Using primary stage as fallback");
                return primaryStage;
            }
        } catch (Exception e) {
            System.err.println("Could not get primary stage: " + e.getMessage());
        }

        System.err.println("Could not find stage from any component");
        return null;
    }

    private void setupScrollableChat() {
        if (chatBox != null) {
            chatBox.setMaxHeight(Double.MAX_VALUE);
            VBox.setVgrow(chatBox, Priority.ALWAYS);

            if (chatScroll != null) {
                chatScroll.setFitToWidth(true);
                chatScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
                chatScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
                chatScroll.setPrefViewportHeight(400);

                chatBox.heightProperty().addListener((observable, oldValue, newValue) -> {
                    Platform.runLater(() -> {
                        chatScroll.setVvalue(1.0);
                    });
                });
            }
        }
    }

    private void setupScrollableParticipants() {
        if (participantsList != null) {
            participantsList.setMaxHeight(Double.MAX_VALUE);
            participantsList.setPrefHeight(300);
            participantsList.setPlaceholder(new Label("No participants in meeting"));

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

    private void initializeHardware() {
        System.out.println("Initializing hardware...");

        try {
            System.out.println("Looking for cameras...");
            List<Webcam> webcams = Webcam.getWebcams();
            System.out.println("Found " + webcams.size() + " cameras");

            if (!webcams.isEmpty()) {
                webcam = webcams.get(0);
                System.out.println("Selected camera: " + webcam.getName());

                try {
                    webcam.setViewSize(new java.awt.Dimension(currentVideoQuality.width, currentVideoQuality.height));
                    System.out.println("Default resolution set to " + currentVideoQuality.width + "x" + currentVideoQuality.height);
                } catch (Exception e) {
                    System.out.println("Could not set default resolution: " + e.getMessage());
                }

                cameraAvailable = true;
                System.out.println("Camera initialized (not opened yet): " + webcam.getName());
            } else {
                System.out.println("No cameras found");
                cameraAvailable = false;
            }
        } catch (Exception e) {
            System.err.println("Error initializing camera: " + e.getMessage());
            cameraAvailable = false;
        }

        try {
            System.out.println("Initializing microphone...");
            audioFormat = new AudioFormat(16000, 16, 1, true, true);
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);

            if (!AudioSystem.isLineSupported(info)) {
                System.out.println("Microphone line not supported");
                microphoneAvailable = false;
                return;
            }

            microphone = (TargetDataLine) AudioSystem.getLine(info);
            microphoneAvailable = true;
            System.out.println("Microphone initialized successfully");
        } catch (Exception e) {
            System.err.println("Error initializing microphone: " + e.getMessage());
            microphoneAvailable = false;
        }

        System.out.println("Hardware initialization complete - Camera: " + cameraAvailable + ", Microphone: " + microphoneAvailable);
    }

    private void updateParticipantsList() {
        if (participantsList == null) return;

        String meetingId = HelloApplication.getActiveMeetingId();
        if (meetingId == null) return;

        List<String> databaseParticipants = Database.getParticipants(meetingId);

        for (String participant : databaseParticipants) {
            if (!currentParticipants.contains(participant)) {
                currentParticipants.add(participant);
            }
        }

        participantsList.getItems().clear();

        if (!currentParticipants.isEmpty()) {
            String hostUsername = HelloApplication.getLoggedInUser();
            for (String participant : currentParticipants) {
                String displayName = participant;
                if (participant.equals(hostUsername) && HelloApplication.isMeetingHost()) {
                    displayName = participant + " (Host)";
                } else if (activeVideoStreams.contains(participant)) {
                    displayName = participant + " (Video)";
                } else {
                    displayName = participant;
                }
                participantsList.getItems().add(displayName);
            }
        }

        int count = currentParticipants.size();
        if (participantsCountLabel != null) {
            participantsCountLabel.setText("Participants: " + count);
        }

        System.out.println("Updated participants list: " + currentParticipants);
    }

    public void addParticipant(String username) {
        if (username != null && !currentParticipants.contains(username)) {
            currentParticipants.add(username);

            String meetingId = HelloApplication.getActiveMeetingId();
            if (meetingId != null) {
                Database.addParticipant(meetingId, username);
            }

            Platform.runLater(() -> {
                updateParticipantsList();
                addSystemMessage(username + " joined the meeting");
            });

            System.out.println("Participant added: " + username);
        }
    }

    public void removeParticipant(String username) {
        if (currentParticipants.remove(username)) {
            String meetingId = HelloApplication.getActiveMeetingId();
            if (meetingId != null) {
                Database.removeParticipant(meetingId, username);
            }

            activeVideoStreams.remove(username);

            Platform.runLater(() -> {
                updateParticipantsList();
                addSystemMessage(username + " left the meeting");

                if (isDisplayingVideoFromUser(username)) {
                    clearVideoFromUser(username);
                }
            });

            System.out.println("Participant removed: " + username);
        }
    }

    private void setupChat() {
        if (chatInput == null) return;

        // Set up Enter key for sending messages
        chatInput.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                onSendChat();
            }
        });

        loadChatHistory();

        addSystemMessage("Welcome to the meeting! Meeting ID: " + HelloApplication.getActiveMeetingId());

        if (HelloApplication.isMeetingHost()) {
            addSystemMessage("You are the host of this meeting. You can start recordings.");
        }
    }

    private void loadChatHistory() {
        String meetingId = HelloApplication.getActiveMeetingId();
        if (meetingId == null) {
            System.out.println("No meeting ID available to load chat history");
            return;
        }

        System.out.println("Loading chat history for meeting: " + meetingId);
        List<Database.ChatMessage> chatHistory = Database.getChatMessages(meetingId);

        if (chatHistory.isEmpty()) {
            System.out.println("No previous chat history found");
        } else {
            System.out.println("Loading " + chatHistory.size() + " previous chat messages");
        }

        for (Database.ChatMessage chatMessage : chatHistory) {
            if ("SYSTEM".equals(chatMessage.getMessageType())) {
                addSystemMessage(chatMessage.getMessage());
            } else {
                addUserMessage(chatMessage.getUsername() + ": " + chatMessage.getMessage());
            }
        }

        scrollToBottom();
    }

    public void handleIncomingChatMessage(String username, String message) {
        Platform.runLater(() -> {
            // Don't display own messages again
            if (username.equals(currentUser)) {
                System.out.println("Ignoring own message from: " + username);
                return;
            }

            System.out.println("Received chat message from " + username + ": " + message);
            addUserMessage(username + ": " + message);

            // Save to database
            String meetingId = HelloApplication.getActiveMeetingId();
            if (meetingId != null) {
                Database.saveChatMessage(meetingId, username, message, "USER");
            }
        });
    }

    private void addUserMessage(String text) {
        addChatMessage(text, "#3498db", "white", "-fx-alignment: center-left; -fx-background-insets: 5;");
    }

    public void addSystemMessage(String text) {
        addChatMessage("System: " + text, "#2c3e50", "white", "-fx-alignment: center; -fx-font-style: italic; -fx-background-insets: 5;");

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
        messageLabel.setMaxWidth(380);
        messageLabel.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: %s; -fx-padding: 10 15; -fx-background-radius: 15; -fx-font-size: 13px; %s",
                bgColor, textColor, additionalStyle));

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

    @FXML
    protected void toggleAudio() {
        HelloApplication.toggleAudio();
        audioMuted = HelloApplication.isAudioMuted();
        updateButtonStyles();
    }

    @FXML
    protected void toggleVideo() {
        System.out.println("MeetingController.toggleVideo() called");

        if (!videoOn) {
            startRealVideoStreaming();
        } else {
            stopRealVideoStreaming();
        }
        videoOn = !videoOn;

        updateButtonStyles();
    }

    @FXML
    protected void onToggleWebRTC() {
        System.out.println("onToggleWebRTC() called - WebRTC toggle functionality");
        addSystemMessage("WebRTC functionality not yet implemented");
    }

    private void startRealVideoStreaming() {
        System.out.println("Starting REAL video streaming to multiple clients...");

        try {
            if (!cameraAvailable) {
                initializeHardware();
            }

            if (!cameraAvailable || webcam == null) {
                System.err.println("No camera available for streaming");
                addSystemMessage("Camera not available for streaming");
                return;
            }

            if (webcam.isOpen()) {
                System.out.println("Webcam is already open, closing it to change resolution...");
                webcam.close();
            }

            System.out.println("Setting video quality to: " + currentVideoQuality.name() +
                    " (" + currentVideoQuality.width + "x" + currentVideoQuality.height + ")");
            webcam.setViewSize(new java.awt.Dimension(currentVideoQuality.width, currentVideoQuality.height));

            startCamera();

            streamingEnabled = true;

            String username = HelloApplication.getLoggedInUser();
            if (!activeVideoStreams.contains(username)) {
                activeVideoStreams.add(username);
                updateParticipantsList();
            }

            String meetingId = HelloApplication.getActiveMeetingId();
            if (HelloApplication.isWebSocketConnected() && meetingId != null) {
                // Send video start notification
                HelloApplication.sendWebSocketMessage(
                        "VIDEO_STATUS",
                        meetingId,
                        username,
                        "VIDEO_STARTED|" + currentVideoQuality.name()
                );
                System.out.println("Sent VIDEO_STARTED notification to all participants");

                // Set ourselves as current video host
                if (currentVideoHost == null) {
                    currentVideoHost = username;
                }
            }

            Platform.runLater(() -> {
                if (videoDisplay != null) {
                    Canvas canvas = new Canvas(currentVideoQuality.width, currentVideoQuality.height);
                    GraphicsContext gc = canvas.getGraphicsContext2D();
                    gc.setFill(Color.rgb(30, 40, 50));
                    gc.fillRect(0, 0, currentVideoQuality.width, currentVideoQuality.height);
                    gc.setFill(Color.WHITE);
                    gc.setFont(new javafx.scene.text.Font(16));
                    gc.fillText("YOUR CAMERA", currentVideoQuality.width/2 - 50, currentVideoQuality.height/2);
                    gc.setFont(new javafx.scene.text.Font(12));
                    gc.fillText("Streaming to all participants...", currentVideoQuality.width/2 - 60, currentVideoQuality.height/2 + 20);

                    WritableImage placeholder = canvas.snapshot(null, null);
                    videoDisplay.setImage(placeholder);
                    videoDisplay.setVisible(true);
                }

                if (videoPlaceholder != null) {
                    videoPlaceholder.setVisible(false);
                }

                // Show overlay for our own video
                showVideoOverlay("Your camera is live");
            });

            addSystemMessage("You started video streaming to all participants");
            System.out.println("REAL video streaming started to multiple clients");

        } catch (Exception e) {
            System.err.println("Failed to start video streaming: " + e.getMessage());
            e.printStackTrace();
            addSystemMessage("Failed to start video streaming: " + e.getMessage());

            try {
                System.out.println("Attempting to recover camera...");
                initializeHardware();
            } catch (Exception ex) {
                System.err.println("Camera recovery failed: " + ex.getMessage());
            }
        }
    }

    private void stopRealVideoStreaming() {
        System.out.println("Stopping REAL video streaming...");

        stopCamera();

        streamingEnabled = false;

        String username = HelloApplication.getLoggedInUser();
        activeVideoStreams.remove(username);
        updateParticipantsList();

        String meetingId = HelloApplication.getActiveMeetingId();
        if (HelloApplication.isWebSocketConnected() && meetingId != null) {
            HelloApplication.sendWebSocketMessage(
                    "VIDEO_STATUS",
                    meetingId,
                    username,
                    "VIDEO_STOPPED"
            );
            System.out.println("Sent VIDEO_STOPPED notification to all participants");
        }

        Platform.runLater(() -> {
            if (videoDisplay != null) {
                videoDisplay.setImage(null);
                videoDisplay.setVisible(false);
            }
            if (videoPlaceholder != null) {
                videoPlaceholder.setVisible(true);
            }

            showVideoOverlay("Your camera is off");
        });

        addSystemMessage("You stopped video streaming");
        System.out.println("REAL video streaming stopped");
    }

    private void startCamera() {
        if (!cameraAvailable || webcam == null) {
            System.err.println("Cannot start camera: No camera available");
            showCameraError();
            return;
        }

        try {
            System.out.println("Starting multi-client camera streaming...");

            if (webcam.isOpen()) {
                System.out.println("Webcam was already open, closing first...");
                webcam.close();
                Thread.sleep(100);
            }

            System.out.println("Opening camera with resolution: " +
                    currentVideoQuality.width + "x" + currentVideoQuality.height);
            webcam.open();
            System.out.println("Camera opened successfully");

            cameraRunning = true;
            cameraThread = new Thread(() -> {
                System.out.println("Camera streaming thread started");
                int frameCount = 0;
                long lastFrameTime = System.currentTimeMillis();
                int frameInterval = 1000 / currentVideoQuality.fps;

                while (cameraRunning && webcam.isOpen()) {
                    try {
                        long currentTime = System.currentTimeMillis();
                        long timeSinceLastFrame = currentTime - lastFrameTime;

                        if (timeSinceLastFrame >= frameInterval) {
                            lastFrameTime = currentTime;

                            java.awt.image.BufferedImage awtImage = webcam.getImage();
                            if (awtImage != null) {
                                Image fxImage = SwingFXUtils.toFXImage(awtImage, null);

                                Platform.runLater(() -> {
                                    if (videoDisplay != null && currentVideoHost != null &&
                                            currentVideoHost.equals(currentUser)) {
                                        // Only show our own video in the main display if we're the current host
                                        videoDisplay.setImage(fxImage);
                                    }
                                    if (videoControlsController != null) {
                                        videoControlsController.displayVideoFrame(fxImage);
                                    }
                                });

                                // Send frame via WebSocket to all participants
                                if (streamingEnabled && HelloApplication.isWebSocketConnected() &&
                                        HelloApplication.getActiveMeetingId() != null) {

                                    String base64Frame = compressAndConvertImage(awtImage);
                                    if (base64Frame != null && !base64Frame.isEmpty()) {
                                        String username = HelloApplication.getLoggedInUser();
                                        String meetingId = HelloApplication.getActiveMeetingId();

                                        // Use the WebSocket client directly
                                        if (webSocketClient != null && webSocketClient.isConnected()) {
                                            webSocketClient.sendMessage(
                                                    "VIDEO_FRAME",
                                                    meetingId,
                                                    username,
                                                    base64Frame
                                            );

                                            frameCount++;
                                            framesSent.incrementAndGet();

                                            if (frameCount % 10 == 0) {
                                                System.out.println("Sent frame #" + frameCount +
                                                        " to all participants (" +
                                                        base64Frame.length() + " bytes)");
                                            }
                                        } else {
                                            System.err.println("WebSocket client not connected, cannot send video frame");
                                        }
                                    }
                                }
                            }
                        }

                        Thread.sleep(10);
                    } catch (Exception e) {
                        System.err.println("Camera capture error: " + e.getMessage());
                        break;
                    }
                }
                System.out.println("Camera streaming thread stopped");
            });
            cameraThread.setDaemon(true);
            cameraThread.start();

            System.out.println("Camera started with multi-client streaming");

        } catch (Exception e) {
            System.err.println("Failed to start camera: " + e.getMessage());
            e.printStackTrace();
            showCameraError();
        }
    }

    private String compressAndConvertImage(java.awt.image.BufferedImage awtImage) {
        try {
            int targetWidth = currentVideoQuality.width;
            int targetHeight = currentVideoQuality.height;

            java.awt.Image scaledImage = awtImage.getScaledInstance(
                    targetWidth, targetHeight, java.awt.Image.SCALE_SMOOTH);

            java.awt.image.BufferedImage bufferedScaledImage = new java.awt.image.BufferedImage(
                    targetWidth, targetHeight, java.awt.image.BufferedImage.TYPE_INT_RGB);

            java.awt.Graphics2D g2d = bufferedScaledImage.createGraphics();
            g2d.drawImage(scaledImage, 0, 0, null);
            g2d.dispose();

            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(bufferedScaledImage, "jpg", baos);
            byte[] imageBytes = baos.toByteArray();

            String base64 = java.util.Base64.getEncoder().encodeToString(imageBytes);
            return base64;

        } catch (Exception e) {
            System.err.println("Error converting to base64: " + e.getMessage());
            return null;
        }
    }

    public void displayVideoFrame(String username, Image videoFrame) {
        Platform.runLater(() -> {
            try {
                System.out.println("DISPLAY VIDEO FROM: " + username);
                System.out.println("Frame size: " + videoFrame.getWidth() + "x" + videoFrame.getHeight());

                if (videoDisplay != null) {
                    videoDisplay.setImage(videoFrame);
                    videoDisplay.setVisible(true);
                    videoDisplay.setFitWidth(640);
                    videoDisplay.setFitHeight(480);
                    videoDisplay.setPreserveRatio(true);
                    videoDisplay.setSmooth(true);

                    System.out.println("Updated video display with frame from " + username);
                }

                if (videoPlaceholder != null) {
                    videoPlaceholder.setVisible(false);
                    System.out.println("Hid video placeholder");
                }

                // Update who we're displaying video from
                displayedVideoUser = username;

                // Update overlay
                String overlayText = username.equals(currentUser) ? "You (Live)" : username + " (Live)";
                showVideoOverlay(overlayText);

                isDisplayingVideo = true;

                if (!activeVideoStreams.contains(username) && !username.equals(currentUser)) {
                    activeVideoStreams.add(username);
                    updateParticipantsList();
                }

            } catch (Exception e) {
                System.err.println("Error in displayVideoFrame: " + e.getMessage());
                e.printStackTrace();
            }
        });
    }

    private void showVideoOverlay(String text) {
        Platform.runLater(() -> {
            if (videoArea != null) {
                // Remove existing overlay
                videoArea.getChildren().removeIf(node -> node instanceof Label &&
                        ((Label) node).getStyle().contains("-fx-background-color: rgba(0,0,0,0.7)"));

                if (text != null && !text.isEmpty()) {
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
            }
        });
    }

    private boolean isDisplayingVideoFromUser(String username) {
        return isDisplayingVideo && username.equals(displayedVideoUser);
    }

    private void stopCamera() {
        System.out.println("Stopping camera...");
        cameraRunning = false;
        streamingEnabled = false;

        if (cameraThread != null && cameraThread.isAlive()) {
            try {
                cameraThread.join(1000);
                cameraThread = null;
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (webcam != null) {
            try {
                if (webcam.isOpen()) {
                    webcam.close();
                    System.out.println("Camera closed");
                }
            } catch (Exception e) {
                System.err.println("Error closing camera: " + e.getMessage());
            }
        }

        Platform.runLater(() -> {
            if (videoDisplay != null && displayedVideoUser != null && displayedVideoUser.equals(currentUser)) {
                videoDisplay.setImage(null);
                videoDisplay.setVisible(false);
            }
            if (videoPlaceholder != null) {
                videoPlaceholder.setVisible(true);
                videoPlaceholder.setManaged(true);
            }
            if (videoControlsController != null) {
                videoControlsController.resetToSimulatedCamera();
            }
            if (videoArea != null) {
                videoArea.getChildren().removeIf(node -> node instanceof Label);
            }
        });

        System.out.println("Camera stopped successfully");
    }

    private void startMicrophone() {
        if (!microphoneAvailable || microphone == null) {
            System.err.println("Cannot start microphone: No microphone available");
            return;
        }

        try {
            System.out.println("Starting microphone...");
            microphone.open(audioFormat);
            microphone.start();

            audioRunning = true;
            audioThread = new Thread(() -> {
                System.out.println("Microphone thread started");
                byte[] buffer = new byte[4096];
                while (audioRunning && microphone.isOpen()) {
                    try {
                        int bytesRead = microphone.read(buffer, 0, buffer.length);
                        if (bytesRead > 0 && !audioMuted) {
                            processAudioData(buffer, bytesRead);
                        }
                    } catch (Exception e) {
                        System.err.println("Microphone capture error: " + e.getMessage());
                        break;
                    }
                }
                System.out.println("Microphone thread stopped");
            });
            audioThread.setDaemon(true);
            audioThread.start();

            System.out.println("Microphone started successfully");

        } catch (Exception e) {
            System.err.println("Failed to start microphone: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void stopMicrophone() {
        System.out.println("Stopping microphone...");
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
            System.out.println("Microphone closed");
        }

        System.out.println("Microphone stopped successfully");
    }

    private void processAudioData(byte[] audioData, int length) {
        if (System.currentTimeMillis() % 5000 < 100) {
            System.out.println("Audio data captured: " + length + " bytes");
        }
    }

    public void handleWebSocketMessage(String message) {
        try {
            System.out.println("=== MEETING CONTROLLER RECEIVED WEBSOCKET MESSAGE ===");
            System.out.println("Full message: " + message);

            String[] parts = message.split("\\|", 4);
            if (parts.length >= 4) {
                String type = parts[0];
                String meetingId = parts[1];
                String username = parts[2];
                String content = parts[3];

                System.out.println("Parsed: Type=" + type + ", Meeting=" + meetingId + ", User=" + username + ", Content=" + content);

                // Skip our own messages (they're already displayed)
                if (username.equals(currentUser)) {
                    System.out.println("Skipping own message: " + type);
                    return;
                }

                // Check meeting ID
                String currentMeetingId = HelloApplication.getActiveMeetingId();
                if (!meetingId.equals(currentMeetingId) && !meetingId.equals("global")) {
                    System.out.println("Ignoring - wrong meeting ID. Current: " + currentMeetingId + ", Received: " + meetingId);
                    return;
                }

                Platform.runLater(() -> {
                    try {
                        System.out.println("Processing message type: " + type);

                        if ("CHAT_MESSAGE".equals(type)) {
                            System.out.println("Handling CHAT_MESSAGE from " + username);
                            handleIncomingChatMessage(username, content);
                        } else if ("CHAT".equals(type)) { // Backward compatibility
                            System.out.println("Handling CHAT from " + username);
                            handleIncomingChatMessage(username, content);
                        } else if ("VIDEO_FRAME".equals(type)) {
                            System.out.println("Video frame from: " + username);
                            int frameCount = framesReceived.incrementAndGet();
                            if (frameCount % 10 == 0) {
                                System.out.println("Total frames received: " + frameCount + " from " + username);
                            }
                            handleVideoFrameFromServer(username, content);
                        } else if ("VIDEO_STATUS".equals(type)) {
                            handleVideoStatus(username, content);
                        } else if ("USER_JOINED".equals(type)) {
                            addSystemMessage(username + " joined the meeting");
                            addParticipant(username);
                        } else if ("USER_LEFT".equals(type)) {
                            addSystemMessage(username + " left the meeting");
                            removeParticipant(username);
                        } else if ("AUDIO_STATUS".equals(type)) {
                            addSystemMessage(username + " " + content);
                        } else if ("MEETING_CREATED".equals(type)) {
                            addSystemMessage("Meeting created: " + content);
                        } else if ("FILE_TRANSFER".equals(type)) {
                            handleIncomingFileTransfer(username, content);
                        } else {
                            System.out.println("Unknown message type: " + type);
                        }
                    } catch (Exception e) {
                        System.err.println("Error in Platform.runLater: " + e.getMessage());
                        e.printStackTrace();
                    }
                });
            } else {
                System.err.println("Invalid WebSocket message format: " + message);
            }
        } catch (Exception e) {
            System.err.println("Error in handleWebSocketMessage: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void handleVideoFrameFromServer(String username, String base64Image) {
        try {
            System.out.println("Processing video frame from: " + username);

            // Check if we should display this user's video
            // We display video from the current video host, or if there's no host, we display the first user with video
            if (currentVideoHost == null || currentVideoHost.equals(username)) {
                Image videoFrame = convertBase64ToImageSimple(base64Image);
                if (videoFrame != null) {
                    displayVideoFrame(username, videoFrame);
                    lastVideoFrameTime = System.currentTimeMillis();
                } else {
                    System.err.println("Failed to convert base64 to image from: " + username);
                }
            } else {
                System.out.println("Not displaying video from " + username + ", current host is " + currentVideoHost);
            }
        } catch (Exception e) {
            System.err.println("Error handling video frame from server: " + e.getMessage());
        }
    }

    private Image convertBase64ToImageSimple(String base64) {
        try {
            System.out.println("Converting Base64 to Image...");
            System.out.println("Base64 length: " + base64.length());

            byte[] bytes = java.util.Base64.getDecoder().decode(base64);
            System.out.println("Decoded bytes: " + bytes.length);

            java.io.ByteArrayInputStream bis = new java.io.ByteArrayInputStream(bytes);
            Image image = new Image(bis);

            if (image.isError()) {
                System.err.println("Image has error");
                Throwable error = image.getException();
                if (error != null) {
                    System.err.println("Image error: " + error.getMessage());
                }
                return null;
            }

            System.out.println("Created image: " + image.getWidth() + "x" + image.getHeight());
            return image;

        } catch (Exception e) {
            System.err.println("Simple conversion error: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    private void cleanupAudioVideoResources() {
        System.out.println("Cleaning up audio/video resources...");

        stopCamera();
        stopMicrophone();

        if (audioThread != null && audioThread.isAlive()) {
            audioThread.interrupt();
        }

        if (cameraThread != null && cameraThread.isAlive()) {
            cameraThread.interrupt();
        }
    }

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
                        handleFileTransferRequest(username, fileId, fileName, data);
                        break;
                    case "DATA":
                        handleFileDataChunk(username, fileId, fileName, data);
                        break;
                    case "COMPLETE":
                        handleFileTransferComplete(username, fileId, fileName);
                        break;
                    case "ERROR":
                        handleFileTransferError(username, fileId, fileName, data);
                        break;
                }
            }
        } catch (Exception e) {
            System.err.println("Error handling file transfer message: " + e.getMessage());
        }
    }

    private void handleFileTransferRequest(String sender, String fileId, String fileName, String fileSizeStr) {
        Platform.runLater(() -> {
            try {
                long fileSize = Long.parseLong(fileSizeStr);

                Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
                alert.setTitle("File Transfer Request");
                alert.setHeaderText("Incoming File from " + sender);
                alert.setContentText(String.format(
                        "File: %s\nSize: %s\nDo you want to accept this file?",
                        fileName, formatFileSize(fileSize)
                ));

                Optional<ButtonType> result = alert.showAndWait();
                if (result.isPresent() && result.get() == ButtonType.OK) {
                    fileTransferHandler.acceptFileTransfer(fileId, sender, fileName, fileSize);
                    addSystemMessage("Accepted file transfer from " + sender + ": " + fileName);
                } else {
                    fileTransferHandler.rejectFileTransfer(fileId, sender);
                    addSystemMessage("Rejected file transfer from " + sender + ": " + fileName);
                }
            } catch (Exception e) {
                System.err.println("Error handling file transfer request: " + e.getMessage());
            }
        });
    }

    private void handleFileDataChunk(String sender, String fileId, String fileName, String chunkData) {
        try {
            boolean success = fileTransferHandler.receiveFileChunk(fileId, sender, chunkData);
            if (success) {
                if (System.currentTimeMillis() % 5000 < 100) {
                    int progress = fileTransferHandler.getTransferProgress(fileId);
                    Platform.runLater(() -> {
                        addSystemMessage("Receiving " + fileName + ": " + progress + "%");
                    });
                }
            }
        } catch (Exception e) {
            System.err.println("Error handling file data chunk: " + e.getMessage());
        }
    }

    private void handleFileTransferComplete(String sender, String fileId, String fileName) {
        Platform.runLater(() -> {
            try {
                File receivedFile = fileTransferHandler.completeFileTransfer(fileId);
                if (receivedFile != null && receivedFile.exists()) {
                    addSystemMessage("File received from " + sender + ": " + fileName);
                    displayReceivedFileInChat(fileName, receivedFile);
                } else {
                    addSystemMessage("Failed to receive file from " + sender + ": " + fileName);
                }
            } catch (Exception e) {
                System.err.println("Error completing file transfer: " + e.getMessage());
            }
        });
    }

    private void handleFileTransferError(String sender, String fileId, String fileName, String error) {
        Platform.runLater(() -> {
            addSystemMessage("File transfer failed from " + sender + ": " + fileName + " (" + error + ")");
            fileTransferHandler.cancelFileTransfer(fileId);
        });
    }

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

    private void createPlaceholderVideo(String username) {
        Platform.runLater(() -> {
            Canvas canvas = new Canvas(640, 480);
            GraphicsContext gc = canvas.getGraphicsContext2D();
            gc.setFill(Color.rgb(40, 40, 60));
            gc.fillRect(0, 0, 640, 480);
            gc.setFill(Color.YELLOW);
            gc.fillText(username + "'s Video Stream", 220, 220);
            gc.fillText("Connecting...", 280, 250);

            WritableImage placeholder = canvas.snapshot(null, null);
            displayVideoFrame(username, placeholder);
        });
    }

    private void showWaitingMessage(String text) {
        Platform.runLater(() -> {
            if (videoArea != null) {
                videoArea.getChildren().removeIf(node ->
                        node instanceof Label && ((Label) node).getText().contains("streaming"));

                Label waitingLabel = new Label("Waiting: " + text);
                waitingLabel.setStyle("-fx-text-fill: white; -fx-font-size: 16px;");

                StackPane.setAlignment(waitingLabel, javafx.geometry.Pos.CENTER);
                videoArea.getChildren().add(waitingLabel);
            }
        });
    }

    private void clearVideoDisplay() {
        Platform.runLater(() -> {
            if (videoDisplay != null) {
                videoDisplay.setImage(null);
                videoDisplay.setVisible(false);
            }
            if (videoPlaceholder != null) {
                videoPlaceholder.setVisible(true);
            }

            if (videoArea != null) {
                videoArea.getChildren().removeIf(node -> node instanceof Label);
            }

            isDisplayingVideo = false;
            displayedVideoUser = null;
        });
    }

    private void clearVideoFromUser(String username) {
        Platform.runLater(() -> {
            if (displayedVideoUser != null && displayedVideoUser.equals(username)) {
                clearVideoDisplay();
                System.out.println("Cleared video from: " + username);
            }
        });
    }

    public void updateVideoState(boolean videoOn) {
        this.videoOn = videoOn;
        updateButtonStyles();
    }

    public void showHostVideoIndicator(boolean show) {
        Platform.runLater(() -> {
            if (show) {
                addSystemMessage("Host is now sharing video - waiting for stream...");

                if (videoDisplay != null) {
                    videoDisplay.setVisible(true);
                }
                if (videoPlaceholder != null) {
                    videoPlaceholder.setVisible(false);
                }
            } else {
                if (videoDisplay != null && currentVideoHost != null &&
                        currentVideoHost.equals(HelloApplication.getLoggedInUser())) {
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

        originalX = stage.getX();
        originalY = stage.getY();
        originalWidth = stage.getWidth();
        originalHeight = stage.getHeight();

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

        titleBar.setOnMouseClicked((MouseEvent event) -> {
            if (event.getClickCount() == 2) {
                toggleMaximize();
            }
        });
    }

    @FXML
    private void onMinimizeButton() {
        System.out.println("Minimize button clicked - Direct approach");

        try {
            Node source = (Node) minimizeButton;
            Stage currentStage = (Stage) source.getScene().getWindow();

            if (currentStage != null) {
                System.out.println("Minimizing stage directly: " + currentStage.getTitle());
                currentStage.setIconified(true);
            } else {
                System.err.println("Could not get stage from button scene");
            }
        } catch (Exception e) {
            System.err.println("Error minimizing window: " + e.getMessage());
            e.printStackTrace();

            tryMinimizeAllApproaches();
        }
    }

    private void tryMinimizeAllApproaches() {
        System.out.println("Trying all minimize approaches...");

        if (stage != null) {
            stage.setIconified(true);
            System.out.println("Minimized using stored stage");
            return;
        }

        Node[] components = {chatBox, videoArea, titleBar, minimizeButton};
        for (Node component : components) {
            if (component != null && component.getScene() != null) {
                Stage currentStage = (Stage) component.getScene().getWindow();
                if (currentStage != null) {
                    currentStage.setIconified(true);
                    System.out.println("Minimized using component: " + component.getClass().getSimpleName());
                    return;
                }
            }
        }

        System.err.println("All minimize approaches failed");
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
                stage.setMaximized(false);
                stage.setX(originalX);
                stage.setY(originalY);
                stage.setWidth(originalWidth);
                stage.setHeight(originalHeight);
                isMaximized = false;
            } else {
                originalX = stage.getX();
                originalY = stage.getY();
                originalWidth = stage.getWidth();
                originalHeight = stage.getHeight();

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
                maximizeButton.setText("Restore");
            } else {
                maximizeButton.setText("Maximize");
            }
        }
    }

    private void updateMeetingInfo() {
        String meetingId = HelloApplication.getActiveMeetingId();
        if (meetingId != null) {
            meetingIdLabel.setText("Meeting ID: " + meetingId);
        }

        if (HelloApplication.isMeetingHost()) {
            hostLabel.setText("Host");
            hostLabel.setStyle("-fx-text-fill: #e67e22; -fx-font-weight: bold;");
            if (recordButton != null) recordButton.setDisable(false);

            if (audioControlsController != null) {
                audioControlsController.onHostStatusChanged(true);
            }
            if (videoControlsController != null) {
                videoControlsController.onHostStatusChanged(true);
            }
        } else {
            hostLabel.setText("Participant");
            hostLabel.setStyle("-fx-text-fill: #3498db; -fx-font-weight: bold;");
            if (recordButton != null) recordButton.setDisable(true);

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
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1), e -> updateTimer())
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
        if (audioButton != null) {
            if (audioMuted) {
                audioButton.setText("Unmute");
                audioButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 5;");
            } else {
                audioButton.setText("Mute");
                audioButton.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-background-radius: 5;");
            }
        }

        if (videoButton != null) {
            if (videoOn) {
                videoButton.setText("Stop Video");
                videoButton.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-background-radius: 5;");
            } else {
                videoButton.setText("Start Video");
                videoButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 5;");
            }
        }

        if (recordButton != null) {
            boolean isRecordingActive = false;

            if (mp4RecordingController != null) {
                isRecordingActive = mp4RecordingController.isRecording();
            }
            if (!isRecordingActive) {
                isRecordingActive = recording;
            }

            if (isRecordingActive) {
                recordButton.setText("Stop Recording");
                recordButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 5; -fx-font-weight: bold;");
                recordButton.setTooltip(new Tooltip("Click to stop recording"));
            } else {
                recordButton.setText("Start Recording");
                recordButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-background-radius: 5;");
                recordButton.setTooltip(new Tooltip("Click to start recording (Host only)"));
            }

            recordButton.setDisable(!HelloApplication.isMeetingHost());
        }

        if (participantsButton != null) {
            if (participantsVisible) {
                participantsButton.setText("Hide Participants");
                participantsButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-background-radius: 5;");
            } else {
                participantsButton.setText("Show Participants");
                participantsButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-background-radius: 5;");
            }
        }

        if (chatButton != null) {
            if (chatVisible) {
                chatButton.setText("Hide Chat");
                chatButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-background-radius: 5;");
            } else {
                chatButton.setText("Show Chat");
                chatButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-background-radius: 5;");
            }
        }

        if (audioControlsButton != null) {
            if (audioControlsVisible) {
                audioControlsButton.setText("Hide Audio Controls");
                audioControlsButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-background-radius: 5;");
            } else {
                audioControlsButton.setText("Show Audio Controls");
                audioControlsButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-background-radius: 5;");
            }
        }

        if (videoControlsButton != null) {
            if (videoControlsVisible) {
                videoControlsButton.setText("Hide Video Controls");
                videoControlsButton.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white; -fx-background-radius: 5;");
            } else {
                videoControlsButton.setText("Show Video Controls");
                videoControlsButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-background-radius: 5;");
            }
        }

        if (screenSizeButton != null) {
            if (isFullscreen) {
                screenSizeButton.setText("Fullscreen");
            } else {
                screenSizeButton.setText(currentScreenSize.displayName);
            }
        }

        if (toggleFullscreenButton != null) {
            if (isFullscreen) {
                toggleFullscreenButton.setText("Exit Fullscreen");
                toggleFullscreenButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");
            } else {
                toggleFullscreenButton.setText("Fullscreen");
                toggleFullscreenButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
            }
        }

        if (shareButton != null) {
            shareButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-background-radius: 5;");
        }
        if (leaveButton != null) {
            leaveButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5;");
        }

        setupWindowControlHoverEffects();
    }

    private void setupWindowControlHoverEffects() {
        if (minimizeButton == null || maximizeButton == null || closeButton == null) return;

        minimizeButton.setOnMouseEntered(e -> minimizeButton.setStyle("-fx-background-color: #5a6c7d; -fx-text-fill: white; -fx-border-color: transparent;"));
        minimizeButton.setOnMouseExited(e -> minimizeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-border-color: transparent;"));

        maximizeButton.setOnMouseEntered(e -> maximizeButton.setStyle("-fx-background-color: #5a6c7d; -fx-text-fill: white; -fx-border-color: transparent;"));
        maximizeButton.setOnMouseExited(e -> maximizeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-border-color: transparent;"));

        closeButton.setOnMouseEntered(e -> closeButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-border-color: transparent;"));
        closeButton.setOnMouseExited(e -> closeButton.setStyle("-fx-background-color: transparent; -fx-text-fill: white; -fx-border-color: transparent;"));
    }

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

    @FXML
    protected void onToggleRecording() {
        if (!HelloApplication.isMeetingHost()) {
            showAlert("Permission Denied", "Only the meeting host can start recordings.");
            return;
        }

        System.out.println("Recording toggle clicked - Current state:");
        System.out.println("Basic recording: " + recording);
        System.out.println("Advanced recording active: " + (mp4RecordingController != null));
        System.out.println("Advanced recording running: " + (mp4RecordingController != null && mp4RecordingController.isRecording()));

        if (mp4RecordingController != null && mp4RecordingController.isRecording()) {
            System.out.println("Stopping advanced recording...");
            mp4RecordingController.onStopRecording();
            mp4RecordingController = null;
            updateButtonStyles();
            addSystemMessage("Advanced recording stopped");
            return;
        }

        if (recording) {
            System.out.println("Stopping basic recording...");
            stopBasicRecording();
            return;
        }

        System.out.println("Starting new recording...");

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

    private void openAdvancedRecordingControls() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("recording-controls.fxml"));
            VBox recordingControls = loader.load();

            mp4RecordingController = loader.getController();

            Stage recordingStage = new Stage();
            recordingStage.setTitle("Advanced Meeting Recording - MP4");

            recordingStage.initStyle(StageStyle.UNDECORATED);

            recordingStage.setScene(new Scene(recordingControls, 550, 700));
            recordingStage.setResizable(false);

            if (stage != null) {
                recordingStage.initOwner(stage);
            }

            mp4RecordingController.setStage(recordingStage);

            enablePopupDragging(recordingControls, recordingStage);

            recordingStage.setOnCloseRequest(e -> {
                System.out.println("Recording window closed");
                if (mp4RecordingController != null && mp4RecordingController.isRecording()) {
                    mp4RecordingController.onStopRecording();
                }
                mp4RecordingController = null;
                updateButtonStyles();
            });

            recordingStage.centerOnScreen();
            recordingStage.show();

            updateButtonStyles();

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Advanced Recording", "Advanced recording features not available. Using basic recording.");
            startBasicRecording();
        }
    }

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
                System.out.println("Recordings directory created: " + created);
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
            System.out.println("Basic recording started: " + currentRecordingFile.getAbsolutePath());

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
                addSystemMessage("Basic recording saved: " + currentRecordingFile.getName());
                System.out.println("Basic recording stopped: " + currentRecordingFile.getAbsolutePath());

                currentRecordingFile = null;
            } else {
                System.err.println("No active recording file found to stop");
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
                new FileChooser.ExtensionFilter("Video", "*.mp4", "*.mov", "*.avi"),
                new FileChooser.ExtensionFilter("Documents", "*.pdf", "*.doc", "*.docx", "*.txt")
        );

        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            try {
                long fileSize = file.length();
                long maxSize = 100 * 1024 * 1024;

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

                boolean transferStarted = fileTransferHandler.startFileTransfer(file, meetingId, username);

                if (transferStarted) {
                    addSystemMessage("Started sending file: " + file.getName() + " (" + formatFileSize(fileSize) + ")");
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

                String meetingId = HelloApplication.getActiveMeetingId();
                if (meetingId != null) {
                    boolean cleared = Database.clearChatMessages(meetingId);
                    if (cleared) {
                        System.out.println("Chat cleared from database");
                    } else {
                        System.err.println("Failed to clear chat from database");
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
                    new Label("Image: " + file.getName() + " (" + formatFileSize(file.length()) + ")"),
                    imgView
            );
            if (chatBox != null) {
                chatBox.getChildren().add(imageContainer);
            }
        } catch (Exception e) {
            System.err.println("Error displaying image: " + e.getMessage());
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

            HBox controls = new HBox(5);
            controls.setAlignment(Pos.CENTER);

            Button playButton = new Button("Play");
            playButton.setOnAction(e -> player.play());

            Button pauseButton = new Button("Pause");
            pauseButton.setOnAction(e -> player.pause());

            Button stopButton = new Button("Stop");
            stopButton.setOnAction(e -> player.stop());

            controls.getChildren().addAll(playButton, pauseButton, stopButton);

            if (currentMediaPlayer != null) {
                currentMediaPlayer.stop();
            }
            currentMediaPlayer = player;

            VBox mediaContainer = new VBox(5);
            mediaContainer.getChildren().addAll(
                    new Label(isVideo(file) ? "Video: " + file.getName() : "Audio: " + file.getName()),
                    mediaView,
                    controls
            );
            if (chatBox != null) {
                chatBox.getChildren().add(mediaContainer);
            }

        } catch (Exception e) {
            System.err.println("Error displaying media: " + e.getMessage());
            displayFileLink(file);
        }
    }

    private void displayFileLink(File file) {
        Hyperlink fileLink = new Hyperlink("File: " + file.getName() + " (" + formatFileSize(file.length()) + ")");
        fileLink.setStyle("-fx-text-fill: #3498db; -fx-border-color: transparent;");
        fileLink.setOnAction(e -> downloadFile(file));
        if (chatBox != null) {
            chatBox.getChildren().add(fileLink);
        }
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    @FXML
    protected void onViewRecordings() throws Exception {
        HelloApplication.setRoot("recordings-view.fxml");
    }

    @FXML
    protected void onLeaveClick() throws Exception {
        System.out.println("Leaving meeting...");

        if (currentMediaPlayer != null) {
            currentMediaPlayer.stop();
            currentMediaPlayer = null;
        }

        if (recording) {
            System.out.println("Stopping basic recording before leaving...");
            stopBasicRecording();
        }

        if (mp4RecordingController != null && mp4RecordingController.isRecording()) {
            System.out.println("Stopping advanced recording before leaving...");
            mp4RecordingController.onStopRecording();
            mp4RecordingController = null;
        }

        if (meetingTimer != null) {
            meetingTimer.stop();
            meetingTimer = null;
        }

        cleanupAudioVideoResources();

        if (fileTransferHandler != null) {
            fileTransferHandler.cleanup();
        }

        if (audioControlsController != null) {
            audioControlsController.onMeetingStateChanged(false);
        }
        if (videoControlsController != null) {
            videoControlsController.onMeetingStateChanged(false);
        }

        String username = HelloApplication.getLoggedInUser();
        String meetingId = HelloApplication.getActiveMeetingId();

        if (HelloApplication.isWebSocketConnected() && meetingId != null) {
            HelloApplication.sendWebSocketMessage(
                    "USER_LEFT",
                    meetingId,
                    username,
                    "left the meeting"
            );

            // Also send video stopped if we were streaming
            if (videoOn) {
                HelloApplication.sendWebSocketMessage(
                        "VIDEO_STATUS",
                        meetingId,
                        username,
                        "VIDEO_STOPPED"
                );
            }
        }

        HelloApplication.leaveCurrentMeeting();
        if (stage != null) {
            stage.setMaximized(false);
        }
        HelloApplication.getPrimaryStage().setFullScreen(false);
        HelloApplication.setRoot("dashboard-view.fxml");
    }

    private void cleanupDirectHardwareAccess() {
        try {
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
                        System.out.println("Camera closed: " + webcam.getClass().getMethod("getName").invoke(webcam));
                    }
                }
            } catch (ClassNotFoundException e) {
            }

            try {
                Class<?> audioSystemClass = Class.forName("javax.sound.sampled.AudioSystem");
            } catch (ClassNotFoundException e) {
            }

        } catch (Exception e) {
            System.err.println("Error during hardware cleanup: " + e.getMessage());
        }
    }

    @FXML
    protected void onShareScreen(ActionEvent event) {
        try {
            addSystemMessage("Opening screen share...");
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

    private void setupFallbackAudioControls() {
        System.out.println("Setting up fallback audio controls");
        if (audioControlsContainer != null) {
            HBox fallbackAudioControls = new HBox(10);
            fallbackAudioControls.setAlignment(Pos.CENTER_LEFT);
            fallbackAudioControls.setStyle("-fx-padding: 10; -fx-background-color: #2c3e50; -fx-border-radius: 5;");

            Button muteButton = new Button("Mute");
            muteButton.setOnAction(e -> toggleAudio());
            muteButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");

            Button deafenButton = new Button("Deafen");
            deafenButton.setOnAction(e -> toggleDeafen());
            deafenButton.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white;");

            fallbackAudioControls.getChildren().addAll(muteButton, deafenButton);
            audioControlsContainer.getChildren().add(fallbackAudioControls);
        }
    }

    private void setupFallbackVideoControls() {
        System.out.println("Setting up fallback video controls");
        if (videoControlsContainer != null) {
            HBox fallbackVideoControls = new HBox(10);
            fallbackVideoControls.setAlignment(Pos.CENTER_LEFT);
            fallbackVideoControls.setStyle("-fx-padding: 10; -fx-background-color: #2c3e50; -fx-border-radius: 5;");

            Button videoToggle = new Button("Start Video");
            videoToggle.setOnAction(e -> toggleVideo());
            videoToggle.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white;");

            Button recordButton = new Button("Record");
            recordButton.setOnAction(e -> onToggleRecording());
            recordButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white;");

            fallbackVideoControls.getChildren().addAll(videoToggle, recordButton);
            videoControlsContainer.getChildren().add(fallbackVideoControls);
        }
    }

    private void setupAllFallbackControls() {
        System.out.println("Setting up all fallback controls");
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

    @FXML
    protected void toggleAudioControls() {
        if (audioControlsContainer != null) {
            audioControlsVisible = !audioControlsVisible;
            audioControlsContainer.setVisible(audioControlsVisible);
            audioControlsContainer.setManaged(audioControlsVisible);
            System.out.println("Audio controls " + (audioControlsVisible ? "shown" : "hidden"));

            if (audioControlsButton != null) {
                if (audioControlsVisible) {
                    audioControlsButton.setText("Hide Audio Controls");
                    audioControlsButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
                } else {
                    audioControlsButton.setText("Show Audio Controls");
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
            System.out.println("Video controls " + (videoControlsVisible ? "shown" : "hidden"));

            if (videoControlsButton != null) {
                if (videoControlsVisible) {
                    videoControlsButton.setText("Hide Video Controls");
                    videoControlsButton.setStyle("-fx-background-color: #2980b9; -fx-text-fill: white;");
                } else {
                    videoControlsButton.setText("Show Video Controls");
                    videoControlsButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white;");
                }
            }
        }
    }

    @FXML
    protected void toggleDeafen() {
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
        System.out.println("Using fallback audio/video controls");
        if (audioButton != null) audioButton.setVisible(true);
        if (videoButton != null) videoButton.setVisible(true);
        if (recordButton != null) recordButton.setVisible(true);
    }

    private void showCameraError() {
        Platform.runLater(() -> {
            if (videoDisplay != null) {
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

    public AudioControlsController getAudioControlsController() {
        return audioControlsController;
    }

    public static boolean connectToServer(String serverUrl) {
        try {
            System.out.println("Attempting to connect to: " + serverUrl);

            HelloApplication.MeetingInfo.initializeWebSocketConnection(serverUrl);

            int attempts = 0;
            while (attempts < 10 && !HelloApplication.isWebSocketConnected()) {
                Thread.sleep(500);
                attempts++;
            }

            if (HelloApplication.isWebSocketConnected()) {
                System.out.println("Successfully connected to: " + serverUrl);
                return true;
            } else {
                System.out.println("Failed to connect to: " + serverUrl);
                return false;
            }

        } catch (Exception e) {
            System.err.println("Connection error: " + e.getMessage());
            return false;
        }
    }

    public VideoControlsController getVideoControlsController() {
        return videoControlsController;
    }

    public static MeetingController getInstance() {
        return instance;
    }

    public void setVideoQuality(VideoQuality quality) {
        this.currentVideoQuality = quality;
        if (webcam != null && webcam.isOpen()) {
            try {
                webcam.setViewSize(new java.awt.Dimension(quality.width, quality.height));
                System.out.println("Video quality changed to: " + quality.name());
            } catch (Exception e) {
                System.err.println("Failed to change video quality: " + e.getMessage());
            }
        }
    }

    public VideoQuality getCurrentVideoQuality() {
        return currentVideoQuality;
    }

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

    // Simple class to track file transfer info
    private class FileTransferInfo {
        String fileId;
        String fileName;
        long fileSize;
        StringBuilder fileData;

        public FileTransferInfo(String fileId, String fileName, long fileSize) {
            this.fileId = fileId;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.fileData = new StringBuilder();
        }
    }
}