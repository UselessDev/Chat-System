package common;

import java.io.Serializable;

/** Client → server: create, join, leave, or list chat rooms. */
public class RoomCommand implements Serializable {
    private static final long serialVersionUID = 1L;

    public enum Action { CREATE, JOIN, LEAVE, LIST }

    private final Action action;
    private final String roomName;

    public RoomCommand(Action action, String roomName) {
        this.action = action;
        this.roomName = roomName == null ? "" : roomName.trim();
    }

    public Action getAction() { return action; }
    public String getRoomName() { return roomName; }
}
