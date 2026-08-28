package com.lanchat.service;

import com.lanchat.controller.MainController;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.*;
import javafx.stage.Stage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "lanchat.guiTest", matches = "true")
class JavaFxSmokeTest {
    @TempDir Path temp;
    @Test void loadsAllFxmlAndRendersMainWindow() throws Exception {
        var ready = new CompletableFuture<Void>(); Platform.startup(() -> ready.complete(null)); ready.get(10, TimeUnit.SECONDS);
        try (var app = new ApplicationLifecycleService(temp, e -> {})) {
            app.profile.update("Alex Morgan", "Alex’s MacBook", "", true, false, true);
            String peerId = java.util.UUID.randomUUID().toString();
            app.registry.register(new com.lanchat.model.DiscoveryMessage("DISCOVERY", "LAN_CHAT", "1.0", peerId, "Jamie Chen", "Jamie’s laptop", 45679, System.currentTimeMillis()), "192.168.1.24");
            app.repository.save(new com.lanchat.model.ChatMessage(java.util.UUID.randomUUID().toString(), peerId, app.profile.id(), "Hey Alex! Are you connected to the studio network?", System.currentTimeMillis() - 120000, com.lanchat.model.MessageStatus.READ));
            app.repository.save(new com.lanchat.model.ChatMessage(java.util.UUID.randomUUID().toString(), app.profile.id(), peerId, "Yes, I’m here. Nice to have a little less cloud in our day.", System.currentTimeMillis() - 60000, com.lanchat.model.MessageStatus.READ));
            var done = new CompletableFuture<Void>();
            Platform.runLater(() -> {
                MainController controller = null;
                try {
                    var loader = new FXMLLoader(getClass().getResource("/fxml/main-view.fxml"));
                    Parent root = loader.load(); controller = loader.getController();
                    var scene = new Scene(root, 1000, 650); scene.getStylesheets().add(getClass().getResource("/css/app.css").toExternalForm());
                    var stage = new Stage(); stage.setScene(scene); controller.attach(app, stage); stage.show(); root.applyCss(); root.layout();
                    assertNotNull(scene.lookup("#search")); assertNotNull(scene.lookup("#input"));
                    var settings = new FXMLLoader(getClass().getResource("/fxml/settings-view.fxml")); assertNotNull(settings.load());
                    MainController attached = controller;
                    app.execute(() -> Platform.runLater(() -> {
                        try {
                            var list = (javafx.scene.control.ListView<?>)scene.lookup("#peers"); list.getSelectionModel().selectFirst();
                            app.execute(() -> Platform.runLater(() -> {
                                try {
                                    root.applyCss(); root.layout();
                                    var image = scene.snapshot(null); var pixels = image.getPixelReader();
                                    var buffered = new java.awt.image.BufferedImage((int)image.getWidth(), (int)image.getHeight(), java.awt.image.BufferedImage.TYPE_INT_ARGB);
                                    for (int y = 0; y < buffered.getHeight(); y++) for (int x = 0; x < buffered.getWidth(); x++) buffered.setRGB(x, y, pixels.getArgb(x, y));
                                    javax.imageio.ImageIO.write(buffered, "png", Path.of("target", "ui-smoke.png").toFile());
                                    assertEquals("Jamie Chen", ((javafx.scene.control.Label)scene.lookup("#title")).getText());
                                    stage.close(); attached.close(); done.complete(null);
                                } catch (Throwable e) { stage.close(); attached.close(); done.completeExceptionally(e); }
                            }), done::completeExceptionally);
                        } catch (Throwable e) { done.completeExceptionally(e); }
                    }), done::completeExceptionally);
                } catch (Throwable e) { done.completeExceptionally(e); }
            });
            done.get(20, TimeUnit.SECONDS);
        } finally { Platform.exit(); }
    }
}
