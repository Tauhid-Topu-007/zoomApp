package org.example.zoom;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.layout.StackPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class MeetingController {

    @FXML private Button audioButton;
    @FXML private Button videoButton;
    @FXML private Button shareButton;
    @FXML private ListView<String> participantsList;
    @FXML private StackPane videoArea;

    @FXML private VBox chatBox;
    @FXML private ScrollPane chatScroll;
    @FXML private TextField chatInput;

    private boolean audioMuted = false;
    private boolean videoOn = true;
    private Stage stage;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    public void initialize() {
        // Load participants dynamically from HelloApplication
        participantsList.getItems().clear();
        participantsList.getItems().addAll(HelloApplication.getActiveParticipants());

        // Enter key sends chat
        chatInput.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ENTER -> onSendChat();
            }
        });
    }


    // ---------------- Video / Audio ----------------
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

    @FXML
    protected void onShareScreen() {
        Label screenLabel = new Label("📺 Screen sharing started...");
        chatBox.getChildren().add(screenLabel);
        scrollToBottom();
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
        if (stage == null) {
            stage = (Stage) chatBox.getScene().getWindow();
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select File to Send");
        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            String fileName = file.getName();
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

                } else { // PDF / text / other
                    Hyperlink fileLink = new Hyperlink("📎 " + fileName);
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
        messageLabel.setStyle(String.format("-fx-background-color: %s; -fx-text-fill: %s; -fx-padding: 6 10; -fx-background-radius: 8;", bgColor, textColor));
        chatBox.getChildren().add(messageLabel);
        scrollToBottom();
    }

    private void scrollToBottom() {
        chatScroll.layout();
        chatScroll.setVvalue(1.0);
    }

    // ---------------- File Type Helpers ----------------
    private boolean isImage(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".png") || name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".gif");
    }

    private boolean isAudio(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".mp3") || name.endsWith(".wav");
    }

    private boolean isVideo(File file) {
        String name = file.getName().toLowerCase();
        return name.endsWith(".mp4") || name.endsWith(".mov") || name.endsWith(".m4v");
    }

    private void downloadFile(File sourceFile) {
        if (stage == null) {
            stage = (Stage) chatBox.getScene().getWindow();
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save File As");
        fileChooser.setInitialFileName(sourceFile.getName());
        File destinationFile = fileChooser.showSaveDialog(stage);

        if (destinationFile != null) {
            try {
                Files.copy(sourceFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "File downloaded successfully!");
                alert.showAndWait();
            } catch (IOException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Error downloading file: " + ex.getMessage());
                alert.showAndWait();
            }
        }
    }

    // ---------------- Leave Meeting ----------------
    @FXML
    protected void onLeaveClick() throws Exception {
        HelloApplication.getPrimaryStage().setFullScreen(false);
        HelloApplication.setRoot("dashboard-view.fxml");
    }
}
