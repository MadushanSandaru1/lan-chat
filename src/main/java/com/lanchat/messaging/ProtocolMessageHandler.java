package com.lanchat.messaging;
import com.lanchat.model.ProtocolMessage;
import java.io.IOException;
@FunctionalInterface
public interface ProtocolMessageHandler { void handle(PeerConnection connection, ProtocolMessage message) throws IOException; }
