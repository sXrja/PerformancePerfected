package de.sxrja.performancePerfected.managers;

import de.sxrja.performancePerfected.PerformancePerfected;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

public class PerformanceOptimizer {

    private JavaPlugin plugin;
    private ConfigManager configManager;
    private NotificationManager notificationManager;
    private YamlConfiguration activeConfig;
    private LazyChunkManager lazyChunkManager;

    private boolean restartRequired = false;

    // Adaptive Clearing System
    private BukkitRunnable adaptiveCleanupTask = null;
    private boolean isAdaptiveCleanupRunning = false;
    private int adaptiveCleanupTaskId = -1;

    // Timers für verschiedene Funktionen
    private BukkitRunnable emergencyMonitorTask = null;
    private BukkitRunnable adaptiveMonitorTask = null;

    public PerformanceOptimizer(JavaPlugin plugin, ConfigManager configManager, NotificationManager notificationManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.notificationManager = notificationManager;
        this.lazyChunkManager = new LazyChunkManager(plugin, configManager);

        this.activeConfig = configManager.getActiveConfig();

        if (this.activeConfig == null) {
            plugin.getLogger().warning("WARNING: Active config is null! Using default config instead.");
            this.activeConfig = configManager.getConfig();
        }

        if (this.activeConfig == null) {
            plugin.getLogger().severe("CRITICAL ERROR: Could not load any configuration!");
            throw new IllegalStateException("No configuration loaded!");
        }
    }

    /**
     * Hauptmethode zum Anwenden aller Optimierungen
     */
    public void applyConfiguration() {
        String applyingMsg = configManager.getLangMessage("plugin.applying-settings",
                "&7[Performance] &fApplying optimizations...");
        plugin.getLogger().info(configManager.stripColor(applyingMsg));

        // Alle Optimierungsbereiche durchgehen
        optimizeServerProperties();
        optimizePaperConfig();
        optimizeSpigotConfig();
        optimizeBukkitConfig();
        optimizeLazyChunks();

        if (restartRequired) {
            String restartMsg = configManager.getLangMessage("config.restart-required",
                    "&e⚠ &6Restart required for some optimizations!");
            plugin.getLogger().warning(configManager.stripColor(restartMsg));
        }

        startEmergencyMonitor();
        startAdaptiveCleanupMonitor();
    }
    private void optimizeLazyChunks() {
        if (configManager.isLazyChunksEnabled()) {
            lazyChunkManager.start();

            String lazyMsg = configManager.getLangMessage("lazy-chunks.enabled",
                    "&7[Performance] &fLazy Chunks aktiviert: &e{DISTANCE} &7Chunks normal, restlich exponentiell langsamer"
            ).replace("{DISTANCE}", String.valueOf(configManager.getLazyChunksDistance()));

            plugin.getLogger().info(configManager.stripColor(lazyMsg));

            if (configManager.isAdaptiveLaziness()) {
                plugin.getLogger().info("✓ Adaptive Lazyness aktiviert (reduziert bei niedrigen TPS)");
            }
        }
    }

    /**
     * 1. SERVER.PROPERTIES OPTIMIERUNGEN
     */
    private void optimizeServerProperties() {
        try {
            File serverProps = new File(plugin.getServer().getWorldContainer(), "server.properties");
            Properties props = new Properties();
            props.load(new FileInputStream(serverProps));

            boolean changed = false;

            // View-Distance optimieren
            int viewDistance = activeConfig.getInt("view-distance", 8);
            if (setPropertyIfDifferent(props, "view-distance", String.valueOf(Math.max(2, Math.min(32, viewDistance))))) {
                changed = true;
            }

            // Simulation-Distance optimieren
            int simulationDistance = activeConfig.getInt("simulation-distance", 6);
            if (setPropertyIfDifferent(props, "simulation-distance", String.valueOf(Math.max(2, Math.min(32, simulationDistance))))) {
                changed = true;
            }

            // Network-Compression optimieren
            int compressionThreshold = activeConfig.getInt("network.compression-threshold", 256);
            if (setPropertyIfDifferent(props, "network-compression-threshold", String.valueOf(compressionThreshold))) {
                changed = true;
            }

            // Max Players limitieren
            int maxPlayers = activeConfig.getInt("max-players", 20);
            if (setPropertyIfDifferent(props, "max-players", String.valueOf(Math.max(1, Math.min(1000, maxPlayers))))) {
                changed = true;
            }

            if (changed) {
                createBackup(serverProps);
                props.store(new FileOutputStream(serverProps), "Optimized by PerformancePerfected");
                plugin.getLogger().info("✓ server.properties optimized");
                restartRequired = true;
            } else {
                plugin.getLogger().info("✓ server.properties already optimized");
            }

        } catch (IOException e) {
            plugin.getLogger().warning("Could not optimize server.properties: " + e.getMessage());
        }
    }

    /**
     * 2. PAPER-WORLD-DEFAULTS.YML OPTIMIERUNGEN
     */
    private void optimizePaperConfig() {
        try {
            File paperConfig = new File(plugin.getServer().getWorldContainer(), "config/paper-world-defaults.yml");
            if (!paperConfig.exists()) {
                plugin.getLogger().warning("Paper config not found, skipping...");
                return;
            }

            YamlConfiguration paperYaml = YamlConfiguration.loadConfiguration(paperConfig);
            boolean changed = false;

            if (activeConfig.contains("entity.activation-range")) {
                paperYaml.set("entity-activation-range.animals",
                        activeConfig.getInt("entity.activation-range.animals", 20));
                paperYaml.set("entity-activation-range.monsters",
                        activeConfig.getInt("entity.activation-range.monsters", 24));
                paperYaml.set("entity-activation-range.misc",
                        activeConfig.getInt("entity.activation-range.misc", 8));
                paperYaml.set("entity-activation-range.tick-inactive-villagers",
                        activeConfig.getBoolean("entity.activation-range.tick-inactive-villagers", false));
                changed = true;
            }

            if (activeConfig.contains("spawn-limits")) {
                paperYaml.set("spawn-limits.monsters",
                        activeConfig.getInt("spawn-limits.monsters", 30));
                paperYaml.set("spawn-limits.animals",
                        activeConfig.getInt("spawn-limits.animals", 15));
                paperYaml.set("spawn-limits.water-animals",
                        activeConfig.getInt("spawn-limits.water-animals", 5));
                paperYaml.set("spawn-limits.ambient",
                        activeConfig.getInt("spawn-limits.ambient", 2));
                changed = true;
            }

            if (activeConfig.contains("despawn-ranges")) {
                paperYaml.set("despawn-ranges.soft",
                        activeConfig.getInt("despawn-ranges.soft", 32));
                paperYaml.set("despawn-ranges.hard",
                        activeConfig.getInt("despawn-ranges.hard", 128));
                changed = true;
            }

            if (activeConfig.contains("redstone")) {
                paperYaml.set("redstone.disable-falling-dust",
                        activeConfig.getBoolean("redstone.disable-falling-dust", false));
                paperYaml.set("redstone.disable-item-frame-glow",
                        activeConfig.getBoolean("redstone.disable-item-frame-glow", false));
                changed = true;
            }

            if (changed) {
                createBackup(paperConfig);
                paperYaml.save(paperConfig);
                plugin.getLogger().info("✓ Paper config optimized");
                restartRequired = true;
            }

        } catch (IOException e) {
            plugin.getLogger().warning("Could not optimize Paper config: " + e.getMessage());
        }
    }

    /**
     * 3. SPIGOT.YML OPTIMIERUNGEN
     */
    private void optimizeSpigotConfig() {
        try {
            File spigotConfig = new File(plugin.getServer().getWorldContainer(), "spigot.yml");
            if (!spigotConfig.exists()) {
                plugin.getLogger().warning("Spigot config not found, skipping...");
                return;
            }

            YamlConfiguration spigotYaml = YamlConfiguration.loadConfiguration(spigotConfig);
            boolean changed = false;

            if (setYamlIfDifferent(spigotYaml, "world-settings.default.entity-tracking-range.players", 48)) {
                changed = true;
            }
            if (setYamlIfDifferent(spigotYaml, "world-settings.default.entity-tracking-range.animals", 32)) {
                changed = true;
            }
            if (setYamlIfDifferent(spigotYaml, "world-settings.default.entity-tracking-range.monsters", 32)) {
                changed = true;
            }
            if (setYamlIfDifferent(spigotYaml, "world-settings.default.entity-tracking-range.misc", 16)) {
                changed = true;
            }

            if (setYamlIfDifferent(spigotYaml, "world-settings.default.mob-spawn-range", 6)) {
                changed = true;
            }

            if (changed) {
                createBackup(spigotConfig);
                spigotYaml.save(spigotConfig);
                plugin.getLogger().info("✓ Spigot config optimized");
                restartRequired = true;
            }

        } catch (IOException e) {
            plugin.getLogger().warning("Could not optimize Spigot config: " + e.getMessage());
        }
    }

    /**
     * 4. BUKKIT.YML OPTIMIERUNGEN
     */
    private void optimizeBukkitConfig() {
        try {
            File bukkitConfig = new File(plugin.getServer().getWorldContainer(), "bukkit.yml");
            if (!bukkitConfig.exists()) {
                plugin.getLogger().warning("Bukkit config not found, skipping...");
                return;
            }

            YamlConfiguration bukkitYaml = YamlConfiguration.loadConfiguration(bukkitConfig);
            boolean changed = false;

            if (setYamlIfDifferent(bukkitYaml, "spawn-limits.monsters", 30)) {
                changed = true;
            }
            if (setYamlIfDifferent(bukkitYaml, "spawn-limits.animals", 15)) {
                changed = true;
            }
            if (setYamlIfDifferent(bukkitYaml, "spawn-limits.water-animals", 5)) {
                changed = true;
            }
            if (setYamlIfDifferent(bukkitYaml, "spawn-limits.ambient", 2)) {
                changed = true;
            }

            if (setYamlIfDifferent(bukkitYaml, "chunk-gc.period-in-ticks", 600)) {
                changed = true;
            }

            if (changed) {
                createBackup(bukkitConfig);
                bukkitYaml.save(bukkitConfig);
                plugin.getLogger().info("✓ Bukkit config optimized");
                restartRequired = true;
            }

        } catch (IOException e) {
            plugin.getLogger().warning("Could not optimize Bukkit config: " + e.getMessage());
        }
    }

    /**
     * ERWEITERTE KONFIGURATION ANWENDEN
     */
    private void applyAdvancedConfiguration() {
        try {
            plugin.getLogger().info("Applying advanced optimizations...");

            for (World world : Bukkit.getWorlds()) {
                optimizeWorldSpecificSettings(world);
            }

            plugin.getLogger().info("Advanced optimizations applied");

        } catch (Exception e) {
            plugin.getLogger().warning("Error applying advanced optimizations: " + e.getMessage());
        }
    }

    /**
     * WELT-SPEZIFISCHE EINSTELLUNGEN OPTIMIEREN
     */
    private void optimizeWorldSpecificSettings(World world) {
        try {
            world.setMonsterSpawnLimit(activeConfig.getInt("spawn-limits.monsters", 30));
            world.setAnimalSpawnLimit(activeConfig.getInt("spawn-limits.animals", 15));
            world.setWaterAnimalSpawnLimit(activeConfig.getInt("spawn-limits.water-animals", 5));
            world.setAmbientSpawnLimit(activeConfig.getInt("spawn-limits.ambient", 2));

        } catch (Exception e) {
            plugin.getLogger().warning("Could not optimize world " + world.getName() + ": " + e.getMessage());
        }
    }

    /**
     * NOTFALL-ÜBERWACHUNG STARTEN (TPS-Check alle 5 Sekunden)
     */
    private void startEmergencyMonitor() {
        emergencyMonitorTask = new BukkitRunnable() {
            @Override
            public void run() {
                double currentTps = Bukkit.getTPS()[0];
                double threshold = activeConfig.getDouble("emergency.tps-threshold", 15.0);

                if (currentTps < threshold) {
                    triggerEmergencyProtocol(currentTps);
                }
            }
        };
        emergencyMonitorTask.runTaskTimer(plugin, 100L, 100L); // Alle 5 Sekunden
    }

    /**
     * ADAPTIVE CLEANUP MONITOR STARTEN (prüft alle 10 Sekunden)
     */
    private void startAdaptiveCleanupMonitor() {
        adaptiveMonitorTask = new BukkitRunnable() {
            @Override
            public void run() {
                double currentTps = Bukkit.getTPS()[0];
                boolean adaptiveClearingEnabled = activeConfig.getBoolean("cleanup.adaptive-clearing", true);

                if (!adaptiveClearingEnabled) {
                    // Normales Cleanup mit fester Zeit
                    if (activeConfig.getBoolean("cleanup.enabled", false)) {
                        runScheduledCleanup();
                    }
                    return;
                }

                // Adaptive Logik
                double adaptiveThreshold = activeConfig.getDouble("cleanup.adaptive-tps-threshold", 17.0);

                if (currentTps < adaptiveThreshold) {
                    // TPS zu niedrig - Starte Cleanup Timer wenn nicht bereits läuft
                    if (!isAdaptiveCleanupRunning) {
                        startAdaptiveCleanupTimer();
                        plugin.getLogger().info("⚠ Adaptive cleanup activated (TPS: " + String.format("%.1f", currentTps) + ")");
                    }
                } else {
                    // TPS normal - Stoppe Cleanup Timer wenn läuft
                    if (isAdaptiveCleanupRunning) {
                        stopAdaptiveCleanupTimer();
                        plugin.getLogger().info("✓ Adaptive cleanup deactivated (TPS: " + String.format("%.1f", currentTps) + ")");
                    }
                }
            }
        };
        adaptiveMonitorTask.runTaskTimer(plugin, 100L, 200L); // Prüft alle 10 Sekunden
    }

    /**
     * ADAPTIVE CLEANUP TIMER STARTEN (mit Countdown)
     */
    private void startAdaptiveCleanupTimer() {
        if (adaptiveCleanupTask != null) {
            stopAdaptiveCleanupTimer();
        }

        isAdaptiveCleanupRunning = true;

        // Countdown-Zeit in Sekunden aus Config
        int countdownSeconds = activeConfig.getInt("cleanup.countdown-seconds", 60);

        // Countdown für Spieler starten
        if (notificationManager != null) {
            notificationManager.startCleanupCountdown(countdownSeconds);
        }

        // Cleanup-Intervall in Sekunden aus Config lesen
        int intervalSeconds = activeConfig.getInt("cleanup.adaptive-interval-seconds", 30);
        long intervalTicks = intervalSeconds * 20L;
        long countdownTicks = countdownSeconds * 20L;

        // Task starten (nach Countdown)
        adaptiveCleanupTask = new BukkitRunnable() {
            @Override
            public void run() {
                runIntelligentCleanup();
            }
        };

        // Erst nach Countdown starten
        adaptiveCleanupTaskId = adaptiveCleanupTask.runTaskTimer(plugin, countdownTicks, intervalTicks).getTaskId();
    }

    /**
     * ADAPTIVE CLEANUP TIMER STOPPEN (mit Countdown-Abbruch)
     */
    private void stopAdaptiveCleanupTimer() {
        if (adaptiveCleanupTask != null) {
            try {
                adaptiveCleanupTask.cancel();
                if (adaptiveCleanupTaskId != -1) {
                    plugin.getServer().getScheduler().cancelTask(adaptiveCleanupTaskId);
                }
            } catch (Exception e) {
                // Ignorieren
            }
        }

        // Countdown auch stoppen
        if (notificationManager != null) {
            notificationManager.stopCleanupCountdown();
        }

        adaptiveCleanupTask = null;
        isAdaptiveCleanupRunning = false;
        adaptiveCleanupTaskId = -1;
    }

    /**
     * INTELLIGENTES CLEANUP (für adaptives Clearing)
     * Entfernt nur Items, die mindestens X Sekunden alt sind
     */
    private void runIntelligentCleanup() {
        int removedItems = 0;
        int removedVehicles = 0;
        int removedExperienceOrbs = 0;

        // Mindestalter für Items in Sekunden
        int minItemAgeSeconds = activeConfig.getInt("cleanup.min-item-age-seconds", 30);
        long minItemAgeTicks = minItemAgeSeconds * 20L;
        long currentTick = Bukkit.getCurrentTick();

        boolean removeGroundItems = activeConfig.getBoolean("cleanup.remove-ground-items", true);
        boolean removeInactiveVehicles = activeConfig.getBoolean("cleanup.remove-inactive-vehicles", true);
        boolean removeExperienceOrbs = activeConfig.getBoolean("cleanup.remove-experience-orbs", true);

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                // Nur Items, die älter als minItemAgeSeconds sind
                if (entity instanceof Item && removeGroundItems) {
                    if ((currentTick - entity.getTicksLived()) > minItemAgeTicks) {
                        entity.remove();
                        removedItems++;
                    }
                }
                // Fahrzeuge (nur wenn leer und alt genug)
                else if ((entity instanceof Boat || entity instanceof Minecart) && removeInactiveVehicles) {
                    if (entity.getPassengers().isEmpty() && (currentTick - entity.getTicksLived()) > minItemAgeTicks) {
                        entity.remove();
                        removedVehicles++;
                    }
                }
                // Erfahrungskugeln
                else if (entity instanceof ExperienceOrb && removeExperienceOrbs) {
                    if ((currentTick - entity.getTicksLived()) > minItemAgeTicks) {
                        entity.remove();
                        removedExperienceOrbs++;
                    }
                }
            }
        }

        if (removedItems > 0 || removedVehicles > 0 || removedExperienceOrbs > 0) {
            plugin.getLogger().info(String.format("🧹 Intelligent cleanup: %d items (age > %ds), %d vehicles, %d XP orbs",
                    removedItems, minItemAgeSeconds, removedVehicles, removedExperienceOrbs));

            // Erfolgsmeldung an Spieler
            if (notificationManager != null) {
                notificationManager.broadcastCleanupCompleted(removedItems, removedVehicles, removedExperienceOrbs);
            }
        }
    }

    /**
     * NORMALES SCHEDULED CLEANUP (nicht-adaptiv)
     */
    private void runScheduledCleanup() {
        // Cleanup-Intervall in Sekunden aus Config
        int intervalSeconds = activeConfig.getInt("cleanup.interval-seconds", 300);
        long intervalTicks = intervalSeconds * 20L;

        // Countdown starten wenn aktiv
        if (activeConfig.getBoolean("cleanup.countdown-enabled", true)) {
            int countdownSeconds = activeConfig.getInt("cleanup.countdown-seconds", 60);
            if (notificationManager != null) {
                notificationManager.startCleanupCountdown(countdownSeconds);
            }

            // Task nach Countdown starten
            new BukkitRunnable() {
                @Override
                public void run() {
                    runIntelligentCleanup();
                }
            }.runTaskLater(plugin, countdownSeconds * 20L);
        } else {
            // Sofort ausführen
            runIntelligentCleanup();
        }
    }

    /**
     * NOTFALLPROTOKOLL AUSLÖSEN
     */
    private void triggerEmergencyProtocol(double currentTps) {
        String tpsWarning = configManager.getLangMessage("emergency.tps-low-warning",
                        "&c⚠ &6Warning: &fServer TPS is critically low (&e{TPS}&f/20)")
                .replace("{TPS}", String.format("%.1f", currentTps));

        plugin.getLogger().warning(configManager.stripColor(tpsWarning));

        boolean killEntities = activeConfig.getBoolean("emergency.kill-non-player-entities", true);

        if (killEntities) {
            killAllNonPlayerEntities();
        }
    }

    /**
     * ALLE NICHT-SPIELER-ENTITIES ENTFERNEN (Notfall-Cleanup)
     */
    public void killAllNonPlayerEntities() {
        Map<String, Integer> removedCounts = new HashMap<>();
        int totalRemoved = 0;

        String activatedMsg = configManager.getLangMessage("emergency.emergency-activated",
                "&4🚨 &cEmergency protocol activated! &fRemoving non-player entities...");
        plugin.getLogger().warning(configManager.stripColor(activatedMsg));

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Player)) {
                    String typeName = entity.getType().toString();
                    removedCounts.put(typeName, removedCounts.getOrDefault(typeName, 0) + 1);
                    entity.remove();
                    totalRemoved++;
                }
            }
        }

        String logMessage = String.format("Emergency cleanup: %d entities removed", totalRemoved);
        if (!removedCounts.isEmpty()) {
            logMessage += " - " + removedCounts.entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue())
                    .collect(Collectors.joining(", "));
        }
        plugin.getLogger().info(logMessage);

        String broadcastMsg = configManager.getLangMessage("emergency.broadcast-warning",
                "&c⚠ &6Warning: &fServer performance critical. Cleanup in progress...");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp() || player.hasPermission("performanceperfected.notify")) {
                player.sendMessage(broadcastMsg);
            }
        }
    }

    /**
     * MANUELLES CLEANUP AUSFÜHREN
     */
    public void forceCleanup() {
        runIntelligentCleanup();
    }

    /**
     * ALLE TIMERS STOPPEN
     */
    public void stopAllTimers() {
        if (emergencyMonitorTask != null) {
            emergencyMonitorTask.cancel();
        }
        if (adaptiveMonitorTask != null) {
            adaptiveMonitorTask.cancel();
        }
        if (lazyChunkManager != null) {
            lazyChunkManager.stop();
        }
        stopAdaptiveCleanupTimer();
    }

    /**
     * HELFER-METHODEN
     */
    private boolean setPropertyIfDifferent(Properties props, String key, String newValue) {
        String oldValue = props.getProperty(key);
        if (!newValue.equals(oldValue)) {
            props.setProperty(key, newValue);
            return true;
        }
        return false;
    }

    private boolean setYamlIfDifferent(YamlConfiguration yaml, String path, Object defaultValue) {
        Object currentValue = yaml.get(path);
        if (currentValue == null || !currentValue.equals(defaultValue)) {
            yaml.set(path, defaultValue);
            return true;
        }
        return false;
    }

    private void createBackup(File originalFile) {
        try {
            File backupFile = new File(originalFile.getParentFile(),
                    originalFile.getName() + ".backup-" + System.currentTimeMillis());

            try (InputStream in = new FileInputStream(originalFile);
                 OutputStream out = new FileOutputStream(backupFile)) {
                byte[] buffer = new byte[1024];
                int length;
                while ((length = in.read(buffer)) > 0) {
                    out.write(buffer, 0, length);
                }
            }

        } catch (IOException e) {
            plugin.getLogger().warning("Could not create backup for " + originalFile.getName() + ": " + e.getMessage());
        }
    }

    /**
     * GETTER FÜR EXTERNE NUTZUNG
     */
    public boolean isRestartRequired() {
        return restartRequired;
    }

    public boolean isAdaptiveCleanupRunning() {
        return isAdaptiveCleanupRunning;
    }

    public LazyChunkManager getLazyChunkManager() {
        return lazyChunkManager;
    }

    public void setLazyChunkManager(LazyChunkManager lazyChunkManager) {
        this.lazyChunkManager = lazyChunkManager;
    }
}