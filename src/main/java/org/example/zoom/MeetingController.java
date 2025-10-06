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
    @FXML private ListView<String> participantsList;
    @FXML private StackPane videoArea;
    @FXML private VBox chatBox;
    @FXML private ScrollPane chatScroll;
    @FXML private TextField chatInput;

    private boolean audioMuted = false;
    private boolean videoOn = true;
    private boolean recording = false;
    private File currentRecordingFile;
    private Stage stage;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    public void initialize() {
        participantsList.getItems().clear();
        participantsList.getItems().addAll(HelloApplication.getActiveParticipants());

        chatInput.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ENTER -> onSendChat();
            }
        });
    }

    // ---------------- Audio / Video ----------------
    @FXML
    protected void toggleAudio() {
        audioMuted = !audioMuted;
        audioButton.setText(audioMuted ? "Unmute" : "Mute");
    }

    @FXML
    protected void toggleVideo() {
        videoOn = !videoOn;
        videoButton.setText(videoOn ? "Camera Off" : "Camera On");
    }

    // ---------------- Recording ----------------
    @FXML
    protected void onToggleRecording() {
        if (!recording) {
            startRecording();
        } else {
            stopRecording();
        }
    }

    private void startRecording() {
        try {
            String fileName = "Meeting_" + new SimpleDateFormat("yyyy-MM-dd_HHmmss").format(new Date()) + ".txt";
            File dir = new File("recordings");
            if (!dir.exists()) dir.mkdirs();

            currentRecordingFile = new File(dir, fileName);
            FileWriter writer = new FileWriter(currentRecordingFile);
            writer.write("Simulated recording started...\n");
            writer.close();

            recording = true;
            recordButton.setText("Stop Recording");
            addChatMessage("🔴 Recording started...", "#e74c3c", "white");

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Error", "Failed to start recording!");
        }
    }

    private void stopRecording() {
        try {
            FileWriter writer = new FileWriter(currentRecordingFile, true);
            writer.write("Recording stopped.\n");
            writer.close();

            recording = false;
            recordButton.setText("Start Recording");
            addChatMessage("✅ Recording saved: " + currentRecordingFile.getName(), "#2ecc71", "white");

        } catch (IOException e) {
            e.printStackTrace();
            showAlert("Error", "Failed to stop recording!");
        }
    }

    // ---------------- Chat ----------------
    @FXML
    protected void onSendChat() {
        String msg = chatInput.getText().trim();
        if (!msg.isEmpty()) {
            addChatMessage("Me: " + msg, "#3498db", "white");
            chatInput.clear();
        }
    }

    @FXML
    protected void onSendFile() {
        if (stage == null) stage = (Stage) chatBox.getScene().getWindow();

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Send");
        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            try {
                if (isImage(file)) {
                    ImageView imgView = new ImageView(new Image(file.toURI().toString()));
                    imgView.setFitWidth(150);
                    imgView.setPreserveRatio(true);
                    imgView.setOnMouseClicked(e -> downloadFile(file));
                    chatBox.getChildren().add(imgView);

                } else if (isAudio(file) || isVideo(file)) {
                    Media media = new Media(file.toURI().toString());
                    MediaPlayer player = new MediaPlayer(media);
                    MediaView mediaView = new MediaView(player);
                    mediaView.setFitWidth(200);
                    mediaView.setPreserveRatio(true);
                    mediaView.setOnMouseClicked(e -> downloadFile(file));
                    chatBox.getChildren().add(mediaView);
                    player.play();

                } else {
                    Hyperlink fileLink = new Hyperlink("📎 " + file.getName());
                    fileLink.setOnAction(e -> downloadFile(file));
                    chatBox.getChildren().add(fileLink);
                }

                scrollToBottom();
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private void addChatMessage(String text, String bgColor, String textColor) {
        Label messageLabel = new Label(text);
        messageLabel.setStyle(String.format(
                "-fx-background-color: %s; -fx-text-fill: %s; -fx-padding: 6 10; -fx-background-radius: 8;",
                bgColor, textColor));
        chatBox.getChildren().add(messageLabel);
        scrollToBottom();
    }

    private void scrollToBottom() {
        chatScroll.layout();
        chatScroll.setVvalue(1.0);
    }

    // ---------------- Helpers ----------------
    private boolean isImage(File f) {
        String n = f.getName().toLowerCase();
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
    }

    private boolean isAudio(File f) {
        String n = f.getName().toLowerCase();
        return n.endsWith(".mp3") || n.endsWith(".wav");
    }

    private boolean isVideo(File f) {
        String n = f.getName().toLowerCase();
        return n.endsWith(".mp4") || n.endsWith(".mov") || n.endsWith(".m4v");
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
                showAlert("Download", "File downloaded successfully!");
            } catch (IOException e) {
                showAlert("Error", "Failed to download file!");
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
        HelloApplication.getPrimaryStage().setFullScreen(false);
        HelloApplication.setRoot("dashboard-view.fxml");
    }

    @FXML
    protected void onShareScreen(ActionEvent event) {
        System.out.println("🖥 Screen sharing feature is under development.");
    }

}
