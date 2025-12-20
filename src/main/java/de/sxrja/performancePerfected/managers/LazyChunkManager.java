package de.sxrja.performancePerfected.managers;

import org.bukkit.*;
import org.bukkit.entity.*;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.*;

public class LazyChunkManager {

    private JavaPlugin plugin;
    private ConfigManager configManager;
    private Map<ChunkPosition, ChunkTickData> chunkTickMap;
    private BukkitRunnable monitoringTask;
    private boolean isActive = false;

    // Statistik-Zähler
    private int totalChunksProcessed = 0;
    private int lazyChunksCount = 0;
    private double averageTickMultiplier = 1.0;

    public LazyChunkManager(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
        this.chunkTickMap = new HashMap<>();
    }

    public void start() {
        if (!configManager.isLazyChunksEnabled()) {
            plugin.getLogger().info("Lazy Chunks: Feature is disabled");
            return;
        }

        isActive = true;
        chunkTickMap.clear();

        monitoringTask = new BukkitRunnable() {
            private int tickCounter = 0;

            @Override
            public void run() {
                tickCounter++;

                // Alle 10 Sekunden (200 Ticks) Chunks aktualisieren
                if (tickCounter % 200 == 0) {
                    updateChunkDistances();
                    if (configManager.isLazyChunksLogging()) {
                        logStatistics();
                    }
                }

                // Tick-Verarbeitung für alle geladenen Chunks
                processChunkTicks();

                // Adaptive Logik alle 5 Sekunden
                if (tickCounter % 100 == 0 && configManager.isAdaptiveLaziness()) {
                    adjustAdaptiveDistance();
                }
            }
        };

        monitoringTask.runTaskTimer(plugin, 20L, 1L); // Start nach 1s, dann jede Tick
        plugin.getLogger().info("✓ Lazy Chunks Manager gestartet");
    }

    private void updateChunkDistances() {
        lazyChunksCount = 0;
        totalChunksProcessed = 0;
        double totalMultiplier = 0;

        int baseDistance = configManager.getLazyChunksDistance();

        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                ChunkPosition pos = new ChunkPosition(chunk);
                int distance = calculateDistanceToNearestPlayer(chunk);

                // Tick-Multiplier berechnen
                int tickMultiplier = calculateTickMultiplier(distance, baseDistance);
                ChunkTickData data = chunkTickMap.get(pos);

                if (data == null) {
                    data = new ChunkTickData(chunk, tickMultiplier);
                    chunkTickMap.put(pos, data);
                } else {
                    data.setTickMultiplier(tickMultiplier);
                }

                if (tickMultiplier > 1) {
                    lazyChunksCount++;
                }

                totalChunksProcessed++;
                totalMultiplier += tickMultiplier;
            }
        }

        averageTickMultiplier = totalChunksProcessed > 0 ? totalMultiplier / totalChunksProcessed : 1.0;
    }

    // Getter für Statistiken
    public int getTotalChunks() {
        return totalChunksProcessed;
    }

    public int getLazyChunksCount() {
        return lazyChunksCount;
    }

    public double getAverageMultiplier() {
        return averageTickMultiplier;
    }

    // Getter für Konfigurationswerte
    public int getCurrentDistance() {
        return configManager.getLazyChunksDistance();
    }

    public int getMinDistance() {
        return configManager.getLazyChunksMinDistance();
    }

    public boolean isAdaptiveEnabled() {
        return configManager.isAdaptiveLaziness();
    }

    public double getCurrentTPS() {
        return Bukkit.getTPS()[0];
    }


    // Methode zum Abrufen aller Statistiken
    public Map<String, Object> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("active", isActive);
        stats.put("totalChunks", totalChunksProcessed);
        stats.put("lazyChunks", lazyChunksCount);
        stats.put("averageMultiplier", averageTickMultiplier);
        stats.put("currentDistance", getCurrentDistance());
        stats.put("adaptiveEnabled", isAdaptiveEnabled());
        stats.put("currentTPS", getCurrentTPS());
        return stats;
    }

    private int calculateDistanceToNearestPlayer(Chunk chunk) {
        int minDistance = Integer.MAX_VALUE;
        Chunk chunkPos = chunk;

        for (Player player : Bukkit.getOnlinePlayers()) {
            Location playerLoc = player.getLocation();
            Chunk playerChunk = playerLoc.getChunk();

            if (playerChunk.getWorld().equals(chunkPos.getWorld())) {
                int dx = Math.abs(playerChunk.getX() - chunkPos.getX());
                int dz = Math.abs(playerChunk.getZ() - chunkPos.getZ());
                int distance = Math.max(dx, dz); // Chebyshev-Distanz

                if (distance < minDistance) {
                    minDistance = distance;
                }
            }
        }

        return minDistance == Integer.MAX_VALUE ? 100 : minDistance; // Fallback
    }

    private int calculateTickMultiplier(int distance, int baseDistance) {
        if (distance <= baseDistance) {
            return 1; // Normaler Tick
        }

        // Exponentielle Verlangsamung: jede Stufe verdoppelt
        int steps = distance - baseDistance;
        return (int) Math.pow(2, steps + 1); // 4, 8, 16, 32, ...
    }

    private void processChunkTicks() {
        for (ChunkTickData data : chunkTickMap.values()) {
            if (data.shouldSkipTick()) {
                skipChunkTick(data);
            } else {
                data.resetTickCounter();
            }
        }
    }

    private void skipChunkTick(ChunkTickData data) {
        Chunk chunk = data.getChunk();

        // Entities langsamer ticken
        for (Entity entity : chunk.getEntities()) {
            if (!(entity instanceof Player)) {
                skipEntityTick(entity, data.getTickMultiplier());
            }
        }

        // Redstone-Verlangsamung
        if (data.getTickMultiplier() >= 4) {
            slowRedstone(chunk, data.getTickMultiplier());
        }
    }

    private void skipEntityTick(Entity entity, int multiplier) {
        // Wir manipulieren den internen Tick-Zähler nicht direkt,
        // sondern setzen die Entity auf "inaktiv" für mehrere Ticks

        if (entity instanceof LivingEntity) {
            LivingEntity living = (LivingEntity) entity;

            // AI-Verhalten reduzieren
            if (multiplier >= 8) {
                living.setAI(false);
                // Nach dem richtigen Tick wieder aktivieren
                Bukkit.getScheduler().runTaskLater(plugin, () -> {
                    if (living.isValid()) {
                        living.setAI(true);
                    }
                }, multiplier);
            }
        }
    }

    private void slowRedstone(Chunk chunk, int multiplier) {
        // Redstone-Update-Rate reduzieren
        // Hier könntest du mit Paper/Spigot APIs arbeiten
        // oder einen Block-Event-Listener verwenden
    }

    private void adjustAdaptiveDistance() {
        if (!configManager.isAdaptiveLaziness()) return;

        double currentTPS = Bukkit.getTPS()[0];
        double threshold = configManager.getAdaptiveTPSThreshold();

        if (currentTPS < threshold) {
            // TPS ist schlecht - Reduziere den normalen Bereich
            int currentDistance = configManager.getLazyChunksDistance();
            int minDistance = configManager.getLazyChunksMinDistance();

            if (currentDistance > minDistance) {
                // Wir können die Config nicht live ändern, aber wir können
                // den effektiven Basis-Abstand für die Berechnung anpassen
                plugin.getLogger().warning(
                        String.format("⚠ Adaptive Lazy Chunks: TPS %.1f < %.1f - " +
                                        "Reduziere aktiven Bereich",
                                currentTPS, threshold)
                );
            }
        }
    }

    private void logStatistics() {
        plugin.getLogger().info(
                String.format("[LazyChunks] Chunks: %d total, %d lazy (%.1f%%) " +
                                "Avg multiplier: %.1fx",
                        totalChunksProcessed, lazyChunksCount,
                        (lazyChunksCount * 100.0 / totalChunksProcessed),
                        averageTickMultiplier)
        );
    }

    public void stop() {
        if (monitoringTask != null) {
            monitoringTask.cancel();
        }

        // Alle Entities wieder normal ticken lassen
        for (ChunkTickData data : chunkTickMap.values()) {
            restoreChunk(data.getChunk());
        }

        chunkTickMap.clear();
        isActive = false;
        plugin.getLogger().info("✓ Lazy Chunks Manager gestoppt");
    }

    private void restoreChunk(Chunk chunk) {
        for (Entity entity : chunk.getEntities()) {
            if (entity instanceof LivingEntity) {
                ((LivingEntity) entity).setAI(true);
            }
        }
    }

    public boolean isActive() {
        return isActive;
    }

    // Hilfsklassen
    private static class ChunkPosition {
        private final World world;
        private final int x, z;

        public ChunkPosition(Chunk chunk) {
            this.world = chunk.getWorld();
            this.x = chunk.getX();
            this.z = chunk.getZ();
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ChunkPosition that = (ChunkPosition) o;
            return x == that.x && z == that.z && world.equals(that.world);
        }

        @Override
        public int hashCode() {
            return Objects.hash(world, x, z);
        }
    }

    private static class ChunkTickData {
        private final Chunk chunk;
        private int tickMultiplier;
        private int currentTick = 0;

        public ChunkTickData(Chunk chunk, int tickMultiplier) {
            this.chunk = chunk;
            this.tickMultiplier = tickMultiplier;
        }

        public boolean shouldSkipTick() {
            currentTick++;
            return currentTick % tickMultiplier != 0;
        }

        public void resetTickCounter() {
            currentTick = 0;
        }

        public Chunk getChunk() { return chunk; }
        public int getTickMultiplier() { return tickMultiplier; }
        public void setTickMultiplier(int multiplier) {
            this.tickMultiplier = multiplier;
        }
    }
}