package com.zartu.dragonegghunt;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.FallingBlock;
import org.bukkit.entity.Item;
import org.bukkit.entity.ItemFrame;
import org.bukkit.entity.Player;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPickupItemEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.ItemDespawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryPickupItemEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DragonEggHunt extends JavaPlugin implements Listener, CommandExecutor {

    private final NamespacedKey eggKey = new NamespacedKey(this, "ctf_dragon_egg");
    private Location spawnLocation;

    private final Map<UUID, Long> pickupTimes = new HashMap<>();

    private static final String CFG_SPAWN_WORLD = "spawn.world";
    private static final String CFG_SPAWN_X = "spawn.x";
    private static final String CFG_SPAWN_Y = "spawn.y";
    private static final String CFG_SPAWN_Z = "spawn.z";

    private static final String CFG_BLOCK_WORLD = "placed_egg.world";
    private static final String CFG_BLOCK_X = "placed_egg.x";
    private static final String CFG_BLOCK_Y = "placed_egg.y";
    private static final String CFG_BLOCK_Z = "placed_egg.z";

    private static final String CFG_DROP_WORLD = "dropped_egg.world";
    private static final String CFG_DROP_X = "dropped_egg.x";
    private static final String CFG_DROP_Y = "dropped_egg.y";
    private static final String CFG_DROP_Z = "dropped_egg.z";
    private static final String CFG_DROP_TIME = "dropped_egg.time";
    private static final String CFG_DROP_UUID = "dropped_egg.uuid";

    @Override
    public void onEnable() {
        saveDefaultConfig();
        loadSpawnLocation();
        getServer().getPluginManager().registerEvents(this, this);

        registerCommand("trackegg");
        registerCommand("seteggspawn");
        registerCommand("giveegg");
        registerCommand("eggleaderboard");

        new BukkitRunnable() {
            @Override
            public void run() {
                checkRules();
            }
        }.runTaskTimer(this, 100L, 20L);
    }

    private void registerCommand(String name) {
        PluginCommand command = getCommand(name);
        if (command != null) {
            command.setExecutor(this);
        } else {
            getLogger().warning("Command '" + name + "' is missing from plugin.yml!");
        }
    }

    private void checkRules() {
        long maxHoldTime = 1000 * 60 * 30;

        if (getConfig().contains(CFG_DROP_TIME)) {
            long dropTime = getConfig().getLong(CFG_DROP_TIME);
            long timeOnGround = System.currentTimeMillis() - dropTime;
            long maxGroundTime = 1000 * 60 * 5;

            if (timeOnGround > maxGroundTime) {
                forceRecoverAbandonedEgg();
                Bukkit.broadcastMessage(ChatColor.RED + "The Artifact was abandoned for too long and has returned to its shrine!");
            }
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean hasEgg = false;

            for (ItemStack item : p.getInventory().getContents()) {
                if (isSpecialEgg(item)) {
                    hasEgg = true;
                    incrementStat(p.getUniqueId(), "time_held");
                    clearDroppedLocation();

                    if (p.getLocation().getY() < -64) {
                        p.getInventory().remove(item);
                        respawnEgg();
                        Bukkit.broadcastMessage(ChatColor.RED + "The Artifact fell into the void and has returned to its shrine!");
                        pickupTimes.remove(p.getUniqueId());
                        return;
                    }

                    pickupTimes.putIfAbsent(p.getUniqueId(), System.currentTimeMillis());
                    Long pickedUpAt = pickupTimes.get(p.getUniqueId());

                    if (pickedUpAt != null) {
                        long timeHeld = System.currentTimeMillis() - pickedUpAt;
                        if (timeHeld > maxHoldTime) {
                            p.getInventory().remove(item);
                            respawnEgg();
                            Bukkit.broadcastMessage(ChatColor.DARK_PURPLE + "The Artifact became too unstable to hold and teleported away!");
                            pickupTimes.remove(p.getUniqueId());
                        }
                    }
                }
            }
            if (!hasEgg) {
                pickupTimes.remove(p.getUniqueId());
            }
        }

        for (World w : Bukkit.getWorlds()) {
            for (Item itemEntity : w.getEntitiesByClass(Item.class)) {
                if (isSpecialEgg(itemEntity.getItemStack())) {
                    if (itemEntity.getLocation().getY() < -64) {
                        itemEntity.remove();
                        respawnEgg();
                        Bukkit.broadcastMessage(ChatColor.RED + "The Artifact fell into the void and has returned to its shrine!");
                    }
                }
            }
        }
    }

    @EventHandler
    public void onEntityPortal(EntityPortalEvent event) {
        if (event.getEntity() instanceof Item item) {
            if (isSpecialEgg(item.getItemStack())) {
                event.setCancelled(true);
                item.remove();
                respawnEgg();
                Bukkit.broadcastMessage(ChatColor.RED + "The Artifact has disintegrated and has returned to its Shrine!");
            }
        }
        else if (event.getEntity() instanceof FallingBlock fb) {
            if (fb.getBlockData().getMaterial() == Material.DRAGON_EGG) {
                event.setCancelled(true);
                fb.remove();
                respawnEgg();
                Bukkit.broadcastMessage(ChatColor.RED + "The Artifact has disintegrated and has returned to its Shrine!");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDimensionJump(PlayerTeleportEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to == null || to.getWorld() == null || from.getWorld() == null) return;
        if (from.getWorld().equals(to.getWorld())) return;

        Player p = event.getPlayer();
        boolean hasEgg = false;

        for (ItemStack item : p.getInventory().getContents()) {
            if (isSpecialEgg(item)) {
                hasEgg = true;
                break;
            }
        }

        if (hasEgg) {
            World.Environment toEnv = to.getWorld().getEnvironment();
            String spawnWorldName = getConfig().getString(CFG_SPAWN_WORLD);
            boolean isGoingHome = spawnWorldName != null && to.getWorld().getName().equals(spawnWorldName);

            if (toEnv == World.Environment.THE_END) {
                stripAndRespawnEgg(p);
                p.sendMessage(ChatColor.RED + "The Artifact cannot enter The End!");
                return;
            }

            if (toEnv == World.Environment.NETHER) {
                return;
            }

            if (toEnv == World.Environment.NORMAL && isGoingHome) {
                return;
            }

            stripAndDropEgg(p, from);
        }
    }

    @EventHandler
    public void onItemFrameInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof ItemFrame) {
            ItemStack hand = event.getPlayer().getInventory().getItem(event.getHand());
            if (isSpecialEgg(hand)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(ChatColor.RED + "The Artifact cannot be framed!");
            }
        }
    }

    @EventHandler
    public void onBundleInput(InventoryClickEvent event) {
        if (event.getCursor() != null && isSpecialEgg(event.getCursor())) {
            ItemStack current = event.getCurrentItem();
            if (current != null && current.getType() == Material.BUNDLE) {
                event.setCancelled(true);
                event.getWhoClicked().sendMessage(ChatColor.RED + "The Artifact is too powerful for a bundle!");
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (isSpecialEgg(event.getItemDrop().getItemStack())) {
            saveDroppedLocation(event.getItemDrop());
        }
    }

    @EventHandler
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (isSpecialEgg(event.getItem().getItemStack())) event.setCancelled(true);
    }

    @EventHandler
    public void onHopperMove(InventoryMoveItemEvent event) {
        if (isSpecialEgg(event.getItem())) event.setCancelled(true);
    }

    @EventHandler
    public void onContainerStore(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;
        boolean isContainer = event.getView().getTopInventory().getType() != InventoryType.CRAFTING;
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if ((isSpecialEgg(current) || isSpecialEgg(cursor)) && isContainer) {
            if (event.getClickedInventory() == event.getView().getTopInventory() || event.isShiftClick()) {
                event.setCancelled(true);
                event.getWhoClicked().sendMessage(ChatColor.RED + "The Artifact refuses to be contained!");
            }
        }
    }

    @EventHandler
    public void onPistonPush(BlockPistonExtendEvent event) {
        for (Block b : event.getBlocks()) {
            if (b.getType() == Material.DRAGON_EGG) event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (b.getType() == Material.DRAGON_EGG) event.setCancelled(true);
        }
    }

    @EventHandler
    public void onItemDespawn(ItemDespawnEvent event) {
        if (isSpecialEgg(event.getEntity().getItemStack())) {
            event.setCancelled(true);
            event.getEntity().remove();
            respawnEgg();
            Bukkit.broadcastMessage(ChatColor.RED + "The Artifact was abandoned and has returned to its shrine!");
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Item itemEntity && isSpecialEgg(itemEntity.getItemStack())) {
            if (event.getCause() == EntityDamageEvent.DamageCause.LAVA ||
                    event.getCause() == EntityDamageEvent.DamageCause.FIRE ||
                    event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK ||
                    event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION ||
                    event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION ||
                    event.getCause() == EntityDamageEvent.DamageCause.VOID) {

                event.setCancelled(true);
                itemEntity.remove();
                respawnEgg();
                Bukkit.broadcastMessage(ChatColor.RED + "The Artifact was destroyed by elements and has respawned!");
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        pickupTimes.remove(p.getUniqueId());
        for (ItemStack item : p.getInventory().getContents()) {
            if (isSpecialEgg(item)) {
                p.getInventory().remove(item);
                Item dropped = p.getWorld().dropItemNaturally(p.getLocation(), item);
                saveDroppedLocation(dropped);
                Bukkit.broadcastMessage(ChatColor.YELLOW + "The Artifact has been dropped because the holder fled the world!");
            }
        }
    }

    @EventHandler
    public void onPlayerPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p && isSpecialEgg(event.getItem().getItemStack())) {
            p.sendMessage(ChatColor.GREEN + "You have seized the Artifact! Keep it safe!");
            Bukkit.broadcastMessage(ChatColor.DARK_RED + "⚠ The Artifact has been picked up! ⚠");
            pickupTimes.put(p.getUniqueId(), System.currentTimeMillis());
            incrementStat(p.getUniqueId(), "captures");
            clearDroppedLocation();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (isSpecialEgg(event.getItemInHand())) {
            event.setCancelled(false);
            event.setBuild(true);
            saveEggBlockLocation(event.getBlock().getLocation());
            clearDroppedLocation();
            event.getPlayer().sendMessage(ChatColor.GOLD + "The Artifact is secured. Tracking active.");
            Bukkit.broadcastMessage(ChatColor.GOLD + "The Artifact has been placed in the world!");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.DRAGON_EGG) {
            if (isSavedEggLocation(event.getBlock().getLocation())) {
                event.setCancelled(false);
                event.setDropItems(false);
                Item dropped = event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), createSpecialEgg());
                saveDroppedLocation(dropped);
                clearEggBlockLocation();
                Bukkit.broadcastMessage(ChatColor.DARK_RED + "⚠ The Artifact has been dislodged! ⚠");
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEggInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.DRAGON_EGG) {
            event.setCancelled(true);
            if (event.getAction().toString().contains("LEFT_CLICK")) {
                if (isSavedEggLocation(event.getClickedBlock().getLocation())) {
                    event.getClickedBlock().setType(Material.AIR);
                    Item dropped = event.getClickedBlock().getWorld().dropItemNaturally(
                            event.getClickedBlock().getLocation(),
                            createSpecialEgg()
                    );
                    saveDroppedLocation(dropped);
                    clearEggBlockLocation();
                    Bukkit.broadcastMessage(ChatColor.DARK_RED + "⚠ The Artifact has been stolen! ⚠");
                }
            }
        }
    }

    @EventHandler
    public void onFallingBlockLand(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof FallingBlock) {
            if (((FallingBlock) event.getEntity()).getBlockData().getMaterial() == Material.DRAGON_EGG) {
                saveEggBlockLocation(event.getBlock().getLocation());
                clearDroppedLocation();
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) return true;

        if (command.getName().equalsIgnoreCase("giveegg")) {
            p.getInventory().addItem(createSpecialEgg());
            p.sendMessage("Received Artifact.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("seteggspawn")) {
            Location loc = p.getLocation();
            spawnLocation = new Location(loc.getWorld(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ());

            if (spawnLocation.getWorld() != null) {
                getConfig().set(CFG_SPAWN_WORLD, spawnLocation.getWorld().getName());
                getConfig().set(CFG_SPAWN_X, spawnLocation.getX());
                getConfig().set(CFG_SPAWN_Y, spawnLocation.getY());
                getConfig().set(CFG_SPAWN_Z, spawnLocation.getZ());
                saveConfig();
                p.sendMessage(ChatColor.GREEN + "Spawn location set! (Snapped to block coordinates: " + spawnLocation.getBlockX() + ", " + spawnLocation.getBlockY() + ", " + spawnLocation.getBlockZ() + ")");
            }
            return true;
        }

        if (command.getName().equalsIgnoreCase("trackegg")) {
            trackEggLogic(p);
            return true;
        }

        if (command.getName().equalsIgnoreCase("eggleaderboard")) {
            showLeaderboard(p);
            return true;
        }
        return false;
    }

    private void trackEggLogic(Player tracker) {
        Location target = null;
        String status = "Unknown";

        for (Player p : Bukkit.getOnlinePlayers()) {
            for (ItemStack i : p.getInventory()) {
                if (isSpecialEgg(i)) {
                    target = p.getLocation();
                    status = "Held by Unknown";
                    break;
                }
            }
        }

        if (target == null && getConfig().contains(CFG_BLOCK_WORLD)) {
            String wName = getConfig().getString(CFG_BLOCK_WORLD);
            if (wName != null) {
                World w = Bukkit.getWorld(wName);
                if (w != null) {
                    target = new Location(w,
                            getConfig().getInt(CFG_BLOCK_X),
                            getConfig().getInt(CFG_BLOCK_Y),
                            getConfig().getInt(CFG_BLOCK_Z));
                    status = "Secured in World";
                }
            }
        }

        if (target == null && getConfig().contains(CFG_DROP_WORLD)) {
            String wName = getConfig().getString(CFG_DROP_WORLD);
            if (wName != null) {
                World w = Bukkit.getWorld(wName);
                if (w != null) {
                    target = new Location(w,
                            getConfig().getDouble(CFG_DROP_X),
                            getConfig().getDouble(CFG_DROP_Y),
                            getConfig().getDouble(CFG_DROP_Z));
                    status = "Dropped (Signal Weak)";
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
            tracker.sendMessage(ChatColor.RED + "Signal lost. (The Artifact may be lost in time...)");
            return;
        }

        int radius = 300;
        double angle = Math.random() * 2 * Math.PI;
        double dist = Math.random() * (radius * 0.6);
        int offsetX = (int) (Math.cos(angle) * dist);
        int offsetZ = (int) (Math.sin(angle) * dist);
        int cX = target.getBlockX() + offsetX;
        int cZ = target.getBlockZ() + offsetZ;

        tracker.sendMessage(ChatColor.GOLD + "--- Artifact Tracker (" + status + ") ---");
        tracker.sendMessage(ChatColor.YELLOW + "Search Area: X[" + (cX - radius) + " : " + (cX + radius) + "]");
        tracker.sendMessage(ChatColor.YELLOW + "Search Area: Z[" + (cZ - radius) + " : " + (cZ + radius) + "]");
    }

    private void showLeaderboard(Player player) {
        player.sendMessage(ChatColor.GOLD + "=== Artifact Legends (Time Held) ===");
        Map<String, Integer> timeScores = new HashMap<>();

        ConfigurationSection statsSection = getConfig().getConfigurationSection("stats");
        if (statsSection == null) {
            player.sendMessage(ChatColor.GRAY + "No records yet.");
            return;
        }

        for (String uuidStr : statsSection.getKeys(false)) {
            int time = getConfig().getInt("stats." + uuidStr + ".time_held");
            if (time > 0) {
                String name = "Unknown";
                try {
                    String pName = Bukkit.getOfflinePlayer(UUID.fromString(uuidStr)).getName();
                    if (pName != null) name = pName;
                } catch (Exception ignored) {}
                timeScores.put(name, time);
            }
        }

        timeScores.entrySet().stream()
                .sorted((k1, k2) -> -k1.getValue().compareTo(k2.getValue()))
                .limit(5)
                .forEach(entry -> {
                    int minutes = entry.getValue() / 60;
                    player.sendMessage(ChatColor.YELLOW + entry.getKey() + ": " + ChatColor.WHITE + minutes + " mins");
                });
    }

    private void stripAndRespawnEgg(Player p) {
        for (ItemStack item : p.getInventory().getContents()) {
            if (isSpecialEgg(item)) {
                p.getInventory().remove(item);
            }
        }
        respawnEgg();
        Bukkit.broadcastMessage(ChatColor.RED + "The Artifact has disintegrated and has returned to its Shrine!");
    }

    private void stripAndDropEgg(Player p, Location dropLoc) {
        if (dropLoc.getWorld() == null) return;

        for (ItemStack item : p.getInventory().getContents()) {
            if (isSpecialEgg(item)) {
                p.getInventory().remove(item);
                Item dropped = dropLoc.getWorld().dropItemNaturally(dropLoc, item);
                saveDroppedLocation(dropped);
            }
        }
        p.sendMessage(ChatColor.RED + "The Artifact is tethered to the " + getConfig().getString(CFG_SPAWN_WORLD) + " dimension!");
        Bukkit.broadcastMessage(ChatColor.YELLOW + "The Artifact was dropped as " + p.getName() + " tried to leave the world!");
    }

    private ItemStack createSpecialEgg() {
        ItemStack egg = new ItemStack(Material.DRAGON_EGG);
        ItemMeta meta = egg.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.DARK_PURPLE + "The Artifact");
            meta.getPersistentDataContainer().set(eggKey, PersistentDataType.BYTE, (byte) 1);
            egg.setItemMeta(meta);
        }
        return egg;
    }

    private boolean isSpecialEgg(ItemStack item) {
        if (item == null || item.getType() != Material.DRAGON_EGG) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(eggKey, PersistentDataType.BYTE);
    }

    private void saveEggBlockLocation(Location loc) {
        if (loc.getWorld() == null) return;
        getConfig().set(CFG_BLOCK_WORLD, loc.getWorld().getName());
        getConfig().set(CFG_BLOCK_X, loc.getBlockX());
        getConfig().set(CFG_BLOCK_Y, loc.getBlockY());
        getConfig().set(CFG_BLOCK_Z, loc.getBlockZ());
        saveConfig();
    }

    private void clearEggBlockLocation() {
        getConfig().set("placed_egg", null);
        saveConfig();
    }

    private boolean isSavedEggLocation(Location loc) {
        if (!getConfig().contains(CFG_BLOCK_WORLD)) return false;
        if (loc.getWorld() == null) return false;

        String savedWorld = getConfig().getString(CFG_BLOCK_WORLD);
        if (savedWorld == null) return false;

        return loc.getWorld().getName().equals(savedWorld) &&
                loc.getBlockX() == getConfig().getInt(CFG_BLOCK_X) &&
                loc.getBlockY() == getConfig().getInt(CFG_BLOCK_Y) &&
                loc.getBlockZ() == getConfig().getInt(CFG_BLOCK_Z);
    }

    private void saveDroppedLocation(Item item) {
        Location loc = item.getLocation();
        if (loc.getWorld() == null) return;
        getConfig().set(CFG_DROP_WORLD, loc.getWorld().getName());
        getConfig().set(CFG_DROP_X, loc.getX());
        getConfig().set(CFG_DROP_Y, loc.getY());
        getConfig().set(CFG_DROP_Z, loc.getZ());
        getConfig().set(CFG_DROP_TIME, System.currentTimeMillis());
        getConfig().set(CFG_DROP_UUID, item.getUniqueId().toString());
        saveConfig();
    }

    private void clearDroppedLocation() {
        getConfig().set("dropped_egg", null);
        saveConfig();
    }

    private void forceRecoverAbandonedEgg() {
        if (!getConfig().contains(CFG_DROP_WORLD)) return;

        String wName = getConfig().getString(CFG_DROP_WORLD);
        if (wName == null) return;
        World w = Bukkit.getWorld(wName);
        if (w == null) return;

        double x = getConfig().getDouble(CFG_DROP_X);
        double y = getConfig().getDouble(CFG_DROP_Y);
        double z = getConfig().getDouble(CFG_DROP_Z);
        Location dropLoc = new Location(w, x, y, z);

        Chunk chunk = dropLoc.getChunk();
        if (!chunk.isLoaded()) {
            chunk.load();
        }

        String uuidStr = getConfig().getString(CFG_DROP_UUID);
        if (uuidStr != null) {
            try {
                Entity e = Bukkit.getEntity(UUID.fromString(uuidStr));
                if (e != null) e.remove();
            } catch (Exception ignored) {}
        }

        clearDroppedLocation();
        respawnEgg();
    }

    private void respawnEgg() {
        if (spawnLocation == null) {
            loadSpawnLocation();
        }

        if (spawnLocation != null && spawnLocation.getWorld() != null) {
            if (!spawnLocation.getChunk().isLoaded()) {
                spawnLocation.getChunk().load();
            }

            Block block = spawnLocation.getBlock();
            block.setType(Material.DRAGON_EGG, false);

            saveEggBlockLocation(spawnLocation);
            clearDroppedLocation();
        }
    }

    private void loadSpawnLocation() {
        if (getConfig().contains(CFG_SPAWN_WORLD)) {
            String wName = getConfig().getString(CFG_SPAWN_WORLD);
            if (wName != null) {
                World w = Bukkit.getWorld(wName);
                if (w != null) {
                    spawnLocation = new Location(w,
                            getConfig().getDouble(CFG_SPAWN_X),
                            getConfig().getDouble(CFG_SPAWN_Y),
                            getConfig().getDouble(CFG_SPAWN_Z)
                    );
                }
            }
        }
    }

    private void incrementStat(UUID uuid, String statName) {
        String path = "stats." + uuid.toString() + "." + statName;
        int current = getConfig().getInt(path, 0);
        getConfig().set(path, current + 1);
        saveConfig();
    }
}