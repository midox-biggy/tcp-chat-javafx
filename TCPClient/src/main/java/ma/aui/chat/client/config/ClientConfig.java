package ma.aui.chat.client.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Where the client connects. Priority (highest first):
 * <ol>
 *   <li>command line: {@code <ServerIPAddress> <PortNumber>}</li>
 *   <li>{@code ./client.properties}, or the file given with {@code -Dconfig=path}</li>
 *   <li>defaults bundled in the JAR</li>
 * </ol>
 */
public record ClientConfig(String host, int port) {

    public static final String FILE_NAME = "client.properties";
    public static final String USAGE = "Usage: java -jar TCPClient.jar <ServerIPAddress> <PortNumber>   (e.g. localhost 3000)";

    public static ClientConfig resolve(String[] args) throws IOException {
        if (args.length >= 2) {
            return new ClientConfig(args[0], parsePort(args[1]));
        }
        if (args.length == 1) {
            throw new IllegalArgumentException("Missing port number.\n" + USAGE);
        }

        Properties props = new Properties();
        try (InputStream in = ClientConfig.class.getResourceAsStream("/" + FILE_NAME)) {
            if (in != null) {
                props.load(in);
            }
        }
        Path external = Path.of(System.getProperty("config", FILE_NAME));
        if (Files.isRegularFile(external)) {
            try (Reader reader = Files.newBufferedReader(external, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
        }
        return new ClientConfig(props.getProperty("server.host", "localhost").trim(),
                parsePort(props.getProperty("server.port", "3000").trim()));
    }

    private static int parsePort(String text) {
        try {
            int port = Integer.parseInt(text);
            if (port >= 1 && port <= 65535) {
                return port;
            }
        } catch (NumberFormatException ignored) {
        }
        throw new IllegalArgumentException("Invalid port number '" + text + "'.\n" + USAGE);
    }
}
