package me.foesio.foKits.api;

/** Count of cooldown timestamps cleared, or provider state when unavailable. */
public record KitResetBatchOutcome(KitResetResult result, int resetCount) {
    public KitResetBatchOutcome {
        if (result == null) throw new IllegalArgumentException("result");
        if (resetCount < 0) throw new IllegalArgumentException("resetCount");
    }
}
