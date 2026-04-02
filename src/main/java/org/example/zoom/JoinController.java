package org.example.zoom;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;

import java.util.List;
import java.util.Optional;

public class JoinController {

    @FXML private TextField meetingIdField;
    @FXML private TextField nameField;
    @FXML private Label statusLabel;

    @FXML
    public void initialize() {
        checkForMeetingId();
        setupKeyHandlers();

        System.out.println("Join Controller initialized");
        System.out.println("Current user: " + HelloApplication.getLoggedInUser());
        System.out.println("WebSocket connected: " + HelloApplication.isWebSocketConnected());

        // Request meeting list from server
        if (HelloApplication.isWebSocketConnected()) {
            System.out.println("Requesting meeting list from server...");
            HelloApplication.refreshMeetingsFromServer();

            if (HelloApplication.getWebSocketClient() != null) {
                HelloApplication.getWebSocketClient().send("GET_ALL_MEETINGS");
            }
        }

        // List all active meetings
        System.out.println("Active meetings in system: " + HelloApplication.getActiveMeetings().keySet());

        // Check database for meetings
        System.out.println("Checking database for meetings...");
        List<Database.MeetingInfo> dbMeetings = Database.getAllMeetingsFromDB();
        for (Database.MeetingInfo meeting : dbMeetings) {
            System.out.println("Database meeting: " + meeting.getMeetingId() + " hosted by " + meeting.getHost());
        }

        // Update status
        if (HelloApplication.getActiveMeetings().isEmpty() && dbMeetings.isEmpty()) {
            statusLabel.setText("No meetings found. Please enter a valid Meeting ID or create one.");
        } else {
            statusLabel.setText("Enter Meeting ID and Name to join");
        }
    }

    private void checkForMeetingId() {
        String activeMeetingId = HelloApplication.getActiveMeetingId();
        if (activeMeetingId != null && !activeMeetingId.isEmpty()) {
            meetingIdField.setText(activeMeetingId);
            statusLabel.setText("Meeting ID detected! Enter your name to join.");
            System.out.println("Auto-filled meeting ID: " + activeMeetingId);
        }
    }

    private void setupKeyHandlers() {
        meetingIdField.setOnKeyPressed(this::handleKeyPress);
        nameField.setOnKeyPressed(this::handleKeyPress);
    }

    private void handleKeyPress(KeyEvent event) {
        if (event.getCode() == KeyCode.ENTER) {
            try {
                onJoinMeetingClick();
            } catch (Exception e) {
                e.printStackTrace();
                statusLabel.setText("Error joining meeting: " + e.getMessage());
            }
        }
    }

    @FXML
    protected void onJoinMeetingClick() throws Exception {
        String meetingId = meetingIdField.getText().trim();
        String name = nameField.getText().trim();

        if (meetingId.isEmpty() || name.isEmpty()) {
            statusLabel.setText("Please enter both Meeting ID and Name!");
            return;
        }

        if (!meetingId.matches("\\d{6}")) {
            statusLabel.setText("Meeting ID must be 6 digits!");
            return;
        }

        statusLabel.setText("Validating meeting...");

        String loggedInUser = HelloApplication.getLoggedInUser();
        String participantName = loggedInUser != null ? loggedInUser : name;

        System.out.println("Attempting to join meeting: " + meetingId + " as: " + participantName);
        System.out.println("WebSocket connected: " + HelloApplication.isWebSocketConnected());

        // Check if meeting exists
        boolean meetingExists = HelloApplication.isValidMeeting(meetingId);

        // If not found locally but WebSocket is connected, try server validation
        if (!meetingExists && HelloApplication.isWebSocketConnected()) {
            statusLabel.setText("Checking with server for meeting...");
            System.out.println("Meeting not found locally, requesting server validation");
            meetingExists = validateMeetingWithServer(meetingId);
        }

        // If still not found, try database directly
        if (!meetingExists) {
            System.out.println("Checking database directly for meeting: " + meetingId);
            meetingExists = Database.meetingExists(meetingId);

            if (meetingExists) {
                System.out.println("Meeting found in database!");
                String host = Database.getMeetingHost(meetingId);
                if (host != null) {
                    HelloApplication.syncMeetingFromServer(meetingId, host);
                }
            }
        }

        if (!meetingExists) {
            statusLabel.setText("Meeting not found! Check the ID or create a new meeting.");
            showAvailableMeetings();
            askToCreateNewMeeting();
            return;
        }

        System.out.println("Meeting validated successfully: " + meetingId);

        // Join the meeting
        boolean joined = HelloApplication.joinMeeting(meetingId, participantName);

        if (joined) {
            statusLabel.setText("Successfully joined meeting! Loading...");

            // Send WebSocket notification
            if (HelloApplication.isWebSocketConnected()) {
                String deviceId = HelloApplication.getDeviceId();
                String deviceName = HelloApplication.getDeviceName();
                String content = participantName + " joined the meeting|" + deviceId + "|" + deviceName;
                HelloApplication.sendWebSocketMessage("USER_JOINED", meetingId, participantName, content);
                System.out.println("Sent USER_JOINED notification for meeting: " + meetingId);
            }

            navigateToMeeting();
        } else {
            statusLabel.setText("Failed to join meeting. Please try again.");
        }
    }

    private boolean validateMeetingWithServer(String meetingId) {
        if (!HelloApplication.isWebSocketConnected()) {
            return false;
        }

        try {
            System.out.println("Validating meeting with server: " + meetingId);

            String loggedInUser = HelloApplication.getLoggedInUser();
            String sender = loggedInUser != null ? loggedInUser : "anonymous";

            HelloApplication.sendWebSocketMessage("VALIDATE_MEETING", meetingId, sender, meetingId);

            // Wait a bit for response
            Thread.sleep(2000);

            // Check again
            boolean exists = HelloApplication.isValidMeeting(meetingId);
            if (!exists) {
                exists = Database.meetingExists(meetingId);
            }

            return exists;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void showAvailableMeetings() {
        List<Database.MeetingInfo> dbMeetings = Database.getAllMeetingsFromDB();

        if (!dbMeetings.isEmpty()) {
            StringBuilder msg = new StringBuilder("Available meetings in database:\n");
            for (Database.MeetingInfo meeting : dbMeetings) {
                msg.append("- ").append(meeting.getMeetingId())
                        .append(" (Host: ").append(meeting.getHost())
                        .append(", Participants: ").append(meeting.getParticipantCount())
                        .append(")\n");
            }
            System.out.println(msg.toString());
        }

        if (!HelloApplication.getActiveMeetings().isEmpty()) {
            StringBuilder msg = new StringBuilder("Active meetings in memory:\n");
            for (String id : HelloApplication.getActiveMeetings().keySet()) {
                msg.append("- ").append(id).append("\n");
            }
            System.out.println(msg.toString());
        }
    }

    private void askToCreateNewMeeting() {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Meeting Not Found");
            alert.setHeaderText("Meeting ID: " + meetingIdField.getText().trim() + " was not found");
            alert.setContentText("Would you like to create a new meeting?");

            ButtonType createButton = new ButtonType("Create New Meeting");
            ButtonType tryAgainButton = new ButtonType("Try Again");
            ButtonType cancelButton = new ButtonType("Cancel", ButtonType.CANCEL.getButtonData());

            alert.getButtonTypes().setAll(createButton, tryAgainButton, cancelButton);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent()) {
                if (result.get() == createButton) {
                    try {
                        HelloApplication.setRoot("new-meeting-view.fxml");
                    } catch (Exception e) {
                        e.printStackTrace();
                        statusLabel.setText("Error creating meeting: " + e.getMessage());
                    }
                } else if (result.get() == tryAgainButton) {
                    statusLabel.setText("Please check the Meeting ID and try again");
                    meetingIdField.requestFocus();
                }
            }
        });
    }

    private void navigateToMeeting() {
        new Thread(() -> {
            try {
                Thread.sleep(1500);
                Platform.runLater(() -> {
                    try {
                        HelloApplication.setRoot("meeting-view.fxml");
                        System.out.println("Navigated to meeting view");
                    } catch (Exception e) {
                        e.printStackTrace();
                        statusLabel.setText("Error navigating to meeting: " + e.getMessage());
                    }
                });
            } catch (InterruptedException e) {
                e.printStackTrace();
                statusLabel.setText("Join process interrupted");
            }
        }).start();
    }

    @FXML
    protected void onCancelClick() throws Exception {
        HelloApplication.setRoot("dashboard-view.fxml");
    }

    @FXML
    protected void onQuickJoinClick() {
        String randomMeetingId = String.valueOf((int) (Math.random() * 900000) + 100000);
        meetingIdField.setText(randomMeetingId);

        HelloApplication.createMeetingForTesting(randomMeetingId);
        Database.saveMeetingWithId(randomMeetingId, HelloApplication.getLoggedInUser(),
                "Test Meeting " + randomMeetingId, "Quick join test meeting");

        statusLabel.setText("Quick Join ID: " + randomMeetingId + " - Enter your name!");
        nameField.requestFocus();
    }

    @FXML
    protected void onRefreshMeetingsClick() {
        statusLabel.setText("Refreshing meeting list...");

        if (HelloApplication.isWebSocketConnected()) {
            HelloApplication.refreshMeetingsFromServer();
            if (HelloApplication.getWebSocketClient() != null) {
                HelloApplication.getWebSocketClient().send("GET_ALL_MEETINGS");
            }
        }

        List<Database.MeetingInfo> meetings = Database.getAllMeetingsFromDB();
        if (!meetings.isEmpty()) {
            StringBuilder msg = new StringBuilder("Available meetings:\n");
            for (Database.MeetingInfo m : meetings) {
                msg.append("- ").append(m.getMeetingId())
                        .append(" (Host: ").append(m.getHost())
                        .append(")\n");
            }
            statusLabel.setText("Found " + meetings.size() + " meetings. Enter ID to join.");
            System.out.println(msg.toString());
        } else {
            statusLabel.setText("No meetings found. Enter a Meeting ID or create one.");
        }
    }

    @FXML
    protected void onPasteMeetingId() {
        statusLabel.setText("Paste your meeting ID in the field above");
    }
}