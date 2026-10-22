package ma.aui.chat.client.model;

import java.util.List;

/**
 * Observer through which {@link ChatClient} reports network events, without depending
 * on any UI toolkit. Callbacks run on the client's network thread.
 */
public interface ChatClientListener {

    /** The server accepted us; readOnly is true when we joined without a username. */
    default void onConnected(String username, boolean readOnly) {
    }

    /** The server refused the username (e.g. already taken). */
    default void onRejected(String reason) {
    }

    /** The server could not be reached. */
    default void onConnectionFailed(String reason) {
    }

    default void onChatMessage(ChatMessage message) {
    }

    /** Join/leave notices and other server announcements. */
    default void onSystemMessage(String text) {
    }

    /** Answer to the "allUsers" command. */
    default void onUserList(List<String> users) {
    }

    default void onError(String text) {
    }

    /** The connection is closed; byUser is true after "end", "bye" or LEAVE. */
    default void onDisconnected(String reason, boolean byUser) {
    }
}
