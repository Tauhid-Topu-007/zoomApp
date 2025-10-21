package org.example.zoom;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.zoom.websocket.SimpleWebSocketClient; // Changed import

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class ChatController {

    @FXML
    private VBox chatBox;

    @FXML
    private TextField messageField;

    @FXML
    private Label statusLabel;

    @FXML
    private Label userLabel;

    @FXML
    private Button sendButton;

    private Stage stage;
    private SimpleWebSocketClient webSocketClient; // Changed type
    private String currentUser;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setWebSocketClient(SimpleWebSocketClient client) { // Simplified method
        this.webSocketClient = client;
        updateConnectionStatus();

        // If we have a client, set up message handling
        if (client != null) {
            // The client already has its message handler set up in HelloApplication
            // We'll handle messages through the handleWebSocketMessage method
        }
    }

    public void setCurrentUser(String username) {
        this.currentUser = username;
        if (userLabel != null) {
            userLabel.setText("Chatting as: " + username);
        }
    }
    // inside initialize() or wherever connection starts
    @FXML
    public void initialize() {
        messageField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                onSendClick();
            }
        });

        String loggedInUser = HelloApplication.getLoggedInUser();
        if (loggedInUser != null) setCurrentUser(loggedInUser);

        // ✅ Connect to real backend WebSocket server
        webSocketClient = new SimpleWebSocketClient("ws://localhost:8080/chat", this::handleWebSocketMessage);
        webSocketClient.connect();

        updateConnectionStatus();
        addSystemMessage("Connecting to chat server...");
    }


    @FXML
    protected void onSendClick() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            if (webSocketClient != null && webSocketClient.isConnected()) {
                // Send message via WebSocket
                webSocketClient.sendMessage("CHAT_MESSAGE", "global", currentUser, message);
                messageField.clear();
            } else {
                // Fallback: local only display
                addUserMessage(message);
                messageField.clear();
                addSystemMessage("⚠️ Message sent locally (not connected to server)");
            }
        }
    }

    @FXML
    protected void onFileSendClick() {
        if (stage == null) {
            stage = (Stage) chatBox.getScene().getWindow();
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Select a File to Send");
        File file = fileChooser.showOpenDialog(stage);

        if (file != null) {
            if (webSocketClient != null && webSocketClient.isConnected()) {
                // In a real app, you'd upload the file to a server and send a link
                webSocketClient.sendMessage("FILE_SHARE", "global", currentUser, "Shared file: " + file.getName());
                addFileMessage(file.getName(), true);
            } else {
                // Local file sharing
                addFileMessage(file.getName(), true);
                addSystemMessage("⚠️ File shared locally (not connected to server)");
            }
        }
    }

    private void downloadFile(File sourceFile) {
        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save File As");
        fileChooser.setInitialFileName(sourceFile.getName());
        File destinationFile = fileChooser.showSaveDialog(stage);

        if (destinationFile != null) {
            try {
                Files.copy(sourceFile.toPath(), destinationFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                showAlert("Success", "File downloaded successfully!", Alert.AlertType.INFORMATION);
            } catch (IOException ex) {
                showAlert("Error", "Error downloading file: " + ex.getMessage(), Alert.AlertType.ERROR);
            }
        }
    }

    @FXML
    protected void onBackClick() throws Exception {
        HelloApplication.setRoot("dashboard-view.fxml");
    }

    @FXML
    protected void onClearClick() {
        chatBox.getChildren().clear();
        addSystemMessage("Chat cleared");
    }

    @FXML
    protected void onRefreshClick() {
        updateConnectionStatus();
        if (webSocketClient != null && !webSocketClient.isConnected()) {
            addSystemMessage("Attempting to reconnect...");
            // You could add reconnection logic here
        }
    }

    // Method to handle incoming WebSocket messages
    public void handleWebSocketMessage(String message) {
        System.out.println("ChatController received: " + message);

        // Parse message: "TYPE|MEETING_ID|USERNAME|CONTENT"
        String[] parts = message.split("\\|", 4);
        if (parts.length >= 4) {
            String type = parts[0];
            String meetingId = parts[1];
            String username = parts[2];
            String content = parts[3];

            switch (type) {
                case "CHAT_MESSAGE":
                case "CHAT":
                    if (!username.equals(currentUser)) { // Don't show our own messages twice
                        addOtherUserMessage(username, content);
                    }
                    break;

                case "FILE_SHARE":
                    addFileMessage(content, false);
                    break;

                case "SYSTEM":
                    addSystemMessage(content);
                    break;

                case "USER_JOINED":
                    addSystemMessage("🟢 " + username + " joined the chat");
                    break;

                case "USER_LEFT":
                    addSystemMessage("🔴 " + username + " left the chat");
                    break;

                case "CONNECTED":
                    updateConnectionStatus();
                    addSystemMessage("✅ " + content);
                    break;

                case "DISCONNECTED":
                    updateConnectionStatus();
                    addSystemMessage("❌ " + content);
                    break;

                default:
                    // Handle unknown message types
                    System.out.println("Unknown message type: " + type);
                    break;
            }
        } else {
            // Handle malformed messages
            System.out.println("Malformed message: " + message);
        }

        updateConnectionStatus();
    }

    private void addUserMessage(String message) {
        Label messageLabel = new Label("You: " + message);
        messageLabel.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 8 12; -fx-background-radius: 10; -fx-wrap-text: true;");
        messageLabel.setMaxWidth(400);
        javafx.application.Platform.runLater(() -> chatBox.getChildren().add(messageLabel));
        scrollToBottom();
    }

    private void addOtherUserMessage(String username, String message) {
        Label messageLabel = new Label(username + ": " + message);
        messageLabel.setStyle("-fx-background-color: #2ecc71; -fx-text-fill: white; -fx-padding: 8 12; -fx-background-radius: 10; -fx-wrap-text: true;");
        messageLabel.setMaxWidth(400);
        javafx.application.Platform.runLater(() -> chatBox.getChildren().add(messageLabel));
        scrollToBottom();
    }

    private void addSystemMessage(String message) {
        Label messageLabel = new Label("💬 " + message);
        messageLabel.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-padding: 8 12; -fx-background-radius: 10; -fx-wrap-text: true;");
        messageLabel.setMaxWidth(400);
        javafx.application.Platform.runLater(() -> chatBox.getChildren().add(messageLabel));
        scrollToBottom();
    }

    private void addFileMessage(String fileName, boolean isOwnFile) {
        Hyperlink fileLink = new Hyperlink("📎 " + fileName + (isOwnFile ? " (sent)" : " (received)"));
        fileLink.setStyle("-fx-font-size: 14px; -fx-text-fill: #8e44ad; -fx-border-color: #8e44ad; -fx-border-width: 1; -fx-padding: 5 10; -fx-border-radius: 5;");

        // For demo purposes, create a temporary file when clicked
        fileLink.setOnAction(e -> {
            try {
                File tempFile = File.createTempFile("zoom_chat_", "_" + fileName);
                addSystemMessage("File placeholder created: " + tempFile.getAbsolutePath());
                showAlert("File Info", "This is a demo. In a real app, the file would be downloaded from the server.\n\nPlaceholder: " + tempFile.getAbsolutePath(), Alert.AlertType.INFORMATION);
            } catch (IOException ex) {
                showAlert("Error", "Could not create file placeholder: " + ex.getMessage(), Alert.AlertType.ERROR);
            }
        });

        javafx.application.Platform.runLater(() -> chatBox.getChildren().add(fileLink));
        scrollToBottom();
    }

    private void updateConnectionStatus() {
        javafx.application.Platform.runLater(() -> {
            if (webSocketClient != null && webSocketClient.isConnected()) {
                statusLabel.setText("🟢 Connected");
                statusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                sendButton.setDisable(false);
            } else {
                statusLabel.setText("🔴 Disconnected");
                statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                sendButton.setDisable(false); // Still allow local messaging
            }
        });
    }

    private void scrollToBottom() {
        javafx.application.Platform.runLater(() -> {
            // Auto-scroll to bottom (VBox doesn't have built-in scrolling, so we simulate it)
            if (chatBox.getParent() instanceof ScrollPane) {
                ScrollPane scrollPane = (ScrollPane) chatBox.getParent();
                scrollPane.setVvalue(1.0);
            }
        });
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        javafx.application.Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}