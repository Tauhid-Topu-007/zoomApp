package org.example.zoom.websocket;

import java.util.function.Consumer;

public class SimpleWebSocketClient {
    private Consumer<String> messageHandler;
    private boolean connected = false;
    private Thread messageSimulator;
    private String currentUser;

    public SimpleWebSocketClient(String serverUrl, Consumer<String> messageHandler) {
        this.messageHandler = messageHandler;
        connect(serverUrl);
    }

    private void connect(String serverUrl) {
        System.out.println("🔗 Attempting to connect to: " + serverUrl);

        // Simulate connection process
        new Thread(() -> {
            try {
                Thread.sleep(1500); // Simulate connection delay
                connected = true;
                System.out.println("✅ Connected to chat server (simulation mode)");
                messageHandler.accept("CONNECTED: Successfully connected to chat server");

                // Simulate welcome message
                Thread.sleep(1000);
                messageHandler.accept("SYSTEM|global|Server|Welcome to Zoom Chat! Start chatting with your team.");

                // Start simulating incoming messages
                startMessageSimulation();

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    private void startMessageSimulation() {
        messageSimulator = new Thread(() -> {
            try {
                while (connected && !Thread.currentThread().isInterrupted()) {
                    Thread.sleep(15000); // Send a system message every 15 seconds

                    if (connected) {
                        String[] systemMessages = {
                                "SYSTEM|global|Server|🔒 Chat is encrypted and secure",
                                "SYSTEM|global|Server|👥 There are 3 users online",
                                "SYSTEM|global|Server|💬 Type your messages below to start chatting",
                                "SYSTEM|global|Server|📎 You can share files using the attachment button"
                        };
                        String randomMessage = systemMessages[(int)(Math.random() * systemMessages.length)];
                        messageHandler.accept(randomMessage);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        });
        messageSimulator.start();
    }

    public void sendMessage(String type, String meetingId, String username, String content) {
        if (!connected) {
            messageHandler.accept("ERROR: Not connected to server");
            return;
        }

        String fullMessage = type + "|" + meetingId + "|" + username + "|" + content;
        System.out.println("📤 Sending: " + fullMessage);

        // Echo the message back immediately (simulate sending to server)
        messageHandler.accept("CHAT|" + meetingId + "|" + username + "|" + content);

        // Simulate other users responding (40% chance)
        if (Math.random() < 0.4) {
            simulateResponse(meetingId, content, username);
        }
    }

    private void simulateResponse(String meetingId, String originalMessage, String originalUser) {
        new Thread(() -> {
            try {
                // Random delay between 1-4 seconds
                Thread.sleep(1000 + (int)(Math.random() * 3000));

                if (!connected) return;

                String[] otherUsers = {"Alice", "Bob", "Charlie", "Diana", "Eve"};
                String[] responses = {
                        "Thanks for sharing that!",
                        "I agree with your point",
                        "Could you explain more about that?",
                        "That's really interesting!",
                        "Let me think about that and get back to you",
                        "Has anyone else tried this approach?",
                        "Great point! I was thinking the same thing.",
                        "Can we schedule a meeting to discuss this further?",
                        "I have some experience with that if you need help",
                        "That worked really well for our team last month"
                };

                String randomUser = otherUsers[(int)(Math.random() * otherUsers.length)];
                // Don't respond to ourselves
                if (!randomUser.equals(originalUser)) {
                    String response = responses[(int)(Math.random() * responses.length)];
                    messageHandler.accept("CHAT|" + meetingId + "|" + randomUser + "|" + response);
                }

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
    }

    public void disconnect() {
        connected = false;
        if (messageSimulator != null) {
            messageSimulator.interrupt();
        }
        System.out.println("❌ Disconnected from chat server");
        messageHandler.accept("DISCONNECTED: Connection closed");
    }

    public boolean isConnected() {
        return connected;
    }

    public void setCurrentUser(String username) {
        this.currentUser = username;
    }
}