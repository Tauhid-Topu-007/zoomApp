package org.example.zoom;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.ListView;
import javafx.scene.control.cell.TextFieldListCell;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

public class RecordingsController {

    @FXML
    private ListView<RecordingFile> recordingsList;

    private ObservableList<RecordingFile> recordings;
    private Stage stage;

    public static class RecordingFile {
        private File file;
        private String displayName;
        private long fileSize;
        private String creationDate;
        private String duration;

        public RecordingFile(File file) {
            this.file = file;
            this.displayName = file.getName();
            this.fileSize = file.length();
            this.creationDate = getFormattedCreationDate(file);
            this.duration = "Unknown"; // Would need actual media analysis to get duration
        }

        private String getFormattedCreationDate(File file) {
            try {
                Path path = file.toPath();
                BasicFileAttributes attrs = Files.readAttributes(path, BasicFileAttributes.class);
                FileTime creationTime = attrs.creationTime();
                return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(creationTime.toMillis());
            } catch (IOException e) {
                return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(file.lastModified());
            }
        }

        public File getFile() { return file; }
        public String getDisplayName() { return displayName; }
        public long getFileSize() { return fileSize; }
        public String getCreationDate() { return creationDate; }
        public String getDuration() { return duration; }

        public String getFormattedFileSize() {
            return formatFileSize(fileSize);
        }

        private String formatFileSize(long bytes) {
            if (bytes < 1024) return bytes + " B";
            int exp = (int) (Math.log(bytes) / Math.log(1024));
            String pre = "KMGTPE".charAt(exp-1) + "";
            return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
        }

        @Override
        public String toString() {
            return String.format("%s (%s - %s)", displayName, getFormattedFileSize(), creationDate);
        }
    }

    @FXML
    public void initialize() {
        recordings = FXCollections.observableArrayList();
        recordingsList.setItems(recordings);

        // Custom cell factory for better display
        recordingsList.setCellFactory(param -> new TextFieldListCell<RecordingFile>() {
            @Override
            public void updateItem(RecordingFile item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(item.toString());
                    setTooltip(new javafx.scene.control.Tooltip(
                            String.format("File: %s\nSize: %s\nCreated: %s\nDuration: %s",
                                    item.getDisplayName(),
                                    item.getFormattedFileSize(),
                                    item.getCreationDate(),
                                    item.getDuration())
                    ));
                }
            }
        });

        // Load recordings on initialization
        loadRecordings();
    }

    public void setStage(Stage stage) {
        this.stage = stage;
    }

    private void loadRecordings() {
        recordings.clear();

        // Get recordings from default location and user-specified locations
        List<File> recordingFiles = findRecordingFiles();

        if (recordingFiles.isEmpty()) {
            System.out.println("📁 No recording files found");
            return;
        }

        // Convert to RecordingFile objects and sort by creation date (newest first)
        List<RecordingFile> recordingFileObjects = recordingFiles.stream()
                .map(RecordingFile::new)
                .sorted(Comparator.comparing(RecordingFile::getCreationDate).reversed())
                .collect(Collectors.toList());

        recordings.addAll(recordingFileObjects);
        System.out.println("✅ Loaded " + recordings.size() + " recording files");
    }

    private List<File> findRecordingFiles() {
        List<File> recordingFiles = new ArrayList<>();

        // Check default recording locations
        String[] possiblePaths = {
                System.getProperty("user.home") + File.separator + "ZoomRecordings",
                System.getProperty("user.home") + File.separator + "Documents" + File.separator + "ZoomRecordings",
                System.getProperty("user.home") + File.separator + "Videos" + File.separator + "ZoomRecordings",
                "recordings" // Current directory
        };

        for (String path : possiblePaths) {
            File directory = new File(path);
            if (directory.exists() && directory.isDirectory()) {
                File[] files = directory.listFiles((dir, name) ->
                        name.toLowerCase().endsWith(".mp4") ||
                                name.toLowerCase().endsWith(".avi") ||
                                name.toLowerCase().endsWith(".mov") ||
                                name.toLowerCase().endsWith(".mkv")
                );

                if (files != null) {
                    for (File file : files) {
                        if (!recordingFiles.contains(file)) {
                            recordingFiles.add(file);
                        }
                    }
                }
            }
        }

        return recordingFiles;
    }

    @FXML
    protected void onRefreshClick() {
        loadRecordings();
        showPopup("Refresh", "🔄 Recordings list refreshed!");
    }

    @FXML
    protected void onPlayClick() {
        RecordingFile selected = recordingsList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            playRecording(selected.getFile());
        } else {
            showPopup("Warning", "⚠ Please select a recording to play!");
        }
    }

    @FXML
    protected void onDownloadClick() {
        RecordingFile selected = recordingsList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            downloadRecording(selected.getFile());
        } else {
            showPopup("Warning", "⚠ Please select a recording to download!");
        }
    }

    @FXML
    protected void onDeleteClick() {
        RecordingFile selected = recordingsList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            deleteRecording(selected);
        } else {
            showPopup("Warning", "⚠ Please select a recording to delete!");
        }
    }

    @FXML
    protected void onOpenFolderClick() {
        DirectoryChooser directoryChooser = new DirectoryChooser();
        directoryChooser.setTitle("Open Recordings Folder");
        directoryChooser.setInitialDirectory(new File(System.getProperty("user.home")));

        File selectedDirectory = directoryChooser.showDialog(stage);
        if (selectedDirectory != null) {
            openFolder(selectedDirectory);
        }
    }

    private void playRecording(File recordingFile) {
        try {
            // Check if file exists
            if (!recordingFile.exists()) {
                showPopup("Playback Error", "❌ Recording file not found: " + recordingFile.getName());
                return;
            }

            // Open with system default media player
            java.awt.Desktop.getDesktop().open(recordingFile);
            showPopup("Play", "▶ Playing recording: " + recordingFile.getName());

            System.out.println("▶ Playing recording: " + recordingFile.getAbsolutePath());

        } catch (IOException e) {
            System.err.println("❌ Failed to play recording: " + e.getMessage());
            showPopup("Playback Error", "❌ Failed to play recording: " + e.getMessage());
        }
    }

    private void downloadRecording(File recordingFile) {
        // In a real application, this would initiate a download process
        // For now, we'll just show the file location

        String message = String.format(
                "⬇ Ready to download:\n" +
                        "File: %s\n" +
                        "Size: %s\n" +
                        "Location: %s",
                recordingFile.getName(),
                formatFileSize(recordingFile.length()),
                recordingFile.getParent()
        );

        showPopup("Download", message);
        System.out.println("⬇ Download initiated: " + recordingFile.getAbsolutePath());
    }

    private void deleteRecording(RecordingFile recording) {
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Delete Recording");
        confirmation.setHeaderText("Delete Recording");
        confirmation.setContentText(
                "Are you sure you want to delete this recording?\n" +
                        "File: " + recording.getDisplayName() + "\n" +
                        "Size: " + recording.getFormattedFileSize() + "\n" +
                        "This action cannot be undone."
        );

        confirmation.showAndWait().ifPresent(response -> {
            if (response == javafx.scene.control.ButtonType.OK) {
                if (recording.getFile().delete()) {
                    recordings.remove(recording);
                    showPopup("Delete", "🗑 Deleted recording: " + recording.getDisplayName());
                    System.out.println("🗑 Deleted recording: " + recording.getFile().getAbsolutePath());
                } else {
                    showPopup("Delete Error", "❌ Failed to delete recording: " + recording.getDisplayName());
                }
            }
        });
    }

    private void openFolder(File folder) {
        try {
            java.awt.Desktop.getDesktop().open(folder);
            showPopup("Open Folder", "📁 Opened folder: " + folder.getAbsolutePath());
        } catch (IOException e) {
            showPopup("Open Folder Error", "❌ Failed to open folder: " + e.getMessage());
        }
    }

    @FXML
    protected void onBackClick() throws Exception {
        HelloApplication.setRoot("dashboard-view.fxml");
    }

    private String formatFileSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp-1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    private void showPopup(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Public method to refresh recordings from other controllers
    public void refreshRecordings() {
        loadRecordings();
    }

    // Method to get the most recent recording
    public File getMostRecentRecording() {
        if (recordings.isEmpty()) {
            return null;
        }
        return recordings.get(0).getFile();
    }

    // Method to get recordings count
    public int getRecordingsCount() {
        return recordings.size();
    }
}