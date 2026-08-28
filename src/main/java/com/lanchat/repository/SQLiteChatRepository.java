package com.lanchat.repository;

import com.lanchat.exception.PersistenceException;
import com.lanchat.model.*;
import java.nio.file.Path;
import java.sql.*;
import java.util.*;

/** One serialized JDBC connection. Callers must run outside the FX thread. */
public final class SQLiteChatRepository implements ChatRepository {
    private final Connection db;
    public SQLiteChatRepository(Path file) {
        try {
            db = DriverManager.getConnection("jdbc:sqlite:" + file.toAbsolutePath());
            try (var s = db.createStatement()) {
                s.execute("PRAGMA journal_mode=WAL"); s.execute("PRAGMA busy_timeout=5000");
                s.execute("CREATE TABLE IF NOT EXISTS messages (message_id TEXT PRIMARY KEY, sender_id TEXT NOT NULL, receiver_id TEXT NOT NULL, content TEXT NOT NULL, timestamp INTEGER NOT NULL, status TEXT NOT NULL)");
                s.execute("CREATE INDEX IF NOT EXISTS conversation ON messages(sender_id, receiver_id, timestamp)");
                s.execute("CREATE TABLE IF NOT EXISTS peers (id TEXT PRIMARY KEY, name TEXT NOT NULL, device TEXT NOT NULL, ip TEXT NOT NULL, port INTEGER NOT NULL, last_seen INTEGER NOT NULL)");
                s.executeUpdate("UPDATE messages SET status='FAILED' WHERE status='SENDING'");
            }
        } catch (SQLException e) { throw failure(e); }
    }
    private PersistenceException failure(SQLException e) { return new PersistenceException("Local chat database operation failed", e); }
    @Override public synchronized boolean save(ChatMessage m) {
        try (var s = db.prepareStatement("INSERT OR IGNORE INTO messages VALUES (?, ?, ?, ?, ?, ?)")) {
            s.setString(1, m.messageId()); s.setString(2, m.senderId()); s.setString(3, m.receiverId());
            s.setString(4, m.content()); s.setLong(5, m.timestamp()); s.setString(6, m.status().name());
            return s.executeUpdate() == 1;
        } catch (SQLException e) { throw failure(e); }
    }
    @Override public synchronized List<ChatMessage> history(String local, String peer) {
        try (var s = db.prepareStatement("SELECT * FROM (SELECT * FROM messages WHERE (sender_id=? AND receiver_id=?) OR (sender_id=? AND receiver_id=?) ORDER BY timestamp DESC, rowid DESC LIMIT 500) ORDER BY timestamp ASC")) {
            s.setString(1, local); s.setString(2, peer); s.setString(3, peer); s.setString(4, local);
            var result = new ArrayList<ChatMessage>();
            try (var r = s.executeQuery()) { while (r.next()) result.add(new ChatMessage(r.getString("message_id"), r.getString("sender_id"), r.getString("receiver_id"), r.getString("content"), r.getLong("timestamp"), MessageStatus.valueOf(r.getString("status")))); }
            return List.copyOf(result);
        } catch (SQLException e) { throw failure(e); }
    }
    @Override public synchronized void status(String id, String sender, String receiver, MessageStatus next) {
        // Include both identities: another peer cannot acknowledge someone else's message.
        String allowed = switch (next) {
            case SENDING -> "'FAILED'";
            case SENT, FAILED -> "'SENDING'";
            case DELIVERED -> "'SENDING','SENT','FAILED'";
            case READ -> "'SENDING','SENT','FAILED','DELIVERED'";
        };
        try (var s = db.prepareStatement("UPDATE messages SET status=? WHERE message_id=? AND sender_id=? AND receiver_id=? AND status IN (" + allowed + ")")) {
            s.setString(1, next.name()); s.setString(2, id); s.setString(3, sender); s.setString(4, receiver); s.executeUpdate();
        } catch (SQLException e) { throw failure(e); }
    }
    @Override public synchronized Map<String, ConversationSummary> summaries(String local) {
        String sql = """
                SELECT peer, content, unread FROM (
                  SELECT CASE WHEN sender_id=? THEN receiver_id ELSE sender_id END AS peer, content,
                    SUM(CASE WHEN receiver_id=? AND status!='READ' THEN 1 ELSE 0 END) OVER
                      (PARTITION BY CASE WHEN sender_id=? THEN receiver_id ELSE sender_id END) AS unread,
                    ROW_NUMBER() OVER (PARTITION BY CASE WHEN sender_id=? THEN receiver_id ELSE sender_id END
                      ORDER BY timestamp DESC, rowid DESC) AS rn
                  FROM messages WHERE sender_id=? OR receiver_id=?
                ) WHERE rn=1
                """;
        try (var s = db.prepareStatement(sql)) {
            for (int i = 1; i <= 6; i++) s.setString(i, local);
            var result = new HashMap<String, ConversationSummary>();
            try (var r = s.executeQuery()) { while (r.next()) result.put(r.getString("peer"), new ConversationSummary(r.getString("content"), r.getLong("unread"))); }
            return Map.copyOf(result);
        } catch (SQLException e) { throw failure(e); }
    }
    @Override public synchronized void savePeer(PeerDevice p) {
        try (var s = db.prepareStatement("INSERT INTO peers VALUES(?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET name=excluded.name, device=excluded.device, ip=excluded.ip, port=excluded.port, last_seen=excluded.last_seen")) {
            s.setString(1, p.deviceId()); s.setString(2, p.displayName()); s.setString(3, p.deviceName()); s.setString(4, p.ipAddress()); s.setInt(5, p.chatPort()); s.setLong(6, p.lastSeen()); s.executeUpdate();
        } catch (SQLException e) { throw failure(e); }
    }
    @Override public synchronized List<PeerDevice> peers() {
        try (var s = db.createStatement(); var r = s.executeQuery("SELECT * FROM peers")) {
            var result = new ArrayList<PeerDevice>();
            while (r.next()) result.add(new PeerDevice(r.getString("id"), r.getString("name"), r.getString("device"), r.getString("ip"), r.getInt("port"), false, r.getLong("last_seen")));
            return List.copyOf(result);
        } catch (SQLException e) { throw failure(e); }
    }
    @Override public synchronized void clear() {
        try (var s = db.createStatement()) { s.executeUpdate("DELETE FROM messages"); s.execute("PRAGMA wal_checkpoint(TRUNCATE)"); }
        catch (SQLException e) { throw failure(e); }
    }
    @Override public synchronized void close() { try { db.close(); } catch (SQLException e) { throw failure(e); } }
}
