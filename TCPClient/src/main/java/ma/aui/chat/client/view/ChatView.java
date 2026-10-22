package ma.aui.chat.client.view;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextArea;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import ma.aui.chat.client.model.ChatMessage;

import java.util.List;
import java.util.function.Consumer;

/**
 * Main chat screen (GridPane layout, styled by client.css). It only displays data and
 * forwards what the user does to the two callbacks it receives; it never touches sockets.
 */
public class ChatView {

    private enum Kind { MESSAGE, SYSTEM, ERROR }

    private record Entry(Kind kind, ChatMessage message, String text) {
    }

    private final GridPane root = new GridPane();
    private final ObservableList<Entry> entries = FXCollections.observableArrayList();
    private final ListView<Entry> messagesView = new ListView<>(entries);
    private final TextArea input = new TextArea();
    private final Button sendButton = new Button("SEND");
    private final Button leaveButton = new Button("LEAVE");
    private final Circle statusDot = new Circle(6);
    private final Label statusLabel = new Label();

    public ChatView(String username, boolean readOnly, Consumer<String> onSend, Runnable onLeave) {
        root.getStyleClass().add("chat-root");
        root.setHgap(10);
        root.setVgap(10);

        ColumnConstraints main = new ColumnConstraints();
        main.setHgrow(Priority.ALWAYS);
        ColumnConstraints side = new ColumnConstraints();
        side.setMinWidth(100);
        root.getColumnConstraints().addAll(main, side);
        RowConstraints header = new RowConstraints();
        RowConstraints messages = new RowConstraints();
        messages.setVgrow(Priority.ALWAYS);
        root.getRowConstraints().addAll(header, messages, new RowConstraints(), new RowConstraints());

        // Row 0: who am I + Online indicator
        Label title = new Label("Group Chat");
        title.getStyleClass().add("chat-title");
        Label who = new Label(readOnly ? "Connected as " + username : "Signed in as " + username);
        who.getStyleClass().add("chat-subtitle");
        VBox identity = new VBox(2, title, who);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox headerBox = new HBox(10, identity, spacer);
        if (readOnly) {
            Label badge = new Label("READ-ONLY MODE");
            badge.getStyleClass().add("readonly-badge");
            headerBox.getChildren().add(badge);
        }
        statusDot.getStyleClass().add("status-dot");
        statusLabel.getStyleClass().add("status-label");
        headerBox.getChildren().addAll(statusDot, statusLabel);
        headerBox.setAlignment(Pos.CENTER_LEFT);
        headerBox.getStyleClass().add("chat-header");
        root.add(headerBox, 0, 0, 2, 1);

        // Row 1: messages
        messagesView.getStyleClass().add("messages");
        messagesView.setCellFactory(list -> new EntryCell());
        messagesView.setFocusTraversable(false);
        root.add(messagesView, 0, 1, 2, 1);

        // Row 2: input + SEND (Enter sends, Shift+Enter inserts a new line)
        input.setPrefRowCount(2);
        input.setWrapText(true);
        input.setPromptText(readOnly
                ? "READ-ONLY MODE: only the commands allUsers, end, bye are available"
                : "Type a message and press Enter...");
        input.getStyleClass().add("message-input");
        input.addEventFilter(KeyEvent.KEY_PRESSED, e -> {
            if (e.getCode() == KeyCode.ENTER) {
                e.consume();
                if (e.isShiftDown()) {
                    input.insertText(input.getCaretPosition(), "\n");
                } else {
                    sendButton.fire();
                }
            }
        });
        sendButton.getStyleClass().add("primary-button");
        sendButton.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        sendButton.setOnAction(e -> {
            String text = input.getText();
            input.clear();
            onSend.accept(text);
        });
        root.add(input, 0, 2);
        root.add(sendButton, 1, 2);

        // Row 3: help + LEAVE
        Label help = new Label("Commands:  allUsers  (list active users)   •   end / bye  (disconnect)");
        help.getStyleClass().add("muted");
        leaveButton.getStyleClass().add("danger-button");
        leaveButton.setMaxWidth(Double.MAX_VALUE);
        leaveButton.setOnAction(e -> onLeave.run());
        root.add(help, 0, 3);
        root.add(leaveButton, 1, 3);

        showOnline();
    }

    public GridPane getRoot() {
        return root;
    }

    public void focusInput() {
        input.requestFocus();
    }

    public void showOnline() {
        statusDot.setFill(Color.web("#22c55e"));
        statusLabel.setText("Online");
    }

    /** Connection lost: grey out the input and turn LEAVE into a way back to the login screen. */
    public void showOffline(String reason) {
        statusDot.setFill(Color.web("#ef4444"));
        statusLabel.setText("Offline");
        input.setDisable(true);
        sendButton.setDisable(true);
        leaveButton.setText("BACK");
        addError(reason);
    }

    public void addMessage(ChatMessage message) {
        append(new Entry(Kind.MESSAGE, message, null));
    }

    public void addSystem(String text) {
        append(new Entry(Kind.SYSTEM, null, text));
    }

    public void addError(String text) {
        append(new Entry(Kind.ERROR, null, text));
    }

    public void addUserList(List<String> users) {
        addSystem("Active users (" + users.size() + "):  " + String.join(",  ", users));
    }

    private void append(Entry entry) {
        entries.add(entry);
        messagesView.scrollTo(entries.size() - 1);
    }

    /** Renders chat bubbles (mine on the right, others on the left) and centred notices. */
    private class EntryCell extends ListCell<Entry> {

        EntryCell() {
            setPrefWidth(0); // let the cell follow the list width instead of scrolling horizontally
        }

        @Override
        protected void updateItem(Entry entry, boolean empty) {
            super.updateItem(entry, empty);
            setText(null);
            if (empty || entry == null) {
                setGraphic(null);
                return;
            }
            if (entry.kind() != Kind.MESSAGE) {
                Label notice = new Label(entry.text());
                notice.setWrapText(true);
                notice.getStyleClass().add(entry.kind() == Kind.ERROR ? "notice-error" : "notice");
                HBox row = new HBox(notice);
                row.setAlignment(Pos.CENTER);
                setGraphic(row);
                return;
            }
            ChatMessage m = entry.message();
            Label meta = new Label((m.mine() ? "You" : m.sender()) + "  •  " + m.time());
            meta.getStyleClass().add("bubble-meta");
            Label body = new Label(m.text());
            body.setWrapText(true);
            body.getStyleClass().add("bubble-text");
            VBox bubble = new VBox(3, meta, body);
            bubble.getStyleClass().addAll("bubble", m.mine() ? "bubble-mine" : "bubble-other");
            bubble.maxWidthProperty().bind(messagesView.widthProperty().multiply(0.68));
            HBox row = new HBox(bubble);
            row.setAlignment(m.mine() ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            setGraphic(row);
        }
    }
}
