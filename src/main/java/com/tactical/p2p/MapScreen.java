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
    private Consumer<ZoneInfo> onZoneTriggered;
    private Consumer<PatrolInfo> onPatrolStarted;
    private PatrolProgressCallback onPatrolProgress;
    private Consumer<PatrolInfo> onPatrolCompleted;

    private AuditLogger auditLogger;

    // Zone state
    private final List<Zone> zones = new ArrayList<>();
    private boolean drawingZone = false;
    private Point zoneStart = null;
    private boolean drawingCircleZone = false;

    // Patrol state
    private final List<PatrolRoute> patrolRoutes = new ArrayList<>();
    private int currentPatrolRoute = -1;
    private int patrolCheckpoint = 0;
    private javax.swing.Timer patrolTimer;
    private long patrolStartTime;
    private boolean creatingPatrol = false;
    private final List<Point> patrolPoints = new ArrayList<>();

    // BiConsumer alternative for patrol progress
    public interface PatrolProgressCallback {
        void accept(int index, int total);
    }

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

    // Zone info for callbacks
    public static class ZoneInfo {
        public final String name;
        public final double x, y, width, height;
        public final boolean isCircle;
        public ZoneInfo(String name, double x, double y, double w, double h, boolean isCircle) {
            this.name = name;
            this.x = x; this.y = y;
            this.width = w; this.height = h;
            this.isCircle = isCircle;
        }
    }

    // Patrol info for callbacks
    public static class PatrolInfo {
        public final String name;
        public final int checkpoints;
        public final double duration;
        public PatrolInfo(String name, int checkpoints, double duration) {
            this.name = name;
            this.checkpoints = checkpoints;
            this.duration = duration;
        }
        @Override
        public String toString() {
            return name + " (" + checkpoints + " checkpoints, " + (int)duration + "s)";
        }
    }

    // Zone representation
    private static class Zone {
        final double x, y, width, height;
        final boolean isCircle;
        final String name;
        final Color color;

        Zone(double x, double y, double w, double h, boolean isCircle, String name) {
            this.x = x; this.y = y;
            this.width = w; this.height = h;
            this.isCircle = isCircle;
            this.name = name;
            this.color = isCircle ? new Color(255, 100, 100, 80) : new Color(100, 100, 255, 80);
        }

        boolean contains(Point p) {
            if (isCircle) {
                double cx = x + width / 2, cy = y + height / 2;
                double radius = Math.min(width, height) / 2;
                double dx = p.getX() - cx, dy = p.getY() - cy;
                return Math.sqrt(dx * dx + dy * dy) <= radius;
            } else {
                return p.getX() >= x && p.getX() <= x + width &&
                       p.getY() >= y && p.getY() <= y + height;
            }
        }
    }

    // Patrol route representation
    private static class PatrolRoute {
        final List<Point> checkpoints;
        final String name;
        final boolean isHostPatrol;

        PatrolRoute(List<Point> checkpoints, String name, boolean isHostPatrol) {
            this.checkpoints = new ArrayList<>(checkpoints);
            this.name = name;
            this.isHostPatrol = isHostPatrol;
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
        // Zone drawing mode
        if (drawingZone) {
            handleZoneClick(x, y);
            return;
        }

        // Patrol creation mode
        if (creatingPatrol) {
            handlePatrolClick(x, y);
            return;
        }

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
    public void setOnZoneTriggered(Consumer<ZoneInfo> c) { onZoneTriggered = c; }
    public void setOnPatrolStarted(Consumer<PatrolInfo> c) { onPatrolStarted = c; }
    public void setOnPatrolProgress(PatrolProgressCallback c) { onPatrolProgress = c; }
    public void setOnPatrolCompleted(Consumer<PatrolInfo> c) { onPatrolCompleted = c; }
    public void setAuditLogger(AuditLogger logger) { this.auditLogger = logger; }

    // ==================== Zone Operations ====================

    public void startZoneDrawing() {
        drawingZone = true;
        drawingCircleZone = false;
        zoneStart = null;
        creatingPatrol = false;
        pathState = PathSelectionState.NONE;
        repaint();
    }

    public void startCircleZoneDrawing() {
        drawingZone = true;
        drawingCircleZone = true;
        zoneStart = null;
        creatingPatrol = false;
        pathState = PathSelectionState.NONE;
        repaint();
    }

    public void clearZones() {
        zones.clear();
        drawingZone = false;
        zoneStart = null;
        repaint();
        if (auditLogger != null) {
            auditLogger.log(AuditLogger.EventType.ZONE_REMOVED, "All zones cleared");
        }
    }

    private void handleZoneClick(int x, int y) {
        Point ratioPoint = toRatio(x, y);

        if (zoneStart == null) {
            zoneStart = ratioPoint;
        } else {
            double x1 = Math.min(zoneStart.getX(), ratioPoint.getX());
            double y1 = Math.min(zoneStart.getY(), ratioPoint.getY());
            double w = Math.abs(ratioPoint.getX() - zoneStart.getX());
            double h = Math.abs(ratioPoint.getY() - zoneStart.getY());

            if (w > 0.01 && h > 0.01) {
                String name = "Zone " + (zones.size() + 1);
                zones.add(new Zone(x1, y1, w, h, drawingCircleZone, name));

                if (auditLogger != null) {
                    auditLogger.log(AuditLogger.EventType.ZONE_CREATED, "Zone created: " + name);
                }
            }

            zoneStart = null;
            drawingZone = false;
            drawingCircleZone = false;
        }
        repaint();
    }

    private void checkZonesForPoint(Point p) {
        for (Zone zone : zones) {
            if (zone.contains(p)) {
                if (onZoneTriggered != null) {
                    onZoneTriggered.accept(new ZoneInfo(zone.name, zone.x, zone.y, zone.width, zone.height, zone.isCircle));
                }
                if (auditLogger != null) {
                    auditLogger.log(AuditLogger.EventType.ZONE_TRIGGERED, "Zone triggered: " + zone.name);
                }
            }
        }
    }

    // ==================== Patrol Operations ====================

    public void startPatrolCreation() {
        creatingPatrol = true;
        patrolPoints.clear();
        drawingZone = false;
        pathState = PathSelectionState.NONE;
        repaint();
    }

    public void startPatrol() {
        if (patrolRoutes.isEmpty()) {
            JOptionPane.showMessageDialog(this, "No patrol routes! Create one first.", "No Routes", JOptionPane.WARNING_MESSAGE);
            return;
        }
        if (patrolTimer != null && patrolTimer.isRunning()) {
            return; // Already patrolling
        }

        currentPatrolRoute = patrolRoutes.size() - 1; // Use latest route
        patrolCheckpoint = 0;
        patrolStartTime = System.currentTimeMillis();

        PatrolRoute route = patrolRoutes.get(currentPatrolRoute);
        if (onPatrolStarted != null) {
            onPatrolStarted.accept(new PatrolInfo(route.name, route.checkpoints.size(), 0));
        }
        if (auditLogger != null) {
            auditLogger.log(AuditLogger.EventType.PATROL_STARTED, "Patrol started: " + route.name);
        }

        // Simulate patrol progress (every 3 seconds advance checkpoint)
        patrolTimer = new javax.swing.Timer(3000, e -> {
            PatrolRoute r = patrolRoutes.get(currentPatrolRoute);
            patrolCheckpoint++;

            if (patrolCheckpoint >= r.checkpoints.size()) {
                // Patrol complete
                double duration = (System.currentTimeMillis() - patrolStartTime) / 1000.0;
                if (onPatrolCompleted != null) {
                    onPatrolCompleted.accept(new PatrolInfo(r.name, r.checkpoints.size(), duration));
                }
                if (auditLogger != null) {
                    auditLogger.log(AuditLogger.EventType.PATROL_COMPLETED, "Patrol completed: " + r.name);
                }
                patrolTimer.stop();
                patrolTimer = null;
                patrolCheckpoint = 0;
            } else {
                if (onPatrolProgress != null) {
                    onPatrolProgress.accept(patrolCheckpoint + 1, r.checkpoints.size());
                }
                if (auditLogger != null) {
                    auditLogger.log(AuditLogger.EventType.PATROL_PROGRESS, "Patrol checkpoint " + (patrolCheckpoint + 1));
                }
            }
            repaint();
        });
        patrolTimer.start();
        repaint();
    }

    public void endPatrol() {
        if (patrolTimer != null) {
            patrolTimer.stop();
            patrolTimer = null;
        }
        patrolCheckpoint = 0;
        repaint();

        if (auditLogger != null) {
            auditLogger.log(AuditLogger.EventType.PATROL_COMPLETED, "Patrol ended manually");
        }
    }

    private void handlePatrolClick(int x, int y) {
        // Find clicked point
        Point clickedPoint = null;
        for (Point p : points) {
            if (isPointHit(p, x, y)) {
                clickedPoint = p;
                break;
            }
        }

        if (clickedPoint != null) {
            patrolPoints.add(clickedPoint);
            repaint();

            // Check if we have enough points for a patrol route
            if (patrolPoints.size() >= 3) {
                int result = JOptionPane.showConfirmDialog(this,
                    "Create patrol route with " + patrolPoints.size() + " checkpoints?",
                    "Create Patrol", JOptionPane.YES_NO_OPTION);
                if (result == JOptionPane.YES_OPTION) {
                    String name = "Patrol " + (patrolRoutes.size() + 1);
                    patrolRoutes.add(new PatrolRoute(patrolPoints, name, isHost));
                    if (auditLogger != null) {
                        auditLogger.log(AuditLogger.EventType.PATROL_STARTED, "Patrol route created: " + name);
                    }
                }
                patrolPoints.clear();
                creatingPatrol = false;
            }
        }
    }

    // ==================== Rendering ====================

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        g2d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Draw zones first (background)
        drawZones(g2d);

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

        // Draw patrol routes
        drawPatrolRoutes(g2d);

        // Points
        for (int i = 0; i < points.size(); i++) {
            drawPoint(g2d, points.get(i), pointOwnership.get(i));
        }

        // Draw patrol creation points
        drawPatrolCreationPoints(g2d);

        drawLegend(g2d);

        String stateText = getPathStateText();
        if (stateText != null) {
            g2d.setColor(new Color(148, 0, 211));
            g2d.setFont(new Font("Arial", Font.BOLD, 14));
            g2d.drawString(stateText, getWidth() / 2 - 100, 25);
        }

        // Zone drawing indicator
        if (drawingZone) {
            g2d.setColor(new Color(148, 0, 211));
            g2d.setFont(new Font("Arial", Font.BOLD, 14));
            String msg = drawingCircleZone ? "Click two corners for circle zone" : "Click two corners for rectangle zone";
            g2d.drawString(msg, getWidth() / 2 - 100, 25);

            if (zoneStart != null) {
                Point px = toPixels(zoneStart.getX(), zoneStart.getY());
                g2d.setColor(new Color(100, 100, 255, 100));
                g2d.fillOval((int)px.getX() - 5, (int)px.getY() - 5, 10, 10);
            }
        }

        // Patrol creation indicator
        if (creatingPatrol) {
            g2d.setColor(new Color(0, 128, 0));
            g2d.setFont(new Font("Arial", Font.BOLD, 14));
            g2d.drawString("Click points for patrol route (" + patrolPoints.size() + " selected)", getWidth() / 2 - 120, 25);
        }

        if (!paths.isEmpty()) {
            g2d.setColor(Color.DARK_GRAY);
            g2d.setFont(new Font("Arial", Font.PLAIN, 12));
            g2d.drawString("Paths: " + paths.size(), getWidth() - 80, 20);
        }
    }

    private void drawZones(Graphics2D g2d) {
        for (Zone zone : zones) {
            int x = (int) (zone.x * getWidth());
            int y = (int) (zone.y * getHeight());
            int w = (int) (zone.width * getWidth());
            int h = (int) (zone.height * getHeight());

            g2d.setColor(zone.color);
            if (zone.isCircle) {
                g2d.fillOval(x, y, w, h);
            } else {
                g2d.fillRect(x, y, w, h);
            }

            g2d.setColor(zone.isCircle ? Color.RED : Color.BLUE);
            g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{5}, 0));
            if (zone.isCircle) {
                g2d.drawOval(x, y, w, h);
            } else {
                g2d.drawRect(x, y, w, h);
            }
            g2d.setStroke(new BasicStroke(1));

            // Zone name
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.BOLD, 12));
            g2d.drawString(zone.name, x + 5, y + 15);
        }
    }

    private void drawPatrolRoutes(Graphics2D g2d) {
        for (int i = 0; i < patrolRoutes.size(); i++) {
            PatrolRoute route = patrolRoutes.get(i);
            if (route.checkpoints.size() < 2) continue;

            g2d.setColor(route.isHostPatrol ? new Color(0, 128, 0, 150) : new Color(255, 128, 0, 150));
            g2d.setStroke(new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{10, 5}, 0));

            for (int j = 0; j < route.checkpoints.size() - 1; j++) {
                Point p1 = route.checkpoints.get(j);
                Point p2 = route.checkpoints.get(j + 1);
                drawLine(g2d, new Line(p1, p2), 0);
            }

            // Draw checkpoint numbers
            g2d.setColor(Color.BLACK);
            g2d.setFont(new Font("Arial", Font.BOLD, 10));
            for (int j = 0; j < route.checkpoints.size(); j++) {
                Point p = route.checkpoints.get(j);
                Point px = toPixels(p.getX(), p.getY());
                boolean isCurrent = (i == currentPatrolRoute && j == patrolCheckpoint);
                if (isCurrent) {
                    g2d.setColor(Color.GREEN);
                    g2d.fillOval((int)px.getX() - 12, (int)px.getY() - 12, 24, 24);
                }
                g2d.setColor(isCurrent ? Color.WHITE : Color.BLACK);
                g2d.drawString(String.valueOf(j + 1), (int)px.getX() - 4, (int)px.getY() + 4);
            }
        }
        g2d.setStroke(new BasicStroke(1));
    }

    private void drawPatrolCreationPoints(Graphics2D g2d) {
        if (patrolPoints.isEmpty()) return;

        g2d.setColor(new Color(0, 200, 0));
        for (int i = 0; i < patrolPoints.size(); i++) {
            Point p = patrolPoints.get(i);
            Point px = toPixels(p.getX(), p.getY());
            g2d.fillOval((int)px.getX() - 10, (int)px.getY() - 10, 20, 20);
            g2d.setColor(Color.WHITE);
            g2d.setFont(new Font("Arial", Font.BOLD, 10));
            g2d.drawString(String.valueOf(i + 1), (int)px.getX() - 4, (int)px.getY() + 4);
            g2d.setColor(new Color(0, 200, 0));
        }

        // Draw lines between patrol points
        if (patrolPoints.size() > 1) {
            g2d.setColor(new Color(0, 200, 0, 150));
            g2d.setStroke(new BasicStroke(2, BasicStroke.CAP_BUTT, BasicStroke.JOIN_MITER, 10, new float[]{5}, 0));
            for (int i = 0; i < patrolPoints.size() - 1; i++) {
                drawLine(g2d, new Line(patrolPoints.get(i), patrolPoints.get(i + 1)), 0);
            }
            g2d.setStroke(new BasicStroke(1));
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
