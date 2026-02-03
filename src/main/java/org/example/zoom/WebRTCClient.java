package org.example.zoom.webrtc;

import javafx.scene.image.Image;
import javafx.application.Platform;

public class WebRTCClient {

    public interface WebRTCCallbacks {
        void onLocalVideoFrame(Image frame);
        void onRemoteVideoFrame(String peerId, Image frame);
        void onAudioStateChanged(boolean enabled);
        void onVideoStateChanged(boolean enabled);
        void onConnectionStateChanged(String state);
        void onError(String error);
    }

    private final String username;
    private final WebRTCCallbacks callbacks;
    private boolean audioEnabled = true;
    private boolean videoEnabled = false;

    public WebRTCClient(String username, WebRTCCallbacks callbacks) {
        this.username = username;
        this.callbacks = callbacks;
        System.out.println("WebRTCClient initialized for: " + username);
    }

    public void startLocalStream() {
        System.out.println("WebRTC: Starting local stream");
        videoEnabled = true;
        if (callbacks != null) {
            callbacks.onVideoStateChanged(true);
        }
    }

    public void stopLocalStream() {
        System.out.println("WebRTC: Stopping local stream");
        videoEnabled = false;
        if (callbacks != null) {
            callbacks.onVideoStateChanged(false);
        }
    }

    public void toggleAudio(boolean enabled) {
        audioEnabled = enabled;
        System.out.println("WebRTC: Audio " + (enabled ? "enabled" : "disabled"));
        if (callbacks != null) {
            callbacks.onAudioStateChanged(enabled);
        }
    }

    public void handleSignalingMessage(String fromPeer, String sdpType, String sdp) {
        System.out.println("WebRTC: Received signaling from " + fromPeer + ", type: " + sdpType);
        // Handle signaling message - you'll need to implement actual WebRTC logic here
    }

    public void dispose() {
        System.out.println("WebRTCClient disposed");
        stopLocalStream();
        audioEnabled = false;
    }
}