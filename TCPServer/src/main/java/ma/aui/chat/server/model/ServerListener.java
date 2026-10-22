package ma.aui.chat.server.model;

/**
 * Observer through which the server model reports what happens, without knowing
 * anything about the user interface. A JavaFX view, a console or a unit test can
 * all implement it.
 * <p>
 * Callbacks are invoked from the server's network threads, not from a UI thread.
 */
public interface ServerListener {

    default void onLog(String message) {
    }

    default void onClientConnected(String username, boolean readOnly) {
    }

    default void onClientDisconnected(String username) {
    }
}
