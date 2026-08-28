package com.lanchat.controller;

import com.lanchat.messaging.MessageService;
import com.lanchat.model.*;
import com.lanchat.service.*;
import javafx.application.Platform;
import javafx.fxml.*;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class MainController implements AutoCloseable {
    @FXML private TextField search;
    @FXML private ListView<PeerDevice> peers;
    @FXML private Label peerCount, networkLabel, profileName, notice;
    @FXML private ChatController chatController;
    private ApplicationLifecycleService app;
    private Stage stage;
    private PeerDevice selected;
    private List<PeerDevice> devices = List.of();
    private final Map<String, List<ChatMessage>> histories = new HashMap<>();
    private Map<String, ConversationSummary> summaries = Map.of();
    private final AtomicBoolean refreshing = new AtomicBoolean();
    private final NotificationService notifications = new NotificationService();
    private boolean updating;
    public void attach(ApplicationLifecycleService app, Stage stage) {
        this.app = app; this.stage = stage;
        var placeholder = new Label("No devices found\n\nOpen LAN Chat on another device\nconnected to the same network.");
        placeholder.getStyleClass().add("peer-detail"); placeholder.setWrapText(true); peers.setPlaceholder(placeholder);
        peers.setCellFactory(list -> new ListCell<>() {
            @Override protected void updateItem(PeerDevice peer, boolean empty) {
                super.updateItem(peer, empty); setText(null); setGraphic(null);
                if (empty || peer == null) return;
                var avatar = new Label(peer.displayName().substring(0, 1).toUpperCase()); avatar.getStyleClass().add("avatar");
                var name = new Label(peer.displayName()); name.getStyleClass().add("peer-name");
                var detail = new Label((peer.online() ? "● " : "○ ") + peer.deviceName()); detail.getStyleClass().add("peer-detail");
                detail.getStyleClass().add(peer.online() ? "online" : "offline");
                var summary = summaries.getOrDefault(peer.deviceId(), new ConversationSummary("Start a conversation", 0));
                String preview = summary.preview().replace('\n', ' ');
                var last = new Label(preview); last.setMaxWidth(150); last.getStyleClass().add("peer-preview");
                var info = new VBox(4, name, detail, last); HBox.setHgrow(info, Priority.ALWAYS);
                var row = new HBox(10, avatar, info); row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
                long count = summary.unread();
                if (count > 0) { var badge = new Label("" + count); badge.getStyleClass().add("unread"); row.getChildren().add(badge); }
                setGraphic(row);
            }
        });
        peers.getSelectionModel().selectedItemProperty().addListener((obs, old, peer) -> {
            if (updating || peer == null) return;
            if (selected != null && !selected.deviceId().equals(peer.deviceId())) type(false);
            selected = peer; showConversation(); refresh(); markRead();
        });
        search.textProperty().addListener((o, a, b) -> filter());
        chatController.callbacks(text -> { var peer = selected; if (peer != null) app.execute(() -> app.messages.send(peer, text), this::error); }, this::type,
                message -> { var peer = selected; if (peer != null) app.execute(() -> app.messages.retry(peer, message), this::error); });
        stage.focusedProperty().addListener((o, a, focused) -> { if (focused) { stage.setTitle("LAN Chat"); markRead(); } });
        app.registry.onChange(() -> Platform.runLater(this::refresh)); refresh();
    }
    private void type(boolean typing) {
        var peer = selected;
        if (peer != null && peer.online()) app.execute(() -> app.messages.typing(peer, typing), e -> org.slf4j.LoggerFactory.getLogger(getClass()).debug("Typing event not delivered", e));
    }
    public void refresh() {
        if (app == null || !refreshing.compareAndSet(false, true)) return;
        String requestedPeer = selected == null ? null : selected.deviceId();
        app.execute(() -> {
            var snapshot = app.registry.snapshot(); var loaded = new HashMap<String, List<ChatMessage>>();
            if (requestedPeer != null) loaded.put(requestedPeer, app.repository.history(app.profile.id(), requestedPeer));
            var summary = app.repository.summaries(app.profile.id());
            Platform.runLater(() -> {
                refreshing.set(false); devices = snapshot; histories.clear(); histories.putAll(loaded); summaries = summary;
                profileName.setText(app.profile.name()); peerCount.setText(snapshot.stream().filter(PeerDevice::online).count() + " online");
                if (selected != null) selected = snapshot.stream().filter(p -> p.deviceId().equals(selected.deviceId())).findFirst().orElse(selected);
                filter(); showConversation();
                if (selected != null && !Objects.equals(requestedPeer, selected.deviceId())) refresh();
            });
        }, e -> { refreshing.set(false); error(e); });
    }
    private void filter() {
        String query = search.getText().strip().toLowerCase(Locale.ROOT);
        updating = true;
        peers.getItems().setAll(devices.stream().filter(p -> (p.displayName() + " " + p.deviceName()).toLowerCase(Locale.ROOT).contains(query)).toList());
        if (selected != null) peers.getSelectionModel().select(selected);
        updating = false;
    }
    private void showConversation() {
        if (selected == null) return;
        chatController.select(selected, app.profile.preference("showIp"));
        chatController.render(histories.getOrDefault(selected.deviceId(), List.of()), app.profile.id());
    }
    private void markRead() {
        var peer = selected;
        if (peer == null || !stage.isFocused() || !peer.online()) return;
        app.execute(() -> { app.messages.markRead(peer); Platform.runLater(this::refresh); }, e -> org.slf4j.LoggerFactory.getLogger(getClass()).debug("Read receipt deferred until conversation is reopened", e));
    }
    public void event(MessageService.Event event) {
        if (event.type() == MessageType.TYPING_START || event.type() == MessageType.TYPING_STOP) {
            if (selected != null && selected.deviceId().equals(event.peerId())) chatController.remoteTyping(selected.displayName(), event.type() == MessageType.TYPING_START);
            return;
        }
        refresh();
        if (event.incoming() != null) {
            if (selected != null && selected.deviceId().equals(event.peerId()) && stage.isFocused()) markRead();
            else {
                var peer = app.registry.get(event.peerId()); String name = peer == null ? "LAN Chat" : peer.displayName();
                stage.setTitle("New message · LAN Chat");
                if (!stage.isFocused()) notifications.notify(name, event.incoming().content(), app.profile.preference("notifications"), app.profile.preference("sounds"));
            }
        }
    }
    public void state(ApplicationState state, String detail) { networkLabel.setText(detail); }
    public void error(Exception e) { Platform.runLater(() -> { notice.setText("Could not complete the action: " + e.getMessage()); notice.setManaged(true); notice.setVisible(true); }); }
    @FXML private void settings() {
        try {
            var loader = new FXMLLoader(getClass().getResource("/fxml/settings-view.fxml"));
            var pane = loader.<javafx.scene.Parent>load();
            var dialog = new Dialog<ButtonType>(); dialog.initOwner(stage); dialog.setTitle("LAN Chat · Settings");
            dialog.getDialogPane().setContent(pane); dialog.getDialogPane().getButtonTypes().addAll(SettingsController.SAVE, ButtonType.CANCEL);
            dialog.getDialogPane().getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
            loader.<SettingsController>getController().attach(app, dialog, this::refresh, this::error); dialog.showAndWait();
        } catch (Exception e) { error(e); }
    }
    @Override public void close() { chatController.close(); notifications.close(); }
}
