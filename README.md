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
   - Use the **Group Chat** tab for everyone; click an online user to open a **DM** tab for private messages.

6. **Direct message tabs**
   - Click a username in the list to open `DM: <name>`.

7. **GUI file transfer**
   - In a DM tab, use **Send File** and pick a file. The receiver sees progress, then a save dialog when the transfer completes.

8. **Admin server monitor**
   - Starting the server opens an **Admin Monitor** window: connected users and which worker thread handles each client.

9. **Emoji / styled chat**
   - Text emoticons like `:)`, `:(`, `<3` are replaced with emoji; usernames and roles use distinct colors in `JTextPane` panes.

10. **Chat history persistence**
   - JSONL logs under `%USERPROFILE%\.introg_chat\` reload after login.

## Project Structure

```text
src/
  common/
    Message.java
    User.java
    FileTransfer.java
  server/
    ChatServer.java
    ClientHandler.java
    AdminMonitor.java
  client/
    ChatClient.java
    ChatGUI.java
    ChatHistoryStore.java
    StyleUtil.java
```

## Requirements

- Java JDK 17+ (or any version that supports your local `javac`/`java` setup)
- Windows PowerShell, CMD, or any terminal

## Build

```powershell
javac -d out src/common/*.java src/server/*.java src/client/*.java
```

## Run

### 1) Start server

```powershell
java -cp out server.ChatServer
```

### 2) Start client (another terminal)

```powershell
java -cp out client.ChatGUI
```

Optional host and port:

```powershell
java -cp out client.ChatGUI 127.0.0.1 5000
```

## Usage

1. Start the server (admin monitor opens on the server desktop).
2. Open one or more clients; history reloads after login.
3. **Group Chat** for broadcasts; click a user for a DM tab (text or **Send File**).

## Notes

- Use classpath-based commands (`java -cp out ...`) instead of `java ChatGUI.java`.
- If imports appear unresolved in the IDE, ensure `.vscode/settings.json` includes:
  - `"java.project.sourcePaths": ["src"]`
