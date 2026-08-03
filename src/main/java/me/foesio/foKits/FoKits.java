package me.foesio.foKits;

import me.foesio.core.FoCoreContext;
import me.foesio.core.FoPluginCore;
import me.foesio.core.message.FoMessageMigrations;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.reload.FoReloadRegistry;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foKits.command.FoKitsAdminCommand;
import me.foesio.foKits.command.FoKitsCommand;
import me.foesio.foKits.config.ConfigManager;
import me.foesio.foKits.config.PluginSettings;
import me.foesio.foKits.gui.GuiManager;
import me.foesio.foKits.placeholder.FoKitsPlaceholderExpansion;
import me.foesio.foKits.service.KitService;
import me.foesio.foKits.storage.KitRepository;
import me.foesio.foKits.storage.UserDataRepository;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class FoKits extends JavaPlugin {
    private static final String MODRINTH_PROJECT_ID = "fokits";
    private static final int BSTATS_PLUGIN_ID = 33113;

    private FoCoreContext core;
    private ConfigManager configManager;
    private PluginSettings settings;
    private FoMessageService messages;
    private KitRepository kitRepository;
    private UserDataRepository userDataRepository;
    private KitService kitService;
    private GuiManager guiManager;
    private UpdateNoticeService updates;

    @Override
    public void onEnable() {
        configManager = new ConfigManager(this);
        configManager.initialize();
        core = FoPluginCore.create(this);
        core.warnIfNativeDialogsUnavailable();
        core.metrics(BSTATS_PLUGIN_ID);

        settings = new PluginSettings(this);
        messages = FoMessageService.load(this, messageMigrations());
        updates = core.createUpdateNotices(messages, MODRINTH_PROJECT_ID);

        userDataRepository = new UserDataRepository(this);
        userDataRepository.ensureFolder();

        kitRepository = new KitRepository(this);
        kitRepository.loadAll();

        kitService = new KitService(this, settings, kitRepository, userDataRepository, messages, core.inventoryDeposits());
        guiManager = new GuiManager(this, core, settings, messages, kitRepository, kitService);

        getServer().getPluginManager().registerEvents(guiManager, this);

        registerCommands();
        registerPlaceholders();
        updates.start();

        getLogger().info("FoKits enabled.");
    }

    @Override
    public void onDisable() {
        if (guiManager != null) {
            guiManager.close();
        }
        if (core != null) {
            core.close();
            core = null;
        }
        getLogger().info("FoKits disabled.");
    }

    public KitRepository getKitRepository() {
        return kitRepository;
    }

    public ReloadSummary reloadPluginData() {
        int[] loadedKits = {kitRepository.getAll().size()};
        FoReloadResult result = FoReloadRegistry.create()
                .add("config", configManager::reload)
                .addMessages(messages)
                .add("dialogs", guiManager::reloadDialogService)
                .add("kits", () -> {
                    kitRepository.loadAll();
                    loadedKits[0] = kitRepository.getAll().size();
                })
                .reload();
        return new ReloadSummary(result, loadedKits[0], getConfigVersion());
    }

    public int getConfigVersion() {
        return configManager.currentVersion();
    }

    public UpdateNoticeService updates() {
        return updates;
    }

    private void registerCommands() {
        FoKitsCommand foKitsCommand = new FoKitsCommand(guiManager, messages, kitRepository, kitService);
        PluginCommand kits = getCommand("fokits");
        if (kits != null) {
            kits.setExecutor(foKitsCommand);
            kits.setTabCompleter(foKitsCommand);
        }

        FoKitsAdminCommand adminCommand = new FoKitsAdminCommand(this, guiManager, messages, userDataRepository, updates);
        PluginCommand admin = getCommand("fokitsadmin");
        if (admin != null) {
            admin.setExecutor(adminCommand);
            admin.setTabCompleter(adminCommand);
        }
    }

    private void registerPlaceholders() {
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") == null) {
            getLogger().info("PlaceholderAPI not found. Skipping placeholders.");
            return;
        }

        new FoKitsPlaceholderExpansion(this, kitService).register();
        getLogger().info("Registered PlaceholderAPI placeholders.");
    }

    private FoMessageMigrations messageMigrations() {
        return FoMessageMigrations.create()
                .add(this::migrateLegacyConfigMessages)
                .build();
    }

    private boolean migrateLegacyConfigMessages(FileConfiguration messagesConfig) {
        FileConfiguration bundledDefaults = bundledMessageDefaults();
        boolean changed = false;

        changed |= copyLegacyConfigValue(messagesConfig, bundledDefaults, "prefix", "tokens.prefix");
        changed |= copyLegacyConfigValue(messagesConfig, bundledDefaults, "colors.theme", "tokens.theme");
        changed |= copyLegacyConfigValue(messagesConfig, bundledDefaults, "colors.muted", "tokens.muted");
        changed |= copyLegacyConfigValue(messagesConfig, bundledDefaults, "colors.white", "tokens.white");
        changed |= copyLegacyConfigValue(messagesConfig, bundledDefaults, "colors.good", "tokens.good");
        changed |= copyLegacyConfigValue(messagesConfig, bundledDefaults, "colors.bad", "tokens.bad");

        ConfigurationSection section = getConfig().getConfigurationSection("messages");
        if (section == null) {
            return changed;
        }
        for (String key : section.getKeys(true)) {
            if (section.isConfigurationSection(key)) {
                continue;
            }
            changed |= copyLegacyConfigValue(messagesConfig, bundledDefaults, "messages." + key, key);
        }
        return changed;
    }

    private boolean copyLegacyConfigValue(FileConfiguration messagesConfig,
                                          FileConfiguration bundledDefaults,
                                          String configPath,
                                          String messagePath) {
        if (!getConfig().contains(configPath)) {
            return false;
        }
        String legacyValue = getConfig().getString(configPath);
        if (legacyValue == null) {
            return false;
        }

        String currentValue = messagesConfig.getString(messagePath);
        String bundledValue = bundledDefaults.getString(messagePath);
        if (currentValue != null && bundledValue != null && !Objects.equals(currentValue, bundledValue)) {
            return false;
        }

        String migratedValue = modernizeLegacyMessage(messagePath, legacyValue);
        if (Objects.equals(currentValue, migratedValue)) {
            return false;
        }
        messagesConfig.set(messagePath, migratedValue);
        return true;
    }

    private String modernizeLegacyMessage(String path, String value) {
        if ("invalid-admin-usage".equals(path)
                && "{prefix}{muted}Use {theme}/fokitsadmin <version|reload|editor>".equals(value)) {
            return "{prefix}{muted}Use {theme}/fokitsadmin <version|reload|editor|resetcooldown>";
        }
        if ("unknown-admin-arg".equals(path)
                && "{prefix}{bad}Unknown subcommand. {muted}Use {theme}version{muted}, {theme}reload{muted}, or {theme}editor".equals(value)) {
            return "{prefix}{bad}Unknown subcommand. {muted}Use {theme}version{muted}, {theme}reload{muted}, {theme}editor{muted}, or {theme}resetcooldown";
        }
        if ("admin-resetcooldown-usage".equals(path)
                && "{prefix}{muted}Use {theme}/fokitsadmin resetcooldown <player>".equals(value)) {
            return "{prefix}{muted}Use {theme}/fokitsadmin resetcooldown <player> [kit]";
        }
        return value;
    }

    private FileConfiguration bundledMessageDefaults() {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream stream = getResource("messages.yml")) {
            if (stream != null) {
                defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
            }
        } catch (Exception exception) {
            getLogger().warning("Could not read bundled messages.yml for legacy migration: " + exception.getMessage());
        }
        return defaults;
    }

    public record ReloadSummary(FoReloadResult result, int loadedKits, int configVersion) {
    }
}
