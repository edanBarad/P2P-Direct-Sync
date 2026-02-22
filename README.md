# P2P Shared Board

## About this project

As a second-year CS student, I built this project to showcase what I've learned so far in school — **Java**, **OOP design patterns**, and **network socket programming**. The goal was to create something that ties these concepts together in a practical way.

I also used **AI-assisted context engineering** to speed up development. By providing clear requirements and constraints to an AI model, I was able to focus on understanding the architecture while getting help with boilerplate code and documentation. It's like pair programming with a very patient partner who never gets tired of explaining threading models.

## How it works

One instance runs as a **Host** (server) and waits for a connection. The other runs as a **Client** and connects to the host. Whatever you type in your bottom text area shows up in the other person's top text area.

```
┌─────────────────┐         ┌─────────────────┐
│  Instance A     │         │  Instance B     │
│  ┌───────────┐  │         │  ┌───────────┐  │
│  │ Remote    │◄─┼─────────┼─►│ Local     │  │
│  │ (receive) │  │  TCP    │  │ (type)    │  │
│  ├───────────┤  │  Socket │  ├───────────┤  │
│  │ Local     │──┼─────────┼──►│ Remote    │  │
│  │ (type)    │  │         │  │ (receive) │  │
│  └───────────┘  │         │  └───────────┘  │
└─────────────────┘         └─────────────────┘
```

## Requirements

- Java 11 or 17
- No external libraries needed

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
├── App.java            # Main class, handles startup
├── StateModel.java     # Stores the text state
├── NetworkManager.java # Socket stuff (sending/receiving)
└── SyncBoardUI.java    # The GUI (Swing)
```

## Design stuff

### Threading
- **EDT (Event Dispatch Thread)**: Handles all GUI updates
- **Background threads**: Handle network I/O so the UI doesn't freeze

This is important because blocking the EDT with socket operations would make the GUI unresponsive.

### Patterns used
- **Observer Pattern**: StateModel notifies listeners when data changes
- **MVC-ish**: StateModel is the model, SyncBoardUI is view/controller
- **Dependency Injection**: Pass dependencies via constructor instead of creating them inside

### SOLID principles
- **SRP**: Each class does one thing (network, state, or UI)
- **DIP**: Classes depend on abstractions, not concrete implementations

## Network protocol

Super simple:
- Uses TCP sockets on port 8888
- Messages are UTF-8 strings ending with newline
- Newlines in text are escaped as `\n` so they don't break the protocol

## Features

- **Text Board**: Real-time synchronized text editor
- **Shared Map**: Click to add pins, right-click to delete
  - Host pins are blue, Client pins are red
  - Pins sync instantly between instances
- Each user can switch between Text Board and Map freely
- [x] Two instances can connect
- [x] Real-time text sync both ways
- [x] Shared map with point synchronization
- [x] Handles multiline text
- [x] Graceful disconnect (no crashes)
- [x] Connection status indicator

## Possible improvements

- Add encryption
- Support more than 2 peers
- Add file sharing
- Use WebSockets for browser support

<img width="1916" height="1012" alt="Screenshot 2026-02-22 115055" src="https://github.com/user-attachments/assets/6ead2e44-4168-49f8-b853-7405e2690986" />


Made for a distributed systems learning project.
