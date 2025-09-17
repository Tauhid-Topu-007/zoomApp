package org.example.zoom;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.fxml.FXML;
import javafx.scene.control.*;

import java.util.List;

public class ContactController {

    @FXML private TextField nameField;
    @FXML private TextField emailField;
    @FXML private TextField phoneField;
    @FXML private ListView<String> contactList;

    private String loggedInUser = "testuser"; // TODO: replace with actual logged-in user
    private ObservableList<String> contactsObservable = FXCollections.observableArrayList();

    private int selectedContactId = -1; // store selected contact id for editing

    @FXML
    public void initialize() {
        loadContacts();

        // When selecting a contact from the list, populate the fields
        contactList.getSelectionModel().selectedItemProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal != null) {
                int id = Integer.parseInt(newVal.split(" - ")[0]);
                Database.Contact contact = Database.getContactById(id);
                if (contact != null) {
                    selectedContactId = id;
                    nameField.setText(contact.getName());
                    emailField.setText(contact.getEmail());
                    phoneField.setText(contact.getPhone());
                }
            }
        });
    }

    private void loadContacts() {
        contactsObservable.clear();
        List<Database.Contact> contacts = Database.getContacts(loggedInUser);
        for (Database.Contact c : contacts) {
            contactsObservable.add(c.getId() + " - " + c.getName() + " (" + c.getPhone() + ")");
        }
        contactList.setItems(contactsObservable);
    }

    @FXML
    protected void onAddClick() {
        String name = nameField.getText().trim();
        String email = emailField.getText().trim();
        String phone = phoneField.getText().trim();

        if (!name.isEmpty()) {
            if (Database.addContact(loggedInUser, name, email, phone)) {
                showAlert(Alert.AlertType.INFORMATION, "Contact added!");
                clearFields();
                loadContacts();
            } else {
                showAlert(Alert.AlertType.ERROR, "Failed to add contact.");
            }
        } else {
            showAlert(Alert.AlertType.WARNING, "Name is required!");
        }
    }

    @FXML
    protected void onDeleteClick() {
        String selected = contactList.getSelectionModel().getSelectedItem();
        if (selected != null) {
            int id = Integer.parseInt(selected.split(" - ")[0]);
            if (Database.deleteContact(id)) {
                showAlert(Alert.AlertType.INFORMATION, "Contact deleted!");
                clearFields();
                loadContacts();
            } else {
                showAlert(Alert.AlertType.ERROR, "Failed to delete contact.");
            }
        } else {
            showAlert(Alert.AlertType.WARNING, "Select a contact to delete.");
        }
    }

    @FXML
    protected void onUpdateClick() {
        if (selectedContactId != -1) {
            String name = nameField.getText().trim();
            String email = emailField.getText().trim();
            String phone = phoneField.getText().trim();

            if (Database.updateContact(selectedContactId, name, email, phone)) {
                showAlert(Alert.AlertType.INFORMATION, "Contact updated!");
                clearFields();
                loadContacts();
                selectedContactId = -1; // reset after update
            } else {
                showAlert(Alert.AlertType.ERROR, "Failed to update contact.");
            }
        } else {
            showAlert(Alert.AlertType.WARNING, "Select a contact to update.");
        }
    }

    private void clearFields() {
        nameField.clear();
        emailField.clear();
        phoneField.clear();
        selectedContactId = -1;
    }

    private void showAlert(Alert.AlertType type, String msg) {
        new Alert(type, msg).showAndWait();
    }
}
