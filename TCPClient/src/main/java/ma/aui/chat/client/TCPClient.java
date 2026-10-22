package ma.aui.chat.client;

import javafx.application.Application;
import ma.aui.chat.client.config.ClientConfig;
import ma.aui.chat.client.view.ClientApp;

/**
 * Entry point: {@code java -jar TCPClient.jar <ServerIPAddress> <PortNumber>}
 * <p>
 * Validates the arguments before opening any window, then starts JavaFX.
 */
public class TCPClient {

    public static void main(String[] args) {
        try {
            ClientConfig config = ClientConfig.resolve(args);
            System.out.println("Connecting to " + config.host() + ":" + config.port());
        } catch (Exception e) {
            System.err.println(e.getMessage());
            System.exit(1);
        }
        Application.launch(ClientApp.class, args);
    }
}
