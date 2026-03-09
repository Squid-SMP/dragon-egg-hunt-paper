package com.zartu.dragonegghunt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
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
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DragonEggHunt extends JavaPlugin implements Listener, CommandExecutor {

    public final Map<UUID, Long> pickupTimes = new HashMap<>();

    public final String CFG_SPAWN_WORLD = "spawn.world";
    public final String CFG_SPAWN_X = "spawn.x";
    public final String CFG_SPAWN_Y = "spawn.y";
    public final String CFG_SPAWN_Z = "spawn.z";

    public final String CFG_BLOCK_WORLD = "placed_egg.world";
    public final String CFG_BLOCK_X = "placed_egg.x";
    public final String CFG_BLOCK_Y = "placed_egg.y";
    public final String CFG_BLOCK_Z = "placed_egg.z";

    public final String CFG_DROP_WORLD = "dropped_egg.world";
    public final String CFG_DROP_X = "dropped_egg.x";
    public final String CFG_DROP_Y = "dropped_egg.y";
    public final String CFG_DROP_Z = "dropped_egg.z";
    public final String CFG_DROP_TIME = "dropped_egg.time";
    public final String CFG_DROP_UUID = "dropped_egg.uuid";

    private DehEggManager eggManager;
    private DehCommandManager commandManager;
    private DehLeaderboard leaderboard;

    public Server server;

    @Override
    public void onEnable() {
        server = getServer();

        eggManager = new DehEggManager(this);
        commandManager = new DehCommandManager(this);
        leaderboard = new DehLeaderboard(this);

        eggManager.setLeaderboard(leaderboard);
        commandManager.setEggManager(eggManager);
        commandManager.setLeaderboard(leaderboard);


        saveDefaultConfig();
        eggManager.loadSpawnLocation();

        server.getPluginManager().registerEvents(this, this);
    }



    @EventHandler
    public void onEntityPortal(EntityPortalEvent event) {
        if (event.getEntity() instanceof Item item) {
            if (eggManager.isSpecialEgg(item.getItemStack())) {
                event.setCancelled(true);
                item.remove();
                eggManager.respawnEgg();
                broadcast("The Artifact has disintegrated and has returned to its Shrine!", NamedTextColor.RED);
            }
        } else if (event.getEntity() instanceof FallingBlock fb) {
            if (fb.getBlockData().getMaterial() == Material.DRAGON_EGG) {
                event.setCancelled(true);
                fb.remove();
                eggManager.respawnEgg();
                broadcast("The Artifact has disintegrated and has returned to its Shrine!", NamedTextColor.RED);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDimensionJump(PlayerTeleportEvent event) {
        Location to = event.getTo();
        Location from = event.getFrom();
        if (to.getWorld() == null || from.getWorld() == null) {
            return;

        }
        if (from.getWorld().equals(to.getWorld())) {
            return;
        }

        Player p = event.getPlayer();
        boolean hasEgg = false;

        for (ItemStack item : p.getInventory().getContents()) {
            if (eggManager.isSpecialEgg(item)) {
                hasEgg = true;
                break;
            }
        }

        if (hasEgg) {
            World.Environment toEnv = to.getWorld().getEnvironment();
            String spawnWorldName = getConfig().getString(CFG_SPAWN_WORLD);
            boolean isGoingHome = spawnWorldName != null && to.getWorld().getName().equals(spawnWorldName);

            if (toEnv == World.Environment.THE_END) {
                eggManager.stripAndRespawnEgg(p);
                p.sendMessage(NamedTextColor.RED + "The Artifact cannot enter The End!");
                return;
            }

            if (toEnv == World.Environment.NETHER) {
                return;
            }

            if (toEnv == World.Environment.NORMAL && isGoingHome) {
                return;
            }

            eggManager.stripAndDropEgg(p, from);
        }
    }

    @EventHandler
    public void onItemFrameInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof ItemFrame) {
            ItemStack hand = event.getPlayer().getInventory().getItem(event.getHand());
            if (eggManager.isSpecialEgg(hand)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage(NamedTextColor.RED + "The Artifact cannot be framed!");
            }
        }
    }

    @EventHandler
    public void onBundleInput(InventoryClickEvent event) {
        ItemStack cursor = event.getCursor();
        if (eggManager.isSpecialEgg(cursor)) {
            ItemStack current = event.getCurrentItem();
            if (current != null && current.getType() == Material.BUNDLE) {
                event.setCancelled(true);
                event.getWhoClicked().sendMessage(NamedTextColor.RED + "The Artifact is too powerful for a bundle!");
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        if (eggManager.isSpecialEgg(event.getItemDrop().getItemStack())) {
            eggManager.saveDroppedLocation(event.getItemDrop());
        }
    }

    @EventHandler
    public void onHopperPickup(InventoryPickupItemEvent event) {
        if (eggManager.isSpecialEgg(event.getItem().getItemStack())) event.setCancelled(true);
    }

    @EventHandler
    public void onHopperMove(InventoryMoveItemEvent event) {
        if (eggManager.isSpecialEgg(event.getItem())) event.setCancelled(true);
    }

    @EventHandler
    public void onContainerStore(InventoryClickEvent event) {
        if (event.getClickedInventory() == null) return;
        boolean isContainer = event.getView().getTopInventory().getType() != InventoryType.CRAFTING;
        ItemStack current = event.getCurrentItem();
        ItemStack cursor = event.getCursor();

        if ((eggManager.isSpecialEgg(current) || eggManager.isSpecialEgg(cursor)) && isContainer) {
            if (event.getClickedInventory() == event.getView().getTopInventory() || event.isShiftClick()) {
                event.setCancelled(true);
                event.getWhoClicked().sendMessage(NamedTextColor.RED + "The Artifact refuses to be contained!");
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
        if (eggManager.isSpecialEgg(event.getEntity().getItemStack())) {
            event.setCancelled(true);
            event.getEntity().remove();
            eggManager.respawnEgg();
            broadcast("The Artifact was abandoned and has returned to its shrine!", NamedTextColor.RED);
        }
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (event.getEntity() instanceof Item itemEntity && eggManager.isSpecialEgg(itemEntity.getItemStack())) {
            if (event.getCause() == EntityDamageEvent.DamageCause.LAVA ||
                    event.getCause() == EntityDamageEvent.DamageCause.FIRE ||
                    event.getCause() == EntityDamageEvent.DamageCause.FIRE_TICK ||
                    event.getCause() == EntityDamageEvent.DamageCause.BLOCK_EXPLOSION ||
                    event.getCause() == EntityDamageEvent.DamageCause.ENTITY_EXPLOSION ||
                    event.getCause() == EntityDamageEvent.DamageCause.VOID) {

                event.setCancelled(true);
                itemEntity.remove();
                eggManager.respawnEgg();
                broadcast( "The Artifact was destroyed by elements and has respawned!", NamedTextColor.RED);
            }
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        Player p = event.getPlayer();
        pickupTimes.remove(p.getUniqueId());
        for (ItemStack item : p.getInventory().getContents()) {
            if (item != null && eggManager.isSpecialEgg(item)) {
                p.getInventory().remove(item);
                Item dropped = p.getWorld().dropItemNaturally(p.getLocation(), item);
                eggManager.saveDroppedLocation(dropped);
                broadcast("The Artifact has been dropped because the holder fled the world!", NamedTextColor.YELLOW);
            }
        }
    }

    @EventHandler
    public void onPlayerPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p && eggManager.isSpecialEgg(event.getItem().getItemStack())) {
            p.sendMessage(NamedTextColor.GREEN + "You have seized the Artifact! Keep it safe!");
            broadcast("⚠ The Artifact has been picked up! ⚠", NamedTextColor.DARK_RED);
            pickupTimes.put(p.getUniqueId(), System.currentTimeMillis());
            leaderboard.incrementStat(p.getUniqueId(), "captures");
            eggManager.clearDroppedLocation();
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (eggManager.isSpecialEgg(event.getItemInHand())) {
            event.setCancelled(false);
            event.setBuild(true);
            eggManager.saveEggBlockLocation(event.getBlock().getLocation());
            eggManager.clearDroppedLocation();
            event.getPlayer().sendMessage(NamedTextColor.GOLD + "The Artifact is secured. Tracking active.");
            broadcast("The Artifact has been placed in the world!", NamedTextColor.GOLD);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.DRAGON_EGG) {
            if (eggManager.isSavedEggLocation(event.getBlock().getLocation())) {
                event.setCancelled(false);
                event.setDropItems(false);
                Item dropped = event.getBlock().getWorld().dropItemNaturally(event.getBlock().getLocation(), eggManager.createSpecialEgg());
                eggManager.saveDroppedLocation(dropped);
                eggManager.clearEggBlockLocation();
                broadcast( "⚠ The Artifact has been dislodged! ⚠", NamedTextColor.DARK_RED);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEggInteract(PlayerInteractEvent event) {
        if (event.getClickedBlock() != null && event.getClickedBlock().getType() == Material.DRAGON_EGG) {
            event.setCancelled(true);
            if (event.getAction().toString().contains("LEFT_CLICK")) {
                if (eggManager.isSavedEggLocation(event.getClickedBlock().getLocation())) {
                    event.getClickedBlock().setType(Material.AIR);
                    Item dropped = event.getClickedBlock().getWorld().dropItemNaturally(
                            event.getClickedBlock().getLocation(),
                            eggManager.createSpecialEgg()
                    );
                    eggManager.saveDroppedLocation(dropped);
                    eggManager.clearEggBlockLocation();
                    broadcast("⚠ The Artifact has been stolen! ⚠", NamedTextColor.DARK_RED);
                }
            }
        }
    }

    @EventHandler
    public void onFallingBlockLand(EntityChangeBlockEvent event) {
        if (event.getEntity() instanceof FallingBlock) {
            if (((FallingBlock) event.getEntity()).getBlockData().getMaterial() == Material.DRAGON_EGG) {
                eggManager.saveEggBlockLocation(event.getBlock().getLocation());
                eggManager.clearDroppedLocation();
            }
        }
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        return commandManager.processCommand(sender, command, label, args);
    }

    public void broadcast(String text, TextColor color) {
        TextComponent textComponent = Component.text(text).color(color);
        server.broadcast(textComponent);
    }
}