package com.lanchat.discovery;

import com.lanchat.service.UserProfileService;
import com.lanchat.util.NetworkUtil;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfSystemProperty(named = "lanchat.networkTest", matches = "true")
class MulticastIntegrationTest {
    @TempDir Path temp;
    @Test void twoInstancesDiscoverEachOtherOnRealMulticastInterface() throws Exception {
        assertFalse(NetworkUtil.interfaces().isEmpty(), "A multicast-capable LAN interface is required");
        try (var a = new UserProfileService(temp.resolve("a")); var b = new UserProfileService(temp.resolve("b"))) {
            a.update("Test Alice", "Test A", "", false, false, false); b.update("Test Bob", "Test B", "", false, false, false);
            var registryA = new DeviceRegistry(a.id()); var registryB = new DeviceRegistry(b.id());
            var foundA = new CompletableFuture<Void>(); var foundB = new CompletableFuture<Void>();
            registryA.onChange(() -> { if (registryA.get(b.id()) != null) foundA.complete(null); });
            registryB.onChange(() -> { if (registryB.get(a.id()) != null) foundB.complete(null); });
            try (var discoveryA = new DeviceDiscoveryService(a, registryA, 45001, (s,d) -> {});
                 var discoveryB = new DeviceDiscoveryService(b, registryB, 45002, (s,d) -> {})) {
                discoveryA.start(); discoveryB.start();
                CompletableFuture.allOf(foundA, foundB).get(12, TimeUnit.SECONDS);
                assertEquals(45002, registryA.get(b.id()).chatPort());
                assertEquals(45001, registryB.get(a.id()).chatPort());
            }
        }
    }
}
