package me.foesio.foKits.api;

/** An immediate result; AVAILABLE is returned only by evaluateClaim. */
public record KitClaimOutcome(KitClaimStatus status, long remainingMillis, String missingPermission) {
    public KitClaimOutcome {
        if (status == null) throw new IllegalArgumentException("status");
        remainingMillis = Math.max(0L, remainingMillis);
        missingPermission = missingPermission == null ? "" : missingPermission;
    }

    public boolean successful() {
        return status == KitClaimStatus.SUCCESS;
    }
}
