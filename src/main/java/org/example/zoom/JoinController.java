package org.example.zoom;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

public class JoinController {

    @FXML private TextField meetingIdField;
    @FXML private TextField nameField;
    @FXML private Label statusLabel;

    @FXML
    protected void onJoinMeetingClick() throws Exception {
        String meetingId = meetingIdField.getText().trim();
        String name = nameField.getText().trim();

        if (meetingId.isEmpty() || name.isEmpty()) {
            statusLabel.setText("⚠ Please enter both Meeting ID and Name!");
            return;
        }

        String activeMeetingId = HelloApplication.getActiveMeetingId();
        if (activeMeetingId != null && activeMeetingId.equals(meetingId)) {
            HelloApplication.addParticipant(name);  // Add participant
            HelloApplication.setRoot("meeting-view.fxml");
        } else {
            statusLabel.setText("❌ Invalid Meeting ID!");
        }
    }

    @FXML
    protected void onCancelClick() throws Exception {
        HelloApplication.setRoot("dashboard-view.fxml"); // Back to dashboard
    }
}
