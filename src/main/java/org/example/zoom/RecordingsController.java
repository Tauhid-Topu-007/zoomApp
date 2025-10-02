package org.example.zoom;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ListView;

public class RecordingsController {

    @FXML
    private ListView<String> recordingsList;

    private ObservableList<String> recordings;

    @FXML
    public void initialize() {
        // Dummy recordings list (replace with actual recordings from disk or DB)
        recordings = FXCollections.observableArrayList(
                "Meeting_2025-10-01_10AM.mp4",
                "TeamCall_2025-10-02_02PM.mp4",
                "ProjectUpdate_2025-10-02_05PM.mp4"
        );
        recordingsList.setItems(recordings);
    }

    @FXML
    protected void onPlayClick() {
        String selected = recordingsList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            showPopup("Play", "▶ Playing recording: " + selected);
            // Later: integrate with MediaPlayer for actual playback
        } else {
            showPopup("Warning", "⚠ Please select a recording to play!");
        }
    }

    @FXML
    protected void onDownloadClick() {
        String selected = recordingsList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            showPopup("Download", "⬇ Downloading recording: " + selected);
            // Later: implement actual file download
        } else {
            showPopup("Warning", "⚠ Please select a recording to download!");
        }
    }

    @FXML
    protected void onDeleteClick() {
        String selected = recordingsList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            recordings.remove(selected);
            showPopup("Delete", "🗑 Deleted recording: " + selected);
            // Later: delete actual file from disk or DB
        } else {
            showPopup("Warning", "⚠ Please select a recording to delete!");
        }
    }

    @FXML
    protected void onBackClick() throws Exception {
        HelloApplication.setRoot("dashboard-view.fxml");
    }

    private void showPopup(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }
}
