package org.example.zoom;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.example.zoom.websocket.SimpleWebSocketClient;

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
    private Label connectionInfoLabel;

    @FXML
    private Button sendButton;

    private Stage stage;
    private SimpleWebSocketClient webSocketClient;
    private String currentUser;
    private String lastMessageId = "";
    private boolean isInitialized = false;

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setWebSocketClient(SimpleWebSocketClient client) {
        if (this.webSocketClient != null && this.webSocketClient != client) {
            // Clean up previous client
            this.webSocketClient.disconnect();
        }

        this.webSocketClient = client;
        if (client != null) {
            updateConnectionUI();
        }
    }

    public void setCurrentUser(String username) {
        this.currentUser = username;
        if (userLabel != null) {
            Platform.runLater(() -> {
                userLabel.setText("Chatting as: " + username);
            });
        }
    }

    @FXML
    public void initialize() {
        // Setup message field for Enter key
        if (messageField != null) {
            messageField.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ENTER) {
                    onSendClick();
                }
            });
        }

        // Set current user from application state
        String loggedInUser = HelloApplication.getLoggedInUser();
        if (loggedInUser != null) {
            setCurrentUser(loggedInUser);
        }

        // Initialize WebSocket connection only once
        if (!isInitialized) {
            initializeWebSocketConnection();
            isInitialized = true;
        }
    }

    private void initializeWebSocketConnection() {
        String serverUrl = HelloApplication.getCurrentServerUrl();

        // Check if we already have a WebSocket client from HelloApplication
        SimpleWebSocketClient globalClient = HelloApplication.getWebSocketClient();

        if (globalClient != null) {
            // Use the global WebSocket client
            webSocketClient = globalClient;
            webSocketClient.setMessageHandler(this::handleWebSocketMessage);
            System.out.println("✅ Using global WebSocket client from HelloApplication");
        } else if (webSocketClient == null) {
            // Create new WebSocket client
            webSocketClient = new SimpleWebSocketClient(serverUrl, this::handleWebSocketMessage);
            System.out.println("✅ Created new WebSocket client for chat");
        } else if (!webSocketClient.getServerUrl().equals(serverUrl)) {
            // Server URL changed, reconnect
            webSocketClient.disconnect();
            webSocketClient = new SimpleWebSocketClient(serverUrl, this::handleWebSocketMessage);
            System.out.println("✅ Updated WebSocket client for new server: " + serverUrl);
        }

        // Connect if not already connected
        if (webSocketClient != null && !webSocketClient.isConnected()) {
            webSocketClient.connect();
        }

        updateConnectionUI();
        addSystemMessage("Connecting to chat server at: " + serverUrl);
    }

    @FXML
    protected void onSendClick() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            // Generate unique ID for this message
            String messageId = System.currentTimeMillis() + "_" + currentUser;
            lastMessageId = messageId;

            if (webSocketClient != null && webSocketClient.isConnected()) {
                // Send message via WebSocket
                webSocketClient.sendMessage("CHAT_MESSAGE", "global", currentUser, message);
                // Display message immediately
                addMessageToUI(currentUser, message, true);
                messageField.clear();
            } else {
                // Local fallback
                addMessageToUI(currentUser, message, true);
                messageField.clear();
                addSystemMessage("Message sent locally (not connected to server)");

                // Try to reconnect
                if (webSocketClient != null && !webSocketClient.isConnected()) {
                    webSocketClient.connect();
                }
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
            String fileName = file.getName();

            if (webSocketClient != null && webSocketClient.isConnected()) {
                webSocketClient.sendMessage("FILE_SHARE", "global", currentUser, "Shared file: " + fileName);
                addFileMessage(fileName, true);
            } else {
                addFileMessage(fileName, true);
                addSystemMessage("File shared locally (not connected to server)");
            }
        }
    }

    @FXML
    protected void onSettingsClick() {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("settings-view.fxml"));
            Scene scene = new Scene(loader.load(), 600, 500);

            SettingsController controller = loader.getController();
            controller.setUser(currentUser);
            controller.setStage(stage);

            Stage settingsStage = new Stage();
            settingsStage.setTitle("Server Settings");
            settingsStage.setScene(scene);
            settingsStage.initOwner(stage);
            settingsStage.showAndWait();

            // Reconnect with new settings
            handleSettingsUpdate();

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Failed to open settings!", Alert.AlertType.ERROR);
        }
    }

    @FXML
    protected void onBackClick() throws Exception {
        // Disconnect only if this is our own client (not the global one)
        if (webSocketClient != null && webSocketClient != HelloApplication.getWebSocketClient()) {
            webSocketClient.disconnect();
        }
        HelloApplication.setRoot("dashboard-view.fxml");
    }

    @FXML
    protected void onClearClick() {
        if (chatBox != null) {
            Platform.runLater(() -> {
                chatBox.getChildren().clear();
                addSystemMessage("Chat cleared");
            });
        }
    }

    @FXML
    protected void onRefreshClick() {
        if (webSocketClient != null && !webSocketClient.isConnected()) {
            addSystemMessage("Attempting to reconnect...");
            webSocketClient.connect();
        }
        updateConnectionUI();
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

            // Skip system messages that are just echoes
            if (username.equals(currentUser) && isOwnMessageEcho(content, type)) {
                System.out.println("Skipping own message echo: " + content);
                return;
            }

            switch (type) {
                case "CHAT_MESSAGE":
                case "CHAT":
                    handleChatMessage(username, content);
                    break;

                case "FILE_SHARE":
                    addFileMessage(content, username.equals(currentUser));
                    break;

                case "SYSTEM":
                    addSystemMessage(content);
                    break;

                case "USER_JOINED":
                    addSystemMessage("🟢 " + username + " joined the chat");
                    break;

                case "USER_LEFT":
                    addSystemMessage( username + " left the chat");
                    break;

                case "CONNECTED":
                    addSystemMessage( content);
                    updateConnectionUI();
                    break;

                case "DISCONNECTED":
                    addSystemMessage("❌ " + content);
                    updateConnectionUI();
                    break;

                default:
                    // For unknown types, treat as regular message
                    handleChatMessage(username, content);
                    break;
            }
        } else {
            // Handle simple messages or malformed ones
            addSystemMessage("Message: " + message);
        }
    }

    private boolean isOwnMessageEcho(String content, String type) {
        // Simple check for own message echoes
        if (type.equals("CHAT_MESSAGE") || type.equals("CHAT")) {
            // Check if this looks like a message we just sent
            return content.contains(lastMessageId) ||
                    (messageField != null && content.equals(messageField.getText().trim()));
        }
        return false;
    }

    private void handleChatMessage(String username, String content) {
        if (username.equals(currentUser)) {
            // This is our own message from server echo - skip
            System.out.println("Server echo of own message: " + content);
        } else {
            // Message from another user
            addMessageToUI(username, content, false);
        }
    }

    private void addMessageToUI(String username, String message, boolean isOwnMessage) {
        Platform.runLater(() -> {
            if (chatBox != null) {
                String displayText = isOwnMessage ? "You: " + message : username + ": " + message;
                String style = isOwnMessage ?
                        "-fx-background-color: #3498db; -fx-text-fill: white;" :
                        "-fx-background-color: #2ecc71; -fx-text-fill: white;";

                Label messageLabel = new Label(displayText);
                messageLabel.setStyle(style + " -fx-padding: 8 12; -fx-background-radius: 10; -fx-wrap-text: true;");
                messageLabel.setMaxWidth(600);
                chatBox.getChildren().add(messageLabel);
                scrollToBottom();
            }
        });
    }

    private void addSystemMessage(String message) {
        Platform.runLater(() -> {
            if (chatBox != null) {
                Label messageLabel = new Label("💬 " + message);
                messageLabel.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-padding: 8 12; -fx-background-radius: 10; -fx-wrap-text: true;");
                messageLabel.setMaxWidth(600);
                chatBox.getChildren().add(messageLabel);
                scrollToBottom();
            }
        });
    }

    private void addFileMessage(String fileName, boolean isOwnFile) {
        Platform.runLater(() -> {
            if (chatBox != null) {
                Hyperlink fileLink = new Hyperlink("📎 " + fileName + (isOwnFile ? " (sent)" : " (received)"));
                fileLink.setStyle("-fx-font-size: 14px; -fx-text-fill: #8e44ad; -fx-border-color: #8e44ad; -fx-border-width: 1; -fx-padding: 5 10; -fx-border-radius: 5;");

                // Demo file handling
                fileLink.setOnAction(e -> handleFileClick(fileName));

                chatBox.getChildren().add(fileLink);
                scrollToBottom();
            }
        });
    }

    private void handleFileClick(String fileName) {
        try {
            File tempFile = File.createTempFile("zoom_chat_", "_" + fileName);
            addSystemMessage("File placeholder created: " + tempFile.getAbsolutePath());
            showAlert("File Info",
                    "This is a demo. In a real app, the file would be downloaded from the server.\n\n" +
                            "Placeholder created at: " + tempFile.getAbsolutePath(),
                    Alert.AlertType.INFORMATION);
        } catch (IOException ex) {
            showAlert("Error", "Could not create file placeholder: " + ex.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void updateConnectionUI() {
        Platform.runLater(() -> {
            boolean isConnected = webSocketClient != null && webSocketClient.isConnected();

            // Update status label
            if (statusLabel != null) {
                if (isConnected) {
                    statusLabel.setText("🟢 Connected");
                    statusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                } else {
                    statusLabel.setText("Disconnected");
                    statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                }
            }

            // Update connection info label
            if (connectionInfoLabel != null) {
                String serverUrl = HelloApplication.getCurrentServerUrl();
                String status = isConnected ? "Connected" : "Disconnected";
                String displayUrl = serverUrl.replace("ws://", "");
                connectionInfoLabel.setText(status + " | " + displayUrl);

                if (isConnected) {
                    connectionInfoLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-size: 12;");
                } else {
                    connectionInfoLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-size: 12;");
                }
            }

            // Update send button state
            if (sendButton != null) {
                sendButton.setDisable(!isConnected);
                if (!isConnected) {
                    sendButton.setTooltip(new Tooltip("Not connected to server"));
                } else {
                    sendButton.setTooltip(null);
                }
            }
        });
    }

    private void handleSettingsUpdate() {
        // Reinitialize WebSocket connection with potentially new settings
        String newServerUrl = HelloApplication.getCurrentServerUrl();
        if (webSocketClient == null || !webSocketClient.getServerUrl().equals(newServerUrl)) {
            if (webSocketClient != null) {
                webSocketClient.disconnect();
            }
            webSocketClient = new SimpleWebSocketClient(newServerUrl, this::handleWebSocketMessage);
            webSocketClient.connect();
        } else if (!webSocketClient.isConnected()) {
            webSocketClient.connect();
        }

        addSystemMessage("Updated connection to: " + newServerUrl);
        updateConnectionUI();
    }

    private void scrollToBottom() {
        Platform.runLater(() -> {
            if (chatBox != null && chatBox.getParent() instanceof ScrollPane) {
                ScrollPane scrollPane = (ScrollPane) chatBox.getParent();
                scrollPane.setVvalue(1.0);
            }
        });
    }

    private void showAlert(String title, String message, Alert.AlertType type) {
        Platform.runLater(() -> {
            Alert alert = new Alert(type);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }
}