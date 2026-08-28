package com.lanchat.config;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Properties;

public final class AppConfig {
    private static final Properties VALUES = load();
    private AppConfig() {}
    private static Properties load() {
        var p = new Properties();
        try (var in = AppConfig.class.getResourceAsStream("/application.properties")) {
            if (in == null) throw new IllegalStateException("Missing application.properties");
            p.load(in);
            return p;
        } catch (IOException e) { throw new IllegalStateException("Cannot read configuration", e); }
    }
    public static String value(String key) { return VALUES.getProperty(key); }
    public static int number(String key) { return Integer.parseInt(value(key)); }
    public static Path dataDirectory() {
        String override = System.getProperty("lanchat.dataDir", System.getenv("LANCHAT_DATA_DIR"));
        if (override != null && !override.isBlank()) return Path.of(override);
        String os = System.getProperty("os.name").toLowerCase();
        String home = System.getProperty("user.home");
        if (os.contains("win")) return Path.of(System.getenv().getOrDefault("APPDATA", home), "LANChat");
        if (os.contains("mac")) return Path.of(home, "Library", "Application Support", "LANChat");
        return Path.of(System.getenv().getOrDefault("XDG_DATA_HOME", home + "/.local/share"), "lanchat");
    }
}
