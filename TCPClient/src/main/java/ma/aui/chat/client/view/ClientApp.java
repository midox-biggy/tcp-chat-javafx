package ma.aui.chat.client.view;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ma.aui.chat.client.config.ClientConfig;
import ma.aui.chat.client.model.ChatClient;
import ma.aui.chat.client.model.ChatClientListener;
import ma.aui.chat.client.model.ChatMessage;

import java.util.List;

/**
 * JavaFX application and controller: switches between the login and chat screens and
 * relays events between the {@link ChatClient} model and the views.
 * <p>
 * Model callbacks arrive on a network thread, so each one is handed to the JavaFX
 * Application Thread with {@link Platform#runLater}.
 */
public class ClientApp extends Application implements ChatClientListener {

    private Stage stage;
    private Scene scene;
    private ClientConfig config;
    private LoginView loginView;
    private ChatView chatView;
    private ChatClient client;

    @Override
    public void start(Stage stage) throws Exception {
        this.stage = stage;
        config = ClientConfig.resolve(getParameters().getRaw().toArray(String[]::new));

        loginView = new LoginView(config.host() + ":" + config.port(), this::join);
        scene = new Scene(loginView.getRoot(), 720, 600);
        scene.getStylesheets().add(getClass().getResource("/client.css").toExternalForm());
        stage.setTitle("TCP Chat Client");
        stage.setMinWidth(520);
        stage.setMinHeight(420);
        stage.setScene(scene);
        stage.show();
        loginView.focus();
    }

    private void join(String username) {
        loginView.setBusy(true);
        client = new ChatClient(config.host(), config.port(), this);
        client.connect(username);
    }

    /** LEAVE button: disconnect, or go back to login if the connection is already gone. */
    private void leave() {
        if (client != null && client.isConnected()) {
            client.leave();
        } else {
            showLogin(null);
        }
    }

    private void showLogin(String info) {
        loginView.setBusy(false);
        if (info != null) {
            loginView.showInfo(info);
        }
        scene.setRoot(loginView.getRoot());
        stage.setTitle("TCP Chat Client");
        loginView.focus();
    }

    @Override
    public void stop() {
        if (client != null) {
            client.leave();
        }
    }

    // ---- ChatClientListener (called from the network thread) -------------------

    @Override
    public void onConnected(String username, boolean readOnly) {
        Platform.runLater(() -> {
            chatView = new ChatView(username, readOnly, client::submit, this::leave);
            scene.setRoot(chatView.getRoot());
            stage.setTitle("TCP Chat Client - " + username + (readOnly ? " (read-only)" : ""));
            chatView.addSystem("Connected to " + client.getServerAddress()
                    + (readOnly ? "  •  READ-ONLY MODE: you can read but not send messages" : ""));
            chatView.focusInput();
        });
    }

    @Override
    public void onRejected(String reason) {
        Platform.runLater(() -> {
            loginView.setBusy(false);
            loginView.showError(reason);
        });
    }

    @Override
    public void onConnectionFailed(String reason) {
        onRejected(reason);
    }

    @Override
    public void onChatMessage(ChatMessage message) {
        Platform.runLater(() -> chatView.addMessage(message));
    }

    @Override
    public void onSystemMessage(String text) {
        Platform.runLater(() -> chatView.addSystem(text));
    }

    @Override
    public void onUserList(List<String> users) {
        Platform.runLater(() -> chatView.addUserList(users));
    }

    @Override
    public void onError(String text) {
        Platform.runLater(() -> chatView.addError(text));
    }

    @Override
    public void onDisconnected(String reason, boolean byUser) {
        Platform.runLater(() -> {
            if (byUser) {
                showLogin(reason + ". You can join again.");
            } else {
                chatView.showOffline(reason);
            }
        });
    }
}
