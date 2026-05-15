package server;

import common.FileTransfer;
import common.LoginRequest;
import common.Message;
import common.RoomCommand;
import common.TypingEvent;
import common.User;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.List;

/**
 * One client connection: auth, then message / room / typing / file loop.
 */
public class ClientHandler implements Runnable {
    private final Socket socket;
    private final ChatServer server;
    private ObjectOutputStream out;
    private ObjectInputStream in;
    private String username;
    private volatile String currentRoom = ChatServer.DEFAULT_ROOM;
    private boolean running = true;

    public ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    public String getUsername() {
        return username;
    }

    public String getCurrentRoom() {
        return currentRoom;
    }

    public void setCurrentRoom(String currentRoom) {
        this.currentRoom = currentRoom == null ? ChatServer.DEFAULT_ROOM : currentRoom;
    }

    @Override
    public void run() {
        try {
            out = new ObjectOutputStream(socket.getOutputStream());
            in = new ObjectInputStream(socket.getInputStream());

            if (!authenticate()) {
                close();
                return;
            }

            while (running) {
                Object received = in.readObject();
                if (received instanceof Message) {
                    handleMessage((Message) received);
                } else if (received instanceof FileTransfer) {
                    handleFileTransfer((FileTransfer) received);
                } else if (received instanceof RoomCommand) {
                    server.handleRoomCommand(this, (RoomCommand) received);
                } else if (received instanceof TypingEvent) {
                    TypingEvent te = (TypingEvent) received;
                    if (username != null && username.equals(te.getUsername())) {
                        server.relayTyping(te, username);
                    }
                }
            }
        } catch (IOException | ClassNotFoundException e) {
            if (username != null) {
                System.out.println(username + " disconnected unexpectedly");
            } else {
                System.out.println("Client disconnected before login");
            }
        } finally {
            cleanup();
        }
    }

    private boolean authenticate() throws IOException, ClassNotFoundException {
        Object first = in.readObject();
        if (!(first instanceof LoginRequest)) {
            out.writeObject(Message.system("ERROR: Use LoginRequest with username and password"));
            out.flush();
            return false;
        }
        LoginRequest lr = (LoginRequest) first;
        String proposed = lr.getUsername() == null ? "" : lr.getUsername().trim();
        if (proposed.isEmpty()) {
            out.writeObject(Message.system("ERROR: Username cannot be empty"));
            out.flush();
            return false;
        }
        if (proposed.length() > 32) {
            out.writeObject(Message.system("ERROR: Username too long"));
            out.flush();
            return false;
        }
        String pw = lr.getPassword() == null ? "" : lr.getPassword();
        if (pw.isEmpty()) {
            out.writeObject(Message.system("ERROR: Password cannot be empty"));
            out.flush();
            return false;
        }

        if (!server.authenticate(proposed, pw, lr.isRegisterNewAccount())) {
            String err = lr.isRegisterNewAccount()
                    ? "ERROR: Could not register (name taken or invalid)"
                    : "ERROR: Invalid username or password";
            out.writeObject(Message.system(err));
            out.flush();
            return false;
        }
        if (server.isUsernameTaken(proposed)) {
            out.writeObject(Message.system("ERROR: This user is already logged in"));
            out.flush();
            return false;
        }

        this.username = proposed;
        out.writeObject(Message.system("SUCCESS"));
        out.flush();
        server.addClient(username, this, Thread.currentThread().getName());
        return true;
    }

    private void handleMessage(Message msg) {
        switch (msg.getType()) {
            case BROADCAST:
                server.broadcast(msg);
                String room = msg.getRoomName();
                if (room == null || room.isEmpty()) {
                    room = ChatServer.DEFAULT_ROOM;
                } else {
                    room = room.trim().toLowerCase();
                }
                // Mirror echo ("Server: …") only in general chat, not in custom rooms
                if (ChatServer.DEFAULT_ROOM.equals(room)) {
                    sendMessage(new Message("Server", username, msg.getContent(), Message.Type.BROADCAST, room));
                }
                break;
            case PRIVATE:
                server.sendPrivate(msg.getRecipient(), msg);
                break;
            default:
                break;
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
        } catch (IOException ignored) {
        }
    }
}
