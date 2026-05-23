package dev.blockscanner.model;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * One logged action from CoreProtect.
 *
 * @param time       epoch seconds
 * @param player     player name (may start with "#" for environment)
 * @param actionId   0=broke, 1=placed, 2=interacted (container open/close)
 * @param blockType  material name
 * @param isAdd      true if item/block was added (placed, put-in), false if removed
 */
public record BlockAction(long time, String player, int actionId, String blockType, boolean isAdd) {

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("dd.MM.yy HH:mm").withZone(ZoneId.systemDefault());

    public String formattedTime() {
        return FMT.format(Instant.ofEpochSecond(time));
    }

    public boolean isEnvironment() {
        return player != null && player.startsWith("#");
    }

    /** Human-readable verb */
    public String verb() {
        return switch (actionId) {
            case 0 -> "broke";
            case 1 -> "placed";
            case 2 -> isAdd ? "put item in" : "took item from";
            default -> "touched";
        };
    }

    /** Icon character for chat display */
    public String icon() {
        return switch (actionId) {
            case 0 -> "§c✖";   // broke
            case 1 -> "§a✚";   // placed
            case 2 -> isAdd ? "§e▲" : "§e▼";  // container in/out
            default -> "§7•";
        };
    }

    public String prettyBlock() {
        if (blockType == null) return "Unknown";
        return blockType.replace("_", " ").toLowerCase();
    }
}
