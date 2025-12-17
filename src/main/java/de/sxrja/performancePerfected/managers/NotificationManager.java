package de.sxrja.performancePerfected.managers;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import java.util.List;

public class NotificationManager {

    private JavaPlugin plugin;
    private ConfigManager configManager;

    // Permission nodes
    public static final String PERM_NOTIFY = "performanceperfected.notify";
    public static final String PERM_ADMIN = "performanceperfected.admin";

    public NotificationManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
    }

    public void onConfigChanged(List<String> changedFiles) {
        // Sofortige Chat-Benachrichtigung an Admins
        String changeMsg = configManager.getLangMessage("config.change-detected",
                "&e⚡ &6PerformancePerfected: &fConfig wurde geändert!");
        String restartMsg = configManager.getLangMessage("config.restart-required",
                "&e🔄 &6Neustart erforderlich");

        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (hasNotifyPermission(player)) {
                player.sendMessage(changeMsg);
                player.sendMessage(restartMsg);
            }
        }

        // Console-Log
        plugin.getLogger().warning("⚠️ Config-Änderungen erkannt! Server-Neustart empfohlen.");
        plugin.getLogger().warning("Geänderte Dateien: " + String.join(", ", changedFiles));
    }

    public void broadcastConfigChange() {
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (hasNotifyPermission(player)) {
                sendConfigChangeNotification(player);
            }
        }
    }

    private void sendConfigChangeNotification(Player player) {
        String[] broadcastLines = {
                configManager.getLangMessage("config.broadcast.line1", "&c⚠══════════════════════════════════════⚠"),
                configManager.getLangMessage("config.broadcast.line2", "&6⚡ &ePerformance-Einstellungen wurden geändert!"),
                configManager.getLangMessage("config.broadcast.line3", "&f➤ &7Ein &cServer-Neustart &7ist erforderlich"),
                configManager.getLangMessage("config.broadcast.line4", "&f➤ &7Nicht nur &e/reload &7verwenden"),
                configManager.getLangMessage("config.broadcast.line5", "&f➤ &7Änderungen werden erst nach Neustart aktiv"),
                configManager.getLangMessage("config.broadcast.line6", "&c⚠══════════════════════════════════════⚠")
        };

        for (String line : broadcastLines) {
            player.sendMessage(line);
        }
    }

    public void sendMessage(Player player, String messagePath, String defaultValue) {
        player.sendMessage(configManager.getLangMessage(messagePath, defaultValue));
    }

    // KORRIGIERT: Zwei broadcastToOps Methoden für beide Aufrufarten
    public void broadcastToOps(String messagePath) {
        // Ein Parameter: Default-Wert aus der Config
        String message = configManager.getLangMessage(messagePath,
                "&cMissing translation: " + messagePath);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.isOp() || hasAdminPermission(player)) {
                player.sendMessage(message);
            }
        }
    }

    public void broadcastToOps(String messagePath, String defaultValue) {
        // Zwei Parameter: Mit eigenem Default-Wert
        String message = configManager.getLangMessage(messagePath, defaultValue);
        for (Player player : plugin.getServer().getOnlinePlayers()) {
            if (player.isOp() || hasAdminPermission(player)) {
                player.sendMessage(message);
            }
        }
    }

    public boolean hasNotifyPermission(Player player) {
        return player.hasPermission(PERM_NOTIFY) || player.hasPermission(PERM_ADMIN) || player.isOp();
    }

    public boolean hasAdminPermission(Player player) {
        return player.hasPermission(PERM_ADMIN) || player.isOp();
    }
}