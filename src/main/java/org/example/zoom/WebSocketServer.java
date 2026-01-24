package org.example.zoom.websocket;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import java.net.InetSocketAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

// Extend the imported WebSocketServer class
public class WebSocketServer extends org.java_websocket.server.WebSocketServer {

    private static final int PORT = 8887;
    private static WebSocketServer instance;
    private final Set<WebSocket> connections = ConcurrentHashMap.newKeySet();
    private final ConcurrentHashMap<String, String> userMeetings = new ConcurrentHashMap<>();

    public WebSocketServer(InetSocketAddress address) {
        super(address);
        System.out.println("🎯 WebSocket Server starting on: " + address);
    }

    public static void startServer() {
        if (instance == null) {
            InetSocketAddress address = new InetSocketAddress("0.0.0.0", PORT);
            instance = new WebSocketServer(address);
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
        connections.add(conn);
        System.out.println("🔗 New connection: " + conn.getRemoteSocketAddress());
        conn.send("WELCOME|global|server|Connected to WebSocket server");
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        connections.remove(conn);
        String user = findUserByConnection(conn);
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
            }

            // Broadcast to all connections except sender
            for (WebSocket connection : connections) {
                if (connection != conn) {
                    connection.send(message);
                }
            }

            System.out.println("📤 Broadcasted to " + (connections.size() - 1) + " clients");
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.err.println("❌ WebSocket error: " + ex.getMessage());
        ex.printStackTrace();
    }

    @Override
    public void onStart() {
        System.out.println("🚀 WebSocket Server ready for connections!");
        System.out.println("🌐 Listening on: " + getAddress());
    }

    private String findUserByConnection(WebSocket conn) {
        // In a real implementation, you'd track this better
        return "user";
    }

    public void broadcast(String message) {
        for (WebSocket conn : connections) {
            conn.send(message);
        }
    }

    public int getConnectionCount() {
        return connections.size();
    }
}