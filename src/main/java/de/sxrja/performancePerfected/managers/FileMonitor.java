package de.sxrja.performancePerfected.managers;


import de.sxrja.performancePerfected.utils.HashUtil;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import java.io.File;
import java.util.*;

public class FileMonitor {

    private JavaPlugin plugin;
    private ConfigManager configManager;
    private NotificationManager notificationManager;
    private Map<String, String> fileHashes = new HashMap<>();
    private boolean configChanged = false;
    private int notificationTaskId = -1;
    private int monitoringTaskId = -1;

    public FileMonitor(JavaPlugin plugin, ConfigManager configManager, NotificationManager notificationManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.notificationManager = notificationManager;
    }

    public void startMonitoring() {
        initializeFileMonitoring();

        // Monitoring-Task: Alle 60 Sekunden (1200 Ticks)
        monitoringTaskId = new BukkitRunnable() {
            @Override
            public void run() {
                checkForConfigChanges();
            }
        }.runTaskTimer(plugin, 1200L, 1200L).getTaskId();
    }

    private void initializeFileMonitoring() {
        String[] monitoredFiles = {
                "config.yml",
                "advanced-config.yml",
                "server.properties",
                "paper-world-defaults.yml",
                "spigot.yml"
        };

        for (String fileName : monitoredFiles) {
            File file = new File(plugin.getDataFolder().getParentFile().getParentFile(), fileName);
            if (file.exists()) {
                fileHashes.put(fileName, HashUtil.calculateFileHash(file));
            }
        }

        // Plugin eigene Configs
        fileHashes.put("plugin-config.yml", HashUtil.calculateFileHash(new File(plugin.getDataFolder(), "config.yml")));
        fileHashes.put("plugin-advanced.yml", HashUtil.calculateFileHash(new File(plugin.getDataFolder(), "advanced-config.yml")));
    }

    private void checkForConfigChanges() {
        boolean changesDetected = false;
        List<String> changedFiles = new ArrayList<>();

        for (Map.Entry<String, String> entry : fileHashes.entrySet()) {
            String fileName = entry.getKey();
            String oldHash = entry.getValue();

            File file = getFileFromName(fileName);

            if (file.exists()) {
                String newHash = HashUtil.calculateFileHash(file);
                if (!newHash.equals(oldHash)) {
                    changesDetected = true;
                    changedFiles.add(fileName);
                    fileHashes.put(fileName, newHash);

                    // Log-Eintrag
                    String fileChangedMsg = configManager.getLangMessage("config.file-changed",
                            "&7Datei geändert: &f{FILE}").replace("{FILE}", fileName);
                    plugin.getLogger().warning(configManager.stripColor(fileChangedMsg));
                }
            }
        }

        if (changesDetected && !configChanged) {
            configChanged = true;
            notificationManager.onConfigChanged(changedFiles);

            if (notificationTaskId == -1) {
                startNotificationBroadcast();
            }
        }
    }

    private File getFileFromName(String fileName) {
        switch (fileName) {
            case "plugin-config.yml":
                return new File(plugin.getDataFolder(), "config.yml");
            case "plugin-advanced.yml":
                return new File(plugin.getDataFolder(), "advanced-config.yml");
            default:
                return new File(plugin.getDataFolder().getParentFile().getParentFile(), fileName);
        }
    }

    private void startNotificationBroadcast() {
        notificationTaskId = new BukkitRunnable() {
            private int runs = 0;
            private final int maxRuns = 30;

            @Override
            public void run() {
                if (runs >= maxRuns || !configChanged) {
                    stopNotificationBroadcast();
                    return;
                }

                notificationManager.broadcastConfigChange();
                runs++;
            }
        }.runTaskTimer(plugin, 0L, 200L).getTaskId(); // 10 Sekunden
    }

    public void stopNotificationBroadcast() {
        if (notificationTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(notificationTaskId);
            notificationTaskId = -1;
            configChanged = false;
        }
    }

    public void stopAllTasks() {
        stopNotificationBroadcast();
        if (monitoringTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(monitoringTaskId);
        }
    }

    public void resetConfigChanged() {
        configChanged = false;
        stopNotificationBroadcast();
    }

    // Getter/Setter
    public boolean isConfigChanged() { return configChanged; }
}