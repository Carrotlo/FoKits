package me.foesio.foKits.placeholder;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import me.foesio.foKits.service.KitService;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

import java.util.LinkedHashMap;
import java.util.Map;

public class FoKitsPlaceholderExpansion extends PlaceholderExpansion {
    private final JavaPlugin plugin;
    private final KitService kitService;

    public FoKitsPlaceholderExpansion(JavaPlugin plugin, KitService kitService) {
        this.plugin = plugin;
        this.kitService = kitService;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "fokits";
    }

    @Override
    public @NotNull String getAuthor() {
        return "Carrotio";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer offlinePlayer, @NotNull String params) {
        Player player = offlinePlayer.getPlayer();
        if (player == null) {
            return "";
        }

        Map<String, String> tokens = new LinkedHashMap<>();
        tokens.put("time_remaining_seconds_", "time_remaining_seconds");
        tokens.put("time_remaining_", "time_remaining");
        tokens.put("can_claim_", "can_claim");
        tokens.put("next_claim_at_", "next_claim_at");
        tokens.put("claimed_once_", "claimed_once");

        for (Map.Entry<String, String> token : tokens.entrySet()) {
            if (params.startsWith(token.getKey())) {
                String kitKey = params.substring(token.getKey().length());
                if (kitKey.isBlank()) {
                    return "";
                }
                return kitService.placeholdersForKit(player, kitKey, token.getValue());
            }
        }

        return "";
    }
}
