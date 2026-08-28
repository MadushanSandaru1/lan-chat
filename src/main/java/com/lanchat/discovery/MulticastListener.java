package com.lanchat.discovery;

import com.lanchat.model.DiscoveryMessage;
import com.lanchat.util.JsonUtil;
import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import org.slf4j.LoggerFactory;

public final class MulticastListener implements Runnable {
    private final MulticastSocket socket;
    private final DeviceRegistry registry;
    public MulticastListener(MulticastSocket socket, DeviceRegistry registry) { this.socket = socket; this.registry = registry; }
    @Override public void run() {
        byte[] buffer = new byte[4097];
        while (!socket.isClosed()) {
            try {
                var packet = new DatagramPacket(buffer, buffer.length);
                socket.receive(packet);
                if (packet.getLength() > 4096) throw new IllegalArgumentException("Oversized discovery datagram");
                registry.register(JsonUtil.decode(Arrays.copyOf(buffer, packet.getLength()), DiscoveryMessage.class), packet.getAddress().getHostAddress());
            } catch (IOException | IllegalArgumentException e) {
                if (!socket.isClosed()) LoggerFactory.getLogger(getClass()).debug("Rejected discovery packet or failed receive: {}", e.toString());
                if (e instanceof SocketException) { socket.close(); break; }
            }
        }
    }
}
