package com.tactical.p2p;

import java.util.*;

/**
 * ShortestPathStrategy - Uses Dijkstra's algorithm to find the shortest path.
 */
public class ShortestPathStrategy implements PathStrategy {

    @Override
    public String getName() {
        return "Shortest Path (Dijkstra)";
    }

    @Override
    public List<Line> findPath(Point source, Point target, List<Point> points, List<Line> lines) {
        if (source == null || target == null || source.equals(target)) {
            return Collections.emptyList();
        }

        // Build adjacency map
        Map<Point, List<Line>> adjacency = new HashMap<>();
        for (Line line : lines) {
            adjacency.computeIfAbsent(line.start(), k -> new ArrayList<>()).add(line);
            adjacency.computeIfAbsent(line.end(), k -> new ArrayList<>()).add(line);
        }

        // Distance and previous maps
        Map<Point, Double> dist = new HashMap<>();
        Map<Point, Line> prev = new HashMap<>();
        Set<Point> visited = new HashSet<>();

        // Priority queue: (distance, point index)
        PriorityQueue<double[]> pq = new PriorityQueue<>(Comparator.comparingDouble(a -> a[0]));

        // Initialize distances
        for (Point p : points) {
            dist.put(p, Double.MAX_VALUE);
        }
        dist.put(source, 0.0);

        int sourceIdx = points.indexOf(source);
        if (sourceIdx == -1) return Collections.emptyList();

        pq.add(new double[]{0.0, sourceIdx});

        while (!pq.isEmpty()) {
            double[] entry = pq.poll();
            int idx = (int) entry[1];
            Point u = points.get(idx);

            if (visited.contains(u)) continue;
            visited.add(u);

            if (u.equals(target)) break;

            List<Line> edges = adjacency.getOrDefault(u, Collections.emptyList());
            for (Line line : edges) {
                Point v = line.start().equals(u) ? line.end() : line.start();
                int vIdx = points.indexOf(v);

                if (vIdx == -1 || visited.contains(v)) continue;

                double alt = dist.get(u) + line.length();
                if (alt < dist.get(v)) {
                    dist.put(v, alt);
                    prev.put(v, line);
                    pq.add(new double[]{alt, vIdx});
                }
            }
        }

        // Reconstruct path
        List<Line> path = new ArrayList<>();
        Point current = target;

        while (prev.containsKey(current)) {
            Line line = prev.get(current);
            path.add(0, line);
            current = line.start().equals(current) ? line.end() : line.start();
        }

        return path;
    }
}
