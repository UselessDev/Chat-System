package common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Server → client: which room this client is in after join/leave/create, plus full room list. */
public class RoomStateEvent implements Serializable {
    private static final long serialVersionUID = 1L;

    private final String activeRoom;
    private final List<String> allRooms;

    public RoomStateEvent(String activeRoom, List<String> allRooms) {
        this.activeRoom = activeRoom == null ? "" : activeRoom;
        this.allRooms = allRooms == null ? new ArrayList<>() : new ArrayList<>(allRooms);
    }

    public String getActiveRoom() { return activeRoom; }
    public List<String> getAllRooms() { return Collections.unmodifiableList(allRooms); }
}
