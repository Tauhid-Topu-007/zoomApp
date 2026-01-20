package org.example.zoom;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import javafx.application.Platform;

public class FileTransferHandler {
    private MeetingController meetingController;
    private Map<String, FileTransfer> ongoingTransfers = new ConcurrentHashMap<>();
    private Map<String, FileTransfer> incomingTransfers = new ConcurrentHashMap<>();

    // Transfer chunk size (64KB)
    private static final int CHUNK_SIZE = 64 * 1024;

    public FileTransferHandler(MeetingController controller) {
        this.meetingController = controller;
    }

    /**
     * Start a file transfer to all participants
     */
    public boolean startFileTransfer(File file, String meetingId, String username) {
        try {
            String fileId = UUID.randomUUID().toString();
            long fileSize = file.length();

            // Create transfer record
            FileTransfer transfer = new FileTransfer(fileId, file.getName(), fileSize, file, username);
            ongoingTransfers.put(fileId, transfer);

            // Send transfer request to all participants
            String requestMessage = "REQUEST|" + fileId + "|" + file.getName() + "|" + fileSize;

            if (HelloApplication.isWebSocketConnected()) {
                HelloApplication.sendWebSocketMessage(
                        "FILE_TRANSFER",
                        meetingId,
                        username,
                        requestMessage
                );
            }

            // Start sending file chunks
            new Thread(() -> sendFileChunks(fileId, file, meetingId, username)).start();

            return true;

        } catch (Exception e) {
            System.err.println("❌ Error starting file transfer: " + e.getMessage());
            return false;
        }
    }

    /**
     * Send file chunks to recipients
     */
    private void sendFileChunks(String fileId, File file, String meetingId, String username) {
        try {
            byte[] buffer = new byte[CHUNK_SIZE];
            try (FileInputStream fis = new FileInputStream(file)) {
                int bytesRead;
                int chunkIndex = 0;
                long totalFileSize = file.length();

                while ((bytesRead = fis.read(buffer)) != -1) {
                    byte[] chunk = Arrays.copyOfRange(buffer, 0, bytesRead);
                    String chunkBase64 = Base64.getEncoder().encodeToString(chunk);

                    // Send chunk
                    String chunkMessage = "DATA|" + fileId + "|" + file.getName() + "|" + chunkBase64;

                    if (HelloApplication.isWebSocketConnected()) {
                        HelloApplication.sendWebSocketMessage(
                                "FILE_TRANSFER",
                                meetingId,
                                username,
                                chunkMessage
                        );
                    }

                    chunkIndex++;

                    // Update progress - FIXED: Proper type casting
                    FileTransfer transfer = ongoingTransfers.get(fileId);
                    if (transfer != null) {
                        // Calculate bytes sent so far
                        long bytesSent = (long) chunkIndex * CHUNK_SIZE;
                        // Calculate progress percentage
                        int progress = (int) ((bytesSent * 100L) / totalFileSize);
                        transfer.setProgress(progress);

                        // Show progress every 10%
                        if (progress % 10 == 0 && progress > 0) {
                            Platform.runLater(() -> {
                                meetingController.addSystemMessage(
                                        "📤 Sending " + file.getName() + ": " + progress + "%"
                                );
                            });
                        }
                    }

                    // Small delay to avoid overwhelming the network
                    Thread.sleep(10);
                }

                // Send completion message
                String completeMessage = "COMPLETE|" + fileId + "|" + file.getName() + "|";

                if (HelloApplication.isWebSocketConnected()) {
                    HelloApplication.sendWebSocketMessage(
                            "FILE_TRANSFER",
                            meetingId,
                            username,
                            completeMessage
                    );
                }

                Platform.runLater(() -> {
                    meetingController.addSystemMessage("✅ File sent successfully: " + file.getName());
                });

                // Clean up
                ongoingTransfers.remove(fileId);

            }
        } catch (Exception e) {
            System.err.println("❌ Error sending file chunks: " + e.getMessage());

            // Send error message
            String errorMessage = "ERROR|" + fileId + "|" + file.getName() + "|" + e.getMessage();

            if (HelloApplication.isWebSocketConnected()) {
                HelloApplication.sendWebSocketMessage(
                        "FILE_TRANSFER",
                        meetingId,
                        username,
                        errorMessage
                );
            }

            ongoingTransfers.remove(fileId);
        }
    }

    /**
     * Accept an incoming file transfer
     */
    public void acceptFileTransfer(String fileId, String sender, String fileName, long fileSize) {
        try {
            // Create directory for received files if it doesn't exist
            File receivedDir = new File("received_files");
            if (!receivedDir.exists()) {
                receivedDir.mkdirs();
            }

            // Create temporary file
            File tempFile = new File(receivedDir, fileId + "_" + fileName);
            FileTransfer transfer = new FileTransfer(fileId, fileName, fileSize, tempFile, sender);
            incomingTransfers.put(fileId, transfer);

            // Send acceptance (in a real implementation)

        } catch (Exception e) {
            System.err.println("❌ Error accepting file transfer: " + e.getMessage());
        }
    }

    /**
     * Reject an incoming file transfer
     */
    public void rejectFileTransfer(String fileId, String sender) {
        // Remove from incoming transfers
        incomingTransfers.remove(fileId);

        // In a real implementation, send rejection message
    }

    /**
     * Receive a file chunk
     */
    public boolean receiveFileChunk(String fileId, String sender, String chunkData) {
        try {
            FileTransfer transfer = incomingTransfers.get(fileId);
            if (transfer == null) {
                return false;
            }

            // Decode chunk
            byte[] chunk = Base64.getDecoder().decode(chunkData);

            // Append to file
            try (FileOutputStream fos = new FileOutputStream(transfer.getFile(), true)) {
                fos.write(chunk);
                transfer.addReceivedBytes(chunk.length);
            }

            return true;

        } catch (Exception e) {
            System.err.println("❌ Error receiving file chunk: " + e.getMessage());
            return false;
        }
    }

    /**
     * Complete a file transfer
     */
    public File completeFileTransfer(String fileId) {
        FileTransfer transfer = incomingTransfers.remove(fileId);
        if (transfer != null && transfer.getFile().exists()) {
            // Rename file to original name
            File finalFile = new File(transfer.getFile().getParent(), transfer.getFileName());
            if (transfer.getFile().renameTo(finalFile)) {
                return finalFile;
            } else {
                return transfer.getFile();
            }
        }
        return null;
    }

    /**
     * Cancel a file transfer
     */
    public void cancelFileTransfer(String fileId) {
        FileTransfer transfer = incomingTransfers.remove(fileId);
        if (transfer != null && transfer.getFile().exists()) {
            transfer.getFile().delete();
        }
    }

    /**
     * Get transfer progress
     */
    public int getTransferProgress(String fileId) {
        FileTransfer transfer = ongoingTransfers.get(fileId);
        if (transfer != null) {
            return transfer.getProgress();
        }
        return 0;
    }

    /**
     * Cleanup all transfers
     */
    public void cleanup() {
        // Clean up ongoing transfers
        for (FileTransfer transfer : ongoingTransfers.values()) {
            // Cancel transfers
        }
        ongoingTransfers.clear();

        // Clean up incoming transfers
        for (FileTransfer transfer : incomingTransfers.values()) {
            if (transfer.getFile().exists()) {
                transfer.getFile().delete();
            }
        }
        incomingTransfers.clear();
    }

    /**
     * Inner class to represent a file transfer
     */
    private static class FileTransfer {
        private String fileId;
        private String fileName;
        private long fileSize;
        private File file;
        private String sender;
        private int progress;
        private long receivedBytes;

        public FileTransfer(String fileId, String fileName, long fileSize, File file, String sender) {
            this.fileId = fileId;
            this.fileName = fileName;
            this.fileSize = fileSize;
            this.file = file;
            this.sender = sender;
            this.progress = 0;
            this.receivedBytes = 0;
        }

        public String getFileId() { return fileId; }
        public String getFileName() { return fileName; }
        public long getFileSize() { return fileSize; }
        public File getFile() { return file; }
        public String getSender() { return sender; }
        public int getProgress() { return progress; }

        public void setProgress(int progress) {
            this.progress = Math.min(100, progress);
        }

        public void addReceivedBytes(long bytes) {
            this.receivedBytes += bytes;
            // FIXED: Proper type casting for progress calculation
            this.progress = (int) ((this.receivedBytes * 100L) / fileSize);
        }
    }
}