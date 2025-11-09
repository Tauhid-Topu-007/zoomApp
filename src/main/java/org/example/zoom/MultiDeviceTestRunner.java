package org.example.zoom;

public class MultiDeviceTestRunner {

    public static void main(String[] args) {
        System.out.println("🎯 =========================================");
        System.out.println("🎯 MULTI-DEVICE TEST ENVIRONMENT");
        System.out.println("🎯 =========================================");
        System.out.println("💾 Shared Database: " + Database.URL); // FIXED: Direct field access
        System.out.println("👤 DB User: " + Database.USER);
        System.out.println("🌐 WebSocket Server: 192.168.0.108:8887");

        // Initialize database first
        System.out.println("\n🔧 Initializing shared database...");
        Database.initializeDatabase();

        // Start Device 1 in a new thread
        new Thread(() -> {
            System.out.println("\n🚀 Launching Device 1 (Host)...");
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            Device1_Host.main(new String[]{});
        }).start();

        // Start Device 2 after a delay
        new Thread(() -> {
            try {
                Thread.sleep(5000); // Wait 5 seconds for Device 1 to start
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("\n🚀 Launching Device 2 (Client)...");
            Device2_Client.main(new String[]{});
        }).start();
    }
}