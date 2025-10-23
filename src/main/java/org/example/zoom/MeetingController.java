package org.example.zoom;

import javafx.fxml.FXML;
import javafx.event.ActionEvent;
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.text.SimpleDateFormat;
import java.util.Date;

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

    private boolean audioMuted = false;
    private boolean videoOn = true;
    private boolean recording = false;
    private boolean participantsVisible = true;
    private boolean chatVisible = true;
    private File currentRecordingFile;
    private Stage stage;
    private MediaPlayer currentMediaPlayer;

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
        updateParticipantsList();
        setupChat();
        updateMeetingInfo();
        updateButtonStyles();
        startMeetingTimer();

        // Get the stage from the scene
        Platform.runLater(() -> {
            Stage currentStage = (Stage) chatBox.getScene().getWindow();
            if (currentStage != null) {
                setStage(currentStage);
            }
        });
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
            recordButton.setDisable(false);
        } else {
            hostLabel.setText("👤 Participant");
            hostLabel.setStyle("-fx-text-fill: #3498db; -fx-font-weight: bold;");
            recordButton.setDisable(true);
        }
    }

    private void updateParticipantsList() {
        participantsList.getItems().clear();
        participantsList.getItems().addAll(HelloApplication.getActiveParticipants());

        // Update participants count
        int count = HelloApplication.getActiveParticipants().size();
        participantsCountLabel.setText("Participants: " + count);

        // Add host indicator
        if (!participantsList.getItems().isEmpty() && HelloApplication.isMeetingHost()) {
            String firstParticipant = participantsList.getItems().get(0);
            if (!firstParticipant.contains("👑")) {
                participantsList.getItems().set(0, "👑 " + firstParticipant + " (Host)");
            }
        }
    }

    private void setupChat() {
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

        if (hours > 0) {
            timerLabel.setText(String.format("Time: %02d:%02d:%02d", hours, minutes, seconds));
        } else {
            timerLabel.setText(String.format("Time: %02d:%02d", minutes, seconds));
        }
    }

    private void updateButtonStyles() {
        // Update audio button
        if (audioMuted) {
            audioButton.setText("🔇 Unmute");
            audioButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 5;");
        } else {
            audioButton.setText("🎤 Mute");
            audioButton.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-background-radius: 5;");
        }

        // Update video button
        if (videoOn) {
            videoButton.setText("📹 Stop Video");
            videoButton.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-background-radius: 5;");
        } else {
            videoButton.setText("📹 Start Video");
            videoButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 5;");
        }

        // Update record button
        if (recording) {
            recordButton.setText("🔴 Stop Recording");
            recordButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-background-radius: 5;");
        } else {
            recordButton.setText("⏺ Start Recording");
            recordButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-background-radius: 5;");
        }

        // Update panel toggle buttons
        if (participantsVisible) {
            participantsButton.setText("👥 Hide Participants");
            participantsButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-background-radius: 5;");
        } else {
            participantsButton.setText("👥 Show Participants");
            participantsButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-background-radius: 5;");
        }

        if (chatVisible) {
            chatButton.setText("💬 Hide Chat");
            chatButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-background-radius: 5;");
        } else {
            chatButton.setText("💬 Show Chat");
            chatButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white; -fx-background-radius: 5;");
        }

        // Style other buttons
        shareButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-background-radius: 5;");
        leaveButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 5;");

        // Add hover effects for window controls
        setupWindowControlHoverEffects();
    }

    private void setupWindowControlHoverEffects() {
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
        participantsPanel.setVisible(participantsVisible);
        participantsPanel.setManaged(participantsVisible);
        updateButtonStyles();
    }

    @FXML
    protected void toggleChatPanel() {
        chatVisible = !chatVisible;
        chatPanel.setVisible(chatVisible);
        chatPanel.setManaged(chatVisible);
        updateButtonStyles();
    }

    // ---------------- Audio / Video ----------------
    @FXML
    protected void toggleAudio() {
        audioMuted = !audioMuted;
        updateButtonStyles();

        if (audioMuted) {
            addSystemMessage("You muted your audio");
        } else {
            addSystemMessage("You unmuted your audio");
        }
    }

    @FXML
    protected void toggleVideo() {
        videoOn = !videoOn;
        updateButtonStyles();

        if (videoOn) {
            addSystemMessage("You started your video");
        } else {
            addSystemMessage("You stopped your video");
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
        String msg = chatInput.getText().trim();
        if (!msg.isEmpty()) {
            String username = HelloApplication.getLoggedInUser();
            if (username == null) username = "Me";

            addUserMessage(username + ": " + msg);
            chatInput.clear();

            // Auto-reply removed - chat is now manual only
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
        chatBox.getChildren().clear();
        addSystemMessage("Chat cleared");
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
        chatBox.getChildren().add(imageContainer);
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
            chatBox.getChildren().add(mediaContainer);

        } catch (Exception e) {
            displayFileLink(file);
        }
    }

    private void displayFileLink(File file) {
        Hyperlink fileLink = new Hyperlink("📎 " + file.getName() + " (" + getFileSize(file) + ")");
        fileLink.setStyle("-fx-text-fill: #3498db; -fx-border-color: transparent;");
        fileLink.setOnAction(e -> downloadFile(file));
        chatBox.getChildren().add(fileLink);
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

        // Leave meeting
        HelloApplication.leaveCurrentMeeting();
        if (stage != null) {
            stage.setMaximized(false);
        }
        HelloApplication.getPrimaryStage().setFullScreen(false);
        HelloApplication.setRoot("dashboard-view.fxml");
    }

    @FXML
    protected void onShareScreen(ActionEvent event) {
        addSystemMessage("🖥 Screen sharing feature is under development...");
        showAlert("Screen Share", "Screen sharing will be available in the next update!");
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
}