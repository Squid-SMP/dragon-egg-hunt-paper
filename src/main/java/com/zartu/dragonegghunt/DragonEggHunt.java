package com.zartu.dragonegghunt;

import com.destroystokyo.paper.event.block.BlockDestroyEvent;
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
import org.bukkit.entity.*;
import org.bukkit.event.EventPriority;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.entity.EntityDamageEvent.DamageCause;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

public class DragonEggHunt extends JavaPlugin implements Listener, CommandExecutor {

    public final int MIN_HEIGHT = 62;

    public final String CFG_SPAWN_WORLD = "spawn.world";
    public final String CFG_SPAWN_X = "spawn.x";
    public final String CFG_SPAWN_Y = "spawn.y";
    public final String CFG_SPAWN_Z = "spawn.z";

    public final String CFG_BLOCK_WORLD = "placed_egg.world";
    public final String CFG_BLOCK_X = "placed_egg.x";
    public final String CFG_BLOCK_Y = "placed_egg.y";
    public final String CFG_BLOCK_Z = "placed_egg.z";

    public final String CFG_HOLDER_UUID = "egg_holder.uuid";
    public final String CFG_HOLDER_TIME = "egg_holder.time";
    public final String CFG_PLACER_UUID = "egg_placer.uuid";

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
        boolean respawnEgg = false;
        Entity entity = event.getEntity();

        if (entity instanceof Item item) {
            ItemStack itemStack = item.getItemStack();
            if (!eggManager.isSpecialEgg(itemStack)) {
                return;
            }

            event.setCancelled(true);
            item.remove();

            respawnEgg = true;
        }
        else if (entity instanceof FallingBlock fallingBlock) {
            if (fallingBlock.getBlockData().getMaterial() != Material.DRAGON_EGG) {
                return;
            }

            event.setCancelled(true);
            fallingBlock.remove();
            respawnEgg = true;
        }

        if (respawnEgg) {
            eggManager.respawnEgg();
            broadcast("The Artifact has disintegrated and has returned to its shrine!", NamedTextColor.DARK_PURPLE);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onDimensionJump(PlayerTeleportEvent event) {
        Location to = event.getTo();
        World toWorld = to.getWorld();
        Location from = event.getFrom();
        World fromWorld = from.getWorld();

        if (toWorld == null || fromWorld == null || fromWorld.equals(toWorld)) {
            return;
        }

        Player p = event.getPlayer();
        boolean hasEgg = false;

        ItemStack[] inventoryContent = p.getInventory().getContents();
        for (ItemStack item : inventoryContent) {
            if (eggManager.isSpecialEgg(item)) {
                hasEgg = true;
                break;
            }
        }

        if (!hasEgg) {
            return;
        }

        World.Environment toEnv = toWorld.getEnvironment();

        switch (toEnv) {
            case World.Environment.THE_END:
            case World.Environment.NETHER:
                eggManager.stripAndRespawnEgg(p);
                sendMessage(p, "The Artifact cannot enter another dimension!", NamedTextColor.RED);
                broadcast("The Artifact has disintegrated and has returned to its shrine!", NamedTextColor.DARK_PURPLE);
                break;
        }
    }

    @EventHandler
    public void onItemFrameInteract(PlayerInteractEntityEvent event) {
        Entity rightClickedEntity = event.getRightClicked();
        if (rightClickedEntity instanceof ItemFrame) {
            Player player = event.getPlayer();
            ItemStack itemInHand = player.getInventory().getItem(event.getHand());

            if (eggManager.isSpecialEgg(itemInHand)) {
                event.setCancelled(true);
                sendMessage(player, "The Artifact cannot be framed!", NamedTextColor.RED);
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

        broadcast("The Artifact was dropped and has returned to its shrine!", NamedTextColor.DARK_PURPLE);
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
            if (b.getType() == Material.DRAGON_EGG) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent event) {
        for (Block b : event.getBlocks()) {
            if (b.getType() == Material.DRAGON_EGG) {
                event.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack itemInHand = event.getItemInHand();
        if (eggManager.isSpecialEgg(itemInHand)) {
            Location location = event.getBlock().getLocation();
            Player player = event.getPlayer();

            if (location.getY() <= MIN_HEIGHT) {
                sendMessage(player, "The Artifact cannot be placed below sea level!", NamedTextColor.RED);
                event.setCancelled(true);
                return;
            }

            event.setCancelled(false);
            event.setBuild(true);

            eggManager.onEggPlaced(player, location);

            sendMessage(event.getPlayer(), "The Artifact is secured. Tracking active.", NamedTextColor.GOLD);
            broadcast("The Artifact has been placed in the world!", NamedTextColor.GOLD);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEggInteract(PlayerInteractEvent event) {
        Block clickedBlock = event.getClickedBlock();
        Player player = event.getPlayer();

        if (clickedBlock == null || clickedBlock.getType() != Material.DRAGON_EGG) {
            return;
        }

        event.setCancelled(true);

        if (!event.getAction().toString().contains("LEFT_CLICK")) {
            return;
        }

        Location blockLocation = clickedBlock.getLocation();
        clickedBlock.setType(Material.AIR);

        if (eggManager.isSavedEggLocation(blockLocation)) {
            ItemStack item = eggManager.createSpecialEgg();
            forceIntoMainHand(player, item);

            eggManager.clearEggBlockLocation();
            broadcast("⚠ The Artifact has been stolen! ⚠", NamedTextColor.DARK_RED);

            eggManager.onEggPickedUp(player);
        }
        else {
            ItemStack egg = new ItemStack(Material.DRAGON_EGG);
            clickedBlock.getWorld().dropItemNaturally(clickedBlock.getLocation(), egg);
        }
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent event) {
        handleExplosion(event.blockList());
    }

    private void handleExplosion(List<Block> blockList) {
        for (Block block : blockList) {
            if (block.getType() == Material.DRAGON_EGG) {
                blockList.remove(block);
                block.setType(Material.AIR);
                eggManager.respawnEgg();
                broadcast("The Artifact was destroyed and has returned to its shrine!", NamedTextColor.DARK_PURPLE);
                return;
            }
        }
    }

    @EventHandler
    public void onFallingBlockSpawn(EntitySpawnEvent event) {
        Entity entity = event.getEntity();
        if (entity instanceof FallingBlock falling) {
            if (falling.getBlockData().getMaterial() == Material.DRAGON_EGG) {
                event.setCancelled(true);
                entity.getLocation().getBlock().setType(Material.DRAGON_EGG);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        if (event.getChangedType() == Material.DRAGON_EGG) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        List<ItemStack> drops = event.getDrops();

        drops.removeIf(item -> item != null && eggManager.isSpecialEgg(item));

        eggManager.respawnEgg();

        broadcast("The Artifact was lost and has returned to its shrine!", NamedTextColor.DARK_PURPLE);
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

    public void forceIntoMainHand(Player p, ItemStack itemStack) {
        PlayerInventory inventory = p.getInventory();
        ItemStack mainHandItem = inventory.getItemInMainHand();
        inventory.setItemInMainHand(itemStack);

        int firstEmptyIdx = inventory.firstEmpty();
        if (firstEmptyIdx != -1) {
            inventory.addItem(mainHandItem);
        }
        else {
            p.dropItem(mainHandItem);
        }
    }
}