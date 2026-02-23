package org.example.zoom;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.application.Platform;

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

        // List all active meetings for debugging
        System.out.println("Active meetings in system: " + HelloApplication.getActiveMeetings().keySet());
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

        // Get the current logged in user (the one who logged in)
        String loggedInUser = HelloApplication.getLoggedInUser();

        // Use the logged in username instead of the name field for consistency
        String participantName = loggedInUser != null ? loggedInUser : name;

        System.out.println("Attempting to join meeting: " + meetingId + " as: " + participantName);
        System.out.println("WebSocket connected: " + HelloApplication.isWebSocketConnected());
        System.out.println("Active meetings before validation: " + HelloApplication.getActiveMeetings().keySet());

        // Check if meeting exists
        if (!HelloApplication.isValidMeeting(meetingId)) {
            statusLabel.setText("❌ Meeting not found! Check the ID or create a new meeting.");

            // Show available meetings for debugging
            if (!HelloApplication.getActiveMeetings().isEmpty()) {
                StringBuilder msg = new StringBuilder("Available meetings: ");
                for (String id : HelloApplication.getActiveMeetings().keySet()) {
                    msg.append(id).append(" ");
                }
                System.out.println(msg.toString());
            }
            return;
        }

        System.out.println("✅ Meeting validated successfully: " + meetingId);

        // Join the meeting using HelloApplication's system
        boolean joined = HelloApplication.joinMeeting(meetingId, participantName);

        if (joined) {
            statusLabel.setText("✅ Successfully joined meeting! Loading...");

            // If WebSocket is connected, send a notification that we joined
            if (HelloApplication.isWebSocketConnected()) {
                String deviceId = HelloApplication.getDeviceId();
                String deviceName = HelloApplication.getDeviceName();
                String content = participantName + " joined the meeting|" + deviceId + "|" + deviceName;

                HelloApplication.sendWebSocketMessage("USER_JOINED", meetingId, participantName, content);
                System.out.println("Sent USER_JOINED notification for meeting: " + meetingId);
            } else {
                System.out.println("WebSocket not connected - will work in offline mode");
            }

            // Navigate to meeting view after a brief delay
            new Thread(() -> {
                try {
                    Thread.sleep(1500); // Show status for 1.5 seconds
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
        } else {
            statusLabel.setText("❌ Failed to join meeting. Please try again.");
        }
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

        statusLabel.setText("🎯 Quick Join ID: " + randomMeetingId + " - Enter your name!");

        // Focus on name field for better UX
        nameField.requestFocus();
    }

    @FXML
    protected void onPasteMeetingId() {
        statusLabel.setText("📋 Paste your meeting ID in the field above");
    }
}