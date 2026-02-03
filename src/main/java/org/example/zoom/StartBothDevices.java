package org.example.zoom;

public class StartBothDevices {
    public static void main(String[] args) {
        System.out.println("🚀 Starting both devices for testing...");

        // Start Device 1 (Host) in a separate thread
        Thread device1Thread = new Thread(() -> {
            try {
                Device1_Host.main(new String[]{});
            } catch (Exception e) {
                System.err.println("❌ Failed to start Device 1: " + e.getMessage());
            }
        });
        device1Thread.start();

        // Wait for Device 1 to start
        try {
            Thread.sleep(3000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        // Start Device 2 (Client) in a separate thread
        Thread device2Thread = new Thread(() -> {
            try {
                Device2_Client.main(new String[]{});
            } catch (Exception e) {
                System.err.println("❌ Failed to start Device 2: " + e.getMessage());
            }
        });
        device2Thread.start();

        System.out.println("✅ Both devices started!");
        System.out.println("🎯 Host is on the left side of screen");
        System.out.println("🎯 Client is on the right side of screen");
    }
}