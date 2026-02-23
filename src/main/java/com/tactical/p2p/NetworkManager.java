package com.tactical.p2p;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

/**
 * NetworkManager - Thread-Safe TCP Socket Wrapper
 *
 * This class encapsulates all network communication logic for the P2P shared board.
 * It implements a Producer-Consumer pattern where:
 * - The UI (consumer) produces text that gets sent over the network
 * - The network (producer) receives text that gets consumed by the UI
 *
 * Threading Model:
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  Event Dispatch Thread (EDT)                                    │
 * │  - All UI operations                                            │
 * │  - SwingUtilities.invokeLater() for callbacks                   │
 * └─────────────────────────────────────────────────────────────────┘
 *          │                                           ▲
 *          │ sendText()                                │ onTextReceived()
 *          ▼                                           │
 * ┌─────────────────────────────────────────────────────────────────┐
 * │  Network Thread (background)                                    │
 * │  - Socket I/O operations                                        │
 * │  - Blocking read loop                                           │
 * │  - Heartbeat monitoring                                         │
 * └─────────────────────────────────────────────────────────────────┘
 *
 * Network Protocol:
 * - Messages are UTF-8 encoded strings
 * - Each message is terminated by a newline character (\n)
 * - This simple protocol allows easy parsing with BufferedReader.readLine()
 */
public class NetworkManager {

    /** Default port for P2P communication */
    public static final int DEFAULT_PORT = 8888;

    /** Loopback address for single-machine testing */
    public static final String LOCALHOST = "127.0.0.1";

    /** Heartbeat interval in milliseconds */
    private static final long HEARTBEAT_INTERVAL_MS = 5000;

    /** Connection timeout in milliseconds */
    private static final int CONNECTION_TIMEOUT_MS = 10000;

    // ==================== Instance Fields ====================

    /** The underlying socket (either from ServerSocket.accept() or new Socket()) */
    private Socket socket;

    /** Output stream for sending data (wrapped in BufferedWriter for efficiency) */
    private BufferedWriter writer;

    /** Input stream for receiving data (wrapped in BufferedReader for line-based reading) */
    private BufferedReader reader;

    /** Server socket for HOST mode */
    private ServerSocket serverSocket;

    /** Flag indicating if the manager is currently running */
    private final AtomicBoolean running = new AtomicBoolean(false);

    /** Flag indicating an active connection */
    private final AtomicBoolean connected = new AtomicBoolean(false);

    /** Background thread for receiving messages */
    private Thread receiverThread;

    /** Background thread for hosting (accepting connections) */
    private Thread hostThread;

    /** Callback invoked when text is received from the peer */
    private Consumer<String> onTextReceived;

    /** Callback invoked when a point is received from the peer */
    private Consumer<PointMessage> onPointReceived;

    /** Callback invoked when a path is received from the peer */
    private Consumer<PathMessage> onPathReceived;

    /** Callback invoked when an edge is received from the peer */
    private Consumer<EdgeMessage> onEdgeReceived;

    /** Callback invoked when path removal is received */
    private Consumer<Integer> onPathRemoved;

    /** Callback invoked when connection status changes */
    private Consumer<ConnectionStatus> onStatusChanged;

    /** Reference to the shared state model */
    private final StateModel stateModel;

    // ==================== Connection Status Enum ====================

    /**
     * Enum representing possible connection states.
     * Used for UI feedback and status indicators.
     */
    public enum ConnectionStatus {
        DISCONNECTED("Disconnected"),
        WAITING("Waiting for peer..."),
        CONNECTING("Connecting..."),
        CONNECTED("Connected"),
        ERROR("Connection Error");

        private final String displayName;

        ConnectionStatus(String displayName) {
            this.displayName = displayName;
        }

        @Override
        public String toString() {
            return displayName;
        }
    }

    // ==================== Point Message Class ====================

    /**
     * Represents a point message for map synchronization.
     * Contains the point coordinates, action (add/remove), and sender type.
     */
    public static class PointMessage {
        public enum Action { ADD, REMOVE }

        private final double x;
        private final double y;
        private final Action action;
        private final boolean fromHost;

        public PointMessage(double x, double y, Action action, boolean fromHost) {
            this.x = x;
            this.y = y;
            this.action = action;
            this.fromHost = fromHost;
        }

        public double getX() { return x; }
        public double getY() { return y; }
        public Action getAction() { return action; }
        public boolean isFromHost() { return fromHost; }

        /**
         * Serializes the point message to a string for network transmission.
         * Format: POINT:ADD:x:y:fromHost or POINT:REMOVE:x:y:fromHost
         */
        public String serialize() {
            return String.format("POINT:%s:%.4f:%.4f:%b", action.name(), x, y, fromHost);
        }

        /**
         * Deserializes a point message from a string.
         * @param data The serialized string
         * @return PointMessage or null if parsing fails
         */
        public static PointMessage deserialize(String data) {
            try {
                String[] parts = data.split(":");
                if (parts.length != 5 || !parts[0].equals("POINT")) {
                    return null;
                }
                Action action = Action.valueOf(parts[1]);
                double x = Double.parseDouble(parts[2]);
                double y = Double.parseDouble(parts[3]);
                boolean fromHost = Boolean.parseBoolean(parts[4]);
                return new PointMessage(x, y, action, fromHost);
            } catch (Exception e) {
                return null;
            }
        }
    }

    // ==================== Path Message Class ====================

    /**
     * Represents a path message for path synchronization.
     * Contains source and destination point coordinates.
     */
    public static class PathMessage {
        private final double sourceX, sourceY;
        private final double destX, destY;

        public PathMessage(double sourceX, double sourceY, double destX, double destY) {
            this.sourceX = sourceX;
            this.sourceY = sourceY;
            this.destX = destX;
            this.destY = destY;
        }

        public double getSourceX() { return sourceX; }
        public double getSourceY() { return sourceY; }
        public double getDestX() { return destX; }
        public double getDestY() { return destY; }

        /**
         * Serializes the path message to a string for network transmission.
         * Format: PATH:sx:sy:dx:dy
         */
        public String serialize() {
            return String.format("PATH:%.4f:%.4f:%.4f:%.4f", sourceX, sourceY, destX, destY);
        }

        /**
         * Deserializes a path message from a string.
         */
        public static PathMessage deserialize(String data) {
            try {
                String[] parts = data.split(":");
                if (parts.length != 5 || !parts[0].equals("PATH")) {
                    return null;
                }
                double sx = Double.parseDouble(parts[1]);
                double sy = Double.parseDouble(parts[2]);
                double dx = Double.parseDouble(parts[3]);
                double dy = Double.parseDouble(parts[4]);
                return new PathMessage(sx, sy, dx, dy);
            } catch (Exception e) {
                return null;
            }
        }
    }

    // ==================== Edge Message Class ====================

    /**
     * Represents an edge message for edge synchronization.
     */
    public static class EdgeMessage {
        private final double x1, y1, x2, y2;

        public EdgeMessage(double x1, double y1, double x2, double y2) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
        }

        public double getX1() { return x1; }
        public double getY1() { return y1; }
        public double getX2() { return x2; }
        public double getY2() { return y2; }

        public String serialize() {
            return String.format("EDGE:%.4f:%.4f:%.4f:%.4f", x1, y1, x2, y2);
        }

        public static EdgeMessage deserialize(String data) {
            try {
                String[] parts = data.split(":");
                if (parts.length != 5 || !parts[0].equals("EDGE")) return null;
                return new EdgeMessage(
                    Double.parseDouble(parts[1]), Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]), Double.parseDouble(parts[4])
                );
            } catch (Exception e) { return null; }
        }
    }

    // ==================== Constructor ====================

    /**
     * Creates a new NetworkManager with the given state model.
     *
     * @param stateModel The shared state model to update on network events
     */
    public NetworkManager(StateModel stateModel) {
        this.stateModel = stateModel;
    }

    // ==================== Callback Setters ====================

    /**
     * Sets the callback for received text.
     * The callback will be invoked on the EDT via SwingUtilities.invokeLater().
     *
     * @param callback Function to process received text
     */
    public void setOnTextReceived(Consumer<String> callback) {
        this.onTextReceived = callback;
    }

    /**
     * Sets the callback for received point messages.
     * The callback will be invoked on the EDT via SwingUtilities.invokeLater().
     *
     * @param callback Function to process received points
     */
    public void setOnPointReceived(Consumer<PointMessage> callback) {
        this.onPointReceived = callback;
    }

    /**
     * Sets the callback for received path messages.
     * The callback will be invoked on the EDT via SwingUtilities.invokeLater().
     *
     * @param callback Function to process received paths
     */
    public void setOnPathReceived(Consumer<PathMessage> callback) {
        this.onPathReceived = callback;
    }

    /**
     * Sets the callback for received edge messages.
     */
    public void setOnEdgeReceived(Consumer<EdgeMessage> callback) {
        this.onEdgeReceived = callback;
    }

    /**
     * Sets the callback for path removal messages.
     */
    public void setOnPathRemoved(Consumer<Integer> callback) {
        this.onPathRemoved = callback;
    }

    /**
     * Sets the callback for connection status changes.
     * The callback will be invoked on the EDT via SwingUtilities.invokeLater().
     *
     * @param callback Function to handle status changes
     */
    public void setOnStatusChanged(Consumer<ConnectionStatus> callback) {
        this.onStatusChanged = callback;
    }

    // ==================== Host Mode ====================

    /**
     * Starts the manager in HOST mode.
     * Creates a ServerSocket and waits for a client to connect.
     *
     * IMPORTANT: This method spawns a background thread and returns immediately.
     * The actual connection is established when a client connects.
     *
     * @param port The port to listen on
     */
    public void startHost(int port) {
        if (running.get()) {
            System.err.println("[NetworkManager] Already running, ignoring startHost()");
            return;
        }

        running.set(true);
        notifyStatusChanged(ConnectionStatus.WAITING);
        stateModel.setRole("HOST");

        // Create background thread to accept connections
        hostThread = new Thread(() -> {
            try {
                // Create server socket bound to the specified port
                serverSocket = new ServerSocket(port);
                System.out.println("[NetworkManager] Hosting on port " + port + ", waiting for client...");

                // Blocking call - waits for client connection
                socket = serverSocket.accept();
                socket.setSoTimeout(0); // No read timeout for normal operation
                socket.setKeepAlive(true);

                System.out.println("[NetworkManager] Client connected from: " + socket.getInetAddress());

                // Initialize streams
                initializeStreams();

                // Update state
                connected.set(true);
                stateModel.setConnected(true);
                notifyStatusChanged(ConnectionStatus.CONNECTED);

                // Start the receiver loop
                startReceiverLoop();

            } catch (SocketException e) {
                if (running.get()) {
                    System.err.println("[NetworkManager] Socket error in host mode: " + e.getMessage());
                    notifyStatusChanged(ConnectionStatus.ERROR);
                }
            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("[NetworkManager] IO error in host mode: " + e.getMessage());
                    notifyStatusChanged(ConnectionStatus.ERROR);
                }
            }
        }, "P2P-Host-Thread");

        hostThread.setDaemon(true);
        hostThread.start();
    }

    // ==================== Client Mode ====================

    /**
     * Starts the manager in CLIENT mode.
     * Attempts to connect to a host at the specified address and port.
     *
     * IMPORTANT: This method spawns a background thread and returns immediately.
     * Connection success/failure is reported via the onStatusChanged callback.
     *
     * @param host The host address to connect to
     * @param port The port to connect to
     */
    public void startClient(String host, int port) {
        if (running.get()) {
            System.err.println("[NetworkManager] Already running, ignoring startClient()");
            return;
        }

        running.set(true);
        notifyStatusChanged(ConnectionStatus.CONNECTING);
        stateModel.setRole("CLIENT");

        // Create background thread for connection
        Thread clientThread = new Thread(() -> {
            try {
                System.out.println("[NetworkManager] Connecting to " + host + ":" + port + "...");

                // Create socket and connect with timeout
                socket = new Socket();
                socket.connect(new InetSocketAddress(host, port), CONNECTION_TIMEOUT_MS);
                socket.setSoTimeout(0); // No read timeout for normal operation
                socket.setKeepAlive(true);

                System.out.println("[NetworkManager] Connected to host!");

                // Initialize streams
                initializeStreams();

                // Update state
                connected.set(true);
                stateModel.setConnected(true);
                notifyStatusChanged(ConnectionStatus.CONNECTED);

                // Start the receiver loop
                startReceiverLoop();

            } catch (SocketTimeoutException e) {
                System.err.println("[NetworkManager] Connection timed out");
                notifyStatusChanged(ConnectionStatus.ERROR);
                cleanup();
            } catch (ConnectException e) {
                System.err.println("[NetworkManager] Connection refused - is the host running?");
                notifyStatusChanged(ConnectionStatus.ERROR);
                cleanup();
            } catch (IOException e) {
                System.err.println("[NetworkManager] IO error in client mode: " + e.getMessage());
                notifyStatusChanged(ConnectionStatus.ERROR);
                cleanup();
            }
        }, "P2P-Client-Thread");

        clientThread.setDaemon(true);
        clientThread.start();
    }

    // ==================== Stream Initialization ====================

    /**
     * Initializes the input and output streams for the connected socket.
     * Uses BufferedReader/BufferedWriter for efficient line-based communication.
     *
     * @throws IOException If stream initialization fails
     */
    private void initializeStreams() throws IOException {
        // Output stream - autoflush enabled for immediate sends
        writer = new BufferedWriter(
            new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8)
        );

        // Input stream - buffered for efficient line reading
        reader = new BufferedReader(
            new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8)
        );
    }

    // ==================== Receiver Loop ====================

    /**
     * Starts the main receiver loop on the current thread.
     * This method blocks until the connection is closed or an error occurs.
     *
     * Protocol:
     * - TEXT messages: Regular text with escaped newlines
     * - POINT messages: Format "POINT:ADD/REMOVE:x:y:fromHost"
     */
    private void startReceiverLoop() {
        receiverThread = new Thread(() -> {
            System.out.println("[NetworkManager] Receiver loop started");

            try {
                String receivedLine;
                // readLine() blocks until a complete line is received
                while (running.get() && (receivedLine = reader.readLine()) != null) {

                    // Skip empty lines (could be heartbeats)
                    if (receivedLine.isEmpty()) {
                        continue;
                    }

                    // Handle heartbeat messages
                    if (receivedLine.equals("HEARTBEAT")) {
                        System.out.println("[NetworkManager] Heartbeat received");
                        continue;
                    }

                    // Check if this is a point message
                    if (receivedLine.startsWith("POINT:")) {
                        PointMessage pointMsg = PointMessage.deserialize(receivedLine);
                        if (pointMsg != null && onPointReceived != null) {
                            System.out.println("[NetworkManager] Received point: (" + pointMsg.getX() + ", " + pointMsg.getY() + ")");
                            javax.swing.SwingUtilities.invokeLater(() -> {
                                onPointReceived.accept(pointMsg);
                            });
                        }
                        continue;
                    }

                    // Check if this is a path message
                    if (receivedLine.startsWith("PATH:")) {
                        PathMessage pathMsg = PathMessage.deserialize(receivedLine);
                        if (pathMsg != null && onPathReceived != null) {
                            System.out.println("[NetworkManager] Received path");
                            javax.swing.SwingUtilities.invokeLater(() -> {
                                onPathReceived.accept(pathMsg);
                            });
                        }
                        continue;
                    }

                    // Check if this is an edge message
                    if (receivedLine.startsWith("EDGE:")) {
                        EdgeMessage edgeMsg = EdgeMessage.deserialize(receivedLine);
                        if (edgeMsg != null && onEdgeReceived != null) {
                            System.out.println("[NetworkManager] Received edge");
                            javax.swing.SwingUtilities.invokeLater(() -> {
                                onEdgeReceived.accept(edgeMsg);
                            });
                        }
                        continue;
                    }

                    // Check if this is a path remove message
                    if (receivedLine.startsWith("PATHREMOVE:")) {
                        try {
                            int pathIndex = Integer.parseInt(receivedLine.substring(11));
                            if (onPathRemoved != null) {
                                javax.swing.SwingUtilities.invokeLater(() -> {
                                    onPathRemoved.accept(pathIndex);
                                });
                            }
                        } catch (NumberFormatException e) { }
                        continue;
                    }

                    // Handle text message - unescape newlines
                    String unescapedText = receivedLine.replace("\\n", "\n").replace("\\\\", "\\");

                    System.out.println("[NetworkManager] Received: " + unescapedText.length() + " chars");

                    // Update UI on the EDT
                    final String text = unescapedText;
                    if (onTextReceived != null) {
                        javax.swing.SwingUtilities.invokeLater(() -> {
                            onTextReceived.accept(text);
                        });
                    }
                }

                // readLine() returned null - connection closed by peer
                System.out.println("[NetworkManager] Connection closed by peer");
                notifyStatusChanged(ConnectionStatus.DISCONNECTED);

            } catch (SocketException e) {
                if (running.get()) {
                    System.out.println("[NetworkManager] Socket closed: " + e.getMessage());
                }
            } catch (IOException e) {
                if (running.get()) {
                    System.err.println("[NetworkManager] Receiver error: " + e.getMessage());
                    notifyStatusChanged(ConnectionStatus.ERROR);
                }
            } finally {
                connected.set(false);
                stateModel.setConnected(false);
                cleanup();
            }
        }, "P2P-Receiver-Thread");

        receiverThread.setDaemon(true);
        receiverThread.start();
    }

    // ==================== Sending Data ====================

    /**
     * Sends text to the connected peer.
     * This method is thread-safe and can be called from any thread (typically EDT).
     *
     * Protocol: Newlines in the text are escaped as \\n (literal backslash-n)
     * before sending, since BufferedReader.readLine() uses \n as message delimiter.
     *
     * @param text The text to send
     * @return true if the send was successful, false otherwise
     */
    public synchronized boolean sendText(String text) {
        if (!connected.get() || writer == null) {
            System.err.println("[NetworkManager] Cannot send - not connected");
            return false;
        }

        try {
            // Escape newlines: replace \n with \\n literal to preserve multiline text
            // This ensures the entire content is sent as a single message
            String escapedText = text.replace("\\", "\\\\").replace("\n", "\\n");

            // Write the escaped text followed by newline delimiter
            writer.write(escapedText);
            writer.newLine();
            writer.flush(); // Ensure immediate transmission
            System.out.println("[NetworkManager] Sent: " + text.length() + " chars");
            return true;

        } catch (IOException e) {
            System.err.println("[NetworkManager] Send error: " + e.getMessage());
            notifyStatusChanged(ConnectionStatus.ERROR);
            cleanup();
            return false;
        }
    }

    /**
     * Sends a point message to the connected peer.
     * This method is thread-safe and can be called from any thread (typically EDT).
     *
     * @param point The point to send
     * @param action ADD or REMOVE
     * @param isFromHost true if this point is from the host
     * @return true if the send was successful, false otherwise
     */
    public synchronized boolean sendPoint(Point point, PointMessage.Action action, boolean isFromHost) {
        if (!connected.get() || writer == null) {
            System.err.println("[NetworkManager] Cannot send point - not connected");
            return false;
        }

        try {
            PointMessage msg = new PointMessage(point.getX(), point.getY(), action, isFromHost);
            String serialized = msg.serialize();

            writer.write(serialized);
            writer.newLine();
            writer.flush();

            System.out.println("[NetworkManager] Sent point: (" + point.getX() + ", " + point.getY() + ") " + action);
            return true;

        } catch (IOException e) {
            System.err.println("[NetworkManager] Send point error: " + e.getMessage());
            notifyStatusChanged(ConnectionStatus.ERROR);
            cleanup();
            return false;
        }
    }

    /**
     * Sends a path message to the connected peer.
     * This method is thread-safe and can be called from any thread (typically EDT).
     *
     * @param sourceX Source point X coordinate
     * @param sourceY Source point Y coordinate
     * @param destX Destination point X coordinate
     * @param destY Destination point Y coordinate
     * @return true if the send was successful, false otherwise
     */
    public synchronized boolean sendPath(double sourceX, double sourceY, double destX, double destY) {
        if (!connected.get() || writer == null) {
            System.err.println("[NetworkManager] Cannot send path - not connected");
            return false;
        }

        try {
            PathMessage msg = new PathMessage(sourceX, sourceY, destX, destY);
            writer.write(msg.serialize());
            writer.newLine();
            writer.flush();

            System.out.println("[NetworkManager] Sent path");
            return true;

        } catch (IOException e) {
            System.err.println("[NetworkManager] Send path error: " + e.getMessage());
            notifyStatusChanged(ConnectionStatus.ERROR);
            cleanup();
            return false;
        }
    }

    /**
     * Sends an edge message to the connected peer.
     */
    public synchronized boolean sendEdge(double x1, double y1, double x2, double y2) {
        if (!connected.get() || writer == null) {
            System.err.println("[NetworkManager] Cannot send edge - not connected");
            return false;
        }

        try {
            EdgeMessage msg = new EdgeMessage(x1, y1, x2, y2);
            writer.write(msg.serialize());
            writer.newLine();
            writer.flush();
            System.out.println("[NetworkManager] Sent edge");
            return true;
        } catch (IOException e) {
            System.err.println("[NetworkManager] Send edge error: " + e.getMessage());
            notifyStatusChanged(ConnectionStatus.ERROR);
            cleanup();
            return false;
        }
    }

    /**
     * Sends a path removal message to the connected peer.
     */
    public synchronized boolean sendPathRemove(int pathIndex) {
        if (!connected.get() || writer == null) {
            return false;
        }
        try {
            writer.write("PATHREMOVE:" + pathIndex);
            writer.newLine();
            writer.flush();
            System.out.println("[NetworkManager] Sent path remove");
            return true;
        } catch (IOException e) {
            System.err.println("[NetworkManager] Send path remove error: " + e.getMessage());
            return false;
        }
    }

    // ==================== Status Notifications ====================

    /**
     * Notifies the status callback of a connection status change.
     * Ensures the callback is invoked on the EDT.
     *
     * @param status The new connection status
     */
    private void notifyStatusChanged(ConnectionStatus status) {
        if (onStatusChanged != null) {
            javax.swing.SwingUtilities.invokeLater(() -> {
                onStatusChanged.accept(status);
            });
        }
    }

    // ==================== Cleanup and Shutdown ====================

    /**
     * Cleans up all network resources.
     * Safe to call multiple times.
     */
    private void cleanup() {
        System.out.println("[NetworkManager] Cleaning up resources...");

        // Close writer
        if (writer != null) {
            try {
                writer.close();
            } catch (IOException e) {
                // Ignore
            }
            writer = null;
        }

        // Close reader
        if (reader != null) {
            try {
                reader.close();
            } catch (IOException e) {
                // Ignore
            }
            reader = null;
        }

        // Close socket
        if (socket != null && !socket.isClosed()) {
            try {
                socket.close();
            } catch (IOException e) {
                // Ignore
            }
            socket = null;
        }

        // Close server socket
        if (serverSocket != null && !serverSocket.isClosed()) {
            try {
                serverSocket.close();
            } catch (IOException e) {
                // Ignore
            }
            serverSocket = null;
        }

        connected.set(false);
        stateModel.setConnected(false);
    }

    /**
     * Gracefully shuts down the network manager.
     * Stops all threads and releases resources.
     *
     * IMPORTANT: This method does NOT call System.exit().
     * Callers should update UI to reflect disconnected state.
     */
    public void shutdown() {
        System.out.println("[NetworkManager] Shutting down...");

        running.set(false);
        cleanup();

        // Interrupt threads to unblock any waiting operations
        if (receiverThread != null) {
            receiverThread.interrupt();
        }
        if (hostThread != null) {
            hostThread.interrupt();
        }
    }

    // ==================== Status Getters ====================

    /**
     * Checks if the manager has an active connection.
     *
     * @return true if connected
     */
    public boolean isConnected() {
        return connected.get();
    }

    /**
     * Checks if the manager is running (attempting to connect or connected).
     *
     * @return true if running
     */
    public boolean isRunning() {
        return running.get();
    }
}
