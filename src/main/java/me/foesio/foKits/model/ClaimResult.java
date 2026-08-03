package me.foesio.foKits.model;

public record ClaimResult(
        ClaimResultType type,
        KitDefinition kit,
        long remainingMillis,
        String missingPermission
) {
    public static ClaimResult success(KitDefinition kit) {
        return new ClaimResult(ClaimResultType.SUCCESS, kit, 0L, "");
    }

    public static ClaimResult of(ClaimResultType type, KitDefinition kit) {
        return new ClaimResult(type, kit, 0L, "");
    }

    public static ClaimResult cooldown(KitDefinition kit, long remainingMillis) {
        return new ClaimResult(ClaimResultType.COOLDOWN, kit, remainingMillis, "");
    }

    public static ClaimResult permission(KitDefinition kit, String permission) {
        return new ClaimResult(ClaimResultType.MISSING_PERMISSION, kit, 0L, permission == null ? "" : permission);
    }
}
