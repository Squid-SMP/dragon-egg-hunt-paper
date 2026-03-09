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

import java.util.Collection;

public class DehEggManager {
    public NamespacedKey eggKey;
    public Location spawnLocation;
    public Location placedLocation;

    private final DragonEggHunt plugin;
    private DehLeaderboard leaderboard;

    public DehEggManager(DragonEggHunt plugin) {
        this.plugin = plugin;
        eggKey = new NamespacedKey(plugin, "ctf_dragon_egg");

        BukkitRunnable runnable = new BukkitRunnable() {
            @Override
            public void run() {
                checkRules();
            }
        };

        runnable.runTaskTimer(plugin, 100L, 20L);
    }

    public void setLeaderboard(DehLeaderboard leaderboard) {
        this.leaderboard = leaderboard;
    }

    private void checkRules() {
        long maxHoldTime = 1000 * 60 * 30;

        FileConfiguration config = plugin.getConfig();

        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean hasEgg = false;

            for (ItemStack item : p.getInventory().getContents()) {
                if (isSpecialEgg(item)) {
                    hasEgg = true;
                    leaderboard.incrementStat(p.getUniqueId(), "time_held");

                    if (p.getLocation().getY() < -64) {
                        p.getInventory().remove(item);
                        respawnEgg();

                        plugin.broadcast("The Artifact fell into the void and has returned to its shrine!", NamedTextColor.RED);

                        plugin.pickupTimes.remove(p.getUniqueId());
                        return;
                    }

                    plugin.pickupTimes.putIfAbsent(p.getUniqueId(), System.currentTimeMillis());
                    Long pickedUpAt = plugin.pickupTimes.get(p.getUniqueId());

                    if (pickedUpAt != null) {
                        long timeHeld = System.currentTimeMillis() - pickedUpAt;
                        if (timeHeld > maxHoldTime) {
                            p.getInventory().remove(item);
                            respawnEgg();
                            plugin.broadcast("The Artifact became too unstable to hold and teleported away!", NamedTextColor.DARK_PURPLE);
                            plugin.pickupTimes.remove(p.getUniqueId());
                        }
                    }
                }
            }
            if (!hasEgg) {
                plugin.pickupTimes.remove(p.getUniqueId());
            }
        }

        for (World w : Bukkit.getWorlds()) {
            for (Item itemEntity : w.getEntitiesByClass(Item.class)) {
                if (isSpecialEgg(itemEntity.getItemStack())) {
                    if (itemEntity.getLocation().getY() < -64) {
                        itemEntity.remove();
                        respawnEgg();
                        plugin.broadcast("The Artifact fell into the void and has returned to its shrine!", NamedTextColor.RED);
                    }
                }
            }
        }
    }

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
        Collection<? extends Player> players = Bukkit.getOnlinePlayers();
        for (Player player : players) {
            stripEgg(player);
        }

        deleteEggInWorld();

        ItemStack egg = createSpecialEgg();
        p.getInventory().addItem(egg);
        plugin.sendMessage(p, "Received Artifact.", NamedTextColor.WHITE);
    }

    private void stripEgg(Player p)
    {
        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null || !isSpecialEgg(item)) {
                continue;
            }

            p.getInventory().remove(item);
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

    public void stripAndRespawnEgg(Player p) {
        stripEgg(p);
        respawnEgg();
        plugin.broadcast("The Artifact has disintegrated and has returned to its Shrine!", NamedTextColor.RED);
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

    // --- Location Loaders ---

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
