package me.foesio.foKits.api.event;

import me.foesio.foKits.api.KitSnapshot;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;

/** Fired after items and claim state change, before configured commands and broadcast. */
public final class KitClaimedEvent extends Event {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final KitSnapshot kit;
    private final long claimedAtEpochMillis;

    public KitClaimedEvent(Player player, KitSnapshot kit, long claimedAtEpochMillis, boolean asynchronous) {
        super(asynchronous);
        this.player = player;
        this.kit = kit;
        this.claimedAtEpochMillis = claimedAtEpochMillis;
    }

    public Player player() { return player; }
    public KitSnapshot kit() { return kit; }
    public long claimedAtEpochMillis() { return claimedAtEpochMillis; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
