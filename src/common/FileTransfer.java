package common;

import java.io.Serializable;

/**
 * Serialized file transfer over the same Object stream as chat messages.
 * START announces the transfer; CHUNK carries payload; END signals completion.
 */
public class FileTransfer implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Phase { START, CHUNK, END }

    private final Phase phase;
    private final String sender;
    private final String recipient;
    private final String transferId;
    private final String fileName;
    private final long totalSize;
    private final int chunkIndex;
    private final int totalChunks;
    private final byte[] data;
    private final boolean lastChunk;

    public static FileTransfer start(String sender, String recipient, String transferId,
                                     String fileName, long totalSize, int totalChunks) {
        return new FileTransfer(Phase.START, sender, recipient, transferId, fileName,
                totalSize, 0, totalChunks, null, false);
    }

    public static FileTransfer chunk(String sender, String recipient, String transferId,
                                     String fileName, long totalSize, int chunkIndex,
                                     int totalChunks, byte[] data, boolean lastChunk) {
        return new FileTransfer(Phase.CHUNK, sender, recipient, transferId, fileName,
                totalSize, chunkIndex, totalChunks, data, lastChunk);
    }

    public static FileTransfer end(String sender, String recipient, String transferId,
                                   String fileName, long totalSize, int totalChunks) {
        return new FileTransfer(Phase.END, sender, recipient, transferId, fileName,
                totalSize, totalChunks, totalChunks, null, true);
    }

    private FileTransfer(Phase phase, String sender, String recipient, String transferId,
                         String fileName, long totalSize, int chunkIndex, int totalChunks,
                         byte[] data, boolean lastChunk) {
        this.phase = phase;
        this.sender = sender;
        this.recipient = recipient;
        this.transferId = transferId;
        this.fileName = fileName;
        this.totalSize = totalSize;
        this.chunkIndex = chunkIndex;
        this.totalChunks = totalChunks;
        this.data = data;
        this.lastChunk = lastChunk;
    }

    public Phase getPhase() { return phase; }
    public String getSender() { return sender; }
    public String getRecipient() { return recipient; }
    public String getTransferId() { return transferId; }
    public String getFileName() { return fileName; }
    public long getTotalSize() { return totalSize; }
    public int getChunkIndex() { return chunkIndex; }
    public int getTotalChunks() { return totalChunks; }
    public byte[] getData() { return data; }
    public boolean isLastChunk() { return lastChunk; }
}
