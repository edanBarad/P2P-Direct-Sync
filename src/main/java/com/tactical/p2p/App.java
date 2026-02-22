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
 * SOLID Principles Applied:
 *
 * 1. Single Responsibility (SRP):
 *    - StateModel: Holds and notifies of state changes
 *    - NetworkManager: Handles all socket I/O
 *    - SyncBoardUI: Manages Swing UI and user input
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
 * Threading Model:
 * ┌──────────────────────────────────────────────────────────────────────┐
 * │ Main Thread                                                          │
 * │   └─► App.main() creates StateModel, NetworkManager, SyncBoardUI    │
 * │       then blocks on SwingUtilities.invokeLater()                    │
 * ├──────────────────────────────────────────────────────────────────────┤
 * │ Event Dispatch Thread (EDT)                                          │
 * │   └─► All Swing UI operations                                       │
 * │   └─► DocumentListener callbacks (user typing)                       │
 * │   └─► StateChangeListener callbacks (network → UI updates)           │
 * ├──────────────────────────────────────────────────────────────────────┤
 * │ Network Threads (background, daemon)                                 │
 * │   └─► P2P-Host-Thread: ServerSocket.accept() blocking call          │
 * │   └─► P2P-Client-Thread: Socket.connect() blocking call             │
 * │   └─► P2P-Receiver-Thread: BufferedReader.readLine() loop           │
 * └──────────────────────────────────────────────────────────────────────┘
 *
 * Usage:
 *   Run the application. A dialog will appear asking to select HOST or CLIENT mode.
 *   - HOST: Starts a server on port 8888, waits for a client to connect
 *   - CLIENT: Connects to localhost:8888
 *
 *   Once connected, text typed in the bottom panel is sent to the peer's top panel.
 */
public class App {

    /** Default port for P2P communication */
    private static final int DEFAULT_PORT = 8888;

    /** Host address for client connection */
    private static final String DEFAULT_HOST = "127.0.0.1";

    // ==================== Main Entry Point ====================

    /**
     * Application entry point.
     * Creates the mode selection dialog and initializes the application.
     *
     * @param args Command line arguments: "host" or "client" to skip dialog
     */
    public static void main(String[] args) {
        System.out.println("===========================================");
        System.out.println("  P2P Shared Board - Phase 1 PoC");
        System.out.println("  Tactical OLAR Device Simulation");
        System.out.println("===========================================");

        // Set system look and feel for native appearance
        setLookAndFeel();

        // Check for command-line mode selection
        String mode = null;
        if (args.length > 0) {
            String arg = args[0].toLowerCase();
            if ("host".equals(arg) || "server".equals(arg)) {
                mode = "HOST";
            } else if ("client".equals(arg)) {
                mode = "CLIENT";
            }
        }

        // Show mode selection dialog on EDT if no command-line arg
        final String selectedMode = mode;
        SwingUtilities.invokeLater(() -> {
            String finalMode = selectedMode;
            if (finalMode == null) {
                finalMode = showModeSelectionDialog();
            }

            if (finalMode == null) {
                System.out.println("[App] No mode selected, exiting.");
                return;
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

        JLabel hostLabel = new JLabel("HOST: Start a server and wait for a client to connect");
        JLabel clientLabel = new JLabel("CLIENT: Connect to an existing host server");

        panel.add(titleLabel);
        panel.add(hostLabel);
        panel.add(clientLabel);

        return panel;
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

        // Step 2: Create the network manager with state model dependency
        NetworkManager networkManager = new NetworkManager(stateModel);

        // Step 3: Create the UI with injected dependencies
        SyncBoardUI ui = new SyncBoardUI(stateModel, networkManager);

        // Step 4: Initialize the UI (creates frame, panels, etc.)
        ui.initialize(mode);

        // Step 5: Start networking based on mode
        if ("HOST".equals(mode)) {
            startHostMode(networkManager);
        } else {
            startClientMode(networkManager);
        }

        System.out.println("[App] Initialization complete");
    }

    // ==================== Mode Starters ====================

    /**
     * Starts the application in HOST mode.
     * Opens a ServerSocket and waits for client connections.
     *
     * @param networkManager The network manager instance
     */
    private static void startHostMode(NetworkManager networkManager) {
        System.out.println("[App] Starting HOST mode on port " + DEFAULT_PORT);
        networkManager.startHost(DEFAULT_PORT);
    }

    /**
     * Starts the application in CLIENT mode.
     * Attempts to connect to the host server.
     *
     * @param networkManager The network manager instance
     */
    private static void startClientMode(NetworkManager networkManager) {
        System.out.println("[App] Starting CLIENT mode, connecting to " + DEFAULT_HOST + ":" + DEFAULT_PORT);
        networkManager.startClient(DEFAULT_HOST, DEFAULT_PORT);
    }
}
