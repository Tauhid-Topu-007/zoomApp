package org.example.zoom.websocket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public class SimpleWebSocketClient implements Listener {

    private WebSocket webSocket;
    private Consumer<String> messageHandler;
    private boolean connected = false;
    private String serverUrl;
    private String currentUser;

    // ✅ Constructor
    public SimpleWebSocketClient(String serverUrl, Consumer<String> messageHandler) {
        this.serverUrl = serverUrl;
        this.messageHandler = messageHandler;
        connect();
    }

    // ✅ Connect to WebSocket server
    public void connect() {
        try {
            HttpClient client = HttpClient.newHttpClient();
            client.newWebSocketBuilder()
                    .buildAsync(URI.create(serverUrl), this)
                    .thenAccept(ws -> {
                        this.webSocket = ws;
                        this.connected = true;
                        if (messageHandler != null) {
                            messageHandler.accept("SYSTEM|global|Server|✅ Connected to " + serverUrl);
                        }

                        // Send JOIN event to server
                        if (currentUser != null) {
                            sendMessage("USER_JOINED", "global", currentUser, "joined the chat");
                        }
                    })
                    .exceptionally(e -> {
                        if (messageHandler != null) {
                            messageHandler.accept("SYSTEM|global|Server|❌ Connection failed: " + e.getMessage());
                        }
                        return null;
                    });
        } catch (Exception e) {
            if (messageHandler != null) {
                messageHandler.accept("SYSTEM|global|Server|❌ Error: " + e.getMessage());
            }
        }
    }

    // ✅ NEW: Reconnect method
    public void reconnect() {
        System.out.println("🔄 Attempting to reconnect to: " + serverUrl);

        // Clean up existing connection
        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Reconnecting");
            } catch (Exception e) {
                // Ignore errors during close
            }
            webSocket = null;
        }

        connected = false;

        // Attempt new connection
        try {
            Thread.sleep(1000); // Wait 1 second before reconnecting
            connect();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    // ✅ Setter for username
    public void setCurrentUser(String username) {
        this.currentUser = username;
    }

    // ✅ Send a formatted message to server
    public void sendMessage(String type, String meetingId, String username, String content) {
        if (webSocket != null && connected) {
            String message = type + "|" + meetingId + "|" + username + "|" + content;
            webSocket.sendText(message, true);
            System.out.println("📤 Sent: " + message);
        } else {
            if (messageHandler != null) {
                messageHandler.accept("SYSTEM|global|Server|⚠️ Not connected to server");
            }
            System.err.println("❌ Cannot send message - WebSocket not connected");
        }
    }

    // ✅ Disconnect cleanly
    public void disconnect() {
        connected = false;
        if (webSocket != null) {
            try {
                webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Goodbye")
                        .thenRun(() -> {
                            if (messageHandler != null) {
                                messageHandler.accept("SYSTEM|global|Server|🔴 Disconnected");
                            }
                        });
            } catch (Exception e) {
                System.err.println("Error during disconnect: " + e.getMessage());
            }
            webSocket = null;
        }
    }

    // ✅ Connection status
    public boolean isConnected() {
        return connected && webSocket != null;
    }

    // ✅ Get server URL
    public String getServerUrl() {
        return serverUrl;
    }

    // ✅ WebSocket event handlers
    @Override
    public void onOpen(WebSocket webSocket) {
        Listener.super.onOpen(webSocket);
        connected = true;
        System.out.println("✅ WebSocket connection opened: " + serverUrl);
        if (messageHandler != null) {
            messageHandler.accept("SYSTEM|global|Server|Connected to WebSocket server");
        }
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        String message = data.toString();
        System.out.println("📨 Received: " + message);
        if (messageHandler != null) {
            messageHandler.accept(message);
        }
        return Listener.super.onText(webSocket, data, last);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        connected = false;
        System.out.println("🔴 WebSocket connection closed: " + reason + " (code: " + statusCode + ")");
        if (messageHandler != null) {
            messageHandler.accept("SYSTEM|global|Server|🔴 Connection closed: " + reason);
        }
        return Listener.super.onClose(webSocket, statusCode, reason);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        connected = false;
        System.err.println("❌ WebSocket error: " + error.getMessage());
        if (messageHandler != null) {
            messageHandler.accept("SYSTEM|global|Server|⚠️ Error: " + error.getMessage());
        }
        Listener.super.onError(webSocket, error);
    }
}