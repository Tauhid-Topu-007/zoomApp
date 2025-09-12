package org.example.zoom;

import javafx.fxml.FXML;
import javafx.scene.control.*;

public class MeetingController {

    @FXML private Button audioButton;
    @FXML private Button videoButton;
    @FXML private Button shareButton;
    @FXML private ListView<String> participantsList;
    @FXML private TextArea chatArea;
    @FXML private TextField chatInput;

    private boolean audioMuted = false;
    private boolean videoOn = true;

    @FXML
    public void initialize() {
        // Sample participants
        participantsList.getItems().addAll("Alice", "Bob", "Charlie");

        // Send chat when Enter key is pressed
        chatInput.setOnAction(event -> onSendChat());
    }

    @FXML
    protected void onLeaveClick() throws Exception {
        // Exit fullscreen when leaving meeting
        HelloApplication.getPrimaryStage().setFullScreen(false);
        HelloApplication.setRoot("dashboard-view.fxml");
    }

    @FXML
    protected void onSendChat() {
        String msg = chatInput.getText();
        if (!msg.isEmpty()) {
            chatArea.appendText("Me: " + msg + "\n");
            chatInput.clear();
        }
    }

    @FXML
    protected void toggleAudio() {
        audioMuted = !audioMuted;
        audioButton.setText(audioMuted ? "Unmute" : "Mute");
    }

    @FXML
    protected void toggleVideo() {
        videoOn = !videoOn;
        videoButton.setText(videoOn ? "Camera Off" : "Camera On");
        // TODO: integrate actual camera view
    }

    @FXML
    protected void onShareScreen() {
        chatArea.appendText("📺 Screen sharing started...\n");
        // TODO: integrate actual screen sharing
    }
}
