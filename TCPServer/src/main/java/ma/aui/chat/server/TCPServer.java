package ma.aui.chat.server;

import javafx.application.Application;
import ma.aui.chat.server.view.ServerApp;

/**
 * Entry point: {@code java -jar TCPServer.jar}
 * <p>
 * Kept separate from the JavaFX {@link Application} subclass so the executable
 * JAR can start JavaFX from the classpath.
 */
public class TCPServer {

    public static void main(String[] args) {
        Application.launch(ServerApp.class, args);
    }
}
