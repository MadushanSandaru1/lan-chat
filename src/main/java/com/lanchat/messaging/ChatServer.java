package com.lanchat.messaging;

import java.io.IOException;
import java.net.*;
import java.util.Set;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.slf4j.LoggerFactory;

public final class ChatServer implements AutoCloseable {
    private final ServerSocket server;
    private final ExecutorService workers = Executors.newVirtualThreadPerTaskExecutor();
    private final Set<Socket> sockets = ConcurrentHashMap.newKeySet();
    private final Semaphore slots = new Semaphore(64);
    public ChatServer(int port, String id, Supplier<String> name, Consumer<PeerConnection> accepted) throws IOException {
        server = new ServerSocket();
        try {
            try { server.bind(new InetSocketAddress(port)); }
            catch (BindException e) { server.bind(new InetSocketAddress(0)); }
        } catch (IOException e) { server.close(); workers.shutdownNow(); throw e; }
        workers.submit(() -> {
            while (!server.isClosed()) {
                try {
                    var socket = server.accept();
                    if (!slots.tryAcquire()) { socket.close(); continue; }
                    sockets.add(socket);
                    workers.submit(() -> {
                        try {
                            var connection = new PeerConnection(socket, id, name.get(), null, false);
                            accepted.accept(connection);
                        } catch (IOException | RuntimeException e) {
                            LoggerFactory.getLogger(getClass()).debug("Rejected/closed peer session: {}", e.toString());
                        } finally {
                            sockets.remove(socket); slots.release();
                            try { socket.close(); } catch (IOException e) { LoggerFactory.getLogger(getClass()).debug("Socket close failed", e); }
                        }
                    });
                } catch (IOException | RejectedExecutionException e) {
                    if (!server.isClosed()) LoggerFactory.getLogger(getClass()).warn("Accept failed: {}", e.toString());
                }
            }
        });
        LoggerFactory.getLogger(getClass()).info("TCP server listening on {}", port());
    }
    public int port() { return server.getLocalPort(); }
    @Override public void close() {
        try { server.close(); } catch (IOException e) { LoggerFactory.getLogger(getClass()).warn("Server close failed", e); }
        sockets.forEach(s -> { try { s.close(); } catch (IOException e) { LoggerFactory.getLogger(getClass()).debug("Socket close failed", e); } });
        workers.shutdownNow();
        try { if (!workers.awaitTermination(5, TimeUnit.SECONDS)) LoggerFactory.getLogger(getClass()).warn("TCP handlers did not terminate promptly"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
