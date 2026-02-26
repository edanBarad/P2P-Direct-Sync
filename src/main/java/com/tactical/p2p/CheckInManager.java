package com.tactical.p2p;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;

/**
 * CheckInManager - Manages the check-in system for lone worker safety.
 * Requires periodic check-ins; alerts the other user if missed.
 */
public class CheckInManager {

    private final int intervalSeconds;
    private Timer countdownTimer;
    private int secondsRemaining;
    private boolean enabled = false;
    private boolean checkedIn = false;

    private final AuditLogger auditLogger;
    private Consumer<String> onAlert;
    private Runnable onMissedCheckIn;
    private Consumer<Integer> onTick;

    private JButton checkInButton;
    private JLabel statusLabel;

    public CheckInManager(int intervalSeconds, AuditLogger auditLogger) {
        this.intervalSeconds = intervalSeconds;
        this.auditLogger = auditLogger;
        this.secondsRemaining = intervalSeconds;
    }

    /**
     * Create the check-in panel UI.
     */
    public JPanel createPanel() {
        JPanel panel = new JPanel(new BorderLayout(5, 0));
        panel.setBorder(BorderFactory.createTitledBorder("Check-in System"));

        statusLabel = new JLabel("Disabled");
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        statusLabel.setForeground(Color.GRAY);

        checkInButton = new JButton("Check In");
        checkInButton.setEnabled(false);
        checkInButton.addActionListener(e -> performCheckIn());

        JButton startBtn = new JButton("Start");
        JButton stopBtn = new JButton("Stop");

        startBtn.addActionListener(e -> start());
        stopBtn.addActionListener(e -> stop());

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        buttonPanel.add(startBtn);
        buttonPanel.add(stopBtn);
        buttonPanel.add(Box.createHorizontalStrut(20));
        buttonPanel.add(checkInButton);

        panel.add(statusLabel, BorderLayout.NORTH);
        panel.add(buttonPanel, BorderLayout.CENTER);

        return panel;
    }

    /**
     * Start the check-in timer.
     */
    public void start() {
        if (enabled) return;

        enabled = true;
        checkedIn = false;
        secondsRemaining = intervalSeconds;

        if (auditLogger != null) {
            auditLogger.log(AuditLogger.EventType.CHECKIN_RESET,
                "Check-in system started", "Interval: " + intervalSeconds + "s");
        }

        checkInButton.setEnabled(true);
        updateStatus();

        countdownTimer = new Timer(1000, e -> {
            secondsRemaining--;
            updateStatus();

            if (onTick != null) {
                onTick.accept(secondsRemaining);
            }

            if (secondsRemaining <= 0) {
                handleMissedCheckIn();
            }
        });
        countdownTimer.start();
    }

    /**
     * Stop the check-in timer.
     */
    public void stop() {
        enabled = false;
        if (countdownTimer != null) {
            countdownTimer.stop();
            countdownTimer = null;
        }
        checkInButton.setEnabled(false);
        statusLabel.setText("Disabled");
        statusLabel.setForeground(Color.GRAY);

        if (auditLogger != null) {
            auditLogger.log(AuditLogger.EventType.CHECKIN_RESET, "Check-in system stopped");
        }
    }

    /**
     * Perform check-in.
     */
    public void performCheckIn() {
        if (!enabled) return;

        checkedIn = true;
        secondsRemaining = intervalSeconds;
        updateStatus();

        if (auditLogger != null) {
            auditLogger.log(AuditLogger.EventType.CHECKIN_SUCCESS, "User checked in");
        }

        if (onAlert != null) {
            onAlert.accept("✓ Check-in received");
        }
    }

    /**
     * Handle missed check-in.
     */
    private void handleMissedCheckIn() {
        // Null check - timer could be null if stop() was called just before this
        if (countdownTimer != null) {
            countdownTimer.stop();
        }
        checkInButton.setEnabled(false);
        statusLabel.setText("⚠ MISSED CHECK-IN!");
        statusLabel.setForeground(Color.RED);

        if (auditLogger != null) {
            auditLogger.log(AuditLogger.EventType.CHECKIN_MISSED, "Check-in missed!", "Alert sent to peer");
        }

        if (onMissedCheckIn != null) {
            onMissedCheckIn.run();
        }

        // Flash the status
        Timer flashTimer = new Timer(500, null);
        final int[] count = {0};
        flashTimer.addActionListener(e -> {
            statusLabel.setBackground(count[0] % 2 == 0 ? Color.RED : Color.WHITE);
            count[0]++;
            if (count[0] >= 10) {
                flashTimer.stop();
                statusLabel.setBackground(null);
                // Reset after alert - create new timer if needed
                secondsRemaining = intervalSeconds;
                if (countdownTimer != null) {
                    countdownTimer.start();
                }
                checkInButton.setEnabled(true);
            }
        });
        flashTimer.start();
    }

    private void updateStatus() {
        if (statusLabel != null) {
            if (secondsRemaining <= 10) {
                statusLabel.setText("⚠ Check-in: " + secondsRemaining + "s");
                statusLabel.setForeground(Color.RED);
            } else if (secondsRemaining <= 30) {
                statusLabel.setText("Check-in: " + secondsRemaining + "s");
                statusLabel.setForeground(Color.ORANGE);
            } else {
                statusLabel.setText("Check-in: " + secondsRemaining + "s");
                statusLabel.setForeground(new Color(0, 128, 0));
            }
        }
    }

    /**
     * Remote check-in received (other user checked in).
     */
    public void remoteCheckIn() {
        if (auditLogger != null) {
            auditLogger.log(AuditLogger.EventType.CHECKIN_SUCCESS, "Remote user checked in");
        }
    }

    /**
     * Remote check-in missed notification.
     */
    public void remoteMissedCheckIn() {
        if (auditLogger != null) {
            auditLogger.log(AuditLogger.EventType.CHECKIN_MISSED, "Remote user missed check-in");
        }
    }

    public void setOnAlert(Consumer<String> callback) { this.onAlert = callback; }
    public void setOnMissedCheckIn(Runnable callback) { this.onMissedCheckIn = callback; }
    public void setOnTick(Consumer<Integer> callback) { this.onTick = callback; }
    public boolean isEnabled() { return enabled; }
    public int getSecondsRemaining() { return secondsRemaining; }
}
