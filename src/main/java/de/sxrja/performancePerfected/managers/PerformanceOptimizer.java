package de.sxrja.performancePerfected.managers;

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
    private YamlConfiguration activeConfig;

    // Konstante für benötigte Neustarts
    private boolean restartRequired = false;

    public PerformanceOptimizer(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;

        // WICHTIG: activeConfig muss NACH dem Laden der Configs gesetzt werden
        // In der Hauptklasse wird PerformanceOptimizer NACH configManager.loadConfigs() erstellt
        // Daher sollte activeConfig hier nicht null sein
        this.activeConfig = configManager.getActiveConfig();

        // Sicherheitsprüfung
        if (this.activeConfig == null) {
            plugin.getLogger().warning("WARNING: Active config is null! Using default config instead.");
            this.activeConfig = configManager.getConfig();
        }

        // Nochmalige Sicherheitsprüfung
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

        // Debug-Info
        plugin.getLogger().info("Active config loaded: " + (activeConfig != null));
        if (activeConfig != null) {
            plugin.getLogger().info("Config keys: " + activeConfig.getKeys(false).size());
        }

        // Alle Optimierungsbereiche durchgehen
        optimizeServerProperties();
        optimizePaperConfig();
        optimizeSpigotConfig();
        optimizeBukkitConfig();

        // Überprüfen ob Neustart benötigt wird
        if (restartRequired) {
            String restartMsg = configManager.getLangMessage("config.restart-required",
                    "&e⚠ &6Restart required for some optimizations!");
            plugin.getLogger().warning(configManager.stripColor(restartMsg));
        }

        startEmergencyMonitor();
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

            // View-Distance optimieren - MIT NULL-SICHERHEIT
            int viewDistance = 8; // Standardwert
            if (activeConfig != null && activeConfig.contains("view-distance")) {
                viewDistance = activeConfig.getInt("view-distance", 8);
            }
            if (setPropertyIfDifferent(props, "view-distance", String.valueOf(Math.max(2, Math.min(32, viewDistance))))) {
                changed = true;
            }

            // Simulation-Distance optimieren - MIT NULL-SICHERHEIT
            int simulationDistance = 6; // Standardwert
            if (activeConfig != null && activeConfig.contains("simulation-distance")) {
                simulationDistance = activeConfig.getInt("simulation-distance", 6);
            }
            if (setPropertyIfDifferent(props, "simulation-distance", String.valueOf(Math.max(2, Math.min(32, simulationDistance))))) {
                changed = true;
            }

            // Network-Compression optimieren - MIT NULL-SICHERHEIT
            int compressionThreshold = 256; // Standardwert
            if (activeConfig != null && activeConfig.contains("network.compression-threshold")) {
                compressionThreshold = activeConfig.getInt("network.compression-threshold", 256);
            }
            if (setPropertyIfDifferent(props, "network-compression-threshold", String.valueOf(compressionThreshold))) {
                changed = true;
            }

            // Max Players limitieren - MIT NULL-SICHERHEIT
            int maxPlayers = 20; // Standardwert
            if (activeConfig != null && activeConfig.contains("max-players")) {
                maxPlayers = activeConfig.getInt("max-players", 20);
            }
            if (setPropertyIfDifferent(props, "max-players", String.valueOf(Math.max(1, Math.min(1000, maxPlayers))))) {
                changed = true;
            }

            if (changed) {
                // Backup erstellen
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

            // Entity Activation Range optimieren - MIT NULL-SICHERHEIT
            if (activeConfig != null && activeConfig.contains("entity.activation-range")) {
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

            // Mob-Spawn Limits optimieren - MIT NULL-SICHERHEIT
            if (activeConfig != null && activeConfig.contains("spawn-limits")) {
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

            // Despawn Ranges optimieren - MIT NULL-SICHERHEIT
            if (activeConfig != null && activeConfig.contains("despawn-ranges")) {
                paperYaml.set("despawn-ranges.soft",
                        activeConfig.getInt("despawn-ranges.soft", 32));
                paperYaml.set("despawn-ranges.hard",
                        activeConfig.getInt("despawn-ranges.hard", 128));
                changed = true;
            }

            // Redstone Optimierungen - MIT NULL-SICHERHEIT
            if (activeConfig != null && activeConfig.contains("redstone")) {
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
            } else {
                plugin.getLogger().info("✓ Paper config already optimized");
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

            // Entity Tracking Range optimieren - MIT NULL-SICHERHEIT
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

            // Mob Spawn Range optimieren - MIT NULL-SICHERHEIT
            if (setYamlIfDifferent(spigotYaml, "world-settings.default.mob-spawn-range", 6)) {
                changed = true;
            }

            if (changed) {
                createBackup(spigotConfig);
                spigotYaml.save(spigotConfig);
                plugin.getLogger().info("✓ Spigot config optimized");
                restartRequired = true;
            } else {
                plugin.getLogger().info("✓ Spigot config already optimized");
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

            // Spawn Limits optimieren - MIT NULL-SICHERHEIT
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

            // Chunk GC optimieren - MIT NULL-SICHERHEIT
            if (setYamlIfDifferent(bukkitYaml, "chunk-gc.period-in-ticks", 600)) {
                changed = true;
            }

            if (changed) {
                createBackup(bukkitConfig);
                bukkitYaml.save(bukkitConfig);
                plugin.getLogger().info("✓ Bukkit config optimized");
                restartRequired = true;
            } else {
                plugin.getLogger().info("✓ Bukkit config already optimized");
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

            // Hier können spezifische erweiterte Optimierungen hinzugefügt werden
            // Beispiel: Welt-spezifische Einstellungen

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
            // Beispiel: Entity-Grenzen pro Welt setzen
            if (activeConfig != null) {
                world.setMonsterSpawnLimit(activeConfig.getInt("spawn-limits.monsters", 30));
                world.setAnimalSpawnLimit(activeConfig.getInt("spawn-limits.animals", 15));
                world.setWaterAnimalSpawnLimit(activeConfig.getInt("spawn-limits.water-animals", 5));
                world.setAmbientSpawnLimit(activeConfig.getInt("spawn-limits.ambient", 2));
            }

        } catch (Exception e) {
            plugin.getLogger().warning("Could not optimize world " + world.getName() + ": " + e.getMessage());
        }
    }

    /**
     * NOTFALL-ÜBERWACHUNG STARTEN
     */
    private void startEmergencyMonitor() {
        new BukkitRunnable() {
            @Override
            public void run() {
                double currentTps = Bukkit.getTPS()[0];
                double threshold = 15.0; // Standardwert

                // MIT NULL-SICHERHEIT
                if (activeConfig != null && activeConfig.contains("emergency.tps-threshold")) {
                    threshold = activeConfig.getDouble("emergency.tps-threshold", 15.0);
                }

                if (currentTps < threshold) {
                    triggerEmergencyProtocol(currentTps);
                }

                // Automatische Aufräumroutinen - MIT NULL-SICHERHEIT
                boolean cleanupEnabled = false;
                if (activeConfig != null && activeConfig.contains("cleanup.enabled")) {
                    cleanupEnabled = activeConfig.getBoolean("cleanup.enabled", false);
                }

                if (cleanupEnabled) {
                    runCleanupRoutines();
                }
            }
        }.runTaskTimer(plugin, 100L, 100L); // Alle 5 Sekunden
    }

    /**
     * NOTFALLPROTOKOLL AUSLÖSEN
     */
    private void triggerEmergencyProtocol(double currentTps) {
        String tpsWarning = configManager.getLangMessage("emergency.tps-low-warning",
                        "&c⚠ &6Warning: &fServer TPS is critically low (&e{TPS}&f/20)")
                .replace("{TPS}", String.format("%.1f", currentTps));

        plugin.getLogger().warning(configManager.stripColor(tpsWarning));

        boolean killEntities = true; // Standardwert
        if (activeConfig != null && activeConfig.contains("emergency.kill-non-player-entities")) {
            killEntities = activeConfig.getBoolean("emergency.kill-non-player-entities", true);
        }

        if (killEntities) {
            killAllNonPlayerEntities();
        }
    }

    /**
     * ALLE NICHT-SPIELER-ENTITIES ENTFERNEN
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

        // Log-Ausgabe mit Details
        String logMessage = String.format("Emergency cleanup: %d entities removed", totalRemoved);
        if (!removedCounts.isEmpty()) {
            logMessage += " - " + removedCounts.entrySet().stream()
                    .map(e -> e.getKey() + ": " + e.getValue())
                    .collect(Collectors.joining(", "));
        }
        plugin.getLogger().info(logMessage);

        // Chat-Nachricht an Ops
        String broadcastMsg = configManager.getLangMessage("emergency.broadcast-warning",
                "&c⚠ &6Warning: &fServer performance critical. Cleanup in progress...");
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.isOp() || player.hasPermission("performanceperfected.notify")) {
                player.sendMessage(broadcastMsg);
            }
        }
    }

    /**
     * AUTOMATISCHE AUFRÄUMROUTINEN
     */
    private void runCleanupRoutines() {
        int groundItemsRemoved = 0;
        int vehiclesRemoved = 0;

        boolean removeGroundItems = true; // Standardwert
        boolean removeInactiveVehicles = true; // Standardwert

        if (activeConfig != null) {
            if (activeConfig.contains("cleanup.remove-ground-items")) {
                removeGroundItems = activeConfig.getBoolean("cleanup.remove-ground-items", true);
            }
            if (activeConfig.contains("cleanup.remove-inactive-vehicles")) {
                removeInactiveVehicles = activeConfig.getBoolean("cleanup.remove-inactive-vehicles", true);
            }
        }

        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (entity instanceof Item && removeGroundItems) {
                    entity.remove();
                    groundItemsRemoved++;
                } else if ((entity instanceof Boat || entity instanceof Minecart) && removeInactiveVehicles) {
                    // Überprüfe ob Fahrzeug leer ist
                    if (entity.getPassengers().isEmpty()) {
                        entity.remove();
                        vehiclesRemoved++;
                    }
                }
            }
        }

        if (groundItemsRemoved > 0 || vehiclesRemoved > 0) {
            plugin.getLogger().info(String.format("Cleanup: %d items, %d vehicles removed",
                    groundItemsRemoved, vehiclesRemoved));
        }
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

            plugin.getLogger().info("Backup created: " + backupFile.getName());

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

    public void forceCleanup() {
        runCleanupRoutines();
    }
}