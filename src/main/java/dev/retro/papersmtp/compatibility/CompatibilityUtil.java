package dev.retro.papersmtp.compatibility;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class CompatibilityUtil {
    private static String serverVersion;
    private static boolean isLegacy; // True if pre-1.13 (1.8.8 - 1.12.2)

    static {
        try {
            String packageName = Bukkit.getServer().getClass().getPackage().getName();
            serverVersion = packageName.substring(packageName.lastIndexOf('.') + 1);
        } catch (Exception e) {
            serverVersion = "unknown";
        }

        // Check if modern gray stained glass pane exists to distinguish legacy vs modern materials
        isLegacy = true;
        for (Material mat : Material.values()) {
            if (mat.name().equals("GRAY_STAINED_GLASS_PANE")) {
                isLegacy = false;
                break;
            }
        }
    }

    public static String getServerVersion() {
        return serverVersion;
    }

    public static boolean isLegacy() {
        return isLegacy;
    }

    @SuppressWarnings("deprecation")
    public static ItemStack createItem(String modernMaterial, int legacyData, String displayName, List<String> lore) {
        Material material = null;
        if (!isLegacy) {
            try {
                material = Material.valueOf(modernMaterial);
            } catch (IllegalArgumentException ignored) {}
        }

        if (material == null) {
            String legacyMaterialName = getLegacyMaterialName(modernMaterial);
            try {
                material = Material.valueOf(legacyMaterialName);
            } catch (IllegalArgumentException e) {
                material = Material.BOOK; // safe fallback
            }
        }

        ItemStack item;
        if (isLegacy && (material.name().equals("WOOL") || material.name().equals("STAINED_GLASS_PANE") || material.name().equals("INK_SACK"))) {
            item = new ItemStack(material, 1, (short) legacyData);
        } else {
            item = new ItemStack(material, 1);
        }

        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(displayName);
            if (lore != null) {
                meta.setLore(lore);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private static String getLegacyMaterialName(String modernMaterial) {
        switch (modernMaterial) {
            case "GRAY_STAINED_GLASS_PANE":
                return "STAINED_GLASS_PANE";
            case "RED_WOOL":
            case "ORANGE_WOOL":
            case "GREEN_WOOL":
                return "WOOL";
            case "WRITABLE_BOOK":
                return "BOOK_AND_QUILL";
            default:
                return modernMaterial;
        }
    }

    public static void playSound(Player player, String modernSound, String legacySound) {
        try {
            String soundName = isLegacy ? legacySound : modernSound;
            org.bukkit.Sound sound = org.bukkit.Sound.valueOf(soundName);
            player.playSound(player.getLocation(), sound, 1.0f, 1.0f);
        } catch (Exception ignored) {}
    }
}
