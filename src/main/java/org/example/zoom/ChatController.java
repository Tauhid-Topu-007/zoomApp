package org.example.zoom;

import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;

public class ChatController {

    @FXML
    private VBox chatBox;

    @FXML
    private TextField messageField;

    private Stage stage; // reference to the main stage for FileChooser

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    @FXML
    public void initialize() {
        // Handle pressing Enter to send message
        messageField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                onSendClick();
            }
        });
    }

    @FXML
    protected void onSendClick() {
        String message = messageField.getText().trim();
        if (!message.isEmpty()) {
            Label messageLabel = new Label("You: " + message);
            messageLabel.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-padding: 8 12; -fx-background-radius: 10;");
            chatBox.getChildren().add(messageLabel);
            messageField.clear();
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
            // Show file as clickable item in chat
            Hyperlink fileLink = new Hyperlink("📎 " + file.getName());
            fileLink.setStyle("-fx-font-size: 14px;");
            fileLink.setOnAction(e -> downloadFile(file));
            chatBox.getChildren().add(fileLink);
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
                Alert alert = new Alert(Alert.AlertType.INFORMATION, "File downloaded successfully!");
                alert.showAndWait();
            } catch (IOException ex) {
                Alert alert = new Alert(Alert.AlertType.ERROR, "Error downloading file: " + ex.getMessage());
                alert.showAndWait();
            }
        }
    }

    @FXML
    protected void onBackClick() throws Exception {
        HelloApplication.setRoot("dashboard-view.fxml"); // back to dashboard
    }
}
