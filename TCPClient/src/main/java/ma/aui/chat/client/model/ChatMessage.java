package ma.aui.chat.client.model;

/**
 * A chat message as formatted by the server: time and sender are stamped server-side.
 *
 * @param mine true when this client sent the message
 */
public record ChatMessage(String time, String sender, String text, boolean mine) {
}
