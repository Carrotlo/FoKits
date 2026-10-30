package me.foesio.foKits.service;

import me.foesio.foKits.api.FoKitsApi;
import me.foesio.foKits.api.KitClaimOutcome;
import me.foesio.foKits.api.KitClaimStatus;
import me.foesio.foKits.api.KitResetResult;
import me.foesio.foKits.api.KitResetBatchOutcome;
import me.foesio.foKits.api.KitSnapshot;
import me.foesio.foKits.gui.GuiManager;
import me.foesio.foKits.model.ClaimResult;
import me.foesio.foKits.model.KitDefinition;
import me.foesio.foKits.storage.KitRepository;
import me.foesio.foKits.storage.UserDataRepository;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

public final class FoKitsApiImpl implements FoKitsApi {
    private final JavaPlugin plugin;
    private final KitRepository kits;
    private final KitService claims;
    private final UserDataRepository players;
    private final GuiManager gui;

    public FoKitsApiImpl(JavaPlugin plugin, KitRepository kits, KitService claims,
                         UserDataRepository players, GuiManager gui) {
        this.plugin = plugin;
        this.kits = kits;
        this.claims = claims;
        this.players = players;
        this.gui = gui;
    }

    @Override
    public List<KitSnapshot> listKits() {
        return plugin.isEnabled() ? kits.getAll().stream().map(KitSnapshots::of).toList() : List.of();
    }

    @Override
    public Optional<KitSnapshot> findKit(String key) {
        return plugin.isEnabled() ? kits.get(key).map(KitSnapshots::of) : Optional.empty();
    }

    @Override
    public KitClaimOutcome evaluateClaim(Player player, String key) {
        return evaluate(player, key, false);
    }

    @Override
    public KitClaimOutcome claimKit(Player player, String key) {
        return evaluate(player, key, true);
    }

    private KitClaimOutcome evaluate(Player player, String key, boolean claim) {
        Objects.requireNonNull(player, "player");
        if (!plugin.isEnabled()) return outcome(KitClaimStatus.PROVIDER_DISABLED);
        if (!player.isOnline()) return outcome(KitClaimStatus.PLAYER_OFFLINE);
        KitDefinition kit = kits.get(key).orElse(null);
        if (kit == null) return outcome(KitClaimStatus.NOT_FOUND);
        if (!player.hasPermission("fokits.use")) {
            return new KitClaimOutcome(KitClaimStatus.MISSING_PERMISSION, 0L, "fokits.use");
        }
        ClaimResult result = claim ? claims.claim(player, kit) : claims.evaluate(player, kit);
        KitClaimStatus status = switch (result.type()) {
            case SUCCESS -> claim ? KitClaimStatus.SUCCESS : KitClaimStatus.AVAILABLE;
            case DISABLED -> KitClaimStatus.DISABLED;
            case MISSING_PERMISSION -> KitClaimStatus.MISSING_PERMISSION;
            case WORLD_BLOCKED -> KitClaimStatus.WORLD_BLOCKED;
            case COOLDOWN -> KitClaimStatus.COOLDOWN;
            case CLAIMED_ONCE -> KitClaimStatus.CLAIMED_ONCE;
            case NO_SPACE -> KitClaimStatus.NO_SPACE;
            case CANCELLED -> KitClaimStatus.CANCELLED;
            case BUSY -> KitClaimStatus.BUSY;
            case NOT_FOUND -> KitClaimStatus.NOT_FOUND;
        };
        return new KitClaimOutcome(status, result.remainingMillis(), result.missingPermission());
    }

    private static KitClaimOutcome outcome(KitClaimStatus status) {
        return new KitClaimOutcome(status, 0L, "");
    }

    @Override
    public KitResetResult resetCooldown(UUID playerId, String key) {
        Objects.requireNonNull(playerId, "playerId");
        if (!plugin.isEnabled()) return KitResetResult.PROVIDER_DISABLED;
        KitDefinition kit = kits.get(key).orElse(null);
        if (kit == null) return KitResetResult.NOT_FOUND;
        return resetResult(players.resetCooldownChecked(playerId, kit.getKey()));
    }

    @Override
    public KitResetBatchOutcome resetAllCooldowns(UUID playerId) {
        Objects.requireNonNull(playerId, "playerId");
        if (!plugin.isEnabled()) return new KitResetBatchOutcome(KitResetResult.PROVIDER_DISABLED, 0);
        UserDataRepository.ResetBatch batch = players.resetCooldownsChecked(playerId);
        return new KitResetBatchOutcome(resetResult(batch.status()), batch.resetCount());
    }

    @Override
    public KitResetResult resetOneTimeClaim(UUID playerId, String key) {
        Objects.requireNonNull(playerId, "playerId");
        if (!plugin.isEnabled()) return KitResetResult.PROVIDER_DISABLED;
        KitDefinition kit = kits.get(key).orElse(null);
        if (kit == null) return KitResetResult.NOT_FOUND;
        return resetResult(players.resetOneTimeClaimChecked(playerId, kit.getKey()));
    }

    private static KitResetResult resetResult(UserDataRepository.ResetStatus status) {
        return switch (status) {
            case RESET -> KitResetResult.RESET;
            case UNCHANGED -> KitResetResult.UNCHANGED;
            case SAVE_FAILED -> KitResetResult.PERSISTENCE_FAILED;
        };
    }

    @Override
    public boolean openKitsGui(Player player) {
        Objects.requireNonNull(player, "player");
        if (!plugin.isEnabled() || !player.isOnline() || !player.hasPermission("fokits.use")) return false;
        gui.openPlayerKits(player);
        return true;
    }
}
