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

public class DehEggManager {
    public NamespacedKey eggKey;
    public Location spawnLocation;
    public Location placedLocation;

    private final DragonEggHunt plugin;
    private DehLeaderboard leaderboard;

    public final Map<UUID, Long> pickupTimes = new HashMap<>();

    private final Duration maxHoldTime = Duration.ofSeconds(15);

    public DehEggManager(DragonEggHunt plugin) {
        this.plugin = plugin;
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
        FileConfiguration config = plugin.getConfig();

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
        FileConfiguration config = plugin.getConfig();

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
        FileConfiguration config = plugin.getConfig();

        UUID playerUUID = player.getUniqueId();

        config.set(plugin.CFG_HOLDER_UUID, playerUUID.toString());
        config.set(plugin.CFG_HOLDER_TIME, System.currentTimeMillis());

        resetPlacerConfig();

        leaderboard.incrementStat(playerUUID, "captures");
    }

    public void onEggRemoved(Player player) {
        FileConfiguration config = plugin.getConfig();

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
        FileConfiguration config = plugin.getConfig();

        config.set(plugin.CFG_HOLDER_UUID, null);
        config.set(plugin.CFG_HOLDER_TIME, null);
    }

    private void resetPlacerConfig() {
        FileConfiguration config = plugin.getConfig();

        config.set(plugin.CFG_PLACER_UUID, null);
    }


    public void onEggPlaced(Player player, Location location) {
        FileConfiguration config = plugin.getConfig();

        saveEggBlockLocation(location);

        resetHolderConfig();

        config.set(plugin.CFG_PLACER_UUID, player.getUniqueId().toString());
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

        FileConfiguration config = plugin.getConfig();

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

        FileConfiguration config = plugin.getConfig();

        resetHolderConfig();
    }

    public void stripAndRespawnEgg(Player p) {
        stripEgg(p);
        respawnEgg();
    }

    // --- Tracking ---

    public void trackEggLogic(Player tracker) {
        Location target = null;
        String status = "Unknown";
        FileConfiguration config = plugin.getConfig();

        for (Player p : Bukkit.getOnlinePlayers()) {
            for (ItemStack i : p.getInventory()) {
                if (isSpecialEgg(i)) {
                    target = p.getLocation();
                    status = "Held by Unknown";
                    break;
                }
            }
        }

        if (target == null && config.contains(plugin.CFG_BLOCK_WORLD)) {
            String wName = config.getString(plugin.CFG_BLOCK_WORLD);
            if (wName != null) {
                World w = Bukkit.getWorld(wName);
                if (w != null) {
                    target = new Location(w,
                            config.getInt(plugin.CFG_BLOCK_X),
                            config.getInt(plugin.CFG_BLOCK_Y),
                            config.getInt(plugin.CFG_BLOCK_Z));
                    status = "Secured in World";
                }
            }
        }

        if (target == null) {
            for (World w : Bukkit.getWorlds()) {
                for (Entity e : w.getEntities()) {
                    if (e instanceof Item itemEntity) {
                        if (isSpecialEgg(itemEntity.getItemStack())) {
                            target = e.getLocation();
                            status = "Dropped on ground";
                            break;
                        }
                    }
                }
            }
        }

        if (target == null) {
            plugin.sendMessage(tracker, "Signal lost. (The Artifact may be lost in time...)", NamedTextColor.RED);
            return;
        }

        int radius = 300;
        double angle = Math.random() * 2 * Math.PI;
        double dist = Math.random() * (radius * 0.6);
        int offsetX = (int) (Math.cos(angle) * dist);
        int offsetZ = (int) (Math.sin(angle) * dist);
        int cX = target.getBlockX() + offsetX;
        int cZ = target.getBlockZ() + offsetZ;

        plugin.sendMessage(tracker, "--- Artifact Tracker (" + status + ") ---", NamedTextColor.GOLD);
        plugin.sendMessage(tracker, "Search Area: X[" + (cX - radius) + " : " + (cX + radius) + "]", NamedTextColor.YELLOW);
        plugin.sendMessage(tracker, "Search Area: Z[" + (cZ - radius) + " : " + (cZ + radius) + "]", NamedTextColor.YELLOW);
    }

    // --- Location Management ---

    public void setSpawnLocation(Location loc)  {
        Location spawnLoc = loc.toBlockLocation();

        World spawnWorld = spawnLoc.getWorld();

        if (spawnWorld == null) {
            return;
        }

        FileConfiguration config = plugin.getConfig();

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
        FileConfiguration config = plugin.getConfig();

        if (loc.getWorld() == null) return;
        config.set(plugin.CFG_BLOCK_WORLD, loc.getWorld().getName());
        config.set(plugin.CFG_BLOCK_X, loc.getBlockX());
        config.set(plugin.CFG_BLOCK_Y, loc.getBlockY());
        config.set(plugin.CFG_BLOCK_Z, loc.getBlockZ());
        plugin.saveConfig();
    }

    public void clearEggBlockLocation() {
        plugin.getConfig().set("placed_egg", null);
        plugin.saveConfig();
    }

    public boolean isSavedEggLocation(Location loc) {
        FileConfiguration config = plugin.getConfig();

        if (!config.contains(plugin.CFG_BLOCK_WORLD)) return false;
        if (loc.getWorld() == null) return false;

        String savedWorld = config.getString(plugin.CFG_BLOCK_WORLD);
        if (savedWorld == null) return false;

        return loc.getWorld().getName().equals(savedWorld) &&
                loc.getBlockX() == config.getInt(plugin.CFG_BLOCK_X) &&
                loc.getBlockY() == config.getInt(plugin.CFG_BLOCK_Y) &&
                loc.getBlockZ() == config.getInt(plugin.CFG_BLOCK_Z);
    }

    public void loadSpawnLocation() {
        FileConfiguration config = plugin.getConfig();

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
        FileConfiguration config = plugin.getConfig();

        if (!config.contains(plugin.CFG_BLOCK_WORLD)) {
            return;
        }

        String worldName = config.getString(plugin.CFG_BLOCK_WORLD);
        if (worldName == null) {
            return;
        }

        World w = Bukkit.getWorld(worldName);
        if (w == null) {
            return;
        }

        placedLocation = new Location(w,
                config.getDouble(plugin.CFG_BLOCK_X),
                config.getDouble(plugin.CFG_BLOCK_Y),
                config.getDouble(plugin.CFG_BLOCK_Z)
        );
    }
}
