package common;

import java.io.Serializable;

/**
 * Message object sent between client and server.
 * Contains: sender, recipient, content, and message type.
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
    
    // Constructor for regular chat messages
    public Message(String sender, String recipient, String content, Type type) {
        this.sender = sender;
        this.recipient = recipient;
        this.content = content;
        this.type = type;
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
    
    // Format: "Alice: Hello" or "[Private] Alice -> Bob: Hello"
    @Override
    public String toString() {
        if (type == Type.SYSTEM) return "[SYSTEM] " + content;
        if (type == Type.PRIVATE) return "[Private] " + sender + " -> " + recipient + ": " + content;
        return sender + ": " + content;
    }
}