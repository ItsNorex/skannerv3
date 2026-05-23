package dev.blockscanner.listeners;

import dev.blockscanner.BlockScannerPlugin;
import dev.blockscanner.model.BlockAction;
import dev.blockscanner.model.TraceResult;
import dev.blockscanner.tracers.ExplosionTracer;
import dev.blockscanner.tracers.FireSourceTracer;
import dev.blockscanner.utils.ChatOutput;
import dev.blockscanner.utils.CoreProtectLookup;
import dev.blockscanner.utils.ScannerItem;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

import java.util.List;

public class ScannerListener implements Listener {

    private final BlockScannerPlugin plugin;
    private final CoreProtectLookup lookup;
    private final ChatOutput chat;
    private final ExplosionTracer explosionTracer;
    private final FireSourceTracer fireTracer;

    public ScannerListener(BlockScannerPlugin plugin) {
        this.plugin = plugin;
        this.lookup = new CoreProtectLookup(plugin);
        this.chat = new ChatOutput(plugin);
        this.explosionTracer = new ExplosionTracer(plugin);
        this.fireTracer = new FireSourceTracer(plugin);
    }

    // ── Right-click BLOCK ─────────────────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractBlock(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();
        ItemStack held = player.getInventory().getItemInMainHand();
        if (!ScannerItem.isScanner(held)) return;

        event.setCancelled(true);

        if (!player.hasPermission("blockscanner.use")) {
            chat.sendNoPermission(player);
            return;
        }

        Block block = event.getClickedBlock();
        if (block == null) return;

        // If already bound — remind them to click a player instead
        if (ScannerItem.isBound(held)) {
            String bound = ScannerItem.getBoundLocation(held);
            Block boundBlock = resolveBound(bound);
            if (boundBlock != null) {
                chat.sendAlreadyBound(player, boundBlock);
            }
            return;
        }

        // ── First use: bind to block and run lookup ────────────────────────
        chat.sendScanning(player);
        playEffect(player, block.getLocation(), false);

        // Capture block reference before going async
        final Block target = block;
        final String worldName = block.getWorld().getName();
        final int bx = block.getX(), by = block.getY(), bz = block.getZ();

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<TraceResult> traces = lookup.lookup(target);
            String explosionSrc = explosionTracer.findExplosionSource(target);
            String fireSrc      = fireTracer.findFireSource(target);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!player.isOnline()) return;

                // Bind the scanner item
                ItemStack current = player.getInventory().getItemInMainHand();
                if (!ScannerItem.isScanner(current)) return; // swapped out
                ScannerItem.bind(current, worldName, bx, by, bz);

                // Sound + particles on result
                playEffect(player, target.getLocation(), true);

                chat.sendScanResult(player, target, traces, explosionSrc, fireSrc);
            });
        });
    }

    // ── Right-click PLAYER (entity) ───────────────────────────────────────────

    @EventHandler(priority = EventPriority.HIGH)
    public void onInteractEntity(PlayerInteractEntityEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) return;
        if (!(event.getRightClicked() instanceof Player suspect)) return;

        Player detective = event.getPlayer();
        ItemStack held = detective.getInventory().getItemInMainHand();
        if (!ScannerItem.isScanner(held)) return;

        event.setCancelled(true);

        if (!detective.hasPermission("blockscanner.use")) {
            chat.sendNoPermission(detective);
            return;
        }

        if (!ScannerItem.isBound(held)) {
            detective.sendMessage(plugin.prefix()
                    + "§7Scan a §eblock §7first before identifying a player.");
            return;
        }

        String boundStr = ScannerItem.getBoundLocation(held);
        Block boundBlock = resolveBound(boundStr);
        if (boundBlock == null) {
            detective.sendMessage(plugin.prefix() + "§cCould not resolve bound block.");
            return;
        }

        final String suspectName = suspect.getName();
        final Block target = boundBlock;

        playIdentifyEffect(detective, suspect);

        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            List<BlockAction> actions = lookup.lookupPlayer(target, suspectName);

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (!detective.isOnline()) return;

                // sendPlayerHistory returns true = match found → consume scanner
                boolean matched = chat.sendPlayerHistory(detective, suspectName, target, actions);
                if (matched) {
                    consumeScanner(detective);
                }
            });
        });
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Parses "world;x;y;z" back into a Block.
     */
    private Block resolveBound(String bound) {
        if (bound == null) return null;
        String[] parts = bound.split(";");
        if (parts.length != 4) return null;
        try {
            var world = plugin.getServer().getWorld(parts[0]);
            if (world == null) return null;
            int x = Integer.parseInt(parts[1]);
            int y = Integer.parseInt(parts[2]);
            int z = Integer.parseInt(parts[3]);
            return world.getBlockAt(x, y, z);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Removes the scanner from the detective's main hand.
     */
    private void consumeScanner(Player player) {
        ItemStack held = player.getInventory().getItemInMainHand();
        if (ScannerItem.isScanner(held)) {
            if (held.getAmount() <= 1) {
                player.getInventory().setItemInMainHand(null);
            } else {
                held.setAmount(held.getAmount() - 1);
            }
        }
    }

    /** Particles + sound when scanning a block */
    private void playEffect(Player player, Location loc, boolean success) {
        Location center = loc.clone().add(0.5, 0.5, 0.5);
        if (success) {
            player.getWorld().spawnParticle(Particle.END_ROD, center, 12, 0.3, 0.3, 0.3, 0.05);
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_CLUSTER_BREAK, 0.6f, 1.4f);
        } else {
            player.getWorld().spawnParticle(Particle.WITCH, center, 8, 0.2, 0.2, 0.2, 0.02);
            player.playSound(player.getLocation(), Sound.ITEM_SPYGLASS_USE, 0.8f, 1.0f);
        }
    }

    /** Effect when identifying a player */
    private void playIdentifyEffect(Player detective, Player suspect) {
        // Sweep particles around the suspect
        suspect.getWorld().spawnParticle(
                Particle.SWEEP_ATTACK,
                suspect.getLocation().add(0, 1, 0),
                3, 0.3, 0.3, 0.3, 0
        );
        detective.playSound(detective.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.8f);
        detective.playSound(detective.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, 1.5f);
    }
}
