package com.lanchat.messaging;

import com.lanchat.model.*;
import java.io.IOException;
import java.util.concurrent.*;
import java.util.function.Supplier;
import org.slf4j.LoggerFactory;

public final class ConnectionManager implements AutoCloseable {
    private final ConcurrentMap<String, PeerConnection> connections = new ConcurrentHashMap<>();
    private final ExecutorService readers = Executors.newVirtualThreadPerTaskExecutor();
    private final ScheduledExecutorService heartbeat = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("tcp-heartbeat").factory());
    private final String localId;
    private final Supplier<String> name;
    private final ProtocolMessageHandler handler;
    private final Object[] locks = new Object[32];
    private volatile boolean closed;
    public ConnectionManager(String id, Supplier<String> name, ProtocolMessageHandler handler) {
        localId = id; this.name = name; this.handler = handler;
        java.util.Arrays.setAll(locks, i -> new Object());
        heartbeat.scheduleWithFixedDelay(() -> connections.values().forEach(c -> {
            try { c.send(ProtocolMessage.event(MessageType.PING, localId, c.peerId(), null, null)); }
            catch (IOException e) { LoggerFactory.getLogger(getClass()).debug("Heartbeat failed for {}", c.peerId(), e); c.close(); }
        }), 20, 20, TimeUnit.SECONDS);
    }
    public void accept(PeerConnection c) { register(c); read(c); }
    private synchronized PeerConnection register(PeerConnection c) {
        if (closed) { c.close(); return c; }
        PeerConnection old = connections.get(c.peerId());
        // The connection initiated by the lexically smaller UUID wins simultaneous connects.
        boolean preferred = c.outbound() == (localId.compareTo(c.peerId()) < 0);
        if (old == null || !old.open() || preferred) {
            connections.put(c.peerId(), c);
            if (old != null && old != c) old.close();
            return c;
        }
        c.close(); return old;
    }
    private void read(PeerConnection c) {
        try { if (c.open()) c.readLoop(handler); }
        catch (IOException | RuntimeException e) { LoggerFactory.getLogger(getClass()).debug("Peer session ended: {}", e.toString()); }
        finally { c.close(); connections.remove(c.peerId(), c); }
    }
    public void send(PeerDevice peer, ProtocolMessage message) throws IOException {
        if (closed) throw new IOException("LAN Chat is shutting down");
        synchronized (locks[(peer.deviceId().hashCode() & Integer.MAX_VALUE) % locks.length]) {
            PeerConnection c = connections.get(peer.deviceId());
            if (c == null || !c.open()) {
                PeerConnection created = new ChatClient().connect(peer, localId, name.get());
                c = register(created);
                if (c == created && c.open()) readers.submit(() -> read(created));
            }
            try { c.send(message); }
            catch (IOException e) { c.close(); connections.remove(peer.deviceId(), c); throw e; }
        }
    }
    @Override public void close() {
        synchronized (this) {
            closed = true; heartbeat.shutdownNow();
            connections.values().forEach(PeerConnection::close); connections.clear(); readers.shutdownNow();
        }
        try { if (!readers.awaitTermination(5, TimeUnit.SECONDS)) LoggerFactory.getLogger(getClass()).warn("Peer readers did not terminate promptly"); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
