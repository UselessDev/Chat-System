package common;

import java.io.Serializable;

/** Ephemeral typing indicator for a chat room (not persisted). */
public class TypingEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String roomName;
    private final String username;
    private final boolean typing;

    public TypingEvent(String roomName, String username, boolean typing) {
        this.roomName = roomName == null ? "" : roomName;
        this.username = username == null ? "" : username;
        this.typing = typing;
    }

    public String getRoomName() { return roomName; }
    public String getUsername() { return username; }
    public boolean isTyping() { return typing; }
}
