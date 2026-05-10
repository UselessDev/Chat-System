package client;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

/**
 * Append-only JSONL chat history per local username.
 */
public final class ChatHistoryStore {

    private ChatHistoryStore() {}

    private static Path historyFile(String localUsername) {
        String safe = localUsername.replaceAll("[^a-zA-Z0-9_-]", "_");
        Path dir = Paths.get(System.getProperty("user.home"), ".introg_chat");
        try {
            Files.createDirectories(dir);
        } catch (IOException ignored) {
        }
        return dir.resolve(safe + "_history.jsonl");
    }

    public static void append(String localUsername, HistoryLine line) {
        if (localUsername == null || localUsername.isEmpty()) return;
        Path p = historyFile(localUsername);
        String json = line.toJsonLine();
        try {
            Files.writeString(p, json + System.lineSeparator(),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException ignored) {
        }
    }

    public static List<HistoryLine> load(String localUsername) {
        List<HistoryLine> out = new ArrayList<>();
        Path p = historyFile(localUsername);
        if (!Files.isRegularFile(p)) return out;
        try (BufferedReader br = Files.newBufferedReader(p, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                HistoryLine hl = HistoryLine.fromJsonLine(line);
                if (hl != null) out.add(hl);
            }
        } catch (IOException ignored) {
        }
        return out;
    }

    public static class HistoryLine {
        public final long timestamp;
        public final String tab;
        public final String sender;
        public final String recipient;
        public final String type;
        public final String content;

        public HistoryLine(long timestamp, String tab, String sender, String recipient, String type, String content) {
            this.timestamp = timestamp;
            this.tab = tab;
            this.sender = sender;
            this.recipient = recipient;
            this.type = type;
            this.content = content;
        }

        String toJsonLine() {
            return "{"
                    + "\"ts\":" + timestamp + ","
                    + "\"tab\":" + jsonEscape(tab) + ","
                    + "\"sender\":" + jsonEscape(sender) + ","
                    + "\"recipient\":" + jsonEscape(recipient) + ","
                    + "\"type\":" + jsonEscape(type) + ","
                    + "\"content\":" + jsonEscape(content)
                    + "}";
        }

        static HistoryLine fromJsonLine(String json) {
            try {
                Long ts = extractLong(json, "ts");
                String tab = extractString(json, "tab");
                String sender = extractString(json, "sender");
                String recipient = extractString(json, "recipient");
                String type = extractString(json, "type");
                String content = extractString(json, "content");
                if (ts == null || tab == null) return null;
                return new HistoryLine(ts, tab, sender == null ? "" : sender,
                        recipient == null ? "" : recipient,
                        type == null ? "" : type, content == null ? "" : content);
            } catch (Exception e) {
                return null;
            }
        }

        private static String jsonEscape(String s) {
            if (s == null) return "null";
            StringBuilder sb = new StringBuilder("\"");
            for (int i = 0; i < s.length(); i++) {
                char c = s.charAt(i);
                switch (c) {
                    case '\\': sb.append("\\\\"); break;
                    case '"': sb.append("\\\""); break;
                    case '\n': sb.append("\\n"); break;
                    case '\r': sb.append("\\r"); break;
                    case '\t': sb.append("\\t"); break;
                    default: sb.append(c);
                }
            }
            sb.append('"');
            return sb.toString();
        }

        private static Long extractLong(String json, String key) {
            String pat = "\"" + key + "\":";
            int i = json.indexOf(pat);
            if (i < 0) return null;
            i += pat.length();
            int j = i;
            while (j < json.length() && (Character.isDigit(json.charAt(j)) || json.charAt(j) == '-')) j++;
            return Long.parseLong(json.substring(i, j));
        }

        private static String extractString(String json, String key) {
            String pat = "\"" + key + "\":";
            int i = json.indexOf(pat);
            if (i < 0) return null;
            i += pat.length();
            if (i >= json.length()) return null;
            while (i < json.length() && Character.isWhitespace(json.charAt(i))) i++;
            if (json.startsWith("null", i)) return "";
            if (json.charAt(i) != '"') return null;
            i++;
            StringBuilder sb = new StringBuilder();
            while (i < json.length()) {
                char c = json.charAt(i);
                if (c == '\\' && i + 1 < json.length()) {
                    char n = json.charAt(i + 1);
                    switch (n) {
                        case 'n': sb.append('\n'); i += 2; continue;
                        case 'r': sb.append('\r'); i += 2; continue;
                        case 't': sb.append('\t'); i += 2; continue;
                        case '\\': sb.append('\\'); i += 2; continue;
                        case '"': sb.append('"'); i += 2; continue;
                        default: sb.append(c); i++; continue;
                    }
                }
                if (c == '"') break;
                sb.append(c);
                i++;
            }
            return sb.toString();
        }
    }
}
