# P2P Shared Board

## About this project

As a second-year CS student, I built this project to showcase what I've learned so far in school — **Java**, **OOP design patterns**, and **network socket programming**. The goal was to create something that ties these concepts together in a practical way.

I also used **AI-assisted context engineering** to speed up development. By providing clear requirements and constraints to an AI model, I was able to focus on understanding the architecture while getting help with boilerplate code and documentation.

## Features

### Messenger-Style Chat
- Real-time synchronized chat between Host and Client
- Messages are color-coded by sender (Host = blue, Client = red)
- System messages in gray (italic)
- Type and press Enter or click Send

### Shared Map with Pathfinding
- **Pre-loaded grid** of yellow points with green lines on white background
- **Add points**: Left-click empty space to add your own point (auto-connects to nearest)
- **Add edges**: Click your existing point, then click another point to create a custom edge
- **Delete points**: Right-click your own points to delete them
- **Color coding**:
  - Gray border = Grid points (permanent)
  - Blue border = Host points
  - Red border = Client points

### Pathfinding with Dijkstra
- **Find Path**: Select source (your point) → destination (other user's point)
- Uses **Dijkstra's algorithm** with edge length as weight
- **Multiple paths** can coexist with different colors
- **Parallel edges** drawn when paths share the same edge
- Path length displayed in pixel distance
- Path events posted to chat automatically

### Strategy Pattern
- `PathStrategy` interface for pathfinding algorithms
- `ShortestPathStrategy` implements Dijkstra
- Easy to add new pathfinding rules in the future

## How to run

### Compile
```bash
javac -d target/classes src/main/java/com/tactical/p2p/*.java
```

### Run two instances

Terminal 1 (Host):
```bash
java -cp target/classes com.tactical.p2p.App host
```

Terminal 2 (Client):
```bash
java -cp target/classes com.tactical.p2p.App client
```

Or just run without args and pick from the GUI dialog.

## Project structure

```
src/main/java/com/tactical/p2p/
├── App.java              # Main class, handles startup
├── StateModel.java       # Stores the shared state
├── NetworkManager.java   # Socket communication
├── SyncBoardUI.java      # Main GUI with tabs
├── MapScreen.java        # Map panel with points/lines
├── Point.java            # 2D point class
├── Line.java             # Line with start/end points
├── PathStrategy.java     # Strategy interface for pathfinding
└── ShortestPathStrategy.java  # Dijkstra implementation
```

## Network protocol

Uses TCP sockets on port 8888. Messages are UTF-8 strings ending with newline.

### Message types
- `MSG:sender:text` - Chat message
- `POINT:ACTION:x:y:fromHost` - Point add/remove
- `EDGE:x1:y1:x2:y2` - Custom edge added
- `PATH:sx:sy:dx:dy` - Path found
- `PATHREMOVE:index` - Path removed

## Design patterns

### Strategy Pattern
```java
public interface PathStrategy {
    List<Line> findPath(Point source, Point target, List<Point> points, List<Line> lines);
}
```
Allows swapping pathfinding algorithms without changing client code.

### Observer Pattern
- `StateModel` notifies listeners when data changes

### MVC
- `StateModel` = Model
- `SyncBoardUI`, `MapScreen` = View/Controller

### Dependency Injection
- Dependencies passed via constructor

## Threading model

- **EDT (Event Dispatch Thread)**: All GUI updates
- **Background threads**: Network I/O (socket read/write)
- `SwingUtilities.invokeLater()` for thread-safe UI updates

## Requirements

- Java 11 or higher
- No external libraries

Made for a distributed systems learning project.
