package com.lanchat.service;

import com.lanchat.config.*;
import com.lanchat.discovery.*;
import com.lanchat.messaging.*;
import com.lanchat.model.*;
import com.lanchat.repository.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.*;
import java.util.function.*;
import org.slf4j.LoggerFactory;

public final class ApplicationLifecycleService implements AutoCloseable {
    public final UserProfileService profile;
    public final ChatRepository repository;
    public final DeviceRegistry registry;
    public final MessageService messages;
    public final ConnectionManager connections;
    private ChatServer server;
    private DeviceDiscoveryService discovery;
    private final ExecutorService work = Executors.newSingleThreadExecutor(Thread.ofVirtual().name("app-work").factory());
    private volatile boolean closed;
    public ApplicationLifecycleService(Path directory, Consumer<MessageService.Event> events) throws IOException {
        profile = new UserProfileService(directory);
        try { repository = new SQLiteChatRepository(directory.resolve("chat.db")); }
        catch (RuntimeException e) { profile.close(); throw e; }
        registry = new DeviceRegistry(profile.id()); registry.restore(repository.peers());
        messages = new MessageService(profile.id(), repository, registry, events);
        connections = new ConnectionManager(profile.id(), profile::name, messages); messages.connections(connections);
        registry.onChange(() -> execute(() -> registry.snapshot().forEach(repository::savePeer), e -> LoggerFactory.getLogger(getClass()).error("Cannot persist peer list", e)));
    }
    public void start(BiConsumer<ApplicationState, String> state) throws IOException {
        server = new ChatServer(NetworkConfig.CHAT_PORT, profile.id(), profile::name, connections::accept);
        discovery = new DeviceDiscoveryService(profile, registry, server.port(), state); discovery.start();
        LoggerFactory.getLogger(getClass()).info("LAN Chat started");
    }
    public int port() { return server == null ? 0 : server.port(); }
    @FunctionalInterface public interface Work { void run() throws Exception; }
    public void execute(Work action, Consumer<Exception> failed) {
        if (closed) return;
        try { work.submit(() -> { try { action.run(); } catch (Exception e) { LoggerFactory.getLogger(getClass()).warn("Background operation failed: {}", e.toString()); failed.accept(e); } }); }
        catch (RejectedExecutionException e) { if (!closed) failed.accept(e); }
    }
    @Override public void close() {
        if (closed) return; closed = true;
        if (discovery != null) discovery.close();
        connections.close(); if (server != null) server.close();
        work.shutdownNow();
        try { if (!work.awaitTermination(5, TimeUnit.SECONDS)) LoggerFactory.getLogger(getClass()).warn("Background work did not terminate promptly"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
        try { repository.close(); } finally {
            try { profile.close(); } catch (IOException e) { LoggerFactory.getLogger(getClass()).warn("Profile close failed", e); }
        }
        LoggerFactory.getLogger(getClass()).info("LAN Chat stopped");
    }
}
