package server;

import common.FileTransfer;
import common.Message;
import common.RoomCommand;
import common.RoomListUpdate;
import common.RoomStateEvent;
import common.TypingEvent;
import common.User;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Chat server: auth, rooms, broadcast/private, file relay, typing indicators.
 */
public class ChatServer {
    public static final String DEFAULT_ROOM = "general";
    private static final int PORT = 5000;

    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final Set<User> userList = ConcurrentHashMap.newKeySet();
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    private final Map<String, String> userThreadNames = new ConcurrentHashMap<>();
    private final List<Runnable> serverStateListeners = new CopyOnWriteArrayList<>();

    /** room name (lowercase key) → members */
    private final Map<String, Set<String>> roomMembers = new ConcurrentHashMap<>();
    private final CredentialStore credentials = new CredentialStore();
    private final RoomStore roomStore = new RoomStore();

    public static void main(String[] args) {
        new ChatServer().start();
    }

    public void start() {
        System.out.println("=== Chat Server Starting ===");
        System.out.println("Port: " + PORT);
        roomMembers.put(DEFAULT_ROOM, ConcurrentHashMap.newKeySet());
        for (String persisted : roomStore.loadRoomNames()) {
            roomMembers.putIfAbsent(persisted, ConcurrentHashMap.newKeySet());
        }
        System.out.println("Persisted rooms loaded: " + (roomMembers.size() - 1) + " custom room(s).");

        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            javax.swing.SwingUtilities.invokeLater(() -> new AdminMonitor(this));
        }

        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server ready. Waiting for clients...\n");
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New connection from: " + clientSocket.getInetAddress());
                ClientHandler handler = new ClientHandler(clientSocket, this);
                threadPool.execute(handler);
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }

    public synchronized void addClient(String username, ClientHandler handler, String workerThreadName) {
        clients.put(username, handler);
        userList.add(new User(username));
        userThreadNames.put(username, workerThreadName);
        handler.setCurrentRoom(DEFAULT_ROOM);
        roomMembers.computeIfAbsent(DEFAULT_ROOM, k -> ConcurrentHashMap.newKeySet()).add(username);
        notifyServerStateListeners();

        broadcastToRoom(DEFAULT_ROOM, Message.systemInRoom(username + " joined #" + DEFAULT_ROOM, DEFAULT_ROOM));
        broadcastUserList();
        broadcastRoomListToAll();
        sendRoomState(handler);
    }

    public synchronized void removeClient(String username) {
        ClientHandler h = clients.remove(username);
        userList.removeIf(u -> u.getUsername().equals(username));
        userThreadNames.remove(username);
        if (h != null) {
            removeUserFromAllRooms(username);
        }
        notifyServerStateListeners();

        broadcast(Message.system(username + " left the chat"));
        broadcastUserList();
        broadcastRoomListToAll();
    }

    private void removeUserFromAllRooms(String username) {
        for (Map.Entry<String, Set<String>> e : roomMembers.entrySet()) {
            e.getValue().remove(username);
        }
    }

    /** Resolve room for broadcast messages. */
    private static String effectiveRoom(Message msg) {
        if (msg.getType() != Message.Type.BROADCAST) return DEFAULT_ROOM;
        String r = msg.getRoomName();
        return (r == null || r.isEmpty()) ? DEFAULT_ROOM : r.trim().toLowerCase();
    }

    public void broadcast(Message message) {
        if (message.getType() == Message.Type.SYSTEM && message.getRoomName() == null) {
            for (ClientHandler c : clients.values()) {
                c.sendMessage(message);
            }
            return;
        }
        if (message.getType() == Message.Type.SYSTEM) {
            broadcastToRoom(message.getRoomName().trim().toLowerCase(), message);
            return;
        }
        if (message.getType() == Message.Type.BROADCAST) {
            String room = effectiveRoom(message);
            broadcastToRoom(room, message);
            return;
        }
        for (ClientHandler c : clients.values()) {
            c.sendMessage(message);
        }
    }

    public void broadcastToRoom(String roomKey, Message message) {
        if (DEFAULT_ROOM.equals(roomKey)) {
            for (ClientHandler c : clients.values()) {
                c.sendMessage(message);
            }
            return;
        }
        Set<String> members = roomMembers.get(roomKey);
        if (members == null || members.isEmpty()) return;
        for (String uname : members) {
            ClientHandler c = clients.get(uname);
            if (c != null) {
                c.sendMessage(message);
            }
        }
    }

    public void sendPrivate(String toUsername, Message message) {
        ClientHandler target = clients.get(toUsername);
        ClientHandler sender = clients.get(message.getSender());
        if (target != null) {
            target.sendMessage(message);
            if (sender != null && !sender.equals(target)) {
                sender.sendMessage(message);
            }
        } else {
            if (sender != null) {
                sender.sendMessage(Message.system("User '" + toUsername + "' not found"));
            }
        }
    }

    public void broadcastUserList() {
        List<User> users = new ArrayList<>(userList);
        for (ClientHandler client : clients.values()) {
            client.sendUserList(users);
        }
    }

    public boolean isUsernameTaken(String username) {
        return clients.containsKey(username);
    }

    public void sendFilePrivate(String toUsername, FileTransfer ft) {
        ClientHandler target = clients.get(toUsername);
        if (target != null) {
            target.sendObject(ft);
        } else {
            ClientHandler sender = clients.get(ft.getSender());
            if (sender != null) {
                sender.sendMessage(Message.system("Cannot deliver file: user '" + toUsername + "' is offline"));
            }
        }
    }

    public void relayTyping(TypingEvent event, String exceptUser) {
        String room = event.getRoomName() == null ? "" : event.getRoomName().trim().toLowerCase();
        if (room.isEmpty()) return;
        if (DEFAULT_ROOM.equals(room)) {
            for (Map.Entry<String, ClientHandler> e : clients.entrySet()) {
                if (e.getKey().equals(exceptUser)) continue;
                e.getValue().sendObject(event);
            }
            return;
        }
        Set<String> members = roomMembers.get(room);
        if (members == null) return;
        for (String uname : members) {
            if (uname.equals(exceptUser)) continue;
            ClientHandler c = clients.get(uname);
            if (c != null) {
                c.sendObject(event);
            }
        }
    }

    public boolean authenticate(String username, String password, boolean register) {
        if (username == null || username.trim().isEmpty()) return false;
        if (password == null) return false;
        String u = username.trim();
        if (u.length() > 32) return false;
        if (register) {
            return credentials.register(u, password);
        }
        if (!credentials.accountExists(u)) return false;
        return credentials.verify(u, password);
    }

    public void handleRoomCommand(ClientHandler handler, RoomCommand cmd) {
        String me = handler.getUsername();
        if (me == null) return;

        switch (cmd.getAction()) {
            case LIST:
                handler.sendObject(new RoomListUpdate(new ArrayList<>(roomMembers.keySet())));
                sendRoomState(handler);
                break;
            case CREATE: {
                String name = sanitizeRoomName(cmd.getRoomName());
                if (name.isEmpty() || name.equals(DEFAULT_ROOM)) {
                    handler.sendMessage(Message.system("Invalid room name."));
                    return;
                }
                Set<String> previous = roomMembers.putIfAbsent(name, ConcurrentHashMap.newKeySet());
                if (previous == null) {
                    roomStore.addPersistentRoom(name);
                }
                joinRoom(handler, name);
                broadcastRoomListToAll();
                broadcastToRoom(name, Message.systemInRoom(me + " created #" + name, name));
                break;
            }
            case JOIN: {
                String name = sanitizeRoomName(cmd.getRoomName());
                if (name.isEmpty() || !roomMembers.containsKey(name)) {
                    handler.sendMessage(Message.system("Room does not exist: " + cmd.getRoomName()));
                    return;
                }
                joinRoom(handler, name);
                broadcastRoomListToAll();
                broadcastToRoom(name, Message.systemInRoom(me + " joined #" + name, name));
                break;
            }
            case LEAVE:
                joinRoom(handler, DEFAULT_ROOM);
                broadcastRoomListToAll();
                handler.sendMessage(Message.system("You returned to #" + DEFAULT_ROOM));
                break;
            default:
                break;
        }
    }

    private void joinRoom(ClientHandler handler, String newRoom) {
        String me = handler.getUsername();
        String old = handler.getCurrentRoom();
        if (old != null && !old.equals(newRoom)) {
            if (!DEFAULT_ROOM.equals(old)) {
                Set<String> oldSet = roomMembers.get(old);
                if (oldSet != null) oldSet.remove(me);
            }
        }
        roomMembers.computeIfAbsent(newRoom, k -> ConcurrentHashMap.newKeySet()).add(me);
        roomMembers.computeIfAbsent(DEFAULT_ROOM, k -> ConcurrentHashMap.newKeySet()).add(me);
        handler.setCurrentRoom(newRoom);
        sendRoomState(handler);
    }

    private void sendRoomState(ClientHandler handler) {
        handler.sendObject(new RoomStateEvent(handler.getCurrentRoom(), new ArrayList<>(roomMembers.keySet())));
    }

    public void broadcastRoomListToAll() {
        RoomListUpdate upd = new RoomListUpdate(new ArrayList<>(roomMembers.keySet()));
        for (ClientHandler c : clients.values()) {
            c.sendObject(upd);
        }
    }

    private static String sanitizeRoomName(String raw) {
        if (raw == null) return "";
        String s = raw.trim().toLowerCase().replaceAll("[^a-z0-9_-]", "");
        if (s.length() > 40) s = s.substring(0, 40);
        return s;
    }

    public List<String> getConnectedUsernames() {
        return new ArrayList<>(clients.keySet());
    }

    public Map<String, String> getUserThreadDisplayNames() {
        return new HashMap<>(userThreadNames);
    }

    public int getActivePoolThreads() {
        if (threadPool instanceof ThreadPoolExecutor) {
            return ((ThreadPoolExecutor) threadPool).getActiveCount();
        }
        return -1;
    }

    public void addServerStateListener(Runnable listener) {
        serverStateListeners.add(listener);
    }

    public void removeServerStateListener(Runnable listener) {
        serverStateListeners.remove(listener);
    }

    private void notifyServerStateListeners() {
        for (Runnable r : serverStateListeners) {
            try {
                r.run();
            } catch (Exception ignored) {
            }
        }
    }
}
