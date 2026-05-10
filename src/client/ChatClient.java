package client;

import common.FileTransfer;
import common.Message;
import common.User;

import java.io.*;
import java.net.Socket;
import java.util.List;
import java.util.function.Consumer;

/**
 * Handles network connection for the GUI.
 * Runs on separate thread to not freeze the UI.
 * 
 * Usage:
 *   ChatClient client = new ChatClient("localhost", 5000);
 *   client.setMessageListener(msg -> gui.appendMessage(msg));
 *   client.connect("Alice");
 */
public class ChatClient {
    private String serverAddress;
    private int serverPort;
    private Socket socket;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String username;
    
    // Callbacks to update GUI
    private Consumer<Message> messageListener;
    private Consumer<List<User>> userListListener;
    private Consumer<Boolean> connectionListener; // true=connected, false=disconnected
    private Consumer<FileTransfer> fileTransferListener;
    
    private Thread listenerThread;
    private volatile boolean connected = false;
    
    public ChatClient(String address, int port) {
        this.serverAddress = address;
        this.serverPort = port;
    }
    
    // Setters for GUI callbacks
    public void setMessageListener(Consumer<Message> listener) {
        this.messageListener = listener;
    }
    
    public void setUserListListener(Consumer<List<User>> listener) {
        this.userListListener = listener;
    }
    
    public void setConnectionListener(Consumer<Boolean> listener) {
        this.connectionListener = listener;
    }

    public void setFileTransferListener(Consumer<FileTransfer> listener) {
        this.fileTransferListener = listener;
    }
    
    /**
     * Connect to server and register username.
     * Returns true if successful, false if failed.
     */
    public boolean connect(String username) {
        this.username = username;
        try {
            socket = new Socket(serverAddress, serverPort);
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());
            
            // Send username and wait for server approval
            out.writeObject(username);
            out.flush();
            
            Object response = in.readObject();
            if (response instanceof Message) {
                Message msg = (Message) response;
                if (msg.getContent().equals("SUCCESS")) {
                    connected = true;
                    notifyConnection(true);
                    startListener(); // Begin background message listening
                    return true;
                } else {
                    // Server rejected username
                    return false;
                }
            }
            return false;
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Connection failed: " + e.getMessage());
            return false;
        }
    }
    
    // Background thread: constantly read server messages
    private void startListener() {
        listenerThread = new Thread(() -> {
            try {
                while (connected) {
                    Object received = in.readObject();
                    
                    if (received instanceof Message) {
                        if (messageListener != null) {
                            messageListener.accept((Message) received);
                        }
                    } else if (received instanceof FileTransfer) {
                        if (fileTransferListener != null) {
                            fileTransferListener.accept((FileTransfer) received);
                        }
                    } else if (received instanceof List<?>) {
                        // User list update
                        @SuppressWarnings("unchecked")
                        List<User> users = (List<User>) received;
                        if (userListListener != null) {
                            userListListener.accept(users);
                        }
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                // Connection lost
                disconnect();
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }
    
    /**
     * Send message to server.
     * type: BROADCAST (to all) or PRIVATE (to specific user)
     */
    public void sendMessage(String content, Message.Type type, String recipient) {
        if (!connected) return;
        try {
            Message msg = new Message(username, recipient, content, type);
            out.writeObject(msg);
            out.flush();
        } catch (IOException e) {
            System.err.println("Send failed: " + e.getMessage());
        }
    }

    public void sendFileTransfer(FileTransfer transfer) {
        if (!connected) return;
        try {
            out.writeObject(transfer);
            out.flush();
        } catch (IOException e) {
            System.err.println("File send failed: " + e.getMessage());
        }
    }
    
    public void disconnect() {
        connected = false;
        notifyConnection(false);
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (socket != null) socket.close();
        } catch (IOException e) {
            // Ignore
        }
    }
    
    public boolean isConnected() {
        return connected;
    }
    
    public String getUsername() {
        return username;
    }
    
    public String getServerInfo() {
        return serverAddress + ":" + serverPort;
    }
    
    private void notifyConnection(boolean status) {
        if (connectionListener != null) {
            connectionListener.accept(status);
        }
    }
}