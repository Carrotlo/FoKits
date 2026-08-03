package me.foesio.foKits.command;

import me.foesio.core.command.FoAdminArguments;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foKits.FoKits;
import me.foesio.foKits.gui.GuiManager;
import me.foesio.foKits.model.KitDefinition;
import me.foesio.foKits.storage.UserDataRepository;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class FoKitsAdminCommand implements CommandExecutor, TabCompleter {
    private final FoKits plugin;
    private final GuiManager guiManager;
    private final FoMessageService messages;
    private final UserDataRepository userDataRepository;
    private final UpdateNoticeService updates;

    public FoKitsAdminCommand(
            FoKits plugin,
            GuiManager guiManager,
            FoMessageService messages,
            UserDataRepository userDataRepository,
            UpdateNoticeService updates
    ) {
        this.plugin = plugin;
        this.guiManager = guiManager;
        this.messages = messages;
        this.userDataRepository = userDataRepository;
        this.updates = updates;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            messages.send(sender, "invalid-admin-usage");
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("version")) {
            updates.checkAndSendVersion(sender);
            return true;
        }

        if (sub.equals("reload")) {
            FoKits.ReloadSummary summary = plugin.reloadPluginData();
            if (!summary.result().successful()) {
                messages.send(sender, "reload-failed",
                        "{prefix}{bad}Reload failed at {theme}{step}{bad}. {muted}{error}",
                        Map.of(
                                "{step}", summary.result().failedStep(),
                                "{error}", summary.result().errorMessage()
                        ));
                return true;
            }
            messages.send(sender, "reload-success", Map.of(
                    "{kit_count}", String.valueOf(summary.loadedKits()),
                    "{config_version}", String.valueOf(summary.configVersion())
            ));
            return true;
        }

        if (sub.equals("editor")) {
            if (!(sender instanceof Player player)) {
                messages.send(sender, "players-only");
                return true;
            }
            guiManager.openAdminRoot(player);
            messages.send(player, "editor-opened");
            return true;
        }

        if (sub.equals("resetcooldown")) {
            if (args.length < 2) {
                messages.send(sender, "admin-resetcooldown-usage");
                return true;
            }

            OfflinePlayer target = plugin.getServer().getPlayerExact(args[1]);
            if (target == null) {
                target = plugin.getServer().getOfflinePlayer(args[1]);
            }

            if (!target.isOnline() && !target.hasPlayedBefore()) {
                messages.send(sender, "player-not-found", Map.of("{player}", args[1]));
                return true;
            }

            String playerName = target.getName() == null ? args[1] : target.getName();
            if (args.length >= 3) {
                Optional<KitDefinition> optionalKit = plugin.getKitRepository().get(args[2]);
                if (optionalKit.isEmpty()) {
                    messages.send(sender, "claim-kit-not-found", Map.of("{kit}", args[2]));
                    return true;
                }

                KitDefinition kit = optionalKit.get();
                userDataRepository.resetCooldown(target.getUniqueId(), kit.getKey());
                messages.send(sender, "admin-reset-kit-cooldown-success", Map.of(
                        "{player}", playerName,
                        "{kit}", kit.getDisplayOrKey()
                ));
                return true;
            }

            int resetCount = userDataRepository.resetCooldowns(target.getUniqueId());
            messages.send(sender, "admin-reset-cooldown-success", Map.of(
                    "{player}", playerName,
                    "{count}", String.valueOf(resetCount)
            ));
            return true;
        }

        messages.send(sender, "unknown-admin-arg");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return FoAdminArguments.completeOptions(List.of("version", "reload", "editor", "resetcooldown"), args[0]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("resetcooldown")) {
            return FoAdminArguments.onlinePlayer().complete(args[1]);
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("resetcooldown")) {
            List<String> kitKeys = plugin.getKitRepository().getAll().stream()
                    .map(KitDefinition::getKey)
                    .toList();
            return FoAdminArguments.completeOptions(kitKeys, args[2]);
        }
        return Collections.emptyList();
    }
}
