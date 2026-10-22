package ma.aui.chat.server;

import ma.aui.chat.server.model.ChatServer;
import ma.aui.chat.server.model.Protocol;
import ma.aui.chat.server.model.ServerListener;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs the real server model over real sockets, with no JavaFX involved. */
class ChatServerTest {

    private final List<String> log = new CopyOnWriteArrayList<>();
    private ChatServer server;

    @BeforeEach
    void startServer() throws IOException {
        server = new ChatServer("127.0.0.1", 0, new ServerListener() {
            @Override
            public void onLog(String message) {
                log.add(message);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop();
    }

    @Test
    void broadcastsMessagesWithSenderAndTimeToEveryClient() throws IOException {
        try (TestClient alice = new TestClient("alice"); TestClient bob = new TestClient("bob")) {
            assertEquals("WELCOME|alice|FULL", alice.next());
            assertEquals("WELCOME|bob|FULL", bob.next());
            alice.skipUntil("SYS|bob joined the chat");

            alice.send(Protocol.encode(Protocol.MSG, "hello | world\nsecond line"));

            for (TestClient c : List.of(alice, bob)) {
                List<String> msg = Protocol.decode(c.skipUntilPrefix("MSG|"));
                assertTrue(msg.get(1).matches("\\d\\d:\\d\\d"), "time stamp");
                assertEquals("alice", msg.get(2));
                assertEquals("hello | world\nsecond line", msg.get(3));
            }
            assertTrue(log.stream().anyMatch(l -> l.startsWith("Server started")));
            assertTrue(log.contains("Welcome alice"));
            assertTrue(log.stream().anyMatch(l -> l.contains("virtual thread")));
        }
    }

    @Test
    void allUsersListsActiveClientsToTheRequesterOnly() throws IOException {
        try (TestClient alice = new TestClient("alice"); TestClient guest = new TestClient("")) {
            alice.next();
            assertEquals("WELCOME|Guest-1|READ_ONLY", guest.next());

            alice.send(Protocol.USERS);
            assertEquals(List.of("USERS", "alice", "Guest-1 (read-only)"), Protocol.decode(alice.skipUntilPrefix("USERS")));
        }
    }

    @Test
    void readOnlyClientCannotSendMessages() throws IOException {
        try (TestClient guest = new TestClient("")) {
            guest.next();
            guest.send(Protocol.encode(Protocol.MSG, "let me talk"));
            assertEquals("ERROR|READ-ONLY MODE: you cannot send messages", guest.skipUntilPrefix("ERROR"));
        }
    }

    @Test
    void duplicateUsernameIsRejected() throws IOException {
        try (TestClient first = new TestClient("alice"); TestClient second = new TestClient("ALICE")) {
            first.next();
            assertTrue(second.next().startsWith("REJECT|"));
        }
    }

    @Test
    void quitRemovesTheClientAndNotifiesOthers() throws IOException {
        try (TestClient alice = new TestClient("alice"); TestClient bob = new TestClient("bob")) {
            alice.next();
            bob.next();
            bob.send(Protocol.QUIT);
            alice.skipUntil("SYS|bob left the chat");
            alice.send(Protocol.USERS);
            assertEquals("USERS|alice", alice.skipUntilPrefix("USERS"));
        }
    }

    /** Minimal raw-socket client speaking the protocol. */
    private class TestClient implements AutoCloseable {
        private final Socket socket;
        private final BufferedReader in;
        private final PrintWriter out;

        TestClient(String username) throws IOException {
            socket = new Socket("127.0.0.1", server.getPort());
            socket.setSoTimeout(3000);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
            out = new PrintWriter(socket.getOutputStream(), true, StandardCharsets.UTF_8);
            send(Protocol.encode(Protocol.JOIN, username));
        }

        void send(String line) {
            out.println(line);
        }

        String next() throws IOException {
            return in.readLine();
        }

        void skipUntil(String expected) throws IOException {
            skipUntilPrefix(expected);
        }

        String skipUntilPrefix(String prefix) throws IOException {
            String line;
            while ((line = in.readLine()) != null) {
                if (line.startsWith(prefix)) {
                    return line;
                }
            }
            throw new AssertionError("Connection closed before receiving " + prefix);
        }

        @Override
        public void close() throws IOException {
            socket.close();
        }
    }
}
