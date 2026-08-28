package com.lanchat.discovery;

import com.lanchat.model.DiscoveryMessage;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class DeviceRegistryTest {
    @Test void ignoresSelfDeduplicatesUsesSourceAndExpiresByLocalClock() {
        String local = UUID.randomUUID().toString(), remote = UUID.randomUUID().toString();
        Clock clock = mock(Clock.class); when(clock.millis()).thenReturn(1000L);
        var registry = new DeviceRegistry(local, clock); var updates = new AtomicInteger(); registry.onChange(updates::incrementAndGet);
        registry.register(advertisement(local), "127.0.0.1"); assertTrue(registry.snapshot().isEmpty());
        registry.register(advertisement(remote), "192.168.1.2"); registry.register(advertisement(remote), "192.168.1.3");
        assertEquals(1, registry.snapshot().size()); assertEquals("192.168.1.3", registry.get(remote).ipAddress());
        when(clock.millis()).thenReturn(10999L); registry.expire(10000); assertTrue(registry.get(remote).online());
        when(clock.millis()).thenReturn(11000L); registry.expire(10000); assertFalse(registry.get(remote).online());
        registry.register(advertisement(remote), "192.168.1.4"); assertTrue(registry.get(remote).online()); assertEquals(4, updates.get());
    }
    private DiscoveryMessage advertisement(String id) { return new DiscoveryMessage("DISCOVERY", "LAN_CHAT", "1.0", id, "Alice", "Laptop", 45679, Long.MAX_VALUE); }
}
