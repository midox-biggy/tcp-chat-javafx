package ma.aui.chat.client.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Line-based text protocol shared by the server and the client.
 * <p>
 * Every message is one UTF-8 line: {@code TYPE|field1|field2...}. Inside a field,
 * '\' , '|' and line breaks are escaped so a message can never break the framing.
 *
 * <pre>
 * Client -> Server                 Server -> Client
 *   JOIN|username (empty = guest)    WELCOME|username|FULL or READ_ONLY
 *   MSG|text                         REJECT|reason
 *   USERS                            MSG|HH:mm|sender|text
 *   QUIT                             SYS|text
 *                                    USERS|name1|name2|...
 *                                    ERROR|text
 * </pre>
 */
public final class Protocol {

    public static final String JOIN = "JOIN";
    public static final String MSG = "MSG";
    public static final String USERS = "USERS";
    public static final String QUIT = "QUIT";
    public static final String WELCOME = "WELCOME";
    public static final String REJECT = "REJECT";
    public static final String SYS = "SYS";
    public static final String ERROR = "ERROR";

    public static final String MODE_FULL = "FULL";
    public static final String MODE_READ_ONLY = "READ_ONLY";

    /** Display names given to clients that join without a username. */
    public static final String GUEST_PREFIX = "Guest-";

    private static final char SEP = '|';
    private static final int MAX_USERNAME = 20;

    private Protocol() {
    }

    public static String encode(String type, String... fields) {
        StringBuilder sb = new StringBuilder(type);
        for (String field : fields) {
            sb.append(SEP).append(escape(field));
        }
        return sb.toString();
    }

    /** Splits a line into [type, field1, field2, ...] and un-escapes each field. */
    public static List<String> decode(String line) {
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (int i = 0; i < line.length(); i++) {
            char c = line.charAt(i);
            if (c == '\\' && i + 1 < line.length()) {
                char next = line.charAt(++i);
                current.append(switch (next) {
                    case 'n' -> '\n';
                    case 'p' -> '|';
                    default -> next;
                });
            } else if (c == SEP) {
                parts.add(current.toString());
                current.setLength(0);
            } else {
                current.append(c);
            }
        }
        parts.add(current.toString());
        return parts;
    }

    /** @return an error message, or null when the username is acceptable. */
    public static String validateUsername(String name) {
        if (name.length() > MAX_USERNAME) {
            return "Username must be at most " + MAX_USERNAME + " characters";
        }
        if (!name.matches("[A-Za-z0-9_.-]+")) {
            return "Username may only contain letters, digits, '.', '_' and '-'";
        }
        if (name.regionMatches(true, 0, GUEST_PREFIX, 0, GUEST_PREFIX.length())) {
            return "Names starting with '" + GUEST_PREFIX + "' are reserved for read-only guests";
        }
        return null;
    }

    private static String escape(String s) {
        return s.replace("\\", "\\\\")
                .replace("|", "\\p")
                .replace("\r\n", "\n")
                .replace("\n", "\\n")
                .replace("\r", "\\n");
    }
}
