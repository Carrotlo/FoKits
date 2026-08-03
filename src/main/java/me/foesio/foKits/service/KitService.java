package me.foesio.foKits.service;

import me.foesio.core.command.CommandPlaceholders;
import me.foesio.core.inventory.InventoryDepositResult;
import me.foesio.core.inventory.InventoryDepositService;
import me.foesio.core.inventory.OverflowPolicy;
import me.foesio.core.message.FoMessageService;
import me.foesio.foKits.config.PluginSettings;
import me.foesio.foKits.model.ClaimMode;
import me.foesio.foKits.model.ClaimResult;
import me.foesio.foKits.model.ClaimResultType;
import me.foesio.foKits.model.KitDefinition;
import me.foesio.foKits.model.KitViewState;
import me.foesio.foKits.storage.KitRepository;
import me.foesio.foKits.storage.UserDataRepository;
import me.foesio.foKits.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class KitService {
    private final JavaPlugin plugin;
    private final PluginSettings settings;
    private final KitRepository kitRepository;
    private final UserDataRepository userDataRepository;
    private final FoMessageService messages;
    private final InventoryDepositService inventoryDeposits;

    public KitService(
            JavaPlugin plugin,
            PluginSettings settings,
            KitRepository kitRepository,
            UserDataRepository userDataRepository,
            FoMessageService messages,
            InventoryDepositService inventoryDeposits
    ) {
        this.plugin = plugin;
        this.settings = settings;
        this.kitRepository = kitRepository;
        this.userDataRepository = userDataRepository;
        this.messages = messages;
        this.inventoryDeposits = inventoryDeposits;
    }

    public ClaimResult evaluate(Player player, KitDefinition kit) {
        if (!kit.isEnabled()) {
            return ClaimResult.of(ClaimResultType.DISABLED, kit);
        }

        if (!kit.getRequiredPermission().isBlank() && !player.hasPermission(kit.getRequiredPermission())) {
            return ClaimResult.permission(kit, kit.getRequiredPermission());
        }

        if (kit.isWorldDenied(player.getWorld().getName())) {
            return ClaimResult.of(ClaimResultType.WORLD_BLOCKED, kit);
        }

        if (kit.getClaimMode() == ClaimMode.ONE_TIME && userDataRepository.hasClaimedOnce(player.getUniqueId(), kit.getKey())) {
            return ClaimResult.of(ClaimResultType.CLAIMED_ONCE, kit);
        }

        if (kit.getClaimMode() == ClaimMode.COOLDOWN) {
            long remaining = remainingMillis(player, kit);
            if (remaining > 0L) {
                return ClaimResult.cooldown(kit, remaining);
            }
        }

        PreparedItems preparedItems = prepareItems(player.getInventory(), kit);
        if (!settings.overrideFullInventories() && !inventoryDeposits.canFitAll(player, preparedItems.storageItems())) {
            return ClaimResult.of(ClaimResultType.NO_SPACE, kit);
        }

        return ClaimResult.success(kit);
    }

    public ClaimResult claim(Player player, KitDefinition kit) {
        ClaimResult evaluation = evaluate(player, kit);
        if (evaluation.type() != ClaimResultType.SUCCESS) {
            return evaluation;
        }

        PreparedItems preparedItems = prepareItems(player.getInventory(), kit);
        PlayerInventory inventory = player.getInventory();

        if (!preparedItems.storageItems().isEmpty()) {
            OverflowPolicy overflowPolicy = settings.overrideFullInventories()
                    ? OverflowPolicy.DROP_OVERFLOW
                    : OverflowPolicy.BLOCK_COLLECTION;
            InventoryDepositResult depositResult = inventoryDeposits.deposit(
                    player,
                    preparedItems.storageItems(),
                    player.getLocation(),
                    overflowPolicy,
                    false
            );
            if (depositResult.blocked()) {
                return ClaimResult.of(ClaimResultType.NO_SPACE, kit);
            }
        }

        if (preparedItems.helmet() != null) {
            inventory.setHelmet(preparedItems.helmet());
        }
        if (preparedItems.chestplate() != null) {
            inventory.setChestplate(preparedItems.chestplate());
        }
        if (preparedItems.leggings() != null) {
            inventory.setLeggings(preparedItems.leggings());
        }
        if (preparedItems.boots() != null) {
            inventory.setBoots(preparedItems.boots());
        }
        if (preparedItems.offhand() != null) {
            inventory.setItemInOffHand(preparedItems.offhand());
        }

        long now = System.currentTimeMillis();
        userDataRepository.setClaim(player.getUniqueId(), kit, now);

        executeClaimCommands(player, kit);
        if (kit.isBroadcastOnClaim()) {
            plugin.getServer().broadcastMessage(messages.render(
                    "claim-broadcast",
                    "{prefix}{muted}{player} claimed {theme}{kit}{muted}.",
                    Map.of(
                            "{player}", player.getName(),
                            "{kit}", kit.getDisplayOrKey()
                    )
            ));
        }

        return ClaimResult.success(kit);
    }

    public void sendClaimFeedback(Player player, ClaimResult result) {
        String kitName = result.kit().getDisplayOrKey();
        switch (result.type()) {
            case SUCCESS -> messages.send(player, "claim-success", Map.of("{kit}", kitName));
            case DISABLED -> messages.send(player, "claim-disabled", Map.of("{kit}", kitName));
            case MISSING_PERMISSION -> messages.send(player, "claim-missing-permission", Map.of(
                    "{kit}", kitName,
                    "{permission}", result.missingPermission()
            ));
            case WORLD_BLOCKED -> messages.send(player, "claim-world-blocked", Map.of("{kit}", kitName));
            case COOLDOWN -> messages.send(player, "claim-cooldown", Map.of(
                    "{kit}", kitName,
                    "{time_remaining}", TimeUtil.formatDuration(result.remainingMillis())
            ));
            case CLAIMED_ONCE -> messages.send(player, "claim-once-used", Map.of("{kit}", kitName));
            case NO_SPACE -> messages.send(player, "claim-no-space", Map.of("{kit}", kitName));
        }
    }

    public KitViewState getViewState(Player player, KitDefinition kit) {
        ClaimResult result = evaluateForView(player, kit);
        return switch (result.type()) {
            case SUCCESS -> KitViewState.AVAILABLE;
            case COOLDOWN -> KitViewState.COOLDOWN;
            case MISSING_PERMISSION -> KitViewState.NO_PERMISSION;
            case CLAIMED_ONCE -> KitViewState.CLAIMED_ONCE;
            case DISABLED -> KitViewState.DISABLED;
            case WORLD_BLOCKED -> KitViewState.WORLD_BLOCKED;
            case NO_SPACE -> KitViewState.AVAILABLE;
        };
    }

    public long remainingMillis(Player player, KitDefinition kit) {
        if (kit.getClaimMode() != ClaimMode.COOLDOWN) {
            return 0L;
        }
        long last = userDataRepository.getLastClaim(player.getUniqueId(), kit.getKey());
        long next = last + kit.getCooldownMillis();
        return Math.max(0L, next - System.currentTimeMillis());
    }

    public boolean hasClaimedOnce(Player player, KitDefinition kit) {
        return userDataRepository.hasClaimedOnce(player.getUniqueId(), kit.getKey());
    }

    private ClaimResult evaluateForView(Player player, KitDefinition kit) {
        if (!kit.isEnabled()) {
            return ClaimResult.of(ClaimResultType.DISABLED, kit);
        }
        if (!kit.getRequiredPermission().isBlank() && !player.hasPermission(kit.getRequiredPermission())) {
            return ClaimResult.permission(kit, kit.getRequiredPermission());
        }
        if (kit.isWorldDenied(player.getWorld().getName())) {
            return ClaimResult.of(ClaimResultType.WORLD_BLOCKED, kit);
        }
        if (kit.getClaimMode() == ClaimMode.ONE_TIME && hasClaimedOnce(player, kit)) {
            return ClaimResult.of(ClaimResultType.CLAIMED_ONCE, kit);
        }
        long remaining = remainingMillis(player, kit);
        if (remaining > 0L) {
            return ClaimResult.cooldown(kit, remaining);
        }
        return ClaimResult.success(kit);
    }

    private void executeClaimCommands(Player player, KitDefinition kit) {
        for (String command : kit.getCommandsOnClaim()) {
            if (command == null || command.isBlank()) {
                continue;
            }
            String resolved = CommandPlaceholders.apply(command, Map.of(
                    "player", player.getName(),
                    "uuid", player.getUniqueId().toString(),
                    "kit", kit.getKey()
            )).trim();
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), resolved);
        }
    }

    private PreparedItems prepareItems(PlayerInventory inventory, KitDefinition kit) {
        List<ItemStack> storageItems = new ArrayList<>();

        ItemStack helmet = mapArmor(kit.getArmorItems(), 0, inventory.getHelmet(), storageItems);
        ItemStack chest = mapArmor(kit.getArmorItems(), 1, inventory.getChestplate(), storageItems);
        ItemStack legs = mapArmor(kit.getArmorItems(), 2, inventory.getLeggings(), storageItems);
        ItemStack boots = mapArmor(kit.getArmorItems(), 3, inventory.getBoots(), storageItems);
        ItemStack offhand = mapHand(kit.getOffhandItem(), inventory.getItemInOffHand(), storageItems);

        for (ItemStack item : kit.getInventoryItems()) {
            if (item == null || item.getType().isAir() || item.getAmount() <= 0) {
                continue;
            }
            storageItems.add(item.clone());
        }

        return new PreparedItems(helmet, chest, legs, boots, offhand, storageItems);
    }

    private ItemStack mapArmor(ItemStack[] armor, int index, ItemStack current, List<ItemStack> storageItems) {
        if (armor == null || index >= armor.length) {
            return null;
        }
        ItemStack item = armor[index];
        if (item == null || item.getType().isAir()) {
            return null;
        }
        if (current == null || current.getType().isAir()) {
            return item.clone();
        }
        storageItems.add(item.clone());
        return null;
    }

    private ItemStack mapHand(ItemStack offhandItem, ItemStack current, List<ItemStack> storageItems) {
        if (offhandItem == null || offhandItem.getType().isAir()) {
            return null;
        }
        if (current == null || current.getType().isAir()) {
            return offhandItem.clone();
        }
        storageItems.add(offhandItem.clone());
        return null;
    }

    public String formatRemaining(Player player, KitDefinition kit) {
        return TimeUtil.formatDuration(remainingMillis(player, kit));
    }

    public String placeholdersForKit(Player player, String key, String token) {
        return switch (token.toLowerCase(Locale.ROOT)) {
            case "time_remaining" -> formatRemaining(player, key);
            case "time_remaining_seconds" -> String.valueOf(remainingSeconds(player, key));
            case "can_claim" -> String.valueOf(canClaim(player, key));
            case "next_claim_at" -> String.valueOf(nextClaimEpochSeconds(player, key));
            case "claimed_once" -> String.valueOf(claimedOnce(player, key));
            default -> "";
        };
    }

    public String formatRemaining(Player player, String key) {
        KitDefinition kit = pluginKitByKey(key);
        if (kit == null) {
            return "0s";
        }
        return formatRemaining(player, kit);
    }

    public long remainingSeconds(Player player, String key) {
        KitDefinition kit = pluginKitByKey(key);
        if (kit == null) {
            return 0L;
        }
        return remainingMillis(player, kit) / 1000L;
    }

    public boolean canClaim(Player player, String key) {
        KitDefinition kit = pluginKitByKey(key);
        return kit != null && evaluate(player, kit).type() == ClaimResultType.SUCCESS;
    }

    public long nextClaimEpochSeconds(Player player, String key) {
        KitDefinition kit = pluginKitByKey(key);
        if (kit == null) {
            return 0L;
        }
        long remaining = remainingMillis(player, kit);
        if (remaining <= 0L) {
            return 0L;
        }
        return (System.currentTimeMillis() + remaining) / 1000L;
    }

    public boolean claimedOnce(Player player, String key) {
        KitDefinition kit = pluginKitByKey(key);
        return kit != null && hasClaimedOnce(player, kit);
    }

    private KitDefinition pluginKitByKey(String key) {
        return kitRepository.get(key).orElse(null);
    }

    private record PreparedItems(
            ItemStack helmet,
            ItemStack chestplate,
            ItemStack leggings,
            ItemStack boots,
            ItemStack offhand,
            List<ItemStack> storageItems
    ) {
    }
}
