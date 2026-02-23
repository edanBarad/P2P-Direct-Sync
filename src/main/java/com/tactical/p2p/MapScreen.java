package com.tactical.p2p;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * MapScreen - Shared map with yellow points and green lines on white background.
 *
 * Features:
 * - Click to add a point (auto-connects to closest point)
 * - Right-click to delete a point (removes all connected lines)
 * - Points sync in real-time between Host and Client
 */
public class MapScreen extends JPanel {

    private static final int POINT_RADIUS = 8;
    private static final Color POINT_COLOR = Color.YELLOW;
    private static final Color LINE_COLOR = new Color(0, 180, 0); // Green
    private static final Color BACKGROUND_COLOR = Color.WHITE;
    private static final Color HOST_POINT_BORDER = new Color(30, 144, 255);  // Blue border for host
    private static final Color CLIENT_POINT_BORDER = new Color(220, 20, 60); // Red border for client
    private static final Color HOVER_COLOR = new Color(255, 165, 0); // Orange for hover

    /** All points on the map (shared between users) */
    private final List<Point> points = new ArrayList<>();

    /** All lines connecting points */
    private final List<Line> lines = new ArrayList<>();

    /** Tracks which points belong to host vs client (by index in points list) */
    private final List<Boolean> pointOwnership = new ArrayList<>(); // null = system (deletable by all), true = host, false = client

    /** Is this instance the host? */
    private boolean isHost = true;

    /** Point currently being hovered over */
    private Point hoveredPoint = null;

    /** Index of hovered point (for checking ownership) */
    private int hoveredPointIndex = -1;

    /** Callback when a point is added locally */
    private Consumer<Point> onPointAdded;

    /** Callback when a point is removed locally */
    private Consumer<Point> onPointRemoved;

    public MapScreen() {
        setBackground(BACKGROUND_COLOR);
        createSampleMap();
        setupMouseListeners();
    }

    /**
     * Creates a pre-loaded grid map as the starting state.
     */
    private void createSampleMap() {
        int width = 700;
        int height = 500;
        int margin = 50;

        // Create points in a grid pattern (5 rows x 7 columns)
        Point[][] grid = new Point[5][7];

        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 7; col++) {
                // Store as ratio coordinates
                double ratioX = (margin + col * 100) / (double) width;
                double ratioY = (margin + row * 100) / (double) height;
                grid[row][col] = new Point(ratioX, ratioY);
                points.add(grid[row][col]);
                pointOwnership.add(null); // Grid points are "system" points - deletable by anyone
            }
        }

        // Create horizontal lines
        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 6; col++) {
                lines.add(new Line(grid[row][col], grid[row][col + 1]));
            }
        }

        // Create vertical lines
        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 7; col++) {
                lines.add(new Line(grid[row][col], grid[row + 1][col]));
            }
        }

        // Add some diagonal lines for variety
        lines.add(new Line(grid[0][0], grid[1][1]));
        lines.add(new Line(grid[3][5], grid[4][6]));
        lines.add(new Line(grid[0][6], grid[1][5]));
    }

    private void setupMouseListeners() {
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

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                updateHover(e.getX(), e.getY());
            }
        });
    }

    /**
     * Sets whether this instance is the host.
     */
    public void setIsHost(boolean isHost) {
        this.isHost = isHost;
    }

    // ==================== Coordinate Conversion ====================

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

    // ==================== Point Operations ====================

    private void addPointAt(int x, int y) {
        Point ratioPoint = toRatio(x, y);
        addPointInternal(ratioPoint, isHost, true);
    }

    /**
     * Internal method to add a point and optionally notify callback.
     */
    private void addPointInternal(Point ratioPoint, boolean fromHost, boolean notifyCallback) {
        // Find closest existing point
        Point closest = findClosestPoint(ratioPoint);

        // Add the point
        points.add(ratioPoint);
        pointOwnership.add(fromHost);

        // Create line to closest point if one exists
        if (closest != null) {
            lines.add(new Line(closest, ratioPoint));
        }

        repaint();

        if (notifyCallback && onPointAdded != null) {
            onPointAdded.accept(ratioPoint);
        }
    }

    /**
     * Find the closest point to the given point (excluding the point itself).
     * Returns null if no points exist.
     */
    private Point findClosestPoint(Point target) {
        Point closest = null;
        double minDistance = Double.MAX_VALUE;

        for (Point p : points) {
            double dist = target.distance(p);
            if (dist < minDistance && dist > 0.0001) { // Exclude same point
                minDistance = dist;
                closest = p;
            }
        }

        return closest;
    }

    private void tryDeletePointAt(int x, int y) {
        // Find clicked point
        for (int i = points.size() - 1; i >= 0; i--) {
            Point p = points.get(i);
            if (isPointHit(p, x, y)) {
                Boolean owner = pointOwnership.get(i);

                // Can delete only if: owns the point (system points are never deletable)
                boolean canDelete = (owner != null) && (owner == isHost);

                if (!canDelete) {
                    return; // Not allowed to delete this point
                }

                // Remove all lines connected to this point
                removeLinesConnectedToPoint(p);

                // Remove the point
                Point removed = points.remove(i);
                pointOwnership.remove(i);
                repaint();

                if (onPointRemoved != null) {
                    onPointRemoved.accept(removed);
                }
                return;
            }
        }
    }

    /**
     * Remove all lines that have the given point as start or end.
     */
    private void removeLinesConnectedToPoint(Point point) {
        lines.removeIf(line ->
            line.start().equals(point) || line.end().equals(point)
        );
    }

    private boolean isPointHit(Point ratioPoint, int mouseX, int mouseY) {
        Point pixelPoint = toPixels(ratioPoint.getX(), ratioPoint.getY());
        double dist = pixelPoint.distance(new Point(mouseX, mouseY));
        return dist <= POINT_RADIUS + 4;
    }

    private void updateHover(int x, int y) {
        Point oldHover = hoveredPoint;
        hoveredPoint = null;
        hoveredPointIndex = -1;

        for (int i = 0; i < points.size(); i++) {
            Point p = points.get(i);
            if (isPointHit(p, x, y)) {
                Boolean owner = pointOwnership.get(i);
                // Only hover on points we can delete (not system points, only own points)
                boolean canDelete = (owner != null) && (owner == isHost);
                if (canDelete) {
                    hoveredPoint = p;
                    hoveredPointIndex = i;
                }
                break;
            }
        }

        if (hoveredPoint != oldHover) {
            repaint();
        }
    }

    // ==================== Network Sync ====================

    /**
     * Adds a point received from the remote peer.
     */
    public void addRemotePoint(double ratioX, double ratioY, boolean fromHost) {
        Point ratioPoint = new Point(ratioX, ratioY);
        SwingUtilities.invokeLater(() -> {
            addPointInternal(ratioPoint, fromHost, false);
        });
    }

    /**
     * Removes a point received from the remote peer.
     */
    public void removeRemotePoint(double ratioX, double ratioY) {
        SwingUtilities.invokeLater(() -> {
            Point target = new Point(ratioX, ratioY);
            for (int i = 0; i < points.size(); i++) {
                if (points.get(i).equals(target)) {
                    // Remove all lines connected to this point
                    removeLinesConnectedToPoint(points.get(i));
                    points.remove(i);
                    pointOwnership.remove(i);
                    break;
                }
            }
            repaint();
        });
    }

    /**
     * Clears all points and lines.
     */
    public void clearPoints() {
        points.clear();
        lines.clear();
        pointOwnership.clear();
        repaint();
    }

    // ==================== Callbacks ====================

    public void setOnPointAdded(Consumer<Point> callback) {
        this.onPointAdded = callback;
    }

    public void setOnPointRemoved(Consumer<Point> callback) {
        this.onPointRemoved = callback;
    }

    // ==================== Rendering ====================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw lines first (behind points)
        g2d.setColor(LINE_COLOR);
        g2d.setStroke(new BasicStroke(2));

        for (Line line : lines) {
            Point startPixel = toPixels(line.start().getX(), line.start().getY());
            Point endPixel = toPixels(line.end().getX(), line.end().getY());

            g2d.drawLine(
                (int) startPixel.getX(), (int) startPixel.getY(),
                (int) endPixel.getX(), (int) endPixel.getY()
            );
        }

        // Draw points
        for (int i = 0; i < points.size(); i++) {
            Point ratioPoint = points.get(i);
            Boolean owner = pointOwnership.get(i);
            boolean isSystemPoint = (owner == null);
            boolean isHostPoint = (owner != null && owner);
            Point pixelPoint = toPixels(ratioPoint.getX(), ratioPoint.getY());

            int x = (int) pixelPoint.getX();
            int y = (int) pixelPoint.getY();

            boolean isHovered = (ratioPoint == hoveredPoint);

            // Draw shadow
            g2d.setColor(new Color(0, 0, 0, 40));
            g2d.fillOval(x - POINT_RADIUS + 2, y - POINT_RADIUS + 2, POINT_RADIUS * 2, POINT_RADIUS * 2);

            // Draw point fill
            if (isHovered) {
                g2d.setColor(HOVER_COLOR);
            } else {
                g2d.setColor(POINT_COLOR);
            }
            g2d.fillOval(x - POINT_RADIUS, y - POINT_RADIUS, POINT_RADIUS * 2, POINT_RADIUS * 2);

            // Draw border (blue for host, red for client, gray for system)
            if (isSystemPoint) {
                g2d.setColor(Color.DARK_GRAY);
            } else {
                g2d.setColor(isHostPoint ? HOST_POINT_BORDER : CLIENT_POINT_BORDER);
            }
            g2d.setStroke(new BasicStroke(2));
            g2d.drawOval(x - POINT_RADIUS, y - POINT_RADIUS, POINT_RADIUS * 2, POINT_RADIUS * 2);

            // Draw X indicator on hover
            if (isHovered) {
                g2d.setColor(Color.BLACK);
                g2d.setStroke(new BasicStroke(2));
                g2d.drawLine(x - 10, y - 10, x + 10, y + 10);
                g2d.drawLine(x + 10, y - 10, x - 10, y + 10);
            }
        }

        // Draw legend
        drawLegend(g2d);
    }

    private void drawLegend(Graphics2D g2d) {
        int padding = 10;
        int boxWidth = 130;
        int boxHeight = 70;

        // Background
        g2d.setColor(new Color(255, 255, 255, 220));
        g2d.fillRect(padding, padding, boxWidth, boxHeight);
        g2d.setColor(Color.DARK_GRAY);
        g2d.drawRect(padding, padding, boxWidth, boxHeight);

        g2d.setFont(new Font("Arial", Font.PLAIN, 11));

        // System point indicator (grid points)
        g2d.setColor(POINT_COLOR);
        g2d.fillOval(padding + 8, padding + 10, 10, 10);
        g2d.setColor(Color.DARK_GRAY);
        g2d.setStroke(new BasicStroke(2));
        g2d.drawOval(padding + 8, padding + 10, 10, 10);
        g2d.setColor(Color.BLACK);
        g2d.drawString("Grid (any)", padding + 24, padding + 19);

        // Host point indicator
        g2d.setColor(POINT_COLOR);
        g2d.fillOval(padding + 8, padding + 26, 10, 10);
        g2d.setColor(HOST_POINT_BORDER);
        g2d.setStroke(new BasicStroke(2));
        g2d.drawOval(padding + 8, padding + 26, 10, 10);
        g2d.setColor(Color.BLACK);
        g2d.drawString("Host", padding + 24, padding + 35);

        // Client point indicator
        g2d.setColor(POINT_COLOR);
        g2d.fillOval(padding + 8, padding + 42, 10, 10);
        g2d.setColor(CLIENT_POINT_BORDER);
        g2d.setStroke(new BasicStroke(2));
        g2d.drawOval(padding + 8, padding + 42, 10, 10);
        g2d.setColor(Color.BLACK);
        g2d.drawString("Client", padding + 24, padding + 51);
    }

    @Override
    public Dimension getPreferredSize() {
        return new Dimension(500, 400);
    }

    /**
     * Launch the map screen in a standalone window (for testing).
     */
    public static void launch() {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Map Screen");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            MapScreen mapScreen = new MapScreen();
            frame.add(mapScreen);
            frame.pack();
            frame.setLocationRelativeTo(null);
            frame.setVisible(true);
        });
    }

    public static void main(String[] args) {
        launch();
    }
}
