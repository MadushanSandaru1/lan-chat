package com.lanchat;

import com.lanchat.config.AppConfig;
import com.lanchat.controller.MainController;
import com.lanchat.service.ApplicationLifecycleService;
import javafx.application.*;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.stage.Stage;

public final class LanChatApplication extends Application {
    private ApplicationLifecycleService services;
    private MainController controller;
    private Exception startupFailure;
    @Override public void init() {
        try { services = new ApplicationLifecycleService(AppConfig.dataDirectory(), event -> Platform.runLater(() -> { if (controller != null) controller.event(event); })); }
        catch (Exception e) { startupFailure = e; }
    }
    @Override public void start(Stage stage) throws Exception {
        if (startupFailure != null) {
            new Alert(Alert.AlertType.ERROR, "LAN Chat could not open its local data. Another instance may be using it.\n" + startupFailure.getMessage()).showAndWait(); Platform.exit(); return;
        }
        var loader = new FXMLLoader(getClass().getResource("/fxml/main-view.fxml"));
        var root = loader.<javafx.scene.Parent>load(); controller = loader.getController();
        var scene = new Scene(root, 1000, 650); scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
        stage.setTitle("LAN Chat"); stage.setMinWidth(800); stage.setMinHeight(500); stage.setScene(scene);
        controller.attach(services, stage); stage.show();
        if (services.profile.name().isBlank()) {
            var dialog = new TextInputDialog(); dialog.initOwner(stage); dialog.setTitle("Welcome to LAN Chat");
            dialog.setHeaderText("A little introduction."); dialog.setContentText("Your display name:");
            var editor = dialog.getEditor();
            dialog.getDialogPane().lookupButton(ButtonType.OK).disableProperty().bind(javafx.beans.binding.Bindings.createBooleanBinding(() -> {
                try { com.lanchat.validation.ProtocolValidator.name(editor.getText()); return false; } catch (IllegalArgumentException e) { return true; }
            }, editor.textProperty()));
            var name = dialog.showAndWait();
            if (name.isEmpty()) { stage.close(); return; }
            services.execute(() -> { services.profile.update(name.get(), services.profile.device(), "", true, true, true); startNetwork(); }, controller::error);
        } else services.execute(this::startNetwork, controller::error);
    }
    private void startNetwork() throws Exception {
        services.start((state, detail) -> Platform.runLater(() -> controller.state(state, detail)));
        Platform.runLater(controller::refresh);
    }
    @Override public void stop() {
        if (controller != null) controller.close();
        // Closing sockets/SQLite must not block the JavaFX thread. Non-daemon shutdown thread finishes cleanup.
        if (services != null) Thread.ofPlatform().name("shutdown").start(services::close);
    }
}
