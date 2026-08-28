package com.lanchat.config;

public final class NetworkConfig {
    private NetworkConfig() {}
    public static final String VERSION = AppConfig.value("app.protocol-version");
    public static final String GROUP = AppConfig.value("network.discovery.multicast-address");
    public static final int DISCOVERY_PORT = AppConfig.number("network.discovery.port");
    public static final int CHAT_PORT = AppConfig.number("network.chat.default-port");
    public static final int INTERVAL = AppConfig.number("network.discovery.interval-ms");
    public static final int PEER_TIMEOUT = AppConfig.number("network.discovery.peer-timeout-ms");
    public static final int CONNECT_TIMEOUT = AppConfig.number("network.chat.connection-timeout-ms");
    public static final int MAX_FRAME = AppConfig.number("network.chat.max-frame-size");
    public static final int MAX_TEXT = AppConfig.number("chat.max-message-size");
    public static final int TYPING_TIMEOUT = AppConfig.number("chat.typing-timeout-ms");
}
