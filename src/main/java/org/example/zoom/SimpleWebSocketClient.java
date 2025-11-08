package org.example.zoom.websocket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.net.http.WebSocket.Listener;
import java.time.Duration;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

public class SimpleWebSocketClient implements Listener {

    private final AtomicReference<WebSocket> webSocket = new AtomicReference<>();
    private final Consumer<String> messageHandler;
    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final String serverUrl;
    private final AtomicReference<String> currentUser = new AtomicReference<>();
    private final HttpClient httpClient;
    private final ScheduledExecutorService heartbeatExecutor;
    private final ScheduledExecutorService reconnectExecutor;

    // Connection settings
    private static final int RECONNECT_DELAY_MS = 3000;
    private static final int HEARTBEAT_INTERVAL_MS = 15000;
    private static final int CONNECTION_TIMEOUT_MS = 10000;

    // Message queue for when connection is unstable
    private final BlockingQueue<String> messageQueue = new LinkedBlockingQueue<>();
    private final ScheduledExecutorService messageProcessor;

    // Connection promise to track when connection is ready
    private final AtomicReference<CompletableFuture<Void>> connectionReady = new AtomicReference<>(
            new CompletableFuture<>()
    );

    // ✅ Constructor
    public SimpleWebSocketClient(String serverUrl, Consumer<String> messageHandler) {
        this.serverUrl = serverUrl;
        this.messageHandler = messageHandler;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        this.heartbeatExecutor = Executors.newSingleThreadScheduledExecutor();
        this.reconnectExecutor = Executors.newSingleThreadScheduledExecutor();
        this.messageProcessor = Executors.newSingleThreadScheduledExecutor();

        // Start message processor
        startMessageProcessor();
        connect();
    }

    // ✅ Connect to WebSocket server with timeout
    public void connect() {
        try {
            System.out.println("🔗 Connecting to: " + serverUrl);

            // Create a new connection ready promise for this connection attempt
            CompletableFuture<Void> newConnectionReady = new CompletableFuture<>();
            connectionReady.set(newConnectionReady);

            CompletableFuture<WebSocket> future = httpClient.newWebSocketBuilder()
                    .buildAsync(URI.create(serverUrl), this);

            // Add timeout
            future.orTimeout(CONNECTION_TIMEOUT_MS, TimeUnit.MILLISECONDS)
                    .thenAccept(ws -> {
                        this.webSocket.set(ws);
                        System.out.println("✅ WebSocket connection established to: " + serverUrl);
                        // Connection will be marked as ready in onOpen()
                    })
                    .exceptionally(e -> {
                        System.err.println("❌ Connection failed or timed out: " + e.getMessage());
                        // Only fail the connection ready if it's still the current one
                        if (connectionReady.get() == newConnectionReady && !newConnectionReady.isDone()) {
                            newConnectionReady.completeExceptionally(e);
                        }
                        if (messageHandler != null) {
                            messageHandler.accept("SYSTEM|global|Client|ERROR|Connection failed: " + e.getMessage());
                        }
                        scheduleReconnect();
                        return null;
                    });

        } catch (Exception e) {
            System.err.println("❌ Error creating WebSocket: " + e.getMessage());
            CompletableFuture<Void> currentReady = connectionReady.get();
            if (!currentReady.isDone()) {
                currentReady.completeExceptionally(e);
            }
            if (messageHandler != null) {
                messageHandler.accept("SYSTEM|global|Client|ERROR|Connection error: " + e.getMessage());
            }
            scheduleReconnect();
        }
    }

    // ✅ Enhanced WebSocket event handlers
    @Override
    public void onOpen(WebSocket webSocket) {
        this.webSocket.set(webSocket);
        this.connected.set(true);

        System.out.println("✅ WebSocket connection opened successfully to: " + serverUrl);

        // Mark connection as ready - only if this is still the current connection attempt
        CompletableFuture<Void> currentReady = connectionReady.get();
        if (!currentReady.isDone()) {
            currentReady.complete(null);
            System.out.println("✅ Connection marked as ready for messaging");
        }

        // Start heartbeat
        startHeartbeat();

        // Send welcome/join message if we have a user
        String user = currentUser.get();
        if (user != null) {
            // Use a small delay to ensure connection is fully ready
            scheduleDelayedTask(() -> {
                sendMessageInternal("USER_JOINED|global|" + user + "|Connected from Java client");
            }, 500);
        }

        // Process any queued messages with a small delay
        scheduleDelayedTask(this::processQueuedMessages, 1000);

        // Notify message handler
        if (messageHandler != null) {
            scheduleDelayedTask(() -> {
                messageHandler.accept("SYSTEM|global|Server|CONNECTED|" + (user != null ? user : "JavaClient") + "|Connected to server at " + serverUrl);
            }, 100);
        }

        Listener.super.onOpen(webSocket);
    }

    // ✅ Helper for delayed tasks
    private void scheduleDelayedTask(Runnable task, long delayMs) {
        CompletableFuture.delayedExecutor(delayMs, TimeUnit.MILLISECONDS)
                .execute(task);
    }

    @Override
    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
        String message = data.toString();
        System.out.println("📨 Received: " + message);

        if (messageHandler != null) {
            messageHandler.accept(message);
        }

        // Handle different message types from Node.js server
        handleServerMessage(message);

        return Listener.super.onText(webSocket, data, last);
    }

    @Override
    public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
        this.connected.set(false);
        this.webSocket.set(null);

        System.out.println("🔴 WebSocket connection closed: " + reason + " (code: " + statusCode + ")");

        // Stop heartbeat
        stopHeartbeat();

        // Notify message handler
        if (messageHandler != null) {
            messageHandler.accept("SYSTEM|global|Server|DISCONNECTED|Connection closed: " + reason);
        }

        // Schedule reconnection if not a normal closure
        if (statusCode != 1000 && statusCode != 1001) {
            scheduleReconnect();
        }

        return Listener.super.onClose(webSocket, statusCode, reason);
    }

    @Override
    public void onError(WebSocket webSocket, Throwable error) {
        this.connected.set(false);
        System.err.println("❌ WebSocket error: " + error.getMessage());

        // Stop heartbeat
        stopHeartbeat();

        // Notify message handler
        if (messageHandler != null) {
            messageHandler.accept("SYSTEM|global|Server|ERROR|WebSocket error: " + error.getMessage());
        }

        // Schedule reconnection
        scheduleReconnect();

        Listener.super.onError(webSocket, error);
    }

    // ✅ Wait for connection to be ready before sending messages
    private CompletableFuture<Void> waitForConnectionReady() {
        CompletableFuture<Void> currentReady = connectionReady.get();
        return currentReady.orTimeout(15, TimeUnit.SECONDS) // Increased timeout
                .exceptionally(e -> {
                    System.err.println("❌ Connection ready timeout: " + e.getMessage());
                    // Don't return null, re-throw to indicate failure
                    throw new CompletionException(e);
                });
    }

    // ✅ Message queue processor to handle unstable connections
    private void startMessageProcessor() {
        messageProcessor.scheduleAtFixedRate(() -> {
            if (isConnected() && !messageQueue.isEmpty()) {
                processQueuedMessages();
            }
        }, 100, 100, TimeUnit.MILLISECONDS);
    }

    private void processQueuedMessages() {
        int processed = 0;
        while (!messageQueue.isEmpty() && isConnected() && processed < 10) { // Limit per cycle
            String message = messageQueue.poll();
            if (message != null) {
                sendMessageInternal(message);
                processed++;
            }
        }
        if (processed > 0) {
            System.out.println("🔄 Processed " + processed + " queued messages");
        }
    }

    // ✅ Handle different message types from Node.js server
    private void handleServerMessage(String message) {
        String[] parts = message.split("\\|", 5);
        if (parts.length >= 4) {
            String type = parts[0];

            switch (type) {
                case "SYSTEM":
                    System.out.println("🔧 System message: " + message);
                    // If we receive a system message, connection is definitely ready
                    if (message.contains("CONNECTED") || message.contains("Welcome")) {
                        CompletableFuture<Void> currentReady = connectionReady.get();
                        if (!currentReady.isDone()) {
                            currentReady.complete(null);
                            System.out.println("✅ Connection confirmed ready via server message");
                        }
                    }
                    break;
                case "PING":
                    handlePingMessage(message);
                    break;
                case "USER_JOINED":
                    System.out.println("👤 User joined: " + parts[2]);
                    break;
                case "USER_LEFT":
                    System.out.println("👤 User left: " + parts[2]);
                    break;
                default:
                    break;
            }
        }
    }

    // ✅ Heartbeat mechanism for Node.js server
    private void startHeartbeat() {
        stopHeartbeat(); // Stop any existing tasks first

        if (!heartbeatExecutor.isShutdown()) {
            heartbeatExecutor.scheduleAtFixedRate(() -> {
                if (isConnected()) {
                    try {
                        String user = currentUser.get();
                        sendMessageInternal("PING|global|" + (user != null ? user : "JavaClient") + "|" + System.currentTimeMillis());
                        System.out.println("💓 Sent heartbeat ping");
                    } catch (Exception e) {
                        System.err.println("❌ Heartbeat failed: " + e.getMessage());
                        scheduleReconnect();
                    }
                }
            }, HEARTBEAT_INTERVAL_MS, HEARTBEAT_INTERVAL_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void stopHeartbeat() {
        // Instead of recreating the executor, just shutdown existing tasks
        try {
            // Get and cancel all scheduled tasks
            heartbeatExecutor.shutdownNow();
        } catch (Exception e) {
            System.err.println("❌ Error stopping heartbeat: " + e.getMessage());
        }
    }

    // ✅ Handle ping messages from server
    private void handlePingMessage(String message) {
        String[] parts = message.split("\\|", 4);
        if (parts.length >= 4 && "PING".equals(parts[0])) {
            String user = currentUser.get();
            sendMessage("PONG", parts[1], user != null ? user : "JavaClient", parts[3]);
        }
    }

    // ✅ Automatic reconnection
    private void scheduleReconnect() {
        if (reconnectExecutor.isShutdown()) {
            return;
        }

        System.out.println("🔄 Scheduling reconnection in " + RECONNECT_DELAY_MS + "ms");

        reconnectExecutor.schedule(() -> {
            if (!isConnected()) {
                System.out.println("🔄 Attempting to reconnect to: " + serverUrl);
                connect();
            }
        }, RECONNECT_DELAY_MS, TimeUnit.MILLISECONDS);
    }

    // ✅ Internal message sending with connection ready check
    private void sendMessageInternal(String message) {
        if (!isConnected()) {
            System.err.println("❌ Not connected, queuing message: " + message);
            if (!message.startsWith("PING")) {
                messageQueue.offer(message);
            }
            return;
        }

        WebSocket ws = webSocket.get();
        if (ws != null) {
            try {
                ws.sendText(message, true)
                        .thenRun(() -> {
                            System.out.println("📤 Sent: " + message);
                        })
                        .exceptionally(e -> {
                            System.err.println("❌ Failed to send message: " + e.getMessage());
                            if (e.getMessage().contains("closed") || e.getMessage().contains("Output closed")) {
                                connected.set(false);
                                scheduleReconnect();
                            }
                            // Re-queue the message if it failed
                            if (!message.startsWith("PING")) {
                                messageQueue.offer(message);
                            }
                            return null;
                        });
            } catch (Exception e) {
                System.err.println("❌ Exception sending message: " + e.getMessage());
                connected.set(false);
                if (!message.startsWith("PING")) {
                    messageQueue.offer(message);
                }
                scheduleReconnect();
            }
        }
    }

    // ✅ Public method to send formatted messages - waits for connection ready
    public void sendMessage(String type, String meetingId, String username, String content) {
        String message = type + "|" + meetingId + "|" + username + "|" + content;

        // For critical connection messages, send immediately without waiting
        if ("USER_JOINED".equals(type) || "PONG".equals(type)) {
            if (isConnected()) {
                sendMessageInternal(message);
            } else {
                System.err.println("❌ Not connected, queuing critical message: " + message);
                messageQueue.offer(message);
            }
            return;
        }

        // For other messages, wait for connection to be ready
        waitForConnectionReady().thenRun(() -> {
            if (isConnected()) {
                sendMessageInternal(message);
            } else {
                System.err.println("❌ Connection not ready, queuing message: " + message);
                if (!type.equals("PING")) {
                    messageQueue.offer(message);
                }
            }
        }).exceptionally(e -> {
            System.err.println("❌ Connection never became ready, queuing message: " + message);
            if (!type.equals("PING")) {
                messageQueue.offer(message);
            }
            return null;
        });
    }

    // ✅ Setter for username - sends join message immediately
    public void setCurrentUser(String username) {
        this.currentUser.set(username);

        if (username != null) {
            // Send user join message immediately without waiting for connection ready
            if (isConnected()) {
                sendMessageInternal("USER_JOINED|global|" + username + "|User identified as " + username);
            } else {
                // Queue it for when connection is ready
                messageQueue.offer("USER_JOINED|global|" + username + "|User identified as " + username);
            }
        }
    }

    // ✅ Reconnect method for manual reconnection
    public void reconnect() {
        System.out.println("🔄 Manual reconnection requested");
        disconnect();

        // Use a new thread to reconnect after a delay
        new Thread(() -> {
            try {
                Thread.sleep(RECONNECT_DELAY_MS);
                connect();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    // ✅ Disconnect cleanly
    public void disconnect() {
        System.out.println("🔴 Disconnecting from WebSocket server: " + serverUrl);
        this.connected.set(false);

        // Stop all executors
        stopAllExecutors();

        WebSocket ws = webSocket.get();
        if (ws != null) {
            try {
                ws.sendClose(WebSocket.NORMAL_CLOSURE, "Java client disconnecting")
                        .orTimeout(2, TimeUnit.SECONDS)
                        .thenRun(() -> {
                            System.out.println("✅ Disconnected cleanly from: " + serverUrl);
                        })
                        .exceptionally(e -> {
                            System.err.println("❌ Error during close: " + e.getMessage());
                            return null;
                        });
            } catch (Exception e) {
                System.err.println("❌ Error sending close: " + e.getMessage());
            }
            webSocket.set(null);
        }

        // Clear message queue
        messageQueue.clear();

        if (messageHandler != null) {
            messageHandler.accept("SYSTEM|global|Server|DISCONNECTED|Disconnected from server: " + serverUrl);
        }
    }

    // ✅ Stop all executors properly
    private void stopAllExecutors() {
        try {
            // Shutdown all executors
            heartbeatExecutor.shutdownNow();
            reconnectExecutor.shutdownNow();
            messageProcessor.shutdownNow();
        } catch (Exception e) {
            System.err.println("❌ Error stopping executors: " + e.getMessage());
        }
    }

    // ✅ Connection status
    public boolean isConnected() {
        return connected.get() && webSocket.get() != null;
    }

    // ✅ Get server URL
    public String getServerUrl() {
        return serverUrl;
    }

    // ✅ Get current user
    public String getCurrentUser() {
        return currentUser.get();
    }

    // ✅ Get queued message count
    public int getQueuedMessageCount() {
        return messageQueue.size();
    }

    // ✅ Check if connection is ready for messages
    public boolean isConnectionReady() {
        CompletableFuture<Void> currentReady = connectionReady.get();
        return currentReady.isDone() && !currentReady.isCompletedExceptionally() && isConnected();
    }

    // ✅ Test connection by sending a test message
    public void sendTestMessage() {
        String user = currentUser.get();
        sendMessage("CHAT", "test", user != null ? user : "TestUser",
                "Test message from Java client to multi-device server");
    }

    // ✅ Get connection info for debugging
    public String getConnectionInfo() {
        CompletableFuture<Void> currentReady = connectionReady.get();
        String readyStatus = currentReady.isDone() ?
                (currentReady.isCompletedExceptionally() ? "ERROR" : "READY") : "PENDING";

        return String.format("URL: %s, Connected: %s, Ready: %s, User: %s, Queued: %d",
                serverUrl, connected.get(), readyStatus, currentUser.get(), messageQueue.size());
    }

    // ✅ Cleanup resources
    public void cleanup() {
        disconnect();
    }
}