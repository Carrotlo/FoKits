package me.foesio.foKits;

import me.foesio.core.FoCoreContext;
import me.foesio.core.FoPluginCore;
import me.foesio.core.message.FoMessageMigrations;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.reload.FoReloadRegistry;
import me.foesio.core.reload.FoReloadResult;
import me.foesio.core.sound.FoAdminSounds;
import me.foesio.core.sound.FoEditorSounds;
import me.foesio.core.sound.FoGuiSounds;
import me.foesio.core.sound.FoSoundService;
import me.foesio.core.update.UpdateNoticeService;
import me.foesio.foKits.command.FoKitsAdminCommand;
import me.foesio.foKits.command.FoKitsCommand;
import me.foesio.foKits.config.ConfigManager;
import me.foesio.foKits.config.PluginSettings;
import me.foesio.foKits.config.PublicKitGuiMigration;
import me.foesio.foKits.gui.GuiManager;
import me.foesio.foKits.placeholder.FoKitsPlaceholderExpansion;
import me.foesio.foKits.service.KitService;
import me.foesio.foKits.storage.KitRepository;
import me.foesio.foKits.storage.UserDataRepository;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.FileAlreadyExistsException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class FoKits extends JavaPlugin {
    private static final String MODRINTH_PROJECT_ID = "fokits";
    private static final int BSTATS_PLUGIN_ID = 33113;
    private static final List<String> NEW_PUBLIC_MESSAGE_FIELDS = List.of(
            "gui-unavailable", "duration.zero", "duration.separator",
            "duration.day.one", "duration.day.many", "duration.hour.one",
            "duration.hour.many", "duration.minute.one", "duration.minute.many",
            "duration.second.one", "duration.second.many",
            "placeholder-values.true", "placeholder-values.false");

    private FoCoreContext core;
    private ConfigManager configManager;
    private PluginSettings settings;
    private FoMessageService messages;
    private KitRepository kitRepository;
    private UserDataRepository userDataRepository;
    private KitService kitService;
    private GuiManager guiManager;
    private UpdateNoticeService updates;
    private FoSoundService sounds;
    private FoAdminSounds adminSounds;
    private FoEditorSounds editorSounds;
    private FoGuiSounds guiSounds;
    private boolean publicGuiMigrationReady;

    @Override
    public void onEnable() {
        File installedConfig = new File(getDataFolder(), "config.yml");
        boolean hadConfig = installedConfig.isFile();
        int installedVersion = hadConfig
                ? YamlConfiguration.loadConfiguration(installedConfig).getInt("config-version", 0)
                : 0;
        boolean freshInstall = freshInstallation(installedConfig);
        boolean guiMigrated = PublicKitGuiMigration.install(
                this, freshInstall, installedVersion);
        publicGuiMigrationReady = guiMigrated;
        if (freshInstall && !guiMigrated) {
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        configManager = new ConfigManager(this);
        if (freshInstall || hadConfig) {
            saveDefaultConfig();
        } else {
            getLogger().warning("Existing FoKits data has no config.yml; keeping it absent for owner recovery.");
        }
        core = FoPluginCore.create(this);
        core.warnIfNativeDialogsUnavailable();
        core.metrics(BSTATS_PLUGIN_ID);
        sounds = FoSoundService.loadPreservingRemovals(this);
        adminSounds = FoAdminSounds.create(sounds);
        editorSounds = FoEditorSounds.create(sounds);
        guiSounds = FoGuiSounds.create(sounds);

        messages = FoMessageService.loadPreservingRemovals(this, FoMessageMigrations.none());
        if (guiMigrated) {
            migratePublicMessageSchema(installedVersion);
        }
        if (guiMigrated && (freshInstall || hadConfig)
                && getConfig().getInt("config-version", 0) >= 12) {
            configManager.initialize();
        }
        settings = new PluginSettings(this);
        updates = core.createUpdateNotices(messages, MODRINTH_PROJECT_ID, adminSounds);

        userDataRepository = new UserDataRepository(this);
        userDataRepository.ensureFolder();

        kitRepository = new KitRepository(this);
        kitRepository.loadAll();

        kitService = new KitService(this, settings, kitRepository, userDataRepository, messages, core.inventoryDeposits(), sounds);
        guiManager = new GuiManager(this, core, settings, messages, kitRepository, kitService, editorSounds, guiSounds, sounds);

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
        sounds = null;
        adminSounds = null;
        editorSounds = null;
        getLogger().info("FoKits disabled.");
    }

    public KitRepository getKitRepository() {
        return kitRepository;
    }

    public ReloadSummary reloadPluginData() {
        int[] loadedKits = {kitRepository.getAll().size()};
        FoReloadResult result = FoReloadRegistry.create()
                .add("public-ui-preflight", () -> {
                    if (!guiManager.validatePublicGuiInstalled().successful()) {
                        throw new IllegalStateException("Installed public GUI files are invalid");
                    }
                    validateMessagesInstalled();
                    if (!sounds.validateInstalled()) {
                        throw new IllegalStateException("Installed sounds.yml is invalid");
                    }
                })
                .add("config", () -> configManager.reload(publicGuiMigrationReady &&
                        new File(getDataFolder(), "config.yml").isFile()))
                .add("gui", () -> {
                    var report = guiManager.reloadPublicGui();
                    if (!report.successful()) {
                        throw new IllegalStateException("Public GUI validation failed; previous cached menus remain active");
                    }
                    settings.reloadGui();
                })
                .add("messages", this::reloadMessagesChecked)
                .add("sounds", () -> {
                    if (!sounds.reloadChecked()) {
                        throw new IllegalStateException("sounds.yml is missing or invalid; previous sounds remain active");
                    }
                })
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

    public FoAdminSounds adminSounds() {
        return adminSounds;
    }

    public void playSound(Player player, String key) {
        sounds.play(player, key);
    }

    private void registerCommands() {
        FoKitsCommand foKitsCommand = new FoKitsCommand(guiManager, messages, kitRepository, kitService, adminSounds);
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

    private boolean freshInstallation(File installedConfig) {
        if (installedConfig.isFile()) {
            return false;
        }
        var dataRoot = getDataFolder().toPath();
        if (!Files.exists(dataRoot)) {
            return true;
        }
        try (var contents = Files.list(dataRoot)) {
            return contents.findAny().isEmpty();
        } catch (IOException error) {
            getLogger().warning("Cannot determine whether FoKits data folder is fresh: " + error.getMessage());
            return false;
        }
    }

    private void migratePublicMessageSchema(int originalVersion) {
        if (getConfig().getInt("config-version", 0) >= 12) {
            return;
        }
        Path installed = getDataFolder().toPath().resolve("messages.yml");
        if (!Files.isRegularFile(installed)) {
            getLogger().warning("messages.yml is absent; public message migration remains pending.");
            return;
        }
        FileConfiguration defaults = bundledMessageDefaults();
        FileConfiguration oldDefaults = originalVersion == 9
                ? bundledMessageDefaults("migrations/messages-v9.yml") : null;
        Map<String, Object> changes = planMessageUpgrade(messages.config(), defaults, oldDefaults);
        Map<String, String> reasons = new LinkedHashMap<>();
        changes.forEach((path, replacement) -> reasons.put(path,
                messages.config().contains(path, true)
                        ? (oldDefaults != null && oldDefaults.contains(path, true)
                        && java.util.Objects.deepEquals(messages.config().get(path), oldDefaults.get(path))
                        ? "unchanged released default" : "invalid value " + messages.config().get(path))
                        : "new field absent from older schema"));
        byte[] original;
        try {
            original = Files.readAllBytes(installed);
            if (!changes.isEmpty()) {
                backupMessageForUpgrade(installed, originalVersion, original);
            }
        } catch (IOException failure) {
            getLogger().warning("Could not back up messages.yml; public message migration remains pending: "
                    + failure.getMessage());
            return;
        }
        boolean migrated = messages.migrateToVersion(core.migrations(), 12, config -> {
            changes.forEach(config::set);
            return !changes.isEmpty();
        });
        if (!migrated) {
            try {
                replaceBytes(installed, original);
                messages.reload();
            } catch (IOException rollbackFailure) {
                getLogger().severe("Could not restore messages.yml after failed migration: "
                        + rollbackFailure.getMessage());
            }
            return;
        }
        changes.forEach((path, replacement) -> getLogger().warning(
                "Replaced older public message field " + path + " (" + reasons.get(path)
                        + ") with newest bundled default " + replacement));
    }

    static Map<String, Object> planMessageUpgrade(FileConfiguration installed, FileConfiguration bundled) {
        return planMessageUpgrade(installed, bundled, null);
    }

    static Map<String, Object> planMessageUpgrade(FileConfiguration installed, FileConfiguration bundled,
                                                   FileConfiguration oldDefaults) {
        Map<String, Object> changes = new LinkedHashMap<>();
        for (String path : bundled.getKeys(true)) {
            if (bundled.isConfigurationSection(path)) {
                continue;
            }
            if (!installed.contains(path, true)) {
                if (NEW_PUBLIC_MESSAGE_FIELDS.contains(path)) {
                    changes.put(path, bundled.get(path));
                }
            } else if (!validMessageValue(path, installed.get(path))) {
                changes.put(path, bundled.get(path));
            } else if (oldDefaults != null && oldDefaults.contains(path, true)
                    && java.util.Objects.deepEquals(installed.get(path), oldDefaults.get(path))
                    && !java.util.Objects.deepEquals(installed.get(path), bundled.get(path))) {
                changes.put(path, bundled.get(path));
            }
        }
        return changes;
    }

    static void backupMessageForUpgrade(Path installed, int originalVersion, byte[] original)
            throws IOException {
        Path backup = installed.resolveSibling(installed.getFileName()
                + ".gui-from-config-v" + originalVersion + "-backup");
        if (!Files.exists(backup)) {
            Path staged = Files.createTempFile(backup.getParent(), ".fokits-message-backup-", ".staged");
            try {
                Files.write(staged, original);
                try {
                    Files.move(staged, backup, StandardCopyOption.ATOMIC_MOVE);
                } catch (AtomicMoveNotSupportedException unsupported) {
                    Files.move(staged, backup);
                } catch (FileAlreadyExistsException existing) {
                    if (!Files.isRegularFile(backup)) {
                        throw existing;
                    }
                }
            } finally {
                Files.deleteIfExists(staged);
            }
        } else if (!Files.isRegularFile(backup)) {
            throw new IOException("existing message backup is not a regular file: " + backup);
        }
    }

    static void replaceBytes(Path target, byte[] bytes) throws IOException {
        Path staged = Files.createTempFile(target.getParent(), ".fokits-message-rollback-", ".staged");
        try {
            Files.write(staged, bytes);
            try {
                Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException unsupported) {
                Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(staged);
        }
    }

    private void reloadMessagesChecked() {
        validateMessagesInstalled();
        messages.reload();
    }

    private void validateMessagesInstalled() {
        File file = new File(getDataFolder(), "messages.yml");
        YamlConfiguration candidate = new YamlConfiguration();
        try {
            candidate.load(file);
        } catch (Exception invalid) {
            throw new IllegalStateException("messages.yml is missing or invalid; previous messages remain active",
                    invalid);
        }
        FileConfiguration bundled = bundledMessageDefaults();
        validateMessageTypes(candidate, bundled);
    }

    static void validateMessageTypes(YamlConfiguration candidate, FileConfiguration bundled) {
        for (String path : bundled.getKeys(true)) {
            if (bundled.isConfigurationSection(path) || !candidate.contains(path, true)) {
                continue;
            }
            Object value = candidate.get(path);
            if (!validMessageValue(path, value)) {
                boolean scalarOnly = scalarMessageValue(path);
                throw new IllegalStateException("messages.yml:" + path + " must be text"
                        + (scalarOnly ? "" : " or a list of text lines"));
            }
        }
    }

    private static boolean validMessageValue(String path, Object value) {
        if (value instanceof String) {
            return true;
        }
        return !scalarMessageValue(path) && value instanceof List<?> lines
                && lines.stream().allMatch(String.class::isInstance);
    }

    private static boolean scalarMessageValue(String path) {
        return path.startsWith("tokens.") || path.startsWith("duration.")
                || path.startsWith("placeholder-values.");
    }

    private FileConfiguration bundledMessageDefaults() {
        return bundledMessageDefaults("messages.yml");
    }

    private FileConfiguration bundledMessageDefaults(String resource) {
        YamlConfiguration defaults = new YamlConfiguration();
        try (InputStream stream = getResource(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing bundled " + resource);
            }
            defaults.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        } catch (Exception exception) {
            throw new IllegalStateException("Could not read bundled " + resource, exception);
        }
        return defaults;
    }

    public record ReloadSummary(FoReloadResult result, int loadedKits, int configVersion) {
    }
}
