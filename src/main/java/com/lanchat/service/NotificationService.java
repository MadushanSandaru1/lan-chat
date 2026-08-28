package com.lanchat.service;

import java.awt.*;
import java.awt.image.BufferedImage;
import org.slf4j.LoggerFactory;

/** Optional AWT tray integration; no Swing or platform command execution. */
public final class NotificationService implements AutoCloseable {
    private TrayIcon icon;
    public NotificationService() {
        EventQueue.invokeLater(() -> {
            if (!GraphicsEnvironment.isHeadless() && SystemTray.isSupported()) {
                try {
                    var image = new BufferedImage(32, 32, BufferedImage.TYPE_INT_ARGB);
                    var g = image.createGraphics(); g.setColor(new Color(36, 165, 123)); g.fillRoundRect(2, 2, 28, 24, 10, 10); g.setColor(Color.WHITE); g.drawString("L", 12, 19); g.dispose();
                    icon = new TrayIcon(image, "LAN Chat"); icon.setImageAutoSize(true); SystemTray.getSystemTray().add(icon);
                } catch (AWTException | RuntimeException e) { LoggerFactory.getLogger(getClass()).info("Desktop notifications unavailable: {}", e.toString()); }
            }
        });
    }
    public void notify(String name, String content, boolean desktop, boolean sound) {
        EventQueue.invokeLater(() -> {
            if (desktop && icon != null) icon.displayMessage(name, content.length() > 160 ? content.substring(0, 160) + "…" : content, TrayIcon.MessageType.INFO);
            if (sound && !GraphicsEnvironment.isHeadless()) Toolkit.getDefaultToolkit().beep();
        });
    }
    @Override public void close() { EventQueue.invokeLater(() -> { if (icon != null) SystemTray.getSystemTray().remove(icon); }); }
}
