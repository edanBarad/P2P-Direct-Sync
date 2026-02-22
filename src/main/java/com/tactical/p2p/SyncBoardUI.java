package com.tactical.p2p;

import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.Document;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

/**
 * SyncBoardUI - Main UI Component for the P2P Shared Board
 *
 * Features two views accessible via tabs:
 * - Text Board: Synchronized text areas between peers
 * - Map View: Shared map with clickable points
 *
 * Design Patterns Used:
 * - Observer: DocumentListener for text changes, StateChangeListener for model updates
 * - MVC: This is the View/Controller, StateModel is the Model
 * - Dependency Injection: StateModel and NetworkManager are injected via constructor
 */
public class SyncBoardUI {

    // ==================== UI Constants ====================

    private static final String TITLE_PREFIX = "P2P Shared Board - ";
    private static final Font TEXT_FONT = new Font("Monospaced", Font.PLAIN, 14);
    private static final int STATUS_BAR_HEIGHT = 30;

    // ==================== UI Components ====================

    private JFrame frame;
    private JTabbedPane tabbedPane;

    // Text board components
    private JTextArea topTextArea;
    private JTextArea bottomTextArea;

    // Map components
    private MapPanel mapPanel;

    // Status bar
    private JLabel statusLabel;
    private JLabel roleLabel;

    // ==================== Dependencies ====================

    private final StateModel stateModel;
    private final NetworkManager networkManager;
    private boolean isUpdatingFromNetwork = false;
    private boolean isHost;

    // ==================== Constructor ====================

    public SyncBoardUI(StateModel stateModel, NetworkManager networkManager) {
        if (stateModel == null || networkManager == null) {
            throw new IllegalArgumentException("StateModel and NetworkManager cannot be null");
        }
        this.stateModel = stateModel;
        this.networkManager = networkManager;
    }

    // ==================== UI Initialization ====================

    public void initialize(String role) {
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> initialize(role));
            return;
        }

        this.isHost = "HOST".equals(role);

        // Create main frame
        frame = new JFrame(TITLE_PREFIX + role);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });

        // Create tabbed pane
        tabbedPane = new JTabbedPane();

        // Create Text Board tab
        JPanel textBoardPanel = createTextBoardPanel();
        tabbedPane.addTab("Text Board", textBoardPanel);

        // Create Map tab
        JPanel mapPanelWrapper = createMapPanel();
        tabbedPane.addTab("Map", mapPanelWrapper);

        frame.add(tabbedPane, BorderLayout.CENTER);

        // Create status bar
        createStatusBar(role);

        // Set up callbacks
        setupNetworkCallbacks();
        setupStateListener();

        // Configure and show frame
        frame.setSize(700, 550);
        frame.setLocationRelativeTo(null);
        frame.setMinimumSize(new Dimension(500, 400));
        frame.setVisible(true);

        // Focus on text area
        bottomTextArea.requestFocusInWindow();
    }

    // ==================== Text Board Panel ====================

    private JPanel createTextBoardPanel() {
        JPanel panel = new JPanel(new GridLayout(2, 1, 5, 5));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top Panel - Remote Text (Read-Only)
        topTextArea = createTextArea(false);
        topTextArea.setBackground(new Color(240, 240, 240));
        JScrollPane topScrollPane = createScrollPane(topTextArea, "Remote Peer (Read-Only)");

        // Bottom Panel - Local Text (Editable)
        bottomTextArea = createTextArea(true);
        bottomTextArea.setBackground(Color.WHITE);
        JScrollPane bottomScrollPane = createScrollPane(bottomTextArea, "Local Input (Type here)");

        // Add document listener for real-time sync
        addDocumentListener();

        panel.add(topScrollPane);
        panel.add(bottomScrollPane);

        return panel;
    }

    private JTextArea createTextArea(boolean editable) {
        JTextArea textArea = new JTextArea();
        textArea.setFont(TEXT_FONT);
        textArea.setEditable(editable);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setMargin(new Insets(5, 5, 5, 5));
        return textArea;
    }

    private JScrollPane createScrollPane(JTextArea textArea, String title) {
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            title
        ));
        return scrollPane;
    }

    private void addDocumentListener() {
        Document doc = bottomTextArea.getDocument();
        doc.addDocumentListener(new DocumentListener() {
            @Override
            public void insertUpdate(DocumentEvent e) {
                onLocalTextChanged();
            }

            @Override
            public void removeUpdate(DocumentEvent e) {
                onLocalTextChanged();
            }

            @Override
            public void changedUpdate(DocumentEvent e) {
                // Ignore attribute changes
            }
        });
    }

    private void onLocalTextChanged() {
        if (isUpdatingFromNetwork) {
            return;
        }

        String text = bottomTextArea.getText();
        stateModel.setLocalText(text);

        if (networkManager.isConnected()) {
            networkManager.sendText(text);
        }
    }

    // ==================== Map Panel ====================

    private JPanel createMapPanel() {
        JPanel wrapper = new JPanel(new BorderLayout());

        // Create map panel
        mapPanel = new MapPanel();
        mapPanel.setIsHost(isHost);

        // Add instructions label
        JLabel instructions = new JLabel(" Left-click to add point | Right-click on point to delete");
        instructions.setForeground(Color.DARK_GRAY);
        instructions.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        wrapper.add(mapPanel, BorderLayout.CENTER);
        wrapper.add(instructions, BorderLayout.SOUTH);

        // Set up point callbacks
        mapPanel.setOnPointAdded(point -> {
            if (networkManager.isConnected()) {
                networkManager.sendPoint(point, NetworkManager.PointMessage.Action.ADD, isHost);
            }
        });

        mapPanel.setOnPointRemoved(removalInfo -> {
            if (networkManager.isConnected()) {
                // Send the actual owner of the point (wasHostPoint), not who deleted it
                networkManager.sendPoint(removalInfo.point, NetworkManager.PointMessage.Action.REMOVE, removalInfo.wasHostPoint);
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
        // Handle received text
        networkManager.setOnTextReceived(text -> {
            updateRemoteText(text);
        });

        // Handle received points
        networkManager.setOnPointReceived(pointMsg -> {
            if (mapPanel != null) {
                if (pointMsg.getAction() == NetworkManager.PointMessage.Action.ADD) {
                    mapPanel.addRemotePoint(pointMsg.getX(), pointMsg.getY(), pointMsg.isFromHost());
                } else {
                    mapPanel.removeRemotePoint(pointMsg.getX(), pointMsg.getY(), pointMsg.isFromHost());
                }
            }
        });

        // Handle status changes
        networkManager.setOnStatusChanged(status -> {
            updateConnectionStatus(status);
        });
    }

    // ==================== State Listener ====================

    private void setupStateListener() {
        stateModel.addListener(model -> {
            SwingUtilities.invokeLater(() -> {
                if (model.isConnected()) {
                    statusLabel.setText("Status: Connected");
                    statusLabel.setForeground(new Color(0, 128, 0));
                } else {
                    statusLabel.setText("Status: Disconnected");
                    statusLabel.setForeground(Color.GRAY);
                }
            });
        });
    }

    // ==================== UI Update Methods ====================

    private void updateRemoteText(String text) {
        isUpdatingFromNetwork = true;

        try {
            int caretPos = topTextArea.getCaretPosition();
            topTextArea.setText(text);

            if (caretPos <= text.length()) {
                topTextArea.setCaretPosition(caretPos);
            }

            stateModel.setRemoteText(text);
        } finally {
            isUpdatingFromNetwork = false;
        }
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

    // ==================== Shutdown ====================

    public void shutdown() {
        System.out.println("[SyncBoardUI] Shutting down...");
        networkManager.shutdown();
        if (frame != null) {
            frame.dispose();
        }
    }

    public JFrame getFrame() {
        return frame;
    }
}
