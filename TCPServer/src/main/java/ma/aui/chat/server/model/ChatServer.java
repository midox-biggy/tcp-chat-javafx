package ma.aui.chat.server.model;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The chat server model: accepts TCP connections and distributes messages.
 * <p>
 * Each connected client is served by its own {@link ClientHandler} running on a
 * virtual thread, so thousands of clients can block on I/O without tying up
 * operating-system threads. This class has no JavaFX dependency.
 */
public class ChatServer {

    private final String host;
    private final int port;
    private final ServerListener listener;

    /** Connected clients by display name; sorted so "allUsers" lists them alphabetically. */
    private final Map<String, ClientHandler> clients = new ConcurrentSkipListMap<>(String.CASE_INSENSITIVE_ORDER);
    private final AtomicInteger guestCounter = new AtomicInteger();

    private ServerSocket serverSocket;
    private ExecutorService clientExecutor;
    private volatile boolean running;

    public ChatServer(String host, int port, ServerListener listener) {
        this.host = host;
        this.port = port;
        this.listener = listener;
    }

    /** Binds the server socket and starts accepting clients in the background. */
    public void start() throws IOException {
        serverSocket = new ServerSocket();
        serverSocket.setReuseAddress(true);
        serverSocket.bind(new InetSocketAddress(host, port));
        clientExecutor = Executors.newVirtualThreadPerTaskExecutor();
        running = true;
        log("Server started on " + host + ":" + getPort());
        Thread.ofVirtual().name("acceptor").start(this::acceptLoop);
    }

    private void acceptLoop() {
        while (running) {
            try {
                log("Waiting for client...");
                Socket socket = serverSocket.accept();
                clientExecutor.submit(new ClientHandler(socket, this));
            } catch (SocketException e) {
                // the server socket was closed by stop()
            } catch (IOException e) {
                if (running) {
                    log("Accept failed: " + e.getMessage());
                }
            }
        }
    }

    /** Disconnects every client and releases the port. */
    public void stop() {
        if (!running) {
            return;
        }
        broadcast(Protocol.encode(Protocol.SYS, "Server is shutting down"));
        running = false;
        try {
            serverSocket.close();
        } catch (IOException ignored) {
        }
        clients.values().forEach(ClientHandler::close);
        clientExecutor.shutdownNow();
        log("Server stopped");
    }

    /** Actual listening port (useful when configured with port 0). */
    public int getPort() {
        return serverSocket == null ? port : serverSocket.getLocalPort();
    }

    public boolean isRunning() {
        return running;
    }

    /** Display names of all active clients, read-only guests marked as such. */
    public List<String> getActiveUsers() {
        List<String> users = new ArrayList<>();
        for (ClientHandler client : clients.values()) {
            users.add(client.isReadOnly() ? client.getUsername() + " (read-only)" : client.getUsername());
        }
        return users;
    }

    // ---- used by ClientHandler -------------------------------------------------

    String nextGuestName() {
        return Protocol.GUEST_PREFIX + guestCounter.incrementAndGet();
    }

    /** @return false when the name is already taken. */
    boolean register(ClientHandler client) {
        if (clients.putIfAbsent(client.getUsername(), client) != null) {
            return false;
        }
        listener.onClientConnected(client.getUsername(), client.isReadOnly());
        return true;
    }

    void unregister(ClientHandler client) {
        if (clients.remove(client.getUsername(), client)) {
            listener.onClientDisconnected(client.getUsername());
        }
    }

    /** Sends one protocol line to every active connection. */
    void broadcast(String line) {
        clients.values().forEach(client -> client.send(line));
    }

    void log(String message) {
        listener.onLog(message);
    }
}
