package com.lanchat.discovery;

import com.lanchat.config.NetworkConfig;
import com.lanchat.model.DiscoveryMessage;
import com.lanchat.service.UserProfileService;
import com.lanchat.util.JsonUtil;
import java.io.IOException;
import java.net.*;

public final class MulticastBroadcaster {
    private final UserProfileService profile;
    private final int chatPort;
    public MulticastBroadcaster(UserProfileService profile, int chatPort) { this.profile = profile; this.chatPort = chatPort; }
    public void broadcast(MulticastSocket socket) throws IOException {
        byte[] data = JsonUtil.encode(new DiscoveryMessage("DISCOVERY", "LAN_CHAT", NetworkConfig.VERSION,
                profile.id(), profile.name(), profile.device(), chatPort, System.currentTimeMillis()));
        socket.send(new DatagramPacket(data, data.length, InetAddress.getByName(NetworkConfig.GROUP), NetworkConfig.DISCOVERY_PORT));
    }
}
