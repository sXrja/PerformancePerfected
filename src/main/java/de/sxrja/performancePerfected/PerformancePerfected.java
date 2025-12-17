package de.sxrja.performancePerfected;

import de.sxrja.performancePerfected.commands.TestCommand;
import de.sxrja.performancePerfected.managers.ConfigManager;
import de.sxrja.performancePerfected.managers.FileMonitor;
import de.sxrja.performancePerfected.managers.NotificationManager;
import de.sxrja.performancePerfected.managers.PerformanceOptimizer;
import org.bukkit.plugin.java.JavaPlugin;

public final class PerformancePerfected extends JavaPlugin {

    private ConfigManager configManager;
    private FileMonitor fileMonitor;
    private NotificationManager notificationManager;
    private PerformanceOptimizer performanceOptimizer;

    @Override
    public void onEnable() {
        // Manager initialisieren
        configManager = new ConfigManager(this);
        configManager.loadConfigs();
        notificationManager = new NotificationManager(this, configManager);
        performanceOptimizer = new PerformanceOptimizer(this, configManager);
        fileMonitor = new FileMonitor(this, configManager, notificationManager);

        // Einstellungen anwenden
        performanceOptimizer.applyConfiguration();

        // Überwachung starten
        fileMonitor.startMonitoring();

        // Commands registrieren
        getCommand("performanceperfected").setExecutor(new CommandHandler(this, configManager, notificationManager, performanceOptimizer));
        getCommand("pptest").setExecutor(new TestCommand(this));

        getLogger().info("PerformancePlugin succesfully loaded!");
    }

    @Override
    public void onDisable() {
        if (fileMonitor != null) {
            fileMonitor.stopAllTasks();
        }
        getLogger().info("PerformancePlugin stopped. (for Devs: this is the clean stop from the plugin itself)");
    }

    // Getter für Manager
    public ConfigManager getConfigManager() { return configManager; }
    public FileMonitor getFileMonitor() { return fileMonitor; }
    public NotificationManager getNotificationManager() { return notificationManager; }
    public PerformanceOptimizer getPerformanceOptimizer() { return performanceOptimizer; }
}