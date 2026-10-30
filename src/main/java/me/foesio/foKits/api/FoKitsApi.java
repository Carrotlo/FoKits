package me.foesio.foKits.api;

import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Supported FoKits integration surface, available through Bukkit ServicesManager. */
public interface FoKitsApi {
    /** Current stored kit definitions, returned as detached snapshots. */
    List<KitSnapshot> listKits();

    Optional<KitSnapshot> findKit(String key);

    /** FoKits' current rules only; other plugins may still veto the actual claim. */
    KitClaimOutcome evaluateClaim(Player player, String key);

    /** Requests a claim with normal kit restrictions and rewards, without sending FoKits feedback. */
    KitClaimOutcome claimKit(Player player, String key);

    /** Clears a saved cooldown, leaving the claimed-once flag untouched. */
    KitResetResult resetCooldown(UUID playerId, String key);

    /** Clears all saved cooldowns for this player, leaving claimed-once flags untouched. */
    KitResetBatchOutcome resetAllCooldowns(UUID playerId);

    /** Clears the claimed-once flag, leaving the last-claim timestamp untouched. */
    KitResetResult resetOneTimeClaim(UUID playerId, String key);

    /** Opens FoKits' player menu; false means the player or provider is unavailable. */
    boolean openKitsGui(Player player);
}
