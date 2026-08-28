package com.lanchat.messaging;

import com.lanchat.config.NetworkConfig;
import com.lanchat.model.PeerDevice;
import java.io.IOException;
import java.net.*;

public final class ChatClient {
    public PeerConnection connect(PeerDevice peer, String localId, String name) throws IOException {
        var socket = new Socket();
        try {
            socket.connect(new InetSocketAddress(peer.ipAddress(), peer.chatPort()), NetworkConfig.CONNECT_TIMEOUT);
            return new PeerConnection(socket, localId, name, peer.deviceId(), true);
        } catch (IOException | RuntimeException e) { socket.close(); throw e; }
    }
}
