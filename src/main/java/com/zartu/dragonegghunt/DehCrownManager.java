package com.zartu.dragonegghunt;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class DehCrownManager {
    public NamespacedKey crownKey;

    private final DragonEggHunt plugin;
    private FileConfiguration config;

    public DehCrownManager(DragonEggHunt plugin) {
        this.plugin = plugin;
        config = plugin.getConfig();

        crownKey = new NamespacedKey(plugin, "ctf_dragon_egg_crown");
    }

    public void giveCrownToPlayer(Player player) {
        ItemStack crown = createCrown();
        PlayerInventory inventory = player.getInventory();

        var helmet = inventory.getHelmet();

        inventory.setHelmet(crown);

        if (helmet == null) {
            return;
        }

        if (inventory.firstEmpty() == -1) {
            player.getWorld().dropItem(player.getLocation(), helmet);
        }
        else {
            inventory.addItem(helmet);
        }
    }

    public void removeCrownFromOfflinePlayer(OfflinePlayer offlinePlayer) {
        Player player = offlinePlayer.getPlayer();

        if (player != null) {
            removeCrownFromPlayer(player);
            return;
        }

        plugin.crownOfflinePlayers.add(offlinePlayer.getUniqueId().toString());
        config.set(plugin.CFG_CROWN_OFFLINE_PLAYERS, plugin.crownOfflinePlayers);
        plugin.saveConfig();
    }

    public void removeCrownFromPlayer(Player player) {
        PlayerInventory inventory = player.getInventory();

        if (isDragonCrown(inventory.getHelmet())) {
            inventory.setHelmet(null);
        }
    }

    private ItemStack createCrown() {
        ItemStack crown = new ItemStack(Material.ENDER_EYE);
        ItemMeta meta = crown.getItemMeta();

        var customModelData = meta.getCustomModelDataComponent();
        customModelData.setFloats(List.of(2f));
        meta.setCustomModelDataComponent(customModelData);

        TextComponent textComponent = Component.text("Dragon Crown").color(NamedTextColor.DARK_PURPLE);
        meta.customName(textComponent);

        var equippable = meta.getEquippable();
        equippable.setSlot(EquipmentSlot.HEAD);
        meta.setEquippable(equippable);

        AttributeModifier modifier = new AttributeModifier(new NamespacedKey(plugin, "armor"), 3.0, AttributeModifier.Operation.ADD_NUMBER);
        meta.addAttributeModifier(Attribute.ARMOR, modifier);

        meta.addEnchant(Enchantment.PROTECTION, 4, false);
        meta.addEnchant(Enchantment.RESPIRATION, 3, false);
        meta.addEnchant(Enchantment.AQUA_AFFINITY, 1, false);

        meta.setEnchantmentGlintOverride(false);

        meta.getPersistentDataContainer().set(crownKey, PersistentDataType.BYTE, (byte) 1);

        crown.setItemMeta(meta);

        return crown;
    }

    public boolean isDragonCrown(ItemStack item) {
        if (item == null || item.getType() != Material.ENDER_EYE) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();
        if (meta == null) {
            return false;
        }

        return meta.getPersistentDataContainer().has(crownKey, PersistentDataType.BYTE);
    }
}
