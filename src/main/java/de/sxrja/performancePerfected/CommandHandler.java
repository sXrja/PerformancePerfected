package de.sxrja.performancePerfected;


import de.sxrja.performancePerfected.managers.ConfigManager;
import de.sxrja.performancePerfected.managers.FileMonitor;
import de.sxrja.performancePerfected.managers.NotificationManager;
import de.sxrja.performancePerfected.managers.PerformanceOptimizer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class CommandHandler implements CommandExecutor {

    private JavaPlugin plugin;
    private ConfigManager configManager;
    private NotificationManager notificationManager;
    private PerformanceOptimizer performanceOptimizer;
    private FileMonitor fileMonitor;

    public CommandHandler(JavaPlugin plugin, ConfigManager configManager,
                          NotificationManager notificationManager,
                          PerformanceOptimizer performanceOptimizer) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.notificationManager = notificationManager;
        this.performanceOptimizer = performanceOptimizer;
        this.fileMonitor = plugin.getPlugin(PerformancePerfected.class).getFileMonitor();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender);
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "reload":
                return handleReload(sender);
            case "status":
                return handleStatus(sender);
            case "emergency":
                return handleEmergency(sender);
            case "cleanup":
                return handleCleanup(sender);
            case "monitor":
                return handleMonitor(sender, args);
            default:
                sendHelp(sender);
                return true;
        }
    }

    private boolean handleReload(CommandSender sender) {
        if (!notificationManager.hasAdminPermission((sender instanceof Player) ? (Player) sender : null)) {
            sender.sendMessage(configManager.getLangMessage("errors.no-permission", "&c❌ Keine Berechtigung"));
            return true;
        }

        configManager.reloadConfigs();
        performanceOptimizer.applyConfiguration();
        if (fileMonitor != null) {
            fileMonitor.resetConfigChanged();
        }

        sender.sendMessage(configManager.getLangMessage("plugin.config-reloaded", "&a✅ Konfiguration neu geladen"));
        return true;
    }

    private boolean handleStatus(CommandSender sender) {
        double[] tps = org.bukkit.Bukkit.getTPS();
        sender.sendMessage("§6⚡ §ePerformance Status:");
        sender.sendMessage("§7TPS (1m/5m/15m): §f" + String.format("%.2f", tps[0]) + "§7/§f" +
                String.format("%.2f", tps[1]) + "§7/§f" + String.format("%.2f", tps[2]));
        sender.sendMessage("§7Config-Modus: §f" + (configManager.useAdvancedConfig() ? "Erweitert" : "Einfach"));
        return true;
    }

    private boolean handleEmergency(CommandSender sender) {
        if (!notificationManager.hasAdminPermission((sender instanceof Player) ? (Player) sender : null)) {
            sender.sendMessage(configManager.getLangMessage("errors.no-permission", "&c❌ Keine Berechtigung"));
            return true;
        }

        performanceOptimizer.killAllNonPlayerEntities();
        sender.sendMessage("§a✅ Notfallbereinigung manuell ausgelöst");
        return true;
    }

    private boolean handleCleanup(CommandSender sender) {
        // Implementierung für manuelles Aufräumen
        sender.sendMessage("§7[Performance] §fManuelles Aufräumen gestartet");
        return true;
    }

    private boolean handleMonitor(CommandSender sender, String[] args) {
        if (args.length > 1 && args[1].equalsIgnoreCase("reset") && fileMonitor != null) {
            fileMonitor.resetConfigChanged();
            sender.sendMessage("§a✅ Monitoring-Status zurückgesetzt");
        }
        return true;
    }

    private void sendHelp(CommandSender sender) {
        String[] helpLines = {
                configManager.getLangMessage("plugin.help.title", "&6⚡ PerformancePlugin Help"),
                configManager.getLangMessage("plugin.help.reload", "&e/pp reload &7- Konfiguration neu laden"),
                configManager.getLangMessage("plugin.help.emergency", "&e/pp emergency &7- Notfall-Lösung manuell auslösen"),
                configManager.getLangMessage("plugin.help.status", "&e/pp status &7- Aktuelle Performance anzeigen"),
                configManager.getLangMessage("plugin.help.cleanup", "&e/pp cleanup &7- Manuelles Aufräumen")
        };

        for (String line : helpLines) {
            sender.sendMessage(line);
        }
    }
}