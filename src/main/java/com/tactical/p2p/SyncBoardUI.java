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
 * This class follows the Single Responsibility Principle (SRP) by handling
 * only UI rendering and user input capture. It delegates:
 * - State management to StateModel
 * - Network operations to NetworkManager
 *
 * Threading Model:
 * ┌────────────────────────────────────────────────────────────────┐
 * │  Event Dispatch Thread (EDT)                                   │
 * │  - All Swing component creation and updates                    │
 * │  - DocumentListener callbacks (user typing)                    │
 * │  - StateChangeListener callbacks (from network updates)        │
 * └────────────────────────────────────────────────────────────────┘
 *
 * The UI uses a GridLayout(2,1) to create two vertical sections:
 * - Top Panel: Read-only view of remote peer's text
 * - Bottom Panel: Editable local text area
 *
 * Design Patterns Used:
 * - Observer: DocumentListener for text changes, StateChangeListener for model updates
 * - MVC: This is the View/Controller, StateModel is the Model
 * - Dependency Injection: StateModel and NetworkManager are injected via constructor
 */
public class SyncBoardUI {

    // ==================== UI Constants ====================

    /** Window title prefix */
    private static final String TITLE_PREFIX = "P2P Shared Board - ";

    /** Font for text areas */
    private static final Font TEXT_FONT = new Font("Monospaced", Font.PLAIN, 14);

    /** Status bar height */
    private static final int STATUS_BAR_HEIGHT = 30;

    // ==================== UI Components ====================

    /** Main application frame */
    private JFrame frame;

    /** Displays text received from the remote peer (read-only) */
    private JTextArea topTextArea;

    /** Local text input area (editable) */
    private JTextArea bottomTextArea;

    /** Status label showing connection state */
    private JLabel statusLabel;

    /** Role indicator (HOST/CLIENT) */
    private JLabel roleLabel;

    // ==================== Dependencies ====================

    /** The shared state model */
    private final StateModel stateModel;

    /** The network manager for sending text */
    private final NetworkManager networkManager;

    /** Flag to prevent recursive update loops */
    private boolean isUpdatingFromNetwork = false;

    // ==================== Constructor (Dependency Injection) ====================

    /**
     * Creates the SyncBoardUI with injected dependencies.
     *
     * This follows the Dependency Inversion Principle (DIP) - the UI depends
     * on abstractions (the StateModel and NetworkManager interfaces/behaviors)
     * rather than creating them itself.
     *
     * @param stateModel The shared state model (must not be null)
     * @param networkManager The network manager (must not be null)
     */
    public SyncBoardUI(StateModel stateModel, NetworkManager networkManager) {
        if (stateModel == null || networkManager == null) {
            throw new IllegalArgumentException("StateModel and NetworkManager cannot be null");
        }
        this.stateModel = stateModel;
        this.networkManager = networkManager;
    }

    // ==================== UI Initialization ====================

    /**
     * Initializes and displays the main UI.
     * Must be called from the Event Dispatch Thread.
     *
     * @param role The role title ("HOST" or "CLIENT")
     */
    public void initialize(String role) {
        // Ensure we're on the EDT for Swing operations
        if (!SwingUtilities.isEventDispatchThread()) {
            SwingUtilities.invokeLater(() -> initialize(role));
            return;
        }

        // Create main frame
        frame = new JFrame(TITLE_PREFIX + role);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.setLayout(new BorderLayout());

        // Add window close handler
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                shutdown();
            }
        });

        // Create components
        createMainPanel();
        createStatusBar(role);

        // Set up callbacks
        setupNetworkCallbacks();
        setupStateListener();

        // Configure and show frame
        frame.setSize(600, 500);
        frame.setLocationRelativeTo(null); // Center on screen
        frame.setMinimumSize(new Dimension(400, 300));

        frame.setVisible(true);

        // Focus on the bottom text area for immediate typing
        bottomTextArea.requestFocusInWindow();
    }

    // ==================== Panel Creation ====================

    /**
     * Creates the main content panel with GridLayout(2,1).
     * Top half: Remote text (read-only)
     * Bottom half: Local text (editable)
     */
    private void createMainPanel() {
        JPanel mainPanel = new JPanel(new GridLayout(2, 1, 5, 5));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Top Panel - Remote Text (Read-Only)
        topTextArea = createTextArea(false);
        topTextArea.setBackground(new Color(240, 240, 240)); // Light gray to indicate read-only
        JScrollPane topScrollPane = createScrollPane(topTextArea, "Remote Peer (Read-Only)");

        // Bottom Panel - Local Text (Editable)
        bottomTextArea = createTextArea(true);
        bottomTextArea.setBackground(Color.WHITE);
        JScrollPane bottomScrollPane = createScrollPane(bottomTextArea, "Local Input (Type here)");

        // Add document listener for real-time sync
        addDocumentListener();

        mainPanel.add(topScrollPane);
        mainPanel.add(bottomScrollPane);

        frame.add(mainPanel, BorderLayout.CENTER);
    }

    /**
     * Creates a JTextArea with common configuration.
     *
     * @param editable Whether the text area should be editable
     * @return Configured JTextArea
     */
    private JTextArea createTextArea(boolean editable) {
        JTextArea textArea = new JTextArea();
        textArea.setFont(TEXT_FONT);
        textArea.setEditable(editable);
        textArea.setLineWrap(true);
        textArea.setWrapStyleWord(true);
        textArea.setMargin(new Insets(5, 5, 5, 5));
        return textArea;
    }

    /**
     * Creates a JScrollPane containing a text area with a titled border.
     *
     * @param textArea The text area to wrap
     * @param title The border title
     * @return Configured JScrollPane
     */
    private JScrollPane createScrollPane(JTextArea textArea, String title) {
        JScrollPane scrollPane = new JScrollPane(textArea);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setBorder(BorderFactory.createTitledBorder(
            BorderFactory.createEtchedBorder(),
            title
        ));
        return scrollPane;
    }

    /**
     * Creates the status bar at the bottom of the window.
     *
     * @param role The current role (HOST/CLIENT)
     */
    private void createStatusBar(String role) {
        JPanel statusPanel = new JPanel(new BorderLayout());
        statusPanel.setPreferredSize(new Dimension(0, STATUS_BAR_HEIGHT));
        statusPanel.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));

        // Left side - Connection status
        statusLabel = new JLabel("Status: Disconnected");
        statusLabel.setForeground(Color.GRAY);

        // Right side - Role indicator
        roleLabel = new JLabel("Role: " + role);
        roleLabel.setForeground(new Color(70, 130, 180)); // Steel blue

        statusPanel.add(statusLabel, BorderLayout.WEST);
        statusPanel.add(roleLabel, BorderLayout.EAST);

        // Add separator above status bar
        frame.add(new JSeparator(JSeparator.HORIZONTAL), BorderLayout.SOUTH);
        frame.add(statusPanel, BorderLayout.PAGE_END);
    }

    // ==================== Document Listener (Observer Pattern) ====================

    /**
     * Adds a DocumentListener to the bottom text area.
     * This implements the Observer pattern - the UI observes the document
     * and reacts to changes by sending data over the network.
     *
     * IMPORTANT: The listener fires on every character change for real-time sync.
     * This provides immediate feedback but may generate high network traffic.
     */
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
                // Attributes changed - ignore for plain text
            }
        });
    }

    /**
     * Called when the local text area content changes.
     * Updates the state model and broadcasts to the remote peer.
     *
     * NOTE: We check isUpdatingFromNetwork to prevent echo loops
     * (network update -> local change -> network send -> ...)
     */
    private void onLocalTextChanged() {
        // Skip if this change came from a network update
        if (isUpdatingFromNetwork) {
            return;
        }

        String text = bottomTextArea.getText();

        // Update local state
        stateModel.setLocalText(text);

        // Send to remote peer if connected
        if (networkManager.isConnected()) {
            networkManager.sendText(text);
        }
    }

    // ==================== Network Callbacks ====================

    /**
     * Sets up callbacks from the NetworkManager.
     * These callbacks bring data from background threads to the EDT.
     */
    private void setupNetworkCallbacks() {
        // Handle received text - update top panel
        networkManager.setOnTextReceived(text -> {
            // This callback is invoked on the EDT by NetworkManager
            updateRemoteText(text);
        });

        // Handle status changes - update status bar
        networkManager.setOnStatusChanged(status -> {
            // This callback is invoked on the EDT by NetworkManager
            updateConnectionStatus(status);
        });
    }

    // ==================== State Listener (Observer Pattern) ====================

    /**
     * Sets up a listener on the StateModel.
     * This allows the UI to react to state changes from any source.
     */
    private void setupStateListener() {
        stateModel.addListener(model -> {
            // Ensure we're on the EDT for UI updates
            SwingUtilities.invokeLater(() -> {
                // Update connection status indicator
                if (model.isConnected()) {
                    statusLabel.setText("Status: Connected");
                    statusLabel.setForeground(new Color(0, 128, 0)); // Green
                } else {
                    statusLabel.setText("Status: Disconnected");
                    statusLabel.setForeground(Color.GRAY);
                }
            });
        });
    }

    // ==================== UI Update Methods ====================

    /**
     * Updates the remote text area with received content.
     * This method must be called on the EDT.
     *
     * @param text The text received from the remote peer
     */
    private void updateRemoteText(String text) {
        // Set flag to prevent echo loop
        isUpdatingFromNetwork = true;

        try {
            // Preserve scroll position
            int caretPos = topTextArea.getCaretPosition();

            topTextArea.setText(text);

            // Restore caret position if valid
            if (caretPos <= text.length()) {
                topTextArea.setCaretPosition(caretPos);
            }

            // Update state model
            stateModel.setRemoteText(text);
        } finally {
            isUpdatingFromNetwork = false;
        }
    }

    /**
     * Updates the connection status indicator.
     * This method must be called on the EDT.
     *
     * @param status The new connection status
     */
    private void updateConnectionStatus(NetworkManager.ConnectionStatus status) {
        switch (status) {
            case CONNECTED:
                statusLabel.setText("Status: Connected");
                statusLabel.setForeground(new Color(0, 128, 0)); // Green
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

    /**
     * Gracefully shuts down the application.
     * Cleans up network resources and disposes the UI.
     *
     * IMPORTANT: Does NOT call System.exit() - allows caller to decide
     * whether to exit the JVM or just close this window.
     */
    public void shutdown() {
        System.out.println("[SyncBoardUI] Shutting down...");

        // Shutdown network first
        networkManager.shutdown();

        // Dispose UI
        if (frame != null) {
            frame.dispose();
        }
    }

    // ==================== Getters ====================

    /**
     * Gets the main frame for positioning or other operations.
     *
     * @return The main JFrame
     */
    public JFrame getFrame() {
        return frame;
    }
}
