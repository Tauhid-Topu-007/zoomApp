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
    private boolean isConnecting = false;

    public interface ConnectionListener {
        void onConnected();
        void onDisconnected();
        void onError(String error);
    }

    public SimpleWebSocketClient(String serverUrl, Consumer<String> messageHandler) {
        this.serverUrl = serverUrl;
        this.messageHandler = messageHandler;
        System.out.println("SimpleWebSocketClient created for: " + serverUrl);
    }

    public void connect() {
        if (isConnecting || isConnected()) {
            System.out.println("Already connected or connecting, skipping...");
            return;
        }

        try {
            isConnecting = true;
            URI serverUri = new URI(serverUrl);

            webSocketClient = new WebSocketClient(serverUri) {
                @Override
                public void onOpen(ServerHandshake handshakedata) {
                    System.out.println("=== WEBSOCKET CONNECTION ESTABLISHED ===");
                    System.out.println("Connected to: " + serverUrl);
                    System.out.println("Handshake status: " + handshakedata.getHttpStatus());
                    System.out.println("Handshake message: " + handshakedata.getHttpStatusMessage());

                    isConnecting = false;

                    if (connectionListener != null) {
                        connectionListener.onConnected();
                    }

                    // Send initial connection message
                    if (currentUser != null) {
                        System.out.println("Sending initial connection message for user: " + currentUser);
                        sendMessage("USER_CONNECTED", "global", currentUser, "User connected to server");
                    }

                    // Test message to verify connection
                    send("TEST|global|server|Connection test");
                }

                @Override
                public void onMessage(String message) {
                    System.out.println("=== WEBSOCKET MESSAGE RECEIVED ===");
                    System.out.println("Raw message: " + message);

                    if (messageHandler != null) {
                        try {
                            messageHandler.accept(message);
                        } catch (Exception e) {
                            System.err.println("Error in message handler: " + e.getMessage());
                        }
                    } else {
                        System.err.println("No message handler registered!");
                    }
                }

                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("=== WEBSOCKET CONNECTION CLOSED ===");
                    System.out.println("Server: " + serverUrl);
                    System.out.println("Code: " + code);
                    System.out.println("Reason: " + reason);
                    System.out.println("Remote: " + remote);

                    isConnecting = false;

                    if (connectionListener != null) {
                        connectionListener.onDisconnected();
                    }
                }

                @Override
                public void onError(Exception ex) {
                    System.err.println("=== WEBSOCKET ERROR ===");
                    System.err.println("Server: " + serverUrl);
                    System.err.println("Error: " + ex.getMessage());
                    ex.printStackTrace();

                    isConnecting = false;

                    if (connectionListener != null) {
                        connectionListener.onError(ex.getMessage());
                    }
                }
            };

            System.out.println("Attempting to connect to WebSocket: " + serverUrl);
            webSocketClient.connect();

        } catch (Exception e) {
            isConnecting = false;
            System.err.println("Failed to create WebSocket connection to " + serverUrl + ": " + e.getMessage());
            e.printStackTrace();

            if (connectionListener != null) {
                connectionListener.onError(e.getMessage());
            }
        }
    }

    public void disconnect() {
        System.out.println("Disconnecting WebSocket from: " + serverUrl);
        if (webSocketClient != null) {
            if (webSocketClient.isOpen()) {
                webSocketClient.close();
            }
            webSocketClient = null;
        }
        isConnecting = false;
    }

    public void send(String message) {
        if (isConnected()) {
            try {
                System.out.println("=== WEBSOCKET SENDING MESSAGE ===");
                System.out.println("To: " + serverUrl);
                System.out.println("Message: " + message);

                webSocketClient.send(message);

                System.out.println("Message sent successfully");
            } catch (Exception e) {
                System.err.println("Failed to send WebSocket message: " + e.getMessage());
                e.printStackTrace();
            }
        } else {
            System.err.println("Cannot send message - WebSocket not connected to: " + serverUrl);
            System.err.println("WebSocket client: " + (webSocketClient != null ? "Exists" : "Null"));
            System.err.println("WebSocket open: " + (webSocketClient != null && webSocketClient.isOpen() ? "Yes" : "No"));
        }
    }

    public void sendMessage(String type, String meetingId, String username, String content) {
        if (isConnected()) {
            // Format: TYPE|MEETING_ID|USERNAME|CONTENT
            String message = type + "|" + meetingId + "|" + username + "|" + content;

            System.out.println("=== SENDING FORMATTED WEBSOCKET MESSAGE ===");
            System.out.println("Type: " + type);
            System.out.println("Meeting ID: " + meetingId);
            System.out.println("Username: " + username);
            System.out.println("Content length: " + (content != null ? content.length() : 0));
            System.out.println("Full message: " + message);

            send(message);
        } else {
            System.err.println("Cannot send formatted message - WebSocket not connected");
            System.err.println("Type: " + type);
            System.err.println("Meeting ID: " + meetingId);
            System.err.println("Username: " + username);
            System.err.println("Connection status: " + getConnectionStatus());

            // Try to reconnect if disconnected
            if (!isConnecting) {
                System.out.println("Attempting to reconnect...");
                connect();
            }
        }
    }

    public boolean isConnected() {
        return webSocketClient != null && webSocketClient.isOpen() && !isConnecting;
    }

    public void setMessageHandler(Consumer<String> messageHandler) {
        System.out.println("Setting WebSocket message handler");
        this.messageHandler = messageHandler;
    }

    public void setCurrentUser(String username) {
        System.out.println("Setting current WebSocket user: " + username);
        this.currentUser = username;
    }

    public void setConnectionListener(ConnectionListener listener) {
        System.out.println("Setting WebSocket connection listener");
        this.connectionListener = listener;
    }

    public String getServerUrl() {
        return serverUrl;
    }

    public String getConnectionStatus() {
        if (isConnecting) {
            return "Connecting to " + serverUrl;
        } else if (isConnected()) {
            return "Connected to " + serverUrl;
        } else if (webSocketClient != null) {
            return "Disconnected from " + serverUrl;
        } else {
            return "Not initialized";
        }
    }

    public void reconnect() {
        System.out.println("Reconnecting WebSocket...");
        disconnect();
        try {
            Thread.sleep(1000); // Wait a bit before reconnecting
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        connect();
    }

    public boolean isConnecting() {
        return isConnecting;
    }
}