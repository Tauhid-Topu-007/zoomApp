package org.example.zoom;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;

import java.util.UUID;

public class NewMeetingController {

    @FXML
    private TextField meetingIdField;

    @FXML
    private Label statusLabel;

    private String meetingId;

    @FXML
    public void initialize() {
        // Generate unique meeting ID
        meetingId = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        meetingIdField.setText(meetingId);
    }

    @FXML
    protected void onStartMeetingClick() {
        statusLabel.setText("✅ Meeting started with ID: " + meetingId);
        // Later: navigate to a meeting room page
    }

    @FXML
    protected void onBackClick() throws Exception {
        HelloApplication.setRoot("dashboard-view.fxml"); // Back to dashboard
    }
}
