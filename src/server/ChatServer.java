package server;

import common.Message;
import common.User;

import common.FileTransfer;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central chat server.
 * - Accepts multiple client connections
 * - Manages user list
 * - Routes messages (broadcast or private)
 * - Runs on port 5000 by default
 */
public class ChatServer {
    private static final int PORT = 5000;
    
    // Thread-safe collections for managing clients
    private final Map<String, ClientHandler> clients = new ConcurrentHashMap<>();
    private final Set<User> userList = ConcurrentHashMap.newKeySet();
    private final ExecutorService threadPool = Executors.newCachedThreadPool();
    /** Maps username to the worker thread name handling that client */
    private final Map<String, String> userThreadNames = new ConcurrentHashMap<>();
    private final List<Runnable> serverStateListeners = new CopyOnWriteArrayList<>();
    
    public static void main(String[] args) {
        new ChatServer().start();
    }
    
    public void start() {
        System.out.println("=== Chat Server Starting ===");
        System.out.println("Port: " + PORT);

        if (!java.awt.GraphicsEnvironment.isHeadless()) {
            javax.swing.SwingUtilities.invokeLater(() -> new AdminMonitor(this));
        }
        
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("Server ready. Waiting for clients...\n");
            
            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("New connection from: " + clientSocket.getInetAddress());
                
                // Handle each client in separate thread
                ClientHandler handler = new ClientHandler(clientSocket, this);
                threadPool.execute(handler);
            }
        } catch (IOException e) {
            System.err.println("Server error: " + e.getMessage());
        }
    }
    
    // Called by ClientHandler after successful username registration
    public synchronized void addClient(String username, ClientHandler handler, String workerThreadName) {
        clients.put(username, handler);
        userList.add(new User(username));
        userThreadNames.put(username, workerThreadName);
        notifyServerStateListeners();
        
        // Notify everyone that user joined
        broadcast(Message.system(username + " joined the chat"));
        broadcastUserList(); // Send updated user list to all
    }
    
    // Remove client on disconnect
    public synchronized void removeClient(String username) {
        clients.remove(username);
        userList.removeIf(u -> u.getUsername().equals(username));
        userThreadNames.remove(username);
        notifyServerStateListeners();
        
        broadcast(Message.system(username + " left the chat"));
        broadcastUserList();
    }
    
    // Send message to all connected clients
    public void broadcast(Message message) {
        for (ClientHandler client : clients.values()) {
            client.sendMessage(message);
        }
    }
    
    // Send private message to specific user
    public void sendPrivate(String toUsername, Message message) {
        ClientHandler target = clients.get(toUsername);
        ClientHandler sender = clients.get(message.getSender());
        
        if (target != null) {
            target.sendMessage(message); // Deliver to recipient
            if (sender != null && !sender.equals(target)) {
                sender.sendMessage(message); // Echo to sender
            }
        } else {
            // Notify sender that user doesn't exist
            if (sender != null) {
                sender.sendMessage(Message.system("User '" + toUsername + "' not found"));
            }
        }
    }
    
    // Send updated user list to all clients
    public void broadcastUserList() {
        List<User> users = new ArrayList<>(userList);
        for (ClientHandler client : clients.values()) {
            client.sendUserList(users);
        }
    }
    
    // Check if username is taken
    public boolean isUsernameTaken(String username) {
        return clients.containsKey(username);
    }

    /** Deliver a file transfer packet only to the named recipient */
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
