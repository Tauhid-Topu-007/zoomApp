package org.example.zoom.websocket;

import java.net.URI;
import java.util.function.Consumer;

public class SimpleWebSocketClient {
    private String serverUrl;
    private Consumer<String> messageHandler;
    private String currentUser; // Add this field

    // Constructor with both parameters
    public SimpleWebSocketClient(String serverUrl, Consumer<String> messageHandler) {
        this.serverUrl = serverUrl;
        this.messageHandler = messageHandler;
    }

    // Constructor with just server URL
    public SimpleWebSocketClient(String serverUrl) {
        this(serverUrl, null);
    }

    // Getter for server URL
    public String getServerUrl() {
        return serverUrl;
    }

    // Set message handler
    public void setMessageHandler(Consumer<String> messageHandler) {
        this.messageHandler = messageHandler;
    }

    // Set current user - ADD THIS METHOD
    public void setCurrentUser(String username) {
        this.currentUser = username;
        System.out.println("✅ WebSocket client user set to: " + username);
    }

    // Get current user
    public String getCurrentUser() {
        return currentUser;
    }

    // When a message is received from WebSocket
    protected void onMessageReceived(String message) {
        if (messageHandler != null) {
            messageHandler.accept(message);
        }
    }

    // Connection methods
    public void connect() {
        // Connect logic
        System.out.println("🔗 Connecting to: " + serverUrl);
        // Implement actual WebSocket connection
    }

    public void disconnect() {
        // Disconnect logic
        System.out.println("🛑 Disconnecting from: " + serverUrl);
        // Implement actual WebSocket disconnection
    }

    public boolean isConnected() {
        // Return connection status
        // For now, return true if we have a serverUrl
        return serverUrl != null && !serverUrl.isEmpty();
    }

    // Send message with the correct format
    public void sendMessage(String type, String meetingId, String username, String content) {
        // Send message logic
        String message = type + "|" + meetingId + "|" + username + "|" + content;
        System.out.println("📤 Sending message: " + message);
        // Send via WebSocket
        if (isConnected()) {
            send(message);
            System.out.println("✅ Message sent: " + type + " from " + username);
        }
    }

    // Simple send method
    public void send(String message) {
        System.out.println("📤 Sending raw message: " + message);
        // Actual WebSocket send implementation
    }

    // If you need connection listener interface, add it:
    public interface ConnectionListener {
        void onConnected();
        void onDisconnected();
        void onError(String error);
    }

    private ConnectionListener connectionListener;

    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }
}