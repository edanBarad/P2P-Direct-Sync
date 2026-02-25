package com.tactical.p2p;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AuthManager - Handles PIN-based authentication for P2P connection.
 *
 * Flow:
 * 1. Host sets a 4-digit PIN on startup
 * 2. Client must enter correct PIN to connect
 * 3. 3 attempts maximum
 * 4. Host gets notified on failed attempts
 * 5. After 3 fails, client is blocked and must restart
 */
public class AuthManager {

    private String pin;
    private final AtomicInteger failedAttempts = new AtomicInteger(0);
    private static final int MAX_ATTEMPTS = 3;
    private boolean authenticated = false;

    private AuthEventListener eventListener;

    public interface AuthEventListener {
        void onAuthAttempt(String attempt, int attemptNumber, boolean success);
        void onAuthBlocked();
        void onAuthSuccess();
    }

    public AuthManager() {
    }

    /**
     * Shows a dialog for the host to set a PIN.
     * @return true if PIN was set successfully
     */
    public boolean showPinSetupDialog(Component parent) {
        JPanel panel = new JPanel(new GridLayout(2, 2, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPasswordField pinField = new JPasswordField(10);
        JPasswordField confirmField = new JPasswordField(10);

        panel.add(new JLabel("Enter 4-digit PIN:"));
        panel.add(pinField);
        panel.add(new JLabel("Confirm PIN:"));
        panel.add(confirmField);

        int result = JOptionPane.showConfirmDialog(
            parent,
            panel,
            "Set Authentication PIN",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            return false;
        }

        String pin1 = new String(pinField.getPassword());
        String pin2 = new String(confirmField.getPassword());

        // Validate PIN
        if (!pin1.equals(pin2)) {
            JOptionPane.showMessageDialog(parent,
                "PINs do not match!",
                "Error",
                JOptionPane.ERROR_MESSAGE);
            return showPinSetupDialog(parent); // Retry
        }

        if (!pin1.matches("\\d{4}")) {
            JOptionPane.showMessageDialog(parent,
                "PIN must be exactly 4 digits!",
                "Error",
                JOptionPane.ERROR_MESSAGE);
            return showPinSetupDialog(parent); // Retry
        }

        this.pin = pin1;
        System.out.println("[AuthManager] PIN set successfully");
        return true;
    }

    /**
     * Shows a dialog for the client to enter the PIN.
     * @param remainingAttempts Number of attempts remaining (from server)
     * @return the entered PIN, or null if cancelled
     */
    public String showPinEntryDialog(Component parent, int remainingAttempts) {
        JPanel panel = new JPanel(new GridLayout(3, 1, 10, 10));
        panel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JPasswordField pinField = new JPasswordField(10);
        JLabel attemptsLabel = new JLabel("Attempts remaining: " + remainingAttempts);
        attemptsLabel.setFont(new Font("Arial", Font.BOLD, 14));
        attemptsLabel.setForeground(remainingAttempts < MAX_ATTEMPTS ? Color.RED : new Color(0, 128, 0));
        attemptsLabel.setHorizontalAlignment(SwingConstants.CENTER);

        panel.add(new JLabel("Enter the 4-digit PIN to connect:"));
        panel.add(pinField);
        panel.add(attemptsLabel);

        int result = JOptionPane.showConfirmDialog(
            parent,
            panel,
            "Authentication Required",
            JOptionPane.OK_CANCEL_OPTION,
            JOptionPane.PLAIN_MESSAGE
        );

        if (result != JOptionPane.OK_OPTION) {
            return null;
        }

        return new String(pinField.getPassword());
    }

    /**
     * Shows a dialog for the client to enter the PIN (first attempt).
     * @return the entered PIN, or null if cancelled
     */
    public String showPinEntryDialog(Component parent) {
        return showPinEntryDialog(parent, MAX_ATTEMPTS);
    }

    /**
     * Validates a PIN attempt (called by host).
     * @param attempt the PIN to validate
     * @return true if correct
     */
    public boolean validatePin(String attempt) {
        boolean success = pin != null && pin.equals(attempt);
        int attemptNum = failedAttempts.incrementAndGet();

        if (eventListener != null) {
            eventListener.onAuthAttempt(attempt, attemptNum, success);
        }

        if (success) {
            authenticated = true;
            if (eventListener != null) {
                eventListener.onAuthSuccess();
            }
            return true;
        }

        if (attemptNum >= MAX_ATTEMPTS) {
            if (eventListener != null) {
                eventListener.onAuthBlocked();
            }
        }

        return false;
    }

    /**
     * Called by client to check if they should be blocked.
     */
    public boolean isBlocked() {
        return failedAttempts.get() >= MAX_ATTEMPTS;
    }

    /**
     * Resets failed attempts counter.
     */
    public void resetAttempts() {
        failedAttempts.set(0);
    }

    /**
     * Gets remaining attempts.
     */
    public int getRemainingAttempts() {
        return Math.max(0, MAX_ATTEMPTS - failedAttempts.get());
    }

    /**
     * Gets the current failed attempt count.
     */
    public int getFailedAttempts() {
        return failedAttempts.get();
    }

    public void setEventListener(AuthEventListener listener) {
        this.eventListener = listener;
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public String getPin() {
        return pin;
    }

    public void setPin(String pin) {
        this.pin = pin;
    }
}
