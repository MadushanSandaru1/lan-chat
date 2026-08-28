package com.lanchat.messaging;

import com.lanchat.config.NetworkConfig;
import com.lanchat.model.*;
import com.lanchat.validation.ProtocolValidator;
import java.io.*;
import java.net.Socket;
import java.util.concurrent.*;
import org.slf4j.LoggerFactory;

public final class PeerConnection implements AutoCloseable {
    private static final ScheduledExecutorService DEADLINES = Executors.newSingleThreadScheduledExecutor(Thread.ofPlatform().daemon().name("socket-deadlines").factory());
    private final Socket socket;
    private final DataInputStream in;
    private final DataOutputStream out;
    private final String localId;
    private final ProtocolMessage hello;
    private final boolean outbound;
    public PeerConnection(Socket socket, String localId, String name, String expected, boolean outbound) throws IOException {
        this.socket = socket; this.localId = localId; this.outbound = outbound;
        socket.setTcpNoDelay(true); socket.setKeepAlive(true); socket.setSoTimeout(NetworkConfig.CONNECT_TIMEOUT);
        in = new DataInputStream(new BufferedInputStream(socket.getInputStream()));
        out = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
        send(ProtocolMessage.hello(localId, name));
        hello = MessageFraming.read(in);
        ProtocolValidator.hello(hello, expected);
        if (hello.deviceId().equals(localId)) throw new IOException("Refusing self connection");
        socket.setSoTimeout(90_000);
    }
    public String peerId() { return hello.deviceId(); }
    public String peerName() { return hello.displayName(); }
    public String address() { return socket.getInetAddress().getHostAddress(); }
    public boolean outbound() { return outbound; }
    public boolean open() { return !socket.isClosed(); }
    public synchronized void send(ProtocolMessage message) throws IOException {
        // Socket SO_TIMEOUT covers reads only. A stalled writer must also be bounded.
        var deadline = DEADLINES.schedule(this::close, NetworkConfig.CONNECT_TIMEOUT, TimeUnit.MILLISECONDS);
        try { MessageFraming.write(out, message); } finally { deadline.cancel(false); }
    }
    public void readLoop(ProtocolMessageHandler handler) throws IOException {
        while (open()) {
            ProtocolMessage m = MessageFraming.read(in);
            ProtocolValidator.message(m, peerId(), localId);
            if (m.type() == MessageType.DISCONNECT) break;
            if (m.type() == MessageType.PING) send(ProtocolMessage.event(MessageType.PONG, localId, peerId(), m.messageId(), null));
            else if (m.type() != MessageType.PONG) handler.handle(this, m);
        }
    }
    @Override public void close() {
        try { socket.close(); } catch (IOException e) { LoggerFactory.getLogger(getClass()).debug("Socket close failed", e); }
    }
}
