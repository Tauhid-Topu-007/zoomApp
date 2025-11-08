package org.example.zoom;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.scene.control.ScrollPane;

import java.util.List;

public class ContactController {

    @FXML private TextField nameField;
    @FXML private TextField emailField;
    @FXML private TextField phoneField;
    @FXML private ListView<String> contactList;
    @FXML private Button backButton;
    @FXML private ScrollPane mainScrollPane;
    @FXML private VBox mainContent;

    private String loggedInUser;
    private ObservableList<String> contactsObservable = FXCollections.observableArrayList();

    private int selectedContactId = -1; // store selected contact id for editing

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

        // Get the actual logged-in user from HelloApplication
        loggedInUser = HelloApplication.getLoggedInUser();
        if (loggedInUser == null) {
            showAlert(Alert.AlertType.ERROR, "No user logged in! Please log in first.");
            return;
        }

        loadContacts();

        // When selecting a contact from the list, populate the fields
        contactList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                try {
                    // Extract ID from the display string (format: "ID - Name (Phone)")
                    String[] parts = newVal.split(" - ");
                    if (parts.length > 0) {
                        int id = Integer.parseInt(parts[0]);
                        Database.Contact contact = Database.getContactById(id);
                        if (contact != null) {
                            selectedContactId = id;
                            nameField.setText(contact.getName());
                            emailField.setText(contact.getEmail());
                            phoneField.setText(contact.getPhone());
                        }
                    }
                } catch (NumberFormatException e) {
                    System.err.println("Error parsing contact ID: " + e.getMessage());
                }
            }
        });
    }

    private void loadContacts() {
        contactsObservable.clear();
        if (loggedInUser != null) {
            List<Database.Contact> contacts = Database.getContacts(loggedInUser);
            for (Database.Contact c : contacts) {
                contactsObservable.add(c.getId() + " - " + c.getName() + " (" + c.getPhone() + ")");
            }
        }
        contactList.setItems(contactsObservable);
    }

    @FXML
    protected void onAddClick() {
        if (loggedInUser == null) {
            showAlert(Alert.AlertType.ERROR, "No user logged in!");
            return;
        }

        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        String phone = phoneField.getText().trim();

        if (!name.isEmpty()) {
            if (Database.addContact(loggedInUser, name, email, phone)) {
                showAlert(Alert.AlertType.INFORMATION, "✅ Contact added successfully!");
                clearFields();
                loadContacts();
            } else {
                showAlert(Alert.AlertType.ERROR, "❌ Failed to add contact. Please try again.");
            }
        } else {
            showAlert(Alert.AlertType.WARNING, "⚠️ Name is required!");
        }
    }

    @FXML
    protected void onDeleteClick() {
        String selected = contactList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            try {
                int id = Integer.parseInt(selected.split(" - ")[0]);
                if (Database.deleteContact(id)) {
                    showAlert(Alert.AlertType.INFORMATION, "✅ Contact deleted successfully!");
                    clearFields();
                    loadContacts();
                } else {
                    showAlert(Alert.AlertType.ERROR, "❌ Failed to delete contact.");
                }
            } catch (NumberFormatException e) {
                showAlert(Alert.AlertType.ERROR, "❌ Invalid contact selection.");
            }
        } else {
            showAlert(Alert.AlertType.WARNING, "⚠️ Please select a contact to delete.");
        }
    }

    @FXML
    protected void onUpdateClick() {
        if (selectedContactId != -1) {
            String name = nameField.getText().trim();
            String email = emailField.getText().trim();
            String phone = phoneField.getText().trim();

            if (!name.isEmpty()) {
                if (Database.updateContact(selectedContactId, name, email, phone)) {
                    showAlert(Alert.AlertType.INFORMATION, "✅ Contact updated successfully!");
                    clearFields();
                    loadContacts();
                    selectedContactId = -1; // reset after update
                } else {
                    showAlert(Alert.AlertType.ERROR, "❌ Failed to update contact.");
                }
            } else {
                showAlert(Alert.AlertType.WARNING, "⚠️ Name is required!");
            }
        } else {
            showAlert(Alert.AlertType.WARNING, "⚠️ Please select a contact to update.");
        }
    }

    @FXML
    protected void onClearClick() {
        clearFields();
        contactList.getSelectionModel().clearSelection();
    }

    @FXML
    protected void onBackClick() throws Exception {
        HelloApplication.setRoot("dashboard-view.fxml");
    }

    @FXML
    protected void onSearchClick() {
        // Optional: Add search functionality
        showAlert(Alert.AlertType.INFORMATION, "🔍 Search functionality coming soon!");
    }

    @FXML
    protected void onExportClick() {
        // Optional: Add export functionality
        showAlert(Alert.AlertType.INFORMATION, "📤 Export functionality coming soon!");
    }

    private void clearFields() {
        nameField.clear();
        emailField.clear();
        phoneField.clear();
        selectedContactId = -1;
    }

    private void showAlert(Alert.AlertType type, String msg) {
        Alert alert = new Alert(type, msg);
        alert.setHeaderText(null);
        alert.showAndWait();
    }
}