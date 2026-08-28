package com.lanchat.service;

import com.lanchat.validation.ProtocolValidator;
import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.*;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.util.Properties;
import java.util.UUID;

/** Holds a process lock so two instances never share an identity or writable database. */
public final class UserProfileService implements AutoCloseable {
    private final Path file;
    private final Properties values = new Properties();
    private final FileChannel lockChannel;
    private final FileLock lock;
    public UserProfileService(Path directory) throws IOException {
        Files.createDirectories(directory);
        file = directory.resolve("profile.properties");
        lockChannel = FileChannel.open(directory.resolve("instance.lock"), StandardOpenOption.CREATE, StandardOpenOption.WRITE);
        FileLock acquired;
        try { acquired = lockChannel.tryLock(); }
        catch (RuntimeException | IOException e) { lockChannel.close(); throw e; }
        if (acquired == null) { lockChannel.close(); throw new IOException("LAN Chat is already using this data directory"); }
        lock = acquired;
        try {
            if (Files.exists(file)) try (var in = Files.newInputStream(file)) { values.load(in); }
            values.putIfAbsent("deviceId", UUID.randomUUID().toString());
            ProtocolValidator.uuid(id());
            values.putIfAbsent("deviceName", deviceName());
            save();
        } catch (IOException | RuntimeException e) { close(); throw e; }
    }
    private static String deviceName() {
        try { return InetAddress.getLocalHost().getHostName(); }
        catch (java.net.UnknownHostException e) { return "My device"; }
    }
    public synchronized String id() { return values.getProperty("deviceId"); }
    public synchronized String name() { return values.getProperty("displayName", ""); }
    public synchronized String device() { return values.getProperty("deviceName"); }
    public synchronized String networkInterface() { return values.getProperty("networkInterface", ""); }
    public synchronized boolean preference(String key) { return Boolean.parseBoolean(values.getProperty(key, "true")); }
    public synchronized void update(String name, String device, String iface, boolean notifications, boolean sounds, boolean showIp) throws IOException {
        ProtocolValidator.name(name); ProtocolValidator.name(device);
        values.setProperty("displayName", name.strip()); values.setProperty("deviceName", device.strip());
        values.setProperty("networkInterface", iface); values.setProperty("notifications", "" + notifications);
        values.setProperty("sounds", "" + sounds); values.setProperty("showIp", "" + showIp); save();
    }
    private void save() throws IOException {
        Path temp = file.resolveSibling("profile.properties.tmp");
        try (var out = Files.newOutputStream(temp)) { values.store(out, "LAN Chat local profile"); }
        try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
        catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
    }
    @Override public synchronized void close() throws IOException { try { lock.release(); } finally { lockChannel.close(); } }
}
