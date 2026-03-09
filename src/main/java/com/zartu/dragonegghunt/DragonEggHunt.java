package com.zartu.dragonegghunt;

import net.kyori.adventure.audience.Audience;
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
import java.util.logging.Logger;

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

    private DehEggManager eggManager;
    private DehCommandManager commandManager;
    private DehLeaderboard leaderboard;

    public Server server;
    public Logger log;

    @Override
    public void onEnable() {
        server = getServer();
        log = getLogger();

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

            switch (toEnv) {
                case World.Environment.THE_END:
                    eggManager.stripAndRespawnEgg(p);
                    sendMessage(p, "The Artifact cannot enter The End!", NamedTextColor.RED);
                    break;
                case World.Environment.NETHER:
                    eggManager.stripAndRespawnEgg(p);
                    sendMessage(p, "The Artifact cannot enter The Nether!", NamedTextColor.RED);
            }
        }
    }

    @EventHandler
    public void onItemFrameInteract(PlayerInteractEntityEvent event) {
        if (event.getRightClicked() instanceof ItemFrame) {
            ItemStack hand = event.getPlayer().getInventory().getItem(event.getHand());
            if (eggManager.isSpecialEgg(hand)) {
                event.setCancelled(true);
                sendMessage(event.getPlayer(), "The Artifact cannot be framed!", NamedTextColor.RED);
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
                sendMessage(event.getWhoClicked(), "The Artifact is too powerful for a bundle!", NamedTextColor.RED);
            }
        }
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Item droppedItem = event.getItemDrop();
        ItemStack droppedItemStack = droppedItem.getItemStack();
        if (!eggManager.isSpecialEgg(droppedItemStack)) {
            return;
        }

        droppedItem.remove();

        eggManager.respawnEgg();

        broadcast("The Artifact was dropped and has returned to its shrine!", NamedTextColor.RED);
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
                sendMessage(event.getWhoClicked(), "The Artifact refuses to be contained!", NamedTextColor.RED);
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
            if (item == null || !eggManager.isSpecialEgg(item)) {
                continue;
            }
            p.getInventory().remove(item);

            eggManager.respawnEgg();

            broadcast("The Artifact has respawned because the holder fled the world!", NamedTextColor.YELLOW);
        }
    }

    @EventHandler
    public void onPlayerPickup(EntityPickupItemEvent event) {
        if (event.getEntity() instanceof Player p && eggManager.isSpecialEgg(event.getItem().getItemStack())) {
            sendMessage(p,"You have seized the Artifact! Keep it safe!", NamedTextColor.GREEN);
            broadcast("⚠ The Artifact has been picked up! ⚠", NamedTextColor.DARK_RED);
            pickupTimes.put(p.getUniqueId(), System.currentTimeMillis());
            leaderboard.incrementStat(p.getUniqueId(), "captures");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        if (eggManager.isSpecialEgg(event.getItemInHand())) {
            event.setCancelled(false);
            event.setBuild(true);
            eggManager.saveEggBlockLocation(event.getBlock().getLocation());
            sendMessage(event.getPlayer(), "The Artifact is secured. Tracking active.", NamedTextColor.GOLD);
            broadcast("The Artifact has been placed in the world!", NamedTextColor.GOLD);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        if (event.getBlock().getType() == Material.DRAGON_EGG) {
            if (eggManager.isSavedEggLocation(event.getBlock().getLocation())) {
                event.setCancelled(false);
                event.setDropItems(false);
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

    public void sendMessage(Audience audience, String text, TextColor color) {
        TextComponent textComponent = Component.text(text).color(color);
        audience.sendMessage(textComponent);
    }
}