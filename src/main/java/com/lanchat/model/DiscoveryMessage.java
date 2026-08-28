package com.lanchat.model;
public record DiscoveryMessage(String type, String app, String protocolVersion, String deviceId,
                               String displayName, String deviceName, int chatPort, long timestamp) {}
