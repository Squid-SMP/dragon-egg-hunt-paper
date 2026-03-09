package com.zartu.dragonegghunt;

import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

public class DehCommandManager {
    public final String CMD_TRACK = "eggtrack";
    public final String CMD_SET_SPAWN = "eggsetspawn";
    public final String CMD_RESET = "eggreset";
    public final String CMD_LEADERBOARD = "eggleaderboard";
    public final String CMD_LOCATION = "egglocation";

    private final DragonEggHunt plugin;
    private DehEggManager eggManager;
    private DehLeaderboard leaderboard;

    public DehCommandManager(DragonEggHunt plugin) {
        this.plugin = plugin;

        registerCommand(CMD_TRACK);
        registerCommand(CMD_SET_SPAWN);
        registerCommand(CMD_RESET);
        registerCommand(CMD_LEADERBOARD);
        registerCommand(CMD_LOCATION);
    }

    public void setEggManager(DehEggManager eggManager) {
        this.eggManager = eggManager;
    }

    public void setLeaderboard(DehLeaderboard leaderboard) {
        this.leaderboard = leaderboard;
    }

    private void registerCommand(String name) {
        PluginCommand command = plugin.getCommand(name);
        if (command != null) {
            command.setExecutor(plugin);
        } else {
            plugin.log.warning("Command '" + name + "' is missing from plugin.yml!");
        }
    }

    public boolean processCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player p)) {
            return true;
        }

        String commandName = command.getName();

        switch (commandName) {
            case CMD_RESET:
                eggManager.resetEgg(p);
                return true;

            case CMD_SET_SPAWN:
                Location playerLoc = p.getLocation();
                eggManager.setSpawnLocation(playerLoc);

                double x = playerLoc.getBlockX();
                double y = playerLoc.getBlockY();
                double z = playerLoc.getBlockZ();

                p.sendMessage(NamedTextColor.GREEN + "Spawn location set! (Snapped to block coordinates: " + x + ", " + y + ", " + z + ")");
                return true;

            case CMD_TRACK:
                eggManager.trackEggLogic(p);
                return true;

            case CMD_LEADERBOARD:
                leaderboard.showLeaderboard(p);
                return true;

            case CMD_LOCATION:
                Location loc;
                eggManager.loadPlacedLocation();
                loc = eggManager.placedLocation;

                if (loc == null)
                {
                    eggManager.loadSpawnLocation();
                    loc = eggManager.spawnLocation;
                }

                p.sendMessage(NamedTextColor.GRAY + "Current location of the egg is: " + loc.toString());
                return true;
        }

        return false;
    }
}
