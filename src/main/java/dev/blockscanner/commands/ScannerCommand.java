package dev.blockscanner.commands;

import dev.blockscanner.BlockScannerPlugin;
import dev.blockscanner.utils.ScannerItem;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class ScannerCommand implements CommandExecutor {

    private final BlockScannerPlugin plugin;

    public ScannerCommand(BlockScannerPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can use this.");
            return true;
        }
        if (!player.hasPermission("blockscanner.give")) {
            player.sendMessage(plugin.prefix() + "§cNo permission.");
            return true;
        }
        player.getInventory().addItem(ScannerItem.create());
        player.sendMessage(plugin.prefix() + "§aYou received a §6§lBlock Scanner§a!");
        return true;
    }
}
