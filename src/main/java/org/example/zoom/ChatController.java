package org.example.zoom;

import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.VBox;

public class ChatController {

    @FXML
    private VBox chatBox;

    @FXML
    private TextField messageField;

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
    protected void onBackClick() throws Exception {
        HelloApplication.setRoot("dashboard-view.fxml"); // back to dashboard
    }
}
