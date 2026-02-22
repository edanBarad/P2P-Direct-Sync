package com.tactical.p2p;

import java.util.ArrayList;
import java.util.List;

/**
 * StateModel - Local-First State Holder
 *
 * This class holds the string buffers for Top (remote) and Bottom (local) sections.
 * It implements a simple observer pattern to notify UI components of state changes.
 *
 * Thread Safety: All mutation methods are synchronized to ensure thread-safe access
 * from both the Event Dispatch Thread (EDT) and Network threads.
 */
public class StateModel {

    /** Text received from the remote peer (displayed in top panel) */
    private volatile String remoteText = "";

    /** Text typed locally (displayed in bottom panel) */
    private volatile String localText = "";

    /** Connection status flag */
    private volatile boolean connected = false;

    /** Current role: "HOST", "CLIENT", or "NONE" */
    private volatile String role = "NONE";

    /** Listeners for state changes */
    private final List<StateChangeListener> listeners = new ArrayList<>();

    /**
     * Functional interface for state change notifications.
     * Allows UI components to react to model updates from any thread.
     */
    @FunctionalInterface
    public interface StateChangeListener {
        void onStateChanged(StateModel model);
    }

    /**
     * Adds a listener to be notified of state changes.
     * Listeners are called on the thread that modified the state.
     *
     * @param listener The callback to invoke on state changes
     */
    public synchronized void addListener(StateChangeListener listener) {
        listeners.add(listener);
    }

    /**
     * Notifies all registered listeners of a state change.
     * Should be called after any state mutation.
     */
    private synchronized void notifyListeners() {
        for (StateChangeListener listener : listeners) {
            listener.onStateChanged(this);
        }
    }

    // ==================== Remote Text (Top Panel) ====================

    /**
     * Gets the remote text (received from peer).
     * Thread-safe via volatile read.
     *
     * @return The current remote text content
     */
    public String getRemoteText() {
        return remoteText;
    }

    /**
     * Sets the remote text from network reception.
     * Called by NetworkManager on background thread.
     *
     * @param text The new remote text
     */
    public synchronized void setRemoteText(String text) {
        this.remoteText = text != null ? text : "";
        notifyListeners();
    }

    // ==================== Local Text (Bottom Panel) ====================

    /**
     * Gets the local text (typed by user).
     * Thread-safe via volatile read.
     *
     * @return The current local text content
     */
    public String getLocalText() {
        return localText;
    }

    /**
     * Sets the local text from UI input.
     * Called by SyncBoardUI on the EDT.
     *
     * @param text The new local text
     */
    public synchronized void setLocalText(String text) {
        this.localText = text != null ? text : "";
        notifyListeners();
    }

    // ==================== Connection Status ====================

    /**
     * Checks if there is an active connection.
     *
     * @return true if connected to a peer
     */
    public boolean isConnected() {
        return connected;
    }

    /**
     * Sets the connection status.
     * Called by NetworkManager on connection state changes.
     *
     * @param connected The new connection status
     */
    public synchronized void setConnected(boolean connected) {
        this.connected = connected;
        notifyListeners();
    }

    // ==================== Role ====================

    /**
     * Gets the current role (HOST, CLIENT, or NONE).
     *
     * @return The current role string
     */
    public String getRole() {
        return role;
    }

    /**
     * Sets the role and updates connection status accordingly.
     *
     * @param role The new role
     */
    public synchronized void setRole(String role) {
        this.role = role;
        notifyListeners();
    }

    /**
     * Resets all state to initial values.
     * Called on disconnection or error.
     */
    public synchronized void reset() {
        this.remoteText = "";
        this.connected = false;
        // Note: localText is preserved for user convenience
        notifyListeners();
    }
}
