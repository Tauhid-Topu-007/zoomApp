package org.example.zoom;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.example.zoom.webrtc.WebRTCManager;
import java.util.Arrays;

public class AudioCaptureService {

    private TargetDataLine targetDataLine;
    private boolean isRecording = false;
    private AudioFormat audioFormat;
    private ByteArrayOutputStream audioBuffer;
    private WebRTCManager webRTCManager;
    private boolean webRTCEnabled = false;

    public AudioCaptureService() {
        // Audio format optimized for WebRTC
        audioFormat = new AudioFormat(48000, 16, 1, true, false);
    }

    public boolean startAudioCapture() {
        try {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, audioFormat);

            if (!AudioSystem.isLineSupported(info)) {
                System.out.println("❌ Audio line not supported");
                return false;
            }

            targetDataLine = (TargetDataLine) AudioSystem.getLine(info);
            targetDataLine.open(audioFormat);
            targetDataLine.start();

            isRecording = true;
            audioBuffer = new ByteArrayOutputStream();

            // Start audio capture thread with WebRTC support
            Thread captureThread = new Thread(this::captureAudioWebRTC);
            captureThread.setDaemon(true);
            captureThread.start();

            System.out.println("🎤 Audio capture started with WebRTC support");
            return true;

        } catch (LineUnavailableException e) {
            System.err.println("❌ Audio line unavailable: " + e.getMessage());
            return false;
        }
    }

    private void captureAudioWebRTC() {
        byte[] buffer = new byte[4096];

        while (isRecording && targetDataLine != null) {
            int bytesRead = targetDataLine.read(buffer, 0, buffer.length);
            if (bytesRead > 0) {
                // Store in buffer
                audioBuffer.write(buffer, 0, bytesRead);

                // Send via WebRTC if enabled
                if (webRTCEnabled && webRTCManager != null) {
                    byte[] audioData = Arrays.copyOf(buffer, bytesRead);
                    // In real implementation, encode and send via WebRTC
                    System.out.println("📤 Sending audio via WebRTC: " + audioData.length + " bytes");
                }
            }
        }
    }

    // Enable WebRTC for audio streaming
    public void enableWebRTC(WebRTCManager manager) {
        this.webRTCManager = manager;
        webRTCEnabled = true;
        System.out.println("✅ WebRTC enabled for audio streaming");
    }

    public void disableWebRTC() {
        webRTCEnabled = false;
        System.out.println("🛑 WebRTC disabled for audio streaming");
    }

    public boolean isWebRTCEnabled() {
        return webRTCEnabled && webRTCManager != null;
    }

    public void stopAudioCapture() {
        isRecording = false;
        if (targetDataLine != null) {
            targetDataLine.stop();
            targetDataLine.close();
            targetDataLine = null;
        }
        System.out.println("🎤 Audio capture stopped");
    }

    public List<Mixer.Info> getAvailableMicrophones() {
        List<Mixer.Info> microphones = new ArrayList<>();
        Mixer.Info[] mixerInfos = AudioSystem.getMixerInfo();

        for (Mixer.Info info : mixerInfos) {
            Mixer mixer = AudioSystem.getMixer(info);
            Line.Info[] sourceLines = mixer.getSourceLineInfo();
            Line.Info[] targetLines = mixer.getTargetLineInfo();

            for (Line.Info lineInfo : targetLines) {
                if (lineInfo.getLineClass().equals(TargetDataLine.class)) {
                    microphones.add(info);
                    break;
                }
            }
        }
        return microphones;
    }

    public boolean isAudioAvailable() {
        return getAvailableMicrophones().size() > 0;
    }

    public byte[] getAudioData() {
        return audioBuffer != null ? audioBuffer.toByteArray() : new byte[0];
    }

    public void clearAudioBuffer() {
        if (audioBuffer != null) {
            audioBuffer.reset();
        }
    }
}