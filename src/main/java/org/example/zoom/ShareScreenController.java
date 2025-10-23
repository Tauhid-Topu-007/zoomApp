package org.example.zoom;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.BorderPane;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import javax.imageio.ImageIO;

public class ShareScreenController {

    @FXML
    private ImageView screenView;
    @FXML
    private Label statusLabel;
    @FXML
    private Label fpsLabel;
    @FXML
    private Label resolutionLabel;
    @FXML
    private Slider qualitySlider;
    @FXML
    private ComboBox<String> regionComboBox;
    @FXML
    private CheckBox cursorCheckBox;
    @FXML
    private Button startButton;  // This should be javafx.scene.control.Button
    @FXML
    private Button stopButton;   // This should be javafx.scene.control.Button
    @FXML
    private Button pauseButton;  // This should be javafx.scene.control.Button
    @FXML
    private BorderPane rootPane;

    private Timer timer;
    private Robot robot;
    private Rectangle screenRect;
    private Rectangle captureRect;
    private boolean isSharing = false;
    private boolean isPaused = false;
    private int frameCount = 0;
    private long lastFpsUpdate = 0;
    private double captureQuality = 1.0;
    private Stage stage;
    private WritableImage currentFrame;

    // Capture regions
    private static final String FULL_SCREEN = "Full Screen";
    private static final String PRIMARY_MONITOR = "Primary Monitor";
    private static final String ACTIVE_WINDOW = "Active Window (Approx)";

    @FXML
    public void initialize() {
        try {
            robot = new Robot();
            setupCaptureRegion();
            setupUI();
            updateStatus("Ready to share screen", "#2ecc71");
        } catch (Exception e) {
            showError("Initialization Error", "Failed to initialize screen capture: " + e.getMessage());
        }
    }

    private void setupCaptureRegion() {
        Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
        screenRect = new Rectangle(screenSize);
        captureRect = screenRect;
        updateResolutionLabel();
    }

    private void setupUI() {
        // Setup region combo box
        regionComboBox.getItems().addAll(FULL_SCREEN, PRIMARY_MONITOR, ACTIVE_WINDOW);
        regionComboBox.setValue(FULL_SCREEN);
        regionComboBox.setOnAction(e -> onRegionChanged());

        // Setup quality slider
        qualitySlider.setMin(0.3);
        qualitySlider.setMax(1.0);
        qualitySlider.setValue(1.0);
        qualitySlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            captureQuality = newVal.doubleValue();
            updateQualityLabel();
        });

        // Setup cursor checkbox
        cursorCheckBox.setSelected(true);

        // Update button states
        updateButtonStates();

        // Make screen view responsive
        screenView.setPreserveRatio(true);
        screenView.setSmooth(true);

        // Update quality label initially
        updateQualityLabel();
    }

    private void updateResolutionLabel() {
        if (captureRect != null) {
            resolutionLabel.setText(String.format("Resolution: %dx%d",
                    captureRect.width, captureRect.height));
        }
    }

    private void updateQualityLabel() {
        int percent = (int)(captureQuality * 100);
        // Visual feedback for quality slider
        if (qualitySlider.lookup(".track") != null) {
            qualitySlider.lookup(".track").setStyle(String.format(
                    "-fx-background-color: linear-gradient(to right, #27ae60 0%%, #27ae60 %d%%, #ecf0f1 %d%%, #ecf0f1 100%%);",
                    percent, percent
            ));
        }
    }

    private void updateButtonStates() {
        startButton.setDisable(isSharing);
        stopButton.setDisable(!isSharing);
        pauseButton.setDisable(!isSharing);
        pauseButton.setText(isPaused ? "Resume" : "Pause");

        if (isPaused) {
            pauseButton.setStyle("-fx-background-color: #f39c12; -fx-text-fill: white;");
        } else {
            pauseButton.setStyle("-fx-background-color: #3498db; -fx-text-fill: white;");
        }
    }

    private void onRegionChanged() {
        String selected = regionComboBox.getValue();
        if (selected.equals(FULL_SCREEN)) {
            Rectangle bounds = new Rectangle();
            GraphicsEnvironment ge = GraphicsEnvironment.getLocalGraphicsEnvironment();
            for (GraphicsDevice gd : ge.getScreenDevices()) {
                bounds = bounds.union(gd.getDefaultConfiguration().getBounds());
            }
            captureRect = bounds;
        } else if (selected.equals(PRIMARY_MONITOR)) {
            GraphicsDevice gd = GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice();
            captureRect = gd.getDefaultConfiguration().getBounds();
        } else if (selected.equals(ACTIVE_WINDOW)) {
            try {
                captureRect = getActiveWindowBounds();
            } catch (Exception e) {
                showError("Active Window Capture",
                        "Could not detect active window. Using full screen instead.");
                captureRect = screenRect;
            }
        }
        updateResolutionLabel();
    }

    private Rectangle getActiveWindowBounds() {
        int width = (int)(screenRect.width * 0.8);
        int height = (int)(screenRect.height * 0.8);
        int x = (screenRect.width - width) / 2;
        int y = (screenRect.height - height) / 2;
        return new Rectangle(x, y, width, height);
    }

    @FXML
    protected void onStartShareClick() {
        if (isSharing) return;

        isSharing = true;
        isPaused = false;
        frameCount = 0;
        lastFpsUpdate = System.currentTimeMillis();

        timer = new Timer(true);
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (!isPaused) {
                    captureAndDisplayScreen();
                }
            }
        }, 0, calculateCaptureInterval());

        updateStatus("🟢 Screen sharing active", "#27ae60");
        updateButtonStates();
    }

    @FXML
    protected void onStopShareClick() {
        stopSharing();
        updateStatus("⏹️ Screen sharing stopped", "#e74c3c");
        screenView.setImage(null);
        currentFrame = null;
    }

    @FXML
    protected void onPauseResumeClick() {
        isPaused = !isPaused;
        if (isPaused) {
            updateStatus("⏸️ Screen sharing paused", "#f39c12");
        } else {
            updateStatus("🟢 Screen sharing active", "#27ae60");
        }
        updateButtonStates();
    }

    @FXML
    protected void onCaptureScreenshotClick() {
        if (!isSharing || isPaused) {
            captureSingleScreenshot();
        } else {
            Platform.runLater(() -> {
                if (currentFrame != null) {
                    screenView.setImage(currentFrame);
                    showInfo("Screenshot", "Current frame captured!");
                } else {
                    captureSingleScreenshot();
                }
            });
        }
    }

    @FXML
    protected void onCopyToClipboardClick() {
        if (currentFrame != null) {
            Clipboard clipboard = Clipboard.getSystemClipboard();
            ClipboardContent content = new ClipboardContent();
            content.putImage(currentFrame);
            clipboard.setContent(content);
            showInfo("Clipboard", "Screenshot copied to clipboard!");
        } else {
            showError("Clipboard Error", "No image available to copy.");
        }
    }

    @FXML
    protected void onSaveScreenshotClick() {
        if (currentFrame != null) {
            saveScreenshotToFile(currentFrame);
        } else {
            showError("Save Error", "No image available to save.");
        }
    }

    @FXML
    protected void onSettingsClick() {
        showInfo("Screen Share Settings",
                "• Change capture region using the dropdown\n" +
                        "• Adjust quality with the slider\n" +
                        "• Toggle cursor visibility\n" +
                        "• Pause/resume during sharing\n" +
                        "• Capture screenshots while sharing");
    }

    @FXML
    protected void onBackClick() {
        try {
            stopSharing();
            HelloApplication.setRoot("dashboard-view.fxml");
        } catch (Exception e) {
            showError("Navigation Error", "Failed to return to dashboard: " + e.getMessage());
        }
    }

    private void captureAndDisplayScreen() {
        try {
            BufferedImage screenCapture = robot.createScreenCapture(captureRect);

            if (captureQuality < 1.0) {
                screenCapture = resizeImage(screenCapture, captureQuality);
            }

            if (cursorCheckBox.isSelected()) {
                screenCapture = includeCursor(screenCapture);
            }

            WritableImage fxImage = SwingFXUtils.toFXImage(screenCapture, null);
            currentFrame = fxImage;

            Platform.runLater(() -> {
                screenView.setImage(fxImage);
                updateFPS();
            });
        } catch (Exception e) {
            Platform.runLater(() ->
                    showError("Capture Error", "Failed to capture screen: " + e.getMessage()));
        }
    }

    private void captureSingleScreenshot() {
        try {
            BufferedImage screenCapture = robot.createScreenCapture(captureRect);
            if (cursorCheckBox.isSelected()) {
                screenCapture = includeCursor(screenCapture);
            }
            WritableImage fxImage = SwingFXUtils.toFXImage(screenCapture, null);
            currentFrame = fxImage;
            Platform.runLater(() -> {
                screenView.setImage(fxImage);
                showInfo("Screenshot", "Screenshot captured successfully!");
            });
        } catch (Exception e) {
            Platform.runLater(() ->
                    showError("Screenshot Error", "Failed to capture screenshot: " + e.getMessage()));
        }
    }

    private BufferedImage resizeImage(BufferedImage original, double scale) {
        int newWidth = (int)(original.getWidth() * scale);
        int newHeight = (int)(original.getHeight() * scale);

        BufferedImage resized = new BufferedImage(newWidth, newHeight, original.getType());
        Graphics2D g = resized.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.drawImage(original, 0, 0, newWidth, newHeight, null);
        g.dispose();

        return resized;
    }

    private BufferedImage includeCursor(BufferedImage image) {
        Point mousePos = MouseInfo.getPointerInfo().getLocation();
        if (captureRect.contains(mousePos)) {
            Graphics2D g = image.createGraphics();

            int x = mousePos.x - captureRect.x;
            int y = mousePos.y - captureRect.y;

            g.setColor(Color.RED);
            g.setStroke(new BasicStroke(2));
            g.drawLine(x - 5, y, x + 5, y);
            g.drawLine(x, y - 5, x, y + 5);
            g.drawOval(x - 3, y - 3, 6, 6);

            g.dispose();
        }
        return image;
    }

    private void saveScreenshotToFile(WritableImage image) {
        if (stage == null) {
            stage = (Stage) rootPane.getScene().getWindow();
        }

        FileChooser fileChooser = new FileChooser();
        fileChooser.setTitle("Save Screenshot");
        fileChooser.getExtensionFilters().addAll(
                new FileChooser.ExtensionFilter("PNG Image", "*.png"),
                new FileChooser.ExtensionFilter("JPEG Image", "*.jpg"),
                new FileChooser.ExtensionFilter("All Files", "*.*")
        );

        String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
        fileChooser.setInitialFileName("screenshot_" + timestamp + ".png");

        File file = fileChooser.showSaveDialog(stage);
        if (file != null) {
            try {
                BufferedImage bImage = SwingFXUtils.fromFXImage(image, null);
                String extension = getFileExtension(file.getName());
                ImageIO.write(bImage, extension, file);
                showInfo("Save Successful", "Screenshot saved to:\n" + file.getAbsolutePath());
            } catch (IOException e) {
                showError("Save Error", "Failed to save screenshot: " + e.getMessage());
            }
        }
    }

    private String getFileExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? "png" : filename.substring(dotIndex + 1);
    }

    private void updateFPS() {
        frameCount++;
        long currentTime = System.currentTimeMillis();
        long elapsed = currentTime - lastFpsUpdate;

        if (elapsed >= 1000) {
            double fps = (frameCount * 1000.0) / elapsed;
            Platform.runLater(() ->
                    fpsLabel.setText(String.format("FPS: %.1f", fps)));
            frameCount = 0;
            lastFpsUpdate = currentTime;
        }
    }

    private void updateStatus(String message, String color) {
        Platform.runLater(() -> {
            statusLabel.setText(message);
            statusLabel.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold;");
        });
    }

    private int calculateCaptureInterval() {
        if (captureQuality >= 0.8) return 100;
        if (captureQuality >= 0.6) return 66;
        return 33;
    }

    private void stopSharing() {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        isSharing = false;
        isPaused = false;
        updateButtonStates();
    }

    private void showError(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.ERROR);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    private void showInfo(String title, String message) {
        Platform.runLater(() -> {
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle(title);
            alert.setHeaderText(null);
            alert.setContentText(message);
            alert.showAndWait();
        });
    }

    public void shutdown() {
        stopSharing();
    }
}