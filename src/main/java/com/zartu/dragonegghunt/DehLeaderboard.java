package com.zartu.dragonegghunt;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DehLeaderboard {
    private final DragonEggHunt plugin;

    public FileConfiguration config;

    public DehLeaderboard(DragonEggHunt plugin) {
        this.plugin = plugin;
        config = plugin.getConfig();
    }

    public void showLeaderboard(Player player) {
        player.sendMessage(NamedTextColor.GOLD + "=== Artifact Legends (Time Held) ===");
        Map<String, Integer> timeScores = new HashMap<>();

        ConfigurationSection statsSection = config.getConfigurationSection("stats");
        if (statsSection == null) {
            player.sendMessage(NamedTextColor.GRAY + "No records yet.");
            return;
        }

        for (String uuidStr : statsSection.getKeys(false)) {
            int time = plugin.getConfig().getInt("stats." + uuidStr + ".time_held");

            if (time > 0) {
                String name = "Unknown";
                try {
                    String pName = Bukkit.getOfflinePlayer(UUID.fromString(uuidStr)).getName();
                    if (pName != null) name = pName;
                }
                catch (Exception ignored) {}

                timeScores.put(name, time);
            }
        }

        timeScores.entrySet().stream()
                .sorted((k1, k2) -> -k1.getValue().compareTo(k2.getValue()))
                .limit(5)
                .forEach(entry -> {
                    int minutes = entry.getValue() / 60;
                    player.sendMessage(NamedTextColor.YELLOW + entry.getKey() + ": " + NamedTextColor.WHITE + minutes + " mins");
                });
    }

    public void incrementStat(UUID uuid, String statName) {
        FileConfiguration config = plugin.getConfig();

        String path = "stats." + uuid.toString() + "." + statName;
        int current = config.getInt(path, 0);
        config.set(path, current + 1);
        plugin.saveConfig();
    }
}
