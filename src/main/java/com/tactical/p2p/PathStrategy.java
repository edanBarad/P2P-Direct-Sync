package com.tactical.p2p;

import java.util.List;

/**
 * PathStrategy - Strategy pattern interface for path finding algorithms.
 *
 * Different implementations can provide different path finding rules:
 * - ShortestPathStrategy: Dijkstra's algorithm (current)
 * - Future: Avoid certain areas, weighted paths, etc.
 */
public interface PathStrategy {

    /**
     * Find a path between two points.
     *
     * @param source The starting point
     * @param target The destination point
     * @param points All available points on the map
     * @param lines All available lines (edges) on the map
     * @return List of lines representing the path, empty if no path found
     */
    List<Line> findPath(Point source, Point target, List<Point> points, List<Line> lines);

    /**
     * Get the name of this strategy for display purposes.
     */
    String getName();
}
