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
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

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

    @FXML
    private Button fileButton;

    private Stage stage;
    private SimpleWebSocketClient webSocketClient;
    private String currentUser;
    private String lastMessageId = "";
    private boolean isInitialized = false;

    // File transfer tracking
    private Map<String, FileTransferInfo> activeFileTransfers = new HashMap<>();
    private String downloadsFolder = "chat_downloads";

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    public void setWebSocketClient(SimpleWebSocketClient client) {
        if (this.webSocketClient != null && this.webSocketClient != client) {
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
        if (messageField != null) {
            messageField.setOnKeyPressed(event -> {
                if (event.getCode() == KeyCode.ENTER) {
                    onSendClick();
                }
            });
        }

        String loggedInUser = HelloApplication.getLoggedInUser();
        if (loggedInUser != null) {
            setCurrentUser(loggedInUser);
        }

        // Create downloads folder
        createDownloadsFolder();

        if (!isInitialized) {
            initializeWebSocketConnection();
            isInitialized = true;
        }
    }

    private void createDownloadsFolder() {
        File folder = new File(downloadsFolder);
        if (!folder.exists()) {
            boolean created = folder.mkdirs();
            System.out.println("Created downloads folder: " + created + " at " + folder.getAbsolutePath());
        }
    }

    private void initializeWebSocketConnection() {
        String serverUrl = HelloApplication.getCurrentServerUrl();

        SimpleWebSocketClient globalClient = HelloApplication.getWebSocketClient();

        if (globalClient != null) {
            webSocketClient = globalClient;
            try {
                webSocketClient.getClass().getMethod("setMessageHandler", java.util.function.Consumer.class)
                        .invoke(webSocketClient, (java.util.function.Consumer<String>) this::handleWebSocketMessage);
                System.out.println("Message handler set on global WebSocket client");
            } catch (Exception e) {
                System.out.println("Could not set message handler on global client: " + e.getMessage());
            }
            System.out.println("Using global WebSocket client from HelloApplication");
        } else if (webSocketClient == null) {
            webSocketClient = new SimpleWebSocketClient(serverUrl, this::handleWebSocketMessage);
            System.out.println("Created new WebSocket client for chat");
        } else {
            boolean needsReconnect = false;
            try {
                String clientUrl = webSocketClient.getServerUrl();
                needsReconnect = !clientUrl.equals(serverUrl);
            } catch (Exception e) {
                System.out.println("getServerUrl() not available, reconnecting...");
                needsReconnect = true;
            }

            if (needsReconnect) {
                webSocketClient.disconnect();
                webSocketClient = new SimpleWebSocketClient(serverUrl, this::handleWebSocketMessage);
                System.out.println("Updated WebSocket client for new server: " + serverUrl);
            }
        }

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
            String messageId = System.currentTimeMillis() + "_" + currentUser;
            lastMessageId = messageId;

            if (webSocketClient != null && webSocketClient.isConnected()) {
                webSocketClient.sendMessage("CHAT_MESSAGE", "global", currentUser, message);
                addMessageToUI(currentUser, message, true);
                messageField.clear();
            } else {
                addMessageToUI(currentUser, message, true);
                messageField.clear();
                addSystemMessage("Message sent locally (not connected to server)");

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
            try {
                String fileName = file.getName();
                long fileSize = file.length();
                String fileId = UUID.randomUUID().toString();

                // Check file size (limit to 10MB for demo)
                long maxSize = 10 * 1024 * 1024; // 10MB
                if (fileSize > maxSize) {
                    showAlert("File Too Large",
                            "File size (" + formatFileSize(fileSize) + ") exceeds 10MB limit.",
                            Alert.AlertType.ERROR);
                    return;
                }

                // Read file bytes
                byte[] fileBytes = Files.readAllBytes(file.toPath());
                String base64Data = Base64.getEncoder().encodeToString(fileBytes);

                // Send file in a single message (for simplicity)
                String fileMessage = fileId + "|" + fileName + "|" + fileSize + "|" + base64Data;

                if (webSocketClient != null && webSocketClient.isConnected()) {
                    webSocketClient.sendMessage("FILE_TRANSFER", "global", currentUser, fileMessage);

                    addFileMessage(fileName, fileSize, true, "Sent");
                    addSystemMessage("File sent: " + fileName + " (" + formatFileSize(fileSize) + ")");
                } else {
                    addFileMessage(fileName, fileSize, true, "Cannot send - not connected");
                    addSystemMessage("File sharing requires server connection");
                }

            } catch (Exception e) {
                e.printStackTrace();
                showAlert("Error", "Failed to send file: " + e.getMessage(), Alert.AlertType.ERROR);
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

            handleSettingsUpdate();

        } catch (Exception e) {
            e.printStackTrace();
            showAlert("Error", "Failed to open settings!", Alert.AlertType.ERROR);
        }
    }

    @FXML
    protected void onBackClick() throws Exception {
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

    public void handleWebSocketMessage(String message) {
        System.out.println("ChatController received: " + message);

        String[] parts = message.split("\\|", 4);
        if (parts.length >= 4) {
            String type = parts[0];
            String meetingId = parts[1];
            String username = parts[2];
            String content = parts[3];

            if (username.equals(currentUser) && isOwnMessageEcho(content, type)) {
                System.out.println("Skipping own message echo: " + content);
                return;
            }

            Platform.runLater(() -> {
                try {
                    switch (type) {
                        case "CHAT_MESSAGE":
                        case "CHAT":
                            handleChatMessage(username, content);
                            break;

                        case "FILE_TRANSFER":
                            handleFileTransfer(username, content);
                            break;

                        case "FILE_SHARE": // Legacy support
                            String fileName = content.replace("Shared file: ", "");
                            addFileMessage(fileName, 0, username.equals(currentUser), "Shared");
                            break;

                        case "SYSTEM":
                            addSystemMessage(content);
                            break;

                        case "USER_JOINED":
                            addSystemMessage(username + " joined the chat");
                            break;

                        case "USER_LEFT":
                            addSystemMessage(username + " left the chat");
                            break;

                        case "CONNECTED":
                            addSystemMessage(content);
                            updateConnectionUI();
                            break;

                        case "DISCONNECTED":
                            addSystemMessage("Disconnected: " + content);
                            updateConnectionUI();
                            break;

                        default:
                            handleChatMessage(username, content);
                            break;
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    addSystemMessage("Error handling message: " + e.getMessage());
                }
            });
        } else {
            Platform.runLater(() -> addSystemMessage("Message: " + message));
        }
    }

    private void handleFileTransfer(String username, String content) {
        String[] fileParts = content.split("\\|", 4);
        if (fileParts.length >= 4) {
            String fileId = fileParts[0];
            String fileName = fileParts[1];
            long fileSize = Long.parseLong(fileParts[2]);
            String base64Data = fileParts[3];

            // Ask user if they want to save the file
            Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
            alert.setTitle("Incoming File");
            alert.setHeaderText("File from " + username);
            alert.setContentText("File: " + fileName + "\n" +
                    "Size: " + formatFileSize(fileSize) + "\n" +
                    "Do you want to save this file?");

            ButtonType yesButton = new ButtonType("Save", ButtonBar.ButtonData.YES);
            ButtonType noButton = new ButtonType("Cancel", ButtonBar.ButtonData.NO);
            alert.getButtonTypes().setAll(yesButton, noButton);

            alert.showAndWait().ifPresent(response -> {
                if (response == yesButton) {
                    // Save the file
                    saveReceivedFile(username, fileName, fileSize, base64Data);
                } else {
                    addSystemMessage("Cancelled receiving file: " + fileName);
                }
            });
        }
    }

    private void saveReceivedFile(String sender, String fileName, long fileSize, String base64Data) {
        try {
            // Decode base64 data
            byte[] fileBytes = Base64.getDecoder().decode(base64Data);

            // Generate unique filename to avoid conflicts
            String uniqueFileName = getUniqueFileName(fileName);
            File outputFile = new File(downloadsFolder, uniqueFileName);

            // Write file to disk
            try (FileOutputStream fos = new FileOutputStream(outputFile)) {
                fos.write(fileBytes);
            }

            addFileMessage(fileName, fileSize, false, "Saved");
            addSystemMessage("File saved: " + outputFile.getAbsolutePath());

            // Ask if user wants to open the file
            Alert openAlert = new Alert(Alert.AlertType.CONFIRMATION);
            openAlert.setTitle("File Saved");
            openAlert.setHeaderText("File saved successfully");
            openAlert.setContentText("File: " + fileName + "\n" +
                    "Saved to: " + outputFile.getAbsolutePath() + "\n" +
                    "Do you want to open the file?");

            openAlert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.OK) {
                    try {
                        java.awt.Desktop.getDesktop().open(outputFile);
                    } catch (Exception e) {
                        showAlert("Error", "Could not open file: " + e.getMessage(), Alert.AlertType.ERROR);
                    }
                }
            });

        } catch (Exception e) {
            e.printStackTrace();
            addSystemMessage("Failed to save file: " + fileName);
            showAlert("Error", "Failed to save file: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private String getUniqueFileName(String originalName) {
        File file = new File(downloadsFolder, originalName);
        if (!file.exists()) {
            return originalName;
        }

        // Add timestamp to filename
        String nameWithoutExt = originalName;
        String extension = "";
        int dotIndex = originalName.lastIndexOf('.');
        if (dotIndex > 0) {
            nameWithoutExt = originalName.substring(0, dotIndex);
            extension = originalName.substring(dotIndex);
        }

        String timestamp = "_" + System.currentTimeMillis();
        return nameWithoutExt + timestamp + extension;
    }

    private boolean isOwnMessageEcho(String content, String type) {
        if (type.equals("CHAT_MESSAGE") || type.equals("CHAT")) {
            return content.contains(lastMessageId) ||
                    (messageField != null && content.equals(messageField.getText().trim()));
        }
        return false;
    }

    private void handleChatMessage(String username, String content) {
        if (username.equals(currentUser)) {
            System.out.println("Server echo of own message: " + content);
        } else {
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
                Label messageLabel = new Label("System: " + message);
                messageLabel.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white; -fx-padding: 8 12; -fx-background-radius: 10; -fx-wrap-text: true;");
                messageLabel.setMaxWidth(600);
                chatBox.getChildren().add(messageLabel);
                scrollToBottom();
            }
        });
    }

    private void addFileMessage(String fileName, long fileSize, boolean isOwnFile, String status) {
        Platform.runLater(() -> {
            if (chatBox != null) {
                String fileInfo = (isOwnFile ? "You sent: " : "Received: ") + fileName;
                if (fileSize > 0) {
                    fileInfo += " (" + formatFileSize(fileSize) + ")";
                }
                fileInfo += " - " + status;

                Label fileLabel = new Label(fileInfo);
                if (isOwnFile) {
                    fileLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #3498db; -fx-background-color: #e3f2fd; -fx-padding: 5 10; -fx-border-radius: 5;");
                } else {
                    fileLabel.setStyle("-fx-font-size: 14px; -fx-text-fill: #2ecc71; -fx-background-color: #e8f5e9; -fx-padding: 5 10; -fx-border-radius: 5;");
                }

                // Make it clickable to open the file
                fileLabel.setOnMouseClicked(event -> {
                    if (!isOwnFile && status.equals("Saved")) {
                        openSavedFile(fileName);
                    }
                });

                chatBox.getChildren().add(fileLabel);
                scrollToBottom();
            }
        });
    }

    private void openSavedFile(String fileName) {
        try {
            // Find the file in downloads folder
            File folder = new File(downloadsFolder);
            File[] files = folder.listFiles((dir, name) -> name.startsWith(fileName.replaceFirst("[.][^.]+$", "")));

            if (files != null && files.length > 0) {
                // Open the most recent file with this name
                File fileToOpen = files[0];
                for (File file : files) {
                    if (file.lastModified() > fileToOpen.lastModified()) {
                        fileToOpen = file;
                    }
                }

                java.awt.Desktop.getDesktop().open(fileToOpen);
            } else {
                showAlert("File Not Found",
                        "Could not find saved file: " + fileName + "\n" +
                                "Check the downloads folder: " + folder.getAbsolutePath(),
                        Alert.AlertType.WARNING);
            }
        } catch (Exception e) {
            showAlert("Error", "Could not open file: " + e.getMessage(), Alert.AlertType.ERROR);
        }
    }

    private void updateConnectionUI() {
        Platform.runLater(() -> {
            boolean isConnected = webSocketClient != null && webSocketClient.isConnected();

            if (statusLabel != null) {
                if (isConnected) {
                    statusLabel.setText("Connected");
                    statusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                } else {
                    statusLabel.setText("Disconnected");
                    statusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                }
            }

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

            if (sendButton != null) {
                sendButton.setDisable(!isConnected);
                if (!isConnected) {
                    sendButton.setTooltip(new Tooltip("Not connected to server"));
                } else {
                    sendButton.setTooltip(null);
                }
            }

            if (fileButton != null) {
                fileButton.setDisable(!isConnected);
                if (!isConnected) {
                    fileButton.setTooltip(new Tooltip("Not connected to server"));
                } else {
                    fileButton.setTooltip(null);
                }
            }
        });
    }

    private void handleSettingsUpdate() {
        String newServerUrl = HelloApplication.getCurrentServerUrl();
        boolean needsReconnect = false;

        if (webSocketClient == null) {
            needsReconnect = true;
        } else {
            try {
                String clientUrl = webSocketClient.getServerUrl();
                needsReconnect = !clientUrl.equals(newServerUrl);
            } catch (Exception e) {
                System.out.println("getServerUrl() not available, reconnecting...");
                needsReconnect = true;
            }
        }

        if (needsReconnect) {
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

    private String formatFileSize(long size) {
        if (size < 1024) return size + " B";
        if (size < 1024 * 1024) return String.format("%.1f KB", size / 1024.0);
        if (size < 1024 * 1024 * 1024) return String.format("%.1f MB", size / (1024.0 * 1024.0));
        return String.format("%.1f GB", size / (1024.0 * 1024.0 * 1024.0));
    }

    // Simple class to track file transfer info
    private class FileTransferInfo {
        String fileId;
        String fileName;
        long fileSize;
        StringBuilder fileData;

        public FileTransferInfo(String fileId, String fileName, long fileSize) {
            this.fileId = fileId;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.fileData = new StringBuilder();
        }
    }
}