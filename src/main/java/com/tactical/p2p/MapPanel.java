package com.tactical.p2p;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

/**
 * MapPanel - Shared map view for pinning and syncing points.
 *
 * Features:
 * - Click to add a point
 * - Right-click on point to delete
 * - Host points are blue, Client points are red
 * - Points sync in real-time between instances
 */
public class MapPanel extends JPanel {

    private static final int POINT_RADIUS = 8;
    private static final Color HOST_COLOR = new Color(30, 144, 255);   // Dodger blue
    private static final Color CLIENT_COLOR = new Color(220, 20, 60);  // Crimson
    private static final Color HOVER_COLOR = new Color(255, 215, 0);   // Gold (hover highlight)

    /** The map background image */
    private BufferedImage mapImage;

    /** List of points from the host (blue) */
    private final List<Point> hostPoints = new ArrayList<>();

    /** List of points from the client (red) */
    private final List<Point> clientPoints = new ArrayList<>();

    /** Is this instance the host? */
    private boolean isHost = true;

    /** Point currently being hovered over (for deletion preview) */
    private Point hoveredPoint = null;
    private boolean hoveredIsHost = false;

    /** Callback when a point is added locally */
    private Consumer<Point> onPointAdded;

    /** Callback when a point is removed locally (includes which list it was from) */
    private Consumer<PointRemovalInfo> onPointRemoved;

    /** Info about a removed point including which side it belonged to */
    public static class PointRemovalInfo {
        public final Point point;
        public final boolean wasHostPoint;  // true if it was a host (blue) point

        public PointRemovalInfo(Point point, boolean wasHostPoint) {
            this.point = point;
            this.wasHostPoint = wasHostPoint;
        }
    }

    public MapPanel() {
        loadMapImage();
        setupMouseListeners();
    }

    private void loadMapImage() {
        try {
            // Try loading from classpath resources
            var stream = getClass().getClassLoader().getResourceAsStream("map.jpg");
            if (stream != null) {
                mapImage = ImageIO.read(stream);
                System.out.println("[MapPanel] Map image loaded from classpath");
                return;
            }

            // Fallback: try direct file path
            java.io.File file = new java.io.File("src/main/resources/map.jpg");
            if (file.exists()) {
                mapImage = ImageIO.read(file);
                System.out.println("[MapPanel] Map image loaded from file");
                return;
            }

            // Also try target/classes for compiled runs
            file = new java.io.File("target/classes/map.jpg");
            if (file.exists()) {
                mapImage = ImageIO.read(file);
                System.out.println("[MapPanel] Map image loaded from target/classes");
                return;
            }

            System.err.println("[MapPanel] Map image not found, using placeholder");
            mapImage = null;
        } catch (Exception e) {
            System.err.println("[MapPanel] Error loading map: " + e.getMessage());
            mapImage = null;
        }
    }

    private void setupMouseListeners() {
        // Left click - add point
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    addPointAt(e.getX(), e.getY());
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    tryDeletePointAt(e.getX(), e.getY());
                }
            }
        });

        // Mouse motion - hover detection for deletion preview
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e.getX(), e.getY());
            }
        });
    }

    /**
     * Sets whether this instance is the host.
     * Determines the color of new points.
     */
    public void setIsHost(boolean isHost) {
        this.isHost = isHost;
    }

    // ==================== Point Operations ====================

    private void addPointAt(int x, int y) {
        Point p = new Point(x, y);

        if (isHost) {
            hostPoints.add(p);
        } else {
            clientPoints.add(p);
        }

        repaint();

        if (onPointAdded != null) {
            onPointAdded.accept(p);
        }
    }

    private void tryDeletePointAt(int x, int y) {
        // Check host points first
        for (int i = hostPoints.size() - 1; i >= 0; i--) {
            Point p = hostPoints.get(i);
            if (isPointHit(p, x, y)) {
                hostPoints.remove(i);
                repaint();
                if (onPointRemoved != null) {
                    onPointRemoved.accept(new PointRemovalInfo(p, true));
                }
                return;
            }
        }

        // Check client points
        for (int i = clientPoints.size() - 1; i >= 0; i--) {
            Point p = clientPoints.get(i);
            if (isPointHit(p, x, y)) {
                clientPoints.remove(i);
                repaint();
                if (onPointRemoved != null) {
                    onPointRemoved.accept(new PointRemovalInfo(p, false));
                }
                return;
            }
        }
    }

    private boolean isPointHit(Point p, int mouseX, int mouseY) {
        double dist = p.distance(new Point(mouseX, mouseY));
        return dist <= POINT_RADIUS + 4; // Small buffer for easier clicking
    }

    private void updateHover(int x, int y) {
        Point oldHover = hoveredPoint;

        hoveredPoint = null;
        hoveredIsHost = false;

        // Check all points
        for (Point p : hostPoints) {
            if (isPointHit(p, x, y)) {
                hoveredPoint = p;
                hoveredIsHost = true;
                break;
            }
        }

        if (hoveredPoint == null) {
            for (Point p : clientPoints) {
                if (isPointHit(p, x, y)) {
                    hoveredPoint = p;
                    hoveredIsHost = false;
                    break;
                }
            }
        }

        if (hoveredPoint != oldHover) {
            repaint();
        }
    }

    // ==================== Network Sync ====================

    /**
     * Adds a point received from the remote peer.
     * Called by NetworkManager when a point message is received.
     */
    public void addRemotePoint(double x, double y, boolean fromHost) {
        Point p = new Point(x, y);

        SwingUtilities.invokeLater(() -> {
            if (fromHost) {
                hostPoints.add(p);
            } else {
                clientPoints.add(p);
            }
            repaint();
        });
    }

    /**
     * Removes a point received from the remote peer.
     * Searches both host and client points since either side can delete any point.
     */
    public void removeRemotePoint(double x, double y, boolean fromHost) {
        SwingUtilities.invokeLater(() -> {
            Point target = new Point(x, y);
            // Search both lists - the point could be from either side
            boolean removed = hostPoints.removeIf(p -> p.equals(target));
            if (!removed) {
                clientPoints.removeIf(p -> p.equals(target));
            }
            repaint();
        });
    }

    /**
     * Clears all points (for reset).
     */
    public void clearPoints() {
        hostPoints.clear();
        clientPoints.clear();
        repaint();
    }

    // ==================== Callbacks ====================

    public void setOnPointAdded(Consumer<Point> callback) {
        this.onPointAdded = callback;
    }

    public void setOnPointRemoved(Consumer<PointRemovalInfo> callback) {
        this.onPointRemoved = callback;
    }

    // ==================== Rendering ====================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw map background
        if (mapImage != null) {
            g2d.drawImage(mapImage, 0, 0, getWidth(), getHeight(), null);
        } else {
            // Placeholder background
            g2d.setColor(new Color(240, 230, 210));
            g2d.fillRect(0, 0, getWidth(), getHeight());
            g2d.setColor(Color.GRAY);
            g2d.drawString("Map not loaded", getWidth()/2 - 40, getHeight()/2);
        }

        // Draw host points (blue)
        drawPoints(g2d, hostPoints, HOST_COLOR, true);

        // Draw client points (red)
        drawPoints(g2d, clientPoints, CLIENT_COLOR, false);

        // Draw legend
        drawLegend(g2d);
    }

    private void drawPoints(Graphics2D g2d, List<Point> points, Color color, boolean isHostList) {
        for (Point p : points) {
            int x = (int) p.getX();
            int y = (int) p.getY();

            // Check if hovered
            boolean isHovered = (p == hoveredPoint);

            // Draw shadow
            g2d.setColor(new Color(0, 0, 0, 50));
            g2d.fillOval(x - POINT_RADIUS + 2, y - POINT_RADIUS + 2, POINT_RADIUS * 2, POINT_RADIUS * 2);

            // Draw point
            if (isHovered) {
                g2d.setColor(HOVER_COLOR);
                // Draw X indicator for deletion
                g2d.setStroke(new BasicStroke(2));
                g2d.drawLine(x - 12, y - 12, x + 12, y + 12);
                g2d.drawLine(x + 12, y - 12, x - 12, y + 12);
            }

            g2d.setColor(color);
            g2d.fillOval(x - POINT_RADIUS, y - POINT_RADIUS, POINT_RADIUS * 2, POINT_RADIUS * 2);

            // Draw border
            g2d.setColor(Color.WHITE);
            g2d.setStroke(new BasicStroke(2));
            g2d.drawOval(x - POINT_RADIUS, y - POINT_RADIUS, POINT_RADIUS * 2, POINT_RADIUS * 2);
        }
    }

    private void drawLegend(Graphics2D g2d) {
        int padding = 10;
        int boxWidth = 80;
        int boxHeight = 50;

        // Background
        g2d.setColor(new Color(255, 255, 255, 200));
        g2d.fillRect(padding, padding, boxWidth, boxHeight);
        g2d.setColor(Color.DARK_GRAY);
        g2d.drawRect(padding, padding, boxWidth, boxHeight);

        // Host indicator
        g2d.setColor(HOST_COLOR);
        g2d.fillOval(padding + 8, padding + 8, 12, 12);
        g2d.setColor(Color.BLACK);
        g2d.drawString("Host", padding + 25, padding + 18);

        // Client indicator
        g2d.setColor(CLIENT_COLOR);
        g2d.fillOval(padding + 8, padding + 28, 12, 12);
        g2d.setColor(Color.BLACK);
        g2d.drawString("Client", padding + 25, padding + 38);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(500, 400);
    }
}
