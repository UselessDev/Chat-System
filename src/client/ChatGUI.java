package client;

import common.Message;
import common.User;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.util.List;

/**
 * Main chat application window.
 * Layout:
 * +------------------+------------------+
 * |                  |  [Status Panel]  |
 * |   Chat History   |  Server: IP:Port |
 * |   (Text Area)    |  Status: Green/  |
 * |                  |         Red      |
 * |                  |                  |
 * |                  |  [User List]     |
 * |                  |  - Alice         |
 * |                  |  - Bob           |
 * |                  |  (Dropdown for   |
 * |                  |   private msg)   |
 * +------------------+------------------+
 * | [Message Input ] [Send] [Send to ▼] |
 * +-------------------------------------+
 */
public class ChatGUI extends JFrame {
    private ChatClient client;
    
    // GUI Components
    private JTextArea chatArea;           // Message history display
    private JTextField inputField;          // Where user types
    private JButton sendButton;             // Send button
    private JComboBox<String> userDropdown; // Select recipient
    private JComboBox<String> targetSelector; // "All" or specific user
    
    // Status Panel Components
    private JLabel statusLabel;
    private JLabel serverInfoLabel;
    private DefaultListModel<String> userListModel;
    private JList<String> userListDisplay;
    
    // Colors
    private static final Color COLOR_ONLINE = new Color(46, 139, 87);   // Green
    private static final Color COLOR_OFFLINE = new Color(220, 20, 60);  // Red
    private static final Color COLOR_SYSTEM = new Color(100, 100, 100); // Gray
    
    public ChatGUI(String serverAddress, int port) {
        setTitle("Chat Application");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(800, 600);
        setLocationRelativeTo(null); // Center on screen
        
        // Initialize client
        client = new ChatClient(serverAddress, port);
        setupCallbacks();
        
        buildGUI();
        
        // Prompt for username on startup
        SwingUtilities.invokeLater(this::promptUsername);
    }
    
    private void buildGUI() {
        setLayout(new BorderLayout(5, 5));
        
        // === CENTER: Chat History ===
        chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        
        JScrollPane chatScroll = new JScrollPane(chatArea);
        chatScroll.setBorder(BorderFactory.createTitledBorder(
            new EtchedBorder(), "Chat History", TitledBorder.LEFT, TitledBorder.TOP,
            new Font("Segoe UI", Font.BOLD, 12)
        ));
        add(chatScroll, BorderLayout.CENTER);
        
        // === EAST: Status & User Panel ===
        JPanel sidePanel = new JPanel(new BorderLayout(5, 5));
        sidePanel.setPreferredSize(new Dimension(200, 0));
        sidePanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        
        // Connection Status Panel
        JPanel statusPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        statusPanel.setBorder(BorderFactory.createTitledBorder(
            new EtchedBorder(), "Connection Status", TitledBorder.LEFT, TitledBorder.TOP
        ));
        
        serverInfoLabel = new JLabel("Server: --", JLabel.CENTER);
        serverInfoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        
        statusLabel = new JLabel("● Disconnected", JLabel.CENTER);
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        statusLabel.setForeground(COLOR_OFFLINE);
        
        statusPanel.add(new JLabel("Network", JLabel.CENTER));
        statusPanel.add(serverInfoLabel);
        statusPanel.add(statusLabel);
        
        sidePanel.add(statusPanel, BorderLayout.NORTH);
        
        // User List
        userListModel = new DefaultListModel<>();
        userListDisplay = new JList<>(userListModel);
        userListDisplay.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        
        JScrollPane userScroll = new JScrollPane(userListDisplay);
        userScroll.setBorder(BorderFactory.createTitledBorder(
            new EtchedBorder(), "Online Users", TitledBorder.LEFT, TitledBorder.TOP
        ));
        sidePanel.add(userScroll, BorderLayout.CENTER);
        
        add(sidePanel, BorderLayout.EAST);
        
        // === SOUTH: Input Area ===
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputPanel.setBorder(new EmptyBorder(5, 5, 5, 5));
        
        inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        inputField.addActionListener(e -> sendMessage()); // Enter key sends
        
        sendButton = new JButton("Send");
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        sendButton.setEnabled(false); // Disabled until connected
        sendButton.addActionListener(e -> sendMessage());
        
        // Target selector: "All Users" or specific person
        targetSelector = new JComboBox<>();
        targetSelector.addItem("Send to All");
        targetSelector.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        
        JPanel buttonPanel = new JPanel(new GridLayout(1, 2, 5, 0));
        buttonPanel.add(targetSelector);
        buttonPanel.add(sendButton);
        
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(buttonPanel, BorderLayout.EAST);
        
        add(inputPanel, BorderLayout.SOUTH);
    }
    
    // Set up client-to-GUI communication
    private void setupCallbacks() {
        // When message received from server
        client.setMessageListener(msg -> SwingUtilities.invokeLater(() -> {
            appendMessage(msg);
        }));
        
        // When user list updated
        client.setUserListListener(users -> SwingUtilities.invokeLater(() -> {
            updateUserList(users);
        }));
        
        // When connection status changes
        client.setConnectionListener(connected -> SwingUtilities.invokeLater(() -> {
            updateConnectionStatus(connected);
        }));
    }
    
    // Popup dialog for username entry
    private void promptUsername() {
        while (true) {
            String username = JOptionPane.showInputDialog(
                this,
                "Enter your username:",
                "Chat Login",
                JOptionPane.QUESTION_MESSAGE
            );
            
            if (username == null) {
                System.exit(0); // User clicked cancel
            }
            
            username = username.trim();
            if (username.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Username cannot be empty");
                continue;
            }
            
            // Attempt connection
            appendSystemMessage("Connecting to " + client.getServerInfo() + "...");
            
            if (client.connect(username)) {
                setTitle("Chat - " + username);
                sendButton.setEnabled(true);
                appendSystemMessage("Connected as " + username);
                break;
            } else {
                JOptionPane.showMessageDialog(
                    this,
                    "Failed to connect. Username may be taken or server unavailable.",
                    "Connection Failed",
                    JOptionPane.ERROR_MESSAGE
                );
            }
        }
    }
    
    // Send message based on target selector
    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;
        
        String target = (String) targetSelector.getSelectedItem();
        
        if ("Send to All".equals(target)) {
            // Broadcast to everyone
            client.sendMessage(text, Message.Type.BROADCAST, null);
            // Note: Server will echo back, so we don't add to GUI here
            // (Real apps often show "pending" then confirm)
        } else {
            // Private message to selected user
            client.sendMessage(text, Message.Type.PRIVATE, target);
        }
        
        inputField.setText(""); // Clear input
        inputField.requestFocus();
    }
    
    // Add formatted message to chat area
    private void appendMessage(Message msg) {
        String timeStr = "[" + msg.getFormattedTime() + "] ";
        String formatted;
        Color color = Color.BLACK;
        
        switch (msg.getType()) {
            case SYSTEM:
                formatted = timeStr + "[SYSTEM] " + msg.getContent();
                color = COLOR_SYSTEM;
                break;
            case PRIVATE:
                formatted = timeStr + "[DM] " + msg.getSender() + " -> " + msg.getRecipient() + ": " + msg.getContent();
                color = new Color(128, 0, 128); // Purple for private
                break;
            default: // BROADCAST
                if (msg.getSender().equals(client.getUsername())) {
                    formatted = timeStr + "You: " + msg.getContent();
                    color = new Color(0, 100, 200); // Blue for own messages
                } else {
                    formatted = timeStr + msg.getSender() + ": " + msg.getContent();
                }
        }
        
        chatArea.append(formatted + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength()); // Auto-scroll
    }
    
    private void appendSystemMessage(String text) {
        chatArea.append("[INFO] " + text + "\n");
        chatArea.setCaretPosition(chatArea.getDocument().getLength());
    }
    
    // Update the user list dropdown and display
    private void updateUserList(List<User> users) {
        // Update JList display
        userListModel.clear();
        for (User user : users) {
            if (!user.getUsername().equals(client.getUsername())) {
                userListModel.addElement(user.getUsername());
            }
        }
        
        // Update dropdown (preserve selection if possible)
        String currentSelection = (String) targetSelector.getSelectedItem();
        targetSelector.removeAllItems();
        targetSelector.addItem("Send to All");
        
        for (User user : users) {
            if (!user.getUsername().equals(client.getUsername())) {
                targetSelector.addItem(user.getUsername());
            }
        }
        
        // Restore selection if still valid
        if (currentSelection != null) {
            for (int i = 0; i < targetSelector.getItemCount(); i++) {
                if (targetSelector.getItemAt(i).equals(currentSelection)) {
                    targetSelector.setSelectedIndex(i);
                    break;
                }
            }
        }
    }
    
    // Update status indicators
    private void updateConnectionStatus(boolean connected) {
        if (connected) {
            statusLabel.setText("● Connected");
            statusLabel.setForeground(COLOR_ONLINE);
            serverInfoLabel.setText("Server: " + client.getServerInfo());
            sendButton.setEnabled(true);
        } else {
            statusLabel.setText("● Disconnected");
            statusLabel.setForeground(COLOR_OFFLINE);
            sendButton.setEnabled(false);
            JOptionPane.showMessageDialog(this, "Connection lost!");
        }
    }

    public static void main(String[] args) {
        String serverAddress = "localhost";
        int port = 5000;

        if (args.length >= 1 && !args[0].trim().isEmpty()) {
            serverAddress = args[0].trim();
        }
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port. Using default 5000.");
            }
        }

        String finalServerAddress = serverAddress;
        int finalPort = port;
        SwingUtilities.invokeLater(() -> {
            ChatGUI gui = new ChatGUI(finalServerAddress, finalPort);
            gui.setVisible(true);
        });
    }
}
