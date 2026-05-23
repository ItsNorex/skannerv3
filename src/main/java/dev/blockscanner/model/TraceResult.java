package dev.blockscanner.model;

import java.util.ArrayList;
import java.util.List;

/** Aggregated activity of one player on one block. */
public class TraceResult {

    private final String playerName;
    private final List<BlockAction> actions = new ArrayList<>();

    public TraceResult(String playerName) {
        this.playerName = playerName;
    }

    public void addAction(BlockAction action) {
        actions.add(action);
    }

    public String getPlayerName() { return playerName; }
    public List<BlockAction> getActions() { return actions; }

    public long getLastActionTime() {
        return actions.isEmpty() ? 0L : actions.get(0).time();
    }

    /** Most recent verb for the brief trace line */
    public String lastVerb() {
        return actions.isEmpty() ? "was here" : actions.get(0).verb();
    }

    /** Most recent formatted timestamp */
    public String lastTime() {
        return actions.isEmpty() ? "?" : actions.get(0).formattedTime();
    }
}
