package org.example.zoom;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;

import java.time.LocalDate;

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
    private TextField meetingTitleField;
    @FXML
    private DatePicker meetingDatePicker;
    @FXML
    private TextField meetingTimeField; // Example: "14:30"

    private String currentUser; // logged-in user
    private ObservableList<Meeting> meetings = FXCollections.observableArrayList();

    // Called from DashboardController
    public void setUser(String username) {
        this.currentUser = username;
        loadMeetings();
    }

    @FXML
    public void initialize() {
        titleColumn.setCellValueFactory(new PropertyValueFactory<>("title"));
        dateColumn.setCellValueFactory(new PropertyValueFactory<>("date"));
        timeColumn.setCellValueFactory(new PropertyValueFactory<>("time"));
        scheduleTable.setItems(meetings);
    }

    private void loadMeetings() {
        meetings.clear();
        meetings.addAll(Database.getMeetings(currentUser));
    }

    @FXML
    protected void onAddMeetingClick() {
        String title = meetingTitleField.getText().trim();
        LocalDate date = meetingDatePicker.getValue();
        String time = meetingTimeField.getText().trim();

        if (!title.isEmpty() && date != null && !time.isEmpty()) {
            boolean saved = Database.saveMeeting(currentUser, title, date.toString(), time);
            if (saved) {
                meetings.add(new Meeting(title, date.toString(), time));
                meetingTitleField.clear();
                meetingDatePicker.setValue(null);
                meetingTimeField.clear();
            } else {
                showAlert("❌ Failed to save meeting to database.");
            }
        } else {
            showAlert("Please fill all fields before adding a meeting.");
        }
    }

    @FXML
    protected void onBackClick() throws Exception {
        HelloApplication.setRoot("dashboard-view.fxml");
    }
    @FXML
    protected void onCancelClick() {
        Meeting selected = scheduleTable.getSelectionModel().getSelectedItem();
        if (selected == null) {
            showAlert("❌ Please select a meeting to cancel.");
            return;
        }

        boolean deleted = Database.deleteMeeting(currentUser,
                selected.getTitle(), selected.getDate(), selected.getTime());

        if (deleted) {
            meetings.remove(selected);
            showAlert("✅ Meeting cancelled successfully.");
        } else {
            showAlert("❌ Failed to cancel meeting.");
        }
    }


    private void showAlert(String msg) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.setTitle("Warning");
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }

    // Inner class for meeting data
    public static class Meeting {
        private final String title;
        private final String date;
        private final String time;

        public Meeting(String title, String date, String time) {
            this.title = title;
            this.date = date;
            this.time = time;
        }

        public String getTitle() { return title; }
        public String getDate() { return date; }
        public String getTime() { return time; }
    }
}
