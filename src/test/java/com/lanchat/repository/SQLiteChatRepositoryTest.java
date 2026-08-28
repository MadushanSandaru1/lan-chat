package com.lanchat.repository;

import com.lanchat.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class SQLiteChatRepositoryTest {
    @TempDir Path temp;
    @Test void persistsDeduplicatesScopesAcknowledgementsAndNeverRegressesStatus() {
        String a = UUID.randomUUID().toString(), b = UUID.randomUUID().toString(), id = UUID.randomUUID().toString();
        try (var repo = new SQLiteChatRepository(temp.resolve("chat.db"))) {
            var m = new ChatMessage(id, a, b, "It's a 'prepared' statement 🌍", 123, MessageStatus.SENDING);
            assertTrue(repo.save(m)); assertFalse(repo.save(m));
            repo.status(id, b, a, MessageStatus.READ); assertEquals(MessageStatus.SENDING, repo.history(a, b).getFirst().status());
            repo.status(id, a, b, MessageStatus.READ); repo.status(id, a, b, MessageStatus.SENT); repo.status(id, a, b, MessageStatus.FAILED);
            assertEquals(MessageStatus.READ, repo.history(a, b).getFirst().status());
            repo.savePeer(new PeerDevice(b, "Bob", "Laptop", "127.0.0.1", 2000, true, 123));
        }
        try (var reopened = new SQLiteChatRepository(temp.resolve("chat.db"))) {
            assertEquals(1, reopened.history(a, b).size()); assertFalse(reopened.peers().getFirst().online());
            reopened.clear(); assertTrue(reopened.history(a, b).isEmpty());
        }
    }
    @Test void recoversInterruptedSendsAsFailed() {
        try (var repo = new SQLiteChatRepository(temp.resolve("chat.db"))) { repo.save(new ChatMessage("id", "a", "b", "hello", 1, MessageStatus.SENDING)); }
        try (var repo = new SQLiteChatRepository(temp.resolve("chat.db"))) { assertEquals(MessageStatus.FAILED, repo.history("a", "b").getFirst().status()); }
    }
    @Test void summariesCountUnreadBeyondTheVisibleHistoryWindow() {
        try (var repo = new SQLiteChatRepository(temp.resolve("chat.db"))) {
            for (int i = 0; i < 510; i++) repo.save(new ChatMessage("id-" + i, "b", "a", "message " + i, i + 1, MessageStatus.DELIVERED));
            assertEquals(500, repo.history("a", "b").size());
            assertEquals(510, repo.summaries("a").get("b").unread());
            assertEquals("message 509", repo.summaries("a").get("b").preview());
            repo.status("id-0", "b", "a", MessageStatus.READ);
            assertEquals(509, repo.summaries("a").get("b").unread());
        }
    }
}
