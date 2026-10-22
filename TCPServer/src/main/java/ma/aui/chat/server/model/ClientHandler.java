package ma.aui.chat.server.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Serves one connected client for the lifetime of its connection.
 * Runs on its own virtual thread (see {@link ChatServer#start()}).
 */
class ClientHandler implements Runnable {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final Socket socket;
    private final ChatServer server;
    private PrintWriter out;
    private String username;
    private boolean readOnly;

    ClientHandler(Socket socket, ChatServer server) {
        this.socket = socket;
        this.server = server;
    }

    @Override
    public void run() {
        String address = socket.getRemoteSocketAddress().toString();
        server.log("Client connected from " + address + " (virtual thread #" + Thread.currentThread().threadId() + ")");
        boolean registered = false;
        try (socket;
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);

            registered = handshake(in.readLine());
            if (!registered) {
                return;
            }
            String line;
            while ((line = in.readLine()) != null) {
                if (!handle(Protocol.decode(line))) {
                    break;
                }
            }
        } catch (IOException e) {
            if (server.isRunning()) {
                server.log("Connection with " + (username != null ? username : address) + " lost: " + e.getMessage());
            }
        } finally {
            if (registered) {
                server.unregister(this);
                server.broadcast(Protocol.encode(Protocol.SYS, username + " left the chat"));
                server.log(username + " disconnected");
            }
        }
    }

    /** Reads the JOIN line, picks the display name and registers the client. */
    private boolean handshake(String firstLine) {
        if (firstLine == null) {
            return false;
        }
        List<String> parts = Protocol.decode(firstLine);
        if (!Protocol.JOIN.equals(parts.get(0))) {
            send(Protocol.encode(Protocol.REJECT, "Expected JOIN as first message"));
            return false;
        }
        String requested = parts.size() > 1 ? parts.get(1).trim() : "";
        if (requested.isEmpty()) {
            readOnly = true;
            username = server.nextGuestName();
        } else {
            String error = Protocol.validateUsername(requested);
            if (error != null) {
                send(Protocol.encode(Protocol.REJECT, error));
                return false;
            }
            username = requested;
        }
        if (!server.register(this)) {
            send(Protocol.encode(Protocol.REJECT, "Username '" + username + "' is already taken"));
            server.log("Rejected duplicate username " + username);
            return false;
        }
        send(Protocol.encode(Protocol.WELCOME, username, readOnly ? Protocol.MODE_READ_ONLY : Protocol.MODE_FULL));
        server.log("Welcome " + username + (readOnly ? " (READ-ONLY MODE)" : ""));
        server.broadcast(Protocol.encode(Protocol.SYS,
                username + (readOnly ? " joined in read-only mode" : " joined the chat")));
        return true;
    }

    /** @return false when the client asked to disconnect. */
    private boolean handle(List<String> parts) {
        switch (parts.get(0)) {
            case Protocol.MSG -> handleMessage(parts.size() > 1 ? parts.get(1) : "");
            case Protocol.USERS -> {
                send(Protocol.encode(Protocol.USERS, server.getActiveUsers().toArray(String[]::new)));
                server.log(username + " requested the list of active users");
            }
            case Protocol.QUIT -> {
                return false;
            }
            default -> send(Protocol.encode(Protocol.ERROR, "Unknown command: " + parts.get(0)));
        }
        return true;
    }

    private void handleMessage(String text) {
        if (readOnly) {
            send(Protocol.encode(Protocol.ERROR, "READ-ONLY MODE: you cannot send messages"));
            server.log("Blocked message from read-only client " + username);
            return;
        }
        if (text.isBlank()) {
            return;
        }
        String time = LocalTime.now().format(TIME);
        server.broadcast(Protocol.encode(Protocol.MSG, time, username, text));
        server.log("[" + time + "] " + username + ": " + text);
    }

    /** Thread-safe: other clients' threads call this when broadcasting. */
    synchronized void send(String line) {
        if (out != null) {
            out.println(line);
        }
    }

    void close() {
        try {
            socket.close();
        } catch (IOException ignored) {
        }
    }

    String getUsername() {
        return username;
    }

    boolean isReadOnly() {
        return readOnly;
    }
}
