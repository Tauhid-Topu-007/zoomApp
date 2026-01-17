package org.example.zoom;

import javafx.scene.image.Image;
import javafx.embed.swing.SwingFXUtils;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.util.Base64;
import java.util.logging.Logger;

public class SafeImageLoader {
    private static final Logger logger = Logger.getLogger(SafeImageLoader.class.getName());

    /**
     * Safely load image from base64 string with error handling
     */
    public static Image loadFromBase64(String base64Data) {
        if (base64Data == null || base64Data.isEmpty()) {
            return createPlaceholderImage();
        }

        try {
            // Remove data URL prefix if present
            String cleanBase64 = base64Data;
            if (base64Data.contains(",")) {
                cleanBase64 = base64Data.split(",")[1];
            }

            byte[] imageData = Base64.getDecoder().decode(cleanBase64);
            return loadFromBytes(imageData);
        } catch (Exception e) {
            logger.warning("Failed to load image from base64: " + e.getMessage());
            return createPlaceholderImage();
        }
    }

    /**
     * Safely load image from byte array
     */
    public static Image loadFromBytes(byte[] imageData) {
        if (imageData == null || imageData.length == 0) {
            return createPlaceholderImage();
        }

        try {
            // First try to validate and fix the image using ImageIO
            ByteArrayInputStream bis = new ByteArrayInputStream(imageData);
            BufferedImage bufferedImage = ImageIO.read(bis);

            if (bufferedImage == null) {
                logger.warning("ImageIO could not read the image data");
                return createPlaceholderImage();
            }

            // Convert to JavaFX Image
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            ImageIO.write(bufferedImage, "png", bos); // Convert to PNG to avoid JPEG issues
            byte[] pngData = bos.toByteArray();

            return new Image(new ByteArrayInputStream(pngData));
        } catch (Exception e) {
            logger.warning("Failed to load image from bytes: " + e.getMessage());
            return createPlaceholderImage();
        }
    }

    /**
     * Safely load image from resource path
     */
    public static Image loadFromResources(String resourcePath) {
        try {
            InputStream inputStream = SafeImageLoader.class.getResourceAsStream(resourcePath);
            if (inputStream == null) {
                logger.warning("Resource not found: " + resourcePath);
                return createPlaceholderImage();
            }

            // Read and convert to ensure compatibility
            BufferedImage bufferedImage = ImageIO.read(inputStream);
            if (bufferedImage == null) {
                logger.warning("Could not read resource image: " + resourcePath);
                return createPlaceholderImage();
            }

            return SwingFXUtils.toFXImage(bufferedImage, null);
        } catch (Exception e) {
            logger.warning("Failed to load image from resources: " + resourcePath + " - " + e.getMessage());
            return createPlaceholderImage();
        }
    }

    /**
     * Create a placeholder image when loading fails
     */
    public static Image createPlaceholderImage() {
        try {
            // Create a simple placeholder programmatically
            javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(200, 150);
            javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();

            gc.setFill(javafx.scene.paint.Color.LIGHTGRAY);
            gc.fillRect(0, 0, 200, 150);

            gc.setFill(javafx.scene.paint.Color.DARKGRAY);
            gc.fillText("Image Not Available", 50, 75);

            return canvas.snapshot(null, null);
        } catch (Exception e) {
            // Ultimate fallback - return null (JavaFX handles null images gracefully)
            return null;
        }
    }

    /**
     * Validate if image data is likely a valid JPEG
     */
    public static boolean isValidJPEG(byte[] data) {
        if (data == null || data.length < 2) {
            return false;
        }

        // Check for JPEG SOI marker
        return (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8;
    }
}