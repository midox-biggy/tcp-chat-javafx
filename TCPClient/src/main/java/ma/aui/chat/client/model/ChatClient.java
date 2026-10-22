package ma.aui.chat.client.model;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Client-side model: owns the TCP socket, interprets what the user types and turns
 * server lines into {@link ChatClientListener} events. Has no JavaFX dependency.
 */
public class ChatClient {

    public static final String CMD_ALL_USERS = "allUsers";
    private static final int CONNECT_TIMEOUT_MS = 5000;

    private final String host;
    private final int port;
    private final ChatClientListener listener;

    private Socket socket;
    private PrintWriter out;
    private volatile String username;
    private volatile boolean readOnly;
    private volatile boolean connected;
    private volatile boolean leaving;

    public ChatClient(String host, int port, ChatClientListener listener) {
        this.host = host;
        this.port = port;
        this.listener = listener;
    }

    /**
     * Connects in the background and asks to join. An empty username joins in
     * read-only mode. The outcome is reported through the listener.
     */
    public void connect(String requestedUsername) {
        Thread.ofVirtual().name("chat-reader").start(() -> run(requestedUsername == null ? "" : requestedUsername.trim()));
    }

    private void run(String requestedUsername) {
        try {
            socket = new Socket();
            socket.connect(new InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
        } catch (IOException e) {
            listener.onConnectionFailed("Cannot reach server at " + host + ":" + port + " (" + e.getMessage() + ")");
            return;
        }

        boolean rejected = false;
        try (BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8))) {
            send(Protocol.encode(Protocol.JOIN, requestedUsername));
            String line;
            while ((line = in.readLine()) != null) {
                rejected |= dispatch(Protocol.decode(line));
            }
        } catch (IOException e) {
            // socket closed: handled below
        } finally {
            close();
            if (connected) {
                connected = false;
                listener.onDisconnected(leaving ? "You left the chat" : "Connection to the server was lost", leaving);
            } else if (!rejected) {
                listener.onConnectionFailed("The server closed the connection");
            }
        }
    }

    /** @return true if the server rejected our JOIN. */
    private boolean dispatch(List<String> parts) {
        String type = parts.get(0);
        switch (type) {
            case Protocol.WELCOME -> {
                username = field(parts, 1);
                readOnly = Protocol.MODE_READ_ONLY.equals(field(parts, 2));
                connected = true;
                listener.onConnected(username, readOnly);
            }
            case Protocol.REJECT -> {
                listener.onRejected(field(parts, 1));
                return true;
            }
            case Protocol.MSG -> {
                String sender = field(parts, 2);
                listener.onChatMessage(new ChatMessage(field(parts, 1), sender, field(parts, 3), sender.equals(username)));
            }
            case Protocol.SYS -> listener.onSystemMessage(field(parts, 1));
            case Protocol.USERS -> listener.onUserList(List.copyOf(parts.subList(1, parts.size())));
            case Protocol.ERROR -> listener.onError(field(parts, 1));
            default -> { /* ignore unknown messages from newer servers */ }
        }
        return false;
    }

    /**
     * Handles one line typed by the user: the commands "allUsers", "end" and "bye",
     * or a chat message (refused locally in read-only mode).
     */
    public void submit(String input) {
        if (input == null || input.isBlank() || !connected) {
            return;
        }
        String text = input.strip();
        if (text.equalsIgnoreCase(CMD_ALL_USERS)) {
            send(Protocol.USERS);
        } else if (text.equalsIgnoreCase("end") || text.equalsIgnoreCase("bye")) {
            leave();
        } else if (readOnly) {
            listener.onError("READ-ONLY MODE: you cannot send messages. Commands: allUsers, end, bye");
        } else {
            send(Protocol.encode(Protocol.MSG, text));
        }
    }

    /** Tells the server we are leaving, then closes the socket. */
    public void leave() {
        if (!connected || leaving) {
            return;
        }
        leaving = true;
        send(Protocol.QUIT);
        close();
    }

    private synchronized void send(String line) {
        if (out != null) {
            out.println(line);
        }
    }

    private void close() {
        try {
            if (socket != null) {
                socket.close();
            }
        } catch (IOException ignored) {
        }
    }

    private static String field(List<String> parts, int index) {
        return index < parts.size() ? parts.get(index) : "";
    }

    public String getServerAddress() {
        return host + ":" + port;
    }

    public String getUsername() {
        return username;
    }

    public boolean isReadOnly() {
        return readOnly;
    }

    public boolean isConnected() {
        return connected;
    }
}
