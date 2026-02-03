package org.example.zoom.websocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

public class SimpleNativeWebSocketServer {

    private static SimpleNativeWebSocketServer instance;
    private WebSocketServer webSocketServer;
    private ExecutorService executorService;
    private int port = 8887;
    private volatile boolean isRunning = false;

    private final ConcurrentHashMap<WebSocket, ClientInfo> clients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<WebSocket>> meetingRooms = new ConcurrentHashMap<>();

    private class ClientInfo {
        String username;
        String meetingId;
        long connectTime;

        ClientInfo(String username, String meetingId) {
            this.username = username;
            this.meetingId = meetingId;
            this.connectTime = System.currentTimeMillis();
        }
    }

    private SimpleNativeWebSocketServer() {
    }

    public static synchronized SimpleNativeWebSocketServer getInstance() {
        if (instance == null) {
            instance = new SimpleNativeWebSocketServer();
        }
        return instance;
    }

    public boolean start() {
        return start(port);
    }

    public synchronized boolean start(int port) {
        if (isRunning) {
            System.out.println("Server is already running on port " + this.port);
            return true;
        }

        this.port = port;

        try {
            webSocketServer = new WebSocketServer(new InetSocketAddress(port)) {
                @Override
                public void onOpen(WebSocket conn, ClientHandshake handshake) {
                    String clientAddress = conn.getRemoteSocketAddress().toString();
                    System.out.println("New client connected: " + clientAddress);

                    clients.put(conn, new ClientInfo("Unknown", "global"));

                    String welcomeMsg = "CONNECTED|global|Server|Welcome to Zoom WebSocket Server";
                    conn.send(welcomeMsg);
                    System.out.println("Sent welcome to: " + clientAddress);

                    broadcast("SYSTEM|global|Server|New user connected from " + clientAddress);
                }

                @Override
                public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                    String clientAddress = conn.getRemoteSocketAddress().toString();
                    ClientInfo info = clients.get(conn);

                    if (info != null) {
                        String username = info.username;
                        String meetingId = info.meetingId;

                        System.out.println("Client disconnected: " + username + " from " + clientAddress);

                        if (meetingId != null && !meetingId.equals("global")) {
                            removeFromMeeting(conn, meetingId);
                            broadcast("USER_LEFT|" + meetingId + "|" + username + "|left the meeting");
                        }

                        clients.remove(conn);

                        broadcast("DISCONNECTED|global|Server|" + username + " disconnected");
                    } else {
                        System.out.println("Unknown client disconnected: " + clientAddress);
                    }
                }

                @Override
                public void onMessage(WebSocket conn, String message) {
                    handleMessage(conn, message);
                }

                @Override
                public void onMessage(WebSocket conn, ByteBuffer message) {
                    String stringMessage = new String(message.array());
                    handleMessage(conn, stringMessage);
                }

                @Override
                public void onError(WebSocket conn, Exception ex) {
                    if (conn != null) {
                        System.err.println("WebSocket error from " + conn.getRemoteSocketAddress() + ": " + ex.getMessage());
                    } else {
                        System.err.println("WebSocket server error: " + ex.getMessage());
                    }
                }

                @Override
                public void onStart() {
                    System.out.println("WebSocket server started successfully on port " + getPort());
                    System.out.println("Server address: ws://" + getAddress().getHostString() + ":" + getPort());
                }
            };

            webSocketServer.setReuseAddr(true);
            webSocketServer.start();
            isRunning = true;
            System.out.println("Server started on port " + port);
            return true;

        } catch (Exception e) {
            System.err.println("Failed to start server on port " + port + ": " + e.getMessage());
            isRunning = false;

            if (port < 8895) {
                System.out.println("Trying alternative port: " + (port + 1));
                return start(port + 1);
            }
            return false;
        }
    }

    private void handleMessage(WebSocket conn, String message) {
        try {
            System.out.println("Received from " + conn.getRemoteSocketAddress() + ": " + message);

            String[] parts = message.split("\\|", 4);
            if (parts.length >= 4) {
                String type = parts[0];
                String meetingId = parts[1];
                String username = parts[2];
                String content = parts[3];

                ClientInfo info = clients.get(conn);
                if (info != null) {
                    info.username = username;
                    info.meetingId = meetingId;

                    if (!meetingId.equals("global")) {
                        addToMeeting(conn, meetingId);
                    }
                }

                switch (type) {
                    case "CHAT":
                    case "CHAT_MESSAGE":
                        broadcastToMeeting(meetingId, message);
                        System.out.println("Broadcasted CHAT to meeting " + meetingId + ": " + content);
                        break;

                    case "VIDEO_STATUS":
                        broadcastToMeeting(meetingId, message);
                        System.out.println("Broadcasted VIDEO_STATUS to meeting " + meetingId + ": " + content);
                        break;

                    case "VIDEO_FRAME":
                        broadcastToMeeting(meetingId, message);
                        break;

                    case "USER_JOINED":
                        broadcastToMeeting(meetingId, message);
                        System.out.println("Broadcasted USER_JOINED to meeting " + meetingId + ": " + username);
                        break;

                    case "USER_LEFT":
                        broadcastToMeeting(meetingId, message);
                        System.out.println("Broadcasted USER_LEFT to meeting " + meetingId + ": " + username);
                        break;

                    case "MEETING_CREATED":
                        broadcastToMeeting(meetingId, message);
                        System.out.println("Broadcasted MEETING_CREATED to meeting " + meetingId + ": " + username);
                        break;

                    case "AUDIO_STATUS":
                        broadcastToMeeting(meetingId, message);
                        System.out.println("Broadcasted AUDIO_STATUS to meeting " + meetingId + ": " + content);
                        break;

                    case "AUDIO_CONTROL":
                        broadcastToMeeting(meetingId, message);
                        System.out.println("Broadcasted AUDIO_CONTROL to meeting " + meetingId + ": " + content);
                        break;

                    case "VIDEO_CONTROL":
                        broadcastToMeeting(meetingId, message);
                        System.out.println("Broadcasted VIDEO_CONTROL to meeting " + meetingId + ": " + content);
                        break;

                    case "FILE_SHARE":
                        broadcastToMeeting(meetingId, message);
                        System.out.println("Broadcasted FILE_SHARE to meeting " + meetingId + ": " + content);
                        break;

                    case "WEBRTC_SIGNAL":
                        broadcastToMeeting(meetingId, message);
                        System.out.println("Forwarded WEBRTC_SIGNAL in meeting " + meetingId);
                        break;

                    default:
                        broadcastToMeeting(meetingId, message);
                        System.out.println("Broadcasted unknown type " + type + " to meeting " + meetingId);
                        break;
                }
            } else {
                System.out.println("Simple message format, broadcasting to all: " + message);
                broadcast("CHAT|global|System|" + message);
            }

        } catch (Exception e) {
            System.err.println("Error handling message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void addToMeeting(WebSocket conn, String meetingId) {
        meetingRooms.computeIfAbsent(meetingId, k -> Collections.synchronizedSet(new HashSet<>())).add(conn);

        ClientInfo info = clients.get(conn);
        if (info != null) {
            info.meetingId = meetingId;
        }

        System.out.println("Added " + conn.getRemoteSocketAddress() + " to meeting " + meetingId);
    }

    private void removeFromMeeting(WebSocket conn, String meetingId) {
        Set<WebSocket> meetingClients = meetingRooms.get(meetingId);
        if (meetingClients != null) {
            meetingClients.remove(conn);
            System.out.println("Removed " + conn.getRemoteSocketAddress() + " from meeting " + meetingId);

            if (meetingClients.isEmpty()) {
                meetingRooms.remove(meetingId);
                System.out.println("Meeting room " + meetingId + " is now empty and removed");
            }
        }
    }

    private void broadcastToMeeting(String meetingId, String message) {
        Set<WebSocket> meetingClients = meetingRooms.get(meetingId);
        if (meetingClients != null && !meetingClients.isEmpty()) {
            System.out.println("Broadcasting to meeting " + meetingId + " (" + meetingClients.size() + " clients): " +
                    message.substring(0, Math.min(50, message.length())) + "...");

            for (WebSocket client : meetingClients) {
                if (client != null && client.isOpen()) {
                    try {
                        client.send(message);
                    } catch (Exception e) {
                        System.err.println("Error sending to client in meeting " + meetingId + ": " + e.getMessage());
                    }
                }
            }
        } else {
            System.out.println("No clients in meeting " + meetingId + " to broadcast to");
        }
    }

    private void broadcast(String message) {
        if (webSocketServer != null) {
            webSocketServer.broadcast(message);
        }
    }

    public void stop() {
        if (webSocketServer != null && isRunning) {
            try {
                for (WebSocket conn : clients.keySet()) {
                    if (conn != null && conn.isOpen()) {
                        conn.close();
                    }
                }
                clients.clear();
                meetingRooms.clear();

                webSocketServer.stop();
                isRunning = false;
                System.out.println("WebSocket server stopped");

            } catch (Exception e) {
                System.err.println("Error stopping server: " + e.getMessage());
            }
        }
    }

    public boolean isRunning() {
        return isRunning && webSocketServer != null;
    }

    public int getPort() {
        return port;
    }

    public int getClientCount() {
        return clients.size();
    }

    public int getMeetingCount() {
        return meetingRooms.size();
    }
}