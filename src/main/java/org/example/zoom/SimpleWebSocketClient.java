package org.example.zoom.websocket;

import java.net.URI;
import java.util.function.Consumer;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

public class SimpleWebSocketClient {

    private WebSocketClient webSocketClient;
    private String serverUrl;
    private Consumer<String> messageHandler;
    private String currentUser;
    private ConnectionListener connectionListener;

    public interface ConnectionListener {
        void onConnected();
        void onDisconnected();
        void onError(String error);
    }

    public SimpleWebSocketClient(String serverUrl, Consumer<String> messageHandler) {
        this.serverUrl = serverUrl;
        this.messageHandler = messageHandler;
    }

    public void connect() {
        try {
            URI serverUri = new URI(serverUrl);

            webSocketClient = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    System.out.println("WebSocket connected to " + serverUrl);
                    if (connectionListener != null) {
                        connectionListener.onConnected();
                    }

                    if (currentUser != null) {
                        sendMessage("CONNECTED", "global", currentUser, "Connected to server");
                    }
                }

                @Override
                public void onMessage(String message) {
                    if (messageHandler != null) {
                        messageHandler.accept(message);
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("WebSocket disconnected from " + serverUrl + ": " + reason);
                    if (connectionListener != null) {
                        connectionListener.onDisconnected();
                    }
                }

                @Override
                public void onError(Exception ex) {
                    System.err.println("WebSocket error: " + ex.getMessage());
                    if (connectionListener != null) {
                        connectionListener.onError(ex.getMessage());
                    }
                }
            };

            webSocketClient.connect();

        } catch (Exception e) {
            System.err.println("Failed to connect to " + serverUrl + ": " + e.getMessage());
            if (connectionListener != null) {
                connectionListener.onError(e.getMessage());
            }
        }
    }

    public void disconnect() {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            webSocketClient.close();
            webSocketClient = null;
        }
    }

    public void send(String message) {
        if (webSocketClient != null && webSocketClient.isOpen()) {
            webSocketClient.send(message);
        } else {
            System.err.println("Cannot send message - WebSocket not connected");
        }
    }

    public void sendMessage(String type, String meetingId, String username, String content) {
        String message = type + "|" + meetingId + "|" + username + "|" + content;
        System.out.println("Sending message: " + message);
        send(message);
    }

    public boolean isConnected() {
        return webSocketClient != null && webSocketClient.isOpen();
    }

    public void setMessageHandler(Consumer<String> messageHandler) {
        this.messageHandler = messageHandler;
    }

    public void setCurrentUser(String username) {
        this.currentUser = username;
    }

    public void setConnectionListener(ConnectionListener listener) {
        this.connectionListener = listener;
    }

    public String getServerUrl() {
        return serverUrl;
    }
}