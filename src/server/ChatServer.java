package server;

import common.Message;
import common.User;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.*;

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
    
    public static void main(String[] args) {
        new ChatServer().start();
    }
    
    public void start() {
        System.out.println("=== Chat Server Starting ===");
        System.out.println("Port: " + PORT);
        
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
    public synchronized void addClient(String username, ClientHandler handler) {
        clients.put(username, handler);
        userList.add(new User(username));
        
        // Notify everyone that user joined
        broadcast(Message.system(username + " joined the chat"));
        broadcastUserList(); // Send updated user list to all
    }
    
    // Remove client on disconnect
    public synchronized void removeClient(String username) {
        clients.remove(username);
        userList.removeIf(u -> u.getUsername().equals(username));
        
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
}