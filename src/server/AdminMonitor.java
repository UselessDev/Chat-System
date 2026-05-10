package server;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.util.List;
import java.util.Map;

/**
 * Real-time administrator dashboard: connected users and worker thread names.
 */
public class AdminMonitor extends JFrame {

    private final ChatServer server;
    private final Runnable refreshRunnable = this::refreshFromServerThread;
    private final DefaultListModel<String> usersModel = new DefaultListModel<>();
    private final DefaultListModel<String> threadsModel = new DefaultListModel<>();
    private final JLabel poolLabel = new JLabel("Thread pool: --", JLabel.CENTER);

    public AdminMonitor(ChatServer server) {
        this.server = server;
        setTitle("Chat Server — Admin Monitor");
        setSize(520, 420);
        setLocationRelativeTo(null);

        JPanel root = new JPanel(new BorderLayout(8, 8));
        root.setBorder(new EmptyBorder(10, 10, 10, 10));

        JLabel header = new JLabel("Live connection overview", JLabel.CENTER);
        header.setFont(new Font("Segoe UI", Font.BOLD, 16));
        root.add(header, BorderLayout.NORTH);

        JPanel lists = new JPanel(new GridLayout(1, 2, 12, 0));

        JList<String> usersList = new JList<>(usersModel);
        usersList.setFont(new Font("Consolas", Font.PLAIN, 13));
        JScrollPane usersScroll = new JScrollPane(usersList);
        usersScroll.setBorder(BorderFactory.createTitledBorder("Connected users"));

        JList<String> threadsList = new JList<>(threadsModel);
        threadsList.setFont(new Font("Consolas", Font.PLAIN, 13));
        JScrollPane threadsScroll = new JScrollPane(threadsList);
        threadsScroll.setBorder(BorderFactory.createTitledBorder("Client handler threads"));

        lists.add(usersScroll);
        lists.add(threadsScroll);
        root.add(lists, BorderLayout.CENTER);

        poolLabel.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        root.add(poolLabel, BorderLayout.SOUTH);

        add(root);

        server.addServerStateListener(refreshRunnable);
        Timer timer = new Timer(400, e -> refreshFromServerThread());
        timer.start();

        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                timer.stop();
                server.removeServerStateListener(refreshRunnable);
            }
        });

        setVisible(true);
        refreshFromServerThread();
    }

    private void refreshFromServerThread() {
        List<String> users = server.getConnectedUsernames();
        Map<String, String> threads = server.getUserThreadDisplayNames();
        int active = server.getActivePoolThreads();

        SwingUtilities.invokeLater(() -> {
            usersModel.clear();
            for (String u : users) {
                usersModel.addElement("• " + u);
            }
            threadsModel.clear();
            for (Map.Entry<String, String> e : threads.entrySet()) {
                threadsModel.addElement(e.getValue() + "  →  " + e.getKey());
            }
            if (active >= 0) {
                poolLabel.setText("Cached thread pool — active worker threads: " + active);
            } else {
                poolLabel.setText("Cached thread pool — status: running");
            }
        });
    }
}
