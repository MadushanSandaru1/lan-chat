package com.lanchat.messaging;

import com.lanchat.discovery.DeviceRegistry;
import com.lanchat.model.*;
import com.lanchat.repository.SQLiteChatRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.*;
import java.io.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

class TcpIntegrationTest {
    @TempDir Path temp;
    private final String a = UUID.randomUUID().toString(), b = UUID.randomUUID().toString();
    @Test void handshakeBidirectionalDeliveryReadAndDuplicateSuppression() throws Exception {
        try (var repoA = new SQLiteChatRepository(temp.resolve("a.db")); var repoB = new SQLiteChatRepository(temp.resolve("b.db"))) {
            var eventsA = new LinkedBlockingQueue<MessageService.Event>(); var eventsB = new LinkedBlockingQueue<MessageService.Event>();
            var serviceA = new MessageService(a, repoA, new DeviceRegistry(a), eventsA::add);
            var serviceB = new MessageService(b, repoB, new DeviceRegistry(b), eventsB::add);
            try (var managerA = new ConnectionManager(a, () -> "Alice", serviceA); var managerB = new ConnectionManager(b, () -> "Bob", serviceB);
                 var serverA = new ChatServer(0, a, () -> "Alice", managerA::accept); var serverB = new ChatServer(0, b, () -> "Bob", managerB::accept)) {
                serviceA.connections(managerA); serviceB.connections(managerB);
                var peerA = peer(a, serverA.port()); var peerB = peer(b, serverB.port());
                serviceA.send(peerB, "Hello B");
                await(eventsB, e -> e.incoming() != null);
                await(eventsA, e -> e.type() == MessageType.DELIVERY_ACK);
                assertEquals(MessageStatus.DELIVERED, repoA.history(a, b).getFirst().status());
                serviceB.markRead(peerA); await(eventsA, e -> e.type() == MessageType.READ_ACK);
                assertEquals(MessageStatus.READ, repoA.history(a, b).getFirst().status());
                serviceB.send(peerA, "Hello A"); await(eventsA, e -> e.incoming() != null);
                await(eventsB, e -> e.type() == MessageType.DELIVERY_ACK);
                assertEquals(2, repoA.history(a, b).size()); assertEquals(2, repoB.history(a, b).size());
                var original = repoA.history(a, b).getFirst();
                managerA.send(peerB, ProtocolMessage.event(MessageType.CHAT_MESSAGE, a, b, original.messageId(), original.content()));
                await(eventsA, e -> e.type() == MessageType.DELIVERY_ACK);
                assertEquals(2, repoB.history(a, b).size());
                serviceA.typing(peerB, true); await(eventsB, e -> e.type() == MessageType.TYPING_START);
                assertEquals(2, repoB.history(a, b).size());
            }
        }
    }
    @Test void malformedClientDoesNotKillServerAndWrongIdentityIsRejected() throws Exception {
        try (var manager = new ConnectionManager(b, () -> "Bob", (c,m) -> {}); var server = new ChatServer(0, b, () -> "Bob", manager::accept)) {
            try (var malicious = new Socket("127.0.0.1", server.port())) {
                new DataOutputStream(malicious.getOutputStream()).writeInt(Integer.MAX_VALUE);
            }
            assertThrows(IllegalArgumentException.class, () -> new ChatClient().connect(peer(a, server.port()), a, "Alice"));
            try (var valid = new ChatClient().connect(peer(b, server.port()), a, "Alice")) { assertEquals(b, valid.peerId()); }
        }
    }
    @Test void unavailableDefaultPortFallsBack() throws Exception {
        try (var occupied = new ServerSocket(0); var server = new ChatServer(occupied.getLocalPort(), b, () -> "Bob", PeerConnection::close)) {
            assertNotEquals(occupied.getLocalPort(), server.port()); assertTrue(server.port() > 0);
        }
    }
    @Test void failedSendCanBeRetriedAfterPeerReturns() throws Exception {
        try (var repoA = new SQLiteChatRepository(temp.resolve("a.db")); var repoB = new SQLiteChatRepository(temp.resolve("b.db"))) {
            var eventsA = new LinkedBlockingQueue<MessageService.Event>(); var eventsB = new LinkedBlockingQueue<MessageService.Event>();
            var serviceA = new MessageService(a, repoA, new DeviceRegistry(a), eventsA::add);
            var serviceB = new MessageService(b, repoB, new DeviceRegistry(b), eventsB::add);
            try (var managerA = new ConnectionManager(a, () -> "Alice", serviceA); var managerB = new ConnectionManager(b, () -> "Bob", serviceB)) {
                serviceA.connections(managerA); serviceB.connections(managerB);
                int closedPort;
                try (var reservation = new ServerSocket(0)) { closedPort = reservation.getLocalPort(); }
                assertThrows(IOException.class, () -> serviceA.send(peer(b, closedPort), "Try again"));
                var failed = repoA.history(a, b).getFirst(); assertEquals(MessageStatus.FAILED, failed.status());
                try (var server = new ChatServer(closedPort, b, () -> "Bob", managerB::accept)) {
                    serviceA.retry(peer(b, server.port()), failed);
                    await(eventsB, e -> e.incoming() != null); await(eventsA, e -> e.type() == MessageType.DELIVERY_ACK);
                    assertEquals(1, repoA.history(a, b).size()); assertEquals(1, repoB.history(a, b).size());
                    assertEquals(MessageStatus.DELIVERED, repoA.history(a, b).getFirst().status());
                }
            }
        }
    }
    private PeerDevice peer(String id, int port) { return new PeerDevice(id, "Peer", "Device", "127.0.0.1", port, true, 1); }
    private void await(BlockingQueue<MessageService.Event> queue, java.util.function.Predicate<MessageService.Event> matches) {
        assertTimeoutPreemptively(Duration.ofSeconds(8), () -> { while (true) { var event = queue.take(); if (matches.test(event)) return; } });
    }
}
