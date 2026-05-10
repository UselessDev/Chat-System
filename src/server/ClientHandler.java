package server;

import common.FileTransfer;
import common.Message;
import common.User;

import java.io.*;
import java.net.Socket;
import java.util.List;

/**
 * One instance per connected client.
 * Runs in its own thread. Handles:
 * - Username registration
 * - Reading incoming messages
 * - Sending messages/objects to client
 */
public class ClientHandler implements Runnable {
    private Socket socket;
    private ChatServer server;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String username;
    private boolean running = true;
    
    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }
    
    @Override
    public void run() {
        try {
            // Setup streams (output first to avoid deadlock)
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            
            // Step 1: Get username from client
            if (!registerUsername()) {
                close(); // Failed to get valid username
                return;
            }
            
            // Step 2: Main loop - read and process messages
            while (running) {
                Object received = in.readObject();
                
                if (received instanceof Message) {
                    Message msg = (Message) received;
                    handleMessage(msg);
                } else if (received instanceof FileTransfer) {
                    handleFileTransfer((FileTransfer) received);
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            System.out.println(username + " disconnected unexpectedly");
        } finally {
            cleanup();
        }
    }
    
    // Username registration with validation
    private boolean registerUsername() throws IOException, ClassNotFoundException {
        // Client sends username immediately after connecting.
        Object response = in.readObject();
        if (!(response instanceof String)) {
            out.writeObject(Message.system("ERROR: Invalid username payload"));
            out.flush();
            return false;
        }

        String proposed = ((String) response).trim();

        // Validation
        if (proposed.isEmpty()) {
            out.writeObject(Message.system("ERROR: Username cannot be empty"));
            out.flush();
            return false;
        }
        if (proposed.length() > 20) {
            out.writeObject(Message.system("ERROR: Username too long (max 20)"));
            out.flush();
            return false;
        }
        if (server.isUsernameTaken(proposed)) {
            out.writeObject(Message.system("ERROR: Username already taken"));
            out.flush();
            return false;
        }

        this.username = proposed;
        out.writeObject(Message.system("SUCCESS")); // Confirm to client
        out.flush();
        server.addClient(username, this, Thread.currentThread().getName());
        return true;
    }
    
    // Route message based on type
    private void handleMessage(Message msg) {
        switch (msg.getType()) {
            case BROADCAST:
                server.broadcast(msg);
                // Echo back to sender as a server message ("Server: <text>")
                sendMessage(new Message("Server", username, msg.getContent(), Message.Type.BROADCAST));
                break;
            case PRIVATE:
                server.sendPrivate(msg.getRecipient(), msg);
                break;
            default:
                System.out.println("Unknown message type");
        }
    }

    private void handleFileTransfer(FileTransfer ft) {
        if (ft.getRecipient() == null || ft.getRecipient().trim().isEmpty()) {
            sendMessage(Message.system("File transfer requires a recipient (use a DM tab)."));
            return;
        }
        if (!username.equals(ft.getSender())) {
            sendMessage(Message.system("Invalid file transfer sender."));
            return;
        }
        server.sendFilePrivate(ft.getRecipient().trim(), ft);
    }
    
    // Send message object to this client
    public void sendMessage(Message msg) {
        try {
            out.writeObject(msg);
            out.flush();
        } catch (IOException e) {
            System.out.println("Failed to send message to " + username);
        }
    }

    public void sendObject(Object obj) {
        try {
            out.writeObject(obj);
            out.flush();
        } catch (IOException e) {
            System.out.println("Failed to send object to " + username);
        }
    }
    
    // Send user list to this client
    public void sendUserList(List<User> users) {
        try {
            out.writeObject(users);
            out.flush();
        } catch (IOException e) {
            System.out.println("Failed to send user list to " + username);
        }
    }
    
    private void cleanup() {
        if (username != null) {
            server.removeClient(username);
        }
        close();
    }
    
    private void close() {
        running = false;
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            // Ignore cleanup errors
        }
    }
}