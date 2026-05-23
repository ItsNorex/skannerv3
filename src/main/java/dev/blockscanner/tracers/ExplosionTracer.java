package dev.blockscanner.tracers;

import dev.blockscanner.BlockScannerPlugin;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.block.Block;

import java.util.List;

/**
 * Finds who placed the TNT that destroyed a block.
 *
 * CoreProtect logs explosion-destroyed blocks with player="#explosion" or "#tnt".
 * We look for the most recent TNT *placement* near the site before the explosion time.
 */
public class ExplosionTracer {

    private final BlockScannerPlugin plugin;

    public ExplosionTracer(BlockScannerPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Checks if the block was destroyed by an explosion in CoreProtect history,
     * and if so returns the name of the player who placed the TNT.
     * Returns null if no explosion found or source unknown.
     * Call async!
     */
    public String findExplosionSource(Block block) {
        CoreProtectAPI api = plugin.getCoreProtectAPI();
        if (api == null) return null;

        int days = plugin.getConfig().getInt("scanner.lookup-days", 2);
        int radius = plugin.getConfig().getInt("tracers.explosion-radius", 12);
        int windowSec = plugin.getConfig().getInt("tracers.explosion-window-sec", 120);

        // Step 1 — find the explosion time: a removal entry on this block by "#explosion" or "#tnt"
        long explosionTime = findExplosionTime(block, days * 86400, api);
        if (explosionTime <= 0) return null;

        // Step 2 — search nearby blocks for TNT placement just before the explosion
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -4; dy <= 4; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block nearby = block.getRelative(dx, dy, dz);
                    List<String[]> history = api.blockLookup(
                            nearby.getX(), nearby.getY(), nearby.getZ(),
                            nearby.getWorld().getName(), windowSec);
                    if (history == null) continue;

                    for (String[] entry : history) {
                        CoreProtectAPI.ParseResult parsed = api.parseResult(entry);
                        if (parsed == null) continue;

                        // TNT placed by a real player before the explosion
                        if (parsed.getType() != null
                                && parsed.getType().name().contains("TNT")
                                && parsed.getActionId() == 1 // placed
                                && !parsed.getPlayer().startsWith("#")
                                && parsed.getTime() <= explosionTime
                                && parsed.getTime() >= explosionTime - windowSec) {
                            return parsed.getPlayer();
                        }
                    }
                }
            }
        }
        return null;
    }

    private long findExplosionTime(Block block, int secondsBack, CoreProtectAPI api) {
        List<String[]> history = api.blockLookup(
                block.getX(), block.getY(), block.getZ(),
                block.getWorld().getName(), secondsBack);
        if (history == null) return -1;

        for (String[] entry : history) {
            CoreProtectAPI.ParseResult parsed = api.parseResult(entry);
            if (parsed == null) continue;
            String player = parsed.getPlayer();
            if (parsed.getActionId() == 0 // block removed
                    && (player.equals("#explosion") || player.equals("#tnt")
                        || player.startsWith("#creeper") || player.startsWith("#wither"))) {
                return parsed.getTime();
            }
        }
        return -1;
    }
}
