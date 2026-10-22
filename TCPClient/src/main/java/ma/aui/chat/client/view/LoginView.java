package ma.aui.chat.client.view;

import javafx.geometry.HPos;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.StackPane;

import java.util.function.Consumer;

/** First screen: asks for a username. Leaving it empty joins in read-only mode. */
public class LoginView {

    private final StackPane root = new StackPane();
    private final TextField usernameField = new TextField();
    private final Button joinButton = new Button("JOIN CHAT");
    private final Label messageLabel = new Label();

    public LoginView(String serverAddress, Consumer<String> onJoin) {
        GridPane card = new GridPane();
        card.getStyleClass().add("login-card");
        card.setVgap(10);
        card.setMaxSize(GridPane.USE_PREF_SIZE, GridPane.USE_PREF_SIZE);

        Label title = new Label("Group Chat");
        title.getStyleClass().add("login-title");
        Label server = new Label("Server  " + serverAddress);
        server.getStyleClass().add("muted");

        Label usernameLabel = new Label("Username");
        usernameLabel.getStyleClass().add("field-label");
        usernameField.setPromptText("e.g. alice");
        usernameField.setPrefWidth(300);

        joinButton.getStyleClass().add("primary-button");
        joinButton.setMaxWidth(Double.MAX_VALUE);
        joinButton.setDefaultButton(true);
        joinButton.setOnAction(e -> {
            messageLabel.setText("");
            onJoin.accept(usernameField.getText().trim());
        });

        Label hint = new Label("Leave the username empty to join in READ-ONLY MODE.");
        hint.getStyleClass().add("muted");
        messageLabel.getStyleClass().add("error-text");
        messageLabel.setWrapText(true);
        messageLabel.setMaxWidth(300);

        card.add(title, 0, 0);
        card.add(server, 0, 1);
        card.add(usernameLabel, 0, 2);
        card.add(usernameField, 0, 3);
        card.add(joinButton, 0, 4);
        card.add(hint, 0, 5);
        card.add(messageLabel, 0, 6);
        GridPane.setHalignment(title, HPos.CENTER);
        GridPane.setHalignment(server, HPos.CENTER);

        root.getStyleClass().add("login-root");
        root.getChildren().add(card);
        StackPane.setAlignment(card, Pos.CENTER);
    }

    public StackPane getRoot() {
        return root;
    }

    public void setBusy(boolean busy) {
        joinButton.setDisable(busy);
        usernameField.setDisable(busy);
        joinButton.setText(busy ? "CONNECTING..." : "JOIN CHAT");
    }

    public void showError(String text) {
        messageLabel.getStyleClass().setAll("error-text");
        messageLabel.setText(text);
    }

    public void showInfo(String text) {
        messageLabel.getStyleClass().setAll("info-text");
        messageLabel.setText(text);
    }

    public void focus() {
        usernameField.requestFocus();
    }
}
