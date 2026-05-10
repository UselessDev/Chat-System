package client;

import common.FileTransfer;
import common.Message;
import common.User;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Main chat window: tabbed group + DM, styled text, file transfer, local history.
 */
public class ChatGUI extends JFrame {
    private static final String GROUP_TAB_TITLE = "Group Chat";
    private static final int FILE_CHUNK = 16 * 1024;

    private static final Color COLOR_SERVER = new Color(200, 95, 0);
    private static final Color COLOR_TIME = new Color(120, 120, 120);
    private static final Color COLOR_SYSTEM = new Color(100, 100, 100);
    private static final Color COLOR_INFO = new Color(70, 130, 180);

    private ChatClient client;

    private JTabbedPane chatTabs;
    private JTextPane groupChatPane;
    private Map<String, JTextPane> directMessagePanes;

    private JTextField inputField;
    private JButton sendButton;
    private JButton sendFileButton;

    private JLabel statusLabel;
    private JLabel serverInfoLabel;
    private DefaultListModel<String> userListModel;
    private JList<String> userListDisplay;

    private static final Color COLOR_ONLINE = new Color(46, 139, 87);
    private static final Color COLOR_OFFLINE = new Color(220, 20, 60);

    private final Map<String, IncomingFileSession> incomingFiles = new ConcurrentHashMap<>();

    public ChatGUI(String serverAddress, int port) {
        setTitle("Chat Application");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(900, 640);
        setLocationRelativeTo(null);

        client = new ChatClient(serverAddress, port);
        directMessagePanes = new HashMap<>();
        setupCallbacks();
        buildGUI();
        SwingUtilities.invokeLater(this::promptUsername);
    }

    private void buildGUI() {
        setLayout(new BorderLayout(5, 5));

        chatTabs = new JTabbedPane();
        groupChatPane = createChatPane();
        JScrollPane groupChatScroll = new JScrollPane(groupChatPane);
        groupChatScroll.setBorder(BorderFactory.createTitledBorder(
                new EtchedBorder(), "Chat", TitledBorder.LEFT, TitledBorder.TOP,
                new Font("Segoe UI", Font.BOLD, 12)));
        chatTabs.addTab(GROUP_TAB_TITLE, groupChatScroll);
        add(chatTabs, BorderLayout.CENTER);

        JPanel sidePanel = new JPanel(new BorderLayout(5, 5));
        sidePanel.setPreferredSize(new Dimension(210, 0));
        sidePanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        JPanel statusPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        statusPanel.setBorder(BorderFactory.createTitledBorder(
                new EtchedBorder(), "Connection Status", TitledBorder.LEFT, TitledBorder.TOP));

        serverInfoLabel = new JLabel("Server: --", JLabel.CENTER);
        serverInfoLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        statusLabel = new JLabel("● Disconnected", JLabel.CENTER);
        statusLabel.setFont(new Font("Segoe UI", Font.BOLD, 14));
        statusLabel.setForeground(COLOR_OFFLINE);

        statusPanel.add(new JLabel("Network", JLabel.CENTER));
        statusPanel.add(serverInfoLabel);
        statusPanel.add(statusLabel);
        sidePanel.add(statusPanel, BorderLayout.NORTH);

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
                new EtchedBorder(), "Online Users (click to DM)", TitledBorder.LEFT, TitledBorder.TOP));
        sidePanel.add(userScroll, BorderLayout.CENTER);
        add(sidePanel, BorderLayout.EAST);

        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputPanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        inputField.addActionListener(e -> sendMessage());

        sendButton = new JButton("Send");
        sendButton.setFont(new Font("Segoe UI", Font.BOLD, 12));
        sendButton.setEnabled(false);
        sendButton.addActionListener(e -> sendMessage());

        sendFileButton = new JButton("Send File");
        sendFileButton.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        sendFileButton.setEnabled(false);
        sendFileButton.setToolTipText("Open a DM tab with a user, then send a file privately.");
        sendFileButton.addActionListener(e -> startFileSend());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttonPanel.add(sendFileButton);
        buttonPanel.add(sendButton);

        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(buttonPanel, BorderLayout.EAST);
        add(inputPanel, BorderLayout.SOUTH);
    }

    private void setupCallbacks() {
        client.setMessageListener(msg -> SwingUtilities.invokeLater(() -> appendMessage(msg)));
        client.setUserListListener(users -> SwingUtilities.invokeLater(() -> updateUserList(users)));
        client.setConnectionListener(connected -> SwingUtilities.invokeLater(() -> updateConnectionStatus(connected)));
        client.setFileTransferListener(ft -> SwingUtilities.invokeLater(() -> handleIncomingFileTransfer(ft)));
    }

    private void promptUsername() {
        while (true) {
            String username = JOptionPane.showInputDialog(this,
                    "Enter your username:", "Chat Login", JOptionPane.QUESTION_MESSAGE);
            if (username == null) {
                System.exit(0);
            }
            username = username.trim();
            if (username.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Username cannot be empty");
                continue;
            }
            appendInfoLine(groupChatPane, "Connecting to " + client.getServerInfo() + "...");
            if (client.connect(username)) {
                setTitle("Chat - " + username);
                sendButton.setEnabled(true);
                sendFileButton.setEnabled(true);
                appendInfoLine(groupChatPane, "Connected as " + username);
                loadChatHistory();
                break;
            } else {
                JOptionPane.showMessageDialog(this,
                        "Failed to connect. Username may be taken or server unavailable.",
                        "Connection Failed", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

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
        inputField.setText("");
        inputField.requestFocus();
    }

    private void startFileSend() {
        String selectedTab = chatTabs.getTitleAt(chatTabs.getSelectedIndex());
        if (GROUP_TAB_TITLE.equals(selectedTab)) {
            JOptionPane.showMessageDialog(this,
                    "Select a Direct Message tab (click a user) before sending a file.",
                    "Send File", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String recipient = extractUsernameFromTabTitle(selectedTab);
        if (recipient == null || recipient.isEmpty()) {
            return;
        }

        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Choose file to send to " + recipient);
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File file = chooser.getSelectedFile();
        if (file == null || !file.isFile()) {
            return;
        }

        JTextPane dmPane = directMessagePanes.get(recipient);
        new Thread(() -> uploadFileToUser(recipient, file, dmPane), "file-upload").start();
    }

    private void uploadFileToUser(String recipient, File file, JTextPane dmPane) {
        if (dmPane == null) {
            return;
        }
        String transferId = UUID.randomUUID().toString();
        String me = client.getUsername();
        try {
            long len = file.length();
            int totalChunks = (int) Math.ceil(len / (double) FILE_CHUNK);
            if (totalChunks == 0) totalChunks = 1;

            appendFileStatusEdt(dmPane, "[FILE] Starting upload: " + file.getName() + " (" + len + " bytes)");
            client.sendFileTransfer(FileTransfer.start(me, recipient, transferId, file.getName(), len, totalChunks));

            try (FileInputStream fis = new FileInputStream(file)) {
                byte[] buf = new byte[FILE_CHUNK];
                long sent = 0;
                int index = 0;
                int read;
                while ((read = fis.read(buf)) != -1) {
                    index++;
                    sent += read;
                    byte[] chunk = java.util.Arrays.copyOf(buf, read);
                    boolean last = sent >= len;
                    client.sendFileTransfer(FileTransfer.chunk(me, recipient, transferId, file.getName(),
                            len, index, totalChunks, chunk, last));

                    int pct = (int) Math.min(100, Math.round(100.0 * sent / Math.max(1, len)));
                    if (index == 1 || last || index % 5 == 0) {
                        appendFileStatusEdt(dmPane, "[FILE] Upload progress: ~" + pct + "%");
                    }
                }
            }
            client.sendFileTransfer(FileTransfer.end(me, recipient, transferId, file.getName(), len, totalChunks));
            appendFileStatusEdt(dmPane, "[FILE] Upload finished — waiting for peer to save.");
            persistLine(dmTabKey(recipient), "file", me, recipient, "SENT " + file.getName());
        } catch (IOException ex) {
            SwingUtilities.invokeLater(() ->
                    appendFileStatus(dmPane, "[FILE] Upload failed: " + ex.getMessage()));
        }
    }

    private void handleIncomingFileTransfer(FileTransfer ft) {
        String me = client.getUsername();
        if (!me.equals(ft.getRecipient())) {
            return;
        }
        String other = ft.getSender();
        JTextPane dmPane = directMessagePanes.get(other);
        if (dmPane == null) {
            openDirectMessageTab(other);
            dmPane = directMessagePanes.get(other);
        }

        switch (ft.getPhase()) {
            case START:
                incomingFiles.put(ft.getTransferId(), new IncomingFileSession(ft.getFileName(), ft.getTotalSize(), ft.getTotalChunks(), other));
                appendFileStatus(dmPane, "[FILE] Incoming from " + other + ": " + ft.getFileName()
                        + " (" + ft.getTotalSize() + " bytes). Receiving...");
                break;
            case CHUNK:
                IncomingFileSession session = incomingFiles.get(ft.getTransferId());
                if (session == null) {
                    appendFileStatus(dmPane, "[FILE] Orphan chunk ignored.");
                    return;
                }
                session.append(ft.getData());
                int pctRecv = (int) Math.min(100,
                        Math.round(100.0 * session.buffer.size() / Math.max(1, session.totalSize)));
                if (ft.getChunkIndex() == 1 || ft.getChunkIndex() % 6 == 0 || ft.isLastChunk()) {
                    appendFileStatus(dmPane, "[FILE] Receiving… ~" + pctRecv + "%");
                }
                break;
            case END:
                IncomingFileSession s = incomingFiles.remove(ft.getTransferId());
                if (s != null) {
                    finishIncomingFile(dmPane, s);
                }
                break;
            default:
                break;
        }
    }

    private void finishIncomingFile(JTextPane dmPane, IncomingFileSession session) {
        byte[] all = session.buffer.toByteArray();
        SwingUtilities.invokeLater(() -> {
            JFileChooser save = new JFileChooser();
            save.setDialogTitle("Save file from " + session.sender);
            save.setSelectedFile(new File(session.fileName));
            if (save.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                appendFileStatus(dmPane, "[FILE] Save cancelled for: " + session.fileName);
                return;
            }
            File dest = save.getSelectedFile();
            try {
                Files.write(dest.toPath(), all);
                appendFileStatus(dmPane, "[FILE] Saved as: " + dest.getAbsolutePath());
                persistLine(dmTabKey(session.sender), "file", session.sender, client.getUsername(), "RECV " + session.fileName);
            } catch (IOException e) {
                appendFileStatus(dmPane, "[FILE] Save failed: " + e.getMessage());
            }
        });
    }

    private void appendMessage(Message msg) {
        String timeStr = "[" + msg.getFormattedTime() + "] ";
        switch (msg.getType()) {
            case SYSTEM:
                appendSystemLine(groupChatPane, timeStr + msg.getContent());
                persistLine("group", "system", "SERVER", null, msg.getContent());
                break;
            case PRIVATE:
                handlePrivateMessage(msg, timeStr, true);
                break;
            default:
                appendBroadcastLine(msg, timeStr, true);
                break;
        }
    }

    private void appendBroadcastLine(Message msg, String timeStr, boolean saveHistory) {
        String body = StyleUtil.applyEmoticons(msg.getContent());
        boolean server = "Server".equals(msg.getSender());
        boolean self = msg.getSender().equals(client.getUsername());
        Color nameColor = server ? COLOR_SERVER : (self ? new Color(0, 90, 200) : StyleUtil.colorForUsername(msg.getSender()));
        String speaker = server ? "Server" : (self ? "You" : msg.getSender());

        insertLineParts(groupChatPane,
                new LinePart(timeStr, COLOR_TIME, false, true),
                new LinePart("[" + speaker + "] ", nameColor, true, false),
                new LinePart(body, new Color(30, 30, 30), false, false));

        if (saveHistory) {
            persistLine("group", "broadcast", msg.getSender(), null, msg.getContent());
        }
    }

    private void handlePrivateMessage(Message msg, String timeStr, boolean saveHistory) {
        String me = client.getUsername();
        String otherUser = msg.getSender().equals(me) ? msg.getRecipient() : msg.getSender();
        if (otherUser == null || otherUser.trim().isEmpty()) {
            appendSystemLine(groupChatPane, timeStr + "[DM] " + msg.getSender() + ": " + msg.getContent());
            return;
        }
        openDirectMessageTab(otherUser);
        JTextPane dmPane = directMessagePanes.get(otherUser);
        String body = StyleUtil.applyEmoticons(msg.getContent());
        boolean self = msg.getSender().equals(me);
        Color nameColor = self ? new Color(0, 90, 200) : StyleUtil.colorForUsername(msg.getSender());
        String speaker = self ? "You" : msg.getSender();
        insertLineParts(dmPane,
                new LinePart(timeStr, COLOR_TIME, false, true),
                new LinePart("[" + speaker + "] ", nameColor, true, false),
                new LinePart(body, new Color(30, 30, 30), false, false));

        if (saveHistory) {
            persistLine(dmTabKey(otherUser), "private", msg.getSender(), msg.getRecipient(), msg.getContent());
        }
    }

    private void appendInfoLine(JTextPane pane, String text) {
        appendPlain(pane, text, COLOR_INFO, false, true);
    }

    private void appendSystemLine(JTextPane pane, String text) {
        appendPlain(pane, text, COLOR_SYSTEM, false, true);
    }

    private void appendFileStatus(JTextPane pane, String text) {
        appendPlain(pane, text, new Color(110, 70, 150), false, true);
    }

    private void appendFileStatusEdt(JTextPane pane, String text) {
        if (SwingUtilities.isEventDispatchThread()) {
            appendFileStatus(pane, text);
        } else {
            SwingUtilities.invokeLater(() -> appendFileStatus(pane, text));
        }
    }

    private void appendPlain(JTextPane pane, String text, Color fg, boolean bold, boolean italic) {
        try {
            StyledDocument doc = pane.getStyledDocument();
            SimpleAttributeSet a = new SimpleAttributeSet();
            StyleConstants.setForeground(a, fg);
            StyleConstants.setBold(a, bold);
            StyleConstants.setItalic(a, italic);
            StyleConstants.setFontFamily(a, "Segoe UI");
            StyleConstants.setFontSize(a, 14);
            doc.insertString(doc.getLength(), text + "\n", a);
            pane.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {
        }
    }

    private void insertLineParts(JTextPane pane, LinePart... parts) {
        try {
            StyledDocument doc = pane.getStyledDocument();
            for (LinePart p : parts) {
                SimpleAttributeSet a = new SimpleAttributeSet();
                StyleConstants.setForeground(a, p.color);
                StyleConstants.setBold(a, p.bold);
                StyleConstants.setItalic(a, p.italic);
                StyleConstants.setFontFamily(a, "Segoe UI");
                StyleConstants.setFontSize(a, 14);
                doc.insertString(doc.getLength(), p.text, a);
            }
            doc.insertString(doc.getLength(), "\n", new SimpleAttributeSet());
            pane.setCaretPosition(doc.getLength());
        } catch (BadLocationException ignored) {
        }
    }

    private static final class LinePart {
        final String text;
        final Color color;
        final boolean bold;
        final boolean italic;

        LinePart(String text, Color color, boolean bold, boolean italic) {
            this.text = text;
            this.color = color;
            this.bold = bold;
            this.italic = italic;
        }
    }

    private JTextPane createChatPane() {
        JTextPane pane = new JTextPane();
        pane.setEditable(false);
        pane.setMargin(new Insets(6, 8, 6, 8));
        return pane;
    }

    private void openDirectMessageTab(String username) {
        if (!directMessagePanes.containsKey(username)) {
            JTextPane dmPane = createChatPane();
            directMessagePanes.put(username, dmPane);
            chatTabs.addTab(getDirectMessageTabTitle(username), new JScrollPane(dmPane));
        }
        chatTabs.setSelectedIndex(findTabIndexByTitle(getDirectMessageTabTitle(username)));
    }

    private void updateUserList(List<User> users) {
        userListModel.clear();
        for (User user : users) {
            if (!user.getUsername().equals(client.getUsername())) {
                userListModel.addElement(user.getUsername());
            }
        }
        removeTabsForOfflineUsers(users);
    }

    private void updateConnectionStatus(boolean connected) {
        if (connected) {
            statusLabel.setText("● Connected");
            statusLabel.setForeground(COLOR_ONLINE);
            serverInfoLabel.setText("Server: " + client.getServerInfo());
            sendButton.setEnabled(true);
            sendFileButton.setEnabled(true);
        } else {
            statusLabel.setText("● Disconnected");
            statusLabel.setForeground(COLOR_OFFLINE);
            sendButton.setEnabled(false);
            sendFileButton.setEnabled(false);
            JOptionPane.showMessageDialog(this, "Connection lost!");
        }
    }

    private void removeTabsForOfflineUsers(List<User> users) {
        Map<String, Boolean> online = new HashMap<>();
        for (User user : users) {
            online.put(user.getUsername(), true);
        }
        directMessagePanes.entrySet().removeIf(entry -> {
            String username = entry.getKey();
            if (!online.containsKey(username)) {
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

    private String dmTabKey(String otherUser) {
        return "dm:" + otherUser;
    }

    private void persistLine(String tab, String type, String sender, String recipient, String content) {
        String user = client.getUsername();
        if (user == null) return;
        ChatHistoryStore.append(user, new ChatHistoryStore.HistoryLine(
                System.currentTimeMillis(), tab, sender == null ? "" : sender,
                recipient == null ? "" : recipient, type == null ? "" : type, content == null ? "" : content));
    }

    private void loadChatHistory() {
        List<ChatHistoryStore.HistoryLine> lines = ChatHistoryStore.load(client.getUsername());
        for (ChatHistoryStore.HistoryLine hl : lines) {
            restoreHistoryLine(hl);
        }
    }

    private void restoreHistoryLine(ChatHistoryStore.HistoryLine hl) {
        JTextPane target = hl.tab.startsWith("dm:") ? directMessagePanes.get(hl.tab.substring(3)) : groupChatPane;
        if (hl.tab.startsWith("dm:")) {
            String other = hl.tab.substring(3);
            if (!directMessagePanes.containsKey(other)) {
                JTextPane dmPane = createChatPane();
                directMessagePanes.put(other, dmPane);
                chatTabs.addTab(getDirectMessageTabTitle(other), new JScrollPane(dmPane));
            }
            target = directMessagePanes.get(other);
        }
        String timeStr = "[" + formatTime(hl.timestamp) + "] ";
        switch (hl.type) {
            case "system":
                appendSystemLine(target, timeStr + "[SYSTEM] " + hl.content);
                break;
            case "broadcast":
                Message fake = new Message(hl.sender, null, hl.content, Message.Type.BROADCAST);
                appendBroadcastLine(fake, timeStr, false);
                break;
            case "private":
                Message pm = new Message(hl.sender, hl.recipient, hl.content, Message.Type.PRIVATE);
                handlePrivateMessage(pm, timeStr, false);
                break;
            case "file":
                appendFileStatus(target, timeStr + "[FILE] " + hl.content);
                break;
            default:
                appendPlain(target, timeStr + hl.content, Color.DARK_GRAY, false, false);
        }
    }

    private String formatTime(long ts) {
        return new java.text.SimpleDateFormat("HH:mm:ss").format(new java.util.Date(ts));
    }

    private static final class IncomingFileSession {
        final String fileName;
        final long totalSize;
        final int totalChunks;
        final String sender;
        final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

        IncomingFileSession(String fileName, long totalSize, int totalChunks, String sender) {
            this.fileName = fileName;
            this.totalSize = totalSize;
            this.totalChunks = totalChunks;
            this.sender = sender;
        }

        void append(byte[] data) {
            if (data != null && data.length > 0) {
                buffer.write(data, 0, data.length);
            }
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
