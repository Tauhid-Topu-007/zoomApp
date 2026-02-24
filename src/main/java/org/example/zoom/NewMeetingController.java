package org.example.zoom;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.control.ScrollPane;
import javafx.application.Platform;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class NewMeetingController {

    @FXML
    private TextField meetingIdField;

    @FXML
    private Label statusLabel;

    @FXML
    private ScrollPane mainScrollPane;

    private String meetingId;
    private String host;

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

        // Store the meeting ID globally so other controllers can access it
        HelloApplication.setActiveMeetingId(meetingId);

        // CRITICAL: Save meeting to database immediately
        host = HelloApplication.getLoggedInUser();
        if (host == null || host.isEmpty()) {
            host = "Host";
        }

        // Save to database with explicit meeting ID
        boolean saved = Database.saveMeetingWithId(meetingId, host, "Meeting " + meetingId,
                "Meeting created by " + host + " on device: " + HelloApplication.getDeviceName());

        if (saved) {
            System.out.println("✅ Meeting saved to database with ID: " + meetingId);
        } else {
            System.err.println("❌ Failed to save meeting to database, trying alternative method");
            // Alternative: at least add participant
            Database.addParticipant(meetingId, host);
        }

        // Also add host as participant
        Database.addParticipant(meetingId, host);

        // CRITICAL: Send WebSocket notification multiple times to ensure delivery
        if (HelloApplication.isWebSocketConnected()) {
            String deviceId = HelloApplication.getDeviceId();
            String deviceName = HelloApplication.getDeviceName();
            String content = "New meeting created by " + host + "|" + deviceId + "|" + deviceName;

            // Store values in final variables for lambda
            final String finalMeetingId = meetingId;
            final String finalHost = host;
            final String finalDeviceId = deviceId;
            final String finalDeviceName = deviceName;
            final String finalContent = content;

            // Send first message
            HelloApplication.sendWebSocketMessage("MEETING_CREATED", meetingId, host, content);
            System.out.println("📢 Sent MEETING_CREATED via WebSocket for meeting: " + meetingId);

            // Send second message after short delay
            new Thread(() -> {
                try {
                    Thread.sleep(500);
                    if (HelloApplication.isWebSocketConnected()) {
                        HelloApplication.sendWebSocketMessage("MEETING_AVAILABLE", finalMeetingId, finalHost,
                                finalMeetingId + "|" + finalHost + "|" + finalDeviceName);
                        System.out.println("📢 Sent additional MEETING_AVAILABLE notification");

                        // Request meeting list to sync with server
                        HelloApplication.requestAllMeetings();
                    }
                } catch (InterruptedException e) {
                    // Ignore
                }
            }).start();

            // Also send via HTTP API if available
            sendMeetingViaHttp(meetingId, host, deviceName);
        } else {
            System.out.println("⚠️ WebSocket not connected - meeting only available locally");
        }

        statusLabel.setText("✅ Meeting ID generated! Share it with participants.");
        System.out.println("🎯 New Meeting Controller: Meeting ID " + meetingId + " is ready for joining!");
        System.out.println("👤 Host: " + host);
        System.out.println("🔌 WebSocket connected: " + HelloApplication.isWebSocketConnected());

        // Request meeting list to sync with server
        HelloApplication.requestAllMeetings();
    }

    /**
     * Send meeting via HTTP API as backup
     */
    private void sendMeetingViaHttp(String meetingId, String host, String deviceName) {
        try {
            String serverUrl = "http://" + HelloApplication.getServerIp() + ":" + HelloApplication.getServerPort() + "/api/meeting";
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            String json = String.format("{\"meetingId\":\"%s\",\"host\":\"%s\",\"deviceName\":\"%s\"}",
                    meetingId, host, deviceName);

            java.net.http.HttpRequest request = java.net.http.HttpRequest.newBuilder()
                    .uri(java.net.URI.create(serverUrl))
                    .header("Content-Type", "application/json")
                    .POST(java.net.http.HttpRequest.BodyPublishers.ofString(json))
                    .build();

            client.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofString())
                    .thenAccept(response -> {
                        System.out.println("📡 HTTP meeting notification sent: " + response.statusCode());
                    });
        } catch (Exception e) {
            System.err.println("❌ Failed to send meeting via HTTP: " + e.getMessage());
        }
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

        // Send meeting started message via WebSocket
        if (HelloApplication.isWebSocketConnected()) {
            String deviceId = HelloApplication.getDeviceId();
            String deviceName = HelloApplication.getDeviceName();
            String content = "Meeting started by " + username + "|" + deviceId + "|" + deviceName;
            HelloApplication.sendWebSocketMessage("MEETING_STARTED", meetingId, username, content);
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

        // Also show a popup with connection instructions
        showConnectionInstructions();
    }

    private void showConnectionInstructions() {
        Platform.runLater(() -> {
            String serverIp = HelloApplication.getServerIp();
            String serverPort = HelloApplication.getServerPort();

            String instructions = "📋 MEETING JOIN INSTRUCTIONS\n\n" +
                    "Meeting ID: " + meetingId + "\n\n" +
                    "For other devices to join:\n\n" +
                    "1. Make sure they are connected to the SAME WiFi network\n" +
                    "2. When joining, they should use this server IP: " + serverIp + "\n" +
                    "3. Port: " + serverPort + "\n\n" +
                    "If joining from localhost, they can use the meeting ID directly.\n\n" +
                    "The meeting ID is also valid for: " + serverIp + ":" + serverPort;

            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle("Meeting Join Instructions");
            alert.setHeaderText("Share this information with participants");
            alert.setContentText(instructions);
            alert.showAndWait();
        });
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
        String currentTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        String host = HelloApplication.getLoggedInUser();
        String serverIp = HelloApplication.getServerIp();
        String serverPort = HelloApplication.getServerPort();

        String shareText = "Join my Zoom meeting!\n" +
                "Meeting ID: " + meetingId + "\n" +
                "Host: " + host + "\n" +
                "Created: " + currentTime + "\n" +
                "\nCONNECTION DETAILS:\n" +
                "Server IP: " + serverIp + "\n" +
                "Server Port: " + serverPort + "\n" +
                "\nINSTRUCTIONS:\n" +
                "1. Open the Zoom application\n" +
                "2. Go to Dashboard → Join Meeting\n" +
                "3. Enter this Meeting ID: " + meetingId + "\n" +
                "4. If prompted for server, use: " + serverIp + "\n" +
                "5. Port: " + serverPort + "\n" +
                "6. Click Join\n" +
                "\n⚠️ Make sure you're on the same WiFi network!";

        Clipboard clipboard = Clipboard.getSystemClipboard();
        ClipboardContent content = new ClipboardContent();
        content.putString(shareText);
        clipboard.setContent(content);

        statusLabel.setText("✅ Meeting details copied! Ready to share with participants.");
    }
}