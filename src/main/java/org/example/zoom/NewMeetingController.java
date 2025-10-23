package org.example.zoom;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;

public class NewMeetingController {

    @FXML
    private TextField meetingIdField;

    @FXML
    private Label statusLabel;

    private String meetingId;

    @FXML
    public void initialize() {
        // Create a new meeting using HelloApplication's system
        meetingId = HelloApplication.createNewMeeting();
        meetingIdField.setText(meetingId);

        statusLabel.setText("✅ Meeting ID generated! Share it with participants.");
    }

    @FXML
    protected void onStartMeetingClick() throws Exception {
        // Add host as first participant with actual username
        String username = HelloApplication.getLoggedInUser();
        if (username == null || username.isEmpty()) {
            username = "Host";
        }
        HelloApplication.addParticipant(username);

        statusLabel.setText("✅ Meeting started with ID: " + meetingId);

        // Notify via WebSocket that meeting was created
        // This is now handled by HelloApplication.createNewMeeting()

        // Navigate directly to meeting view as host
        HelloApplication.setRoot("meeting-view.fxml");
    }

    @FXML
    protected void onBackClick() throws Exception {
        // Clear the meeting if we're going back without starting
        HelloApplication.leaveCurrentMeeting();
        HelloApplication.setRoot("dashboard-view.fxml");
    }

    @FXML
    protected void onCopyIdClick() {
        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(meetingId);
        clipboard.setContent(content);
        statusLabel.setText("✅ Meeting ID copied! Participants can paste it to join.");
    }

    @FXML
    protected void onTestJoinClick() throws Exception {
        // Test joining the same meeting
        onCopyIdClick(); // Copy ID first

        // Set up for joining as a test participant
        HelloApplication.setMeetingHost(false); // Not host for test join

        // Navigate to join view
        HelloApplication.setRoot("join-view.fxml");
    }
}