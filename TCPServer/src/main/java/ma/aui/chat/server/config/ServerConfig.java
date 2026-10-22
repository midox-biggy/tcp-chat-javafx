package ma.aui.chat.server.config;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Network settings of the server, read at runtime from {@code server.properties}.
 * <p>
 * Lookup order (later wins): defaults bundled in the JAR, then {@code ./server.properties}
 * in the working directory, or the file given with {@code -Dconfig=path}. Changing the
 * host or port therefore never requires recompiling.
 */
public record ServerConfig(String host, int port, String source) {

    public static final String FILE_NAME = "server.properties";

    public static ServerConfig load() throws IOException {
        Properties props = new Properties();
        String source = "built-in defaults";
        try (InputStream in = ServerConfig.class.getResourceAsStream("/" + FILE_NAME)) {
            if (in != null) {
                props.load(in);
            }
        }
        Path external = Path.of(System.getProperty("config", FILE_NAME));
        if (Files.isRegularFile(external)) {
            try (Reader reader = Files.newBufferedReader(external, StandardCharsets.UTF_8)) {
                props.load(reader);
            }
            source = external.toAbsolutePath().toString();
        }

        String host = props.getProperty("server.host", "0.0.0.0").trim();
        String portText = props.getProperty("server.port", "3000").trim();
        try {
            int port = Integer.parseInt(portText);
            if (port < 0 || port > 65535) {
                throw new NumberFormatException();
            }
            return new ServerConfig(host, port, source);
        } catch (NumberFormatException e) {
            throw new IOException("Invalid server.port '" + portText + "' in " + source);
        }
    }
}
