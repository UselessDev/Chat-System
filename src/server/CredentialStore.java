package server;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Persists username → SHA-256(password) for demo login. Production apps should use bcrypt + salt.
 */
public final class CredentialStore {

    private final Path file;
    private final Map<String, String> users = new ConcurrentHashMap<>();

    public CredentialStore() {
        file = Paths.get(System.getProperty("user.home"), ".introg_chat", "users_credentials.txt");
        load();
    }

    private synchronized void load() {
        users.clear();
        try {
            if (!Files.isRegularFile(file)) {
                Files.createDirectories(file.getParent());
                return;
            }
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int i = line.indexOf(':');
                if (i <= 0) continue;
                String u = line.substring(0, i).trim().toLowerCase();
                String h = line.substring(i + 1).trim();
                if (!u.isEmpty() && !h.isEmpty()) {
                    users.put(u, h);
                }
            }
        } catch (Exception ignored) {
        }
    }

    private synchronized void save() {
        try {
            Files.createDirectories(file.getParent());
            StringBuilder sb = new StringBuilder();
            sb.append("# username:sha256hex (demo store — use stronger crypto in production)\n");
            for (Map.Entry<String, String> e : users.entrySet()) {
                sb.append(e.getKey()).append(':').append(e.getValue()).append('\n');
            }
            Files.writeString(file, sb.toString(), StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (Exception ignored) {
        }
    }

    public boolean accountExists(String username) {
        return users.containsKey(username.toLowerCase());
    }

    public boolean verify(String username, String password) {
        String expected = users.get(username.toLowerCase());
        if (expected == null) return false;
        return expected.equalsIgnoreCase(hash(password));
    }

    public boolean register(String username, String password) {
        String key = username.toLowerCase();
        if (users.containsKey(key)) return false;
        users.put(key, hash(password));
        save();
        return true;
    }

    private static String hash(String password) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(password.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
