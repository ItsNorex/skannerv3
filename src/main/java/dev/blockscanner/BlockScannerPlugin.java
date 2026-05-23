package dev.blockscanner;

import dev.blockscanner.commands.ScannerCommand;
import dev.blockscanner.listeners.ScannerListener;
import net.coreprotect.CoreProtect;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public class BlockScannerPlugin extends JavaPlugin {

    private static BlockScannerPlugin instance;
    private CoreProtectAPI coreProtectAPI;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        if (!hookCoreProtect()) {
            getLogger().severe("CoreProtect not found or API too old! Disabling.");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        getLogger().info("CoreProtect API hooked (v" + coreProtectAPI.APIVersion() + ").");

        getServer().getPluginManager().registerEvents(new ScannerListener(this), this);

        var cmd = getCommand("scanner");
        if (cmd != null) {
            var handler = new ScannerCommand(this);
            cmd.setExecutor(handler);
        }

        getLogger().info("BlockScanner v2 enabled.");
    }

    @Override
    public void onDisable() {
        getLogger().info("BlockScanner disabled.");
    }

    private boolean hookCoreProtect() {
        Plugin plugin = getServer().getPluginManager().getPlugin("CoreProtect");
        if (!(plugin instanceof CoreProtect cp)) return false;
        CoreProtectAPI api = cp.getAPI();
        if (!api.isEnabled() || api.APIVersion() < 9) return false;
        this.coreProtectAPI = api;
        return true;
    }

    public CoreProtectAPI getCoreProtectAPI() { return coreProtectAPI; }

    public static BlockScannerPlugin getInstance() { return instance; }

    /** Shorthand for colored prefix from config */
    public String prefix() {
        return color(getConfig().getString("prefix", "&8[&6Scanner&8]&r "));
    }

    public static String color(String s) {
        return s == null ? "" : s.replace("&", "§");
    }
}
