package common;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Server → client: names of all rooms (for UI list). */
public class RoomListUpdate implements Serializable {
    private static final long serialVersionUID = 1L;

    private final List<String> roomNames;

    public RoomListUpdate(List<String> roomNames) {
        this.roomNames = roomNames == null ? new ArrayList<>() : new ArrayList<>(roomNames);
    }

    public List<String> getRoomNames() {
        return Collections.unmodifiableList(roomNames);
    }
}
