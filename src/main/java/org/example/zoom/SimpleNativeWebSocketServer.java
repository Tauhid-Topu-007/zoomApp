package org.example.zoom.websocket;

import java.io.IOException;
import java.net.BindException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

public class SimpleNativeWebSocketServer {

    private static SimpleNativeWebSocketServer instance;
    private WebSocketServer webSocketServer;
    private ExecutorService executorService;
    private int port = 8887;
    private volatile boolean isRunning = false;
    private AtomicBoolean isStarting = new AtomicBoolean(false);
    private int actualPort = -1;
    private String bindAddress = "0.0.0.0";

    // Port range for fallback
    private static final int MIN_PORT = 8887;
    private static final int MAX_PORT = 8895;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final ConcurrentHashMap<WebSocket, ClientInfo> clients = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<WebSocket>> meetingRooms = new ConcurrentHashMap<>();

    private class ClientInfo {
        String username;
        String meetingId;
        long connectTime;
        String ipAddress;

        ClientInfo(String username, String meetingId, String ipAddress) {
            this.username = username;
            this.meetingId = meetingId;
            this.connectTime = System.currentTimeMillis();
            this.ipAddress = ipAddress;
        }
    }

    private SimpleNativeWebSocketServer() {
        executorService = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "WebSocketServer-Thread");
            t.setDaemon(true);
            return t;
        });
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

    public synchronized boolean start(int preferredPort) {
        if (isRunning) {
            System.out.println("Server is already running on port " + actualPort);
            return true;
        }

        if (isStarting.get()) {
            System.out.println("Server is already starting...");
            return false;
        }

        isStarting.set(true);
        this.port = preferredPort;

        System.out.println("Attempting to start WebSocket server on port: " + preferredPort);

        // Try the preferred port first
        if (tryStartOnPort(preferredPort)) {
            isStarting.set(false);
            return true;
        }

        // If preferred port fails, try other ports in range
        System.out.println("Port " + preferredPort + " is unavailable, scanning for available ports...");

        for (int attempt = 1; attempt <= (MAX_PORT - MIN_PORT); attempt++) {
            int testPort = MIN_PORT + ((preferredPort - MIN_PORT + attempt) % (MAX_PORT - MIN_PORT + 1));

            if (testPort == preferredPort) continue;

            System.out.println("Trying alternative port: " + testPort);
            if (tryStartOnPort(testPort)) {
                this.port = testPort;
                this.actualPort = testPort;
                System.out.println("Successfully started server on alternative port: " + testPort);
                isStarting.set(false);
                return true;
            }
        }

        // Try completely random ports as last resort
        System.out.println("No ports in range available, trying random ports...");
        for (int attempt = 0; attempt < 5; attempt++) {
            int randomPort = 9000 + (int)(Math.random() * 1000);
            System.out.println("Trying random port: " + randomPort);
            if (tryStartOnPort(randomPort)) {
                this.port = randomPort;
                this.actualPort = randomPort;
                System.out.println("Successfully started server on random port: " + randomPort);
                isStarting.set(false);
                return true;
            }
        }

        System.err.println("Failed to start server on any port after multiple attempts");
        isStarting.set(false);
        return false;
    }

    private boolean tryStartOnPort(int port) {
        try {
            // Check if port is available before attempting to bind
            if (!isPortAvailable(port)) {
                System.out.println("Port " + port + " is already in use by another process");
                return false;
            }

            webSocketServer = new WebSocketServer(new InetSocketAddress(bindAddress, port)) {
                @Override
                public void onOpen(WebSocket conn, ClientHandshake handshake) {
                    String clientAddress = conn.getRemoteSocketAddress().toString();
                    String clientIp = conn.getRemoteSocketAddress().getAddress().getHostAddress();
                    System.out.println("New client connected: " + clientAddress);

                    clients.put(conn, new ClientInfo("Unknown", "global", clientIp));

                    String welcomeMsg = "CONNECTED|global|Server|Welcome to Zoom WebSocket Server|" + getPort();
                    conn.send(welcomeMsg);
                    System.out.println("Sent welcome to: " + clientAddress);

                    broadcast("SYSTEM|global|Server|New user connected from " + clientAddress);
                }

                @Override
                public void onClose(WebSocket conn, int code, String reason, boolean remote) {
                    String clientAddress = conn.getRemoteSocketAddress() != null ?
                            conn.getRemoteSocketAddress().toString() : "unknown";
                    ClientInfo info = clients.remove(conn);

                    if (info != null) {
                        String username = info.username;
                        String meetingId = info.meetingId;

                        System.out.println("Client disconnected: " + username + " from " + clientAddress +
                                " (Code: " + code + ", Reason: " + reason + ")");

                        if (meetingId != null && !meetingId.equals("global")) {
                            removeFromMeeting(conn, meetingId);
                            broadcastToMeeting(meetingId, "USER_LEFT|" + meetingId + "|" + username + "|left the meeting", null);
                        }

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
                    try {
                        String stringMessage = new String(message.array(), "UTF-8");
                        handleMessage(conn, stringMessage);
                    } catch (Exception e) {
                        System.err.println("Error decoding binary message: " + e.getMessage());
                    }
                }

                @Override
                public void onError(WebSocket conn, Exception ex) {
                    if (conn != null) {
                        String clientAddress = conn.getRemoteSocketAddress() != null ?
                                conn.getRemoteSocketAddress().toString() : "unknown";
                        System.err.println("WebSocket error from " + clientAddress + ": " + ex.getMessage());

                        if (ex instanceof BindException) {
                            System.err.println("Port binding error - this should not happen after start");
                        }
                    } else {
                        System.err.println("WebSocket server error: " + ex.getMessage());
                        if (ex instanceof BindException) {
                            System.err.println("Failed to bind to port " + getPort() + " - address already in use");
                            isRunning = false;
                        }
                    }
                }

                @Override
                public void onStart() {
                    actualPort = getPort();
                    isRunning = true;
                    System.out.println("\n" + "=".repeat(50));
                    System.out.println("✅ WebSocket server started successfully!");
                    System.out.println("   Port: " + actualPort);
                    System.out.println("   Address: " + getAddress().getHostString());
                    System.out.println("   URL: ws://" + getAddress().getHostString() + ":" + actualPort);
                    System.out.println("=".repeat(50) + "\n");
                }
            };

            // Configure server
            webSocketServer.setReuseAddr(true);
            webSocketServer.setTcpNoDelay(true);
            webSocketServer.setConnectionLostTimeout(30);
            webSocketServer.setMaxPendingConnections(100);

            // Start server in background thread
            executorService.submit(() -> {
                try {
                    webSocketServer.start();
                } catch (Exception e) {
                    System.err.println("Failed to start WebSocket server thread on port " + port + ": " + e.getMessage());
                    isRunning = false;
                }
            });

            // Wait for server to start
            long startTime = System.currentTimeMillis();
            long timeout = 5000; // 5 seconds timeout

            while (!isRunning && (System.currentTimeMillis() - startTime) < timeout) {
                Thread.sleep(100);
            }

            return isRunning;

        } catch (Exception e) {
            System.err.println("Failed to start server on port " + port + ": " + e.getMessage());

            // Clean up if server was created but not started
            if (webSocketServer != null) {
                try {
                    webSocketServer.stop();
                } catch (Exception ex) {
                    // Ignore
                }
                webSocketServer = null;
            }

            return false;
        }
    }

    private void handleMessage(WebSocket conn, String message) {
        try {
            String clientAddress = conn.getRemoteSocketAddress() != null ?
                    conn.getRemoteSocketAddress().toString() : "unknown";
            System.out.println("Received from " + clientAddress + ": " + message);

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
                }

                if (!meetingId.equals("global") && !meetingId.isEmpty()) {
                    addToMeeting(conn, meetingId);
                }

                switch (type) {
                    case "CHAT":
                    case "CHAT_MESSAGE":
                        broadcastToMeeting(meetingId, message, conn);
                        System.out.println("Broadcasted CHAT to meeting " + meetingId + ": " + content);
                        break;

                    case "VIDEO_STATUS":
                        broadcastToMeeting(meetingId, message, conn);
                        System.out.println("Broadcasted VIDEO_STATUS to meeting " + meetingId + ": " + content);
                        break;

                    case "VIDEO_FRAME":
                        broadcastToMeeting(meetingId, message, conn);
                        if (content.length() > 100) {
                            System.out.println("Broadcasted VIDEO_FRAME to meeting " + meetingId + " (size: " + content.length() + " chars)");
                        }
                        break;

                    case "USER_JOINED":
                        broadcastToMeeting(meetingId, message, conn);
                        System.out.println("Broadcasted USER_JOINED to meeting " + meetingId + ": " + username);
                        break;

                    case "USER_LEFT":
                        broadcastToMeeting(meetingId, message, conn);
                        System.out.println("Broadcasted USER_LEFT to meeting " + meetingId + ": " + username);
                        removeFromMeeting(conn, meetingId);
                        break;

                    case "MEETING_CREATED":
                        broadcastToMeeting(meetingId, message, conn);
                        System.out.println("Broadcasted MEETING_CREATED to meeting " + meetingId + ": " + username);
                        break;

                    case "AUDIO_STATUS":
                        broadcastToMeeting(meetingId, message, conn);
                        System.out.println("Broadcasted AUDIO_STATUS to meeting " + meetingId + ": " + content);
                        break;

                    case "AUDIO_CONTROL":
                        broadcastToMeeting(meetingId, message, conn);
                        System.out.println("Broadcasted AUDIO_CONTROL to meeting " + meetingId + ": " + content);
                        break;

                    case "VIDEO_CONTROL":
                        broadcastToMeeting(meetingId, message, conn);
                        System.out.println("Broadcasted VIDEO_CONTROL to meeting " + meetingId + ": " + content);
                        break;

                    case "FILE_SHARE":
                        broadcastToMeeting(meetingId, message, conn);
                        System.out.println("Broadcasted FILE_SHARE to meeting " + meetingId + ": " + content.substring(0, Math.min(50, content.length())) + "...");
                        break;

                    case "WEBRTC_SIGNAL":
                        broadcastToMeeting(meetingId, message, conn);
                        System.out.println("Forwarded WEBRTC_SIGNAL in meeting " + meetingId);
                        break;

                    case "PING":
                        conn.send("PONG|" + meetingId + "|Server|" + System.currentTimeMillis());
                        break;

                    default:
                        broadcastToMeeting(meetingId, message, conn);
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

    private void broadcastToMeeting(String meetingId, String message, WebSocket exclude) {
        Set<WebSocket> meetingClients = meetingRooms.get(meetingId);
        if (meetingClients != null && !meetingClients.isEmpty()) {
            int sentCount = 0;
            for (WebSocket client : meetingClients) {
                if (client != null && client.isOpen() && client != exclude) {
                    try {
                        client.send(message);
                        sentCount++;
                    } catch (Exception e) {
                        System.err.println("Error sending to client in meeting " + meetingId + ": " + e.getMessage());
                    }
                }
            }
            if (sentCount > 0) {
                System.out.println("Broadcast to meeting " + meetingId + ": sent to " + sentCount + " clients");
            }
        }
    }

    public void broadcastToMeeting(String meetingId, String message) {
        broadcastToMeeting(meetingId, message, null);
    }

    public void broadcast(String message) {
        if (webSocketServer != null && isRunning) {
            int sentCount = 0;
            for (WebSocket client : clients.keySet()) {
                if (client != null && client.isOpen()) {
                    try {
                        client.send(message);
                        sentCount++;
                    } catch (Exception e) {
                        System.err.println("Error broadcasting to client: " + e.getMessage());
                    }
                }
            }
            System.out.println("Global broadcast: sent to " + sentCount + " clients");
        }
    }

    public void stop() {
        if (webSocketServer != null && isRunning) {
            try {
                System.out.println("Stopping WebSocket server...");

                // Notify all clients
                broadcast("SYSTEM|global|Server|Server is shutting down");

                // Close all connections
                for (WebSocket conn : clients.keySet()) {
                    if (conn != null && conn.isOpen()) {
                        conn.close(1000, "Server shutting down");
                    }
                }

                // Clear collections
                clients.clear();
                meetingRooms.clear();

                // Stop server
                webSocketServer.stop(5000);
                isRunning = false;
                actualPort = -1;

                // Shutdown executor
                if (executorService != null) {
                    executorService.shutdown();
                    try {
                        if (!executorService.awaitTermination(5000, TimeUnit.MILLISECONDS)) {
                            executorService.shutdownNow();
                        }
                    } catch (InterruptedException e) {
                        executorService.shutdownNow();
                        Thread.currentThread().interrupt();
                    }
                }

                System.out.println("WebSocket server stopped successfully");

            } catch (Exception e) {
                System.err.println("Error stopping server: " + e.getMessage());
                e.printStackTrace();
            } finally {
                webSocketServer = null;
            }
        }
    }

    public boolean isRunning() {
        return isRunning;
    }

    public int getPort() {
        return actualPort != -1 ? actualPort : port;
    }

    public int getClientCount() {
        return clients.size();
    }

    public int getMeetingCount() {
        return meetingRooms.size();
    }

    public Set<String> getActiveMeetings() {
        return new HashSet<>(meetingRooms.keySet());
    }

    public String getBindAddress() {
        return bindAddress;
    }

    public void setBindAddress(String bindAddress) {
        this.bindAddress = bindAddress;
    }

    // Utility method to check if a port is available
    private boolean isPortAvailable(int port) {
        try (java.net.ServerSocket ss = new java.net.ServerSocket(port)) {
            ss.setReuseAddress(true);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    // Utility method to find an available port
    public static int findAvailablePort(int startPort, int maxAttempts) {
        for (int i = 0; i < maxAttempts; i++) {
            int testPort = startPort + i;
            try (java.net.ServerSocket ss = new java.net.ServerSocket(testPort)) {
                ss.setReuseAddress(true);
                return testPort;
            } catch (IOException e) {
                // Port in use, continue
            }
        }
        return -1;
    }

    // Reset instance (useful for testing)
    public static synchronized void resetInstance() {
        if (instance != null) {
            instance.stop();
            instance = null;
        }
    }
}