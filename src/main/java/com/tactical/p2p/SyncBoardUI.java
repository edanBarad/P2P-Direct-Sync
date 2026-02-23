package com.tactical.p2p;

import javax.swing.*;
import javax.swing.border.*;
import javax.swing.text.*;
import java.awt.*;
import java.awt.event.*;
import java.io.File;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * SyncBoardUI - Main UI with Messenger-style chat, Map, Security features, and Audit Log.
 */
public class SyncBoardUI {

    private static final String TITLE_PREFIX = "P2P Shared Board - ";
    private static final int STATUS_BAR_HEIGHT = 30;

    // UI Components
    private JFrame frame;
    private JTabbedPane tabbedPane;

    // Chat components
    private JTextPane chatPane;
    private StyledDocument chatDoc;
    private JTextField messageInput;
    private JScrollPane chatScrollPane;
    private JComboBox<String> priorityCombo;
    private JTextField selfDestructField;

    // Styles
    private Style hostStyle;
    private Style clientStyle;
    private Style systemStyle;
    private Style alertStyle;
    private Style criticalStyle;

    // Map
    private MapScreen mapScreen;

    // Check-in system
    private CheckInManager checkInManager;

    // Status
    private JLabel statusLabel;
    private JLabel roleLabel;

    // Audit log display
    private JTextArea logArea;

    // Dependencies
    private final StateModel stateModel;
    private final NetworkManager networkManager;
    private final AuditLogger auditLogger;
    private boolean isHost;
    private boolean isUpdatingFromNetwork = false;

    // Self-destructing messages: map of id -> timer
    private Map<Integer, javax.swing.Timer> selfDestructTimers = new HashMap<>();
    private Map<Integer, Integer> messagePositions = new HashMap<>();

    public SyncBoardUI(StateModel stateModel, NetworkManager networkManager) {
        if (stateModel == null || networkManager == null) {
            throw new IllegalArgumentException("StateModel and NetworkManager cannot be null");
        }
        this.stateModel = stateModel;
        this.networkManager = networkManager;
        this.auditLogger = new AuditLogger("Unknown");
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
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });

        tabbedPane = new JTabbedPane();

        // Create Chat tab
        JPanel chatPanel = createChatPanel();
        tabbedPane.addTab("Chat", chatPanel);

        // Create Map tab
        JPanel mapPanelWrapper = createMapPanel();
        tabbedPane.addTab("Map", mapPanelWrapper);

        // Create Audit Log tab
        JPanel auditPanel = createAuditPanel();
        tabbedPane.addTab("Audit Log", auditPanel);

        // Create Check-in tab
        JPanel checkInPanel = createCheckInPanel();
        tabbedPane.addTab("Check-in", checkInPanel);

        frame.add(tabbedPane, BorderLayout.CENTER);
        createStatusBar(role);
        setupNetworkCallbacks();
        setupStateListener();

        frame.setSize(800, 600);
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);

        messageInput.requestFocusInWindow();
        addSystemMessage("Connected as " + role);
        auditLogger.log(AuditLogger.EventType.CONNECTION, "User connected", "Role: " + role);
    }

    // ==================== Chat Panel ====================

    private JPanel createChatPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Chat display
        chatPane = new JTextPane();
        chatPane.setEditable(false);
        chatPane.setFont(new Font("Arial", Font.PLAIN, 14));
        chatPane.setMargin(new Insets(10, 10, 10, 10));

        chatDoc = chatPane.getStyledDocument();
        initStyles();

        chatScrollPane = new JScrollPane(chatPane);
        chatScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);
        chatScrollPane.setBorder(BorderFactory.createTitledBorder("Messages"));

        panel.add(chatScrollPane, BorderLayout.CENTER);

        // Input panel with priority and Self-destruct
        JPanel inputPanel = new JPanel(new BorderLayout(5, 0));

        JPanel controlsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));

        // Priority selector
        controlsPanel.add(new JLabel("Priority:"));
        priorityCombo = new JComboBox<>(new String[]{"Normal", "Alert", "Critical"});
        priorityCombo.setSelectedIndex(0);
        controlsPanel.add(priorityCombo);

        // Self-destruct field
        controlsPanel.add(new JLabel("  | Self-destruct (sec):"));
        selfDestructField = new JTextField(5);
        selfDestructField.setText("");
        controlsPanel.add(selfDestructField);

        inputPanel.add(controlsPanel, BorderLayout.NORTH);

        // Message input and send button
        JPanel messagePanel = new JPanel(new BorderLayout(5, 0));
        messageInput = new JTextField();
        messageInput.setFont(new Font("Arial", Font.PLAIN, 14));
        messageInput.addActionListener(e -> sendMessage());

        JButton sendBtn = new JButton("Send");
        sendBtn.addActionListener(e -> sendMessage());
        messagePanel.add(messageInput, BorderLayout.CENTER);
        messagePanel.add(sendBtn, BorderLayout.EAST);

        inputPanel.add(messagePanel, BorderLayout.CENTER);

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

        alertStyle = chatDoc.addStyle("AlertStyle", null);
        StyleConstants.setForeground(alertStyle, new Color(255, 140, 0));
        StyleConstants.setBold(alertStyle, true);

        criticalStyle = chatDoc.addStyle("CriticalStyle", null);
        StyleConstants.setForeground(criticalStyle, Color.RED);
        StyleConstants.setBold(criticalStyle, true);
        StyleConstants.setFontSize(criticalStyle, 16);
    }

    private void sendMessage() {
        String text = messageInput.getText().trim();
        if (text.isEmpty()) return;

        // Get priority
        int priorityIndex = priorityCombo.getSelectedIndex();
        String priority = priorityIndex == 0 ? "NORMAL" : priorityIndex == 1 ? "ALERT" : "CRITICAL";

        // Get self-destruct time
        int selfDestructSecs = 0;
        try {
            String sdText = selfDestructField.getText().trim();
            if (!sdText.isEmpty()) {
                selfDestructSecs = Integer.parseInt(sdText);
                if (selfDestructSecs < 1 || selfDestructSecs > 300) {
                    JOptionPane.showMessageDialog(frame,
                        "Self-destruct must be between 1 and 300 seconds",
                        "Invalid Self-destruct Time", JOptionPane.WARNING_MESSAGE);
                    return;
                }
            }
        } catch (NumberFormatException e) {
            JOptionPane.showMessageDialog(frame,
                "Self-destruct must be a number",
                "Invalid Input", JOptionPane.ERROR_MESSAGE);
            return;
        }

        String sender = isHost ? "Host" : "Client";

        // Send to network with format: MSG:sender:priority:text:sd
        if (networkManager.isConnected()) {
            String message = "MSG:" + sender + ":" + priority + ":" + text + ":" + selfDestructSecs;
            networkManager.sendText(message);
        }

        // Display message locally
        addMessage(sender, text, isHost, priority, selfDestructSecs);

        // Log to audit
        String prefix = priority.equals("ALERT") ? "[ALERT] " : priority.equals("CRITICAL") ? "[CRITICAL] " : "";
        auditLogger.log(AuditLogger.EventType.MESSAGE_SENT,
            "Message sent by " + sender, ": " + prefix + text);

        // Handle critical alert visual effect
        if (priority.equals("CRITICAL")) {
            flashScreen(Color.RED, 3);
            playAlertSound();
        } else if (priority.equals("ALERT")) {
            flashScreen(Color.ORANGE, 2);
        }

        messageInput.setText("");
        selfDestructField.setText("");
    }

    public void addMessage(String sender, String text, boolean fromHost) {
        addMessage(sender, text, fromHost, "NORMAL", 0);
    }

    public void addMessage(String sender, String text, boolean fromHost, String priority, int selfDestructSecs) {
        SwingUtilities.invokeLater(() -> {
            try {
                // Add sender name
                Style senderStyle = fromHost ? hostStyle : clientStyle;
                chatDoc.insertString(chatDoc.getLength(), sender + ": ", senderStyle);

                // Add priority prefix if applicable
                Style textStyle = chatDoc.getStyle("RegularStyle");
                if (priority.equals("ALERT")) {
                    chatDoc.insertString(chatDoc.getLength(), "[ALERT] ", alertStyle);
                    textStyle = alertStyle;
                } else if (priority.equals("CRITICAL")) {
                    chatDoc.insertString(chatDoc.getLength(), "[CRITICAL] ", criticalStyle);
                    textStyle = criticalStyle;
                }

                // Add text
                chatDoc.insertString(chatDoc.getLength(), text, textStyle);

                // Add self-destruct indicator
                if (selfDestructSecs > 0) {
                    Style sdStyle = chatDoc.addStyle("SDStyle", null);
                    StyleConstants.setForeground(sdStyle, Color.GRAY);
                    StyleConstants.setFontSize(sdStyle, 10);
                    chatDoc.insertString(chatDoc.getLength(), " [SD: " + selfDestructSecs + "s]\n", sdStyle);

                    // Schedule self-destruct
                    final int messageStart = chatDoc.getLength();
                    int messageId = System.identityHashCode(text);
                    javax.swing.Timer timer = new javax.swing.Timer(selfDestructSecs * 1000, e -> {
                        addSystemMessage("[Message self-destructed]");
                        auditLogger.log(AuditLogger.EventType.SELF_DESTRUCT, "Message self-destructed");
                    });
                    timer.setRepeats(false);
                    timer.start();
                    selfDestructTimers.put(messageId, timer);
                } else {
                    chatDoc.insertString(chatDoc.getLength(), "\n", chatDoc.getStyle("RegularStyle"));
                }

                chatPane.setCaretPosition(chatDoc.getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
    }

    private void flashScreen(Color color, int times) {
        final Color originalBg = chatPane.getBackground();
        javax.swing.Timer flashTimer = new javax.swing.Timer(200, null);
        final int[] count = {0};
        flashTimer.addActionListener(e -> {
            if (count[0] % 2 == 0) {
                chatPane.setBackground(color);
            } else {
                chatPane.setBackground(originalBg);
            }
            count[0]++;
            if (count[0] >= times * 2) {
                flashTimer.stop();
                chatPane.setBackground(originalBg);
            }
        });
        flashTimer.start();
    }

    private void playAlertSound() {
        Toolkit.getDefaultToolkit().beep();
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

    public void addAlertMessage(String title, String text, boolean fromHost) {
        SwingUtilities.invokeLater(() -> {
            try {
                chatDoc.insertString(chatDoc.getLength(), "[" + title + "] ", criticalStyle);
                chatDoc.insertString(chatDoc.getLength(), text + "\n", alertStyle);
                chatPane.setCaretPosition(chatDoc.getLength());
            } catch (BadLocationException e) {
                e.printStackTrace();
            }
        });
        flashScreen(Color.RED, 3);
        playAlertSound();
    }

    // ==================== Map Panel ====================

    private JPanel createMapPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());

        mapScreen = new MapScreen();
        mapScreen.setIsHost(isHost);
        mapScreen.setAuditLogger(auditLogger);

        // Top panel with buttons
        JPanel topPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 2, 2));

        JButton findPathBtn = new JButton("Find Path");
        findPathBtn.addActionListener(e -> mapScreen.startPathSelection());
        topPanel.add(findPathBtn);

        JButton removePathBtn = new JButton("Remove Path");
        removePathBtn.addActionListener(e -> mapScreen.startPathRemoval());
        topPanel.add(removePathBtn);

        JButton clearBtn = new JButton("Clear Paths");
        clearBtn.addActionListener(e -> mapScreen.clearAllPaths());
        topPanel.add(clearBtn);

        // Zone buttons
        topPanel.add(Box.createHorizontalStrut(10));
        JButton zoneRectBtn = new JButton("Add Zone");
        zoneRectBtn.addActionListener(e -> mapScreen.startZoneDrawing());
        topPanel.add(zoneRectBtn);

        JButton zoneCircleBtn = new JButton("Add Circle Zone");
        zoneCircleBtn.addActionListener(e -> mapScreen.startCircleZoneDrawing());
        topPanel.add(zoneCircleBtn);

        JButton clearZonesBtn = new JButton("Clear Zones");
        clearZonesBtn.addActionListener(e -> mapScreen.clearZones());
        topPanel.add(clearZonesBtn);

        // Patrol buttons
        topPanel.add(Box.createHorizontalStrut(10));
        JButton patrolBtn = new JButton("Create Patrol");
        patrolBtn.addActionListener(e -> mapScreen.startPatrolCreation());
        topPanel.add(patrolBtn);

        JButton startPatrolBtn = new JButton("Start Patrol");
        startPatrolBtn.addActionListener(e -> mapScreen.startPatrol());
        topPanel.add(startPatrolBtn);

        JButton endPatrolBtn = new JButton("End Patrol");
        endPatrolBtn.addActionListener(e -> mapScreen.endPatrol());
        topPanel.add(endPatrolBtn);

        wrapper.add(topPanel, BorderLayout.NORTH);
        wrapper.add(mapScreen, BorderLayout.CENTER);

        // === Callbacks ===

        mapScreen.setOnPointAdded(point -> {
            if (networkManager.isConnected()) {
                networkManager.sendPoint(point, NetworkManager.PointMessage.Action.ADD, isHost);
            }
            auditLogger.log(AuditLogger.EventType.POINT_ADDED, "Point added", point.toString());
        });

        mapScreen.setOnPointRemoved(point -> {
            if (networkManager.isConnected()) {
                networkManager.sendPoint(point, NetworkManager.PointMessage.Action.REMOVE, isHost);
            }
            auditLogger.log(AuditLogger.EventType.POINT_REMOVED, "Point removed", point.toString());
        });

        mapScreen.setOnEdgeAdded(edge -> {
            if (networkManager.isConnected()) {
                networkManager.sendEdge(edge.x1, edge.y1, edge.x2, edge.y2);
            }
            auditLogger.log(AuditLogger.EventType.EDGE_ADDED, "Edge added", edge.toString());
        });

        mapScreen.setOnPathFound(pathInfo -> {
            if (networkManager.isConnected()) {
                networkManager.sendPath(pathInfo.sourceX, pathInfo.sourceY, pathInfo.destX, pathInfo.destY);
            }
            addSystemMessage("Path found with length: " + (int) pathInfo.length + "px");
            auditLogger.log(AuditLogger.EventType.PATH_FOUND, "Path found", "Length: " + (int) pathInfo.length);
        });

        mapScreen.setOnPathRemoved(idx -> {
            if (networkManager.isConnected()) {
                networkManager.sendPathRemove(idx);
            }
            auditLogger.log(AuditLogger.EventType.PATH_REMOVED, "Path removed", "Index: " + idx);
        });

        mapScreen.setOnZoneTriggered(zoneInfo -> {
            addAlertMessage("GEOFENCE ALERT", "Zone triggered: " + zoneInfo.name, isHost);
            auditLogger.log(AuditLogger.EventType.ZONE_TRIGGERED, "Zone triggered", zoneInfo.name);
        });

        mapScreen.setOnPatrolStarted(patrolInfo -> {
            addSystemMessage("Patrol started: " + patrolInfo.name);
            auditLogger.log(AuditLogger.EventType.PATROL_STARTED, "Patrol started", patrolInfo.toString());
        });

        mapScreen.setOnPatrolProgress((index, total) -> {
            addSystemMessage("Patrol checkpoint " + index + "/" + total);
            auditLogger.log(AuditLogger.EventType.PATROL_PROGRESS, "Patrol progress", "Checkpoint: " + index);
        });

        mapScreen.setOnPatrolCompleted(patrolInfo -> {
            addSystemMessage("Patrol completed: " + patrolInfo.name + " duration: " + (int) patrolInfo.duration + "s");
            auditLogger.log(AuditLogger.EventType.PATROL_COMPLETED, "Patrol completed", patrolInfo.toString());
        });

        return wrapper;
    }

    // ==================== Audit Log Panel ====================

    private JPanel createAuditPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Log display
        logArea = new JTextArea();
        logArea.setEditable(false);
        logArea.setFont(new Font("Monospaced", Font.PLAIN, 12));
        JScrollPane logScrollPane = new JScrollPane(logArea);
        logScrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        panel.add(logScrollPane, BorderLayout.CENTER);

        // Export button
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        JButton exportBtn = new JButton("Export Log...");
        JButton refreshBtn = new JButton("Refresh");
        JButton clearBtn = new JButton("Clear Log");

        exportBtn.addActionListener(e -> exportLog());
        refreshBtn.addActionListener(e -> updateLogDisplay());
        clearBtn.addActionListener(e -> {
            auditLogger.clear();
            updateLogDisplay();
            addSystemMessage("Audit log cleared");
        });

        buttonPanel.add(refreshBtn);
        buttonPanel.add(exportBtn);
        buttonPanel.add(clearBtn);
        panel.add(buttonPanel, BorderLayout.SOUTH);

        return panel;
    }

    private void updateLogDisplay() {
        if (logArea == null) return;
        StringBuilder sb = new StringBuilder();
        for (AuditLogger.LogEntry entry : auditLogger.getEntries()) {
            sb.append(entry.toString()).append("\n");
        }
        logArea.setText(sb.toString());
    }

    private void exportLog() {
        JFileChooser chooser = new JFileChooser();
        chooser.setDialogTitle("Export Audit Log");
        int result = chooser.showSaveDialog(frame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File file = chooser.getSelectedFile();
            try {
                auditLogger.exportToFile(file);
                addSystemMessage("Audit log exported to " + file.getName());
            } catch (IOException e) {
                JOptionPane.showMessageDialog(frame,
                    "Error exporting log: " + e.getMessage(),
                    "Export Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    // ==================== Check-in Panel ====================

    private JPanel createCheckInPanel() {
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Create check-in manager with 60 second interval
        checkInManager = new CheckInManager(60, auditLogger);

        // Get the check-in panel from manager
        JPanel checkInControlPanel = checkInManager.createPanel();
        panel.add(checkInControlPanel, BorderLayout.NORTH);

        // Info panel
        JPanel infoPanel = new JPanel(new GridLayout(4, 1, 5, 5));
        infoPanel.setBorder(BorderFactory.createTitledBorder("Instructions"));

        infoPanel.add(new JLabel("1. Click 'Start' to begin the check-in timer"));
        infoPanel.add(new JLabel("2. Click 'Check In' before time runs out"));
        infoPanel.add(new JLabel("3. If you miss a check-in, your peer will be alerted"));
        infoPanel.add(new JLabel("4. Click 'Stop' to end the check-in system"));

        panel.add(infoPanel, BorderLayout.CENTER);

        // Status area
        JLabel infoLabel = new JLabel("Check-in system for lone worker safety");
        infoLabel.setHorizontalAlignment(SwingConstants.CENTER);
        infoLabel.setForeground(Color.GRAY);
        panel.add(infoLabel, BorderLayout.SOUTH);

        // Set up callbacks
        checkInManager.setOnAlert(msg -> {
            addSystemMessage(msg);
            // Notify peer of successful check-in
            if (networkManager.isConnected()) {
                networkManager.sendText("CHECKIN:" + (isHost ? "Host" : "Client"));
            }
        });

        checkInManager.setOnMissedCheckIn(() -> {
            addAlertMessage("CHECK-IN MISSED", "User failed to check in!", isHost);
            if (networkManager.isConnected()) {
                networkManager.sendText("CHECKIN_MISSED:" + (isHost ? "Host" : "Client"));
            }
        });

        return panel;
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
        // Handle received text
        networkManager.setOnTextReceived(text -> {
            // Parse message format: MSG:sender:priority:text:sd
            if (text.startsWith("MSG:")) {
                String[] parts = text.split(":", 5);
                if (parts.length >= 4) {
                    String sender = parts[1];
                    String priority = parts[2];
                    String msgText = parts[3];
                    String sdStr = parts.length > 4 ? parts[4] : "0";
                    int selfDestructSecs = 0;
                    try {
                        selfDestructSecs = Integer.parseInt(sdStr);
                    } catch (NumberFormatException e) {
                        // Ignore
                    }

                    boolean fromHost = sender.equals("Host");
                    addMessage(sender, msgText, fromHost, priority, selfDestructSecs);

                    // Handle alert visuals for received messages
                    if (priority.equals("CRITICAL")) {
                        flashScreen(Color.RED, 3);
                        playAlertSound();
                    } else if (priority.equals("ALERT")) {
                        flashScreen(Color.ORANGE, 2);
                    }

                    auditLogger.log(AuditLogger.EventType.MESSAGE_RECEIVED,
                        "Message received from " + sender);
                }
            }
            // Handle self-destruct notification
            else if (text.startsWith("SD:")) {
                addSystemMessage("A message self-destructed");
            }
            // Handle check-in messages
            else if (text.startsWith("CHECKIN:")) {
                if (checkInManager != null) {
                    checkInManager.remoteCheckIn();
                    addSystemMessage("Remote user checked in");
                }
            }
            else if (text.startsWith("CHECKIN_MISSED:")) {
                if (checkInManager != null) {
                    checkInManager.remoteMissedCheckIn();
                    addAlertMessage("ALERT", "Remote user missed check-in!", !isHost);
                }
            }
        });

        // Handle points
        networkManager.setOnPointReceived(pointMsg -> {
            if (mapScreen != null) {
                if (pointMsg.getAction() == NetworkManager.PointMessage.Action.ADD) {
                    mapScreen.addRemotePoint(pointMsg.getX(), pointMsg.getY(), pointMsg.isFromHost());
                    auditLogger.log(AuditLogger.EventType.POINT_ADDED, "Remote point added");
                } else {
                    mapScreen.removeRemotePoint(pointMsg.getX(), pointMsg.getY());
                    auditLogger.log(AuditLogger.EventType.POINT_REMOVED, "Remote point removed");
                }
            }
        });

        // Handle paths
        networkManager.setOnPathReceived(pathMsg -> {
            if (mapScreen != null) {
                mapScreen.setRemotePath(pathMsg.getSourceX(), pathMsg.getSourceY(),
                    pathMsg.getDestX(), pathMsg.getDestY(), 0);
                auditLogger.log(AuditLogger.EventType.PATH_FOUND, "Remote path found");
            }
        });

        networkManager.setOnPathRemoved(pathIndex -> {
            if (mapScreen != null) {
                mapScreen.removeRemotePath(pathIndex);
                auditLogger.log(AuditLogger.EventType.PATH_REMOVED, "Remote path removed");
            }
        });

        // Handle edges
        networkManager.setOnEdgeReceived(edgeMsg -> {
            if (mapScreen != null) {
                mapScreen.addRemoteEdge(edgeMsg.getX1(), edgeMsg.getY1(),
                    edgeMsg.getX2(), edgeMsg.getY2());
                auditLogger.log(AuditLogger.EventType.EDGE_ADDED, "Remote edge added");
            }
        });

        // Handle status
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
        auditLogger.log(AuditLogger.EventType.DISCONNECTION, "Application shutdown");

        // Stop all self-destruct timers
        for (javax.swing.Timer timer : selfDestructTimers.values()) {
            timer.stop();
        }
        selfDestructTimers.clear();

        networkManager.shutdown();
        if (frame != null) {
            frame.dispose();
        }
    }

    public JFrame getFrame() {
        return frame;
    }
}
