package client;

import common.FileTransfer;
import common.LoginRequest;
import common.Message;
import common.RoomCommand;
import common.RoomListUpdate;
import common.RoomStateEvent;
import common.TypingEvent;
import common.User;

import java.io.IOException;
import java.net.Socket;
import java.util.List;
import java.util.function.Consumer;

/**
 * Network client: login, chat, rooms, typing, files.
 */
public class ChatClient {
    private String serverAddress;
    private int serverPort;
    private Socket socket;
    private java.io.ObjectOutputStream out;
    private java.io.ObjectInputStream in;
    private String username;

    private Consumer<Message> messageListener;
    private Consumer<List<User>> userListListener;
    private Consumer<Boolean> connectionListener;
    private Consumer<FileTransfer> fileTransferListener;
    private Consumer<RoomListUpdate> roomListListener;
    private Consumer<RoomStateEvent> roomStateListener;
    private Consumer<TypingEvent> typingListener;

    private Thread listenerThread;
    private volatile boolean connected = false;

    public ChatClient(String address, int port) {
        this.serverAddress = address;
        this.serverPort = port;
    }

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

    public void setRoomListListener(Consumer<RoomListUpdate> listener) {
        this.roomListListener = listener;
    }

    public void setRoomStateListener(Consumer<RoomStateEvent> listener) {
        this.roomStateListener = listener;
    }

    public void setTypingListener(Consumer<TypingEvent> listener) {
        this.typingListener = listener;
    }

    /**
     * Connect and authenticate. Returns true if server responds with SUCCESS.
     */
    public boolean connect(LoginRequest login) {
        this.username = login.getUsername() == null ? null : login.getUsername().trim();
        try {
            socket = new Socket(serverAddress, serverPort);
            out = new java.io.ObjectOutputStream(socket.getOutputStream());
            in = new java.io.ObjectInputStream(socket.getInputStream());

            out.writeObject(login);
            out.flush();

            Object response = in.readObject();
            if (response instanceof Message) {
                Message msg = (Message) response;
                if ("SUCCESS".equals(msg.getContent())) {
                    connected = true;
                    notifyConnection(true);
                    startListener();
                    requestRoomList();
                    return true;
                }
            }
            closeQuietly();
            return false;
        } catch (IOException | ClassNotFoundException e) {
            System.err.println("Connection failed: " + e.getMessage());
            closeQuietly();
            return false;
        }
    }

    private void closeQuietly() {
        try {
            if (in != null) in.close();
        } catch (IOException ignored) {
        }
        try {
            if (out != null) out.close();
        } catch (IOException ignored) {
        }
        try {
            if (socket != null) socket.close();
        } catch (IOException ignored) {
        }
        in = null;
        out = null;
        socket = null;
    }

    public void requestRoomList() {
        sendRoomCommand(new RoomCommand(RoomCommand.Action.LIST, ""));
    }

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
                    } else if (received instanceof RoomListUpdate) {
                        if (roomListListener != null) {
                            roomListListener.accept((RoomListUpdate) received);
                        }
                    } else if (received instanceof RoomStateEvent) {
                        if (roomStateListener != null) {
                            roomStateListener.accept((RoomStateEvent) received);
                        }
                    } else if (received instanceof TypingEvent) {
                        if (typingListener != null) {
                            typingListener.accept((TypingEvent) received);
                        }
                    } else if (received instanceof List<?>) {
                        @SuppressWarnings("unchecked")
                        List<User> users = (List<User>) received;
                        if (userListListener != null) {
                            userListListener.accept(users);
                        }
                    }
                }
            } catch (IOException | ClassNotFoundException e) {
                disconnect();
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public void sendMessage(String content, Message.Type type, String recipient, String roomName) {
        if (!connected) return;
        try {
            Message msg = new Message(username, recipient, content, type, roomName);
            out.writeObject(msg);
            out.flush();
        } catch (IOException e) {
            System.err.println("Send failed: " + e.getMessage());
        }
    }

    public void sendRoomCommand(RoomCommand cmd) {
        if (!connected) return;
        try {
            out.writeObject(cmd);
            out.flush();
        } catch (IOException e) {
            System.err.println("Room command failed: " + e.getMessage());
        }
    }

    public void sendTypingEvent(TypingEvent event) {
        if (!connected) return;
        try {
            out.writeObject(event);
            out.flush();
        } catch (IOException e) {
            // ignore typing errors
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
        } catch (IOException ignored) {
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
