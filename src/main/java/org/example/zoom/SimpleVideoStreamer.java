package org.example.zoom;

import javafx.application.Platform;
import javafx.scene.image.Image;
import javafx.scene.image.WritableImage;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import java.util.Base64;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import javafx.embed.swing.SwingFXUtils;

public class SimpleVideoStreamer {

    private static boolean streaming = false;
    private static Thread streamingThread;

    /**
     * Start streaming test video
     */
    public static void startStreaming(String username, String meetingId) {
        if (streaming) {
            return;
        }

        streaming = true;
        streamingThread = new Thread(() -> {
            System.out.println("📺 Starting simple video stream for: " + username);
            int frameCount = 0;

            while (streaming) {
                try {
                    // Create a simple test frame - do this in FX thread
                    WritableImage testImage = createTestFrame(username, frameCount);

                    // Convert to Base64 (SIMPLIFIED)
                    String base64Image = imageToBase64Simple(testImage);

                    if (base64Image != null && HelloApplication.isWebSocketConnected()) {
                        // Send via WebSocket
                        HelloApplication.sendWebSocketMessage(
                                "VIDEO_FRAME",
                                meetingId,
                                username,
                                base64Image
                        );

                        if (frameCount % 10 == 0) {
                            System.out.println("📺 Sent frame #" + frameCount + " (" + base64Image.length() + " chars)");
                        }
                    }

                    frameCount++;
                    Thread.sleep(500); // 2 FPS - slower for testing

                } catch (Exception e) {
                    System.err.println("❌ Stream error: " + e.getMessage());
                    e.printStackTrace();
                    break;
                }
            }
            System.out.println("📺 Streaming stopped");
        });

        streamingThread.setDaemon(true);
        streamingThread.start();
    }

    /**
     * Create test frame on FX application thread
     */
    private static WritableImage createTestFrame(String username, int frameCount) {
        // Use a simple approach that doesn't require FX thread
        try {
            // Create BufferedImage directly (not using JavaFX Canvas)
            BufferedImage bufferedImage = new BufferedImage(320, 240, BufferedImage.TYPE_INT_RGB);
            java.awt.Graphics2D g2d = bufferedImage.createGraphics();

            // Create moving pattern
            long time = System.currentTimeMillis() / 1000;
            int x = 160 + (int)(100 * Math.sin(time));
            int y = 120 + (int)(80 * Math.cos(time * 1.5));

            // Draw background
            g2d.setColor(new java.awt.Color(0, 0, 100)); // Dark blue
            g2d.fillRect(0, 0, 320, 240);

            // Draw moving circle
            g2d.setColor(java.awt.Color.RED);
            g2d.fillOval(x - 20, y - 20, 40, 40);

            // Draw text
            g2d.setColor(java.awt.Color.WHITE);
            g2d.drawString("LIVE VIDEO", 120, 30);
            g2d.drawString("User: " + username, 10, 50);
            g2d.drawString("Frame: " + frameCount, 10, 70);
            g2d.drawString("Time: " + new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date()), 10, 90);
            g2d.drawString("Meeting: " + HelloApplication.getActiveMeetingId(), 10, 110);

            g2d.dispose();

            // Convert to JavaFX Image
            return SwingFXUtils.toFXImage(bufferedImage, null);

        } catch (Exception e) {
            System.err.println("❌ Error creating test frame: " + e.getMessage());
            return null;
        }
    }

    /**
     * SUPER SIMPLE Image to Base64 conversion
     */
    private static String imageToBase64Simple(WritableImage image) {
        try {
            if (image == null) {
                System.err.println("❌ Image is null");
                return "TEST_BASE64_IMAGE";
            }

            // Convert to BufferedImage
            BufferedImage bufferedImage = SwingFXUtils.fromFXImage(image, null);

            if (bufferedImage == null) {
                System.err.println("❌ BufferedImage is null");
                return "TEST_BASE64_IMAGE";
            }

            // Write to byte array as PNG
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            boolean success = ImageIO.write(bufferedImage, "png", baos);

            if (!success) {
                System.err.println("❌ Failed to write PNG");
                return "TEST_BASE64_IMAGE";
            }

            byte[] imageBytes = baos.toByteArray();

            // Convert to Base64
            String base64 = Base64.getEncoder().encodeToString(imageBytes);

            if (base64 == null || base64.isEmpty()) {
                System.err.println("❌ Base64 is empty");
                return "TEST_BASE64_IMAGE";
            }

            return base64;

        } catch (Exception e) {
            System.err.println("❌ Simple base64 conversion failed: " + e.getMessage());
            // Return a simple test base64 string
            return "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg==";
        }
    }

    /**
     * Stop streaming
     */
    public static void stopStreaming() {
        streaming = false;
        if (streamingThread != null) {
            try {
                streamingThread.join(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    public static boolean isStreaming() {
        return streaming;
    }
}