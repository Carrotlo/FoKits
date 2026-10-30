package me.foesio.foKits.api;

/** Result of checking or requesting a kit claim. */
public enum KitClaimStatus {
    AVAILABLE,
    SUCCESS,
    NOT_FOUND,
    PLAYER_OFFLINE,
    PROVIDER_DISABLED,
    DISABLED,
    MISSING_PERMISSION,
    WORLD_BLOCKED,
    COOLDOWN,
    CLAIMED_ONCE,
    NO_SPACE,
    CANCELLED,
    BUSY
}
