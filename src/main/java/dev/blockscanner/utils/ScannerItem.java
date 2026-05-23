package dev.blockscanner.utils;

import dev.blockscanner.BlockScannerPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class ScannerItem {

    public static final NamespacedKey KEY_SCANNER =
            new NamespacedKey(BlockScannerPlugin.getInstance(), "block_scanner");

    /** Key that stores the bound block coords as "world;x;y;z" after scanning */
    public static final NamespacedKey KEY_BOUND =
            new NamespacedKey(BlockScannerPlugin.getInstance(), "scanner_bound");

    public static ItemStack create() {
        BlockScannerPlugin plugin = BlockScannerPlugin.getInstance();
        Material mat = Material.matchMaterial(
                plugin.getConfig().getString("scanner.material", "STICK"));
        if (mat == null) mat = Material.STICK;

        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.setCustomModelData(plugin.getConfig().getInt("scanner.custom-model-data", 1001));
        meta.setDisplayName("§6§lBlock Scanner");
        meta.setLore(List.of(
                "§7Right-click a §eblock §7to scan it.",
                "§7Right-click a §eplayer §7to identify them.",
                "",
                "§8Single-use detective tool."
        ));
        meta.getPersistentDataContainer().set(KEY_SCANNER, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isScanner(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;
        return meta.getPersistentDataContainer().has(KEY_SCANNER, PersistentDataType.BYTE);
    }

    /** Returns "world;x;y;z" if bound, null otherwise */
    public static String getBoundLocation(ItemStack item) {
        if (!isScanner(item)) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        return meta.getPersistentDataContainer().get(KEY_BOUND, PersistentDataType.STRING);
    }

    /** Stores binding coords and updates lore to show bound state */
    public static void bind(ItemStack item, String worldName, int x, int y, int z) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        String coords = worldName + ";" + x + ";" + y + ";" + z;
        meta.getPersistentDataContainer().set(KEY_BOUND, PersistentDataType.STRING, coords);
        meta.setDisplayName("§6§lBlock Scanner §8[§eBound§8]");
        meta.setLore(List.of(
                "§7Bound to: §f" + x + ", " + y + ", " + z,
                "§7World: §f" + worldName,
                "",
                "§eRight-click a player §7to identify.",
                "§8Single-use. Will be consumed."
        ));
        item.setItemMeta(meta);
    }

    public static boolean isBound(ItemStack item) {
        return getBoundLocation(item) != null;
    }
}
