package org.example.zoom.websocket;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import java.net.InetSocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Set;
import java.util.Collections;

public class CustomWebSocketServer extends WebSocketServer {
    private static final int PORT = 8887;
    private static CustomWebSocketServer instance;
    private final ConcurrentHashMap<WebSocket, String> connectionUsers = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> userMeetings = new ConcurrentHashMap<>();
    private final Set<WebSocket> connections = Collections.newSetFromMap(new ConcurrentHashMap<>());

    public CustomWebSocketServer(InetSocketAddress address) {
        super(address);
        System.out.println("🎯 WebSocket Server starting on: " + address);
    }

    public static void startServer() {
        if (instance == null) {
            InetSocketAddress address = new InetSocketAddress("0.0.0.0", PORT);
            instance = new CustomWebSocketServer(address);
            instance.start();
            System.out.println("✅ WebSocket Server started on port " + PORT);
        }
    }

    public static void stopServer() {
        if (instance != null) {
            try {
                instance.stop();
                instance = null;
                System.out.println("🛑 WebSocket Server stopped");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        connectionUsers.put(conn, null); // User not identified yet
        connections.add(conn); // Add to connections set
        System.out.println("🔗 New connection: " + conn.getRemoteSocketAddress());
        conn.send("WELCOME|global|server|Connected to WebSocket server");
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        String user = connectionUsers.remove(conn);
        connections.remove(conn);
        if (user != null) {
            String meetingId = userMeetings.remove(user);
            if (meetingId != null) {
                broadcast("USER_LEFT|" + meetingId + "|" + user + "|Disconnected");
            }
        }
        System.out.println("❌ Connection closed: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("📥 Received: " + message);

        String[] parts = message.split("\\|", 4);
        if (parts.length >= 4) {
            String type = parts[0];
            String meetingId = parts[1];
            String username = parts[2];
            String content = parts[3];

            // Store user-meeting association
            if (!"global".equals(meetingId)) {
                userMeetings.put(username, meetingId);
                connectionUsers.put(conn, username);
            }

            // Broadcast to all connections except sender
            for (WebSocket connection : connections) {
                if (connection != conn && connection.isOpen()) {
                    connection.send(message);
                }
            }

            System.out.println("📤 Broadcasted to " + (connections.size() - 1) + " clients");
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("❌ WebSocket error: " + ex.getMessage());
        if (conn != null) {
            System.err.println("Connection: " + conn.getRemoteSocketAddress());
        }
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("🚀 WebSocket Server ready for connections!");
        System.out.println("🌐 Listening on: " + getAddress());
    }

    public void broadcast(String message) {
        for (WebSocket conn : connections) {
            if (conn.isOpen()) {
                conn.send(message);
            }
        }
    }

    public int getConnectionCount() {
        return connections.size();
    }
}