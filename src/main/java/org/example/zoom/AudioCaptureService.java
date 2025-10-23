package org.example.zoom;

import javax.sound.sampled.*;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;

public class AudioCaptureService {

    private TargetDataLine targetDataLine;
    private boolean isRecording = false;
    private AudioFormat audioFormat;
    private ByteArrayOutputStream audioBuffer;

    public AudioCaptureService() {
        // Audio format: 16kHz, 16-bit, mono, signed, little-endian
        audioFormat = new AudioFormat(16000, 16, 1, true, false);
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

            // Start audio capture thread
            Thread captureThread = new Thread(this::captureAudio);
            captureThread.setDaemon(true);
            captureThread.start();

            System.out.println("🎤 Audio capture started");
            return true;

        } catch (LineUnavailableException e) {
            System.err.println("❌ Audio line unavailable: " + e.getMessage());
            return false;
        }
    }

    private void captureAudio() {
        byte[] buffer = new byte[4096];

        while (isRecording && targetDataLine != null) {
            int bytesRead = targetDataLine.read(buffer, 0, buffer.length);
            if (bytesRead > 0) {
                // Process audio data here
                // In a real implementation, you'd send this over WebSocket
                audioBuffer.write(buffer, 0, bytesRead);
            }
        }
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

            // Look for microphones (target data lines)
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