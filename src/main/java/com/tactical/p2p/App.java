package com.tactical.p2p;

import javax.swing.*;
import java.awt.*;
import java.util.function.Consumer;

/**
 * App - Main Entry Point for P2P Shared Board
 *
 * This class serves as the composition root, following the Dependency Inversion
 * Principle (DIP) by creating and wiring all dependencies at the top level.
 *
 * Architecture Overview:
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │                           App.java                                  │
 * │  (Composition Root - wires everything together)                     │
 * │                                                                      │
 * │  ┌─────────────┐     ┌──────────────────┐     ┌─────────────────┐  │
 * │  │ StateModel  │◄────│  SyncBoardUI     │────►│ NetworkManager  │  │
 * │  │   (Model)   │     │ (View/Controller)│     │   (Service)     │  │
 * │  └─────────────┘     └──────────────────┘     └─────────────────┘  │
 * │         ▲                     │                       ▲            │
 * │         │                     │                       │            │
 * │         └─────────────────────┴───────────────────────┘            │
 * │                    (Observer Pattern)                              │
 * └─────────────────────────────────────────────────────────────────────┘
 *
 * Authentication Flow:
 * - HOST: Sets 4-digit PIN on startup → validates client PIN attempts
 * - CLIENT: Must enter correct PIN within 3 attempts to connect
 *
 * SOLID Principles Applied:
 *
 * 1. Single Responsibility (SRP):
 *    - StateModel: Holds and notifies of state changes
 *    - NetworkManager: Handles all socket I/O and authentication
 *    - SyncBoardUI: Manages Swing UI and user input
 *    - AuthManager: Handles PIN validation and attempt tracking
 *    - DeadMansSwitch: Handles emergency alert system
 *    - App: Entry point and dependency wiring
 *
 * 2. Open/Closed (OCP):
 *    - ConnectionStatus enum allows extending status types without modifying code
 *    - StateChangeListener interface allows unlimited observers
 *
 * 3. Liskov Substitution (LSP):
 *    - Any StateChangeListener implementation can be used
 *
 * 4. Interface Segregation (ISP):
 *    - Small, focused functional interfaces (Consumer<String>, StateChangeListener)
 *
 * 5. Dependency Inversion (DIP):
 *    - High-level modules (SyncBoardUI) don't create low-level modules
 *    - Dependencies are injected via constructor
 *
 * Usage:
 *   Run the application. A dialog will appear asking to select HOST or CLIENT mode.
 *   - HOST: Sets PIN, starts a server on port 8888, waits for a client to connect
 *   - CLIENT: Connects to localhost:8888, must authenticate with PIN
 *
 *   Once connected, text typed in the bottom panel is sent to the peer's top panel.
 */
public class App {

    /** Default port for P2P communication */
    private static final int DEFAULT_PORT = 8888;

    /** Default host address for client connection (localhost for testing) */
    private static final String DEFAULT_HOST = "127.0.0.1";

    /** Stores the host address for client mode (can be changed via dialog) */
    private static String clientHostAddress = DEFAULT_HOST;

    // ==================== Main Entry Point ====================

    /**
     * Application entry point.
     * Creates the mode selection dialog and initializes the application.
     *
     * @param args Command line arguments:
     *             - "host" or "server": Start in host mode
     *             - "client [hostname]": Start in client mode, optionally with host address
     */
    public static void main(String[] args) {
        System.out.println("===========================================");
        System.out.println("  P2P Shared Board - Tactical Security");
        System.out.println("  Military/Security Operations System");
        System.out.println("===========================================");

        // Set system look and feel for native appearance
        setLookAndFeel();

        // Parse command-line arguments
        String mode = null;
        String hostArg = null;

        if (args.length > 0) {
            String arg = args[0].toLowerCase();
            if ("host".equals(arg) || "server".equals(arg)) {
                mode = "HOST";
            } else if ("client".equals(arg)) {
                mode = "CLIENT";
                // Check if host address is provided as second argument
                if (args.length > 1) {
                    hostArg = args[1];
                }
            }
        }

        // Show mode selection dialog on EDT if no command-line arg
        final String selectedMode = mode;
        final String providedHost = hostArg;
        SwingUtilities.invokeLater(() -> {
            String finalMode = selectedMode;

            // Show mode selection dialog if no command-line mode specified
            if (finalMode == null) {
                finalMode = showModeSelectionDialog();
            }

            if (finalMode == null) {
                System.out.println("[App] No mode selected, exiting.");
                return;
            }

            // For CLIENT mode, show host address dialog if not provided via CLI
            if ("CLIENT".equals(finalMode)) {
                String host = providedHost;
                if (host == null) {
                    // Show dialog to get host address from user
                    host = showHostAddressDialog();
                    if (host == null) {
                        System.out.println("[App] No host address provided, exiting.");
                        return;
                    }
                }
                clientHostAddress = host;
            }

            // Initialize and start the application
            initializeApplication(finalMode);
        });
    }

    // ==================== Look and Feel Configuration ====================

    /**
     * Sets the system look and feel for native appearance.
     * Falls back to cross-platform L&F if system L&F fails.
     */
    private static void setLookAndFeel() {
        try {
            UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
        } catch (Exception e) {
            System.err.println("[App] Could not set system look and feel: " + e.getMessage());
            // Cross-platform L&F is the default fallback
        }
    }

    // ==================== Mode Selection Dialog ====================

    /**
     * Shows a dialog allowing the user to select HOST or CLIENT mode.
     *
     * @return "HOST", "CLIENT", or null if cancelled
     */
    private static String showModeSelectionDialog() {
        // Create custom button panel
        String[] options = {"HOST (Server)", "CLIENT (Connect)", "Cancel"};

        int choice = JOptionPane.showOptionDialog(
            null,
            buildModeSelectionPanel(),
            "P2P Shared Board - Mode Selection",
            JOptionPane.DEFAULT_OPTION,
            JOptionPane.QUESTION_MESSAGE,
            null,
            options,
            options[0]
        );

        switch (choice) {
            case 0:
                return "HOST";
            case 1:
                return "CLIENT";
            default:
                return null;
        }
    }

    /**
     * Builds the mode selection panel with descriptions.
     *
     * @return The panel to display in the dialog
     */
    private static JPanel buildModeSelectionPanel() {
        JPanel panel = new JPanel(new GridLayout(3, 1, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 20, 10, 20));

        JLabel titleLabel = new JLabel("Select Connection Mode:", JLabel.CENTER);
        titleLabel.setFont(titleLabel.getFont().deriveFont(Font.BOLD, 14f));

        JLabel hostLabel = new JLabel("HOST: Set PIN, start server, wait for client");
        JLabel clientLabel = new JLabel("CLIENT: Connect to host and authenticate with PIN");

        panel.add(titleLabel);
        panel.add(hostLabel);
        panel.add(clientLabel);

        return panel;
    }

    // ==================== Host Address Dialog ====================

    /**
     * Shows a dialog for the user to enter the host IP address when in CLIENT mode.
     * This allows connecting to a remote host over the network instead of only localhost.
     *
     * @return The host IP address entered by user, or null if cancelled
     */
    private static String showHostAddressDialog() {
        // Create panel with input field and helpful labels
        JPanel panel = new JPanel(new BorderLayout(10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        // Instructions
        JLabel instructionLabel = new JLabel("<html>Enter the IP address or hostname of the server you want to connect to.<br><br>" +
            "For local testing, use: <b>127.0.0.1</b> or <b>localhost</b></html>");
        panel.add(instructionLabel, BorderLayout.NORTH);

        // Input field with default value
        JTextField hostField = new JTextField(DEFAULT_HOST, 20);
        hostField.selectAll(); // Pre-select text for easy replacement
        panel.add(hostField, BorderLayout.CENTER);

        // Help text showing how to find your IP
        JLabel helpLabel = new JLabel("<html><i>Tip: On the host machine, run 'ipconfig' (Windows) or 'ifconfig'/'ip addr' (Linux/Mac)<br>" +
            "to find its IP address on your network.</i></html>");
        helpLabel.setFont(helpLabel.getFont().deriveFont(10f));
        panel.add(helpLabel, BorderLayout.SOUTH);

        int result = JOptionPane.showConfirmDialog(
            null,
            panel,
            "Enter Host Address",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        String host = hostField.getText().trim();
        if (host.isEmpty()) {
            return DEFAULT_HOST;
        }

        return host;
    }

    // ==================== Application Initialization ====================

    /**
     * Initializes the application components and starts the network connection.
     *
     * This method serves as the Composition Root in Dependency Injection terminology.
     * It creates all objects and wires their dependencies together.
     *
     * @param mode "HOST" or "CLIENT"
     */
    private static void initializeApplication(String mode) {
        System.out.println("[App] Initializing as " + mode);

        // Step 1: Create the shared state model (singleton for this app)
        StateModel stateModel = new StateModel();

        // Step 2: Create the authentication manager
        AuthManager authManager = new AuthManager();

        // Step 3: For HOST mode, show PIN setup dialog first
        if ("HOST".equals(mode)) {
            boolean pinSet = authManager.showPinSetupDialog(null);
            if (!pinSet) {
                System.out.println("[App] PIN setup cancelled, exiting.");
                System.exit(0);
                return;
            }
        }

        // Step 4: Create the network manager with state model dependency
        NetworkManager networkManager = new NetworkManager(stateModel);
        networkManager.setAuthManager(authManager);

        // Step 5: Create the UI with injected dependencies
        SyncBoardUI ui = new SyncBoardUI(stateModel, networkManager);

        // Step 6: Set up authentication callbacks BEFORE initializing UI
        setupAuthCallbacks(networkManager, authManager, ui, mode);

        // Step 7: Initialize the UI (creates frame, panels, etc.)
        // This must complete before starting network
        if (SwingUtilities.isEventDispatchThread()) {
            ui.initialize(mode);
        } else {
            try {
                SwingUtilities.invokeAndWait(() -> ui.initialize(mode));
            } catch (Exception e) {
                System.err.println("[App] Error initializing UI: " + e.getMessage());
                return;
            }
        }

        // Step 8: Set parent frame for dialogs (after UI is initialized)
        networkManager.setParentFrame(ui.getFrame());

        // Step 9: Start networking based on mode
        if ("HOST".equals(mode)) {
            startHostMode(networkManager, ui);
        } else {
            startClientMode(networkManager, ui);
        }

        System.out.println("[App] Initialization complete");
    }

    // ==================== Authentication Callbacks ====================

    /**
     * Sets up authentication callbacks between network manager and UI.
     * Uses Observer pattern to notify UI of authentication events.
     */
    private static void setupAuthCallbacks(NetworkManager nm, AuthManager auth,
                                            SyncBoardUI ui, String mode) {

        if ("HOST".equals(mode)) {
            // Host receives auth attempt notifications
            nm.setOnAuthAttempt(attemptInfo -> {
                // Format: pin:RESULT:remaining
                String[] parts = attemptInfo.split(":");
                if (parts.length >= 3) {
                    String pin = parts[0];
                    String result = parts[1];
                    int remaining = Integer.parseInt(parts[2]);

                    if ("FAIL".equals(result)) {
                        // Show alert dialog to host
                        JOptionPane.showMessageDialog(ui.getFrame(),
                            "Failed authentication attempt!\n" +
                            "PIN tried: " + pin + "\n" +
                            "Attempts remaining: " + remaining,
                            "Security Alert",
                            JOptionPane.WARNING_MESSAGE);

                        ui.addSystemMessage("[SECURITY] Failed auth attempt. PIN: " + pin +
                            ". " + remaining + " attempts remaining.");

                        if (remaining <= 0) {
                            ui.addAlertMessage("SECURITY ALERT",
                                "Client blocked after 3 failed attempts!", true);
                        }
                    } else if ("SUCCESS".equals(result)) {
                        ui.addSystemMessage("[SECURITY] Client authenticated successfully.");
                    }
                }
            });
        } else {
            // Client authentication is now handled internally by NetworkManager
            // No callbacks needed - it uses blocking dialogs
        }
    }

    // ==================== Mode Starters ====================

    /**
     * Starts the application in HOST mode.
     * Opens a ServerSocket and waits for client connections.
     *
     * @param networkManager The network manager instance
     * @param ui The UI instance for status updates
     */
    private static void startHostMode(NetworkManager networkManager, SyncBoardUI ui) {
        System.out.println("[App] Starting HOST mode on port " + DEFAULT_PORT);
        ui.addSystemMessage("Waiting for client connection on port " + DEFAULT_PORT + "...");
        networkManager.startHost(DEFAULT_PORT);
    }

    /**
     * Starts the application in CLIENT mode.
     * Attempts to connect to the host server.
     *
     * @param networkManager The network manager instance
     * @param ui The UI instance for status updates
     */
    private static void startClientMode(NetworkManager networkManager, SyncBoardUI ui) {
        System.out.println("[App] Starting CLIENT mode, connecting to " + clientHostAddress + ":" + DEFAULT_PORT);
        ui.addSystemMessage("Connecting to " + clientHostAddress + ":" + DEFAULT_PORT + "...");
        networkManager.startClient(clientHostAddress, DEFAULT_PORT);
    }
}
