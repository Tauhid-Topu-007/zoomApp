package org.example.zoom.websocket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;

public class HttpWebSocketClient implements WebSocket.Listener {

    private Consumer<String> messageHandler;
    private WebSocket webSocket;
    private boolean connected = false;

    public HttpWebSocketClient(String serverUrl, Consumer<String> messageHandler) {
        this.messageHandler = messageHandler;
        connect(serverUrl);
    }

    /** ✅ Connect to real WebSocket server */
    private void connect(String serverUrl) {
        try {
            HttpClient client = HttpClient.newHttpClient();
            client.newWebSocketBuilder()
                    .buildAsync(URI.create(serverUrl), this)
                    .thenAccept(ws -> {
                        this.webSocket = ws;
                        connected = true;
                        messageHandler.accept("CONNECTED: Connected to " + serverUrl);
                    })
                    .exceptionally(ex -> {
                        messageHandler.accept("ERROR: Failed to connect - " + ex.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            messageHandler.accept("ERROR: " + e.getMessage());
        }
    }

    /** ✅ Send message to server */
    public void sendMessage(String type, String meetingId, String username, String content) {
        if (!connected || webSocket == null) {
            messageHandler.accept("ERROR: Not connected to server");
            return;
        }

        String fullMessage = type + "|" + meetingId + "|" + username + "|" + content;
        webSocket.sendText(fullMessage, true);
        System.out.println("📤 Sent: " + fullMessage);
    }

    /** ✅ Disconnect from server */
    public void disconnect() {
        connected = false;
        if (webSocket != null) {
            webSocket.sendClose(WebSocket.NORMAL_CLOSURE, "Client closed").thenRun(() ->
                    messageHandler.accept("DISCONNECTED: Connection closed"));
        }
    }

    public boolean isConnected() {
        return connected;
    }

    // ---------------- WebSocket Listener Methods ---------------- //

    @Override
    public void onOpen(WebSocket webSocket) {
        messageHandler.accept("SYSTEM|global|Server|Connection opened.");
        webSocket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        messageHandler.accept(data.toString());
        webSocket.request(1);
        return null;
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        messageHandler.accept("ERROR: " + error.getMessage());
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        connected = false;
        messageHandler.accept("DISCONNECTED: " + reason);
        return null;
    }
}
