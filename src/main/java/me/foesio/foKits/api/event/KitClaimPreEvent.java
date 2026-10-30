package me.foesio.foKits.api.event;

import me.foesio.foKits.api.KitSnapshot;
import org.bukkit.event.Cancellable;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.entity.Player;

/** Fired after FoKits validation and before inventory, claim data, or rewards change. */
public final class KitClaimPreEvent extends Event implements Cancellable {
    private static final HandlerList HANDLERS = new HandlerList();
    private final Player player;
    private final KitSnapshot kit;
    private boolean cancelled;

    public KitClaimPreEvent(Player player, KitSnapshot kit, boolean asynchronous) {
        super(asynchronous);
        this.player = player;
        this.kit = kit;
    }

    public Player player() { return player; }
    public KitSnapshot kit() { return kit; }
    @Override public boolean isCancelled() { return cancelled; }
    @Override public void setCancelled(boolean cancelled) { this.cancelled = cancelled; }
    @Override public HandlerList getHandlers() { return HANDLERS; }
    public static HandlerList getHandlerList() { return HANDLERS; }
}
