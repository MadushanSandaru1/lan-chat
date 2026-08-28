package com.lanchat.discovery;

import com.lanchat.config.NetworkConfig;
import com.lanchat.model.ApplicationState;
import com.lanchat.service.UserProfileService;
import com.lanchat.util.NetworkUtil;
import java.net.*;
import java.util.concurrent.*;
import java.util.function.BiConsumer;
import org.slf4j.LoggerFactory;

/** Polls interface/address changes on the heartbeat schedule, then re-joins as needed. */
public final class DeviceDiscoveryService implements AutoCloseable {
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("discovery").factory());
    private final UserProfileService profile;
    private final DeviceRegistry registry;
    private final MulticastBroadcaster broadcaster;
    private final BiConsumer<ApplicationState, String> state;
    private MulticastSocket socket;
    private NetworkInterface selected;
    private String signature = "";
    private boolean closed;
    public DeviceDiscoveryService(UserProfileService profile, DeviceRegistry registry, int port, BiConsumer<ApplicationState, String> state) {
        this.profile = profile; this.registry = registry; this.broadcaster = new MulticastBroadcaster(profile, port); this.state = state;
    }
    public void start() { scheduler.scheduleWithFixedDelay(this::tick, 0, NetworkConfig.INTERVAL, TimeUnit.MILLISECONDS); }
    private synchronized void tick() {
        if (closed) return;
        try {
            var available = NetworkUtil.interfaces();
            String preference = profile.networkInterface();
            var next = available.stream().filter(n -> preference.isEmpty() || preference.equals(n.getName())).findFirst().orElse(null);
            if (next == null) { leave(); state.accept(ApplicationState.NETWORK_UNAVAILABLE, "Connect to Wi-Fi or Ethernet. Check your selected interface in Settings."); }
            else {
                String nextSignature = next.getName() + ":" + NetworkUtil.ipv4(next);
                if (socket == null || socket.isClosed() || !nextSignature.equals(signature)) {
                    leave();
                    socket = new MulticastSocket(null);
                    socket.setReuseAddress(true);
                    socket.bind(new InetSocketAddress(NetworkConfig.DISCOVERY_PORT));
                    socket.setNetworkInterface(next); socket.setTimeToLive(1);
                    socket.joinGroup(new InetSocketAddress(NetworkConfig.GROUP, NetworkConfig.DISCOVERY_PORT), next);
                    selected = next; signature = nextSignature;
                    Thread.ofVirtual().name("multicast-listener").start(new MulticastListener(socket, registry));
                    LoggerFactory.getLogger(getClass()).info("Discovery joined on {}", signature);
                }
                broadcaster.broadcast(socket);
                state.accept(ApplicationState.ONLINE, "Connected · " + next.getDisplayName());
            }
        } catch (Exception e) {
            leave();
            LoggerFactory.getLogger(getClass()).warn("Discovery unavailable: {}", e.toString());
            state.accept(ApplicationState.NETWORK_UNAVAILABLE, "Discovery unavailable. Check firewall, VPN, and network permissions.");
        } finally { registry.expire(NetworkConfig.PEER_TIMEOUT); }
    }
    private void leave() {
        if (socket != null) {
            try { if (!socket.isClosed() && selected != null) socket.leaveGroup(new InetSocketAddress(NetworkConfig.GROUP, NetworkConfig.DISCOVERY_PORT), selected); }
            catch (Exception e) { LoggerFactory.getLogger(getClass()).debug("Could not leave multicast group", e); }
            socket.close(); socket = null;
        }
        selected = null; signature = "";
    }
    @Override public synchronized void close() { closed = true; scheduler.shutdownNow(); leave(); }
}
