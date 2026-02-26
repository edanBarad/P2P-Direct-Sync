package com.tactical.p2p;

import java.io.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * AuditLogger - Records all events for security auditing.
 * Logs connections, messages, points, paths, zones, check-ins, etc.
 */
public class AuditLogger {

    private static final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    private final List<LogEntry> entries = new ArrayList<>();
    private final String role;

    /** Callback to notify when new entries are added (for auto-refresh) */
    private Runnable onEntryAdded;

    public AuditLogger(String role) {
        this.role = role;
    }

    /**
     * Sets a callback to be notified when a new log entry is added.
     * @param callback Runnable to execute when entry is added
     */
    public void setOnEntryAdded(Runnable callback) {
        this.onEntryAdded = callback;
    }

    public enum EventType {
        CONNECTION, DISCONNECTION,
        MESSAGE_SENT, MESSAGE_RECEIVED,
        POINT_ADDED, POINT_REMOVED,
        EDGE_ADDED,
        PATH_FOUND, PATH_REMOVED,
        ZONE_CREATED, ZONE_TRIGGERED, ZONE_REMOVED,
        PATROL_STARTED, PATROL_COMPLETED, PATROL_PROGRESS,
        CHECKIN_SUCCESS, CHECKIN_MISSED, CHECKIN_RESET,
        ALERT_NORMAL, ALERT_CRITICAL, ALERT_ACKNOWLEDGED,
        SELF_DESTRUCT,
        AUTH_ATTEMPT, AUTH_SUCCESS, AUTH_BLOCKED
    }

    public static class LogEntry {
        public final LocalDateTime timestamp;
        public final EventType type;
        public final String description;
        public final String details;

        LogEntry(EventType type, String description, String details) {
            this.timestamp = LocalDateTime.now();
            this.type = type;
            this.description = description;
            this.details = details;
        }

        @Override
        public String toString() {
            return String.format("[%s] [%s] %s%s",
                timestamp.format(formatter),
                type.name(),
                description,
                details.isEmpty() ? "" : " - " + details);
        }
    }

    public void log(EventType type, String description) {
        log(type, description, "");
    }

    public void log(EventType type, String description, String details) {
        LogEntry entry = new LogEntry(type, description, details);
        entries.add(entry);
        System.out.println("[AUDIT] " + entry);

        // Notify listener (for auto-refresh in UI)
        if (onEntryAdded != null) {
            onEntryAdded.run();
        }
    }

    public List<LogEntry> getEntries() {
        return new ArrayList<>(entries);
    }

    /**
     * Export log to file.
     */
    public void exportToFile(File file) throws IOException {
        try (PrintWriter writer = new PrintWriter(new FileWriter(file))) {
            writer.println("=== P2P Security Audit Log ===");
            writer.println("Role: " + role);
            writer.println("Exported: " + LocalDateTime.now().format(formatter));
            writer.println("Total Entries: " + entries.size());
            writer.println("================================\n");

            for (LogEntry entry : entries) {
                writer.println(entry.toString());
            }
        }
    }

    /**
     * Export log as string.
     */
    public String exportAsString() {
        StringBuilder sb = new StringBuilder();
        sb.append("=== P2P Security Audit Log ===\n");
        sb.append("Role: ").append(role).append("\n");
        sb.append("Exported: ").append(LocalDateTime.now().format(formatter)).append("\n");
        sb.append("Total Entries: ").append(entries.size()).append("\n");
        sb.append("================================\n\n");

        for (LogEntry entry : entries) {
            sb.append(entry.toString()).append("\n");
        }
        return sb.toString();
    }

    /**
     * Clear all log entries.
     */
    public void clear() {
        entries.clear();
        log(EventType.CONNECTION, "Audit log cleared");
    }
}
