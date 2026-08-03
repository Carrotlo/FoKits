package me.foesio.foKits.command;

import me.foesio.core.message.FoMessageService;
import me.foesio.foKits.gui.GuiManager;
import me.foesio.foKits.model.KitDefinition;
import me.foesio.foKits.service.KitService;
import me.foesio.foKits.storage.KitRepository;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public class FoKitsCommand implements CommandExecutor, TabCompleter {
    private final GuiManager guiManager;
    private final FoMessageService messages;
    private final KitRepository kits;
    private final KitService kitService;

    public FoKitsCommand(GuiManager guiManager, FoMessageService messages, KitRepository kits, KitService kitService) {
        this.guiManager = guiManager;
        this.messages = messages;
        this.kits = kits;
        this.kitService = kitService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            messages.send(sender, "players-only");
            return true;
        }

        if (args.length == 0) {
            guiManager.openPlayerKits(player);
            return true;
        }

        String requestedKey = KitDefinition.sanitizeKey(args[0]);
        Optional<KitDefinition> optionalKit = kits.get(requestedKey);
        if (optionalKit.isEmpty()) {
            messages.send(player, "claim-kit-not-found", Map.of("{kit}", args[0]));
            return true;
        }

        kitService.sendClaimFeedback(player, kitService.claim(player, optionalKit.get()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        String needle = args[0].toLowerCase(Locale.ROOT);
        Player player = sender instanceof Player p ? p : null;
        List<String> matches = new ArrayList<>();
        for (KitDefinition kit : kits.getAll()) {
            if (player != null && !kit.getRequiredPermission().isBlank() && !player.hasPermission(kit.getRequiredPermission())) {
                continue;
            }
            String key = kit.getKey();
            if (key.toLowerCase(Locale.ROOT).startsWith(needle)) {
                matches.add(key);
            }
        }
        return matches;
    }
}
