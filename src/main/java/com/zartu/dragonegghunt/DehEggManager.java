package com.zartu.dragonegghunt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitRunnable;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class DehEggManager {
    public NamespacedKey eggKey;
    public Location spawnLocation;
    public Location placedLocation;

    private final DragonEggHunt plugin;
    private DehLeaderboard leaderboard;

    public final Map<UUID, Long> pickupTimes = new HashMap<>();

    private final Duration maxHoldTime = Duration.ofSeconds(15);

    public FileConfiguration config;

    public DehEggManager(DragonEggHunt plugin) {
        this.plugin = plugin;
        config = plugin.getConfig();
        eggKey = new NamespacedKey(plugin, "ctf_dragon_egg");

        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                update();
            }
        };

        runnable.runTaskTimer(plugin, 100L, 20L);
    }

    public void setLeaderboard(DehLeaderboard leaderboard) {
        this.leaderboard = leaderboard;
    }

    private void update() {
        checkHolder();
        checkPlacer();
    }

    private void checkHolder() {
        String configUUID = config.getString(plugin.CFG_HOLDER_UUID);

        if (configUUID == null) {
            return;
        }

        long pickedUpTime = config.getLong(plugin.CFG_HOLDER_TIME);

        if (pickedUpTime == 0) {
            return;
        }

        long timeHeld = System.currentTimeMillis() - pickedUpTime;
        if (timeHeld >= maxHoldTime.toMillis()) {
            UUID holderUUID = UUID.fromString(configUUID);
            OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(holderUUID);
            Player player = offlinePlayer.getPlayer();

            if (player != null) {
                stripEgg(player);
            }

            respawnEgg();

            plugin.broadcast("The Artifact became too unstable to hold and has returned to its shrine!", NamedTextColor.DARK_PURPLE);
        }
    }

    private void checkPlacer() {
        String configUUID = config.getString(plugin.CFG_PLACER_UUID);

        if (configUUID == null) {
            return;
        }

        UUID placerUUID = UUID.fromString(configUUID);
        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(placerUUID);
        Player player = offlinePlayer.getPlayer();

        if (player != null) {
            leaderboard.incrementStat(placerUUID, "time_held");
        }
    }

    public void onEggPickedUp(Player player) {
        UUID playerUUID = player.getUniqueId();

        config.set(plugin.CFG_HOLDER_UUID, playerUUID.toString());
        config.set(plugin.CFG_HOLDER_TIME, System.currentTimeMillis());

        resetPlacerConfig();

        leaderboard.incrementStat(playerUUID, "captures");
    }

    public void onEggRemoved(Player player) {
        String configUUID = config.getString(plugin.CFG_HOLDER_UUID);

        if (configUUID == null) {
            return;
        }

        String playerUUIDStr = player.getUniqueId().toString();
        if (configUUID.equals(playerUUIDStr)) {
            resetHolderConfig();
        }
    }

    private void resetHolderConfig() {
        config.set(plugin.CFG_HOLDER_UUID, null);
        config.set(plugin.CFG_HOLDER_TIME, null);
    }

    private void resetPlacerConfig() {
        config.set(plugin.CFG_PLACER_UUID, null);
        config.set(plugin.CFG_PLACER_TIME, null);
    }


    public void onEggPlaced(Player player, Location location) {
        saveEggBlockLocation(location);

        resetHolderConfig();
        resetTrackConfig();

        config.set(plugin.CFG_PLACER_UUID, player.getUniqueId().toString());
        config.set(plugin.CFG_PLACER_TIME, System.currentTimeMillis());
    }

    // --- Special Egg Handling ---

    public ItemStack createSpecialEgg() {
        ItemStack egg = new ItemStack(Material.DRAGON_EGG);
        ItemMeta meta = egg.getItemMeta();

        if (meta != null) {
            TextComponent textComponent = Component.text("The Artifact").color(NamedTextColor.DARK_PURPLE);
            meta.customName(textComponent);
            meta.getPersistentDataContainer().set(eggKey, PersistentDataType.BYTE, (byte) 1);
            egg.setItemMeta(meta);
        }

        return egg;
    }

    public boolean isSpecialEgg(ItemStack item) {
        if (item == null || item.getType() != Material.DRAGON_EGG) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        return meta.getPersistentDataContainer().has(eggKey, PersistentDataType.BYTE);
    }

    public void resetEgg(Player p) {
        stripAllPlayers();

        deleteEggInWorld();

        ItemStack egg = createSpecialEgg();
        p.getInventory().addItem(egg);
        plugin.sendMessage(p, "Received Artifact.", NamedTextColor.WHITE);
    }

    private void stripAllPlayers() {
        OfflinePlayer[] offlinePlayers = Bukkit.getOfflinePlayers();
        for (OfflinePlayer offlinePlayer : offlinePlayers) {
            Player player = offlinePlayer.getPlayer();

            if (player == null) {
                continue;
            }

            stripEgg(player);
        }
    }

    public void stripEgg(Player p)
    {
        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null || !isSpecialEgg(item)) {
                continue;
            }

            p.getInventory().remove(item);
            onEggRemoved(p);
            break;
        }
    }

    public void deleteEggInWorld()  {
        if (placedLocation == null) {
            loadPlacedLocation();
        }

        if (placedLocation == null) {
            return;
        }

        Block block = placedLocation.getBlock();
        block.setType(Material.AIR);

        resetHolderConfig();
        resetPlacerConfig();
    }

    public void respawnEgg() {
        if (spawnLocation == null) {
            loadSpawnLocation();
        }

        if (spawnLocation == null) {
            return;
        }

        if (spawnLocation.getWorld() == null) {
            return;
        }

        if (!spawnLocation.getChunk().isLoaded()) {
            spawnLocation.getChunk().load();
        }

        Block block = spawnLocation.getBlock();
        block.setType(Material.DRAGON_EGG, false);

        saveEggBlockLocation(spawnLocation);

        resetHolderConfig();
    }

    public void stripAndRespawnEgg(Player p) {
        stripEgg(p);
        respawnEgg();
    }

    // --- Tracking ---

    public void trackEgg(Player tracker) {
        Location target = null;

        if (config.contains(plugin.CFG_HOLDER_UUID)) {
            plugin.sendMessage(tracker, "Signal currently in movement.", NamedTextColor.GOLD);
            return;
        }

        loadPlacedLocation();
        target = placedLocation;

        if (target == null) {
            plugin.sendMessage(tracker, "Signal lost. (The Artifact may be lost in time...)", NamedTextColor.RED);
            return;
        }

        long timePlace = config.getLong(plugin.CFG_PLACER_TIME);
        long msSincePlace = System.currentTimeMillis() - timePlace;

        var a = msSincePlace / 1800000;
        int halfHourIntervalsSincePlace = Math.round(a);
        plugin.log.info(String.valueOf(a));
        plugin.log.info(String.valueOf(halfHourIntervalsSincePlace));

        int radius = plugin.TRACK_RADIUS_BASE - (halfHourIntervalsSincePlace * plugin.TRACK_RADIUS_REDUCTION);
        if (radius < plugin.TRACK_RADIUS_REDUCTION) {
            radius = plugin.TRACK_RADIUS_REDUCTION;
        }

        if (config.contains(plugin.CFG_TRACK_RADIUS)) {
            int configRadius = config.getInt(plugin.CFG_TRACK_RADIUS);

            if (radius != configRadius) {
                resetTrackConfig();
            }
        }

        config.set(plugin.CFG_TRACK_RADIUS, radius);

        int offsetX = 0;
        int offsetY = 0;

        ThreadLocalRandom rand = ThreadLocalRandom.current();

        if (!config.contains(plugin.CFG_TRACK_OFFSET_X)) {
            offsetX = rand.nextInt(-radius, radius);
            config.set(plugin.CFG_TRACK_OFFSET_X, offsetX);
        }
        else {
            offsetX = config.getInt(plugin.CFG_TRACK_OFFSET_X);
        }

        if (!config.contains(plugin.CFG_TRACK_OFFSET_Y)) {
            offsetY = rand.nextInt(-radius, radius);
            config.set(plugin.CFG_TRACK_OFFSET_Y, offsetY);
        }
        else {
            offsetY = config.getInt(plugin.CFG_TRACK_OFFSET_Y);
        }

        int cX = target.getBlockX() + offsetX;
        int cZ = target.getBlockZ() + offsetY;

        plugin.sendMessage(tracker, "——— Artifact Tracker ———", NamedTextColor.GOLD);
        plugin.sendMessage(tracker, "Search Area: X[" + (cX - radius) + " : " + (cX + radius) + "]", NamedTextColor.YELLOW);
        plugin.sendMessage(tracker, "Search Area: Z[" + (cZ - radius) + " : " + (cZ + radius) + "]", NamedTextColor.YELLOW);
    }

    private void resetTrackConfig() {
        config.set(plugin.CFG_TRACK_RADIUS, null);
        config.set(plugin.CFG_TRACK_OFFSET_X, null);
        config.set(plugin.CFG_TRACK_OFFSET_Y, null);
    }

    // --- Location Management ---

    public void setSpawnLocation(Location loc)  {
        Location spawnLoc = loc.toBlockLocation();

        World spawnWorld = spawnLoc.getWorld();

        if (spawnWorld == null) {
            return;
        }

        double x = spawnLoc.getX();
        double y = spawnLoc.getY();
        double z = spawnLoc.getZ();

        config.set(plugin.CFG_SPAWN_WORLD, spawnWorld.getName());
        config.set(plugin.CFG_SPAWN_X, x);
        config.set(plugin.CFG_SPAWN_Y, y);
        config.set(plugin.CFG_SPAWN_Z, z);

        plugin.saveConfig();

        spawnLocation = spawnLoc;
    }

    public void saveEggBlockLocation(Location loc) {
        if (loc.getWorld() == null) {
            return;
        }

        config.set(plugin.CFG_BLOCK_WORLD, loc.getWorld().getName());
        config.set(plugin.CFG_BLOCK_X, loc.getBlockX());
        config.set(plugin.CFG_BLOCK_Y, loc.getBlockY());
        config.set(plugin.CFG_BLOCK_Z, loc.getBlockZ());
        plugin.saveConfig();

        placedLocation = loc;
    }

    public void clearEggBlockLocation() {
        plugin.getConfig().set("placed_egg", null);
        plugin.saveConfig();
    }

    public boolean isSavedEggLocation(Location loc) {
        if (!config.contains(plugin.CFG_BLOCK_WORLD)) {
            return false;
        }

        if (loc.getWorld() == null) {
            return false;
        }

        String savedWorld = config.getString(plugin.CFG_BLOCK_WORLD);
        if (savedWorld == null) return false;

        return loc.getWorld().getName().equals(savedWorld) &&
                loc.getBlockX() == config.getInt(plugin.CFG_BLOCK_X) &&
                loc.getBlockY() == config.getInt(plugin.CFG_BLOCK_Y) &&
                loc.getBlockZ() == config.getInt(plugin.CFG_BLOCK_Z);
    }

    public void loadSpawnLocation() {
        if (!config.contains(plugin.CFG_SPAWN_WORLD)) {
            return;
        }

        String worldName = config.getString(plugin.CFG_SPAWN_WORLD);
        if (worldName == null) {
            return;
        }

        World w = Bukkit.getWorld(worldName);
        if (w == null) {
            return;
        }

        spawnLocation = new Location(w,
                config.getDouble(plugin.CFG_SPAWN_X),
                config.getDouble(plugin.CFG_SPAWN_Y),
                config.getDouble(plugin.CFG_SPAWN_Z)
        );
    }

    public void loadPlacedLocation() {
        if (!config.contains(plugin.CFG_BLOCK_WORLD)) {
            placedLocation = null;
            return;
        }

        String worldName = config.getString(plugin.CFG_BLOCK_WORLD);
        if (worldName == null) {
            placedLocation = null;
            return;
        }

        World w = Bukkit.getWorld(worldName);
        if (w == null) {
            placedLocation = null;
            return;
        }

        placedLocation = new Location(w,
                config.getDouble(plugin.CFG_BLOCK_X),
                config.getDouble(plugin.CFG_BLOCK_Y),
                config.getDouble(plugin.CFG_BLOCK_Z)
        );
    }
}
