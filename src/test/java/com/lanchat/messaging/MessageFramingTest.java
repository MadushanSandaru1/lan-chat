package com.lanchat.messaging;

import com.lanchat.model.*;
import com.lanchat.util.JsonUtil;
import org.junit.jupiter.api.Test;
import java.io.*;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

class MessageFramingTest {
    @Test void readsFragmentedAndCoalescedFrames() throws Exception {
        var m = ProtocolMessage.hello(UUID.randomUUID().toString(), "Alice");
        var bytes = new ByteArrayOutputStream(); var out = new DataOutputStream(bytes);
        MessageFraming.write(out, m); MessageFraming.write(out, m);
        var fragmented = new FilterInputStream(new ByteArrayInputStream(bytes.toByteArray())) {
            @Override public int read(byte[] b, int off, int len) throws IOException { return super.read(b, off, Math.min(1, len)); }
        };
        var in = new DataInputStream(fragmented);
        assertEquals(m, MessageFraming.read(in)); assertEquals(m, MessageFraming.read(in));
        assertThrows(EOFException.class, () -> MessageFraming.read(in));
    }
    @Test void rejectsOversizeNegativeZeroAndTruncatedFrames() throws Exception {
        for (int length : new int[]{-1, 0, 65537, Integer.MAX_VALUE, 50}) {
            var bytes = new ByteArrayOutputStream(); new DataOutputStream(bytes).writeInt(length);
            assertThrows(IOException.class, () -> MessageFraming.read(new DataInputStream(new ByteArrayInputStream(bytes.toByteArray()))));
        }
    }
    @Test void rejectsUnknownTypeTrailingJsonAndDuplicateKeys() {
        for (String json : new String[]{"{\"type\":\"EXPLOIT\"}", "{} {}", "{\"type\":\"PING\",\"type\":\"PONG\"}", "not json"})
            assertThrows(IOException.class, () -> JsonUtil.decode(json.getBytes(), ProtocolMessage.class));
    }
}
