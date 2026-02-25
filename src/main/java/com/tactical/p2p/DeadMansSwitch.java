package com.tactical.p2p;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.function.Consumer;

/**
 * DeadMansSwitch - Emergency alert system triggered when a key is released.
 *
 * The operator holds down a designated key (SPACE by default). If they release
 * it without properly disarming (e.g., due to incapacitation or duress),
 * an immediate alert is sent to the connected peer.
 *
 * Features:
 * - Visual indicator while key is held
 * - Countdown grace period (3 seconds) before alert triggers
 * - Audio/visual alarm on receiving end
 * - Logs to audit trail
 */
public class DeadMansSwitch {

    private static final int GRACE_PERIOD_MS = 3000; // 3 second grace period
    private static final int CHECK_INTERVAL_MS = 100;

    private boolean armed = false;
    private boolean keyHeld = false;
    private boolean triggered = false;
    private long keyReleasedTime = 0;
    private int graceCountdown = 0;

    private final int activationKey; // KeyCode, default SPACE
    private Timer countdownTimer;
    private Timer graceTimer;

    private JPanel indicatorPanel;
    private JLabel statusLabel;
    private JProgressBar countdownBar;

    private Consumer<String> onAlertTriggered;
    private Consumer<String> onAlertReceived;
    private Consumer<Integer> onCountdownTick;
    private Runnable onAlertCancelled;
    private AuditLogger auditLogger;

    private JFrame parentFrame;

    public DeadMansSwitch() {
        this(KeyEvent.VK_SPACE);
    }

    public DeadMansSwitch(int activationKey) {
        this.activationKey = activationKey;
    }

    /**
     * Creates the visual indicator panel for the switch.
     */
    public JPanel createIndicatorPanel() {
        indicatorPanel = new JPanel(new BorderLayout(5, 5));
        indicatorPanel.setBorder(BorderFactory.createCompoundBorder(
            BorderFactory.createTitledBorder("Dead Man's Switch"),
            BorderFactory.createEmptyBorder(5, 5, 5, 5)
        ));
        indicatorPanel.setBackground(new Color(60, 60, 60));

        statusLabel = new JLabel("HOLD SPACE to arm", JLabel.CENTER);
        statusLabel.setFont(new Font("Arial", Font.BOLD, 14));
        statusLabel.setForeground(Color.LIGHT_GRAY);
        statusLabel.setOpaque(true);
        statusLabel.setBackground(new Color(60, 60, 60));

        countdownBar = new JProgressBar(0, GRACE_PERIOD_MS / CHECK_INTERVAL_MS);
        countdownBar.setStringPainted(true);
        countdownBar.setString("Ready");
        countdownBar.setForeground(new Color(0, 150, 0));
        countdownBar.setBackground(new Color(40, 40, 40));
        countdownBar.setValue(0);

        JLabel instructionLabel = new JLabel("Release triggers alert after 3s grace period");
        instructionLabel.setFont(new Font("Arial", Font.PLAIN, 10));
        instructionLabel.setForeground(Color.GRAY);
        instructionLabel.setHorizontalAlignment(SwingConstants.CENTER);

        indicatorPanel.add(statusLabel, BorderLayout.CENTER);
        indicatorPanel.add(countdownBar, BorderLayout.SOUTH);
        indicatorPanel.add(instructionLabel, BorderLayout.NORTH);

        return indicatorPanel;
    }

    /**
     * Attaches key listeners to a component.
     */
    public void attachToComponent(Component component, JFrame frame) {
        this.parentFrame = frame;

        component.addKeyListener(new KeyAdapter() {
            @Override
            public void keyPressed(KeyEvent e) {
                if (e.getKeyCode() == activationKey && !keyHeld && !triggered) {
                    activateSwitch();
                }
            }

            @Override
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == activationKey && keyHeld) {
                    releaseSwitch();
                }
            }
        });

        // Make component focusable
        component.setFocusable(true);
        component.requestFocusInWindow();
    }

    /**
     * Called when the activation key is pressed.
     */
    private void activateSwitch() {
        keyHeld = true;
        armed = true;
        keyReleasedTime = 0;

        // Cancel any running grace timer
        if (graceTimer != null && graceTimer.isRunning()) {
            graceTimer.stop();
        }

        updateIndicator();

        if (auditLogger != null) {
            auditLogger.log(AuditLogger.EventType.ALERT_NORMAL,
                "Dead Man's Switch armed", "Key held down");
        }
    }

    /**
     * Called when the activation key is released.
     */
    private void releaseSwitch() {
        if (!keyHeld) return;

        keyHeld = false;
        keyReleasedTime = System.currentTimeMillis();
        graceCountdown = GRACE_PERIOD_MS / CHECK_INTERVAL_MS;

        updateIndicator();

        // Start grace period countdown
        graceTimer = new Timer(CHECK_INTERVAL_MS, e -> {
            graceCountdown--;

            if (onCountdownTick != null) {
                onCountdownTick.accept(graceCountdown);
            }

            updateCountdownBar();

            if (graceCountdown <= 0) {
                graceTimer.stop();
                triggerAlert();
            }
        });
        graceTimer.start();

        if (auditLogger != null) {
            auditLogger.log(AuditLogger.EventType.ALERT_NORMAL,
                "Dead Man's Switch grace period started", graceCountdown + " ticks remaining");
        }
    }

    /**
     * Cancels the grace period (user re-pressed the key).
     */
    public void cancelGracePeriod() {
        if (graceTimer != null && graceTimer.isRunning()) {
            graceTimer.stop();
        }
        keyHeld = true;
        keyReleasedTime = 0;
        graceCountdown = 0;
        triggered = false;

        updateIndicator();
        updateCountdownBar();

        if (onAlertCancelled != null) {
            onAlertCancelled.run();
        }

        if (auditLogger != null) {
            auditLogger.log(AuditLogger.EventType.ALERT_NORMAL,
                "Dead Man's Switch grace period cancelled", "User re-pressed key");
        }
    }

    private void triggerAlert() {
        triggered = true;
        armed = false;

        updateIndicator();

        if (auditLogger != null) {
            auditLogger.log(AuditLogger.EventType.ALERT_CRITICAL,
                "DEAD MAN'S SWITCH TRIGGERED!", "Emergency alert sent to peer");
        }

        if (onAlertTriggered != null) {
            onAlertTriggered.accept("DEAD_MAN_SWITCH_TRIGGERED");
        }

        // Visual alarm on this end too
        showTriggeredAlarm();
    }

    private void updateIndicator() {
        if (statusLabel == null) return;

        SwingUtilities.invokeLater(() -> {
            if (triggered) {
                statusLabel.setText("!!! ALERT TRIGGERED !!!");
                statusLabel.setBackground(Color.RED);
                statusLabel.setForeground(Color.WHITE);
            } else if (keyHeld) {
                statusLabel.setText("ARMED - Keep holding SPACE");
                statusLabel.setBackground(new Color(0, 100, 0));
                statusLabel.setForeground(Color.WHITE);
            } else if (keyReleasedTime > 0) {
                statusLabel.setText("GRACE PERIOD - Press SPACE!");
                statusLabel.setBackground(Color.ORANGE);
                statusLabel.setForeground(Color.BLACK);
            } else {
                statusLabel.setText("HOLD SPACE to arm");
                statusLabel.setBackground(new Color(60, 60, 60));
                statusLabel.setForeground(Color.LIGHT_GRAY);
            }
        });
    }

    private void updateCountdownBar() {
        if (countdownBar == null) return;

        SwingUtilities.invokeLater(() -> {
            int max = GRACE_PERIOD_MS / CHECK_INTERVAL_MS;
            countdownBar.setValue(max - graceCountdown);
            countdownBar.setString(graceCountdown > 0 ?
                "Alert in: " + (graceCountdown * CHECK_INTERVAL_MS / 1000.0) + "s" : "Ready");

            if (graceCountdown > 0) {
                countdownBar.setForeground(graceCountdown > 20 ? Color.ORANGE : Color.RED);
            } else {
                countdownBar.setForeground(new Color(0, 150, 0));
            }
        });
    }

    private void showTriggeredAlert() {
        if (parentFrame == null) return;

        // Full screen red flash
        JDialog alertDialog = new JDialog(parentFrame, "EMERGENCY ALERT", true);
        alertDialog.setUndecorated(true);
        alertDialog.setBackground(Color.RED);

        JPanel panel = new JPanel(new BorderLayout());
        panel.setBackground(Color.RED);

        JLabel alertLabel = new JLabel(
            "<html><center><h1>DEAD MAN'S SWITCH</h1>" +
            "<h2>ALERT TRIGGERED BY PEER</h2>" +
            "<p>Operator may be compromised!</p></center></html>",
            JLabel.CENTER);
        alertLabel.setForeground(Color.WHITE);
        alertLabel.setFont(new Font("Arial", Font.BOLD, 24));

        JButton ackButton = new JButton("ACKNOWLEDGE ALERT");
        ackButton.setFont(new Font("Arial", Font.BOLD, 18));
        ackButton.setBackground(Color.WHITE);
        ackButton.setForeground(Color.RED);
        ackButton.addActionListener(e -> {
            alertDialog.dispose();
            if (auditLogger != null) {
                auditLogger.log(AuditLogger.EventType.ALERT_ACKNOWLEDGED,
                    "Dead Man's Switch alert acknowledged");
            }
        });

        panel.add(alertLabel, BorderLayout.CENTER);
        panel.add(ackButton, BorderLayout.SOUTH);

        alertDialog.setContentPane(panel);
        alertDialog.setSize(400, 250);
        alertDialog.setLocationRelativeTo(parentFrame);

        // Flash effect
        Timer flashTimer = new Timer(300, null);
        final int[] count = {0};
        flashTimer.addActionListener(e -> {
            panel.setBackground(count[0] % 2 == 0 ? Color.RED : new Color(139, 0, 0));
            count[0]++;
        });
        flashTimer.start();

        // Sound alarm
        Toolkit.getDefaultToolkit().beep();
        Timer beepTimer = new Timer(500, e -> Toolkit.getDefaultToolkit().beep());
        beepTimer.start();

        alertDialog.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosed(WindowEvent e) {
                flashTimer.stop();
                beepTimer.stop();
            }
        });

        alertDialog.setVisible(true);
    }

    private void showTriggeredAlarm() {
        if (parentFrame == null) return;

        // Flash the main frame
        Timer flashTimer = new Timer(200, null);
        final int[] count = {0};
        final Color original = parentFrame.getBackground();

        flashTimer.addActionListener(e -> {
            if (count[0] % 2 == 0) {
                parentFrame.getContentPane().setBackground(Color.RED);
            } else {
                parentFrame.getContentPane().setBackground(original);
            }
            count[0]++;
            if (count[0] >= 10) {
                flashTimer.stop();
                parentFrame.getContentPane().setBackground(original);
            }
        });
        flashTimer.start();

        // Continuous beep
        for (int i = 0; i < 5; i++) {
            try {
                Thread.sleep(200);
                Toolkit.getDefaultToolkit().beep();
            } catch (InterruptedException ignored) {}
        }
    }

    /**
     * Called when receiving a Dead Man's Switch alert from peer.
     */
    public void receiveAlert(String from) {
        if (auditLogger != null) {
            auditLogger.log(AuditLogger.EventType.ALERT_CRITICAL,
                "DEAD MAN'S SWITCH ALERT RECEIVED", "From: " + from);
        }

        if (onAlertReceived != null) {
            onAlertReceived.accept(from);
        }

        showTriggeredAlert();
    }

    /**
     * Resets the switch to initial state.
     */
    public void reset() {
        keyHeld = false;
        armed = false;
        triggered = false;
        keyReleasedTime = 0;
        graceCountdown = 0;

        if (graceTimer != null && graceTimer.isRunning()) {
            graceTimer.stop();
        }

        updateIndicator();
        updateCountdownBar();
    }

    // ==================== Getters/Setters ====================

    public void setOnAlertTriggered(Consumer<String> callback) {
        this.onAlertTriggered = callback;
    }

    public void setOnAlertReceived(Consumer<String> callback) {
        this.onAlertReceived = callback;
    }

    public void setOnCountdownTick(Consumer<Integer> callback) {
        this.onCountdownTick = callback;
    }

    public void setOnAlertCancelled(Runnable callback) {
        this.onAlertCancelled = callback;
    }

    public void setAuditLogger(AuditLogger logger) {
        this.auditLogger = logger;
    }

    public boolean isArmed() {
        return armed;
    }

    public boolean isTriggered() {
        return triggered;
    }

    public boolean isKeyHeld() {
        return keyHeld;
    }
}
