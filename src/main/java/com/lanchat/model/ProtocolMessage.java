package com.lanchat.model;

import com.lanchat.config.NetworkConfig;
import java.util.UUID;

public record ProtocolMessage(MessageType type, String protocolVersion, String messageId,
                              String senderId, String receiverId, String content, long timestamp,
                              String deviceId, String displayName) {
    public static ProtocolMessage hello(String id, String name) {
        return new ProtocolMessage(MessageType.HELLO, NetworkConfig.VERSION, null, null, null, null,
                System.currentTimeMillis(), id, name);
    }
    public static ProtocolMessage event(MessageType type, String sender, String receiver, String id, String content) {
        return new ProtocolMessage(type, NetworkConfig.VERSION, id == null ? UUID.randomUUID().toString() : id,
                sender, receiver, content, System.currentTimeMillis(), null, null);
    }
}
