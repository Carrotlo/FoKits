package me.foesio.foKits.api;

/** Result of clearing saved player claim data. */
public enum KitResetResult {
    RESET,
    UNCHANGED,
    NOT_FOUND,
    PERSISTENCE_FAILED,
    PROVIDER_DISABLED
}
