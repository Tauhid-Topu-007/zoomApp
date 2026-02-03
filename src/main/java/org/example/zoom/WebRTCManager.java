package org.example.zoom.webrtc;

import javafx.scene.image.Image;
import java.util.function.Consumer;
import java.util.HashMap;
import java.util.Map;

public class WebRTCManager {

    private static WebRTCManager instance;
    private Map<String, WebRTCClient> clients = new HashMap<>();
    private Consumer<String> statusConsumer;
    private Consumer<Image> videoFrameConsumer;
    private boolean webRTCEnabled = false;

    private WebRTCManager() {
        System.out.println("WebRTCManager initialized");
    }

    public static synchronized WebRTCManager getInstance() {
        if (instance == null) {
            instance = new WebRTCManager();
        }
        return instance;
    }

    public void enableWebRTC() {
        webRTCEnabled = true;
        System.out.println("WebRTC enabled");

        if (statusConsumer != null) {
            statusConsumer.accept("WebRTC enabled");
        }
    }

    public void disableWebRTC() {
        webRTCEnabled = false;
        System.out.println("WebRTC disabled");

        // Dispose all clients
        for (WebRTCClient client : clients.values()) {
            client.dispose();
        }
        clients.clear();

        if (statusConsumer != null) {
            statusConsumer.accept("WebRTC disabled");
        }
    }

    public void startWebRTCSession(String meetingId, String username) {
        if (!webRTCEnabled) {
            System.out.println("WebRTC is not enabled, cannot start session");
            return;
        }

        System.out.println("Starting WebRTC session for meeting: " + meetingId + ", user: " + username);

        if (statusConsumer != null) {
            statusConsumer.accept("Starting WebRTC session for " + username);
        }
    }

    public void stop() {
        System.out.println("Stopping WebRTC manager");
        disableWebRTC();
    }

    public void setStatusConsumer(Consumer<String> statusConsumer) {
        this.statusConsumer = statusConsumer;
    }

    public void setVideoFrameConsumer(Consumer<Image> videoFrameConsumer) {
        this.videoFrameConsumer = videoFrameConsumer;
    }

    public boolean isWebRTCEnabled() {
        return webRTCEnabled;
    }

    public WebRTCClient createClient(String username, WebRTCClient.WebRTCCallbacks callbacks) {
        WebRTCClient client = new WebRTCClient(username, callbacks);
        clients.put(username, client);

        System.out.println("Created WebRTC client for: " + username);

        if (statusConsumer != null) {
            statusConsumer.accept("Created WebRTC client for " + username);
        }

        return client;
    }

    public WebRTCClient getClient(String username) {
        return clients.get(username);
    }

    public void removeClient(String username) {
        WebRTCClient client = clients.remove(username);
        if (client != null) {
            client.dispose();
            System.out.println("Removed WebRTC client for: " + username);
        }
    }
}