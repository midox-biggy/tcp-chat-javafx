package ma.aui.chat.server.view;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.stage.Stage;
import ma.aui.chat.server.config.ServerConfig;
import ma.aui.chat.server.model.ChatServer;

/** JavaFX application: wires the server model to its view and starts listening. */
public class ServerApp extends Application {

    private ChatServer server;

    @Override
    public void start(Stage stage) {
        ServerView view = new ServerView();
        Scene scene = new Scene(view.getRoot(), 900, 560);
        scene.getStylesheets().add(getClass().getResource("/server.css").toExternalForm());
        stage.setTitle("TCP Chat Server");
        stage.setMinWidth(640);
        stage.setMinHeight(400);
        stage.setScene(scene);
        stage.show();

        try {
            ServerConfig config = ServerConfig.load();
            view.onLog("Configuration loaded from " + config.source());
            server = new ChatServer(config.host(), config.port(), view);
            server.start();
            view.showOnline(config.host(), server.getPort());
        } catch (Exception e) {
            view.onLog("Could not start server: " + e.getMessage());
            view.showOffline();
        }
    }

    @Override
    public void stop() {
        if (server != null) {
            server.stop();
        }
    }
}
