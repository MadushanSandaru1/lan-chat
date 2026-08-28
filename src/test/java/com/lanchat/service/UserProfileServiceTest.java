package com.lanchat.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import static org.junit.jupiter.api.Assertions.*;

class UserProfileServiceTest {
    @TempDir Path temp;
    @Test void persistsIdentityPreferencesAndPreventsConcurrentUse() throws Exception {
        String id;
        try (var profile = new UserProfileService(temp)) {
            id = profile.id(); assertTrue(profile.name().isEmpty());
            profile.update("Alice", "Laptop", "en0", false, false, true);
            assertThrows(Exception.class, () -> new UserProfileService(temp));
        }
        try (var profile = new UserProfileService(temp)) {
            assertEquals(id, profile.id()); assertEquals("Alice", profile.name());
            assertFalse(profile.preference("notifications")); assertEquals("en0", profile.networkInterface());
        }
    }
}
