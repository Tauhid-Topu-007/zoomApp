package org.example.zoom;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;

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
    }

    private void checkForMeetingId() {
        // Check if there's an active meeting ID set from NewMeetingController
        String activeMeetingId = HelloApplication.getActiveMeetingId();
        if (activeMeetingId != null && !activeMeetingId.isEmpty()) {
            meetingIdField.setText(activeMeetingId);
            statusLabel.setText("📋 Meeting ID detected! Enter your name to join.");
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

        // Use HelloApplication's meeting validation system
        if (!HelloApplication.isValidMeeting(meetingId)) {
            statusLabel.setText("❌ Meeting not found! Check the ID or create a new meeting.");
            return;
        }

        // Join the meeting using HelloApplication's system
        boolean joined = HelloApplication.joinMeeting(meetingId, name);

        if (joined) {
            statusLabel.setText("✅ Joining meeting...");

            // Navigate to meeting view after a brief delay to show the status
            new Thread(() -> {
                try {
                    Thread.sleep(1000); // Show status for 1 second
                    javafx.application.Platform.runLater(() -> {
                        try {
                            HelloApplication.setRoot("meeting-view.fxml");
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

        statusLabel.setText("🎯 Quick Join ID: " + randomMeetingId);

        // Focus on name field for better UX
        nameField.requestFocus();
    }

    @FXML
    protected void onPasteMeetingId() {
        statusLabel.setText("📋 Paste your meeting ID in the field above");
    }
}