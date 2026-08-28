package com.lanchat.repository;

import com.lanchat.model.*;
import java.util.List;

public interface ChatRepository extends AutoCloseable {
    boolean save(ChatMessage message);
    List<ChatMessage> history(String local, String peer);
    java.util.Map<String, ConversationSummary> summaries(String local);
    void status(String messageId, String sender, String receiver, MessageStatus status);
    void savePeer(PeerDevice peer);
    List<PeerDevice> peers();
    void clear();
    @Override void close();
}
