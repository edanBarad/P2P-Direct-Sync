package com.tactical.p2p;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.util.*;
import java.util.List;
import java.util.function.Consumer;

/**
 * MapScreen - Shared map with yellow points and green lines on white background.
 */
public class MapScreen extends JPanel {

    private static final int POINT_RADIUS = 8;
    private static final Color POINT_COLOR = Color.YELLOW;
    private static final Color LINE_COLOR = new Color(0, 180, 0);
    private static final Color BACKGROUND_COLOR = Color.WHITE;
    private static final Color HOST_POINT_BORDER = new Color(30, 144, 255);
    private static final Color CLIENT_POINT_BORDER = new Color(220, 20, 60);
    private static final Color HOVER_COLOR = new Color(255, 165, 0);
    private static final Color SELECT_COLOR = new Color(148, 0, 211);
    private static final Color DESTINATION_COLOR = new Color(255, 69, 0);

    private static final Color[] PATH_COLORS = {
        new Color(30, 144, 255),
        new Color(255, 105, 180),
        new Color(50, 205, 50),
        new Color(255, 165, 0),
        new Color(138, 43, 226),
    };

    private final List<Point> points = new ArrayList<>();
    private final List<Line> lines = new ArrayList<>();
    private final List<Boolean> pointOwnership = new ArrayList<>();
    private final List<PathData> paths = new ArrayList<>();

    private boolean isHost = true;
    private Point hoveredPoint = null;
    private PathSelectionState pathState = PathSelectionState.NONE;
    private Point currentSourcePoint = null;
    private Point currentDestPoint = null;
    private Point edgeStartPoint = null;
    private int pathRemovalIndex = -1; // For path removal mode

    private PathStrategy pathStrategy = new ShortestPathStrategy();
    private static final Random random = new Random(42);

    private Consumer<Point> onPointAdded;
    private Consumer<Point> onPointRemoved;
    private Consumer<EdgeInfo> onEdgeAdded;
    private Consumer<PathInfo> onPathFound;
    private Consumer<Integer> onPathRemoved; // path index
    private Consumer<String> onMessage; // for chat messages

    public static class EdgeInfo {
        public final double x1, y1, x2, y2;
        public EdgeInfo(double x1, double y1, double x2, double y2) {
            this.x1 = x1; this.y1 = y1; this.x2 = x2; this.y2 = y2;
        }
    }

    public static class PathInfo {
        public final double sourceX, sourceY, destX, destY;
        public final double length;
        public final int pathIndex;
        public PathInfo(double sx, double sy, double dx, double dy, double length, int pathIndex) {
            this.sourceX = sx; this.sourceY = sy;
            this.destX = dx; this.destY = dy;
            this.length = length;
            this.pathIndex = pathIndex;
        }
    }

    private static class PathData {
        final List<Line> lines;
        final Color color;
        final double length;
        final boolean isHostPath; // who created this path
        final Point source;
        final Point dest;

        PathData(List<Line> lines, Color color, double length, boolean isHostPath, Point source, Point dest) {
            this.lines = lines;
            this.color = color;
            this.length = length;
            this.isHostPath = isHostPath;
            this.source = source;
            this.dest = dest;
        }
    }

    private enum PathSelectionState {
        NONE, SELECTING_SOURCE, SELECTING_DEST, SELECTING_PATH_TO_REMOVE
    }

    public MapScreen() {
        setBackground(BACKGROUND_COLOR);
        createSampleMap();
        setupMouseListeners();
    }

    private void createSampleMap() {
        int width = 700;
        int height = 500;
        int margin = 50;

        Point[][] grid = new Point[5][7];
        boolean[][] keepPoint = new boolean[5][7];

        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 7; col++) {
                keepPoint[row][col] = random.nextBoolean();
            }
        }
        keepPoint[0][0] = true;
        keepPoint[4][6] = true;

        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 7; col++) {
                if (keepPoint[row][col]) {
                    double ratioX = (margin + col * 100) / (double) width;
                    double ratioY = (margin + row * 100) / (double) height;
                    grid[row][col] = new Point(ratioX, ratioY);
                    points.add(grid[row][col]);
                    pointOwnership.add(null);
                }
            }
        }

        for (int row = 0; row < 5; row++) {
            for (int col = 0; col < 6; col++) {
                if (grid[row][col] != null && grid[row][col + 1] != null) {
                    lines.add(new Line(grid[row][col], grid[row][col + 1]));
                }
            }
        }

        for (int row = 0; row < 4; row++) {
            for (int col = 0; col < 7; col++) {
                if (grid[row][col] != null && grid[row + 1][col] != null) {
                    lines.add(new Line(grid[row][col], grid[row + 1][col]));
                }
            }
        }

        if (grid[0][0] != null && grid[1][1] != null) lines.add(new Line(grid[0][0], grid[1][1]));
        if (grid[3][5] != null && grid[4][6] != null) lines.add(new Line(grid[3][5], grid[4][6]));
        if (grid[0][6] != null && grid[1][5] != null) lines.add(new Line(grid[0][6], grid[1][5]));
    }

    private void setupMouseListeners() {
        addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (SwingUtilities.isLeftMouseButton(e)) {
                    handleLeftClick(e.getX(), e.getY());
                } else if (SwingUtilities.isRightMouseButton(e)) {
                    if (pathState == PathSelectionState.NONE && edgeStartPoint == null) {
                        tryDeletePointAt(e.getX(), e.getY());
                    }
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

    public void setIsHost(boolean isHost) { this.isHost = isHost; }
    public void setPathStrategy(PathStrategy strategy) { this.pathStrategy = strategy; }

    // ==================== Click Handling ====================

    private void handleLeftClick(int x, int y) {
        Point clickedPoint = null;
        Boolean clickedOwner = null;

        for (int i = 0; i < points.size(); i++) {
            if (isPointHit(points.get(i), x, y)) {
                clickedPoint = points.get(i);
                clickedOwner = pointOwnership.get(i);
                break;
            }
        }

        // Path removal mode
        if (pathState == PathSelectionState.SELECTING_PATH_TO_REMOVE) {
            if (clickedPoint != null) {
                // Find which of user's paths this endpoint belongs to
                for (int i = paths.size() - 1; i >= 0; i--) {
                    PathData pd = paths.get(i);
                    if (pd.isHostPath == isHost &&
                        (clickedPoint.equals(pd.source) || clickedPoint.equals(pd.dest))) {
                        removePath(i);
                        pathState = PathSelectionState.NONE;
                        repaint();
                        return;
                    }
                }
            }
            return;
        }

        // Edge creation mode
        if (edgeStartPoint != null && pathState == PathSelectionState.NONE) {
            if (clickedPoint != null && !clickedPoint.equals(edgeStartPoint)) {
                addEdge(edgeStartPoint, clickedPoint);
            }
            edgeStartPoint = null;
            repaint();
            return;
        }

        // Path source selection
        if (pathState == PathSelectionState.SELECTING_SOURCE) {
            if (clickedPoint != null && clickedOwner != null && clickedOwner == isHost) {
                currentSourcePoint = clickedPoint;
                pathState = PathSelectionState.SELECTING_DEST;
                edgeStartPoint = null;
                repaint();
            }
            return;
        }

        // Path dest selection
        if (pathState == PathSelectionState.SELECTING_DEST) {
            if (clickedPoint != null && clickedOwner != null && clickedOwner != isHost) {
                currentDestPoint = clickedPoint;
                findAndShowPath();
                pathState = PathSelectionState.NONE;
            }
            return;
        }

        // Normal mode
        if (clickedPoint == null) {
            addPointAt(x, y);
        } else if (clickedOwner != null && clickedOwner == isHost) {
            edgeStartPoint = clickedPoint;
            repaint();
        }
    }

    // ==================== Edge Operations ====================

    private void addEdge(Point from, Point to) {
        for (Line line : lines) {
            if ((line.start().equals(from) && line.end().equals(to)) ||
                (line.start().equals(to) && line.end().equals(from))) {
                return;
            }
        }

        Line newLine = new Line(from, to);
        lines.add(newLine);
        repaint();

        if (onEdgeAdded != null) {
            onEdgeAdded.accept(new EdgeInfo(from.getX(), from.getY(), to.getX(), to.getY()));
        }
    }

    public void addRemoteEdge(double x1, double y1, double x2, double y2) {
        SwingUtilities.invokeLater(() -> {
            Point p1 = findPointByCoords(x1, y1);
            Point p2 = findPointByCoords(x2, y2);
            if (p1 != null && p2 != null) {
                for (Line line : lines) {
                    if ((line.start().equals(p1) && line.end().equals(p2)) ||
                        (line.start().equals(p2) && line.end().equals(p1))) return;
                }
                lines.add(new Line(p1, p2));
                repaint();
            }
        });
    }

    // ==================== Path Finding ====================

    /**
     * Calculate actual pixel length of a path.
     */
    private double calculatePixelPathLength(List<Line> pathLines) {
        double total = 0;
        for (Line line : pathLines) {
            // Convert ratio to pixels
            Point startPx = toPixels(line.start().getX(), line.start().getY());
            Point endPx = toPixels(line.end().getX(), line.end().getY());
            total += startPx.distance(endPx);
        }
        return total;
    }

    public void startPathSelection() {
        if (!hasOwnPoints()) {
            JOptionPane.showMessageDialog(this, "You don't have any points!", "No Points", JOptionPane.WARNING_MESSAGE);
            return;
        }
        pathState = PathSelectionState.SELECTING_SOURCE;
        currentSourcePoint = null;
        currentDestPoint = null;
        edgeStartPoint = null;
        repaint();
    }

    public void startPathRemoval() {
        // Check if user has any paths
        boolean hasPaths = false;
        for (PathData pd : paths) {
            if (pd.isHostPath == isHost) { hasPaths = true; break; }
        }
        if (!hasPaths) {
            JOptionPane.showMessageDialog(this, "You don't have any paths to remove!", "No Paths", JOptionPane.WARNING_MESSAGE);
            return;
        }
        pathState = PathSelectionState.SELECTING_PATH_TO_REMOVE;
        edgeStartPoint = null;
        repaint();
    }

    private void findAndShowPath() {
        if (currentSourcePoint == null || currentDestPoint == null) return;

        List<Line> path = pathStrategy.findPath(currentSourcePoint, currentDestPoint, points, lines);

        if (path.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No path found!", "No Path", JOptionPane.WARNING_MESSAGE);
            return;
        }

        // Calculate pixel length
        double pixelLength = calculatePixelPathLength(path);
        int pathIndex = paths.size();

        Color pathColor = PATH_COLORS[pathIndex % PATH_COLORS.length];
        paths.add(new PathData(new ArrayList<>(path), pathColor, pixelLength, isHost, currentSourcePoint, currentDestPoint));

        repaint();

        if (onPathFound != null) {
            onPathFound.accept(new PathInfo(
                currentSourcePoint.getX(), currentSourcePoint.getY(),
                currentDestPoint.getX(), currentDestPoint.getY(),
                pixelLength, pathIndex
            ));
        }
    }

    private void removePath(int index) {
        if (index >= 0 && index < paths.size()) {
            PathData removed = paths.remove(index);
            if (onPathRemoved != null) {
                onPathRemoved.accept(index);
            }
            if (onMessage != null) {
                onMessage.accept("removed a path (length: " + (int) removed.length + ")");
            }
        }
    }

    public void removeRemotePath(int pathIndex) {
        SwingUtilities.invokeLater(() -> {
            if (pathIndex >= 0 && pathIndex < paths.size()) {
                paths.remove(pathIndex);
                repaint();
            }
        });
    }

    public void setRemotePath(double sourceX, double sourceY, double destX, double destY, double length) {
        SwingUtilities.invokeLater(() -> {
            Point src = findPointByCoords(sourceX, sourceY);
            Point dest = findPointByCoords(destX, destY);

            if (src != null && dest != null) {
                List<Line> path = pathStrategy.findPath(src, dest, points, lines);
                if (!path.isEmpty()) {
                    Color pathColor = PATH_COLORS[paths.size() % PATH_COLORS.length];
                    paths.add(new PathData(path, pathColor, length, !isHost, src, dest));
                    repaint();
                }
            }
        });
    }

    public void clearAllPaths() {
        paths.clear();
        currentSourcePoint = null;
        currentDestPoint = null;
        pathState = PathSelectionState.NONE;
        edgeStartPoint = null;
        repaint();
    }

    private Point findPointByCoords(double x, double y) {
        Point target = new Point(x, y);
        for (Point p : points) {
            if (p.equals(target)) return p;
        }
        return null;
    }

    private boolean hasOwnPoints() {
        for (int i = 0; i < points.size(); i++) {
            Boolean owner = pointOwnership.get(i);
            if (owner != null && owner == isHost) return true;
        }
        return false;
    }

    public String getPathStateText() {
        if (edgeStartPoint != null && pathState == PathSelectionState.NONE) {
            return "Click another point to add edge...";
        }
        switch (pathState) {
            case SELECTING_SOURCE: return "Click YOUR point (source)";
            case SELECTING_DEST: return "Click OTHER user's point (destination)";
            case SELECTING_PATH_TO_REMOVE: return "Click endpoint of YOUR path to remove";
            default: return null;
        }
    }

    // ==================== Coordinate Conversion ====================

    private Point toRatio(int x, int y) {
        double ratioX = getWidth() > 0 ? (double) x / getWidth() : 0;
        double ratioY = getHeight() > 0 ? (double) y / getHeight() : 0;
        return new Point(ratioX, ratioY);
    }

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

    private void addPointInternal(Point ratioPoint, boolean fromHost, boolean notifyCallback) {
        Point closest = findClosestPoint(ratioPoint);
        points.add(ratioPoint);
        pointOwnership.add(fromHost);

        if (closest != null) {
            lines.add(new Line(closest, ratioPoint));
        }
        repaint();

        if (notifyCallback && onPointAdded != null) {
            onPointAdded.accept(ratioPoint);
        }
    }

    private Point findClosestPoint(Point target) {
        Point closest = null;
        double minDistance = Double.MAX_VALUE;
        for (Point p : points) {
            double dist = target.distance(p);
            if (dist < minDistance && dist > 0.0001) {
                minDistance = dist;
                closest = p;
            }
        }
        return closest;
    }

    private void tryDeletePointAt(int x, int y) {
        for (int i = points.size() - 1; i >= 0; i--) {
            Point p = points.get(i);
            if (isPointHit(p, x, y)) {
                Boolean owner = pointOwnership.get(i);
                if (owner == null || owner != isHost) return;

                removeLinesConnectedToPoint(p);
                points.remove(i);
                pointOwnership.remove(i);
                repaint();

                if (onPointRemoved != null) onPointRemoved.accept(p);
                return;
            }
        }
    }

    private void removeLinesConnectedToPoint(Point point) {
        lines.removeIf(line -> line.start().equals(point) || line.end().equals(point));
        for (int i = paths.size() - 1; i >= 0; i--) {
            PathData pd = paths.get(i);
            for (Line line : pd.lines) {
                if (line.start().equals(point) || line.end().equals(point)) {
                    paths.remove(i);
                    break;
                }
            }
        }
    }

    private boolean isPointHit(Point ratioPoint, int mouseX, int mouseY) {
        Point pixelPoint = toPixels(ratioPoint.getX(), ratioPoint.getY());
        return pixelPoint.distance(new Point(mouseX, mouseY)) <= POINT_RADIUS + 4;
    }

    private void updateHover(int x, int y) {
        Point oldHover = hoveredPoint;
        hoveredPoint = null;

        for (int i = 0; i < points.size(); i++) {
            Point p = points.get(i);
            if (isPointHit(p, x, y)) {
                Boolean owner = pointOwnership.get(i);
                boolean canHover = false;

                if (pathState == PathSelectionState.SELECTING_SOURCE) {
                    canHover = (owner != null && owner == isHost);
                } else if (pathState == PathSelectionState.SELECTING_DEST) {
                    canHover = (owner != null && owner != isHost);
                } else if (pathState == PathSelectionState.SELECTING_PATH_TO_REMOVE) {
                    // Can hover on own path endpoints
                    for (PathData pd : paths) {
                        if (pd.isHostPath == isHost && (p.equals(pd.source) || p.equals(pd.dest))) {
                            canHover = true;
                            break;
                        }
                    }
                } else if (edgeStartPoint != null) {
                    canHover = !p.equals(edgeStartPoint);
                } else {
                    canHover = (owner != null && owner == isHost);
                }

                if (canHover) hoveredPoint = p;
                break;
            }
        }

        if (hoveredPoint != oldHover) repaint();
    }

    // ==================== Network Sync ====================

    public void addRemotePoint(double ratioX, double ratioY, boolean fromHost) {
        SwingUtilities.invokeLater(() -> addPointInternal(new Point(ratioX, ratioY), fromHost, false));
    }

    public void removeRemotePoint(double ratioX, double ratioY) {
        SwingUtilities.invokeLater(() -> {
            Point target = new Point(ratioX, ratioY);
            for (int i = 0; i < points.size(); i++) {
                if (points.get(i).equals(target)) {
                    removeLinesConnectedToPoint(points.get(i));
                    points.remove(i);
                    pointOwnership.remove(i);
                    break;
                }
            }
            repaint();
        });
    }

    // ==================== Callbacks ====================

    public void setOnPointAdded(Consumer<Point> c) { onPointAdded = c; }
    public void setOnPointRemoved(Consumer<Point> c) { onPointRemoved = c; }
    public void setOnEdgeAdded(Consumer<EdgeInfo> c) { onEdgeAdded = c; }
    public void setOnPathFound(Consumer<PathInfo> c) { onPathFound = c; }
    public void setOnPathRemoved(Consumer<Integer> c) { onPathRemoved = c; }
    public void setOnMessage(Consumer<String> c) { onMessage = c; }

    // ==================== Rendering ====================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Normal lines
        g2d.setColor(LINE_COLOR);
        g2d.setStroke(new BasicStroke(2));
        for (Line line : lines) {
            boolean inAnyPath = paths.stream().anyMatch(pd -> pd.lines.contains(line));
            if (!inAnyPath) drawLine(g2d, line, 0);
        }

        // Path lines
        for (int pathIdx = 0; pathIdx < paths.size(); pathIdx++) {
            PathData pd = paths.get(pathIdx);
            g2d.setColor(pd.color);
            g2d.setStroke(new BasicStroke(4));

            for (Line line : pd.lines) {
                int lineOffset = 0;
                for (int prevIdx = 0; prevIdx < pathIdx; prevIdx++) {
                    for (Line prevLine : paths.get(prevIdx).lines) {
                        if (linesEqual(line, prevLine)) lineOffset++;
                    }
                }
                drawLine(g2d, line, lineOffset * 6);
            }
        }

        // Points
        for (int i = 0; i < points.size(); i++) {
            drawPoint(g2d, points.get(i), pointOwnership.get(i));
        }

        drawLegend(g2d);

        String stateText = getPathStateText();
        if (stateText != null) {
            g2d.setColor(new Color(148, 0, 211));
            g2d.setFont(new Font("Arial", Font.BOLD, 14));
            g2d.drawString(stateText, getWidth() / 2 - 100, 25);
        }

        if (!paths.isEmpty()) {
            g2d.setColor(Color.DARK_GRAY);
            g2d.setFont(new Font("Arial", Font.PLAIN, 12));
            g2d.drawString("Paths: " + paths.size(), getWidth() - 80, 20);
        }
    }

    private boolean linesEqual(Line l1, Line l2) {
        return (l1.start().equals(l2.start()) && l1.end().equals(l2.end())) ||
               (l1.start().equals(l2.end()) && l1.end().equals(l2.start()));
    }

    private void drawLine(Graphics2D g2d, Line line, int offset) {
        Point start = toPixels(line.start().getX(), line.start().getY());
        Point end = toPixels(line.end().getX(), line.end().getY());

        int x1 = (int) start.getX(), y1 = (int) start.getY();
        int x2 = (int) end.getX(), y2 = (int) end.getY();

        if (offset > 0) {
            double dx = x2 - x1, dy = y2 - y1;
            double len = Math.sqrt(dx * dx + dy * dy);
            if (len > 0) {
                double px = -dy / len * offset, py = dx / len * offset;
                x1 += px; y1 += py; x2 += px; y2 += py;
            }
        }
        g2d.drawLine(x1, y1, x2, y2);
    }

    private void drawPoint(Graphics2D g2d, Point ratioPoint, Boolean owner) {
        Point pixel = toPixels(ratioPoint.getX(), ratioPoint.getY());
        int x = (int) pixel.getX(), y = (int) pixel.getY();

        boolean isSystemPoint = (owner == null);
        boolean isHostPoint = (owner != null && owner);
        boolean isHovered = (ratioPoint == hoveredPoint);
        boolean isSource = (ratioPoint == currentSourcePoint);
        boolean isDest = (ratioPoint == currentDestPoint);
        boolean isEdgeStart = (ratioPoint == edgeStartPoint);

        // Shadow
        g2d.setColor(new Color(0, 0, 0, 40));
        g2d.fillOval(x - POINT_RADIUS + 2, y - POINT_RADIUS + 2, POINT_RADIUS * 2, POINT_RADIUS * 2);

        // Fill
        if (isSource) g2d.setColor(SELECT_COLOR);
        else if (isDest) g2d.setColor(DESTINATION_COLOR);
        else if (isEdgeStart) g2d.setColor(new Color(0, 200, 200));
        else if (isHovered) g2d.setColor(HOVER_COLOR);
        else g2d.setColor(POINT_COLOR);

        g2d.fillOval(x - POINT_RADIUS, y - POINT_RADIUS, POINT_RADIUS * 2, POINT_RADIUS * 2);

        // Border
        g2d.setColor(isSystemPoint ? Color.DARK_GRAY : (isHostPoint ? HOST_POINT_BORDER : CLIENT_POINT_BORDER));
        g2d.setStroke(new BasicStroke(2));
        g2d.drawOval(x - POINT_RADIUS, y - POINT_RADIUS, POINT_RADIUS * 2, POINT_RADIUS * 2);

        // Selection ring
        if (isSource || isDest) {
            g2d.setColor(isSource ? SELECT_COLOR : DESTINATION_COLOR);
            g2d.setStroke(new BasicStroke(3));
            g2d.drawOval(x - POINT_RADIUS - 4, y - POINT_RADIUS - 4, (POINT_RADIUS + 4) * 2, (POINT_RADIUS + 4) * 2);
        }

        // X for delete
        if (isHovered && pathState == PathSelectionState.NONE && edgeStartPoint == null) {
            g2d.setColor(Color.BLACK);
            g2d.setStroke(new BasicStroke(2));
            g2d.drawLine(x - 10, y - 10, x + 10, y + 10);
            g2d.drawLine(x + 10, y - 10, x - 10, y + 10);
        }
    }

    private void drawLegend(Graphics2D g2d) {
        int padding = 10, boxWidth = 130, boxHeight = 70;

        g2d.setColor(new Color(255, 255, 255, 220));
        g2d.fillRect(padding, padding, boxWidth, boxHeight);
        g2d.setColor(Color.DARK_GRAY);
        g2d.drawRect(padding, padding, boxWidth, boxHeight);

        g2d.setFont(new Font("Arial", Font.PLAIN, 11));

        g2d.setColor(POINT_COLOR);
        g2d.fillOval(padding + 8, padding + 10, 10, 10);
        g2d.setColor(Color.DARK_GRAY);
        g2d.drawOval(padding + 8, padding + 10, 10, 10);
        g2d.setColor(Color.BLACK);
        g2d.drawString("Grid", padding + 24, padding + 19);

        g2d.setColor(POINT_COLOR);
        g2d.fillOval(padding + 8, padding + 26, 10, 10);
        g2d.setColor(HOST_POINT_BORDER);
        g2d.drawOval(padding + 8, padding + 26, 10, 10);
        g2d.drawString("Host", padding + 24, padding + 35);

        g2d.setColor(POINT_COLOR);
        g2d.fillOval(padding + 8, padding + 42, 10, 10);
        g2d.setColor(CLIENT_POINT_BORDER);
        g2d.drawOval(padding + 8, padding + 42, 10, 10);
        g2d.drawString("Client", padding + 24, padding + 51);
    }

    @Override
    public Dimension getPreferredSize() { return new Dimension(500, 400); }
}
