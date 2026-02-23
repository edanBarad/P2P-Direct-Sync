# P2P Tactical Board

## About this project

As a second-year CS student, I built this project to showcase what I've learned so far in school — **Java**, **OOP design patterns**, and **network socket programming**. The goal was to create something that ties these concepts together in a practical way, with a focus on security industry applications.

I also used **AI-assisted context engineering** to speed up development. By providing clear requirements and constraints to an AI model, I was able to focus on understanding the architecture while getting help with boilerplate code and documentation.

## Features

### Tab-Based Interface
The application uses a tabbed interface with four main sections:
- **Chat** - Messenger-style communication with priority alerts
- **Map** - Shared tactical map with pathfinding and zones
- **Audit Log** - Security event logging and export
- **Check-in** - Lone worker safety system

---

### Chat System

#### Messenger-Style Communication
- Real-time synchronized chat between Host and Client
- Messages are color-coded by sender (Host = blue, Client = red)
- System messages in gray (italic)
- Type and press Enter or click Send

#### Priority Alerts System
Three priority levels for messages:
- **Normal** - Standard message display
- **Alert** - Orange text with `[ALERT]` prefix, screen flashes orange twice
- **Critical** - Red, bold, larger text with `[CRITICAL]` prefix, screen flashes red three times with audio alert

#### Self-Destructing Messages
- Set a timer (1-300 seconds) for messages to auto-delete
- Messages display `[SD: Xs]` indicator
- After expiration, shows "[Message self-destructed]" notification
- Useful for sensitive tactical communications

---

### Shared Map with Pathfinding

#### Pre-loaded Grid
- Yellow points with green connecting lines on white background
- Approximately half the grid points randomly removed for more working space

#### Point Operations
- **Add points**: Left-click empty space (auto-connects to nearest point)
- **Add edges**: Click your point, then click another point to create custom edge
- **Delete points**: Right-click your own points (grid points are permanent)

#### Color Coding
| Border Color | Meaning |
|--------------|---------|
| Gray | Grid points (permanent system points) |
| Blue | Host's points |
| Red | Client's points |

#### Pathfinding with Dijkstra
- **Find Path**: Select your point as source → other user's point as destination
- Uses **Dijkstra's algorithm** with edge length (pixel distance) as weight
- **Multiple paths** can coexist with different colors
- **Parallel edges** drawn when paths share the same line
- Path length displayed in pixel distance
- Path events posted to chat automatically

#### Geofencing (Map Zones)
- **Add Zone**: Click two corners to create a rectangular zone
- **Add Circle Zone**: Click two corners to create a circular zone
- Zones displayed as semi-transparent overlays (blue for rectangles, red for circles)
- Dashed borders with zone labels
- Zone events logged to audit trail

#### Patrol Routes
- **Create Patrol**: Click 3+ points to define checkpoint route
- **Start Patrol**: Begins simulated patrol with 3-second intervals per checkpoint
- **End Patrol**: Manually stop current patrol
- Routes shown as dashed green lines with numbered checkpoints
- Current checkpoint highlighted in green
- Progress posted to chat and audit log

---

### Audit Log

#### Event Logging
Records all security-relevant events with timestamps:
- Connections and disconnections
- Messages sent and received
- Points and edges added/removed
- Paths found and removed
- Zones created and triggered
- Patrol started, progress, completed
- Check-in success, missed, reset
- Self-destruct events

#### Features
- Real-time log display in dedicated tab
- **Export**: Save log to file for compliance records
- **Refresh**: Update display with latest entries
- **Clear**: Reset log (clearing is itself logged)

---

### Check-in System (Lone Worker Safety)

#### How It Works
1. Click **Start** to begin the countdown timer (default 60 seconds)
2. Click **Check In** before time runs out to reset the timer
3. If you miss a check-in, your peer is immediately alerted
4. Click **Stop** to end the monitoring session

#### Visual Feedback
- **Green**: Plenty of time remaining (>30s)
- **Orange**: Warning zone (10-30s)
- **Red**: Critical (<10s)
- **Flashing red**: Missed check-in alert

#### Network Alerts
- Successful check-ins notify your peer
- Missed check-ins trigger alert on peer's screen with audio

---

## How to Run

### Compile
```bash
javac -d target/classes src/main/java/com/tactical/p2p/*.java
```

### Run Two Instances

Terminal 1 (Host):
```bash
java -cp target/classes com.tactical.p2p.App host
```

Terminal 2 (Client):
```bash
java -cp target/classes com.tactical.p2p.App client
```

Or just run without args and pick from the GUI dialog.

---

## Project Structure

```
src/main/java/com/tactical/p2p/
├── App.java                 # Main class, handles startup
├── StateModel.java          # Stores the shared state
├── NetworkManager.java      # Socket communication
├── SyncBoardUI.java         # Main GUI with tabs
├── MapScreen.java           # Map panel with points, lines, zones, patrols
├── Point.java               # 2D point class (ratio coordinates)
├── Line.java                # Line with start/end points
├── PathStrategy.java        # Strategy interface for pathfinding
├── ShortestPathStrategy.java # Dijkstra implementation
├── AuditLogger.java         # Security audit logging
└── CheckInManager.java      # Lone worker safety system
```

---

## Network Protocol

Uses TCP sockets on port 8888. Messages are UTF-8 strings ending with newline.

### Message Types
| Format | Description |
|--------|-------------|
| `MSG:sender:priority:text:sd` | Chat message with priority and self-destruct |
| `POINT:ACTION:x:y:fromHost` | Point add/remove |
| `EDGE:x1:y1:x2:y2` | Custom edge added |
| `PATH:sx:sy:dx:dy` | Path found |
| `PATHREMOVE:index` | Path removed |
| `CHECKIN:user` | User checked in |
| `CHECKIN_MISSED:user` | User missed check-in |
| `SD:id` | Message self-destructed |

---

## Design Patterns

### Strategy Pattern
```java
public interface PathStrategy {
    List<Line> findPath(Point source, Point target, List<Point> points, List<Line> lines);
    String getName();
}
```
Allows swapping pathfinding algorithms without changing client code.

### Observer Pattern
- `StateModel` notifies listeners when data changes
- Callbacks throughout for event-driven architecture

### MVC Architecture
- `StateModel` = Model
- `SyncBoardUI`, `MapScreen` = View/Controller
- `AuditLogger`, `CheckInManager` = Services

### Dependency Injection
- Dependencies passed via constructor
- Callbacks set via setter methods

---

## Threading Model

- **EDT (Event Dispatch Thread)**: All GUI updates
- **Background threads**: Network I/O (socket read/write)
- `SwingUtilities.invokeLater()` for thread-safe UI updates
- Swing Timers for countdown, patrol simulation, screen flashing

---

## Requirements

- Java 11 or higher
- No external libraries

---

## Use Cases

This application simulates a tactical coordination system suitable for:
- Security team coordination
- Event management and monitoring
- Lone worker safety compliance
- Patrol route management
- Geofenced area monitoring

Made for a distributed systems learning project with security industry focus.
