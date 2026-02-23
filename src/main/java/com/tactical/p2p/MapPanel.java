package com.tactical.p2p;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.imageio.ImageIO;

// Temporary debug imports
import java.awt.geom.Ellipse2D;

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

    // ==================== DEBUG: Graph Analysis Overlay ====================
    /** Set to true to show graph vertices/edges overlay for analysis */
    private boolean showGraphDebug = true;

    // Debug vertices (pixel coordinates - user verified)
    // Image dimensions appear to be ~800 x ~600
    private final int[][] debugVertices = {
        // Top row (vertical roads to top edge)
        {220, 175},  // V1 - top edge, column 1
        {450, 175},  // V2 - top edge, column 2
        {670, 175},  // V3 - top edge, column 3
        // Middle horizontal road (y=305)
        {10, 305},   // V4 - left edge
        {220, 305},  // V5 - junction (col 1)
        {335, 305},  // V6 - junction (col 1.5, river crossing)
        {450, 305},  // V7 - junction (col 2)
        {560, 305},  // V8 - junction (col 2.5)
        {670, 305},  // V9 - junction (col 3)
        {780, 305},  // V10 - right edge
        // Placeholder for V11 (not used, keeping index consistent)
        {0, 0},      // V11 - placeholder
        // Lower horizontal road (y=430)
        {115, 430},  // V12 - left edge
        {220, 430},  // V13 - junction (col 1)
        {335, 430},  // V14 - junction (col 1.5)
        {450, 430},  // V15 - junction (col 2)
        {560, 430},  // V16 - junction (col 2.5)
        {670, 430},  // V17 - junction (col 3)
        {780, 430},  // V18 - right edge
        // Placeholder for V19 (not used, keeping index consistent)
        {0, 0},      // V19 - placeholder
        // Bottom row (vertical roads to bottom edge, y=555)
        {220, 555},  // V20 - bottom edge, col 1
        {450, 555},  // V21 - bottom edge, col 2
        {560, 555},  // V22 - bottom edge, col 2.5 (river end)
        {670, 555},  // V23 - bottom edge, col 3
        // River start
        {155, 50},   // V24 - top edge (river start)
    };

    // Debug edges (pairs of vertex indices, 0-based)
    private final int[][] debugEdges = {
        // River (cyan) - from V24 diagonally to V5, then to V22
        {23, 4},   // V24 -> V5 (river upper segment - diagonal)
        {4, 21},   // V5 -> V22 (river lower segment - diagonal) NOTE: user said V15->V22 diagonal

        // Top vertical roads
        {0, 4},    // V1 -> V5 (vertical)
        {1, 6},    // V2 -> V7 (vertical)
        {2, 8},    // V3 -> V9 (vertical)

        // Middle horizontal road (V4 -> V10 across)
        {3, 4},    // V4 -> V5
        {4, 5},    // V5 -> V6
        {5, 6},    // V6 -> V7
        {6, 7},    // V7 -> V8
        {7, 8},    // V8 -> V9
        {8, 9},    // V9 -> V10

        // Vertical connectors between middle and lower roads
        {4, 12},   // V5 -> V13 (vertical)
        {5, 13},   // V6 -> V14 (vertical)
        {6, 14},   // V7 -> V15 (vertical)
        {8, 16},   // V9 -> V17 (vertical)

        // Lower horizontal road (V12 -> V18 across)
        {11, 12},  // V12 -> V13
        {12, 13},  // V13 -> V14
        {13, 14},  // V14 -> V15
        {14, 15},  // V15 -> V16
        {15, 16},  // V16 -> V17
        {16, 17},  // V17 -> V18

        // Bottom vertical roads
        {12, 19},  // V13 -> V20 (vertical)
        {14, 20},  // V15 -> V21 (vertical)
        {14, 21},  // V15 -> V22 (diagonal - river connection)
        {15, 21},  // V16 -> V22 (vertical)
        {16, 22},  // V17 -> V23 (vertical)
    };

    // Which edges are river (cyan instead of green)
    private final int[] riverEdgeIndices = {0, 1, 22}; // indices in debugEdges array
    // ==================== END DEBUG ====================

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

    /**
     * Converts screen pixels to ratio (0.0 to 1.0) for storage/network.
     */
    private Point toRatio(int x, int y) {
        double ratioX = getWidth() > 0 ? (double) x / getWidth() : 0;
        double ratioY = getHeight() > 0 ? (double) y / getHeight() : 0;
        return new Point(ratioX, ratioY);
    }

    /**
     * Converts ratio to screen pixels for rendering.
     */
    private Point toPixels(double ratioX, double ratioY) {
        int x = (int) (ratioX * getWidth());
        int y = (int) (ratioY * getHeight());
        return new Point(x, y);
    }

    private void addPointAt(int x, int y) {
        // Store as ratio for resolution-independent storage
        Point ratioPoint = toRatio(x, y);

        if (isHost) {
            hostPoints.add(ratioPoint);
        } else {
            clientPoints.add(ratioPoint);
        }

        repaint();

        if (onPointAdded != null) {
            // Send ratio coordinates over network
            onPointAdded.accept(ratioPoint);
        }
    }

    private void tryDeletePointAt(int x, int y) {
        // Each user can only delete their own points
        if (isHost) {
            // Host can only delete host (blue) points
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
        } else {
            // Client can only delete client (red) points
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
    }

    private boolean isPointHit(Point ratioPoint, int mouseX, int mouseY) {
        // Convert ratio to pixels for hit detection
        Point pixelPoint = toPixels(ratioPoint.getX(), ratioPoint.getY());
        double dist = pixelPoint.distance(new Point(mouseX, mouseY));
        return dist <= POINT_RADIUS + 4; // Small buffer for easier clicking
    }

    private void updateHover(int x, int y) {
        Point oldHover = hoveredPoint;

        hoveredPoint = null;
        hoveredIsHost = false;

        // Only show hover effect on own points (the ones you can delete)
        if (isHost) {
            // Host can only hover over host (blue) points
            for (Point p : hostPoints) {
                if (isPointHit(p, x, y)) {
                    hoveredPoint = p;
                    hoveredIsHost = true;
                    break;
                }
            }
        } else {
            // Client can only hover over client (red) points
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

    /**
     * Gets the hovered point in pixel coordinates for rendering.
     */
    private Point getHoveredPixelPoint() {
        if (hoveredPoint == null) return null;
        return toPixels(hoveredPoint.getX(), hoveredPoint.getY());
    }

    // ==================== Network Sync ====================

    /**
     * Adds a point received from the remote peer.
     * Coordinates are expected to be ratios (0.0 to 1.0).
     * Called by NetworkManager when a point message is received.
     */
    public void addRemotePoint(double ratioX, double ratioY, boolean fromHost) {
        Point ratioPoint = new Point(ratioX, ratioY);

        SwingUtilities.invokeLater(() -> {
            if (fromHost) {
                hostPoints.add(ratioPoint);
            } else {
                clientPoints.add(ratioPoint);
            }
            repaint();
        });
    }

    /**
     * Removes a point received from the remote peer.
     * Coordinates are expected to be ratios (0.0 to 1.0).
     * Since each user can only delete their own points, fromHost indicates
     * which list to remove from.
     */
    public void removeRemotePoint(double ratioX, double ratioY, boolean fromHost) {
        SwingUtilities.invokeLater(() -> {
            Point target = new Point(ratioX, ratioY);
            // Remove from the correct list based on who owned the point
            if (fromHost) {
                hostPoints.removeIf(p -> p.equals(target));
            } else {
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

        // Draw graph debug overlay (vertices and edges)
        if (showGraphDebug) {
            drawGraphDebug(g2d);
        }

        // Draw legend
        drawLegend(g2d);
    }

    // ==================== DEBUG: Graph Drawing ====================
    private void drawGraphDebug(Graphics2D g2d) {
        // Get original image dimensions for scaling
        int imgWidth = (mapImage != null) ? mapImage.getWidth() : 800;
        int imgHeight = (mapImage != null) ? mapImage.getHeight() : 600;

        // Scale factors from original image to current panel size
        double scaleX = getWidth() / (double) imgWidth;
        double scaleY = getHeight() / (double) imgHeight;

        // First pass: draw all road edges (green)
        g2d.setColor(new Color(0, 200, 0, 200)); // Semi-transparent green
        g2d.setStroke(new BasicStroke(3));

        for (int i = 0; i < debugEdges.length; i++) {
            // Skip river edges in first pass
            boolean isRiverEdge = false;
            for (int ri : riverEdgeIndices) {
                if (ri == i) { isRiverEdge = true; break; }
            }
            if (isRiverEdge) continue;

            int[] edge = debugEdges[i];
            int[] v1 = debugVertices[edge[0]];
            int[] v2 = debugVertices[edge[1]];

            // Skip placeholder vertices
            if (v1[0] == 0 && v1[1] == 0) continue;
            if (v2[0] == 0 && v2[1] == 0) continue;

            int x1 = (int) (v1[0] * scaleX);
            int y1 = (int) (v1[1] * scaleY);
            int x2 = (int) (v2[0] * scaleX);
            int y2 = (int) (v2[1] * scaleY);
            g2d.drawLine(x1, y1, x2, y2);
        }

        // Second pass: draw river edges (cyan)
        g2d.setColor(new Color(0, 220, 220, 200)); // Cyan for river
        g2d.setStroke(new BasicStroke(4));

        for (int ri : riverEdgeIndices) {
            int[] edge = debugEdges[ri];
            int[] v1 = debugVertices[edge[0]];
            int[] v2 = debugVertices[edge[1]];

            // Skip placeholder vertices
            if (v1[0] == 0 && v1[1] == 0) continue;
            if (v2[0] == 0 && v2[1] == 0) continue;

            int x1 = (int) (v1[0] * scaleX);
            int y1 = (int) (v1[1] * scaleY);
            int x2 = (int) (v2[0] * scaleX);
            int y2 = (int) (v2[1] * scaleY);
            g2d.drawLine(x1, y1, x2, y2);
        }

        // Draw vertices (yellow dots)
        g2d.setColor(new Color(255, 255, 0, 240)); // Yellow
        int vertexRadius = 8;

        for (int i = 0; i < debugVertices.length; i++) {
            int[] v = debugVertices[i];

            // Skip placeholder vertices (V11, V19)
            if (v[0] == 0 && v[1] == 0) continue;

            int x = (int) (v[0] * scaleX);
            int y = (int) (v[1] * scaleY);

            // Draw dot
            g2d.fillOval(x - vertexRadius, y - vertexRadius, vertexRadius * 2, vertexRadius * 2);

            // Draw vertex label
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.BOLD, 10));
            g2d.drawString("V" + (i + 1), x + vertexRadius + 2, y + 4);
            g2d.setColor(new Color(255, 255, 0, 240));
        }
    }

    /** Turn off graph debug overlay */
    public void hideGraphDebug() {
        showGraphDebug = false;
        repaint();
    }

    /** Turn on graph debug overlay */
    public void showGraphDebug() {
        showGraphDebug = true;
        repaint();
    }
    // ==================== END DEBUG ====================

    private void drawPoints(Graphics2D g2d, List<Point> ratioPoints, Color color, boolean isHostList) {
        for (Point ratioPoint : ratioPoints) {
            // Convert ratio to pixels for rendering
            Point pixelPoint = toPixels(ratioPoint.getX(), ratioPoint.getY());
            int x = (int) pixelPoint.getX();
            int y = (int) pixelPoint.getY();

            // Check if hovered
            boolean isHovered = (ratioPoint == hoveredPoint);

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
