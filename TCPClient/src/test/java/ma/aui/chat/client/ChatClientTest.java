package ma.aui.chat.client;

import ma.aui.chat.client.model.ChatClient;
import ma.aui.chat.client.model.ChatClientListener;
import ma.aui.chat.client.model.ChatMessage;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Drives the client model against a scripted fake server; no JavaFX involved. */
class ChatClientTest {

    private final BlockingQueue<String> events = new LinkedBlockingQueue<>();

    private final ChatClientListener recorder = new ChatClientListener() {
        @Override
        public void onConnected(String username, boolean readOnly) {
            events.add("connected " + username + " " + readOnly);
        }

        @Override
        public void onChatMessage(ChatMessage m) {
            events.add("message " + m.sender() + " " + m.text() + " mine=" + m.mine());
        }

        @Override
        public void onUserList(List<String> users) {
            events.add("users " + users);
        }

        @Override
        public void onError(String text) {
            events.add("error " + text);
        }

        @Override
        public void onDisconnected(String reason, boolean byUser) {
            events.add("disconnected byUser=" + byUser);
        }

        @Override
        public void onRejected(String reason) {
            events.add("rejected " + reason);
        }
    };

    @Test
    void translatesCommandsAndServerLines() throws Exception {
        try (ServerSocket fakeServer = new ServerSocket(0)) {
            ChatClient client = new ChatClient("127.0.0.1", fakeServer.getLocalPort(), recorder);
            client.connect("alice");

            try (Socket s = fakeServer.accept()) {
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter out = new PrintWriter(s.getOutputStream(), true, StandardCharsets.UTF_8);

                assertEquals("JOIN|alice", in.readLine());
                out.println("WELCOME|alice|FULL");
                assertEquals("connected alice false", next());

                client.submit("hi there");
                assertEquals("MSG|hi there", in.readLine());
                out.println("MSG|10:15|alice|hi there");
                assertEquals("message alice hi there mine=true", next());

                client.submit("allUsers");
                assertEquals("USERS", in.readLine());
                out.println("USERS|alice|bob");
                assertEquals("users [alice, bob]", next());

                client.submit("bye");
                assertEquals("QUIT", in.readLine());
                assertEquals("disconnected byUser=true", next());
            }
        }
    }

    @Test
    void readOnlyClientDoesNotSendMessages() throws Exception {
        try (ServerSocket fakeServer = new ServerSocket(0)) {
            ChatClient client = new ChatClient("127.0.0.1", fakeServer.getLocalPort(), recorder);
            client.connect("");

            try (Socket s = fakeServer.accept()) {
                BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8));
                PrintWriter out = new PrintWriter(s.getOutputStream(), true, StandardCharsets.UTF_8);

                assertEquals("JOIN|", in.readLine());
                out.println("WELCOME|Guest-1|READ_ONLY");
                assertEquals("connected Guest-1 true", next());

                client.submit("hello?");
                assertTrue(next().startsWith("error READ-ONLY MODE"));
                client.submit("end");
                assertEquals("QUIT", in.readLine());
            }
        }
    }

    @Test
    void reportsLostConnection() throws Exception {
        try (ServerSocket fakeServer = new ServerSocket(0)) {
            ChatClient client = new ChatClient("127.0.0.1", fakeServer.getLocalPort(), recorder);
            client.connect("bob");
            try (Socket s = fakeServer.accept()) {
                new BufferedReader(new InputStreamReader(s.getInputStream(), StandardCharsets.UTF_8)).readLine();
                new PrintWriter(s.getOutputStream(), true, StandardCharsets.UTF_8).println("WELCOME|bob|FULL");
                assertEquals("connected bob false", next());
            }
            assertEquals("disconnected byUser=false", next());
        }
    }

    private String next() throws InterruptedException {
        String event = events.poll(3, TimeUnit.SECONDS);
        if (event == null) {
            throw new AssertionError("no event received");
        }
        return event;
    }
}
