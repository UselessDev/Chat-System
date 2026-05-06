package client;

import common.Message;
import common.User;

import javax.swing.*;
import javax.swing.border.*;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
    private static final String GROUP_TAB_TITLE = "Group Chat";

    private ChatClient client;
    
    // GUI Components
    private JTabbedPane chatTabs;
    private JTextArea groupChatArea;
    private Map<String, JTextArea> directMessageAreas;
    private JTextField inputField;          // Where user types
    private JButton sendButton;             // Send button
    
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
        directMessageAreas = new HashMap<>();
        setupCallbacks();
        
        buildGUI();
        
        // Prompt for username on startup
        SwingUtilities.invokeLater(this::promptUsername);
    }
    
    private void buildGUI() {
        setLayout(new BorderLayout(5, 5));
        
        // === CENTER: Group + Direct Message Tabs ===
        chatTabs = new JTabbedPane();
        groupChatArea = createChatArea();

        JScrollPane groupChatScroll = new JScrollPane(groupChatArea);
        groupChatScroll.setBorder(BorderFactory.createTitledBorder(
            new EtchedBorder(), "Chat History", TitledBorder.LEFT, TitledBorder.TOP,
            new Font("Segoe UI", Font.BOLD, 12)
        ));
        chatTabs.addTab(GROUP_TAB_TITLE, groupChatScroll);
        add(chatTabs, BorderLayout.CENTER);
        
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
        userListDisplay.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    String selectedUser = userListDisplay.getSelectedValue();
                    if (selectedUser != null && !selectedUser.trim().isEmpty()) {
                        openDirectMessageTab(selectedUser);
                    }
                }
            }
        });
        
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
        
        JPanel buttonPanel = new JPanel(new GridLayout(1, 1, 5, 0));
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

        String selectedTab = chatTabs.getTitleAt(chatTabs.getSelectedIndex());
        if (GROUP_TAB_TITLE.equals(selectedTab)) {
            client.sendMessage(text, Message.Type.BROADCAST, null);
        } else {
            String recipient = extractUsernameFromTabTitle(selectedTab);
            client.sendMessage(text, Message.Type.PRIVATE, recipient);
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
    }
    
    private void appendSystemMessage(String text) {
        appendToArea(groupChatArea, "[INFO] " + text);
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
        
        removeTabsForOfflineUsers(users);
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

    private JTextArea createChatArea() {
        JTextArea area = new JTextArea();
        area.setEditable(false);
        area.setLineWrap(true);
        area.setWrapStyleWord(true);
        area.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        return area;
    }

    private void openDirectMessageTab(String username) {
        if (!directMessageAreas.containsKey(username)) {
            JTextArea dmArea = createChatArea();
            directMessageAreas.put(username, dmArea);
            JScrollPane dmScroll = new JScrollPane(dmArea);
            chatTabs.addTab(getDirectMessageTabTitle(username), dmScroll);
        }
        chatTabs.setSelectedIndex(findTabIndexByTitle(getDirectMessageTabTitle(username)));
    }

    private void handlePrivateMessage(Message msg) {
        String me = client.getUsername();
        String otherUser = msg.getSender().equals(me) ? msg.getRecipient() : msg.getSender();
        if (otherUser == null || otherUser.trim().isEmpty()) {
            appendToArea(groupChatArea, "[DM] " + msg.getSender() + ": " + msg.getContent());
            return;
        }

        openDirectMessageTab(otherUser);
        JTextArea dmArea = directMessageAreas.get(otherUser);
        if (msg.getSender().equals(me)) {
            appendToArea(dmArea, "You: " + msg.getContent());
        } else {
            appendToArea(dmArea, msg.getSender() + ": " + msg.getContent());
        }
    }

    private void appendToArea(JTextArea area, String line) {
        area.append(line + "\n");
        area.setCaretPosition(area.getDocument().getLength());
    }

    private void removeTabsForOfflineUsers(List<User> users) {
        Map<String, Boolean> onlineUsers = new HashMap<>();
        for (User user : users) {
            onlineUsers.put(user.getUsername(), true);
        }

        directMessageAreas.entrySet().removeIf(entry -> {
            String username = entry.getKey();
            if (!onlineUsers.containsKey(username)) {
                int tabIndex = findTabIndexByTitle(getDirectMessageTabTitle(username));
                if (tabIndex >= 0) {
                    chatTabs.remove(tabIndex);
                }
                return true;
            }
            return false;
        });
    }

    private int findTabIndexByTitle(String title) {
        for (int i = 0; i < chatTabs.getTabCount(); i++) {
            if (title.equals(chatTabs.getTitleAt(i))) {
                return i;
            }
        }
        return -1;
    }

    private String getDirectMessageTabTitle(String username) {
        return "DM: " + username;
    }

    private String extractUsernameFromTabTitle(String tabTitle) {
        String prefix = "DM: ";
        if (tabTitle != null && tabTitle.startsWith(prefix)) {
            return tabTitle.substring(prefix.length()).trim();
        }
        return null;
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
