package org.example.zoom;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.collections.transformation.SortedList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

public class ScheduleController {

    @FXML
    private TableView<Meeting> scheduleTable;
    @FXML
    private TableColumn<Meeting, String> titleColumn;
    @FXML
    private TableColumn<Meeting, String> dateColumn;
    @FXML
    private TableColumn<Meeting, String> timeColumn;
    @FXML
    private TableColumn<Meeting, String> statusColumn;

    @FXML
    private TextField meetingTitleField;
    @FXML
    private DatePicker meetingDatePicker;
    @FXML
    private TextField meetingTimeField;
    @FXML
    private TextArea meetingDescriptionField;
    @FXML
    private TextField searchField;

    @FXML
    private Button addButton;
    @FXML
    private Button editButton;
    @FXML
    private Button cancelButton;
    @FXML
    private Button startNowButton;
    @FXML
    private Button exportButton;

    private String currentUser;
    private ObservableList<Meeting> meetings = FXCollections.observableArrayList();
    private FilteredList<Meeting> filteredMeetings;
    private DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");

    public void setUser(String username) {
        this.currentUser = username;
        loadMeetings();
        updateButtonStates();
    }

    @FXML
    public void initialize() {
        setupTableColumns();
        setupSearchFilter();
        setupKeyboardShortcuts();
        setupTableSelectionListener();

        // Set today as default date
        meetingDatePicker.setValue(LocalDate.now());

        // Set default time to next hour
        meetingTimeField.setText(LocalTime.now().plusHours(1).format(timeFormatter));
    }

    private void setupTableColumns() {
        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        timeColumn.setCellValueFactory(new PropertyValueFactory<>("time"));
        statusColumn.setCellValueFactory(new PropertyValueFactory<>("status"));

        // Custom cell factory for status column with colors
        statusColumn.setCellFactory(column -> new TableCell<Meeting, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    switch (item) {
                        case "Scheduled":
                            setStyle("-fx-text-fill: #f39c12; -fx-font-weight: bold;");
                            break;
                        case "Completed":
                            setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
                            break;
                        case "Cancelled":
                            setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
                            break;
                        case "In Progress":
                            setStyle("-fx-text-fill: #3498db; -fx-font-weight: bold;");
                            break;
                        default:
                            setStyle("");
                    }
                }
            }
        });

        scheduleTable.setItems(meetings);
    }

    private void setupSearchFilter() {
        filteredMeetings = new FilteredList<>(meetings, p -> true);

        searchField.textProperty().addListener((observable, oldValue, newValue) -> {
            filteredMeetings.setPredicate(meeting -> {
                if (newValue == null || newValue.isEmpty()) {
                    return true;
                }

                String lowerCaseFilter = newValue.toLowerCase();
                return meeting.getTitle().toLowerCase().contains(lowerCaseFilter) ||
                        meeting.getDate().toLowerCase().contains(lowerCaseFilter) ||
                        meeting.getTime().toLowerCase().contains(lowerCaseFilter);
            });
        });

        SortedList<Meeting> sortedMeetings = new SortedList<>(filteredMeetings);
        sortedMeetings.comparatorProperty().bind(scheduleTable.comparatorProperty());
        scheduleTable.setItems(sortedMeetings);
    }

    private void setupKeyboardShortcuts() {
        // Enter key to add meeting
        meetingTitleField.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                onAddMeetingClick();
            }
        });

        // Delete key to cancel selected meeting
        scheduleTable.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.DELETE) {
                onCancelClick();
            }
        });
    }

    private void setupTableSelectionListener() {
        scheduleTable.getSelectionModel().selectedItemProperty().addListener(
                (observable, oldValue, newValue) -> updateButtonStates());
    }

    private void updateButtonStates() {
        Meeting selected = scheduleTable.getSelectionModel().getSelectedItem();
        boolean hasSelection = selected != null;

        editButton.setDisable(!hasSelection);
        cancelButton.setDisable(!hasSelection);
        startNowButton.setDisable(!hasSelection || !"Scheduled".equals(selected.getStatus()));

        // Update button text based on selection
        if (hasSelection && "Scheduled".equals(selected.getStatus())) {
            startNowButton.setText("Start Meeting Now");
            startNowButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white;");
        } else if (hasSelection && "In Progress".equals(selected.getStatus())) {
            startNowButton.setText("Join Meeting");
            startNowButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        } else {
            startNowButton.setStyle("-fx-background-color: #95a5a6; -fx-text-fill: white;");
        }
    }

    private void loadMeetings() {
        meetings.clear();
        meetings.addAll(Database.getMeetings(currentUser));
        updateMeetingStatuses();
    }

    private void updateMeetingStatuses() {
        LocalDate today = LocalDate.now();
        for (Meeting meeting : meetings) {
            LocalDate meetingDate = LocalDate.parse(meeting.getDate());
            if (meetingDate.isBefore(today)) {
                meeting.setStatus("Completed");
            } else if (meetingDate.isEqual(today)) {
                // Check if meeting time has passed
                try {
                    LocalTime meetingTime = LocalTime.parse(meeting.getTime(), timeFormatter);
                    if (LocalTime.now().isAfter(meetingTime)) {
                        meeting.setStatus("Completed");
                    } else {
                        meeting.setStatus("Scheduled");
                    }
                } catch (DateTimeParseException e) {
                    meeting.setStatus("Scheduled");
                }
            } else {
                meeting.setStatus("Scheduled");
            }
        }
    }

    @FXML
    protected void onAddMeetingClick() {
        String title = meetingTitleField.getText().trim();
        LocalDate date = meetingDatePicker.getValue();
        String time = meetingTimeField.getText().trim();
        String description = meetingDescriptionField.getText().trim();

        if (validateInput(title, date, time)) {
            String formattedTime = formatTime(time);
            if (formattedTime == null) {
                showAlert("Invalid Time", "Please enter time in HH:MM format (e.g., 14:30)");
                return;
            }

            // FIXED: Use only 4 parameters for saveMeeting
            boolean saved = Database.saveMeeting(currentUser, title, date.toString(), formattedTime);
            if (saved) {
                // Store description locally in the Meeting object (not in database)
                Meeting newMeeting = new Meeting(title, date.toString(), formattedTime, description);
                meetings.add(newMeeting);
                clearForm();
                showAlert("Success", "✅ Meeting scheduled successfully!");
            } else {
                showAlert("Error", "❌ Failed to save meeting to database.");
            }
        }
    }

    @FXML
    protected void onEditMeetingClick() {
        Meeting selected = scheduleTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("No Selection", "❌ Please select a meeting to edit.");
            return;
        }

        // Pre-fill form with selected meeting data
        meetingTitleField.setText(selected.getTitle());
        meetingDatePicker.setValue(LocalDate.parse(selected.getDate()));
        meetingTimeField.setText(selected.getTime());
        meetingDescriptionField.setText(selected.getDescription());

        // Remove the old meeting
        meetings.remove(selected);
        Database.deleteMeeting(currentUser, selected.getTitle(), selected.getDate(), selected.getTime());
    }

    @FXML
    protected void onCancelClick() {
        Meeting selected = scheduleTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("No Selection", "❌ Please select a meeting to cancel.");
            return;
        }

        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirm Cancellation");
        confirmation.setHeaderText("Cancel Meeting");
        confirmation.setContentText("Are you sure you want to cancel: " + selected.getTitle() + "?");

        Optional<ButtonType> result = confirmation.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            boolean deleted = Database.deleteMeeting(currentUser, selected.getTitle(), selected.getDate(), selected.getTime());
            if (deleted) {
                selected.setStatus("Cancelled");
                showAlert("Success", "✅ Meeting cancelled successfully.");
            } else {
                showAlert("Error", "❌ Failed to cancel meeting.");
            }
        }
    }

    @FXML
    protected void onStartNowClick() {
        Meeting selected = scheduleTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("No Selection", "❌ Please select a meeting to start.");
            return;
        }

        try {
            // Create a new meeting with the scheduled meeting's title
            String meetingId = HelloApplication.createNewMeeting();
            selected.setStatus("In Progress");

            showAlert("Meeting Started", "✅ Meeting started with ID: " + meetingId +
                    "\nTitle: " + selected.getTitle());

            // Navigate to meeting view
            HelloApplication.setRoot("meeting-view.fxml");

        } catch (Exception e) {
            showAlert("Error", "❌ Failed to start meeting: " + e.getMessage());
        }
    }

    @FXML
    protected void onQuickMeetingClick() {
        String title = "Quick Meeting - " + LocalDate.now().toString();
        String meetingId = HelloApplication.createNewMeeting();

        // Add to schedule (without description since Database doesn't support it)
        Meeting quickMeeting = new Meeting(title, LocalDate.now().toString(),
                LocalTime.now().format(timeFormatter), "Quick meeting");
        quickMeeting.setStatus("In Progress");
        meetings.add(quickMeeting);

        // FIXED: Use only 4 parameters
        Database.saveMeeting(currentUser, title, LocalDate.now().toString(),
                LocalTime.now().format(timeFormatter));

        showAlert("Quick Meeting", "✅ Quick meeting started with ID: " + meetingId);

        try {
            HelloApplication.setRoot("meeting-view.fxml");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @FXML
    protected void onExportClick() {
        // Simple export functionality
        StringBuilder exportData = new StringBuilder();
        exportData.append("Meeting Schedule Export\n");
        exportData.append("Generated on: ").append(LocalDate.now()).append("\n\n");

        for (Meeting meeting : meetings) {
            exportData.append(String.format("Title: %s\nDate: %s\nTime: %s\nStatus: %s\nDescription: %s\n\n",
                    meeting.getTitle(), meeting.getDate(), meeting.getTime(),
                    meeting.getStatus(), meeting.getDescription()));
        }

        TextArea exportArea = new TextArea(exportData.toString());
        exportArea.setEditable(false);

        ScrollPane scrollPane = new ScrollPane(exportArea);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefSize(400, 300);

        Alert exportAlert = new Alert(Alert.AlertType.INFORMATION);
        exportAlert.setTitle("Export Schedule");
        exportAlert.setHeaderText("Your Meeting Schedule");
        exportAlert.getDialogPane().setContent(scrollPane);
        exportAlert.showAndWait();
    }

    @FXML
    protected void onClearFormClick() {
        clearForm();
    }

    @FXML
    protected void onBackClick() throws Exception {
        HelloApplication.setRoot("dashboard-view.fxml");
    }

    private boolean validateInput(String title, LocalDate date, String time) {
        if (title.isEmpty() || date == null || time.isEmpty()) {
            showAlert("Validation Error", "❌ Please fill all required fields.");
            return false;
        }

        if (date.isBefore(LocalDate.now())) {
            showAlert("Validation Error", "❌ Meeting date cannot be in the past.");
            return false;
        }

        if (title.length() < 3) {
            showAlert("Validation Error", "❌ Meeting title must be at least 3 characters.");
            return false;
        }

        return true;
    }

    private String formatTime(String time) {
        try {
            // Try to parse and format the time
            LocalTime parsedTime = LocalTime.parse(time, timeFormatter);
            return parsedTime.format(timeFormatter);
        } catch (DateTimeParseException e) {
            return null;
        }
    }

    private void clearForm() {
        meetingTitleField.clear();
        meetingDatePicker.setValue(LocalDate.now());
        meetingTimeField.setText(LocalTime.now().plusHours(1).format(timeFormatter));
        meetingDescriptionField.clear();
        meetingTitleField.requestFocus();
    }

    private void showAlert(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    // Enhanced Meeting class (description stored locally only)
    public static class Meeting {
        private final String title;
        private final String date;
        private final String time;
        private final String description;
        private String status;

        public Meeting(String title, String date, String time, String description) {
            this.title = title;
            this.date = date;
            this.time = time;
            this.description = description;
            this.status = "Scheduled";
        }

        public String getTitle() { return title; }
        public String getDate() { return date; }
        public String getTime() { return time; }
        public String getDescription() { return description; }
        public String getStatus() { return status; }

        public void setStatus(String status) { this.status = status; }
    }
}