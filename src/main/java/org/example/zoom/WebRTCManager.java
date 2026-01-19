package org.example.zoom.webrtc;

import javafx.application.Platform;
import javafx.scene.image.Image;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

public class WebRTCManager {

    private static WebRTCManager instance;
    private final Map<String, RTCPeer> peers = new ConcurrentHashMap<>();
    private boolean webRTCEnabled = false;

    // Callbacks
    private VideoFrameConsumer videoFrameConsumer;
    private StatusConsumer statusConsumer;

    public interface VideoFrameConsumer {
        void onVideoFrameReceived(Image videoFrame);
    }

    public interface StatusConsumer {
        void onStatusUpdate(String status);
    }

    private WebRTCManager() {
        System.out.println("✅ WebRTC Manager initialized");
    }

    public static synchronized WebRTCManager getInstance() {
        if (instance == null) {
            instance = new WebRTCManager();
        }
        return instance;
    }

    public void enableWebRTC() {
        webRTCEnabled = true;
        System.out.println("✅ WebRTC enabled");
    }

    public void disableWebRTC() {
        webRTCEnabled = false;
        System.out.println("🛑 WebRTC disabled");
    }

    public boolean isWebRTCEnabled() {
        return webRTCEnabled;
    }

    public void startWebRTCSession(String meetingId, String userId) {
        if (!webRTCEnabled) {
            System.out.println("⚠️ WebRTC is disabled. Enable it first.");
            return;
        }

        System.out.println("🚀 Starting WebRTC session for meeting: " + meetingId + ", user: " + userId);

        if (meetingId == null || meetingId.isEmpty()) {
            System.err.println("❌ Cannot start WebRTC session: meetingId is null or empty");
            return;
        }

        if (userId == null || userId.isEmpty()) {
            System.err.println("❌ Cannot start WebRTC session: userId is null or empty");
            return;
        }

        // Simulate connecting to peers
        simulatePeerConnections(meetingId);

        if (statusConsumer != null) {
            statusConsumer.onStatusUpdate("WebRTC session started for meeting: " + meetingId);
        }

        System.out.println("✅ WebRTC session started successfully");
    }

    private void simulatePeerConnections(String meetingId) {
        // Simulate connecting to 3 random peers
        Random random = new Random();
        int peerCount = random.nextInt(4) + 1; // 1-4 peers

        for (int i = 0; i < peerCount; i++) {
            String peerId = "Peer-" + (1000 + random.nextInt(9000));
            RTCPeer peer = new RTCPeer(peerId);
            peers.put(peerId, peer);

            // Simulate connection after a delay
            new Thread(() -> {
                try {
                    Thread.sleep(1000 + random.nextInt(2000));
                    peer.connect();

                    // Simulate receiving a video frame
                    if (videoFrameConsumer != null) {
                        Platform.runLater(() -> {
                            // Create a simulated video frame
                            videoFrameConsumer.onVideoFrameReceived(createSimulatedVideoFrame(peerId));
                        });
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            }).start();
        }
    }

    private Image createSimulatedVideoFrame(String peerId) {
        // Create a simple simulated video frame
        javafx.scene.canvas.Canvas canvas = new javafx.scene.canvas.Canvas(320, 240);
        javafx.scene.canvas.GraphicsContext gc = canvas.getGraphicsContext2D();

        // Draw background
        gc.setFill(javafx.scene.paint.Color.LIGHTBLUE);
        gc.fillRect(0, 0, 320, 240);

        // Draw peer ID
        gc.setFill(javafx.scene.paint.Color.BLACK);
        gc.fillText("Peer: " + peerId, 10, 20);

        // Draw timestamp
        String timestamp = new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date());
        gc.fillText("Time: " + timestamp, 10, 40);

        // Draw WebRTC indicator
        gc.setFill(javafx.scene.paint.Color.GREEN);
        gc.fillOval(280, 10, 10, 10);
        gc.setFill(javafx.scene.paint.Color.BLACK);
        gc.fillText("WebRTC", 250, 20);

        return canvas.snapshot(null, null);
    }

    public void handleSignalingMessage(String message) {
        if (!webRTCEnabled) return;

        System.out.println("📨 WebRTC Signaling message received");

        // Simple message handling without JSON parsing
        if (message.contains("WEBRTC_NEW_PEER") || message.contains("new peer")) {
            System.out.println("👤 New WebRTC peer detected");
            if (statusConsumer != null) {
                statusConsumer.onStatusUpdate("New peer joined via WebRTC");
            }
        } else if (message.contains("WEBRTC_PEER_LEFT") || message.contains("peer left")) {
            System.out.println("👤 WebRTC peer left");
            if (statusConsumer != null) {
                statusConsumer.onStatusUpdate("Peer left WebRTC session");
            }
        } else if (message.contains("WEBRTC_OFFER") || message.contains("offer")) {
            System.out.println("📨 Received WebRTC offer");
            if (statusConsumer != null) {
                statusConsumer.onStatusUpdate("Received WebRTC connection offer");
            }
        } else if (message.contains("WEBRTC_ANSWER") || message.contains("answer")) {
            System.out.println("📨 Received WebRTC answer");
            if (statusConsumer != null) {
                statusConsumer.onStatusUpdate("Received WebRTC connection answer");
            }
        } else if (message.contains("ICE") || message.contains("candidate")) {
            System.out.println("📨 Received ICE candidate");
        } else if (message.contains("STUN") || message.contains("TURN")) {
            System.out.println("✅ ICE servers configured");
            if (statusConsumer != null) {
                statusConsumer.onStatusUpdate("STUN/TURN servers configured");
            }
        }
    }

    public void sendVideoFrame(Image image) {
        if (!webRTCEnabled) return;

        System.out.println("📤 Sending video frame via WebRTC");

        // Simulate sending to all peers
        for (RTCPeer peer : peers.values()) {
            if (peer.isConnected()) {
                System.out.println("   → To: " + peer.getPeerId());
            }
        }
    }

    public void displayRemoteVideo(Image image) {
        Platform.runLater(() -> {
            if (videoFrameConsumer != null && image != null) {
                videoFrameConsumer.onVideoFrameReceived(image);
            }
        });
    }

    // Getters and setters
    public void setVideoFrameConsumer(VideoFrameConsumer consumer) {
        this.videoFrameConsumer = consumer;
    }

    public void setStatusConsumer(StatusConsumer consumer) {
        this.statusConsumer = consumer;
    }

    public int getConnectedPeersCount() {
        return (int) peers.values().stream().filter(RTCPeer::isConnected).count();
    }

    public int getTotalPeersCount() {
        return peers.size();
    }

    public void stop() {
        System.out.println("🛑 Stopping WebRTC manager...");

        for (RTCPeer peer : peers.values()) {
            peer.disconnect();
        }
        peers.clear();

        webRTCEnabled = false;

        if (statusConsumer != null) {
            statusConsumer.onStatusUpdate("WebRTC session stopped");
        }

        System.out.println("✅ WebRTC manager stopped");
    }

    // Get STUN/TURN server configuration as a simple string
    public String getIceServersConfig() {
        return """
            STUN Servers:
            - stun:stun.l.google.com:19302
            - stun:stun1.l.google.com:19302
            - stun:stun2.l.google.com:19302
            
            TURN Server (optional):
            - turn:your-server-ip:3478
              Username: zoomuser
              Password: zoompass123
            """;
    }

    // RTCPeer inner class
    private static class RTCPeer {
        private final String peerId;
        private final AtomicBoolean connected = new AtomicBoolean(false);

        public RTCPeer(String peerId) {
            this.peerId = peerId;
        }

        public void connect() {
            if (connected.compareAndSet(false, true)) {
                System.out.println("✅ Connected to peer: " + peerId);
            }
        }

        public void disconnect() {
            if (connected.compareAndSet(true, false)) {
                System.out.println("Disconnected from peer: " + peerId);
            }
        }

        public boolean isConnected() {
            return connected.get();
        }

        public String getPeerId() {
            return peerId;
        }
    }
}