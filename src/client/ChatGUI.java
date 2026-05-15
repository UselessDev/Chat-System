package client;

import common.FileTransfer;
import common.LoginRequest;
import common.Message;
import common.RoomCommand;
import common.RoomListUpdate;
import common.RoomStateEvent;
import common.TypingEvent;
import common.User;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.plaf.ColorUIResource;
import javax.swing.plaf.basic.BasicButtonUI;
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
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Login card + chat: rooms, styled messages, DMs, files, typing indicators.
 */
public class ChatGUI extends JFrame {
    /** Tab title for the global room (all users receive these messages). */
    private static final String GENERAL_TAB_TITLE = "General";
    private static final String ROOM_KEY_PROP = "ROOM_KEY";
    private static final String DEFAULT_ROOM_KEY = "general";
    private static final int FILE_CHUNK = 16 * 1024;

    private static final Color COLOR_SERVER = new Color(0xFA, 0xA6, 0x1A);
    private static final Color COLOR_TIME = new Color(0x72, 0x76, 0x7D);
    private static final Color COLOR_SYSTEM = new Color(0xB9, 0xBB, 0xBE);
    private static final Color COLOR_INFO = new Color(0x58, 0x65, 0xF2);
    private static final Color COLOR_TYPING = new Color(0x94, 0x9C, 0xF7);
    /** Message body on dark chat background (Discord “normal text”). */
    private static final Color COLOR_BODY = new Color(0xDC, 0xDD, 0xDE);
    /** Highlight for “You” / self in chat. */
    private static final Color COLOR_SELF = new Color(0x58, 0x65, 0xF2);

    private static final String CARD_LOGIN = "login";
    private static final String CARD_CHAT = "chat";

    private final ChatClient client;
    private final CardLayout rootLayout = new CardLayout();
    private final JPanel root = new JPanel(rootLayout);

    private JTextField loginUserField;
    private JPasswordField loginPassField;
    private JCheckBox registerCheckBox;
    private JLabel loginErrorLabel;

    private JTabbedPane chatTabs;
    /** Room id (lowercase) → chat pane. General is always {@code general}. */
    private final Map<String, JTextPane> roomChatPanes = new HashMap<>();
    private final Map<String, JTextPane> directMessagePanes = new HashMap<>();

    private JTextField inputField;
    private JButton sendButton;
    private JButton sendFileButton;
    private JLabel statusLabel;
    private JLabel serverInfoLabel;
    private JLabel roomTitleLabel;
    private JLabel typingIndicatorLabel;
    private DefaultListModel<String> userListModel;
    private JList<String> userListDisplay;
    private DefaultListModel<String> roomListModel;
    private JList<String> roomListDisplay;

    private static final Color COLOR_ONLINE = new Color(0x23, 0xA5, 0x5A);
    private static final Color COLOR_OFFLINE = new Color(0xED, 0x42, 0x45);

    private final Map<String, IncomingFileSession> incomingFiles = new ConcurrentHashMap<>();
    private final Set<String> remoteTypingUsers = ConcurrentHashMap.newKeySet();
    private volatile String currentRoom = DEFAULT_ROOM_KEY;
    private Timer typingIdleTimer;
    /** Avoid typing packets while replaying history into the input document chain. */
    private boolean suppressTypingPing;
    /** Last room we sent typing=true for (cleared on tab switch / idle). */
    private String lastTypingRoomKey;

    /** Lowercase room keys the server last advertised (includes {@code general}). */
    private final Set<String> serverKnownRooms = new HashSet<>();
    /** Local history is applied once, after the first room list/state sync from the server. */
    private volatile boolean chatHistoryLoadedOnce;

    public ChatGUI(String serverAddress, int port) {
        setTitle("Chat Application");
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setSize(960, 680);
        setLocationRelativeTo(null);
        getContentPane().setBackground(DiscordTheme.BG_MAIN);

        client = new ChatClient(serverAddress, port);
        setupCallbacks();
        root.add(buildLoginPanelWrapper(), CARD_LOGIN);
        root.add(buildChatRootPanel(), CARD_CHAT);
        add(root);
        rootLayout.show(root, CARD_LOGIN);
    }

    private JPanel buildLoginPanelWrapper() {
        JPanel wrap = new JPanel(new GridBagLayout());
        wrap.setOpaque(true);
        wrap.setBackground(DiscordTheme.BG_MAIN);
        wrap.setBorder(new EmptyBorder(24, 24, 24, 24));
        JPanel card = new JPanel(new GridBagLayout());
        card.setOpaque(true);
        card.setBackground(DiscordTheme.BG_SECONDARY);
        card.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(DiscordTheme.BORDER_STRONG),
                new EmptyBorder(20, 24, 20, 24)));

        GridBagConstraints gc = new GridBagConstraints();
        gc.insets = new Insets(6, 6, 6, 6);
        gc.gridx = 0;
        gc.gridy = 0;
        gc.gridwidth = 2;
        gc.anchor = GridBagConstraints.WEST;
        JLabel titleLbl = new JLabel("Sign in to chat", JLabel.LEFT);
        titleLbl.setForeground(DiscordTheme.TEXT_NORMAL);
        titleLbl.setFont(new Font("Segoe UI", Font.BOLD, 16));
        card.add(titleLbl, gc);
        gc.gridy++;
        JLabel serverLbl = new JLabel("Server: " + client.getServerInfo());
        serverLbl.setForeground(DiscordTheme.TEXT_MUTED);
        serverLbl.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        card.add(serverLbl, gc);

        gc.gridwidth = 1;
        gc.gridy++;
        JLabel uLbl = new JLabel("Username:");
        uLbl.setForeground(DiscordTheme.TEXT_MUTED);
        card.add(uLbl, gc);
        gc.gridx = 1;
        loginUserField = new JTextField(18);
        DiscordTheme.styleTextField(loginUserField);
        card.add(loginUserField, gc);

        gc.gridx = 0;
        gc.gridy++;
        JLabel pLbl = new JLabel("Password:");
        pLbl.setForeground(DiscordTheme.TEXT_MUTED);
        card.add(pLbl, gc);
        gc.gridx = 1;
        loginPassField = new JPasswordField(18);
        DiscordTheme.stylePasswordField(loginPassField);
        card.add(loginPassField, gc);

        gc.gridx = 0;
        gc.gridy++;
        gc.gridwidth = 2;
        registerCheckBox = new JCheckBox("Create new account (register)");
        registerCheckBox.setOpaque(false);
        registerCheckBox.setForeground(DiscordTheme.TEXT_NORMAL);
        registerCheckBox.setBackground(DiscordTheme.BG_SECONDARY);
        card.add(registerCheckBox, gc);

        gc.gridy++;
        loginErrorLabel = new JLabel(" ");
        loginErrorLabel.setForeground(COLOR_OFFLINE);
        card.add(loginErrorLabel, gc);

        gc.gridy++;
        JButton loginBtn = new JButton("Login");
        loginBtn.addActionListener(e -> attemptLogin());
        DiscordTheme.stylePrimaryButton(loginBtn);
        card.add(loginBtn, gc);

        wrap.add(card);
        return wrap;
    }

    private void attemptLogin() {
        loginErrorLabel.setText(" ");
        String u = loginUserField.getText().trim();
        String p = new String(loginPassField.getPassword());
        if (u.isEmpty() || p.isEmpty()) {
            loginErrorLabel.setText("Enter username and password.");
            return;
        }
        LoginRequest req = new LoginRequest(u, p, registerCheckBox.isSelected());
        if (client.connect(req)) {
            rootLayout.show(root, CARD_CHAT);
            setTitle("Chat - " + client.getUsername());
            sendButton.setEnabled(true);
            sendFileButton.setEnabled(true);
            appendInfoLine(getGeneralChatPane(), "Connected as " + client.getUsername());
        } else {
            loginErrorLabel.setText(registerCheckBox.isSelected()
                    ? "Registration failed or username already exists."
                    : "Invalid username or password, or user already online.");
        }
    }

    private JPanel buildChatRootPanel() {
        JPanel p = new JPanel(new BorderLayout(5, 5));
        p.setOpaque(true);
        p.setBackground(DiscordTheme.BG_MAIN);
        JPanel west = new JPanel(new BorderLayout(4, 4));
        west.setOpaque(true);
        west.setBackground(DiscordTheme.BG_SECONDARY);
        west.setPreferredSize(new Dimension(200, 0));
        west.setBorder(new EmptyBorder(5, 5, 5, 5));

        roomTitleLabel = new JLabel("Room: general", JLabel.CENTER);
        roomTitleLabel.setFont(new Font("Segoe UI", Font.BOLD, 12));
        roomTitleLabel.setForeground(DiscordTheme.TEXT_NORMAL);
        west.add(roomTitleLabel, BorderLayout.NORTH);

        roomListModel = new DefaultListModel<>();
        roomListDisplay = new JList<>(roomListModel);
        roomListDisplay.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        DiscordTheme.styleList(roomListDisplay);
        JScrollPane rs = new JScrollPane(roomListDisplay);
        DiscordTheme.styleRoomScroll(rs, "Rooms");
        west.add(rs, BorderLayout.CENTER);

        JPanel roomBtns = new JPanel(new GridLayout(3, 1, 4, 4));
        roomBtns.setOpaque(false);
        JButton createBtn = new JButton("Create room");
        createBtn.addActionListener(e -> {
            String name = JOptionPane.showInputDialog(this, "New room name (letters, numbers, - _):");
            if (name != null && !name.trim().isEmpty()) {
                client.sendRoomCommand(new RoomCommand(RoomCommand.Action.CREATE, name.trim()));
            }
        });
        JButton joinBtn = new JButton("Join Room");
        joinBtn.addActionListener(e -> {
            String sel = roomListDisplay.getSelectedValue();
            if (sel != null) {
                client.sendRoomCommand(new RoomCommand(RoomCommand.Action.JOIN, sel));
            }
        });
        JButton leaveBtn = new JButton("Leave Room");
        leaveBtn.addActionListener(e -> client.sendRoomCommand(new RoomCommand(RoomCommand.Action.LEAVE, "")));
        roomBtns.add(createBtn);
        roomBtns.add(joinBtn);
        roomBtns.add(leaveBtn);
        DiscordTheme.styleSecondaryButton(createBtn);
        DiscordTheme.styleSecondaryButton(joinBtn);
        DiscordTheme.styleSecondaryButton(leaveBtn);
        west.add(roomBtns, BorderLayout.SOUTH);

        p.add(west, BorderLayout.WEST);

        JPanel center = new JPanel(new BorderLayout(4, 4));
        center.setOpaque(true);
        center.setBackground(DiscordTheme.BG_MAIN);
        typingIndicatorLabel = new JLabel(" ", JLabel.CENTER);
        typingIndicatorLabel.setFont(new Font("Segoe UI", Font.ITALIC, 12));
        typingIndicatorLabel.setForeground(COLOR_TYPING);
        typingIndicatorLabel.setOpaque(true);
        typingIndicatorLabel.setBackground(DiscordTheme.BG_MAIN);
        center.add(typingIndicatorLabel, BorderLayout.NORTH);

        chatTabs = new JTabbedPane();
        DiscordTheme.styleTabbedPane(chatTabs);
        JTextPane generalPane = createChatPane();
        roomChatPanes.put(DEFAULT_ROOM_KEY, generalPane);
        JScrollPane generalScroll = new JScrollPane(generalPane);
        generalScroll.putClientProperty(ROOM_KEY_PROP, DEFAULT_ROOM_KEY);
        DiscordTheme.styleChatScroll(generalScroll, "Everyone");
        chatTabs.addTab(GENERAL_TAB_TITLE, generalScroll);
        chatTabs.addChangeListener(e -> onChatTabChanged());
        center.add(chatTabs, BorderLayout.CENTER);
        p.add(center, BorderLayout.CENTER);

        JPanel sidePanel = new JPanel(new BorderLayout(5, 5));
        sidePanel.setOpaque(true);
        sidePanel.setBackground(DiscordTheme.BG_SECONDARY);
        sidePanel.setPreferredSize(new Dimension(200, 0));
        sidePanel.setBorder(new EmptyBorder(5, 5, 5, 5));

        JPanel statusPanel = new JPanel(new GridLayout(3, 1, 5, 5));
        statusPanel.setOpaque(true);
        statusPanel.setBackground(DiscordTheme.BG_SECONDARY);
        DiscordTheme.applyTitledBorder(statusPanel, "Connection");
        serverInfoLabel = new JLabel("Server: --", JLabel.CENTER);
        serverInfoLabel.setForeground(DiscordTheme.TEXT_MUTED);
        statusLabel = new JLabel("● Offline", JLabel.CENTER);
        statusLabel.setForeground(COLOR_OFFLINE);
        statusPanel.add(serverInfoLabel);
        statusPanel.add(statusLabel);
        sidePanel.add(statusPanel, BorderLayout.NORTH);

        userListModel = new DefaultListModel<>();
        userListDisplay = new JList<>(userListModel);
        userListDisplay.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        DiscordTheme.styleList(userListDisplay);
        userListDisplay.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 1) {
                    String sel = userListDisplay.getSelectedValue();
                    if (sel != null && !sel.trim().isEmpty()) {
                        openDirectMessageTab(sel);
                    }
                }
            }
        });
        JScrollPane userScroll = new JScrollPane(userListDisplay);
        DiscordTheme.styleRoomScroll(userScroll, "Online (DM)");
        sidePanel.add(userScroll, BorderLayout.CENTER);
        p.add(sidePanel, BorderLayout.EAST);

        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        inputPanel.setOpaque(true);
        inputPanel.setBackground(DiscordTheme.BG_TERTIARY);
        inputPanel.setBorder(new EmptyBorder(8, 10, 8, 10));
        inputField = new JTextField();
        inputField.setFont(new Font("Segoe UI", Font.PLAIN, 14));
        DiscordTheme.styleTextField(inputField);
        inputField.addActionListener(e -> sendMessage());
        wireTypingIndicator();

        sendButton = new JButton("Send");
        sendButton.setEnabled(false);
        sendButton.addActionListener(e -> sendMessage());
        sendFileButton = new JButton("Send File");
        sendFileButton.setEnabled(false);
        sendFileButton.addActionListener(e -> startFileSend());
        DiscordTheme.stylePrimaryButton(sendButton);
        DiscordTheme.styleSecondaryButton(sendFileButton);

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 6, 0));
        buttonPanel.setOpaque(false);
        buttonPanel.setBackground(DiscordTheme.BG_TERTIARY);
        buttonPanel.add(sendFileButton);
        buttonPanel.add(sendButton);
        inputPanel.add(inputField, BorderLayout.CENTER);
        inputPanel.add(buttonPanel, BorderLayout.EAST);
        p.add(inputPanel, BorderLayout.SOUTH);

        root.setOpaque(true);
        root.setBackground(DiscordTheme.BG_MAIN);
        return p;
    }

    private JTextPane getGeneralChatPane() {
        return roomChatPanes.computeIfAbsent(DEFAULT_ROOM_KEY, k -> createChatPane());
    }

    private void onChatTabChanged() {
        String now = getSelectedRoomKeyFromTab();
        if (client.isConnected() && lastTypingRoomKey != null
                && (now == null || !lastTypingRoomKey.equalsIgnoreCase(now))) {
            client.sendTypingEvent(new TypingEvent(lastTypingRoomKey, client.getUsername(), false));
            lastTypingRoomKey = null;
        }
        if (now != null) {
            currentRoom = now;
            roomTitleLabel.setText("Room: " + currentRoom);
        }
        typingIndicatorLabel.setText(" ");
        remoteTypingUsers.clear();
    }

    /** @return room key when a room chat tab is selected; {@code null} for DM tabs */
    private String getSelectedRoomKeyFromTab() {
        int i = chatTabs.getSelectedIndex();
        if (i < 0) return null;
        Component c = chatTabs.getComponentAt(i);
        if (c instanceof JScrollPane) {
            Object k = ((JScrollPane) c).getClientProperty(ROOM_KEY_PROP);
            if (k instanceof String) return (String) k;
        }
        return null;
    }

    private String effectiveRoomKey(Message msg) {
        String r = msg.getRoomName();
        if (r == null || r.trim().isEmpty()) return DEFAULT_ROOM_KEY;
        return r.trim().toLowerCase();
    }

    private JTextPane ensureRoomTab(String roomKey) {
        if (roomKey == null || roomKey.isEmpty()) {
            roomKey = DEFAULT_ROOM_KEY;
        } else {
            String t = roomKey.trim().toLowerCase(Locale.ROOT);
            roomKey = t.isEmpty() ? DEFAULT_ROOM_KEY : t;
        }
        JTextPane hit = roomChatPanes.get(roomKey);
        if (hit != null) {
            return hit;
        }
        for (Map.Entry<String, JTextPane> e : roomChatPanes.entrySet()) {
            if (DEFAULT_ROOM_KEY.equals(e.getKey())) continue;
            if (roomKey.equalsIgnoreCase(e.getKey())) {
                JTextPane pane = roomChatPanes.remove(e.getKey());
                roomChatPanes.put(roomKey, pane);
                retitleRoomScrollToCanonicalRoomKey(e.getKey(), roomKey);
                return pane;
            }
        }
        JTextPane pane = createChatPane();
        roomChatPanes.put(roomKey, pane);
        JScrollPane sp = new JScrollPane(pane);
        sp.putClientProperty(ROOM_KEY_PROP, roomKey);
        DiscordTheme.styleChatScroll(sp, "#" + roomKey);
        chatTabs.addTab(tabTitleForRoom(roomKey), sp);
        return pane;
    }

    private void retitleRoomScrollToCanonicalRoomKey(String oldKey, String newKey) {
        for (int i = 0; i < chatTabs.getTabCount(); i++) {
            Component c = chatTabs.getComponentAt(i);
            if (!(c instanceof JScrollPane)) continue;
            JScrollPane sp = (JScrollPane) c;
            Object k = sp.getClientProperty(ROOM_KEY_PROP);
            if (k instanceof String && ((String) k).equals(oldKey)) {
                sp.putClientProperty(ROOM_KEY_PROP, newKey);
                chatTabs.setTitleAt(i, tabTitleForRoom(newKey));
                DiscordTheme.styleChatScroll(sp, "#" + newKey);
                return;
            }
        }
    }

    private static String tabTitleForRoom(String roomKey) {
        return DEFAULT_ROOM_KEY.equals(roomKey) ? GENERAL_TAB_TITLE : "#" + roomKey;
    }

    private int findTabIndexByRoomKey(String roomKey) {
        if (roomKey == null) return -1;
        String want = roomKey.trim().toLowerCase(Locale.ROOT);
        for (int i = 0; i < chatTabs.getTabCount(); i++) {
            Component c = chatTabs.getComponentAt(i);
            if (c instanceof JScrollPane) {
                Object k = ((JScrollPane) c).getClientProperty(ROOM_KEY_PROP);
                if (k instanceof String && want.equals(((String) k).trim().toLowerCase(Locale.ROOT))) {
                    return i;
                }
            }
        }
        return -1;
    }

    private void wireTypingIndicator() {
        inputField.getDocument().addDocumentListener(new DocumentListener() {
            private void ping() {
                if (suppressTypingPing || !client.isConnected()) return;
                String rk = getSelectedRoomKeyFromTab();
                if (rk == null) {
                    sendTypingFalse();
                    return;
                }
                rk = rk.trim().toLowerCase(Locale.ROOT);
                lastTypingRoomKey = rk;
                client.sendTypingEvent(new TypingEvent(rk, client.getUsername(), true));
                if (typingIdleTimer != null) {
                    typingIdleTimer.stop();
                }
                typingIdleTimer = new Timer(1200, ev -> sendTypingFalse());
                typingIdleTimer.setRepeats(false);
                typingIdleTimer.start();
            }

            @Override
            public void insertUpdate(DocumentEvent e) { ping(); }
            @Override
            public void removeUpdate(DocumentEvent e) { ping(); }
            @Override
            public void changedUpdate(DocumentEvent e) { ping(); }
        });
    }

    private void sendTypingFalse() {
        if (client.isConnected()) {
            String rk = lastTypingRoomKey != null ? lastTypingRoomKey : getSelectedRoomKeyFromTab();
            if (rk != null) {
                rk = rk.trim().toLowerCase(Locale.ROOT);
                client.sendTypingEvent(new TypingEvent(rk, client.getUsername(), false));
            }
            lastTypingRoomKey = null;
        }
    }

    private void rememberServerRoomsFromList(List<String> rooms) {
        synchronized (serverKnownRooms) {
            serverKnownRooms.clear();
            serverKnownRooms.add(DEFAULT_ROOM_KEY);
            if (rooms != null) {
                for (String r : rooms) {
                    if (r != null && !r.trim().isEmpty()) {
                        serverKnownRooms.add(r.trim().toLowerCase());
                    }
                }
            }
        }
    }

    private boolean isRoomKnownOnServer(String roomKey) {
        if (roomKey == null || roomKey.trim().isEmpty()) return false;
        synchronized (serverKnownRooms) {
            return serverKnownRooms.contains(roomKey.trim().toLowerCase());
        }
    }

    private void ensureChatHistoryLoadedOnce() {
        if (chatHistoryLoadedOnce) return;
        chatHistoryLoadedOnce = true;
        loadChatHistory();
    }

    /**
     * Removes room chat tabs that are not in the server's room list (DM tabs unchanged).
     */
    private void syncRoomTabsToServerRoomNames(List<String> serverRoomNames) {
        if (chatTabs == null) return;
        Set<String> valid = new HashSet<>();
        valid.add(DEFAULT_ROOM_KEY);
        if (serverRoomNames != null) {
            for (String r : serverRoomNames) {
                if (r != null && !r.trim().isEmpty()) {
                    valid.add(r.trim().toLowerCase());
                }
            }
        }
        for (int i = chatTabs.getTabCount() - 1; i >= 0; i--) {
            Component c = chatTabs.getComponentAt(i);
            if (!(c instanceof JScrollPane)) continue;
            Object k = ((JScrollPane) c).getClientProperty(ROOM_KEY_PROP);
            if (!(k instanceof String)) continue;
            String roomKey = ((String) k).trim().toLowerCase();
            if (DEFAULT_ROOM_KEY.equals(roomKey)) continue;
            if (!valid.contains(roomKey)) {
                roomChatPanes.remove(roomKey);
                chatTabs.remove(i);
            }
        }
        int sel = chatTabs.getSelectedIndex();
        if (sel < 0 || sel >= chatTabs.getTabCount()) {
            int g = findTabIndexByRoomKey(DEFAULT_ROOM_KEY);
            if (g >= 0) {
                chatTabs.setSelectedIndex(g);
                currentRoom = DEFAULT_ROOM_KEY;
                roomTitleLabel.setText("Room: " + currentRoom);
            }
        }
    }

    private void setupCallbacks() {
        client.setMessageListener(msg -> SwingUtilities.invokeLater(() -> appendMessage(msg)));
        client.setUserListListener(users -> SwingUtilities.invokeLater(() -> updateUserList(users)));
        client.setConnectionListener(connected -> SwingUtilities.invokeLater(() -> updateConnectionStatus(connected)));
        client.setFileTransferListener(ft -> SwingUtilities.invokeLater(() -> handleIncomingFileTransfer(ft)));
        client.setRoomListListener(upd -> SwingUtilities.invokeLater(() -> {
            rememberServerRoomsFromList(upd.getRoomNames());
            ensureChatHistoryLoadedOnce();
            syncRoomTabsToServerRoomNames(upd.getRoomNames());
            roomListModel.clear();
            for (String r : upd.getRoomNames()) {
                if (r != null && !r.trim().isEmpty()) {
                    roomListModel.addElement(r);
                }
            }
        }));
        client.setRoomStateListener(ev -> SwingUtilities.invokeLater(() -> {
            rememberServerRoomsFromList(ev.getAllRooms());
            ensureChatHistoryLoadedOnce();
            syncRoomTabsToServerRoomNames(ev.getAllRooms());
            String active = ev.getActiveRoom() == null || ev.getActiveRoom().isEmpty()
                    ? DEFAULT_ROOM_KEY : ev.getActiveRoom().trim().toLowerCase();
            if (!isRoomKnownOnServer(active)) {
                active = DEFAULT_ROOM_KEY;
            }
            currentRoom = active;
            roomTitleLabel.setText("Room: " + currentRoom);
            ensureRoomTab(active);
            int idx = findTabIndexByRoomKey(active);
            if (idx >= 0) {
                chatTabs.setSelectedIndex(idx);
            }
            roomListModel.clear();
            for (String r : ev.getAllRooms()) {
                if (r != null && !r.trim().isEmpty()) {
                    roomListModel.addElement(r);
                }
            }
        }));
        client.setTypingListener(ev -> SwingUtilities.invokeLater(() -> handleTypingEvent(ev)));
    }

    private void handleTypingEvent(TypingEvent ev) {
        if (client.getUsername().equals(ev.getUsername())) return;
        String evRoom = ev.getRoomName() == null ? "" : ev.getRoomName().trim().toLowerCase(Locale.ROOT);
        if (evRoom.isEmpty()) return;
        String viewRoom = getSelectedRoomKeyFromTab();
        if (viewRoom == null) return;
        viewRoom = viewRoom.trim().toLowerCase(Locale.ROOT);
        if (!evRoom.equals(viewRoom)) return;
        if (ev.isTyping()) {
            remoteTypingUsers.add(ev.getUsername());
        } else {
            remoteTypingUsers.remove(ev.getUsername());
        }
        if (remoteTypingUsers.isEmpty()) {
            typingIndicatorLabel.setText(" ");
        } else {
            typingIndicatorLabel.setText(String.join(", ", remoteTypingUsers) + " is typing…");
        }
    }

    private void sendMessage() {
        sendTypingFalse();
        String text = inputField.getText().trim();
        if (text.isEmpty()) return;

        String roomKey = getSelectedRoomKeyFromTab();
        if (roomKey != null) {
            client.sendMessage(text, Message.Type.BROADCAST, null, roomKey);
        } else {
            String recipient = extractUsernameFromTabTitle(chatTabs.getTitleAt(chatTabs.getSelectedIndex()));
            client.sendMessage(text, Message.Type.PRIVATE, recipient, null);
        }
        inputField.setText("");
        inputField.requestFocus();
    }

    private void startFileSend() {
        if (getSelectedRoomKeyFromTab() != null) {
            JOptionPane.showMessageDialog(this, "Open a DM tab (click a user) to send a file.", "Send File", JOptionPane.INFORMATION_MESSAGE);
            return;
        }
        String recipient = extractUsernameFromTabTitle(chatTabs.getTitleAt(chatTabs.getSelectedIndex()));
        if (recipient == null) return;
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) return;
        File file = chooser.getSelectedFile();
        if (file == null || !file.isFile()) return;
        JTextPane dmPane = directMessagePanes.get(recipient);
        new Thread(() -> uploadFileToUser(recipient, file, dmPane), "file-upload").start();
    }

    private void uploadFileToUser(String recipient, File file, JTextPane dmPane) {
        if (dmPane == null) return;
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
                        appendFileStatusEdt(dmPane, "[FILE] Upload ~" + pct + "%");
                    }
                }
            }
            client.sendFileTransfer(FileTransfer.end(me, recipient, transferId, file.getName(), len, totalChunks));
            appendFileStatusEdt(dmPane, "[FILE] Upload finished.");
            persistLine(dmTabKey(recipient), "file", me, recipient, "SENT " + file.getName());
        } catch (IOException ex) {
            SwingUtilities.invokeLater(() -> appendFileStatus(dmPane, "[FILE] Failed: " + ex.getMessage()));
        }
    }

    private void handleIncomingFileTransfer(FileTransfer ft) {
        if (!client.getUsername().equals(ft.getRecipient())) return;
        String other = ft.getSender();
        JTextPane dmPane = directMessagePanes.get(other);
        if (dmPane == null) {
            openDirectMessageTab(other);
            dmPane = directMessagePanes.get(other);
        }
        switch (ft.getPhase()) {
            case START:
                incomingFiles.put(ft.getTransferId(), new IncomingFileSession(ft.getFileName(), ft.getTotalSize(), ft.getTotalChunks(), other));
                appendFileStatus(dmPane, "[FILE] Incoming from " + other + ": " + ft.getFileName());
                break;
            case CHUNK:
                IncomingFileSession session = incomingFiles.get(ft.getTransferId());
                if (session == null) return;
                session.append(ft.getData());
                break;
            case END:
                IncomingFileSession s = incomingFiles.remove(ft.getTransferId());
                if (s != null) finishIncomingFile(dmPane, s);
                break;
            default:
                break;
        }
    }

    private void finishIncomingFile(JTextPane dmPane, IncomingFileSession session) {
        byte[] all = session.buffer.toByteArray();
        SwingUtilities.invokeLater(() -> {
            JFileChooser save = new JFileChooser();
            save.setSelectedFile(new File(session.fileName));
            if (save.showSaveDialog(this) != JFileChooser.APPROVE_OPTION) {
                appendFileStatus(dmPane, "[FILE] Save cancelled.");
                return;
            }
            try {
                Files.write(save.getSelectedFile().toPath(), all);
                appendFileStatus(dmPane, "[FILE] Saved: " + save.getSelectedFile().getAbsolutePath());
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
                if (msg.getRoomName() == null || msg.getRoomName().trim().isEmpty()) {
                    appendSystemLine(getGeneralChatPane(), timeStr + msg.getContent());
                    persistLine("group", "system", "SERVER", null, msg.getContent());
                } else {
                    String rk = effectiveRoomKey(msg);
                    if (DEFAULT_ROOM_KEY.equals(rk)) {
                        appendSystemLine(getGeneralChatPane(), timeStr + msg.getContent());
                        persistLine("group", "system", "SERVER", null, msg.getContent());
                    }
                }
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
        String rk = effectiveRoomKey(msg);
        // Server mirror line ("Server: …") only in General; never show in custom room tabs
        if (!DEFAULT_ROOM_KEY.equals(rk) && "Server".equals(msg.getSender())) {
            return;
        }
        JTextPane pane = DEFAULT_ROOM_KEY.equals(rk) ? getGeneralChatPane() : ensureRoomTab(rk);
        String body = StyleUtil.applyEmoticons(msg.getContent());
        boolean server = "Server".equals(msg.getSender());
        String sender = msg.getSender();
        boolean self = sender != null && sender.equals(client.getUsername());
        Color nameColor = server ? COLOR_SERVER : (self ? COLOR_SELF : StyleUtil.colorForUsername(sender));
        String speaker = server ? "Server" : (self ? "You" : sender);
        insertLineParts(pane,
                new LinePart(timeStr, COLOR_TIME, false, true),
                new LinePart("[" + speaker + "] ", nameColor, true, false),
                new LinePart(body, COLOR_BODY, false, false));
        if (saveHistory) {
            if (DEFAULT_ROOM_KEY.equals(rk)) {
                persistLine("group", "broadcast", sender == null ? "" : sender, null, msg.getContent());
            } else {
                persistLine("room:" + rk, "broadcast", sender == null ? "" : sender, null, msg.getContent());
            }
        }
    }

    private void handlePrivateMessage(Message msg, String timeStr, boolean saveHistory) {
        String me = client.getUsername();
        String other = msg.getSender().equals(me) ? msg.getRecipient() : msg.getSender();
        if (other == null || other.trim().isEmpty()) {
            appendSystemLine(getGeneralChatPane(), timeStr + "[DM] " + msg.getSender() + ": " + msg.getContent());
            return;
        }
        openDirectMessageTab(other);
        JTextPane dm = directMessagePanes.get(other);
        String body = StyleUtil.applyEmoticons(msg.getContent());
        boolean self = msg.getSender().equals(me);
        Color nameColor = self ? COLOR_SELF : StyleUtil.colorForUsername(msg.getSender());
        String speaker = self ? "You" : msg.getSender();
        insertLineParts(dm,
                new LinePart(timeStr, COLOR_TIME, false, true),
                new LinePart("[" + speaker + "] ", nameColor, true, false),
                new LinePart(body, COLOR_BODY, false, false));
        if (saveHistory) {
            persistLine(dmTabKey(other), "private", msg.getSender(), msg.getRecipient(), msg.getContent());
        }
    }

    private void appendInfoLine(JTextPane pane, String text) {
        appendPlain(pane, text, COLOR_INFO, false, true);
    }

    private void appendSystemLine(JTextPane pane, String text) {
        appendPlain(pane, text, COLOR_SYSTEM, false, true);
    }

    private void appendFileStatus(JTextPane pane, String text) {
        appendPlain(pane, text, new Color(0xEB, 0x45, 0x9E), false, true);
    }

    private void appendFileStatusEdt(JTextPane pane, String text) {
        if (SwingUtilities.isEventDispatchThread()) appendFileStatus(pane, text);
        else SwingUtilities.invokeLater(() -> appendFileStatus(pane, text));
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
            SimpleAttributeSet nl = new SimpleAttributeSet();
            StyleConstants.setForeground(nl, DiscordTheme.TEXT_MUTED);
            StyleConstants.setFontFamily(nl, "Segoe UI");
            StyleConstants.setFontSize(nl, 14);
            doc.insertString(doc.getLength(), "\n", nl);
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
        DiscordTheme.styleChatPane(pane);
        return pane;
    }

    private void openDirectMessageTab(String username) {
        if (!directMessagePanes.containsKey(username)) {
            JTextPane dm = createChatPane();
            directMessagePanes.put(username, dm);
            JScrollPane dmScroll = new JScrollPane(dm);
            DiscordTheme.styleDmScroll(dmScroll, username);
            chatTabs.addTab(getDirectMessageTabTitle(username), dmScroll);
        }
        chatTabs.setSelectedIndex(findTabIndexByTitle(getDirectMessageTabTitle(username)));
    }

    private void updateUserList(List<User> users) {
        userListModel.clear();
        for (User u : users) {
            if (!u.getUsername().equals(client.getUsername())) {
                userListModel.addElement(u.getUsername());
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
        for (User u : users) online.put(u.getUsername(), true);
        directMessagePanes.entrySet().removeIf(e -> {
            if (!online.containsKey(e.getKey())) {
                int idx = findTabIndexByTitle(getDirectMessageTabTitle(e.getKey()));
                if (idx >= 0) chatTabs.remove(idx);
                return true;
            }
            return false;
        });
    }

    private int findTabIndexByTitle(String title) {
        for (int i = 0; i < chatTabs.getTabCount(); i++) {
            if (title.equals(chatTabs.getTitleAt(i))) return i;
        }
        return -1;
    }

    private String getDirectMessageTabTitle(String username) {
        return "DM: " + username;
    }

    private String extractUsernameFromTabTitle(String tabTitle) {
        if (tabTitle != null && tabTitle.startsWith("DM: ")) {
            return tabTitle.substring(4).trim();
        }
        return null;
    }

    private String dmTabKey(String other) {
        return "dm:" + other;
    }

    private void persistLine(String tab, String type, String sender, String recipient, String content) {
        String user = client.getUsername();
        if (user == null) return;
        ChatHistoryStore.append(user, new ChatHistoryStore.HistoryLine(
                System.currentTimeMillis(), tab, sender == null ? "" : sender,
                recipient == null ? "" : recipient, type == null ? "" : type, content == null ? "" : content));
    }

    private void loadChatHistory() {
        suppressTypingPing = true;
        try {
            for (ChatHistoryStore.HistoryLine hl : ChatHistoryStore.load(client.getUsername())) {
                restoreHistoryLine(hl);
            }
        } finally {
            suppressTypingPing = false;
        }
    }

    private void restoreHistoryLine(ChatHistoryStore.HistoryLine hl) {
        if (hl.tab == null) return;
        if (hl.tab.startsWith("room:")) {
            String rkEarly = hl.tab.substring("room:".length()).trim().toLowerCase();
            if (!isRoomKnownOnServer(rkEarly)) return;
        }
        JTextPane target;
        if (hl.tab.startsWith("dm:")) {
            String other = hl.tab.substring(3);
            if (!directMessagePanes.containsKey(other)) {
                JTextPane dm = createChatPane();
                directMessagePanes.put(other, dm);
                JScrollPane dmScroll = new JScrollPane(dm);
                DiscordTheme.styleDmScroll(dmScroll, other);
                chatTabs.addTab(getDirectMessageTabTitle(other), dmScroll);
            }
            target = directMessagePanes.get(other);
        } else if (hl.tab.startsWith("room:")) {
            String rk = hl.tab.substring("room:".length());
            if ("system".equals(hl.type) && !DEFAULT_ROOM_KEY.equals(rk)) {
                return;
            }
            target = ensureRoomTab(rk);
        } else {
            target = getGeneralChatPane();
        }
        String timeStr = "[" + formatTime(hl.timestamp) + "] ";
        switch (hl.type) {
            case "system":
                appendSystemLine(target, timeStr + "[SYSTEM] " + hl.content);
                break;
            case "broadcast":
                if (hl.tab.startsWith("room:")) {
                    String roomKey = hl.tab.substring("room:".length());
                    appendBroadcastLine(new Message(hl.sender, null, hl.content, Message.Type.BROADCAST, roomKey), timeStr, false);
                } else {
                    appendBroadcastLine(new Message(hl.sender, null, hl.content, Message.Type.BROADCAST), timeStr, false);
                }
                break;
            case "private":
                Message pm = new Message(hl.sender, hl.recipient, hl.content, Message.Type.PRIVATE);
                handlePrivateMessage(pm, timeStr, false);
                break;
            case "file":
                appendFileStatus(target, timeStr + "[FILE] " + hl.content);
                break;
            default:
                appendPlain(target, timeStr + hl.content, DiscordTheme.TEXT_MUTED, false, false);
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
            if (data != null && data.length > 0) buffer.write(data, 0, data.length);
        }
    }

    public static void main(String[] args) {
        String host = "localhost";
        int port = 5000;
        if (args.length >= 1 && !args[0].trim().isEmpty()) host = args[0].trim();
        if (args.length >= 2) {
            try {
                port = Integer.parseInt(args[1]);
            } catch (NumberFormatException e) {
                System.err.println("Invalid port, using 5000.");
            }
        }
        String fh = host;
        int fp = port;
        SwingUtilities.invokeLater(() -> {
            DiscordTheme.installUiDefaults();
            ChatGUI g = new ChatGUI(fh, fp);
            g.setVisible(true);
        });
    }

    /**
     * Discord-inspired dark palette and Swing styling helpers.
     */
    private static final class DiscordTheme {
        static final Color BG_MAIN = new Color(0x36, 0x39, 0x3F);
        static final Color BG_SECONDARY = new Color(0x2F, 0x31, 0x36);
        static final Color BG_TERTIARY = new Color(0x20, 0x22, 0x25);
        static final Color BG_ELEVATED = new Color(0x40, 0x44, 0x4B);
        static final Color BG_CHAT = new Color(0x32, 0x35, 0x3B);
        static final Color TEXT_NORMAL = new Color(0xDC, 0xDD, 0xDE);
        static final Color TEXT_MUTED = new Color(0x72, 0x76, 0x7D);
        static final Color ACCENT = new Color(0x58, 0x65, 0xF2);
        static final Color BORDER = new Color(0x20, 0x22, 0x25);
        static final Color BORDER_STRONG = new Color(0x1A, 0x1B, 0x1E);

        private static Font uiFont(int style, int size) {
            return new Font("Segoe UI", style, size);
        }

        static void installUiDefaults() {
            UIManager.put("Panel.background", new ColorUIResource(BG_MAIN));
            UIManager.put("Label.foreground", new ColorUIResource(TEXT_NORMAL));
            UIManager.put("TextField.background", new ColorUIResource(BG_ELEVATED));
            UIManager.put("TextField.foreground", new ColorUIResource(TEXT_NORMAL));
            UIManager.put("TextField.caretForeground", new ColorUIResource(TEXT_NORMAL));
            UIManager.put("PasswordField.background", new ColorUIResource(BG_ELEVATED));
            UIManager.put("PasswordField.foreground", new ColorUIResource(TEXT_NORMAL));
            UIManager.put("TextPane.background", new ColorUIResource(BG_CHAT));
            UIManager.put("TextPane.foreground", new ColorUIResource(TEXT_NORMAL));
            UIManager.put("List.background", new ColorUIResource(BG_ELEVATED));
            UIManager.put("List.foreground", new ColorUIResource(TEXT_NORMAL));
            UIManager.put("List.selectionBackground", new ColorUIResource(ACCENT));
            UIManager.put("List.selectionForeground", new ColorUIResource(Color.WHITE));
            UIManager.put("ScrollPane.background", new ColorUIResource(BG_SECONDARY));
            UIManager.put("TabbedPane.background", new ColorUIResource(BG_SECONDARY));
            UIManager.put("TabbedPane.foreground", new ColorUIResource(TEXT_MUTED));
            UIManager.put("TabbedPane.selected", new ColorUIResource(BG_MAIN));
            UIManager.put("TabbedPane.contentAreaColor", new ColorUIResource(BG_MAIN));
            UIManager.put("TabbedPane.unselectedBackground", new ColorUIResource(BG_SECONDARY));
            UIManager.put("OptionPane.background", new ColorUIResource(BG_MAIN));
            UIManager.put("OptionPane.messageForeground", new ColorUIResource(TEXT_NORMAL));
            UIManager.put("CheckBox.background", new ColorUIResource(BG_SECONDARY));
            UIManager.put("CheckBox.foreground", new ColorUIResource(TEXT_NORMAL));
        }

        static void styleChatPane(JTextPane pane) {
            pane.setOpaque(true);
            pane.setBackground(BG_CHAT);
            pane.setForeground(TEXT_NORMAL);
            pane.setCaretColor(TEXT_NORMAL);
            pane.setSelectedTextColor(Color.WHITE);
            pane.setSelectionColor(new Color(ACCENT.getRed(), ACCENT.getGreen(), ACCENT.getBlue(), 180));
        }

        static void styleList(JList<?> list) {
            list.setOpaque(true);
            list.setBackground(BG_ELEVATED);
            list.setForeground(TEXT_NORMAL);
            list.setSelectionBackground(ACCENT);
            list.setSelectionForeground(Color.WHITE);
        }

        static void styleTextField(JTextField f) {
            f.setOpaque(true);
            f.setBackground(BG_ELEVATED);
            f.setForeground(TEXT_NORMAL);
            f.setCaretColor(TEXT_NORMAL);
            f.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER),
                    new EmptyBorder(6, 8, 6, 8)));
        }

        static void stylePasswordField(JPasswordField f) {
            f.setOpaque(true);
            f.setBackground(BG_ELEVATED);
            f.setForeground(TEXT_NORMAL);
            f.setCaretColor(TEXT_NORMAL);
            f.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER),
                    new EmptyBorder(6, 8, 6, 8)));
        }

        static void stylePrimaryButton(JButton b) {
            b.setUI(new BasicButtonUI());
            b.setOpaque(true);
            b.setContentAreaFilled(true);
            b.setBorderPainted(false);
            b.setBackground(ACCENT);
            b.setForeground(Color.WHITE);
            b.setFont(uiFont(Font.BOLD, 13));
            b.setBorder(BorderFactory.createEmptyBorder(8, 16, 8, 16));
            b.setFocusPainted(false);
        }

        static void styleSecondaryButton(JButton b) {
            b.setUI(new BasicButtonUI());
            b.setOpaque(true);
            b.setContentAreaFilled(true);
            b.setBorderPainted(true);
            b.setBackground(BG_ELEVATED);
            b.setForeground(TEXT_NORMAL);
            b.setFont(uiFont(Font.PLAIN, 12));
            b.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createLineBorder(BORDER),
                    new EmptyBorder(6, 10, 6, 10)));
            b.setFocusPainted(false);
        }

        static void styleTabbedPane(JTabbedPane tabs) {
            tabs.setOpaque(true);
            tabs.setBackground(BG_SECONDARY);
            tabs.setForeground(TEXT_MUTED);
            tabs.setFont(uiFont(Font.PLAIN, 12));
        }

        static void applyTitledBorder(JComponent c, String title) {
            TitledBorder tb = BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(BORDER),
                    title,
                    TitledBorder.LEFT,
                    TitledBorder.TOP,
                    uiFont(Font.BOLD, 11),
                    TEXT_MUTED);
            c.setBorder(tb);
        }

        static void styleChatScroll(JScrollPane sp, String title) {
            sp.setOpaque(true);
            sp.getViewport().setOpaque(true);
            sp.getViewport().setBackground(BG_CHAT);
            sp.setBackground(BG_SECONDARY);
            sp.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(BORDER),
                    title,
                    TitledBorder.LEFT,
                    TitledBorder.TOP,
                    uiFont(Font.BOLD, 12),
                    TEXT_MUTED));
        }

        static void styleRoomScroll(JScrollPane sp, String title) {
            sp.setOpaque(true);
            sp.getViewport().setOpaque(true);
            sp.getViewport().setBackground(BG_ELEVATED);
            sp.setBackground(BG_SECONDARY);
            sp.setBorder(BorderFactory.createTitledBorder(
                    BorderFactory.createLineBorder(BORDER),
                    title,
                    TitledBorder.LEFT,
                    TitledBorder.TOP,
                    uiFont(Font.BOLD, 11),
                    TEXT_MUTED));
        }

        static void styleDmScroll(JScrollPane sp, String peer) {
            styleChatScroll(sp, "@" + peer);
        }
    }
}
