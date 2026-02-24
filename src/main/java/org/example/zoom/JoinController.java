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
        // Check if there's a meeting ID in clipboard or from previous session
        checkForMeetingId();

        // Set up proper key event handlers
        setupKeyHandlers();

        System.out.println("🔍 Join Controller initialized - checking for available meetings...");
        System.out.println("Current user: " + HelloApplication.getLoggedInUser());
        System.out.println("WebSocket connected: " + HelloApplication.isWebSocketConnected());

        // Request meeting list from server to sync
        if (HelloApplication.isWebSocketConnected()) {
            System.out.println("📋 Requesting meeting list from server...");
            HelloApplication.requestAllMeetings();

            // Also request all meetings via direct message
            if (HelloApplication.getWebSocketClient() != null) {
                HelloApplication.getWebSocketClient().send("GET_ALL_MEETINGS");
            }
        }

        // List all active meetings for debugging
        System.out.println("Active meetings in system: " + HelloApplication.getActiveMeetings().keySet());

        // Also check database for meetings
        System.out.println("Checking database for meetings...");
        List<Database.MeetingInfo> dbMeetings = Database.getAllMeetings();
        for (Database.MeetingInfo meeting : dbMeetings) {
            System.out.println("📅 Database meeting: " + meeting.getMeetingId() + " hosted by " + meeting.getHost());
        }

        if (HelloApplication.getActiveMeetingId() != null) {
            boolean exists = Database.meetingExists(HelloApplication.getActiveMeetingId());
            System.out.println("Meeting " + HelloApplication.getActiveMeetingId() + " exists in DB: " + exists);
        }

        // If no active meetings, show helpful message
        if (HelloApplication.getActiveMeetings().isEmpty() && dbMeetings.isEmpty()) {
            statusLabel.setText("ℹ️ No meetings found. Please enter a valid Meeting ID or create one.");
        } else {
            statusLabel.setText("📋 Enter Meeting ID and Name to join");
            displayAvailableMeetings();
        }

        // Auto-fill name if user is logged in
        String loggedInUser = HelloApplication.getLoggedInUser();
        if (loggedInUser != null && !loggedInUser.isEmpty()) {
            nameField.setText(loggedInUser);
        }
    }

    private void checkForMeetingId() {
        // Check if there's an active meeting ID set from NewMeetingController
        String activeMeetingId = HelloApplication.getActiveMeetingId();
        if (activeMeetingId != null && !activeMeetingId.isEmpty()) {
            meetingIdField.setText(activeMeetingId);
            statusLabel.setText("📋 Meeting ID detected! Enter your name to join.");
            System.out.println("✅ Auto-filled meeting ID from NewMeeting: " + activeMeetingId);
        } else {
            System.out.println("ℹ️ No active meeting ID detected from NewMeetingController");

            // Check clipboard for a 6-digit number
            try {
                String clipboard = javafx.scene.input.Clipboard.getSystemClipboard().getString();
                if (clipboard != null && clipboard.matches("\\d{6}")) {
                    meetingIdField.setText(clipboard);
                    statusLabel.setText("📋 Meeting ID found in clipboard! Click Join to enter.");
                    System.out.println("✅ Auto-filled meeting ID from clipboard: " + clipboard);
                }
            } catch (Exception e) {
                // Ignore clipboard errors
            }
        }
    }

    private void setupKeyHandlers() {
        // Add key pressed listeners to both text fields
        meetingIdField.setOnKeyPressed(this::handleKeyPress);
        nameField.setOnKeyPressed(this::handleKeyPress);
    }

    private void handleKeyPress(KeyEvent event) {
        // Only trigger join when Enter key is pressed
        if (event.getCode() == KeyCode.ENTER) {
            try {
                onJoinMeetingClick();
            } catch (Exception e) {
                e.printStackTrace();
                statusLabel.setText("❌ Error joining meeting: " + e.getMessage());
            }
        }
    }

    @FXML
    protected void onJoinMeetingClick() throws Exception {
        String meetingId = meetingIdField.getText().trim();
        String name = nameField.getText().trim();

        if (meetingId.isEmpty() || name.isEmpty()) {
            statusLabel.setText("⚠ Please enter both Meeting ID and Name!");
            return;
        }

        // Validate meeting ID format (should be 6 digits)
        if (!meetingId.matches("\\d{6}")) {
            statusLabel.setText("❌ Meeting ID must be 6 digits!");
            return;
        }

        statusLabel.setText("🔄 Validating meeting...");

        // Get the current logged in user
        String loggedInUser = HelloApplication.getLoggedInUser();
        String participantName = loggedInUser != null ? loggedInUser : name;

        System.out.println("Attempting to join meeting: " + meetingId + " as: " + participantName);
        System.out.println("WebSocket connected: " + HelloApplication.isWebSocketConnected());

        // First check if meeting exists locally
        boolean meetingExists = HelloApplication.isValidMeeting(meetingId);

        // If not found locally but WebSocket is connected, try server validation
        if (!meetingExists && HelloApplication.isWebSocketConnected()) {
            statusLabel.setText("🔄 Checking with server for meeting...");
            System.out.println("Meeting not found locally, requesting server validation");

            // Try server validation
            meetingExists = validateMeetingWithServer(meetingId);
        }

        // If still not found, try database directly
        if (!meetingExists) {
            System.out.println("Checking database directly for meeting: " + meetingId);
            meetingExists = Database.meetingExists(meetingId);

            if (meetingExists) {
                System.out.println("✅ Meeting found in database!");
                String host = Database.getMeetingHost(meetingId);
                if (host != null) {
                    HelloApplication.syncMeetingFromServer(meetingId, host);
                }
            }
        }

        // Check if meeting exists
        if (!meetingExists) {
            statusLabel.setText("❌ Meeting not found! Check the ID or create a new meeting.");

            // Show available meetings for debugging
            showAvailableMeetings();

            // Ask if user wants to create a new meeting
            askToCreateNewMeeting(meetingId);
            return;
        }

        System.out.println("✅ Meeting validated successfully: " + meetingId);

        // Join the meeting
        boolean joined = HelloApplication.joinMeeting(meetingId, participantName);

        if (joined) {
            statusLabel.setText("✅ Successfully joined meeting! Loading...");

            // Send WebSocket notification
            if (HelloApplication.isWebSocketConnected()) {
                String deviceId = HelloApplication.getDeviceId();
                String deviceName = HelloApplication.getDeviceName();
                String content = participantName + " joined the meeting|" + deviceId + "|" + deviceName;

                HelloApplication.sendWebSocketMessage("USER_JOINED", meetingId, participantName, content);
                System.out.println("Sent USER_JOINED notification for meeting: " + meetingId);
            } else {
                System.out.println("WebSocket not connected - will work in offline mode");
            }

            // Navigate to meeting view
            navigateToMeeting();
        } else {
            statusLabel.setText("❌ Failed to join meeting. Please try again.");
        }
    }

    /**
     * Validate meeting with server
     */
    private boolean validateMeetingWithServer(String meetingId) {
        if (!HelloApplication.isWebSocketConnected()) {
            return false;
        }

        try {
            System.out.println("🔄 Validating meeting with server: " + meetingId);

            // Send validation request
            HelloApplication.sendWebSocketMessage("VALIDATE_MEETING", meetingId,
                    HelloApplication.getLoggedInUser(), meetingId);

            // Wait for response (give server time to respond)
            Thread.sleep(1500);

            // Check again
            boolean exists = HelloApplication.isValidMeeting(meetingId);
            if (!exists) {
                // Try database one more time
                exists = Database.meetingExists(meetingId);
            }

            return exists;

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    /**
     * Show available meetings
     */
    private void displayAvailableMeetings() {
        List<Database.MeetingInfo> dbMeetings = Database.getAllMeetings();

        if (!dbMeetings.isEmpty()) {
            StringBuilder msg = new StringBuilder("Available meetings:\n");
            for (Database.MeetingInfo meeting : dbMeetings) {
                msg.append("• ").append(meeting.getMeetingId())
                        .append(" (Host: ").append(meeting.getHost())
                        .append(")\n");
            }
            System.out.println(msg.toString());
        }
    }

    private void showAvailableMeetings() {
        List<Database.MeetingInfo> dbMeetings = Database.getAllMeetings();

        if (!dbMeetings.isEmpty()) {
            StringBuilder msg = new StringBuilder("Available meetings in database:\n");
            for (Database.MeetingInfo meeting : dbMeetings) {
                msg.append("• ").append(meeting.getMeetingId())
                        .append(" (Host: ").append(meeting.getHost())
                        .append(", Participants: ").append(meeting.getParticipantCount())
                        .append(")\n");
            }
            System.out.println(msg.toString());

            // Show in UI as well
            Platform.runLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION);
                alert.setTitle("Available Meetings");
                alert.setHeaderText("These meetings are currently available:");
                alert.setContentText(msg.toString());
                alert.showAndWait();
            });
        }

        if (!HelloApplication.getActiveMeetings().isEmpty()) {
            StringBuilder msg = new StringBuilder("Active meetings in memory:\n");
            for (String id : HelloApplication.getActiveMeetings().keySet()) {
                msg.append("• ").append(id).append("\n");
            }
            System.out.println(msg.toString());
        }

        if (dbMeetings.isEmpty() && HelloApplication.getActiveMeetings().isEmpty()) {
            System.out.println("No meetings found in database or memory");
        }
    }

    /**
     * Ask user if they want to create a new meeting
     */
    private void askToCreateNewMeeting(String meetingId) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Meeting Not Found");
            alert.setHeaderText("Meeting ID: " + meetingId + " was not found");
            alert.setContentText("Would you like to create a new meeting with this ID?");

            ButtonType createButton = new ButtonType("Create New Meeting");
            ButtonType tryAgainButton = new ButtonType("Try Again");
            ButtonType cancelButton = new ButtonType("Cancel", ButtonType.CANCEL.getButtonData());

            alert.getButtonTypes().setAll(createButton, tryAgainButton, cancelButton);

            Optional<ButtonType> result = alert.showAndWait();
            if (result.isPresent()) {
                if (result.get() == createButton) {
                    try {
                        HelloApplication.createMeetingForTesting(meetingId);
                        HelloApplication.setActiveMeetingId(meetingId);
                        HelloApplication.setRoot("new-meeting-view.fxml");
                    } catch (Exception e) {
                        e.printStackTrace();
                        statusLabel.setText("❌ Error creating meeting: " + e.getMessage());
                    }
                } else if (result.get() == tryAgainButton) {
                    statusLabel.setText("Please check the Meeting ID and try again");
                    meetingIdField.requestFocus();
                }
            }
        });
    }

    /**
     * Navigate to meeting view
     */
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
                        statusLabel.setText("❌ Error navigating to meeting: " + e.getMessage());
                    }
                });
            } catch (InterruptedException e) {
                e.printStackTrace();
                statusLabel.setText("❌ Join process interrupted");
            }
        }).start();
    }

    @FXML
    protected void onCancelClick() throws Exception {
        HelloApplication.setRoot("dashboard-view.fxml");
    }

    @FXML
    protected void onQuickJoinClick() {
        // Generate a random 6-digit meeting ID for quick testing
        String randomMeetingId = String.valueOf((int) (Math.random() * 900000) + 100000);
        meetingIdField.setText(randomMeetingId);

        // Register this as a valid meeting for testing
        HelloApplication.createMeetingForTesting(randomMeetingId);
        Database.saveMeetingWithId(randomMeetingId, HelloApplication.getLoggedInUser(),
                "Test Meeting " + randomMeetingId, "Quick join test meeting");

        statusLabel.setText("🎯 Quick Join ID: " + randomMeetingId + " - Enter your name!");

        // Focus on name field for better UX
        nameField.requestFocus();
    }

    @FXML
    protected void onRefreshMeetingsClick() {
        statusLabel.setText("🔄 Refreshing meeting list...");

        // Request meetings from server
        if (HelloApplication.isWebSocketConnected()) {
            HelloApplication.requestAllMeetings();
            if (HelloApplication.getWebSocketClient() != null) {
                HelloApplication.getWebSocketClient().send("GET_ALL_MEETINGS");
            }
        }

        // Check database
        List<Database.MeetingInfo> meetings = Database.getAllMeetings();
        if (!meetings.isEmpty()) {
            StringBuilder msg = new StringBuilder("📋 Available meetings:\n");
            for (Database.MeetingInfo m : meetings) {
                msg.append("• ").append(m.getMeetingId())
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
        statusLabel.setText("📋 Paste your meeting ID in the field above");
    }
}