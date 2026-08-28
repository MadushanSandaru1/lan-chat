package com.lanchat.discovery;

import com.lanchat.model.*;
import com.lanchat.validation.ProtocolValidator;
import java.time.Clock;
import java.util.*;
import java.util.concurrent.*;
import org.slf4j.LoggerFactory;

public final class DeviceRegistry {
    private final String localId;
    private final Clock clock;
    private final ConcurrentMap<String, PeerDevice> peers = new ConcurrentHashMap<>();
    private final CopyOnWriteArrayList<Runnable> listeners = new CopyOnWriteArrayList<>();
    public DeviceRegistry(String localId) { this(localId, Clock.systemUTC()); }
    public DeviceRegistry(String localId, Clock clock) { this.localId = localId; this.clock = clock; }
    public void onChange(Runnable listener) { listeners.add(listener); }
    public void restore(List<PeerDevice> saved) { saved.forEach(p -> peers.putIfAbsent(p.deviceId(), p.offline())); changed(); }
    public void register(DiscoveryMessage m, String sourceAddress) {
        ProtocolValidator.discovery(m);
        if (localId.equals(m.deviceId())) return;
        if (!peers.containsKey(m.deviceId()) && peers.size() >= 1024) return;
        var peer = new PeerDevice(m.deviceId(), m.displayName(), m.deviceName(), sourceAddress, m.chatPort(), true, clock.millis());
        PeerDevice old = peers.put(m.deviceId(), peer);
        if (old == null || !old.online()) LoggerFactory.getLogger(getClass()).info("Peer discovered: {}", m.deviceId());
        changed();
    }
    public void expire(long timeoutMillis) {
        boolean[] updated = { false };
        peers.replaceAll((id, p) -> {
            if (p.online() && clock.millis() - p.lastSeen() >= timeoutMillis) {
                updated[0] = true;
                LoggerFactory.getLogger(getClass()).info("Peer offline: {}", id);
                return p.offline();
            }
            return p;
        });
        if (updated[0]) changed();
    }
    public List<PeerDevice> snapshot() { return peers.values().stream().sorted(Comparator.comparing(PeerDevice::online).reversed().thenComparing(PeerDevice::displayName)).toList(); }
    public PeerDevice get(String id) { return peers.get(id); }
    private void changed() { listeners.forEach(Runnable::run); }
}
