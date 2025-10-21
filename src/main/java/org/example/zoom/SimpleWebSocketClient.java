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
                        messageHandler.accept("SYSTEM|global|Server|✅ Connected to " + serverUrl);

                        // Send JOIN event to server
                        if (currentUser != null) {
                            sendMessage("USER_JOINED", "global", currentUser, "joined the chat");
                        }
                    })
                    .exceptionally(e -> {
                        messageHandler.accept("SYSTEM|global|Server|❌ Connection failed: " + e.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            messageHandler.accept("SYSTEM|global|Server|❌ Error: " + e.getMessage());
        }
    }

    // ✅ Setter for username (fix for your error)
    public void setCurrentUser(String username) {
        this.currentUser = username;
    }

    // ✅ Send a formatted message to server
    public void sendMessage(String type, String meetingId, String username, String content) {
        if (webSocket != null && connected) {
            String message = type + "|" + meetingId + "|" + username + "|" + content;
            webSocket.sendText(message, true);
        } else {
            messageHandler.accept("SYSTEM|global|Server|⚠️ Not connected to server");
        }
    }

    // ✅ Disconnect cleanly
    public void disconnect() {
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Goodbye")
                    .thenRun(() -> messageHandler.accept("SYSTEM|global|Server|🔴 Disconnected"));
        }
        connected = false;
    }

    // ✅ Connection status
    public boolean isConnected() {
        return connected;
    }

    // ✅ WebSocket event handlers
    @Override
    public void onOpen(WebSocket webSocket) {
        Listener.super.onOpen(webSocket);
        connected = true;
        messageHandler.accept("SYSTEM|global|Server|Connected to WebSocket server");
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        messageHandler.accept(data.toString());
        return Listener.super.onText(webSocket, data, last);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        connected = false;
        messageHandler.accept("SYSTEM|global|Server|🔴 Connection closed: " + reason);
        return Listener.super.onClose(webSocket, statusCode, reason);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        connected = false;
        messageHandler.accept("SYSTEM|global|Server|⚠️ Error: " + error.getMessage());
        Listener.super.onError(webSocket, error);
    }
}
