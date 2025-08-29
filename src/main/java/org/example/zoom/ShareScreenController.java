package org.example.zoom;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.Timer;
import java.util.TimerTask;

public class ShareScreenController {

    @FXML
    private ImageView screenView; // ImageView defined in FXML

    private Timer timer;
    private Robot robot;
    private Rectangle screenRect;

    @FXML
    public void initialize() {
        try {
            robot = new Robot();
            Dimension screenSize = Toolkit.getDefaultToolkit().getScreenSize();
            screenRect = new Rectangle(screenSize);

            // Keep responsive
            screenView.setPreserveRatio(true);
            screenView.setSmooth(true);
        } catch (Exception e) {
            showError("Error: " + e.getMessage());
        }
    }

    @FXML
    protected void onStartShareClick() {
        if (timer != null) return;

        timer = new Timer();
        timer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                BufferedImage screenCapture = robot.createScreenCapture(screenRect);
                WritableImage fxImage = SwingFXUtils.toFXImage(screenCapture, null);

                Platform.runLater(() -> {
                    screenView.setImage(fxImage);

                    // Make responsive with window
                    screenView.fitWidthProperty().bind(screenView.getScene().widthProperty());
                    screenView.fitHeightProperty().bind(screenView.getScene().heightProperty().subtract(70));
                });
            }
        }, 0, 150);
    }

    @FXML
    protected void onStopShareClick() {
        if (timer != null) {
            timer.cancel();
            timer = null;
            screenView.setImage(null);
        }
    }

    @FXML
    protected void onBackClick() throws Exception {
        if (timer != null) {
            timer.cancel();
            timer = null;
        }
        HelloApplication.setRoot("dashboard-view.fxml");
    }

    private void showError(String msg) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText("Screen Share Error");
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
