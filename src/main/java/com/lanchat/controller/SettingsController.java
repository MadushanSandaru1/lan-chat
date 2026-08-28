package com.lanchat.controller;

import com.lanchat.config.NetworkConfig;
import com.lanchat.service.ApplicationLifecycleService;
import com.lanchat.util.NetworkUtil;
import com.lanchat.validation.ProtocolValidator;
import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.scene.control.*;
import java.util.function.Consumer;

public final class SettingsController {
    public static final ButtonType SAVE = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
    @FXML private TextField name, device;
    @FXML private Label identity, networkDetails, validation;
    @FXML private ComboBox<String> network;
    @FXML private CheckBox notifications, sounds, showIp;
    private ApplicationLifecycleService app;
    private Runnable refresh;
    private Consumer<Exception> error;
    public void attach(ApplicationLifecycleService app, Dialog<ButtonType> dialog, Runnable refresh, Consumer<Exception> error) {
        this.app = app; this.refresh = refresh; this.error = error;
        name.setText(app.profile.name()); device.setText(app.profile.device()); identity.setText("Device ID: " + app.profile.id());
        notifications.setSelected(app.profile.preference("notifications")); sounds.setSelected(app.profile.preference("sounds")); showIp.setSelected(app.profile.preference("showIp"));
        network.getItems().add("Automatic"); network.setValue(app.profile.networkInterface().isEmpty() ? "Automatic" : app.profile.networkInterface());
        app.execute(() -> {
            var interfaces = NetworkUtil.interfaces();
            String details = "UDP " + NetworkConfig.DISCOVERY_PORT + " · TCP " + app.port() + "\n" + interfaces.stream().map(n -> n.getName() + ": " + NetworkUtil.ipv4(n)).collect(java.util.stream.Collectors.joining("\n"));
            Platform.runLater(() -> { interfaces.forEach(n -> network.getItems().add(n.getName())); networkDetails.setText(details); });
        }, error);
        dialog.getDialogPane().lookupButton(SAVE).addEventFilter(javafx.event.ActionEvent.ACTION, event -> {
            event.consume();
            try { ProtocolValidator.name(name.getText()); ProtocolValidator.name(device.getText()); }
            catch (IllegalArgumentException e) { validation.setText(e.getMessage()); return; }
            String newName = name.getText(), newDevice = device.getText(), iface = "Automatic".equals(network.getValue()) ? "" : network.getValue();
            boolean desktop = notifications.isSelected(), sound = sounds.isSelected(), ip = showIp.isSelected();
            dialog.getDialogPane().lookupButton(SAVE).setDisable(true);
            app.execute(() -> {
                app.profile.update(newName, newDevice, iface, desktop, sound, ip);
                Platform.runLater(() -> { dialog.setResult(SAVE); dialog.close(); refresh.run(); });
            }, e -> Platform.runLater(() -> { validation.setText(e.getMessage()); dialog.getDialogPane().lookupButton(SAVE).setDisable(false); }));
        });
    }
    @FXML private void clearHistory() {
        var confirmation = new Alert(Alert.AlertType.CONFIRMATION, "Delete all local conversations? This cannot be undone.", ButtonType.CANCEL, ButtonType.OK);
        confirmation.initOwner(name.getScene().getWindow()); confirmation.setHeaderText("Clear chat history?");
        if (confirmation.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK) app.execute(() -> { app.repository.clear(); Platform.runLater(refresh); }, error);
    }
}
