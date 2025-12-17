package de.sxrja.performancePerfected.managers;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;
import java.io.File;

public class ConfigManager {

    private JavaPlugin plugin;
    private YamlConfiguration config, advancedConfig, langConfig;
    private boolean useAdvancedConfig;
    private String language;

    public ConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void loadConfigs() {
        // Standard-Configs speichern
        plugin.saveDefaultConfig();
        plugin.saveResource("advanced-config.yml", false);
        plugin.saveResource("lang-config.yml", false);

        // Configs laden
        config = (YamlConfiguration) plugin.getConfig();
        advancedConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "advanced-config.yml"));
        langConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "lang-config.yml"));

        useAdvancedConfig = config.getBoolean("enableAdvancedConfig", false);

        // Sprache aus der aktiven Config laden
        if (useAdvancedConfig) {
            language = advancedConfig.getString("language", "en");
        } else {
            language = config.getString("language", "en");
        }

        // Sprache validieren
        if (!language.equals("de") && !language.equals("en")) {
            plugin.getLogger().warning("Invalid language: " + language + ". Defaulting to English.");
            language = "en";
        }

        plugin.getLogger().info("Language set to: " + language + " (using " +
                (useAdvancedConfig ? "advanced" : "simple") + " config)");
    }

    // FEHLTE: reloadConfigs() Methode
    public void reloadConfigs() {
        plugin.reloadConfig();
        config = (YamlConfiguration) plugin.getConfig();
        advancedConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "advanced-config.yml"));
        langConfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "lang-config.yml"));

        // Sprache neu laden
        if (useAdvancedConfig) {
            language = advancedConfig.getString("language", "en");
        } else {
            language = config.getString("language", "en");
        }

        if (!language.equals("de") && !language.equals("en")) {
            language = "en";
        }
    }

    public String getLangMessage(String path, String defaultValue) {
        // Pfad mit Sprachpräfix erstellen (z.B. "en.config.change-detected")
        String fullPath = language + "." + path;

        if (langConfig.contains(fullPath)) {
            String message = langConfig.getString(fullPath);
            return ChatColor.translateAlternateColorCodes('&', message);
        } else {
            // Fallback auf Default-Wert mit Formatierung
            return ChatColor.translateAlternateColorCodes('&', defaultValue);
        }
    }

    // Überladene Methode ohne Default-Wert
    public String getLangMessage(String path) {
        return getLangMessage(path, "&cMissing translation: " + path);
    }

    // FEHLTE: stripColor() Methode
    public String stripColor(String message) {
        return message.replaceAll("&[0-9a-fk-or]", "");
    }

    // Neue Methode für Broadcast-Lines
    public String[] getBroadcastLines() {
        String[] lines = new String[6];
        for (int i = 1; i <= 6; i++) {
            lines[i-1] = getLangMessage("config.broadcast.line" + i,
                    getDefaultBroadcastLine(i, language));
        }
        return lines;
    }

    private String getDefaultBroadcastLine(int line, String lang) {
        // Englisch als Standard-Fallback
        if (lang.equals("de")) {
            switch (line) {
                case 1: return "&c⚠══════════════════════════════════════⚠";
                case 2: return "&6⚡ &ePerformance-Einstellungen wurden geändert!";
                case 3: return "&f➤ &7Ein &cServer-Neustart &7ist erforderlich";
                case 4: return "&f➤ &7Nicht nur &e/reload &7verwenden";
                case 5: return "&f➤ &7Änderungen werden erst nach Neustart aktiv";
                case 6: return "&c⚠══════════════════════════════════════⚠";
            }
        } else {
            // Englisch als Default
            switch (line) {
                case 1: return "&c⚠══════════════════════════════════════⚠";
                case 2: return "&6⚡ &ePerformance settings have been changed!";
                case 3: return "&f➤ &7A &cserver restart &7is required";
                case 4: return "&f➤ &7Do not just use &e/reload";
                case 5: return "&f➤ &7Changes will only take effect after restart";
                case 6: return "&c⚠══════════════════════════════════════⚠";
            }
        }
        return "";
    }

    // Getter für aktuelle Sprache
    public String getLanguage() {
        return language;
    }

    // Getter für die aktive Config (je nach Modus)
    public YamlConfiguration getActiveConfig() {
        return useAdvancedConfig ? advancedConfig : config;
    }

    // Getter für useAdvancedConfig
    public boolean useAdvancedConfig() {
        return useAdvancedConfig;
    }

    // Weitere Getter...
    public YamlConfiguration getConfig() { return config; }
    public YamlConfiguration getAdvancedConfig() { return advancedConfig; }
    public YamlConfiguration getLangConfig() { return langConfig; }
    public JavaPlugin getPlugin() { return plugin; }
}