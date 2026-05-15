package server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Persists custom room names (excluding {@code general}) under {@code ~/.introg_chat/rooms.txt}.
 * Membership stays in memory; only room keys survive server restarts.
 */
public final class RoomStore {

    private final Path file;

    public RoomStore() {
        file = Paths.get(System.getProperty("user.home"), ".introg_chat", "rooms.txt");
    }

    /** Room names from disk, lowercased, excluding {@link ChatServer#DEFAULT_ROOM}. */
    public synchronized List<String> loadRoomNames() {
        return new ArrayList<>(readKeysFromFile());
    }

    /** Record a newly created room name (idempotent). */
    public synchronized void addPersistentRoom(String roomKey) {
        if (roomKey == null || roomKey.isEmpty()) return;
        String key = roomKey.trim().toLowerCase();
        if (key.isEmpty() || ChatServer.DEFAULT_ROOM.equals(key)) return;
        try {
            Files.createDirectories(file.getParent());
            LinkedHashSet<String> all = readKeysFromFile();
            if (!all.add(key)) return;
            writeAll(all);
        } catch (IOException ignored) {
        }
    }

    private LinkedHashSet<String> readKeysFromFile() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        try {
            if (!Files.isRegularFile(file)) {
                return names;
            }
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String k = line.toLowerCase();
                if (!k.isEmpty() && !ChatServer.DEFAULT_ROOM.equals(k)) {
                    names.add(k);
                }
            }
        } catch (Exception ignored) {
        }
        return names;
    }

    private void writeAll(LinkedHashSet<String> names) throws IOException {
        StringBuilder sb = new StringBuilder();
        sb.append("# Custom chat rooms (lowercase). \"general\" is implicit.\n");
        for (String n : names.stream().sorted().collect(Collectors.toList())) {
            sb.append(n).append('\n');
        }
        Files.writeString(file, sb.toString(), StandardCharsets.UTF_8,
                StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
    }
}
