package com.lanchat.messaging;

import com.lanchat.config.NetworkConfig;
import com.lanchat.model.ProtocolMessage;
import com.lanchat.util.JsonUtil;
import java.io.*;

public final class MessageFraming {
    private MessageFraming() {}
    public static ProtocolMessage read(DataInputStream in) throws IOException {
        int size = in.readInt();
        if (size < 1 || size > NetworkConfig.MAX_FRAME) throw new IOException("Invalid frame length: " + size);
        byte[] bytes = new byte[size]; in.readFully(bytes);
        return JsonUtil.decode(bytes, ProtocolMessage.class);
    }
    public static void write(DataOutputStream out, ProtocolMessage message) throws IOException {
        byte[] bytes = JsonUtil.encode(message);
        if (bytes.length > NetworkConfig.MAX_FRAME) throw new IOException("Frame exceeds limit");
        out.writeInt(bytes.length); out.write(bytes); out.flush();
    }
}
