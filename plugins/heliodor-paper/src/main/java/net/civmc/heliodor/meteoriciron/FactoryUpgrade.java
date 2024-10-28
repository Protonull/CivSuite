package net.civmc.heliodor.meteoriciron;

import net.civmc.heliodor.AnvilRepairListener;
import net.civmc.heliodor.HeliodorPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemRarity;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import java.util.List;

public interface FactoryUpgrade {

    NamespacedKey FACTORY_UPGRADE_KEY = new NamespacedKey(JavaPlugin.getPlugin(HeliodorPlugin.class), "factory_upgrade");

    static ItemStack createUpgrade() {
        ItemStack pickaxe = new ItemStack(Material.REDSTONE_TORCH);
        Damageable meta = (Damageable) pickaxe.getItemMeta();

        meta.displayName(Component.text("Factory Upgrade", NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        meta.setRarity(ItemRarity.EPIC);
        meta.lore(List.of(
            Component.text("Factories can be upgraded with one of two paths:", NamedTextColor.AQUA).decoration(TextDecoration.ITALIC, false),
            Component.text("Charcoal consumption: ", NamedTextColor.GOLD).append(Component.text("1/4 -> 1/8 -> 1/12 -> 1/16", NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false),
            Component.text("Factory speed: ", NamedTextColor.GOLD).append(Component.text("x2 -> x3 -> x4 -> x5", NamedTextColor.YELLOW)).decoration(TextDecoration.ITALIC, false)
        ));
        meta.setFireResistant(true);
        meta.setEnchantmentGlintOverride(true);
        meta.getPersistentDataContainer().set(FACTORY_UPGRADE_KEY, PersistentDataType.BOOLEAN, true);
        AnvilRepairListener.setNoCombine(meta);
        pickaxe.setItemMeta(meta);
        return pickaxe;
    }


    static List<ShapedRecipe> getRecipes(Plugin plugin) {
        return List.of(
            new ShapedRecipe(new NamespacedKey(plugin, "factory_upgrade"), FactoryUpgrade.createUpgrade())
                .shape("#c#", "#r#", "#f#")
                .setIngredient('#', MeteoricIron.createIngot())
                .setIngredient('c', Material.CHEST)
                .setIngredient('r', Material.CRAFTING_TABLE)
                .setIngredient('f', Material.FURNACE)
        );
    }
}