package com.lanchat.util;

import java.net.*;
import java.util.*;

public final class NetworkUtil {
    private NetworkUtil() {}
    public static List<NetworkInterface> interfaces() throws SocketException {
        var result = new ArrayList<NetworkInterface>();
        for (var n : Collections.list(NetworkInterface.getNetworkInterfaces())) {
            if (n.isUp() && !n.isLoopback() && n.supportsMulticast() && !n.isVirtual()
                    && usableName(n.getName()) && !ipv4(n).isEmpty()) result.add(n);
        }
        result.sort(Comparator.comparing(NetworkInterface::getName));
        return List.copyOf(result);
    }
    public static boolean usableName(String name) {
        return !name.matches("(?i)^(docker|veth|virbr|vmnet|vbox|br-|utun|tun|tap|awdl|llw).*");
    }
    public static String ipv4(NetworkInterface n) {
        return Collections.list(n.getInetAddresses()).stream().filter(a -> a instanceof Inet4Address && !a.isLoopbackAddress())
                .map(InetAddress::getHostAddress).findFirst().orElse("");
    }
}
