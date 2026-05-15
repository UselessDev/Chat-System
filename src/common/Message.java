package common;

import java.io.Serializable;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * Message object sent between client and server.
 * Types: BROADCAST, PRIVATE, SYSTEM.
 * {@code roomName} scopes BROADCAST (and optional SYSTEM) to a chat room; null means {@code general}.
 */
public class Message implements Serializable {
    private static final long serialVersionUID = 2L;

    public enum Type { BROADCAST, PRIVATE, SYSTEM }

    private String sender;
    private String recipient;
    private String content;
    private Type type;
    private long timestamp;
    /** Room for broadcast/system routing; null treated as "general" on server. */
    private String roomName;

    public Message(String sender, String recipient, String content, Type type) {
        this(sender, recipient, content, type, null);
    }

    public Message(String sender, String recipient, String content, Type type, String roomName) {
        this.sender = sender;
        this.recipient = recipient;
        this.content = content;
        this.type = type;
        this.timestamp = System.currentTimeMillis();
        this.roomName = roomName;
    }

    public static Message system(String text) {
        return new Message("SERVER", null, text, Type.SYSTEM, null);
    }

    /** System line scoped to one room (only members receive it). */
    public static Message systemInRoom(String text, String room) {
        return new Message("SERVER", null, text, Type.SYSTEM, room);
    }

    public String getSender() { return sender; }
    public String getRecipient() { return recipient; }
    public String getContent() { return content; }
    public Type getType() { return type; }
    public long getTimestamp() { return timestamp; }
    public String getRoomName() { return roomName; }

    public void setRoomName(String roomName) {
        this.roomName = roomName;
    }

    public String getFormattedTime() {
        SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss");
        return sdf.format(new Date(timestamp));
    }

    @Override
    public String toString() {
        String timeStr = "[" + getFormattedTime() + "] ";
        if (type == Type.SYSTEM) return timeStr + "[SYSTEM] " + content;
        if (type == Type.PRIVATE) return timeStr + "[Private] " + sender + " -> " + recipient + ": " + content;
        return timeStr + sender + ": " + content;
    }
}
