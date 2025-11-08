package org.example.zoom;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.control.ScrollPane;

public class NewMeetingController {

    @FXML
    private TextField meetingIdField;

    @FXML
    private Label statusLabel;

    @FXML
    private ScrollPane mainScrollPane;

    private String meetingId;

    @FXML
    public void initialize() {
        // Configure scroll pane
        if (mainScrollPane != null) {
            mainScrollPane.setFitToWidth(true);
            mainScrollPane.setFitToHeight(true);
            mainScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            mainScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            mainScrollPane.setStyle("-fx-background: #2c3e50; -fx-border-color: #2c3e50;");
        }

        // Create a new meeting using HelloApplication's system
        meetingId = HelloApplication.createNewMeeting();
        meetingIdField.setText(meetingId);

        // Store the meeting ID globally so JoinController can access it
        HelloApplication.setActiveMeetingId(meetingId);

        statusLabel.setText("✅ Meeting ID generated! Share it with participants.");
        System.out.println("🎯 New Meeting Controller: Meeting ID " + meetingId + " is ready for joining!");
    }

    @FXML
    protected void onStartMeetingClick() throws Exception {
        // Check WebSocket connection before starting meeting
        if (!HelloApplication.isWebSocketConnected()) {
            statusLabel.setText("❌ Cannot start meeting - no server connection. Please check your connection first.");
            return;
        }

        // Add host as first participant with actual username
        String username = HelloApplication.getLoggedInUser();
        if (username == null || username.isEmpty()) {
            username = "Host";
        }
        HelloApplication.addParticipant(username);

        statusLabel.setText("✅ Meeting started with ID: " + meetingId);

        // Send meeting created message via WebSocket
        if (HelloApplication.isWebSocketConnected()) {
            HelloApplication.sendWebSocketMessage("MEETING_CREATED", meetingId, "Meeting created by " + username);
        }

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

        statusLabel.setText("🔄 Testing join functionality...");

        // Navigate to join view - the meeting ID will be automatically detected
        HelloApplication.setRoot("join-view.fxml");
    }

    @FXML
    protected void onShareInstructionsClick() {
        String shareText = "Join my Zoom meeting!\nMeeting ID: " + meetingId + "\nUse this ID in the Join Meeting section.";

        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(shareText);
        clipboard.setContent(content);

        statusLabel.setText("✅ Meeting details copied! Ready to share with participants.");
    }
}