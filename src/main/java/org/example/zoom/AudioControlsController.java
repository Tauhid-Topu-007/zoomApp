package org.example.zoom;

import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Slider;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.animation.ScaleTransition;
import javafx.util.Duration;

import java.net.URL;
import java.util.ResourceBundle;

public class AudioControlsController implements Initializable {

    @FXML
    private VBox audioContainer;
    @FXML
    private Button audioToggleButton;
    @FXML
    private Label audioStatusLabel;
    @FXML
    private HBox audioControlsBox;
    @FXML
    private Button muteAllButton;
    @FXML
    private Button deafenButton;

    // New FXML elements from the updated FXML file
    @FXML
    private ComboBox<String> microphoneComboBox;
    @FXML
    private ComboBox<String> speakerComboBox;
    @FXML
    private Slider microphoneSlider;
    @FXML
    private Slider speakerSlider;

    private boolean audioMuted = false;
    private boolean isDeafened = false;
    private boolean allMuted = false;

    @Override
    public void initialize(URL location, ResourceBundle resources) {
        setupAudioControls();
        setupAudioSettings(); // Initialize the new audio settings
        updateButtonStyles();

        // Register this controller with the main application
        HelloApplication.setAudioControlsController(this);
    }

    private void setupAudioControls() {
        // Add hover effects
        setupButtonHoverEffects(audioToggleButton);
        setupButtonHoverEffects(muteAllButton);
        setupButtonHoverEffects(deafenButton);

        // Set initial styles
        audioToggleButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;");
        muteAllButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold;");
        deafenButton.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-weight: bold;");

        // Update status label
        updateStatusLabel();
    }

    private void setupAudioSettings() {
        // Setup microphone selection
        if (microphoneComboBox != null) {
            microphoneComboBox.getItems().addAll("Default Microphone", "Built-in Microphone", "External Microphone");
            microphoneComboBox.setValue("Default Microphone");
            microphoneComboBox.setOnAction(e -> onMicrophoneChanged());
        }

        // Setup speaker selection
        if (speakerComboBox != null) {
            speakerComboBox.getItems().addAll("Default Speaker", "Built-in Speakers", "Headphones", "External Speakers");
            speakerComboBox.setValue("Default Speaker");
            speakerComboBox.setOnAction(e -> onSpeakerChanged());
        }

        // Setup volume sliders
        if (microphoneSlider != null) {
            microphoneSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                System.out.println("🎤 Microphone volume: " + newVal.intValue() + "%");
                updateAudioLevels();
            });
        }

        if (speakerSlider != null) {
            speakerSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
                System.out.println("🔊 Speaker volume: " + newVal.intValue() + "%");
                updateAudioLevels();
            });
        }
    }

    private void setupButtonHoverEffects(Button button) {
        button.setOnMouseEntered(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(100), button);
            st.setToX(1.05);
            st.setToY(1.05);
            st.play();
        });

        button.setOnMouseExited(e -> {
            ScaleTransition st = new ScaleTransition(Duration.millis(100), button);
            st.setToX(1.0);
            st.setToY(1.0);
            st.play();
        });
    }

    @FXML
    protected void toggleAudio() {
        // Use centralized audio control from HelloApplication
        HelloApplication.toggleAudio();

        // Update local state
        audioMuted = HelloApplication.isAudioMuted();
        updateButtonStyles();
        updateStatusLabel();

        if (audioMuted) {
            addSystemMessage("You muted your audio");
        } else {
            addSystemMessage("You unmuted your audio");
        }
    }

    @FXML
    protected void toggleMuteAll() {
        // Use the centralized mute all control from HelloApplication
        HelloApplication.muteAllParticipants();

        // Update local state
        allMuted = HelloApplication.isAllMuted();
        updateMuteAllButton();
        updateStatusLabel();

        animateButton(muteAllButton);
    }

    @FXML
    protected void toggleDeafen() {
        // Use the centralized deafen control from HelloApplication
        HelloApplication.toggleDeafen();

        // Update local state
        isDeafened = HelloApplication.isDeafened();
        updateDeafenButton();
        updateStatusLabel();

        animateButton(deafenButton);
    }

    @FXML
    protected void testAudio() {
        System.out.println("🔊 Testing audio...");

        // Simulate audio test
        animateButton(audioToggleButton);

        // Show test confirmation
        javafx.application.Platform.runLater(() -> {
            javafx.scene.control.Alert alert = new javafx.scene.control.Alert(
                    javafx.scene.control.Alert.AlertType.INFORMATION);
            alert.setTitle("Audio Test");
            alert.setHeaderText(null);
            alert.setContentText("Audio test completed!\nYou should hear a test sound (simulated).\n\n" +
                    "Microphone: " + (microphoneComboBox != null ? microphoneComboBox.getValue() : "Default") + "\n" +
                    "Speaker: " + (speakerComboBox != null ? speakerComboBox.getValue() : "Default"));
            alert.showAndWait();
        });

        addSystemMessage("Audio test performed");
    }

    private void onMicrophoneChanged() {
        if (microphoneComboBox != null) {
            String selectedMic = microphoneComboBox.getValue();
            System.out.println("🎤 Microphone changed to: " + selectedMic);
            addSystemMessage("Switched to microphone: " + selectedMic);
        }
    }

    private void onSpeakerChanged() {
        if (speakerComboBox != null) {
            String selectedSpeaker = speakerComboBox.getValue();
            System.out.println("🔊 Speaker changed to: " + selectedSpeaker);
            addSystemMessage("Switched to speaker: " + selectedSpeaker);
        }
    }

    private void updateAudioLevels() {
        // Update audio levels based on slider values
        if (microphoneSlider != null && speakerSlider != null) {
            int micLevel = (int) microphoneSlider.getValue();
            int speakerLevel = (int) speakerSlider.getValue();

            // Update status label with volume info when not in special states
            if (!isDeafened && !audioMuted && !allMuted) {
                audioStatusLabel.setText("Audio: Normal 🔊 (Mic: " + micLevel + "%, Spk: " + speakerLevel + "%)");
            }
        }
    }

    public void updateButtonStyles() {
        if (audioMuted) {
            audioToggleButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;");
            audioToggleButton.setText("Unmute");
        } else {
            audioToggleButton.setStyle("-fx-background-color: #27ae60; -fx-text-fill: white; -fx-font-weight: bold;");
            audioToggleButton.setText("Mute");
        }
    }

    private void updateMuteAllButton() {
        if (allMuted) {
            muteAllButton.setStyle("-fx-background-color: #e74c3c; -fx-text-fill: white; -fx-font-weight: bold;");
            muteAllButton.setText("Unmute All");
        } else {
            muteAllButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white; -fx-font-weight: bold;");
            muteAllButton.setText("Mute All");
        }

        // Only enable mute all button for hosts
        boolean isHost = HelloApplication.isMeetingHost();
        muteAllButton.setDisable(!isHost);

        if (!isHost) {
            muteAllButton.setStyle("-fx-background-color: #7f8c8d; -fx-text-fill: white; -fx-font-weight: bold;");
            muteAllButton.setTooltip(new javafx.scene.control.Tooltip("Only available for meeting hosts"));
        }
    }

    private void updateDeafenButton() {
        if (isDeafened) {
            deafenButton.setStyle("-fx-background-color: #c0392b; -fx-text-fill: white; -fx-font-weight: bold;");
            deafenButton.setText("Undeafen");
        } else {
            deafenButton.setStyle("-fx-background-color: #e67e22; -fx-text-fill: white; -fx-font-weight: bold;");
            deafenButton.setText("Deafen");
        }
    }

    private void updateStatusLabel() {
        if (isDeafened) {
            audioStatusLabel.setText("Audio: Deafened 🔇");
            audioStatusLabel.setStyle("-fx-text-fill: #e74c3c; -fx-font-weight: bold;");
        } else if (audioMuted) {
            audioStatusLabel.setText("Audio: Muted 🔇");
            audioStatusLabel.setStyle("-fx-text-fill: #e67e22; -fx-font-weight: bold;");
        } else if (allMuted) {
            audioStatusLabel.setText("All Participants Muted 🔇");
            audioStatusLabel.setStyle("-fx-text-fill: #3498db; -fx-font-weight: bold;");
        } else {
            // Include volume information in normal state
            if (microphoneSlider != null && speakerSlider != null) {
                int micLevel = (int) microphoneSlider.getValue();
                int speakerLevel = (int) speakerSlider.getValue();
                audioStatusLabel.setText("Audio: Normal 🔊 (Mic: " + micLevel + "%, Spk: " + speakerLevel + "%)");
            } else {
                audioStatusLabel.setText("Audio: Normal 🔊");
            }
            audioStatusLabel.setStyle("-fx-text-fill: #27ae60; -fx-font-weight: bold;");
        }
    }

    private void animateButton(Button button) {
        ScaleTransition st = new ScaleTransition(Duration.millis(150), button);
        st.setFromX(1.0);
        st.setFromY(1.0);
        st.setToX(1.1);
        st.setToY(1.1);
        st.setAutoReverse(true);
        st.setCycleCount(2);
        st.play();
    }

    private void addSystemMessage(String message) {
        System.out.println("🔊 " + message);

        // You can also integrate this with your chat system if needed
        // ChatController.addSystemMessage(message);
    }

    // Method to sync with global state
    public void syncWithGlobalState() {
        audioMuted = HelloApplication.isAudioMuted();
        isDeafened = HelloApplication.isDeafened();
        allMuted = HelloApplication.isAllMuted();

        updateButtonStyles();
        updateMuteAllButton();
        updateDeafenButton();
        updateStatusLabel();
    }

    // Method to update from server messages
    public void updateFromServer(String audioStatus) {
        System.out.println("AudioControlsController: Received audio status - " + audioStatus);

        // Parse audio status messages from other users
        if (audioStatus.contains("muted their audio")) {
            // Another user muted their audio
            System.out.println("Another participant muted their audio");
        } else if (audioStatus.contains("unmuted their audio")) {
            // Another user unmuted their audio
            System.out.println("Another participant unmuted their audio");
        } else if (audioStatus.contains("deafened themselves")) {
            // Another user deafened themselves
            System.out.println("Another participant deafened themselves");
        } else if (audioStatus.contains("undeafened themselves")) {
            // Another user undeafened themselves
            System.out.println("Another participant undeafened themselves");
        }
    }

    // Method called when meeting host status changes
    public void onHostStatusChanged(boolean isHost) {
        updateMuteAllButton();

        if (isHost) {
            System.out.println("AudioControls: You are now the meeting host");
        } else {
            System.out.println("AudioControls: You are a participant");
        }
    }

    // Method called when joining/leaving a meeting
    public void onMeetingStateChanged(boolean inMeeting) {
        if (inMeeting) {
            // Reset audio states when joining a new meeting
            audioMuted = false;
            isDeafened = false;
            allMuted = false;

            // Reset volume sliders to default
            if (microphoneSlider != null) microphoneSlider.setValue(80);
            if (speakerSlider != null) speakerSlider.setValue(80);

            updateButtonStyles();
            updateMuteAllButton();
            updateDeafenButton();
            updateStatusLabel();

            System.out.println("AudioControls: Joined meeting - audio controls activated");
        } else {
            // Meeting ended or left
            audioStatusLabel.setText("Audio: Not in Meeting");
            audioStatusLabel.setStyle("-fx-text-fill: #7f8c8d; -fx-font-weight: bold;");

            System.out.println("AudioControls: Left meeting - audio controls deactivated");
        }
    }

    // Cleanup method
    public void cleanup() {
        System.out.println("✅ Audio controls cleaned up");
    }
}