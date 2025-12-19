package de.sxrja.performancePerfected.managers;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.ChatColor;
import java.util.List;
import java.util.HashSet;
import java.util.Set;

public class NotificationManager {

    private JavaPlugin plugin;
    private ConfigManager configManager;

    // Countdown System
    private BukkitRunnable countdownTask = null;
    private int countdownSeconds = 0;
    private boolean countdownActive = false;
    private Set<String> notifiedPlayers = new HashSet<>();

    // Permission nodes
    public static final String PERM_NOTIFY = "performanceperfected.notify";
    public static final String PERM_ADMIN = "performanceperfected.admin";

    public NotificationManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    /**
     * COUNTDOWN FÜR CLEANUP STARTEN
     * @param totalSeconds Gesamte Countdown-Zeit in Sekunden
     */
    public void startCleanupCountdown(int totalSeconds) {
        stopCleanupCountdown();

        countdownActive = true;
        countdownSeconds = totalSeconds;
        notifiedPlayers.clear();

        // Sofortige 1-Minute Warnung
        broadcastCountdownWarning(60, "minute");

        countdownTask = new BukkitRunnable() {
            @Override
            public void run() {
                if (countdownSeconds <= 0 || !countdownActive) {
                    broadcastCleanupStarted();
                    stopCleanupCountdown();
                    return;
                }

                // Countdown-Logik
                if (countdownSeconds == 30) {
                    broadcastCountdownWarning(30, "seconds");
                } else if (countdownSeconds == 10) {
                    broadcastCountdownWarning(10, "seconds");
                } else if (countdownSeconds <= 5 && countdownSeconds >= 1) {
                    broadcastCountdownSeconds(countdownSeconds);
                }

                countdownSeconds--;
            }
        };

        // Task starten (jede Sekunde)
        countdownTask.runTaskTimer(plugin, 20L, 20L); // 20 Ticks = 1 Sekunde
    }

    /**
     * COUNTDOWN STOPPEN (wenn TPS sich verbessert)
     */
    public void stopCleanupCountdown() {
        if (countdownTask != null) {
            try {
                countdownTask.cancel();
            } catch (Exception e) {
                // Ignorieren
            }
        }

        if (countdownActive) {
            broadcastCountdownCancelled();
        }

        countdownTask = null;
        countdownActive = false;
        countdownSeconds = 0;
        notifiedPlayers.clear();
    }

    /**
     * COUNTDOWN-WARNUNGEN BROADCASTEN
     */
    private void broadcastCountdownWarning(int seconds, String timeUnit) {
        String messageKey = "cleanup.countdown." + seconds + (timeUnit.equals("minute") ? "min" : "sec");
        String message = configManager.getLangMessage(messageKey, getDefaultCountdownMessage(seconds, timeUnit));

        for (Player player : getServer().getOnlinePlayers()) {
            if (shouldNotifyPlayer(player)) {
                player.sendMessage(message);

                // Action Bar für wichtige Warnungen
                if (seconds <= 10) {
                    sendActionBar(player, "§c⚠ §eCleanup in §6" + seconds + "s §c⚠");
                }

                // Title für 1-Minute Warnung
                if (seconds == 60) {
                    sendTitle(player, "§c⚠ CLEANUP WARNING ⚠", "§7Items deleted in 1 minute", 10, 40, 10);
                }
            }
        }

        plugin.getLogger().info("Cleanup countdown: " + seconds + " " + timeUnit + " remaining");
    }

    /**
     * COUNTDOWN SEKUNDEN (5-1)
     */
    private void broadcastCountdownSeconds(int seconds) {
        String message = configManager.getLangMessage("cleanup.countdown." + seconds + "sec",
                "§c[" + seconds + "] §6Items will be deleted in §c" + seconds + " §6seconds!");

        for (Player player : getServer().getOnlinePlayers()) {
            if (shouldNotifyPlayer(player)) {
                player.sendMessage(message);
                sendActionBar(player, "§c" + seconds + " §7seconds until cleanup");
            }
        }
    }

    /**
     * CLEANUP GESTARTET NACHRICHT
     */
    private void broadcastCleanupStarted() {
        String message = configManager.getLangMessage("cleanup.started",
                "§c⚡ §6Performance Cleanup started! §7Deleting items...");

        for (Player player : getServer().getOnlinePlayers()) {
            if (shouldNotifyPlayer(player)) {
                player.sendMessage(message);
                sendTitle(player, "§c⚠ CLEANUP STARTED ⚠", "§7Items are being deleted", 10, 40, 10);
            }
        }

        plugin.getLogger().info("Cleanup started - items are being removed");
    }

    /**
     * CLEANUP ABGESCHLOSSEN NACHRICHT
     */
    public void broadcastCleanupCompleted(int itemsRemoved, int vehiclesRemoved, int xpOrbsRemoved) {
        String message = configManager.getLangMessage("cleanup.completed",
                "§a✅ §6Cleanup completed: §7" + itemsRemoved + " items, " + vehiclesRemoved + " vehicles, " + xpOrbsRemoved + " XP orbs removed");

        for (Player player : getServer().getOnlinePlayers()) {
            if (shouldNotifyPlayer(player)) {
                player.sendMessage(message);
            }
        }

        plugin.getLogger().info("Cleanup completed: " + itemsRemoved + " items, " + vehiclesRemoved + " vehicles, " + xpOrbsRemoved + " XP orbs removed");
    }

    /**
     * COUNTDOWN ABGEBROCHEN (TPS verbessert)
     */
    private void broadcastCountdownCancelled() {
        String message = configManager.getLangMessage("cleanup.cancelled",
                "§a✅ §6Cleanup cancelled §7(Server performance improved)");

        for (Player player : getServer().getOnlinePlayers()) {
            if (shouldNotifyPlayer(player)) {
                player.sendMessage(message);
                sendActionBar(player, "§a✅ Cleanup cancelled");
            }
        }

        plugin.getLogger().info("Cleanup countdown cancelled - server performance improved");
    }

    /**
     * ACTION BAR NACHRICHT SENDEN
     */
    private void sendActionBar(Player player, String message) {
        try {
            player.sendActionBar(ChatColor.translateAlternateColorCodes('&', message));
        } catch (Exception e) {
            // Fallback
            player.sendMessage(message);
        }
    }

    /**
     * TITLE NACHRICHT SENDEN
     */
    private void sendTitle(Player player, String title, String subtitle, int fadeIn, int stay, int fadeOut) {
        try {
            player.sendTitle(
                    ChatColor.translateAlternateColorCodes('&', title),
                    ChatColor.translateAlternateColorCodes('&', subtitle),
                    fadeIn, stay, fadeOut
            );
        } catch (Exception e) {
            // Ignorieren
        }
    }

    /**
     * DEFAULT COUNTDOWN NACHRICHTEN
     */
    private String getDefaultCountdownMessage(int seconds, String timeUnit) {
        if (timeUnit.equals("minute")) {
            return "§c⚠ §6Warning! §7Items will be deleted in §c1 minute §7(§e" + seconds + "s§7)";
        } else {
            return "§c[" + seconds + "] §6Items will be deleted in §c" + seconds + " §6seconds!";
        }
    }

    /**
     * PRÜFEN OB SPIELER BENACHRICHTIGUNGEN ERHÄLT
     */
    private boolean shouldNotifyPlayer(Player player) {
        boolean notifyAll = configManager.getActiveConfig().getBoolean("cleanup.notify-all-players", true);

        if (notifyAll) {
            return true;
        } else {
            return hasNotifyPermission(player);
        }
    }

    /**
     * KONFIGURATIONSÄNDERUNGEN
     */
    public void onConfigChanged(List<String> changedFiles) {
        String changeMsg = configManager.getLangMessage("config.change-detected",
                "&e⚡ &6PerformancePerfected: &fConfiguration changed!");
        String restartMsg = configManager.getLangMessage("config.restart-required",
                "&e🔄 &6Server restart required");

        for (Player player : getServer().getOnlinePlayers()) {
            if (hasNotifyPermission(player)) {
                player.sendMessage(changeMsg);
                player.sendMessage(restartMsg);
            }
        }

        plugin.getLogger().warning("⚠️ Configuration changes detected! Server restart recommended.");
        plugin.getLogger().warning("Changed files: " + String.join(", ", changedFiles));
    }

    public void broadcastConfigChange() {
        for (Player player : getServer().getOnlinePlayers()) {
            if (hasNotifyPermission(player)) {
                sendConfigChangeNotification(player);
            }
        }
    }

    private void sendConfigChangeNotification(Player player) {
        String[] broadcastLines = {
                configManager.getLangMessage("config.broadcast.line1", "&c⚠══════════════════════════════════════⚠"),
                configManager.getLangMessage("config.broadcast.line2", "&6⚡ &ePerformance settings have been changed!"),
                configManager.getLangMessage("config.broadcast.line3", "&f➤ &7A &cserver restart &7is required"),
                configManager.getLangMessage("config.broadcast.line4", "&f➤ &7Do not just use &e/reload"),
                configManager.getLangMessage("config.broadcast.line5", "&f➤ &7Changes will only take effect after restart"),
                configManager.getLangMessage("config.broadcast.line6", "&c⚠══════════════════════════════════════⚠")
        };

        for (String line : broadcastLines) {
            player.sendMessage(line);
        }
    }

    public void broadcastToOps(String messagePath) {
        String message = configManager.getLangMessage(messagePath,
                "&cMissing translation: " + messagePath);
        for (Player player : getServer().getOnlinePlayers()) {
            if (player.isOp() || hasAdminPermission(player)) {
                player.sendMessage(message);
            }
        }
    }

    public void broadcastToOps(String messagePath, String defaultValue) {
        String message = configManager.getLangMessage(messagePath, defaultValue);
        for (Player player : getServer().getOnlinePlayers()) {
            if (player.isOp() || hasAdminPermission(player)) {
                player.sendMessage(message);
            }
        }
    }

    /**
     * HELFER-METHODEN
     */
    private org.bukkit.Server getServer() {
        return plugin.getServer();
    }

    public boolean hasNotifyPermission(Player player) {
        return player.hasPermission(PERM_NOTIFY) || player.hasPermission(PERM_ADMIN) || player.isOp();
    }

    public boolean hasAdminPermission(Player player) {
        return player.hasPermission(PERM_ADMIN) || player.isOp();
    }

    public boolean isCountdownActive() {
        return countdownActive;
    }

    public int getRemainingCountdownSeconds() {
        return countdownSeconds;
    }
}