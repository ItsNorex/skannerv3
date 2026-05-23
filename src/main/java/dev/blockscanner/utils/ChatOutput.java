package dev.blockscanner.utils;

import dev.blockscanner.BlockScannerPlugin;
import dev.blockscanner.model.BlockAction;
import dev.blockscanner.model.TraceResult;

import org.bukkit.block.Block;
import org.bukkit.entity.Player;

import java.util.List;

/**
 * Handles all chat output for the scanner.
 * Uses Spigot's TextComponent API for clickable elements.
 */
public class ChatOutput {

    private final BlockScannerPlugin plugin;

    public ChatOutput(BlockScannerPlugin plugin) {
        this.plugin = plugin;
    }

    // ── Block scan result ─────────────────────────────────────────────────────

    /**
     * Sends the initial block scan result to the detective.
     * Shows up to 3 traces with the block info.
     */
    public void sendScanResult(Player detective, Block block, List<TraceResult> traces,
                               String explosionSource, String fireSource) {
        String p = plugin.prefix();

        detective.sendMessage("");
        detective.sendMessage(p + "§eScanned: §f" + prettyBlock(block)
                + " §8at §7" + coords(block));
        detective.sendMessage(p + "§8─────────────────────────");

        // Explosion / fire — источник называется прямо, это факт о блоке а не о следах
        if (explosionSource != null) {
            detective.sendMessage(p + "§c⚡ Destroyed by explosion");
            detective.sendMessage(p + "§c   TNT placed by: §f§l" + explosionSource);
        }
        if (fireSource != null) {
            detective.sendMessage(p + "§6🔥 Destroyed by fire");
            detective.sendMessage(p + "§6   Ignited by: §f§l" + fireSource);
        }
        if (explosionSource != null || fireSource != null) {
            detective.sendMessage(p + "§8─────────────────────────");
        }

        int days = plugin.getConfig().getInt("scanner.lookup-days", 2);

        if (traces.isEmpty() && explosionSource == null && fireSource == null) {
            detective.sendMessage(p + "§7No activity found in the last §e" + days + " §7days.");
            detective.sendMessage("");
            return;
        }

        if (!traces.isEmpty()) {
            // Только число — никаких имён
            detective.sendMessage(p + "§7Found §e§l" + traces.size()
                    + " §r§7trace(s) over the last §e" + days + " §7day(s).");
        }

        detective.sendMessage("");
        detective.sendMessage(p + "§8Right-click suspects in the world to identify them.");
        detective.sendMessage("");
    }

    // ── Player identification result ──────────────────────────────────────────

    /**
     * Sends the detailed history of one suspect on the scanned block.
     * Called after the detective right-clicks a player with the bound scanner.
     */
    /**
     * Called when detective clicks a suspect.
     * Returns true if the suspect had interactions (scanner should be consumed),
     * false if no match (scanner stays).
     */
    public boolean sendPlayerHistory(Player detective, String suspectName,
                                     Block block, List<BlockAction> actions) {
        String p = plugin.prefix();

        detective.sendMessage("");
        detective.sendMessage(p + "§eIdentifying: §f§l" + suspectName);
        detective.sendMessage(p + "§7Block: §f" + prettyBlock(block)
                + " §8at §7" + coords(block));
        detective.sendMessage(p + "§8─────────────────────────");

        if (actions.isEmpty()) {
            detective.sendMessage(p + "§a✔ §f§l" + suspectName
                    + " §7did not interact with this block.");
            detective.sendMessage(p + "§8Scanner intact — keep investigating.");
            detective.sendMessage("");
            return false; // scanner survives
        }

        detective.sendMessage(p + "§c⚠ §e" + actions.size()
                + " §7recorded interaction(s) found:");
        detective.sendMessage("");

        int limit = plugin.getConfig().getInt("scanner.max-actions-per-player", 5);
        List<BlockAction> shown = actions.subList(0, Math.min(limit, actions.size()));

        for (BlockAction action : shown) {
            detective.sendMessage(
                    "    " + action.icon() + " §7" + action.verb()
                    + " §f" + action.prettyBlock()
                    + " §8— " + action.formattedTime()
            );
        }

        if (actions.size() > limit) {
            detective.sendMessage("    §8... and " + (actions.size() - limit) + " more.");
        }

        detective.sendMessage("");
        detective.sendMessage(p + "§8Scanner consumed. Investigation complete.");
        detective.sendMessage("");
        return true; // scanner consumed
    }

    // ── Misc messages ─────────────────────────────────────────────────────────

    public void sendAlreadyBound(Player p, Block block) {
        p.sendMessage(plugin.prefix()
                + "§7Scanner is bound to §e" + prettyBlock(block)
                + " §8(" + coords(block) + ")§7. Right-click a player.");
    }

    public void sendScanning(Player p) {
        p.sendMessage(plugin.prefix() + "§eAnalysing block history...");
    }

    public void sendNoPermission(Player p) {
        p.sendMessage(plugin.prefix() + "§cYou don't have permission to use this.");
    }

    public void sendCPUnavailable(Player p) {
        p.sendMessage(plugin.prefix() + "§cCoreProtect is not available.");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String prettyBlock(Block b) {
        return b.getType().name().replace("_", " ").toLowerCase();
    }

    private static String coords(Block b) {
        return b.getX() + ", " + b.getY() + ", " + b.getZ();
    }
}
