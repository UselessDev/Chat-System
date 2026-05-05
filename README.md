# IntroG Chat Application (Java GUI)

A simple Java socket-based chat system with a Swing GUI client and a multithreaded server.

## Features
1. **GUI Echo Client-Server**
   - Client sends a message and receives a server echo back.
   - Example:
     - `You: Hello`
     - `Server: Hello`
2. **Multi-Client GUI Chat**
   - Multiple clients can connect at the same time.
   - Broadcast messages appear in all connected client windows in real time.
3. **Username-based Chat**
   - User enters a username on startup.
   - Messages are shown with sender names (e.g., `Alice: Hi`).
4. **Connection Status Panel**
   - Client GUI displays:
     - Connected/Disconnected status
     - Server IP and port
5. **Broadcast and Private Messaging**
   - Select `Send to All` for group chat.
   - Select a specific user for private messages.

## Project Structure

```text
src/
  common/
    Message.java
    User.java
  server/
    ChatServer.java
    ClientHandler.java
  client/
    ChatClient.java
    ChatGUI.java
```

## Requirements

- Java JDK 17+ (or any version that supports your local `javac`/`java` setup)
- Windows PowerShell, CMD, or any terminal


## Run

### 1) Start Server

```(Current Dir) CMD/Terminal
java ChatServer.java
```

### 2) Start Client (open another terminal)

```(Current Dir) CMD/Terminal
java ChatGUI.java
```

Optional custom host and port:

(OPTIONAL)
```powershell
java -cp out client.ChatGUI 127.0.0.1 5000
```

## Usage

1. Start the server.
2. Open one or more client instances.
3. Enter a username when prompted.
4. Type a message and click **Send**.
5. Choose between:
   - **Send to All** for broadcast
   - A specific username for private message

## Notes

- Use classpath-based commands (`java -cp out ...`) instead of `java ChatGUI.java`.
- If imports appear unresolved in the IDE, ensure `.vscode/settings.json` includes:
  - `"java.project.sourcePaths": ["src"]`
