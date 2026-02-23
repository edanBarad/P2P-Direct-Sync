package com.tactical.p2p;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;

/**
 * SyncBoardUI - Main UI with Messenger-style chat and Map.
 */
public class SyncBoardUI {

    private static final String TITLE_PREFIX = "P2P Shared Board - ";
    private static final int STATUS_BAR_HEIGHT = 30;

    private JFrame frame;
    private JTabbedPane tabbedPane;

    // Chat components
    private JTextPane chatPane;
    private StyledDocument chatDoc;
    private JTextField messageInput;
    private JScrollPane chatScrollPane;

    // Style attributes for chat
    private Style hostStyle;
    private Style clientStyle;
    private Style systemStyle;

    // Map components
    private MapScreen mapScreen;

    // Status bar
    private JLabel statusLabel;
    private JLabel roleLabel;

    // Dependencies
    private final StateModel stateModel;
    private final NetworkManager networkManager;
    private boolean isHost;
    private boolean isUpdatingFromNetwork = false;

    public SyncBoardUI(StateModel stateModel, NetworkManager networkManager) {
        if (stateModel == null || networkManager == null) {
            throw new IllegalArgumentException("StateModel and NetworkManager cannot be null");
        }
        this.stateModel = stateModel;
        this.networkManager = networkManager;
    }

    public void initialize(String role) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> initialize(role));
            return;
        }

        this.isHost = "HOST".equals(role);

        frame = new JFrame(TITLE_PREFIX + role);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) { shutdown(); }
        });

        tabbedPane = new JTabbedPane();

        // Create Chat tab
        JPanel chatPanel = createChatPanel();
        tabbedPane.addTab("Chat", chatPanel);

        // Create Map tab
        JPanel mapPanelWrapper = createMapPanel();
        tabbedPane.addTab("Map", mapPanelWrapper);

        frame.add(tabbedPane, BorderLayout.CENTER);
        createStatusBar(role);
        setupNetworkCallbacks();
        setupStateListener();

        frame.setSize(700, 550);
        frame.setLocationRelativeTo(null);
        frame.setMinimumSize(new Dimension(500, 400));
        frame.setVisible(true);

        messageInput.requestFocusInWindow();

        // Add welcome message
        addSystemMessage("Connected as " + role);
    }

    // ==================== Chat Panel ====================

    private JPanel createChatPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Chat display area
        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatPane.setFont(new Font("Arial", Font.PLAIN, 14));
        chatPane.setMargin(new Insets(10, 10, 10, 10));

        // Initialize styles
        chatDoc = chatPane.getStyledDocument();
        initStyles();

        chatScrollPane = new JScrollPane(chatPane);
        chatScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        chatScrollPane.setBorder(BorderFactory.createTitledBorder("Messages"));

        // Message input
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));
        messageInput = new JTextField();
        messageInput.setFont(new Font("Arial", Font.PLAIN, 14));
        messageInput.addActionListener(e -> sendMessage());

        JButton sendBtn = new JButton("Send");
        sendBtn.addActionListener(e -> sendMessage());

        inputPanel.add(messageInput, BorderLayout.CENTER);
        inputPanel.add(sendBtn, BorderLayout.EAST);

        panel.add(chatScrollPane, BorderLayout.CENTER);
        panel.add(inputPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void initStyles() {
        hostStyle = chatDoc.addStyle("HostStyle", null);
        StyleConstants.setForeground(hostStyle, new Color(30, 144, 255));
        StyleConstants.setBold(hostStyle, true);

        clientStyle = chatDoc.addStyle("ClientStyle", null);
        StyleConstants.setForeground(clientStyle, new Color(220, 20, 60));
        StyleConstants.setBold(clientStyle, true);

        systemStyle = chatDoc.addStyle("SystemStyle", null);
        StyleConstants.setForeground(systemStyle, new Color(128, 128, 128));
        StyleConstants.setItalic(systemStyle, true);

        Style regularStyle = chatDoc.addStyle("RegularStyle", null);
        StyleConstants.setForeground(regularStyle, Color.BLACK);
    }

    private void sendMessage() {
        String text = messageInput.getText().trim();
        if (text.isEmpty()) return;

        String sender = isHost ? "Host" : "Client";
        addMessage(sender, text, isHost);

        messageInput.setText("");

        // Send to network
        if (networkManager.isConnected()) {
            networkManager.sendText("MSG:" + sender + ":" + text);
        }
    }

    public void addMessage(String sender, String text, boolean fromHost) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Add sender name
                chatDoc.insertString(chatDoc.getLength(), sender + ": ", fromHost ? hostStyle : clientStyle);
                // Add message text
                chatDoc.insertString(chatDoc.getLength(), text + "\n", chatDoc.getStyle("RegularStyle"));
                // Scroll to bottom
                chatPane.setCaretPosition(chatDoc.getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    public void addSystemMessage(String text) {
        SwingUtilities.invokeLater(() -> {
            try {
                chatDoc.insertString(chatDoc.getLength(), "[System] " + text + "\n", systemStyle);
                chatPane.setCaretPosition(chatDoc.getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    // ==================== Map Panel ====================

    private JPanel createMapPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());

        mapScreen = new MapScreen();
        mapScreen.setIsHost(isHost);

        // Top panel with buttons
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        JButton findPathBtn = new JButton("Find Path");
        JButton removePathBtn = new JButton("Remove Path");
        JButton clearAllBtn = new JButton("Clear All Paths");

        findPathBtn.addActionListener(e -> mapScreen.startPathSelection());
        removePathBtn.addActionListener(e -> mapScreen.startPathRemoval());
        clearAllBtn.addActionListener(e -> mapScreen.clearAllPaths());

        topPanel.add(findPathBtn);
        topPanel.add(removePathBtn);
        topPanel.add(clearAllBtn);

        JLabel instructions = new JLabel("Click: add point | Click own point → another: add edge | Right-click: delete");
        instructions.setForeground(Color.DARK_GRAY);
        topPanel.add(Box.createHorizontalStrut(10));
        topPanel.add(instructions);

        wrapper.add(topPanel, BorderLayout.NORTH);
        wrapper.add(mapScreen, BorderLayout.CENTER);

        // Point callbacks
        mapScreen.setOnPointAdded(point -> {
            if (networkManager.isConnected()) {
                networkManager.sendPoint(point, NetworkManager.PointMessage.Action.ADD, isHost);
            }
        });

        mapScreen.setOnPointRemoved(point -> {
            if (networkManager.isConnected()) {
                networkManager.sendPoint(point, NetworkManager.PointMessage.Action.REMOVE, isHost);
            }
        });

        // Edge callback
        mapScreen.setOnEdgeAdded(edgeInfo -> {
            if (networkManager.isConnected()) {
                networkManager.sendEdge(edgeInfo.x1, edgeInfo.y1, edgeInfo.x2, edgeInfo.y2);
            }
        });

        // Path found callback
        mapScreen.setOnPathFound(pathInfo -> {
            if (networkManager.isConnected()) {
                networkManager.sendPath(pathInfo.sourceX, pathInfo.sourceY, pathInfo.destX, pathInfo.destY);
                String sender = isHost ? "Host" : "Client";
                addMessage(sender, "Found a path with length " + (int) pathInfo.length, isHost);
                networkManager.sendText("MSG:" + sender + ":Found a path with length " + (int) pathInfo.length);
            }
        });

        // Path removed callback
        mapScreen.setOnPathRemoved(pathIndex -> {
            if (networkManager.isConnected()) {
                networkManager.sendPathRemove(pathIndex);
            }
        });

        // Message callback (for path removal notification)
        mapScreen.setOnMessage(msg -> {
            String sender = isHost ? "Host" : "Client";
            addMessage(sender, msg, isHost);
            if (networkManager.isConnected()) {
                networkManager.sendText("MSG:" + sender + ":" + msg);
            }
        });

        return wrapper;
    }

    // ==================== Status Bar ====================

    private void createStatusBar(String role) {
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setPreferredSize(new Dimension(0, STATUS_BAR_HEIGHT));
        statusPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        statusLabel = new JLabel("Status: Disconnected");
        statusLabel.setForeground(Color.GRAY);

        roleLabel = new JLabel("Role: " + role);
        roleLabel.setForeground(new Color(70, 130, 180));

        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(roleLabel, BorderLayout.EAST);

        frame.add(new JSeparator(JSeparator.HORIZONTAL), BorderLayout.SOUTH);
        frame.add(statusPanel, BorderLayout.PAGE_END);
    }

    // ==================== Network Callbacks ====================

    private void setupNetworkCallbacks() {
        networkManager.setOnTextReceived(text -> {
            // Parse message format: MSG:sender:text
            if (text.startsWith("MSG:")) {
                String[] parts = text.split(":", 3);
                if (parts.length >= 3) {
                    String sender = parts[1];
                    String msgText = parts[2];
                    addMessage(sender, msgText, sender.equals("Host"));
                }
            }
        });

        networkManager.setOnPointReceived(pointMsg -> {
            if (mapScreen != null) {
                if (pointMsg.getAction() == NetworkManager.PointMessage.Action.ADD) {
                    mapScreen.addRemotePoint(pointMsg.getX(), pointMsg.getY(), pointMsg.isFromHost());
                } else {
                    mapScreen.removeRemotePoint(pointMsg.getX(), pointMsg.getY());
                }
            }
        });

        networkManager.setOnPathReceived(pathMsg -> {
            if (mapScreen != null) {
                mapScreen.setRemotePath(pathMsg.getSourceX(), pathMsg.getSourceY(),
                                        pathMsg.getDestX(), pathMsg.getDestY(), 0);
            }
        });

        networkManager.setOnEdgeReceived(edgeMsg -> {
            if (mapScreen != null) {
                mapScreen.addRemoteEdge(edgeMsg.getX1(), edgeMsg.getY1(),
                                        edgeMsg.getX2(), edgeMsg.getY2());
            }
        });

        networkManager.setOnPathRemoved(pathIndex -> {
            if (mapScreen != null) {
                mapScreen.removeRemotePath(pathIndex);
            }
        });

        networkManager.setOnStatusChanged(status -> updateConnectionStatus(status));
    }

    private void setupStateListener() {
        stateModel.addListener(model -> SwingUtilities.invokeLater(() -> {
            if (model.isConnected()) {
                statusLabel.setText("Status: Connected");
                statusLabel.setForeground(new Color(0, 128, 0));
            } else {
                statusLabel.setText("Status: Disconnected");
                statusLabel.setForeground(Color.GRAY);
            }
        }));
    }

    private void updateConnectionStatus(NetworkManager.ConnectionStatus status) {
        switch (status) {
            case CONNECTED:
                statusLabel.setText("Status: Connected");
                statusLabel.setForeground(new Color(0, 128, 0));
                break;
            case DISCONNECTED:
                statusLabel.setText("Status: Disconnected");
                statusLabel.setForeground(Color.GRAY);
                break;
            case CONNECTING:
                statusLabel.setText("Status: Connecting...");
                statusLabel.setForeground(Color.ORANGE);
                break;
            case WAITING:
                statusLabel.setText("Status: Waiting for peer...");
                statusLabel.setForeground(Color.ORANGE);
                break;
            case ERROR:
                statusLabel.setText("Status: Connection Error");
                statusLabel.setForeground(Color.RED);
                break;
        }
    }

    public void shutdown() {
        System.out.println("[SyncBoardUI] Shutting down...");
        networkManager.shutdown();
        if (frame != null) frame.dispose();
    }

    public JFrame getFrame() { return frame; }
}
