package ma.aui.chat.server.view;

import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.HPos;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.RowConstraints;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import ma.aui.chat.server.model.ServerListener;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

/**
 * Server window (GridPane layout, styled by server.css): activity log on the left,
 * live list of connected users on the right.
 * <p>
 * Implements {@link ServerListener}; every callback arrives on a network thread and is
 * moved onto the JavaFX Application Thread with {@link Platform#runLater}.
 */
public class ServerView implements ServerListener {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    private final GridPane root = new GridPane();
    private final ObservableList<String> logEntries = FXCollections.observableArrayList();
    private final ObservableList<String> users = FXCollections.observableArrayList();
    private final Map<String, String> userColors = new HashMap<>();
    private final Random random = new Random();

    private final ListView<String> logView = new ListView<>(logEntries);
    private final Circle statusDot = new Circle(6);
    private final Label statusLabel = new Label("Starting...");
    private final Label usersTitle = new Label();

    public ServerView() {
        build();
        updateUserCount();
    }

    public GridPane getRoot() {
        return root;
    }

    private void build() {
        root.getStyleClass().add("root-pane");
        root.setHgap(16);
        root.setVgap(10);

        ColumnConstraints left = new ColumnConstraints();
        left.setPercentWidth(64);
        ColumnConstraints right = new ColumnConstraints();
        right.setPercentWidth(36);
        root.getColumnConstraints().addAll(left, right);

        RowConstraints header = new RowConstraints();
        RowConstraints titles = new RowConstraints();
        RowConstraints lists = new RowConstraints();
        lists.setVgrow(Priority.ALWAYS);
        RowConstraints bottom = new RowConstraints();
        root.getRowConstraints().addAll(header, titles, lists, bottom);

        // Row 0: title + status indicator
        Label title = new Label("TCP Chat Server");
        title.getStyleClass().add("title");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        statusDot.getStyleClass().add("status-dot");
        statusLabel.getStyleClass().add("status-label");
        HBox headerBox = new HBox(10, title, spacer, statusDot, statusLabel);
        headerBox.getStyleClass().add("header");
        root.add(headerBox, 0, 0, 2, 1);

        // Row 1: section titles
        Label logTitle = new Label("Activity log");
        logTitle.getStyleClass().add("section-title");
        usersTitle.getStyleClass().add("section-title");
        root.add(logTitle, 0, 1);
        root.add(usersTitle, 1, 1);

        // Row 2: log + users
        logView.getStyleClass().add("log-list");
        ListView<String> usersView = new ListView<>(users);
        usersView.getStyleClass().add("users-list");
        usersView.setCellFactory(list -> new UserCell());
        usersView.setPlaceholder(new Label("No clients connected"));
        root.add(logView, 0, 2);
        root.add(usersView, 1, 2);

        // Row 3: footer
        Label footer = new Label("Each client is served by its own virtual thread  •  Close the window to stop the server");
        footer.getStyleClass().add("footer");
        GridPane.setHalignment(footer, HPos.LEFT);
        root.add(footer, 0, 3, 2, 1);
        showOffline();
    }

    public void showOnline(String host, int port) {
        statusDot.setFill(Color.web("#22c55e"));
        statusLabel.setText("Online  •  " + host + ":" + port);
    }

    public void showOffline() {
        statusDot.setFill(Color.web("#ef4444"));
        statusLabel.setText("Offline");
    }

    // ---- ServerListener (called from network threads) ---------------------------

    @Override
    public void onLog(String message) {
        String entry = "[" + LocalTime.now().format(TIME) + "]  " + message;
        Platform.runLater(() -> {
            logEntries.add(entry);
            logView.scrollTo(logEntries.size() - 1);
        });
    }

    @Override
    public void onClientConnected(String username, boolean readOnly) {
        String label = readOnly ? username + " (read-only)" : username;
        Platform.runLater(() -> {
            userColors.put(label, randomPastel());
            users.add(label);
            updateUserCount();
        });
    }

    @Override
    public void onClientDisconnected(String username) {
        Platform.runLater(() -> {
            users.removeIf(label -> label.equals(username) || label.equals(username + " (read-only)"));
            updateUserCount();
        });
    }

    private void updateUserCount() {
        usersTitle.setText("Connected users (" + users.size() + ")");
    }

    /** Random hue with fixed low saturation / high brightness so dark text stays readable. */
    private String randomPastel() {
        Color c = Color.hsb(random.nextDouble() * 360, 0.35, 0.97);
        return String.format("#%02x%02x%02x",
                (int) (c.getRed() * 255), (int) (c.getGreen() * 255), (int) (c.getBlue() * 255));
    }

    /** Shows each user on the background colour assigned when they connected. */
    private class UserCell extends ListCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) {
                setText(null);
                setStyle("");
            } else {
                setText("●  " + item);
                setStyle("-fx-background-color: " + userColors.getOrDefault(item, "#ffffff") + ";");
            }
        }
    }
}
