package com.lanchat.model;
public record PeerDevice(String deviceId, String displayName, String deviceName, String ipAddress,
                         int chatPort, boolean online, long lastSeen) {
    public PeerDevice offline() { return new PeerDevice(deviceId, displayName, deviceName, ipAddress, chatPort, false, lastSeen); }
}
