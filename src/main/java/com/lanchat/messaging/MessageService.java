package com.lanchat.messaging;

import com.lanchat.model.*;
import com.lanchat.repository.ChatRepository;
import com.lanchat.discovery.DeviceRegistry;
import com.lanchat.config.NetworkConfig;
import com.lanchat.validation.ProtocolValidator;
import java.io.IOException;
import java.util.List;
import java.util.function.Consumer;

public final class MessageService implements ProtocolMessageHandler {
    public record Event(String peerId, ChatMessage incoming, MessageType type) {}
    private final String localId;
    private final ChatRepository repository;
    private final DeviceRegistry registry;
    private final Consumer<Event> changed;
    private ConnectionManager connections;
    public MessageService(String localId, ChatRepository repository, DeviceRegistry registry, Consumer<Event> changed) {
        this.localId = localId; this.repository = repository; this.registry = registry; this.changed = changed;
    }
    public void connections(ConnectionManager connections) { this.connections = connections; }
    @Override public void handle(PeerConnection connection, ProtocolMessage m) throws IOException {
        String peer = connection.peerId();
        switch (m.type()) {
            case CHAT_MESSAGE -> {
                // Remember an inbound peer even if its multicast advertisement has not arrived yet.
                if (registry.get(peer) == null) {
                    var p = new PeerDevice(peer, connection.peerName(), "LAN device", connection.address(), NetworkConfig.CHAT_PORT, false, System.currentTimeMillis());
                    repository.savePeer(p); registry.restore(List.of(p));
                }
                var chat = new ChatMessage(m.messageId(), m.senderId(), m.receiverId(), m.content(), m.timestamp(), MessageStatus.DELIVERED);
                boolean inserted = repository.save(chat);
                connection.send(ProtocolMessage.event(MessageType.DELIVERY_ACK, localId, peer, m.messageId(), null));
                if (inserted) changed.accept(new Event(peer, chat, m.type()));
                else {
                    // A retry of a previously-read message receives its original read receipt again.
                    boolean read = repository.history(localId, peer).stream().anyMatch(x -> x.messageId().equals(m.messageId()) && x.status() == MessageStatus.READ);
                    if (read) connection.send(ProtocolMessage.event(MessageType.READ_ACK, localId, peer, m.messageId(), null));
                }
            }
            case DELIVERY_ACK, READ_ACK -> {
                repository.status(m.messageId(), localId, peer, m.type() == MessageType.READ_ACK ? MessageStatus.READ : MessageStatus.DELIVERED);
                changed.accept(new Event(peer, null, m.type()));
            }
            case TYPING_START, TYPING_STOP -> changed.accept(new Event(peer, null, m.type()));
            default -> { /* Heartbeats/disconnect are handled by the session. */ }
        }
    }
    public void send(PeerDevice peer, String content) throws IOException {
        ProtocolValidator.text(content);
        var wire = ProtocolMessage.event(MessageType.CHAT_MESSAGE, localId, peer.deviceId(), null, content);
        var chat = new ChatMessage(wire.messageId(), localId, peer.deviceId(), content, wire.timestamp(), MessageStatus.SENDING);
        repository.save(chat); changed.accept(new Event(peer.deviceId(), null, MessageType.CHAT_MESSAGE));
        transmit(peer, wire);
    }
    private void transmit(PeerDevice peer, ProtocolMessage wire) throws IOException {
        try { connections.send(peer, wire); repository.status(wire.messageId(), localId, peer.deviceId(), MessageStatus.SENT); }
        catch (IOException | RuntimeException e) { repository.status(wire.messageId(), localId, peer.deviceId(), MessageStatus.FAILED); throw e; }
        finally { changed.accept(new Event(peer.deviceId(), null, MessageType.CHAT_MESSAGE)); }
    }
    public void retry(PeerDevice peer, ChatMessage chat) throws IOException {
        if (!chat.senderId().equals(localId) || !chat.receiverId().equals(peer.deviceId()) || chat.status() != MessageStatus.FAILED) return;
        repository.status(chat.messageId(), localId, peer.deviceId(), MessageStatus.SENDING);
        transmit(peer, new ProtocolMessage(MessageType.CHAT_MESSAGE, NetworkConfig.VERSION, chat.messageId(), localId, peer.deviceId(), chat.content(), chat.timestamp(), null, null));
    }
    public void markRead(PeerDevice peer) throws IOException {
        for (var m : repository.history(localId, peer.deviceId())) {
            if (m.receiverId().equals(localId) && m.status() != MessageStatus.READ) {
                connections.send(peer, ProtocolMessage.event(MessageType.READ_ACK, localId, peer.deviceId(), m.messageId(), null));
                repository.status(m.messageId(), peer.deviceId(), localId, MessageStatus.READ);
            }
        }
    }
    public void typing(PeerDevice peer, boolean typing) throws IOException {
        connections.send(peer, ProtocolMessage.event(typing ? MessageType.TYPING_START : MessageType.TYPING_STOP, localId, peer.deviceId(), null, null));
    }
}
