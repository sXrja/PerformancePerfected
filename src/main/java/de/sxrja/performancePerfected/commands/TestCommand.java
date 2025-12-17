package de.sxrja.performancePerfected.commands;

import de.sxrja.performancePerfected.PerformancePerfected;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class TestCommand implements CommandExecutor {

    private final PerformancePerfected plugin;

    public TestCommand(PerformancePerfected plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("performanceperfected.admin")) {
            sender.sendMessage("§cNo permission!");
            return true;
        }

        if (args.length == 0) {
            sender.sendMessage("§6⚡ PerformancePerfected Test Commands:");
            sender.sendMessage("§e/pptest tps §7- Simulate low TPS");
            sender.sendMessage("§e/pptest cleanup §7- Manual cleanup");
            sender.sendMessage("§e/pptest notify §7- Test notifications");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "tps":
                plugin.getPerformanceOptimizer().killAllNonPlayerEntities();
                sender.sendMessage("§aEmergency cleanup triggered!");
                break;

            case "cleanup":
                plugin.getPerformanceOptimizer().forceCleanup();
                sender.sendMessage("§aManual cleanup executed!");
                break;

            case "notify":
                // Jetzt mit der korrekten ein-Parameter Methode
                plugin.getNotificationManager().broadcastToOps("config.change-detected");
                sender.sendMessage("§aTest notification sent!");
                break;
        }

        return true;
    }
}