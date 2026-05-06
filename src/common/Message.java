package common;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Message object sent between client and server.
 * Contains: sender, recipient, content, message type, and timestamp.
 * 
 * Types:
 * - BROADCAST: sent to all users
 * - PRIVATE: sent to specific user only
 * - SYSTEM: server notifications (join/leave/status)
 */
public class Message implements Serializable {
    private static final long serialVersionUID = 1L;
    
    public enum Type { BROADCAST, PRIVATE, SYSTEM }
    
    private String sender;      // Who sent the message
    private String recipient;   // Target user (null for broadcast)
    private String content;     // Message text
    private Type type;          // Message category
    private long timestamp;     // When message was created (milliseconds)
    
    // Constructor for regular chat messages
    public Message(String sender, String recipient, String content, Type type) {
        this.sender = sender;
        this.recipient = recipient;
        this.content = content;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
    }
    
    // Convenience constructor for system messages
    public static Message system(String text) {
        return new Message("SERVER", null, text, Type.SYSTEM);
    }
    
    // Getters
    public String getSender() { return sender; }
    public String getRecipient() { return recipient; }
    public String getContent() { return content; }
    public Type getType() { return type; }
    public long getTimestamp() { return timestamp; }
    
    // Format timestamp as HH:mm:ss
    public String getFormattedTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        return sdf.format(new Date(timestamp));
    }
    
    // Format: "[HH:mm:ss] Alice: Hello" or "[HH:mm:ss] [Private] Alice -> Bob: Hello"
    @Override
    public String toString() {
        String timeStr = "[" + getFormattedTime() + "] ";
        if (type == Type.SYSTEM) return timeStr + "[SYSTEM] " + content;
        if (type == Type.PRIVATE) return timeStr + "[Private] " + sender + " -> " + recipient + ": " + content;
        return timeStr + sender + ": " + content;
    }
}
