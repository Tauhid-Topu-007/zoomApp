package org.example.zoom.websocket;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SimpleNativeWebSocketServer {
    private static final int DEFAULT_PORT = 8887;
    private static final int[] FALLBACK_PORTS = {8888, 8889, 8890, 8891, 8892, 8893, 8894, 8895};
    private static SimpleNativeWebSocketServer instance;
    private ServerSocketChannel serverChannel;
    private Selector selector;
    private final Map<SocketChannel, String> clients = new ConcurrentHashMap<>();
    private final Map<String, String> userMeetings = new ConcurrentHashMap<>();
    private Thread serverThread;
    private volatile boolean running = false;
    private int currentPort = -1;

    private SimpleNativeWebSocketServer() {
        System.out.println("🎯 Creating Simple WebSocket Server");
    }

    public static synchronized SimpleNativeWebSocketServer getInstance() {
        if (instance == null) {
            instance = new SimpleNativeWebSocketServer();
        }
        return instance;
    }

    public void start() {
        if (running) {
            System.out.println("⚠️ Server is already running on port " + currentPort);
            return;
        }

        // Try to find an available port
        for (int port : getAllPortsToTry()) {
            try {
                serverChannel = ServerSocketChannel.open();
                serverChannel.configureBlocking(false);
                InetSocketAddress address = new InetSocketAddress("0.0.0.0", port);
                serverChannel.bind(address);

                selector = Selector.open();
                serverChannel.register(selector, SelectionKey.OP_ACCEPT);

                currentPort = port;
                running = true;
                serverThread = new Thread(this::runServer);
                serverThread.setName("WebSocket-Server-" + port);
                serverThread.setDaemon(true);
                serverThread.start();

                System.out.println("✅ WebSocket Server started on port " + port);
                System.out.println("🌐 Listening on: 0.0.0.0:" + port);
                return; // Success, exit the method

            } catch (java.net.BindException e) {
                cleanupResources();
                System.out.println("⚠️ Port " + port + " is already in use, trying next port...");
                continue; // Try next port
            } catch (IOException e) {
                cleanupResources();
                System.err.println("❌ Error starting server on port " + port + ": " + e.getMessage());
                continue;
            }
        }

        // If we get here, no ports were available
        System.err.println("❌ Failed to start WebSocket server: All ports are in use");
        System.err.println("💡 Please check if another instance is running or try again later.");
    }

    private int[] getAllPortsToTry() {
        int[] ports = new int[FALLBACK_PORTS.length + 1];
        ports[0] = DEFAULT_PORT;
        System.arraycopy(FALLBACK_PORTS, 0, ports, 1, FALLBACK_PORTS.length);
        return ports;
    }

    private void cleanupResources() {
        try {
            if (serverChannel != null && serverChannel.isOpen()) {
                serverChannel.close();
            }
            if (selector != null && selector.isOpen()) {
                selector.close();
            }
        } catch (IOException e) {
            // Ignore cleanup errors
        }
        serverChannel = null;
        selector = null;
    }

    public void stop() {
        running = false;

        // Interrupt server thread
        if (serverThread != null) {
            serverThread.interrupt();
            try {
                serverThread.join(3000); // Wait for thread to finish
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        // Close all client connections
        for (SocketChannel client : new ArrayList<>(clients.keySet())) {
            try {
                client.close();
            } catch (IOException e) {
                // Ignore
            }
        }
        clients.clear();
        userMeetings.clear();

        try {
            if (selector != null && selector.isOpen()) {
                selector.close();
            }
            if (serverChannel != null && serverChannel.isOpen()) {
                serverChannel.close();
            }
        } catch (IOException e) {
            System.err.println("Error closing server resources: " + e.getMessage());
        }

        currentPort = -1;
        System.out.println("🛑 WebSocket Server stopped");
    }

    private void runServer() {
        System.out.println("🚀 WebSocket Server thread started on port " + currentPort);

        while (running && !Thread.currentThread().isInterrupted()) {
            try {
                int readyChannels = selector.select(1000); // Wait up to 1 second

                if (readyChannels == 0) {
                    continue;
                }

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iter = selectedKeys.iterator();

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    try {
                        if (key.isAcceptable()) {
                            acceptConnection(key);
                        } else if (key.isReadable()) {
                            readData(key);
                        }
                    } catch (CancelledKeyException e) {
                        // Key was cancelled, skip it
                    }
                }
            } catch (IOException e) {
                if (running) {
                    System.err.println("❌ Server error: " + e.getMessage());
                }
            } catch (ClosedSelectorException e) {
                // Normal when stopping
                break;
            }
        }

        System.out.println("🔌 WebSocket Server thread exiting");
    }

    private void acceptConnection(SelectionKey key) throws IOException {
        ServerSocketChannel serverChannel = (ServerSocketChannel) key.channel();
        SocketChannel clientChannel = serverChannel.accept();

        if (clientChannel != null) {
            clientChannel.configureBlocking(false);
            clientChannel.register(selector, SelectionKey.OP_READ);
            String clientId = "Anonymous-" + System.currentTimeMillis();
            clients.put(clientChannel, clientId);

            System.out.println("🔗 New connection from: " + clientChannel.getRemoteAddress() + " [ID: " + clientId + "]");
            sendWelcomeMessage(clientChannel);
        }
    }

    private void sendWelcomeMessage(SocketChannel clientChannel) {
        String welcomeMessage = "WELCOME|global|server|Connected to WebSocket server on port " + currentPort;
        sendMessageToClient(clientChannel, welcomeMessage);
    }

    private void readData(SelectionKey key) {
        SocketChannel clientChannel = (SocketChannel) key.channel();

        try {
            ByteBuffer buffer = ByteBuffer.allocate(4096);
            int bytesRead = clientChannel.read(buffer);

            if (bytesRead == -1) {
                // Client disconnected
                disconnectClient(clientChannel);
                return;
            }

            if (bytesRead > 0) {
                buffer.flip();
                byte[] bytes = new byte[buffer.remaining()];
                buffer.get(bytes);
                String message = new String(bytes).trim();
                handleMessage(clientChannel, message);
            }
        } catch (IOException e) {
            disconnectClient(clientChannel);
        }
    }

    private void handleMessage(SocketChannel senderChannel, String message) {
        System.out.println("📥 Received from " + clients.get(senderChannel) + ": " + message);

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

            // Update client username
            clients.put(senderChannel, username);

            // Broadcast to all other clients
            broadcast(message, senderChannel);

            System.out.println("📤 Broadcasted to " + (clients.size() - 1) + " clients");
        } else {
            System.err.println("⚠️ Invalid message format: " + message);
        }
    }

    private void broadcast(String message, SocketChannel excludeChannel) {
        int sentCount = 0;
        for (Map.Entry<SocketChannel, String> entry : clients.entrySet()) {
            SocketChannel clientChannel = entry.getKey();
            if (clientChannel != excludeChannel) {
                if (sendMessageToClient(clientChannel, message)) {
                    sentCount++;
                }
            }
        }
        if (sentCount > 0) {
            System.out.println("📡 Message sent to " + sentCount + " client(s)");
        }
    }

    private boolean sendMessageToClient(SocketChannel clientChannel, String message) {
        try {
            if (clientChannel.isOpen() && clientChannel.isConnected()) {
                ByteBuffer buffer = ByteBuffer.wrap((message + "\n").getBytes());
                clientChannel.write(buffer);
                return true;
            } else {
                disconnectClient(clientChannel);
                return false;
            }
        } catch (IOException e) {
            System.err.println("❌ Failed to send message to client: " + e.getMessage());
            disconnectClient(clientChannel);
            return false;
        }
    }

    private void disconnectClient(SocketChannel clientChannel) {
        String username = clients.remove(clientChannel);
        if (username != null) {
            String meetingId = userMeetings.remove(username);
            if (meetingId != null) {
                broadcast("USER_LEFT|" + meetingId + "|" + username + "|Disconnected", null);
            }
        }

        try {
            if (clientChannel.isOpen()) {
                clientChannel.close();
            }
        } catch (IOException e) {
            // Ignore
        }

        System.out.println("❌ Client disconnected: " + (username != null ? username : "Unknown"));
    }

    public boolean isRunning() {
        return running;
    }

    public int getPort() {
        return currentPort;
    }

    public int getClientCount() {
        return clients.size();
    }

    public String getServerInfo() {
        return "WebSocket Server running on port " + currentPort + " with " + clients.size() + " connected clients";
    }
}