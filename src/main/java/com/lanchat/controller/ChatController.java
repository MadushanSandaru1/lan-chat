package com.lanchat.controller;

import com.lanchat.config.NetworkConfig;
import com.lanchat.model.*;
import javafx.animation.PauseTransition;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.*;
import javafx.util.Duration;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.Consumer;

public final class ChatController {
    @FXML private Label avatar, title, subtitle, typing;
    @FXML private TextArea input;
    @FXML private Button sendButton;
    @FXML private ScrollPane scroll;
    @FXML private VBox bubbles;
    private Consumer<String> send = s -> {};
    private Consumer<Boolean> onTyping = b -> {};
    private Consumer<ChatMessage> retry = m -> {};
    private final PauseTransition idle = new PauseTransition(Duration.millis(NetworkConfig.TYPING_TIMEOUT));
    private final PauseTransition remoteIdle = new PauseTransition(Duration.seconds(5));
    private boolean typingSent;
    private String peerId;
    @FXML private void initialize() {
        input.setDisable(true); sendButton.setDisable(true); empty();
        input.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ENTER && !e.isShiftDown()) { e.consume(); send(); } });
        input.textProperty().addListener((obs, old, value) -> {
            if (peerId == null || input.isDisabled()) return;
            if (!value.isBlank() && !typingSent) { typingSent = true; onTyping.accept(true); }
            idle.playFromStart();
        });
        idle.setOnFinished(e -> stopTyping()); remoteIdle.setOnFinished(e -> typing.setText(" "));
    }
    public void callbacks(Consumer<String> send, Consumer<Boolean> typing, Consumer<ChatMessage> retry) { this.send = send; this.onTyping = typing; this.retry = retry; }
    private void stopTyping() { if (typingSent) { typingSent = false; onTyping.accept(false); } }
    @FXML private void send() {
        if (input.isDisabled() || input.getText().isBlank()) return;
        String text = input.getText();
        try { com.lanchat.validation.ProtocolValidator.text(text); } catch (IllegalArgumentException e) { typing.setText(e.getMessage()); return; }
        stopTyping(); send.accept(text); input.clear(); input.requestFocus();
    }
    public void select(PeerDevice peer, boolean showIp) {
        if (peerId == null || !peerId.equals(peer.deviceId())) { idle.stop(); typingSent = false; input.clear(); bubbles.getChildren().clear(); remoteIdle.stop(); typing.setText(" "); }
        peerId = peer.deviceId();
        avatar.setText(peer.displayName().substring(0, 1).toUpperCase()); title.setText(peer.displayName());
        subtitle.setText((peer.online() ? "● Online" : "○ Offline") + " · " + peer.deviceName() + (showIp ? " · " + peer.ipAddress() : ""));
        subtitle.getStyleClass().removeAll("online", "offline"); subtitle.getStyleClass().add(peer.online() ? "online" : "offline");
        input.setDisable(!peer.online()); sendButton.setDisable(!peer.online());
    }
    public void remoteTyping(String name, boolean active) { typing.setText(active ? name + " is typing…" : " "); if (active) remoteIdle.playFromStart(); else remoteIdle.stop(); }
    private void empty() {
        var heading = new Label("Good conversations start nearby."); heading.getStyleClass().add("empty-title"); heading.setWrapText(true);
        var copy = new Label("Select a device to start chatting.\n\nLAN Chat finds people on your local network.\nNo accounts. No cloud. Just say hello."); copy.getStyleClass().add("empty-copy"); copy.setWrapText(true);
        var box = new VBox(18, heading, copy); box.setAlignment(Pos.CENTER); box.setMinHeight(320); bubbles.getChildren().setAll(box);
    }
    public void render(List<ChatMessage> history, String localId) {
        double previousScroll = scroll.getVvalue(); boolean atBottom = previousScroll > .95 || bubbles.getChildren().isEmpty();
        bubbles.getChildren().clear();
        if (history.isEmpty()) { var welcome = new Label("You’re on the same network. Say hello!"); welcome.getStyleClass().add("empty-copy"); bubbles.getChildren().add(welcome); }
        for (var message : history) {
            boolean outgoing = message.senderId().equals(localId);
            var text = new Label(message.content()); text.setWrapText(true); text.getStyleClass().add("message-text"); text.setMaxWidth(420);
            String timestamp;
            try { timestamp = DateTimeFormatter.ofPattern("MMM d · HH:mm").withZone(ZoneId.systemDefault()).format(Instant.ofEpochMilli(message.timestamp())); }
            catch (DateTimeException e) { timestamp = "Unknown time"; }
            var meta = new Label(timestamp + (outgoing ? "  ·  " + message.status().name().toLowerCase() : "")); meta.getStyleClass().add("message-meta");
            var bubble = new VBox(6, text, meta); bubble.setMaxWidth(450); bubble.getStyleClass().add("bubble");
            if (outgoing) bubble.getStyleClass().add("outgoing");
            if (outgoing && message.status() == MessageStatus.FAILED) { var button = new Button("Retry message"); button.getStyleClass().add("retry-button"); button.setOnAction(e -> retry.accept(message)); bubble.getChildren().add(button); }
            var row = new HBox(bubble); row.setAlignment(outgoing ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT); bubbles.getChildren().add(row);
        }
        if (atBottom) javafx.application.Platform.runLater(() -> scroll.setVvalue(1)); else scroll.setVvalue(previousScroll);
    }
    public void close() { idle.stop(); remoteIdle.stop(); }
}
