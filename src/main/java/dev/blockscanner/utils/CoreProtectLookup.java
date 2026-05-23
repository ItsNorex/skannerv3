package dev.blockscanner.utils;

import dev.blockscanner.BlockScannerPlugin;
import dev.blockscanner.model.BlockAction;
import dev.blockscanner.model.TraceResult;
import net.coreprotect.CoreProtectAPI;
import org.bukkit.block.Block;
import org.bukkit.block.Container;

import java.util.*;

/**
 * Wraps CoreProtect lookups.
 * Always call from an async thread.
 */
public class CoreProtectLookup {

    private final BlockScannerPlugin plugin;

    public CoreProtectLookup(BlockScannerPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * Returns up to maxTraces players who interacted with this block,
     * each with up to maxPerPlayer actions, newest first.
     *
     * Handles both regular blocks and containers (chests etc.) —
     * for containers we also run a session lookup to catch open/close + item movements.
     */
    public List<TraceResult> lookup(Block block) {
        CoreProtectAPI api = plugin.getCoreProtectAPI();
        if (api == null || !api.isEnabled()) return Collections.emptyList();

        int days        = plugin.getConfig().getInt("scanner.lookup-days", 2);
        int maxTraces   = plugin.getConfig().getInt("scanner.max-traces", 3);
        int maxPerPlayer= plugin.getConfig().getInt("scanner.max-actions-per-player", 5);
        int secondsBack = days * 86400;

        // LinkedHashMap preserves insertion order (CoreProtect → newest first)
        Map<String, TraceResult> byPlayer = new LinkedHashMap<>();

        // ── 1. Block place/break lookup ───────────────────────────────────────
        processEntries(api.blockLookup(block, secondsBack), api, byPlayer, maxPerPlayer, false);

        // ── 2. Container session lookup (open/close + item movements) ─────────
        // CoreProtect logs container interactions separately via sessionLookup
        if (block.getState() instanceof Container) {
            processEntries(api.sessionLookup(block, secondsBack), api, byPlayer, maxPerPlayer, true);
        }

        // Sort by most recent action, cap at maxTraces
        List<TraceResult> results = new ArrayList<>(byPlayer.values());
        results.removeIf(r -> r.getPlayerName().startsWith("#")); // strip environment entries
        results.sort(Comparator.comparingLong(TraceResult::getLastActionTime).reversed());
        return results.subList(0, Math.min(maxTraces, results.size()));
    }

    /**
     * Returns ALL actions for one specific player on the block (no cap on count).
     * Used when the detective clicks on a suspect.
     */
    public List<BlockAction> lookupPlayer(Block block, String playerName) {
        CoreProtectAPI api = plugin.getCoreProtectAPI();
        if (api == null || !api.isEnabled()) return Collections.emptyList();

        int days        = plugin.getConfig().getInt("scanner.lookup-days", 2);
        int secondsBack = days * 86400;

        List<BlockAction> actions = new ArrayList<>();

        collectForPlayer(api.blockLookup(block, secondsBack), api, playerName, actions, false);

        if (block.getState() instanceof Container) {
            collectForPlayer(api.sessionLookup(block, secondsBack), api, playerName, actions, true);
        }

        actions.sort(Comparator.comparingLong(BlockAction::time).reversed());
        return actions;
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void processEntries(List<String[]> raw, CoreProtectAPI api,
                                Map<String, TraceResult> byPlayer,
                                int maxPerPlayer, boolean isContainer) {
        if (raw == null) return;
        for (String[] entry : raw) {
            try {
                CoreProtectAPI.ParseResult parsed = api.parseResult(entry);
                if (parsed == null) continue;

                String player = parsed.getPlayer();
                BlockAction action = toAction(parsed, isContainer);

                TraceResult trace = byPlayer.computeIfAbsent(player, TraceResult::new);
                if (trace.getActions().size() < maxPerPlayer) {
                    trace.addAction(action);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("CoreProtect parse error: " + e.getMessage());
            }
        }
    }

    private void collectForPlayer(List<String[]> raw, CoreProtectAPI api,
                                  String playerName, List<BlockAction> out,
                                  boolean isContainer) {
        if (raw == null) return;
        for (String[] entry : raw) {
            try {
                CoreProtectAPI.ParseResult parsed = api.parseResult(entry);
                if (parsed == null) continue;
                if (!parsed.getPlayer().equalsIgnoreCase(playerName)) continue;
                out.add(toAction(parsed, isContainer));
            } catch (Exception e) {
                plugin.getLogger().warning("CoreProtect parse error: " + e.getMessage());
            }
        }
    }

    private BlockAction toAction(CoreProtectAPI.ParseResult parsed, boolean isContainer) {
        String typeName = parsed.getType() != null ? parsed.getType().name() : "UNKNOWN";
        int actionId = parsed.getActionId();

        // For container sessions: actionId 2 = interaction; isAdd tells direction
        // (CoreProtect: actionId 2 with isAdd=true → item inserted, false → item removed)
        boolean isAdd = (actionId == 1) || (actionId == 2 && isContainer);

        return new BlockAction(
                parsed.getTime(),
                parsed.getPlayer(),
                actionId,
                typeName,
                isAdd
        );
    }
}
