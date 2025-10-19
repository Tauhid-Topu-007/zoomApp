package org.example.zoom.websocket;

import java.io.*;
import java.net.*;
import java.util.function.Consumer;

public class HttpWebSocketClient {
    private Consumer<String> messageHandler;
    private String serverUrl;
    private boolean connected = false;
    private Thread simulationThread;

    public HttpWebSocketClient(String serverUrl, Consumer<String> messageHandler) {
        this.serverUrl = serverUrl;
        this.messageHandler = messageHandler;
    }

    public void connect() {
        // Simulate WebSocket connection for demo
        connected = true;

        simulationThread = new Thread(() -> {
            try {
                // Simulate connection delay
                Thread.sleep(1000);
                messageHandler.accept("CONNECTED: Connected to chat server (Simulation)");

                // Simulate welcome message
                Thread.sleep(2000);
                messageHandler.accept("SYSTEM|global|Server|Welcome to the chat! Type your messages below.");

                // Simulate periodic messages
                while (connected) {
                    Thread.sleep(15000);
                    if (connected) {
                        messageHandler.accept("SYSTEM|global|Server|Chat is active and working!");
                    }
                }
            } catch (InterruptedException e) {
                // Thread interrupted, exit quietly
            }
        });
        simulationThread.start();
    }

    public void sendMessage(String type, String meetingId, String username, String content) {
        if (connected) {
            // Simulate message sending and receiving
            String message = type + "|" + meetingId + "|" + username + "|" + content;
            System.out.println("Sending message: " + message);

            // Echo the message back (simulate other users in real app)
            messageHandler.accept("CHAT|" + meetingId + "|" + username + "|" + content);

            // Simulate other users responding
            if (Math.random() > 0.7) {
                new Thread(() -> {
                    try {
                        Thread.sleep(2000);
                        messageHandler.accept("CHAT|" + meetingId + "|Other User|I received your message!");
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }).start();
            }
        }
    }

    public void disconnect() {
        connected = false;
        if (simulationThread != null) {
            simulationThread.interrupt();
        }
        messageHandler.accept("DISCONNECTED: Connection closed");
    }

    public boolean isConnected() {
        return connected;
    }
}