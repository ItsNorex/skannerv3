package dev.blockscanner.tracers;

import dev.blockscanner.BlockScannerPlugin;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.Material;
import org.bukkit.block.Block;

import java.util.List;
import java.util.Set;

/**
 * Finds who ignited a fire that burned a block.
 *
 * CoreProtect logs fire-spread blocks with player="#fire".
 * We backtrack to find the original flint-and-steel ignition.
 */
public class FireSourceTracer {

    private static final Set<Material> FIRE_TYPES = Set.of(Material.FIRE, Material.SOUL_FIRE);

    private final BlockScannerPlugin plugin;

    public FireSourceTracer(BlockScannerPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Returns the player who ignited the fire that burned this block,
     * or null if not fire-caused or source unknown.
     * Call async!
     */
    public String findFireSource(Block block) {
        CoreProtectAPI api = plugin.getCoreProtectAPI();
        if (api == null) return null;

        int days      = plugin.getConfig().getInt("scanner.lookup-days", 2);
        int radius    = plugin.getConfig().getInt("tracers.fire-spread-radius", 20);
        int windowSec = plugin.getConfig().getInt("tracers.fire-window-sec", 600);

        // Step 1 — find time when this block was destroyed by "#fire"
        long fireDestroyTime = findFireDestroyTime(block, days * 86400, api);
        if (fireDestroyTime <= 0) return null;

        // Step 2 — search nearby for a fire block *placed* by a real player before destroy time
        //           (direct ignition: flint-and-steel or fire charge)
        String directIgniter = findDirectIgnition(block, fireDestroyTime, windowSec, radius, api);
        if (directIgniter != null) return directIgniter;

        return null;
    }

    private long findFireDestroyTime(Block block, int secondsBack, CoreProtectAPI api) {
        List<String[]> history = api.blockLookup(block, secondsBack);
        if (history == null) return -1;

        for (String[] entry : history) {
            CoreProtectAPI.ParseResult parsed = api.parseResult(entry);
            if (parsed == null) continue;
            if (parsed.getActionId() == 0
                    && parsed.getPlayer().startsWith("#fire")) {
                return parsed.getTime();
            }
        }
        return -1;
    }

    private String findDirectIgnition(Block center, long beforeTime,
                                       int windowSec, int radius, CoreProtectAPI api) {
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -2; dy <= 8; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    Block check = center.getRelative(dx, dy, dz);
                    List<String[]> history = api.blockLookup(check, windowSec);
                    if (history == null) continue;

                    for (String[] entry : history) {
                        CoreProtectAPI.ParseResult parsed = api.parseResult(entry);
                        if (parsed == null) continue;

                        if (parsed.getType() != null
                                && FIRE_TYPES.contains(parsed.getType())
                                && parsed.getActionId() == 1 // placed
                                && !parsed.getPlayer().startsWith("#")
                                && parsed.getTime() <= beforeTime
                                && parsed.getTime() >= beforeTime - windowSec) {
                            return parsed.getPlayer();
                        }
                    }
                }
            }
        }
        return null;
    }
}
