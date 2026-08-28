package me.foesio.foKits.gui;

import me.foesio.core.FoCoreContext;
import me.foesio.core.dialog.DialogButton;
import me.foesio.core.dialog.DialogService;
import me.foesio.core.dialog.DialogServiceFactory;
import me.foesio.core.dialog.FallbackDialogService;
import me.foesio.core.dialog.NativeDialogSettings;
import me.foesio.core.dialog.NativeDialogSupport;
import me.foesio.core.dialog.TextDialogRequest;
import me.foesio.core.editor.ChatPromptManager;
import me.foesio.core.editor.EditorDialogInputs;
import me.foesio.core.editor.EditorItemFactory;
import me.foesio.core.gui.GuiButtonConfig;
import me.foesio.core.gui.GuiSlots;
import me.foesio.core.gui.EntryBrowserClick;
import me.foesio.core.gui.EntryBrowserHolder;
import me.foesio.core.gui.EntryBrowserMenus;
import me.foesio.core.gui.EntryBrowserRequest;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.selector.TriStateSelectionActionType;
import me.foesio.core.selector.TriStateSelectionClick;
import me.foesio.core.selector.TriStateSelectionHolder;
import me.foesio.core.selector.TriStateSelectionMenus;
import me.foesio.core.selector.TriStateSelectionRequest;
import me.foesio.core.selector.TriStateSelectionState;
import me.foesio.core.selector.TriStateSelections;
import me.foesio.core.selector.WorldSelectionEntries;
import me.foesio.core.sound.FoEditorSounds;
import me.foesio.core.sound.FoGuiSounds;
import me.foesio.core.sound.FoSoundService;
import me.foesio.foKits.config.PluginSettings;
import me.foesio.foKits.model.ClaimMode;
import me.foesio.foKits.model.ClaimResult;
import me.foesio.foKits.model.KitDefinition;
import me.foesio.foKits.model.KitViewState;
import me.foesio.foKits.service.KitService;
import me.foesio.foKits.storage.KitRepository;
import me.foesio.foKits.util.TimeUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryAction;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.regex.Pattern;

public class GuiManager implements Listener {
    private static final int PREVIEW_SIZE = 54;
    private static final int PREVIEW_STORAGE_START_SLOT = 18;
    private static final int ADMIN_KIT_DELETE_SLOT = 31;
    private static final int DELETE_CONFIRM_CANCEL_SLOT = GuiSlots.bottomMiddleSlot(3);
    private static final int DELETE_CONFIRM_CONFIRM_SLOT = 15;
    private static final int CLAIMED_ITEM_EDIT_SLOT = 13;
    private static final int KIT_OFFHAND_SLOT = 6;
    private static final Material KIT_SLOT_MARKER_MATERIAL = Material.CHEST;
    private static final Pattern LEGACY_AMPERSAND_COLOR_PATTERN = Pattern.compile("(?i)&(?:#[0-9a-f]{6}|x(?:&[0-9a-f]){6}|[0-9a-fk-or])");
    private static final List<Integer> ITEM_EDITOR_EDITABLE_SLOT_ORDER = List.of(
            1, 2, 3, 4, KIT_OFFHAND_SLOT,
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
    );
    private static final List<Integer> ITEM_EDITOR_STORAGE_SLOTS = List.of(
            9, 10, 11, 12, 13, 14, 15, 16, 17,
            18, 19, 20, 21, 22, 23, 24, 25, 26,
            27, 28, 29, 30, 31, 32, 33, 34, 35,
            36, 37, 38, 39, 40, 41, 42, 43, 44
    );
    private static final Set<Integer> ITEM_EDITOR_EDITABLE_SLOTS = Set.copyOf(ITEM_EDITOR_EDITABLE_SLOT_ORDER);
    private static final Set<Integer> CLAIMED_ITEM_EDITOR_EDITABLE_SLOTS = Set.of(CLAIMED_ITEM_EDIT_SLOT);

    private final JavaPlugin plugin;
    private final FoCoreContext core;
    private final PluginSettings settings;
    private final FoMessageService messages;
    private final GuiButtonConfig buttons = GuiButtonConfig.defaults();
    private final ChatPromptManager prompts;
    private final KitRepository kits;
    private final KitService kitService;
    private final FoEditorSounds editorSounds;
    private final FoGuiSounds guiSounds;
    private final FoSoundService sounds;
    private final Map<UUID, GuiSession> sessions = new HashMap<>();
    private final Map<UUID, AdminListState> adminListStates = new HashMap<>();
    private final Map<UUID, String> activeWorldSelectors = new HashMap<>();
    private final Set<UUID> warnedNativeFallbackPlayers = ConcurrentHashMap.newKeySet();
    private DialogService dialogs;

    public GuiManager(
            JavaPlugin plugin,
            FoCoreContext core,
            PluginSettings settings,
            FoMessageService messages,
            KitRepository kits,
            KitService kitService,
            FoEditorSounds editorSounds,
            FoGuiSounds guiSounds,
            FoSoundService sounds
    ) {
        this.plugin = plugin;
        this.core = core;
        this.settings = settings;
        this.messages = messages;
        this.prompts = new ChatPromptManager(plugin, core.scheduler());
        this.kits = kits;
        this.kitService = kitService;
        this.editorSounds = editorSounds;
        this.guiSounds = guiSounds;
        this.sounds = sounds;
        reloadDialogService();
    }

    public void reloadDialogService() {
        NativeDialogSupport support = NativeDialogSupport.detect(
                plugin,
                NativeDialogSettings.fromConfig(plugin.getConfig().getConfigurationSection("native-dialogs"))
        );
        FallbackDialogService fallback = new FallbackDialogService(
                support,
                (player, request, onClose) -> run(onClose),
                (player, request, onConfirm, onCancel) -> run(onCancel),
                this::openChatFallback
        );
        dialogs = DialogServiceFactory.create(plugin, support, fallback, core.scheduler());
        warnedNativeFallbackPlayers.clear();

        if (support.canUseNativeDialogs()) {
            plugin.getLogger().info("Native dialogs enabled.");
        }
    }

    public void close() {
        prompts.close();
        warnedNativeFallbackPlayers.clear();
    }

    public void openPlayerKits(Player player) {
        openPlayerKits(player, true);
    }

    private void openPlayerKits(Player player, boolean playOpenSound) {
        if (playOpenSound) {
            guiSounds.open(player);
        }
        int size = settings.playerGuiRows() * 9;
        Inventory inventory = Bukkit.createInventory(player, size, guiTitle(settings.playerGuiTitle()));
        GuiSession session = new GuiSession(GuiType.PLAYER_KITS, "");

        if (settings.fillBackground()) {
            ItemStack filler = makeItem(settings.fillerMaterial(), " ", List.of(), null);
            for (int i = 0; i < size; i++) {
                inventory.setItem(i, filler);
            }
        }

        List<KitDefinition> list = new ArrayList<>(kits.getAll());
        list.sort(Comparator.comparingInt(KitDefinition::getOrderIndex).thenComparing(KitDefinition::getKey));

        Map<Integer, ItemStack> staticItems = settings.playerGuiStaticItems();
        for (Map.Entry<Integer, ItemStack> entry : staticItems.entrySet()) {
            if (entry.getValue() == null || entry.getValue().getType().isAir()) {
                continue;
            }
            if (entry.getKey() < 0 || entry.getKey() >= size) {
                continue;
            }
            inventory.setItem(entry.getKey(), entry.getValue().clone());
        }

        List<Integer> kitSlots = settings.playerGuiKitSlots();
        if (kitSlots.isEmpty()) {
            Set<Integer> blocked = new HashSet<>(staticItems.keySet());
            for (int i = 0; i < size; i++) {
                if (!blocked.contains(i)) {
                    kitSlots.add(i);
                }
            }
        }

        ItemStack emptyKitSlot = makeItem(Material.LIGHT_GRAY_STAINED_GLASS_PANE, " ", List.of(), null);
        for (Integer slot : kitSlots) {
            if (slot != null && slot >= 0 && slot < size) {
                inventory.setItem(slot, emptyKitSlot.clone());
            }
        }

        int kitIndex = 0;
        for (Integer slot : kitSlots) {
            if (slot == null || slot < 0 || slot >= size || kitIndex >= list.size()) {
                continue;
            }
            KitDefinition kit = list.get(kitIndex++);
            ItemStack icon = buildKitDisplayIcon(player, kit);
            inventory.setItem(slot, icon);
            session.getActions().put(slot, "kit:" + kit.getKey());
        }

        player.openInventory(inventory);
        sessions.put(player.getUniqueId(), session);
    }

    public void openPreview(Player player, KitDefinition kit, boolean backToAdmin) {
        if (!backToAdmin) {
            guiSounds.open(player);
        }
        Inventory inventory = Bukkit.createInventory(player, PREVIEW_SIZE,
                guiTitle("&8ᴋɪᴛ ᴘʀᴇᴠɪᴇᴡ &8- " + kit.getDisplayOrKey()));
        GuiSession session = new GuiSession(GuiType.PLAYER_PREVIEW, kit.getKey());

        ItemStack filler = makeItem(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), null);
        for (int slot = 0; slot < PREVIEW_SIZE; slot++) {
            inventory.setItem(slot, filler);
        }

        ItemStack[] armor = kit.getArmorItems();
        inventory.setItem(1, cloneOrNull(armor, 0));
        inventory.setItem(2, cloneOrNull(armor, 1));
        inventory.setItem(3, cloneOrNull(armor, 2));
        inventory.setItem(4, cloneOrNull(armor, 3));
        inventory.setItem(KIT_OFFHAND_SLOT, kit.getOffhandItem() == null ? null : kit.getOffhandItem().clone());

        ItemStack[] items = kit.getInventoryItems();
        for (int i = 0; i < 36; i++) {
            inventory.setItem(PREVIEW_STORAGE_START_SLOT + i, cloneOrNull(items, i));
        }

        if (backToAdmin) {
            inventory.setItem(8, buttons.back());
            session.getActions().put(8, "back:admin-kit-settings:" + kit.getKey());
        } else {
            inventory.setItem(8, buttons.back());
            session.getActions().put(8, "back:player-kits");
        }

        player.openInventory(inventory);
        sessions.put(player.getUniqueId(), session);
    }

    public void openAdminRoot(Player player) {
        openAdminRoot(player, true);
    }

    private void openAdminRoot(Player player, boolean playOpenSound) {
        if (playOpenSound) {
            editorSounds.open(player);
        }
        Inventory inventory = Bukkit.createInventory(player, 27, guiTitle("&8ꜰᴏᴋɪᴛꜱ ᴇᴅɪᴛᴏʀ"));
        GuiSession session = new GuiSession(GuiType.ADMIN_ROOT, "");

        fill(inventory, Material.GRAY_STAINED_GLASS_PANE);
        inventory.setItem(11, makeItem(Material.CHEST, "{theme}Kit Editor", List.of("{white}Create, edit and delete kits."), null));
        inventory.setItem(15, makeItem(Material.PAINTING, "{theme}Main GUI Settings", List.of("{white}Edit the player /kits menu."), null));

        session.getActions().put(11, "open:admin-kit-list:reset");
        session.getActions().put(15, "open:admin-gui-settings");

        player.openInventory(inventory);
        sessions.put(player.getUniqueId(), session);
    }

    public void openAdminKitList(Player player) {
        AdminListState state = adminListStates.getOrDefault(player.getUniqueId(), new AdminListState(0, ""));
        openAdminKitList(player, state.page(), state.search());
    }

    public void openAdminKitList(Player player, int page, String search) {
        Inventory inventory = Bukkit.createInventory(player, 54, guiTitle("&8ᴋɪᴛ ʟɪsᴛ"));
        List<KitDefinition> list = new ArrayList<>(kits.getAll());
        list.sort(Comparator.comparingInt(KitDefinition::getOrderIndex).thenComparing(KitDefinition::getKey));
        String normalizedSearch = normalizeSearch(search);
        if (!normalizedSearch.isEmpty()) {
            list.removeIf(kit -> !matchesSearch(kit, normalizedSearch));
        }

        List<EntryBrowserRequest.Entry> entries = list.stream()
                .map(kit -> EntryBrowserRequest.Entry.of(kit.getKey(), buildAdminKitItem(kit)))
                .toList();
        int maxPage = EntryBrowserMenus.maxPage(EntryBrowserRequest.builder().entries(entries).build());
        int resolvedPage = Math.max(0, Math.min(page, maxPage));
        adminListStates.put(player.getUniqueId(), new AdminListState(resolvedPage, normalizedSearch));

        sessions.remove(player.getUniqueId());
        EntryBrowserRequest request = EntryBrowserRequest.builder()
                .title("&8ᴋɪᴛ ʟɪꜱᴛ")
                .entries(entries)
                .page(resolvedPage)
                .filter(normalizedSearch)
                .buttons(buttons)
                .showBack(true)
                .addButton(makeItem(Material.ANVIL, "{theme}Create Kit", List.of(
                        "{white}Click to create a new kit.",
                        "{white}Expected key: letters, numbers, _ or -"), null))
                .emptyItem(makeItem(Material.PAPER, "{bad}No Kits", List.of(
                        "{white}No kits match the current search."), null))
                .build();
        player.openInventory(EntryBrowserMenus.createInventory(request));
    }

    public void openAdminKitSettings(Player player, String kitKey) {
        Optional<KitDefinition> optionalKit = kits.get(kitKey);
        if (optionalKit.isEmpty()) {
            openLater(player, () -> openAdminKitList(player));
            return;
        }

        KitDefinition kit = optionalKit.get();
        Inventory inventory = Bukkit.createInventory(player, 45, guiTitle("&8ᴇᴅɪᴛ ᴋɪᴛ &8- " + kit.getKey()));
        GuiSession session = new GuiSession(GuiType.ADMIN_KIT_SETTINGS, kit.getKey());
        int backSlot = GuiSlots.bottomMiddleSlot(5);

        fill(inventory, Material.GRAY_STAINED_GLASS_PANE);

        inventory.setItem(10, makeItem(kit.isEnabled() ? Material.LIME_DYE : Material.RED_DYE,
                kit.isEnabled() ? "{good}Enabled" : "{bad}Disabled",
                List.of("{white}Toggle whether players can claim this kit."), null));
        inventory.setItem(11, makeItem(Material.NAME_TAG, "{theme}Display Name",
                List.of("{white}Current: {theme}" + safe(kit.getDisplayName()), "{white}Click to edit in chat."), null));
        inventory.setItem(12, makeItem(Material.OAK_SIGN, "{theme}Rename Kit Key",
                List.of("{white}Current key: {theme}" + kit.getKey(), "{white}Click to rename in chat."), null));
        boolean hasIconItem = kit.getIconItem() != null && !kit.getIconItem().getType().isAir();
        inventory.setItem(13, makeDisplayItem(
                hasIconItem ? kit.getIconItem() : new ItemStack(Material.ITEM_FRAME),
                "{theme}Kit Icon Item",
                List.of(
                        "{white}Current: {theme}" + (hasIconItem ? "set" : "not set"),
                        "{white}Set the icon shown in kit menus.",
                        "{white}Keeps full item data."
                )
        ));
        boolean hasClaimedDisplay = kit.getClaimedDisplayItem() != null && !kit.getClaimedDisplayItem().getType().isAir();
        inventory.setItem(14, makeDisplayItem(
                hasClaimedDisplay ? kit.getClaimedDisplayItem() : new ItemStack(Material.RED_DYE),
                "{theme}Claimed-State Item",
                List.of(
                        "{white}Current: {theme}" + (hasClaimedDisplay ? "set" : "not set"),
                        "{white}Set the icon shown after one-time claim.",
                        "{white}Preview remains available."
                )
        ));
        inventory.setItem(15, makeItem(Material.CHEST_MINECART, "{theme}Edit Kit Items",
                List.of("{white}Open item editor GUI.", "{white}Supports full item meta."), null));
        inventory.setItem(16, makeItem(Material.ENDER_EYE, "{theme}Preview Kit",
                List.of("{white}Open the player-style preview."), null));

        inventory.setItem(19, makeItem(Material.CLOCK, "{theme}Claim Mode",
                List.of("{white}Current: {theme}" + kit.getClaimMode().name().toLowerCase(Locale.ROOT),
                        "{white}Click to toggle cooldown/one-time."), null));
        inventory.setItem(20, makeItem(Material.REPEATER, "{theme}Cooldown",
                List.of("{white}Current: {theme}" + TimeUtil.formatDuration(kit.getCooldownMillis()),
                        "{white}Example: 12h30m"), null));
        inventory.setItem(21, makeItem(Material.TRIPWIRE_HOOK, "{theme}Required Permission",
                List.of("{white}Current: {theme}" + (kit.getRequiredPermission().isBlank() ? "none" : kit.getRequiredPermission()),
                        "{white}Use 'none' to clear."), null));
        inventory.setItem(22, makeItem(Material.HOPPER, "{theme}Order Index",
                List.of("{white}Current: {theme}" + kit.getOrderIndex(), "{white}Lower index appears first."), null));
        inventory.setItem(23, makeItem(kit.isBroadcastOnClaim() ? Material.LIME_DYE : Material.RED_DYE,
                "{theme}Broadcast On Claim",
                List.of("{white}Current: " + (kit.isBroadcastOnClaim() ? "{good}enabled" : "{bad}disabled"),
                        "{white}Click to toggle."), null));
        List<String> commandLore = new ArrayList<>();
        commandLore.add("{white}Current: {theme}" + kit.getCommandsOnClaim().size() + " command(s)");
        if (kit.getCommandsOnClaim().isEmpty()) {
            commandLore.add("{white}No commands configured.");
        } else {
            int previewCount = Math.min(4, kit.getCommandsOnClaim().size());
            for (int i = 0; i < previewCount; i++) {
                commandLore.add("{muted}- {theme}" + kit.getCommandsOnClaim().get(i));
            }
            if (kit.getCommandsOnClaim().size() > previewCount) {
                commandLore.add("{muted}... +" + (kit.getCommandsOnClaim().size() - previewCount) + " more");
            }
        }
        commandLore.add("{white}Input format: cmd1|cmd2|cmd3");
        commandLore.add("{white}Use 'none' to clear.");
        inventory.setItem(24, makeItem(Material.COMMAND_BLOCK, "{theme}Commands On Claim", commandLore, null));
        inventory.setItem(25, EditorItemFactory.worlds(0, kit.getDenyWorlds().size(), "Allowed"));
        inventory.setItem(ADMIN_KIT_DELETE_SLOT, makeItem(Material.LAVA_BUCKET, "{bad}Delete Kit", List.of(
                "{white}Open confirmation first.",
                "{bad}This permanently deletes the kit."
        ), null));
        inventory.setItem(backSlot, buttons.back());

        session.getActions().put(10, "toggle-enabled");
        session.getActions().put(11, "edit-display-name");
        session.getActions().put(12, "rename-key");
        session.getActions().put(13, "open-icon-item-editor");
        session.getActions().put(14, "open-claimed-item-editor");
        session.getActions().put(15, "open-item-editor");
        session.getActions().put(16, "preview-kit");
        session.getActions().put(19, "toggle-claim-mode");
        session.getActions().put(20, "edit-cooldown");
        session.getActions().put(21, "edit-permission");
        session.getActions().put(22, "edit-order-index");
        session.getActions().put(23, "toggle-broadcast");
        session.getActions().put(24, "edit-commands");
        session.getActions().put(25, "edit-worlds");
        session.getActions().put(ADMIN_KIT_DELETE_SLOT, "delete-kit");
        session.getActions().put(backSlot, "open:admin-kit-list");

        player.openInventory(inventory);
        sessions.put(player.getUniqueId(), session);
    }

    private void openDeleteConfirmGui(Player player, String kitKey) {
        GuiSession session = new GuiSession(GuiType.ADMIN_DELETE_CONFIRM, kitKey);
        Inventory inventory = Bukkit.createInventory(player, 27, guiTitle("&8ᴄᴏɴꜰɪʀᴍ ᴅᴇʟᴇᴛᴇ"));

        fill(inventory, Material.GRAY_STAINED_GLASS_PANE);
        inventory.setItem(DELETE_CONFIRM_CANCEL_SLOT, makeItem(Material.BARRIER, "{bad}Cancel", List.of(
                "{white}Return to the kit editor."
        ), null));
        inventory.setItem(DELETE_CONFIRM_CONFIRM_SLOT, makeItem(Material.LAVA_BUCKET, "{bad}Confirm Delete", List.of(
                "{white}Kit: {theme}" + kitKey,
                "{white}Delete this kit now.",
                "{bad}This cannot be undone."
        ), null));

        session.getActions().put(DELETE_CONFIRM_CANCEL_SLOT, "cancel-delete-kit");
        session.getActions().put(DELETE_CONFIRM_CONFIRM_SLOT, "confirm-delete-kit");

        player.openInventory(inventory);
        sessions.put(player.getUniqueId(), session);
    }

    public void openAdminItemEditor(Player player, String kitKey) {
        Optional<KitDefinition> optionalKit = kits.get(kitKey);
        if (optionalKit.isEmpty()) {
            openLater(player, () -> openAdminKitList(player));
            return;
        }

        KitDefinition kit = optionalKit.get();
        Inventory inventory = Bukkit.createInventory(player, 54, guiTitle("&8ɪᴛᴇᴍs &8- " + kit.getKey()));
        GuiSession session = new GuiSession(GuiType.ADMIN_ITEM_EDITOR, kit.getKey());

        fill(inventory, Material.GRAY_STAINED_GLASS_PANE);

        ItemStack[] armor = kit.getArmorItems();
        inventory.setItem(1, cloneOrNull(armor, 0));
        inventory.setItem(2, cloneOrNull(armor, 1));
        inventory.setItem(3, cloneOrNull(armor, 2));
        inventory.setItem(4, cloneOrNull(armor, 3));
        inventory.setItem(KIT_OFFHAND_SLOT, kit.getOffhandItem() == null ? null : kit.getOffhandItem().clone());

        ItemStack[] contents = kit.getInventoryItems();
        for (int i = 0; i < 36; i++) {
            inventory.setItem(9 + i, cloneOrNull(contents, i));
        }

        inventory.setItem(47, makeItem(Material.CHEST, "{theme}Copy From Inventory", List.of(
                "{white}Copy storage, armor and offhand.",
                "{white}Keeps full item data."
        ), null));
        inventory.setItem(GuiSlots.bottomMiddleSlot(6), buttons.back());
        inventory.setItem(51, makeItem(Material.BARRIER, "{bad}Clear Items", List.of("{white}Clear all item slots."), null));

        session.getActions().put(47, "copy-from-inventory");
        session.getActions().put(GuiSlots.bottomMiddleSlot(6), "back-items");
        session.getActions().put(51, "clear-items");

        player.openInventory(inventory);
        sessions.put(player.getUniqueId(), session);
    }

    public void openAdminClaimedItemEditor(Player player, String kitKey) {
        Optional<KitDefinition> optionalKit = kits.get(kitKey);
        if (optionalKit.isEmpty()) {
            openLater(player, () -> openAdminKitList(player));
            return;
        }

        KitDefinition kit = optionalKit.get();
        Inventory inventory = Bukkit.createInventory(player, 27, guiTitle("&8ᴄʟᴀɪᴍᴇᴅ ɪᴄᴏɴ &8- " + kit.getKey()));
        GuiSession session = new GuiSession(GuiType.ADMIN_CLAIMED_ITEM_EDITOR, kit.getKey());

        fill(inventory, Material.GRAY_STAINED_GLASS_PANE);
        inventory.setItem(CLAIMED_ITEM_EDIT_SLOT, kit.getClaimedDisplayItem() == null ? null : kit.getClaimedDisplayItem().clone());
        inventory.setItem(18, makeItem(Material.LIME_CONCRETE, "{good}Save & Back", List.of("{white}Save claimed-state item and return."), null));
        inventory.setItem(22, makeItem(Material.BARRIER, "{bad}Clear Item", List.of("{white}Remove the claimed-state item."), null));
        inventory.setItem(26, makeItem(Material.RED_CONCRETE, "{bad}Cancel", List.of("{white}Discard changes and return."), null));

        session.getActions().put(18, "save-claimed-item");
        session.getActions().put(22, "clear-claimed-item");
        session.getActions().put(26, "cancel-claimed-item");

        player.openInventory(inventory);
        sessions.put(player.getUniqueId(), session);
    }

    public void openAdminIconItemEditor(Player player, String kitKey) {
        Optional<KitDefinition> optionalKit = kits.get(kitKey);
        if (optionalKit.isEmpty()) {
            openLater(player, () -> openAdminKitList(player));
            return;
        }

        KitDefinition kit = optionalKit.get();
        Inventory inventory = Bukkit.createInventory(player, 27, guiTitle("&8ᴋɪᴛ ɪᴄᴏɴ &8- " + kit.getKey()));
        GuiSession session = new GuiSession(GuiType.ADMIN_ICON_ITEM_EDITOR, kit.getKey());

        fill(inventory, Material.GRAY_STAINED_GLASS_PANE);
        inventory.setItem(CLAIMED_ITEM_EDIT_SLOT, kit.getIconItem() == null ? null : kit.getIconItem().clone());
        inventory.setItem(18, makeItem(Material.LIME_CONCRETE, "{good}Save & Back", List.of("{white}Save kit icon item and return."), null));
        inventory.setItem(22, makeItem(Material.BARRIER, "{bad}Clear Item", List.of("{white}Remove the kit icon item."), null));
        inventory.setItem(26, makeItem(Material.RED_CONCRETE, "{bad}Cancel", List.of("{white}Discard changes and return."), null));

        session.getActions().put(18, "save-icon-item");
        session.getActions().put(22, "clear-icon-item");
        session.getActions().put(26, "cancel-icon-item");

        player.openInventory(inventory);
        sessions.put(player.getUniqueId(), session);
    }

    public void openAdminPlayerGuiLayoutEditor(Player player) {
        int size = settings.playerGuiRows() * 9;
        Inventory inventory = Bukkit.createInventory(player, size, guiTitle("&8ᴋɪᴛ ɢᴜɪ ʟᴀʏᴏᴜᴛ"));
        GuiSession session = new GuiSession(GuiType.ADMIN_PLAYER_GUI_LAYOUT_EDITOR, "");

        fill(inventory, settings.fillerMaterial());

        for (Map.Entry<Integer, ItemStack> entry : settings.playerGuiStaticItems().entrySet()) {
            if (entry.getKey() < 0 || entry.getKey() >= size || entry.getValue() == null || entry.getValue().getType().isAir()) {
                continue;
            }
            inventory.setItem(entry.getKey(), entry.getValue().clone());
        }

        for (Integer slot : settings.playerGuiKitSlots()) {
            if (slot == null || slot < 0 || slot >= size) {
                continue;
            }
            inventory.setItem(slot, makeItem(
                    KIT_SLOT_MARKER_MATERIAL,
                    "{theme}Kit Slot Marker",
                    List.of(
                            "{white}This chest marks where kits auto-place.",
                            "{white}Close inventory to save."
                    ),
                    null
            ));
        }

        messages.send(player, "layout-editor-start",
                "{prefix}{muted}Layout editor: place {theme}CHEST{muted} items as kit slots, any other items become static GUI items, then close to save.");
        player.openInventory(inventory);
        sessions.put(player.getUniqueId(), session);
    }

    public void openAdminGuiSettings(Player player) {
        Inventory inventory = Bukkit.createInventory(player, 27, guiTitle("&8ᴍᴀɪɴ ɢᴜɪ sᴇᴛᴛɪɴɢs"));
        GuiSession session = new GuiSession(GuiType.ADMIN_GUI_SETTINGS, "");

        fill(inventory, Material.GRAY_STAINED_GLASS_PANE);

        inventory.setItem(10, makeItem(Material.CHEST, "{theme}Edit /kits Layout",
                List.of("{white}Use chest markers for kit slots.", "{white}Close layout editor to save."), null));
        inventory.setItem(11, makeItem(Material.NAME_TAG, "{theme}Title",
                List.of("{white}Current: {theme}" + settings.playerGuiTitle(), "{white}Click to edit in chat."), null));
        inventory.setItem(13, makeItem(Material.CHEST, "{theme}Rows",
                List.of("{white}Current: {theme}" + settings.playerGuiRows(), "{white}Use values 1-6."), null));
        inventory.setItem(15, makeItem(settings.fillBackground() ? Material.LIME_DYE : Material.RED_DYE,
                "{theme}Fill Background",
                List.of("{white}Current: " + (settings.fillBackground() ? "{good}enabled" : "{bad}disabled"),
                        "{white}Click to toggle."), null));
        inventory.setItem(16, makeItem(Material.GRAY_STAINED_GLASS_PANE, "{theme}Filler Material",
                List.of("{white}Current: {theme}" + settings.fillerMaterial().name(), "{white}Click to edit in chat."), null));
        inventory.setItem(GuiSlots.bottomMiddleSlot(3), buttons.back());

        session.getActions().put(10, "open-gui-layout-editor");
        session.getActions().put(11, "edit-gui-title");
        session.getActions().put(13, "edit-gui-rows");
        session.getActions().put(15, "toggle-gui-fill");
        session.getActions().put(16, "edit-gui-filler");
        session.getActions().put(GuiSlots.bottomMiddleSlot(3), "back:admin-root");

        player.openInventory(inventory);
        sessions.put(player.getUniqueId(), session);
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof EntryBrowserHolder holder) {
            if (event.getClickedInventory() == event.getView().getTopInventory()) {
                event.setCancelled(true);
                handleEntryBrowserClick(player, event.getRawSlot(), event.getClick(), holder);
            } else if (event.isShiftClick()) {
                event.setCancelled(true);
            }
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof TriStateSelectionHolder holder) {
            handleBlockedWorldSelectorClick(event, player, holder);
            return;
        }

        GuiSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        if (event.getClickedInventory() == null) {
            event.setCancelled(true);
            return;
        }

        if (session.getType() == GuiType.ADMIN_ITEM_EDITOR) {
            handleItemEditorClick(event, player, session);
            return;
        }
        if (session.getType() == GuiType.ADMIN_ICON_ITEM_EDITOR) {
            handleIconItemEditorClick(event, player, session);
            return;
        }
        if (session.getType() == GuiType.ADMIN_CLAIMED_ITEM_EDITOR) {
            handleClaimedItemEditorClick(event, player, session);
            return;
        }
        if (session.getType() == GuiType.ADMIN_PLAYER_GUI_LAYOUT_EDITOR) {
            handlePlayerGuiLayoutEditorClick(event);
            return;
        }

        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            event.setCancelled(true);
            return;
        }

        event.setCancelled(true);
        String action = session.getActions().get(event.getRawSlot());
        if (action == null) {
            return;
        }

        handleAction(player, session, action, event.getClick());
    }

    @EventHandler
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        if (event.getView().getTopInventory().getHolder() instanceof EntryBrowserHolder) {
            int topSize = event.getView().getTopInventory().getSize();
            for (int rawSlot : event.getRawSlots()) {
                if (rawSlot < topSize) {
                    event.setCancelled(true);
                    return;
                }
            }
        }

        GuiSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        int topSize = event.getView().getTopInventory().getSize();
        for (int rawSlot : event.getRawSlots()) {
            if (rawSlot < topSize) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player player)) {
            return;
        }

        if (core.inventoryCloseSuppressor().consumeSuppressedClose(player)) {
            return;
        }

        if (event.getInventory().getHolder() instanceof TriStateSelectionHolder) {
            activeWorldSelectors.remove(player.getUniqueId());
            return;
        }

        GuiSession session = sessions.get(player.getUniqueId());
        if (session == null) {
            return;
        }

        sessions.remove(player.getUniqueId());

        if (session.getType() != GuiType.ADMIN_ITEM_EDITOR
                && session.getType() != GuiType.ADMIN_ICON_ITEM_EDITOR
                && session.getType() != GuiType.ADMIN_CLAIMED_ITEM_EDITOR
                && session.getType() != GuiType.ADMIN_PLAYER_GUI_LAYOUT_EDITOR) {
            return;
        }

        if (session.isCancelItemEditorSave()) {
            return;
        }

        if (session.getType() == GuiType.ADMIN_PLAYER_GUI_LAYOUT_EDITOR) {
            savePlayerGuiLayout(event.getInventory());
            sounds.play(player, "kit.layout-saved");
            messages.send(player, "saved");
            return;
        }

        Optional<KitDefinition> optionalKit = kits.get(session.getKitKey());
        if (optionalKit.isEmpty()) {
            return;
        }

        if (session.getType() == GuiType.ADMIN_ITEM_EDITOR) {
            saveEditorInventoryIntoKit(event.getInventory(), optionalKit.get());
        } else if (session.getType() == GuiType.ADMIN_ICON_ITEM_EDITOR) {
            saveIconItemIntoKit(event.getInventory(), optionalKit.get());
        } else {
            saveClaimedItemIntoKit(event.getInventory(), optionalKit.get());
        }
        editorSounds.save(player);
        messages.send(player, "saved");
    }

    private void handleItemEditorClick(InventoryClickEvent event, Player player, GuiSession session) {
        int raw = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();

        if (raw < topSize) {
            if (ITEM_EDITOR_EDITABLE_SLOTS.contains(raw)) {
                if (blockEditorItemExtraction(event)) {
                    return;
                }
                event.setCancelled(false);
                return;
            }

            event.setCancelled(true);
            String action = session.getActions().get(raw);
            if (action == null) {
                return;
            }

            switch (action) {
                case "back-items" -> {
                    Optional<KitDefinition> optionalKit = kits.get(session.getKitKey());
                    optionalKit.ifPresent(kit -> saveEditorInventoryIntoKit(event.getView().getTopInventory(), kit));
                    session.setCancelItemEditorSave(true);
                    editorSounds.back(player);
                    openLater(player, () -> openAdminKitSettings(player, session.getKitKey()));
                }
                case "clear-items" -> {
                    clearEditorSlots(event.getView().getTopInventory());
                    editorSounds.delete(player);
                }
                case "copy-from-inventory" -> {
                    copyPlayerInventoryIntoEditor(player, event.getView().getTopInventory());
                    sounds.play(player, "kit.items-copied");
                    messages.send(player, "copied-inventory");
                }
                default -> {
                }
            }
            return;
        }

        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR || event.getClick() == ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
            return;
        }

        if (event.getClick() == ClickType.SHIFT_LEFT
                || event.getClick() == ClickType.SHIFT_RIGHT
                || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            event.setCancelled(true);
            shiftItemIntoEditor(event, player);
        }
    }

    private void handleBlockedWorldSelectorClick(InventoryClickEvent event, Player player, TriStateSelectionHolder holder) {
        event.setCancelled(true);

        String kitKey = activeWorldSelectors.get(player.getUniqueId());
        if (kitKey == null) {
            openLater(player, () -> openAdminKitList(player));
            return;
        }

        if (event.getClickedInventory() != event.getView().getTopInventory()) {
            return;
        }

        TriStateSelectionClick click = TriStateSelectionMenus.handleClick(event.getRawSlot(), holder);
        if (click.action() == TriStateSelectionActionType.NONE) {
            return;
        }

        if (click.action() == TriStateSelectionActionType.BACK) {
            editorSounds.back(player);
            activeWorldSelectors.remove(player.getUniqueId());
            openLater(player, () -> openAdminKitSettings(player, kitKey));
            return;
        }

        if (click.action() == TriStateSelectionActionType.SEARCH) {
            editorSounds.search(player);
            openTextInput(player, prompt(
                            "world-search",
                            "{theme}Search Worlds",
                            "{muted}Filter the world list.\n{white}Use {theme}clear{white} to clear the filter.",
                            "{white}World",
                            holder.request().filter(),
                            "world name or clear",
                            64
                    ),
                    input -> {
                        String filter = input.equalsIgnoreCase("clear") || input.equalsIgnoreCase("none") ? "" : input;
                        openBlockedWorldSelector(player, kitKey, filter);
                    },
                    () -> openBlockedWorldSelector(player, kitKey, holder.request().filter(), holder.request().page()));
            return;
        }

        if (click.action() == TriStateSelectionActionType.PREVIOUS_PAGE
                || click.action() == TriStateSelectionActionType.NEXT_PAGE
                || click.action() == TriStateSelectionActionType.CLEAR_SEARCH) {
            if (click.action() == TriStateSelectionActionType.PREVIOUS_PAGE) {
                editorSounds.previousPage(player);
            } else if (click.action() == TriStateSelectionActionType.NEXT_PAGE) {
                editorSounds.nextPage(player);
            } else {
                editorSounds.clearSearch(player);
            }
            TriStateSelectionRequest nextRequest = click.nextRequest();
            openLater(player, () -> openBlockedWorldSelector(player, kitKey, nextRequest.filter(), nextRequest.page()));
            return;
        }

        if (click.action() != TriStateSelectionActionType.TOGGLE) {
            return;
        }

        KitDefinition refreshed = kits.get(kitKey).orElse(null);
        if (refreshed == null) {
            activeWorldSelectors.remove(player.getUniqueId());
            openLater(player, () -> openAdminKitList(player));
            return;
        }

        refreshed.setDenyWorlds(TriStateSelections.disabledKeys(click.nextRequest()));
        kits.save(refreshed);
        editorSounds.cycle(player);
        messages.send(player, "saved");
        String refreshedKey = refreshed.getKey();
        TriStateSelectionRequest nextRequest = click.nextRequest();
        openLater(player, () -> openBlockedWorldSelector(player, refreshedKey, nextRequest.filter(), nextRequest.page()));
    }

    private void openBlockedWorldSelector(Player player, String kitKey, String filter) {
        openBlockedWorldSelector(player, kitKey, filter, 0);
    }

    private void openBlockedWorldSelector(Player player, String kitKey, String filter, int page) {
        KitDefinition kit = kits.get(kitKey).orElse(null);
        if (kit == null) {
            activeWorldSelectors.remove(player.getUniqueId());
            openAdminKitList(player);
            return;
        }
        openBlockedWorldSelector(player, kit, filter, page);
    }

    private void openBlockedWorldSelector(Player player, KitDefinition kit, String filter) {
        openBlockedWorldSelector(player, kit, filter, 0);
    }

    private void openBlockedWorldSelector(Player player, KitDefinition kit, String filter, int page) {
        activeWorldSelectors.put(player.getUniqueId(), kit.getKey());

        TriStateSelectionRequest request = TriStateSelectionRequest.builder()
                .worldSelection()
                .entries(WorldSelectionEntries.loadedAndConfigured(plugin, kit.getDenyWorlds()))
                .states(TriStateSelections.fromEnabledDisabled(List.of(), kit.getDenyWorlds()))
                .cycleOrder(List.of(TriStateSelectionState.NEUTRAL, TriStateSelectionState.DISABLED))
                .page(page)
                .filter(filter)
                .buttons(buttons)
                .enabledLabel("Allowed")
                .disabledLabel("Blocked")
                .neutralLabel("Allowed")
                .clickHint("Click to toggle blocked.")
                .emptyTitle("No Worlds")
                .emptyLore(List.of("No loaded or configured worlds are available."))
                .build();
        TriStateSelectionMenus.open(player, request);
    }

    private void handleClaimedItemEditorClick(InventoryClickEvent event, Player player, GuiSession session) {
        int raw = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();

        if (raw < topSize) {
            if (CLAIMED_ITEM_EDITOR_EDITABLE_SLOTS.contains(raw)) {
                if (blockEditorItemExtraction(event)) {
                    return;
                }
                event.setCancelled(false);
                return;
            }

            event.setCancelled(true);
            String action = session.getActions().get(raw);
            if (action == null) {
                return;
            }

            switch (action) {
                case "save-claimed-item" -> {
                    Optional<KitDefinition> optionalKit = kits.get(session.getKitKey());
                    if (optionalKit.isPresent()) {
                        saveClaimedItemIntoKit(event.getView().getTopInventory(), optionalKit.get());
                        editorSounds.save(player);
                        messages.send(player, "saved");
                    }
                    session.setCancelItemEditorSave(true);
                    openLater(player, () -> openAdminKitSettings(player, session.getKitKey()));
                }
                case "clear-claimed-item" -> {
                    event.getView().getTopInventory().setItem(CLAIMED_ITEM_EDIT_SLOT, null);
                    editorSounds.delete(player);
                }
                case "cancel-claimed-item" -> {
                    session.setCancelItemEditorSave(true);
                    editorSounds.back(player);
                    openLater(player, () -> openAdminKitSettings(player, session.getKitKey()));
                }
                default -> {
                }
            }
            return;
        }

        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR || event.getClick() == ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
            return;
        }

        if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
            event.setCancelled(true);
        }
    }

    private void handleIconItemEditorClick(InventoryClickEvent event, Player player, GuiSession session) {
        int raw = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();

        if (raw < topSize) {
            if (CLAIMED_ITEM_EDITOR_EDITABLE_SLOTS.contains(raw)) {
                if (blockEditorItemExtraction(event)) {
                    return;
                }
                event.setCancelled(false);
                return;
            }

            event.setCancelled(true);
            String action = session.getActions().get(raw);
            if (action == null) {
                return;
            }

            switch (action) {
                case "save-icon-item" -> {
                    Optional<KitDefinition> optionalKit = kits.get(session.getKitKey());
                    if (optionalKit.isPresent()) {
                        saveIconItemIntoKit(event.getView().getTopInventory(), optionalKit.get());
                        editorSounds.save(player);
                        messages.send(player, "saved");
                    }
                    session.setCancelItemEditorSave(true);
                    openLater(player, () -> openAdminKitSettings(player, session.getKitKey()));
                }
                case "clear-icon-item" -> {
                    event.getView().getTopInventory().setItem(CLAIMED_ITEM_EDIT_SLOT, null);
                    editorSounds.delete(player);
                }
                case "cancel-icon-item" -> {
                    session.setCancelItemEditorSave(true);
                    editorSounds.back(player);
                    openLater(player, () -> openAdminKitSettings(player, session.getKitKey()));
                }
                default -> {
                }
            }
            return;
        }

        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR || event.getClick() == ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
            return;
        }

        if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
            event.setCancelled(true);
        }
    }


    private void handlePlayerGuiLayoutEditorClick(InventoryClickEvent event) {
        int raw = event.getRawSlot();
        int topSize = event.getView().getTopInventory().getSize();
        if (raw < topSize) {
            event.setCancelled(false);
            return;
        }

        if (event.getAction() == InventoryAction.COLLECT_TO_CURSOR || event.getClick() == ClickType.DOUBLE_CLICK) {
            event.setCancelled(true);
            return;
        }

        if (event.getClick() == ClickType.SHIFT_LEFT || event.getClick() == ClickType.SHIFT_RIGHT) {
            event.setCancelled(true);
        }
    }

    private boolean blockEditorItemExtraction(InventoryClickEvent event) {
        ClickType click = event.getClick();
        if (click.isShiftClick()
                || click == ClickType.NUMBER_KEY
                || click == ClickType.SWAP_OFFHAND
                || click == ClickType.DROP
                || click == ClickType.CONTROL_DROP
                || click == ClickType.MIDDLE
                || click == ClickType.CREATIVE
                || click == ClickType.DOUBLE_CLICK
                || event.getAction() == InventoryAction.COLLECT_TO_CURSOR
                || event.getAction() == InventoryAction.MOVE_TO_OTHER_INVENTORY) {
            event.setCancelled(true);
            return true;
        }

        ItemStack cursor = event.getCursor();
        ItemStack current = event.getCurrentItem();
        if (isEmpty(cursor) && !isEmpty(current)) {
            event.setCancelled(true);
            event.setCurrentItem(null);
            return true;
        }
        if (!isEmpty(cursor) && !isEmpty(current)) {
            event.setCancelled(true);
            return true;
        }
        return false;
    }

    private void handleEntryBrowserClick(Player player, int slot, ClickType clickType, EntryBrowserHolder holder) {
        EntryBrowserClick click = EntryBrowserMenus.handleClick(slot, holder, clickType);
        AdminListState state = adminListStates.getOrDefault(player.getUniqueId(),
                new AdminListState(holder.request().page(), holder.request().filter()));
        GuiSession listSession = new GuiSession(GuiType.ADMIN_KIT_LIST, "");
        listSession.setPage(state.page());
        listSession.setSearchQuery(state.search());
        switch (click.action()) {
            case ENTRY -> {
                String key = click.entryId();
                if (clickType != null && clickType.isShiftClick() && clickType.isRightClick()) {
                    if (kits.delete(key)) {
                        sounds.play(player, "kit.deleted");
                        messages.send(player, "deleted", Map.of("{kit}", key));
                    } else {
                        editorSounds.error(player);
                        messages.send(player, "claim-kit-not-found", Map.of("{kit}", key));
                    }
                    openLater(player, () -> openAdminKitList(player));
                } else if (clickType != null && clickType.isRightClick()) {
                    editorSounds.open(player);
                    kits.get(key).ifPresent(kit -> openLater(player, () -> openPreview(player, kit, true)));
                } else {
                    editorSounds.open(player);
                    openLater(player, () -> openAdminKitSettings(player, key));
                }
            }
            case ADD -> {
                editorSounds.add(player);
                handleAction(player, listSession, "create-kit", clickType);
            }
            case SEARCH -> {
                editorSounds.search(player);
                handleAction(player, listSession, "search-kit-list", clickType);
            }
            case CLEAR_SEARCH -> {
                editorSounds.clearSearch(player);
                openLater(player, () -> openAdminKitList(player, 0, ""));
            }
            case PREVIOUS_PAGE -> {
                editorSounds.previousPage(player);
                openLater(player, () -> openAdminKitList(
                        player, holder.request().page() - 1, holder.request().filter()));
            }
            case NEXT_PAGE -> {
                editorSounds.nextPage(player);
                openLater(player, () -> openAdminKitList(
                        player, holder.request().page() + 1, holder.request().filter()));
            }
            case BACK -> {
                editorSounds.back(player);
                openLater(player, () -> openAdminRoot(player, false));
            }
            case NONE -> {
            }
        }
    }

    private void handleAction(Player player, GuiSession session, String action, ClickType click) {
        if (action.startsWith("open:")) {
            editorSounds.open(player);
            String target = action.substring("open:".length());
            switch (target) {
                case "admin-root" -> openLater(player, () -> openAdminRoot(player, false));
                case "admin-kit-list" -> openLater(player, () -> openAdminKitList(player));
                case "admin-kit-list:reset" -> openLater(player, () -> openAdminKitList(player, 0, ""));
                case "admin-gui-settings" -> openLater(player, () -> openAdminGuiSettings(player));
                default -> {
                }
            }
            return;
        }

        if (action.startsWith("back:")) {
            String target = action.substring("back:".length());
            if (target.equals("player-kits")) {
                guiSounds.back(player);
                openLater(player, () -> openPlayerKits(player, false));
                return;
            }
            editorSounds.back(player);
            if (target.equals("admin-root")) {
                openLater(player, () -> openAdminRoot(player, false));
                return;
            }
            if (target.startsWith("admin-kit-settings:")) {
                String key = target.substring("admin-kit-settings:".length());
                openLater(player, () -> openAdminKitSettings(player, key));
            }
            return;
        }

        if (action.startsWith("kit:")) {
            String kitKey = action.substring("kit:".length());
            Optional<KitDefinition> optionalKit = kits.get(kitKey);
            if (optionalKit.isEmpty()) {
                guiSounds.error(player);
                openLater(player, () -> openPlayerKits(player, false));
                return;
            }
            KitDefinition kit = optionalKit.get();
            if (click.isRightClick()) {
                openLater(player, () -> openPreview(player, kit, false));
                return;
            }

            ClaimResult result = kitService.claim(player, kit);
            kitService.sendClaimFeedback(player, result);
            openLater(player, () -> openPlayerKits(player, false));
            return;
        }

        if (action.equals("create-kit")) {
            openTextInput(player, prompt(
                            "create-kit",
                            "{theme}Create Kit",
                            "{muted}Enter a new kit key.\n{white}Allowed: {format}",
                            "{white}Kit key",
                            "",
                            "letters, numbers, _ or -",
                            64
                    ),
                    input -> {
                        String key = KitDefinition.sanitizeKey(input);
                        if (kits.exists(key)) {
                            editorSounds.error(player);
                            messages.send(player, "invalid-input");
                            openAdminKitList(player);
                            return;
                        }
                        try {
                            kits.createNew(key);
                            sounds.play(player, "kit.created");
                            messages.send(player, "created", Map.of("{kit}", key));
                        } catch (Exception exception) {
                            editorSounds.error(player);
                            messages.send(player, "invalid-input");
                        }
                        openAdminKitList(player);
                    },
                    () -> openAdminKitList(player));
            return;
        }

        if (action.equals("search-kit-list")) {
            openTextInput(player, prompt(
                            "kit-search",
                            "{theme}Search Kits",
                            "{muted}Filter by key or display name.\n{white}Type {theme}clear{white} to clear the filter.",
                            "{white}Search",
                            session.getSearchQuery(),
                            "text, clear, or none",
                            64
                    ),
                    input -> {
                        String normalized = normalizeSearch(input);
                        if (normalized.equalsIgnoreCase("clear") || normalized.equalsIgnoreCase("none")) {
                            normalized = "";
                        }
                        openAdminKitList(player, 0, normalized);
                    },
                    () -> openAdminKitList(player, session.getPage(), session.getSearchQuery()));
            return;
        }

        if (action.equals("kit-list-prev-page")) {
            editorSounds.previousPage(player);
            openLater(player, () -> openAdminKitList(player, session.getPage() - 1, session.getSearchQuery()));
            return;
        }

        if (action.equals("kit-list-next-page")) {
            editorSounds.nextPage(player);
            openLater(player, () -> openAdminKitList(player, session.getPage() + 1, session.getSearchQuery()));
            return;
        }

        if (action.equals("kit-list-clear-search")) {
            editorSounds.clearSearch(player);
            openLater(player, () -> openAdminKitList(player, 0, ""));
            return;
        }

        if (action.equals("cancel-delete-kit")) {
            editorSounds.back(player);
            openLater(player, () -> openAdminKitSettings(player, session.getKitKey()));
            return;
        }

        if (action.equals("confirm-delete-kit")) {
            deleteKitAndReturn(player, session.getKitKey());
            return;
        }

        if (action.startsWith("kit-list:")) {
            String key = action.substring("kit-list:".length());
            if (click.isShiftClick() && click.isRightClick()) {
                if (kits.delete(key)) {
                    sounds.play(player, "kit.deleted");
                    messages.send(player, "deleted", Map.of("{kit}", key));
                } else {
                    editorSounds.error(player);
                    messages.send(player, "claim-kit-not-found", Map.of("{kit}", key));
                }
                openLater(player, () -> openAdminKitList(player));
                return;
            }
            if (click.isRightClick()) {
                editorSounds.open(player);
                kits.get(key).ifPresent(kit -> openLater(player, () -> openPreview(player, kit, true)));
                return;
            }
            editorSounds.open(player);
            openLater(player, () -> openAdminKitSettings(player, key));
            return;
        }

        if (action.equals("open-gui-layout-editor")
                || action.equals("edit-gui-title")
                || action.equals("edit-gui-rows")
                || action.equals("toggle-gui-fill")
                || action.equals("edit-gui-filler")) {
            handleGuiSettingsAction(player, action);
            return;
        }

        Optional<KitDefinition> optionalKit = kits.get(session.getKitKey());
        if (optionalKit.isEmpty()) {
            openLater(player, () -> openAdminKitList(player));
            return;
        }
        KitDefinition kit = optionalKit.get();

        switch (action) {
            case "toggle-enabled" -> {
                boolean enabled = !kit.isEnabled();
                kit.setEnabled(enabled);
                kits.save(kit);
                editorSounds.toggle(player, enabled);
                messages.send(player, "saved");
                openLater(player, () -> openAdminKitSettings(player, kit.getKey()));
            }
            case "toggle-claim-mode" -> {
                kit.setClaimMode(kit.getClaimMode() == ClaimMode.COOLDOWN ? ClaimMode.ONE_TIME : ClaimMode.COOLDOWN);
                kits.save(kit);
                editorSounds.cycle(player);
                messages.send(player, "saved");
                openLater(player, () -> openAdminKitSettings(player, kit.getKey()));
            }
            case "toggle-broadcast" -> {
                boolean enabled = !kit.isBroadcastOnClaim();
                kit.setBroadcastOnClaim(enabled);
                kits.save(kit);
                editorSounds.toggle(player, enabled);
                messages.send(player, "saved");
                openLater(player, () -> openAdminKitSettings(player, kit.getKey()));
            }
            case "edit-display-name" -> {
                openTextInput(player, prompt(
                                "kit-display-name",
                                "{theme}Display Name",
                                "{muted}Set the kit display name.\n{white}Example: {format}",
                                "{white}Display name",
                                kit.getDisplayName(),
                                "&aStarter Kit",
                                128
                        ),
                        input -> {
                            KitDefinition refreshed = kits.get(kit.getKey()).orElse(null);
                            if (refreshed == null) {
                                editorSounds.error(player);
                                openAdminKitList(player);
                                return;
                            }
                            refreshed.setDisplayName(input);
                            kits.save(refreshed);
                            editorSounds.save(player);
                            messages.send(player, "saved");
                            openAdminKitSettings(player, refreshed.getKey());
                        },
                        () -> openAdminKitSettings(player, kit.getKey()));
            }
            case "edit-order-index" -> {
                openTextInput(player, prompt(
                                "kit-order-index",
                                "{theme}Order Index",
                                "{muted}Lower indexes appear first.\n{white}Expected: {format}",
                                "{white}Order index",
                                String.valueOf(kit.getOrderIndex()),
                                "0 or higher",
                                10
                        ),
                        input -> {
                            int value;
                            try {
                                value = Integer.parseInt(input);
                            } catch (NumberFormatException exception) {
                                editorSounds.error(player);
                                messages.send(player, "invalid-input");
                                openAdminKitSettings(player, kit.getKey());
                                return;
                            }

                            if (value < 0) {
                                editorSounds.error(player);
                                messages.send(player, "invalid-input");
                                openAdminKitSettings(player, kit.getKey());
                                return;
                            }

                            KitDefinition refreshed = kits.get(kit.getKey()).orElse(null);
                            if (refreshed == null) {
                                editorSounds.error(player);
                                openAdminKitList(player);
                                return;
                            }
                            refreshed.setOrderIndex(value);
                            kits.save(refreshed);
                            editorSounds.save(player);
                            messages.send(player, "saved");
                            openAdminKitSettings(player, refreshed.getKey());
                        },
                        () -> openAdminKitSettings(player, kit.getKey()));
            }
            case "edit-permission" -> {
                openTextInput(player, prompt(
                                "kit-permission",
                                "{theme}Required Permission",
                                "{muted}Set the permission required to claim this kit.\n{white}Use {theme}none{white} to clear.",
                                "{white}Permission",
                                kit.getRequiredPermission().isBlank() ? "none" : kit.getRequiredPermission(),
                                "permission node or none",
                                128
                        ),
                        input -> {
                            KitDefinition refreshed = kits.get(kit.getKey()).orElse(null);
                            if (refreshed == null) {
                                editorSounds.error(player);
                                openAdminKitList(player);
                                return;
                            }
                            refreshed.setRequiredPermission(input.equalsIgnoreCase("none") ? "" : input);
                            kits.save(refreshed);
                            editorSounds.save(player);
                            messages.send(player, "saved");
                            openAdminKitSettings(player, refreshed.getKey());
                        },
                        () -> openAdminKitSettings(player, kit.getKey()));
            }
            case "edit-cooldown" -> {
                openTextInput(player, prompt(
                                "kit-cooldown",
                                "{theme}Cooldown",
                                "{muted}Set the cooldown duration.\n{white}Example: {format}",
                                "{white}Cooldown",
                                TimeUtil.formatDuration(kit.getCooldownMillis()),
                                "12h30m",
                                32
                        ),
                        input -> {
                            long millis = TimeUtil.parseDurationMillis(input);
                            if (millis <= 0L) {
                                editorSounds.error(player);
                                messages.send(player, "invalid-input");
                                openAdminKitSettings(player, kit.getKey());
                                return;
                            }

                            KitDefinition refreshed = kits.get(kit.getKey()).orElse(null);
                            if (refreshed == null) {
                                editorSounds.error(player);
                                openAdminKitList(player);
                                return;
                            }
                            refreshed.setCooldownMillis(millis);
                            kits.save(refreshed);
                            editorSounds.save(player);
                            messages.send(player, "saved");
                            openAdminKitSettings(player, refreshed.getKey());
                        },
                        () -> openAdminKitSettings(player, kit.getKey()));
            }
            case "edit-worlds" -> {
                editorSounds.open(player);
                openBlockedWorldSelector(player, kit, "");
            }
            case "edit-commands" -> {
                openTextInput(player, prompt(
                                "kit-commands",
                                "{theme}Commands On Claim",
                                "{muted}Set commands run by console when the kit is claimed.\n{white}Format: {format}",
                                "{white}Commands",
                                kit.getCommandsOnClaim().isEmpty() ? "none" : String.join("|", kit.getCommandsOnClaim()),
                                "cmd1|cmd2|cmd3 or none",
                                512
                        ),
                        input -> {
                            KitDefinition refreshed = kits.get(kit.getKey()).orElse(null);
                            if (refreshed == null) {
                                editorSounds.error(player);
                                openAdminKitList(player);
                                return;
                            }

                            if (input.equalsIgnoreCase("none")) {
                                refreshed.setCommandsOnClaim(List.of());
                            } else {
                                String[] split = input.split("\\|");
                                List<String> commands = new ArrayList<>();
                                for (String command : split) {
                                    if (!command.isBlank()) {
                                        commands.add(command.trim());
                                    }
                                }
                                refreshed.setCommandsOnClaim(commands);
                            }

                            kits.save(refreshed);
                            editorSounds.save(player);
                            messages.send(player, "saved");
                            openAdminKitSettings(player, refreshed.getKey());
                        },
                        () -> openAdminKitSettings(player, kit.getKey()));
            }
            case "rename-key" -> {
                openTextInput(player, prompt(
                                "kit-rename",
                                "{theme}Rename Kit Key",
                                "{muted}Enter the new kit key.\n{white}Allowed: {format}",
                                "{white}New key",
                                kit.getKey(),
                                "letters, numbers, _ or -",
                                64
                        ),
                        input -> {
                            String newKey = KitDefinition.sanitizeKey(input);
                            Optional<KitDefinition> renamed = kits.rename(kit.getKey(), newKey);
                            if (renamed.isEmpty()) {
                                editorSounds.error(player);
                                messages.send(player, "invalid-input");
                                openAdminKitSettings(player, kit.getKey());
                                return;
                            }
                            sounds.play(player, "kit.renamed");
                            messages.send(player, "renamed", Map.of("{kit}", newKey));
                            openAdminKitSettings(player, newKey);
                        },
                        () -> openAdminKitSettings(player, kit.getKey()));
            }
            case "delete-kit" -> {
                editorSounds.open(player);
                openLater(player, () -> openDeleteConfirmGui(player, kit.getKey()));
            }
            case "open-item-editor" -> {
                editorSounds.open(player);
                openLater(player, () -> openAdminItemEditor(player, kit.getKey()));
            }
            case "open-icon-item-editor" -> {
                editorSounds.open(player);
                openLater(player, () -> openAdminIconItemEditor(player, kit.getKey()));
            }
            case "open-claimed-item-editor" -> {
                editorSounds.open(player);
                openLater(player, () -> openAdminClaimedItemEditor(player, kit.getKey()));
            }
            case "preview-kit" -> {
                editorSounds.open(player);
                openLater(player, () -> openPreview(player, kit, true));
            }
            default -> {
            }
        }
    }

    private void deleteKitAndReturn(Player player, String kitKey) {
        boolean existed = kits.exists(kitKey);
        boolean deleted = existed && kits.delete(kitKey);
        if (deleted) {
            sounds.play(player, "kit.deleted");
            messages.send(player, "deleted", Map.of("{kit}", kitKey));
        } else {
            editorSounds.error(player);
            messages.send(player, "claim-kit-not-found", Map.of("{kit}", kitKey));
        }
        openAdminKitList(player);
    }

    private void handleGuiSettingsAction(Player player, String action) {
        switch (action) {
            case "open-gui-layout-editor" -> {
                editorSounds.open(player);
                openLater(player, () -> openAdminPlayerGuiLayoutEditor(player));
            }
            case "edit-gui-title" -> {
                openTextInput(player, prompt(
                                "gui-title",
                                "{theme}Main GUI Title",
                                "{muted}Set the title shown on the player kits menu.",
                                "{white}Title",
                                settings.playerGuiTitle(),
                                "text with color codes",
                                128
                        ),
                        input -> {
                            FileConfiguration config = settings.rawConfig();
                            config.set("player-gui.title", input);
                            plugin.saveConfig();
                            editorSounds.save(player);
                            messages.send(player, "saved");
                            openAdminGuiSettings(player);
                        },
                        () -> openAdminGuiSettings(player));
            }
            case "edit-gui-rows" -> {
                openTextInput(player, prompt(
                                "gui-rows",
                                "{theme}Main GUI Rows",
                                "{muted}Set the player kits menu height.\n{white}Expected: {format}",
                                "{white}Rows",
                                String.valueOf(settings.playerGuiRows()),
                                "1-6",
                                1
                        ),
                        input -> {
                            int rows;
                            try {
                                rows = Integer.parseInt(input);
                            } catch (NumberFormatException exception) {
                                editorSounds.error(player);
                                messages.send(player, "invalid-input");
                                openAdminGuiSettings(player);
                                return;
                            }

                            if (rows < 1 || rows > 6) {
                                editorSounds.error(player);
                                messages.send(player, "invalid-input");
                                openAdminGuiSettings(player);
                                return;
                            }

                            settings.rawConfig().set("player-gui.rows", rows);
                            plugin.saveConfig();
                            editorSounds.save(player);
                            messages.send(player, "saved");
                            openAdminGuiSettings(player);
                        },
                        () -> openAdminGuiSettings(player));
            }
            case "toggle-gui-fill" -> {
                boolean next = !settings.fillBackground();
                settings.rawConfig().set("player-gui.fill-background", next);
                plugin.saveConfig();
                editorSounds.toggle(player, next);
                messages.send(player, "saved");
                openLater(player, () -> openAdminGuiSettings(player));
            }
            case "edit-gui-filler" -> {
                openTextInput(player, prompt(
                                "gui-filler",
                                "{theme}Filler Material",
                                "{muted}Set the background filler item material.\n{white}Example: {format}",
                                "{white}Material",
                                settings.fillerMaterial().name(),
                                "BLACK_STAINED_GLASS_PANE",
                                64
                        ),
                        input -> {
                            Material material = Material.matchMaterial(input);
                            if (material == null || material.isAir()) {
                                editorSounds.error(player);
                                messages.send(player, "invalid-input");
                                openAdminGuiSettings(player);
                                return;
                            }
                            settings.rawConfig().set("player-gui.filler-material", material.name());
                            plugin.saveConfig();
                            editorSounds.save(player);
                            messages.send(player, "saved");
                            openAdminGuiSettings(player);
                        },
                        () -> openAdminGuiSettings(player));
            }
            default -> {
            }
        }
    }

    private void openTextInput(Player player, TextDialogRequest request, Consumer<String> onSubmit, Runnable onCancel) {
        DialogService activeDialogs = dialogs == null ? core.dialogService() : dialogs;
        Consumer<String> submit = value -> {
            if (onSubmit != null) {
                onSubmit.accept(value == null ? "" : value.trim());
            }
        };
        Runnable cancel = () -> {
            if (activeDialogs.support().canUseNativeDialogs()) {
                editorSounds.back(player);
                messages.send(player, "prompt-cancelled");
            }
            run(onCancel);
        };

        boolean openedNative = EditorDialogInputs.openTextFromInventory(
                plugin,
                core.inventoryCloseSuppressor(),
                activeDialogs,
                player,
                request,
                submit,
                cancel
        );
        if (openedNative) {
            editorSounds.open(player);
        }
        warnNativeFallback(player, activeDialogs.support(), openedNative);
    }

    private void openChatFallback(Player player, TextDialogRequest request, Consumer<String> onSubmit, Runnable onCancel) {
        player.closeInventory();
        List<String> lines = new ArrayList<>();
        for (String line : chatPromptLines(request)) {
            lines.add(messages.renderTemplate(line, Map.of()));
        }
        lines.add(messages.render("prompt-start", "{prefix}{muted}Type your input in chat. Type {theme}cancel {muted}to abort."));
        prompts.openRaw(player, lines, "cancel", onSubmit, () -> {
            messages.send(player, "prompt-cancelled", "{prefix}{bad}Input cancelled.");
            run(onCancel);
        });
    }

    private void warnNativeFallback(Player player, NativeDialogSupport support, boolean openedNative) {
        if (openedNative || support == null || !support.configEnabled() || support.canUseNativeDialogs() || !support.warnOnFallback()) {
            return;
        }
        if (!player.hasPermission("fokits.admin")) {
            return;
        }
        if (warnedNativeFallbackPlayers.add(player.getUniqueId())) {
            messages.send(player, "native-dialogs-fallback");
        }
    }

    private List<String> chatPromptLines(TextDialogRequest request) {
        List<String> lines = new ArrayList<>();
        lines.add(request.title());
        lines.addAll(request.body());
        if (!request.fieldLabel().isBlank()) {
            lines.add("{muted}" + request.fieldLabel());
        }
        if (!request.initialValue().isBlank()) {
            lines.add("{white}Current: {theme}" + request.initialValue());
        }
        if (!request.placeholder().isBlank()) {
            lines.add("{white}Expected: {theme}" + request.placeholder());
        }
        return lines;
    }

    private TextDialogRequest prompt(
            String dialogId,
            String title,
            String body,
            String fieldLabel,
            String currentValue,
            String format,
            int maxLength
    ) {
        Map<String, String> replacements = new HashMap<>();
        replacements.put("{current}", currentValue == null ? "" : currentValue);
        replacements.put("{format}", format == null ? "" : format);
        replacements.put("{setting}", title == null ? "" : title);

        return new TextDialogRequest(
                messages.renderTemplate(title, replacements),
                formatDialogBody(body, replacements),
                messages.renderTemplate(fieldLabel, replacements),
                currentValue == null ? "" : currentValue,
                format == null ? "" : format,
                submitButton(dialogId),
                DialogButton.cancel("Cancel", "", 100),
                300,
                300,
                maxLength,
                true,
                false,
                false
        );
    }

    private DialogButton submitButton(String dialogId) {
        if (dialogId != null && dialogId.endsWith("-search")) {
            return DialogButton.search("Search", "", 100);
        }
        if ("create-kit".equals(dialogId)) {
            return DialogButton.confirm("Create", "", 100);
        }
        return DialogButton.save("Save", "", 100);
    }

    private List<String> formatDialogBody(String body, Map<String, String> replacements) {
        if (body == null || body.isBlank()) {
            return List.of();
        }
        List<String> lines = new ArrayList<>();
        for (String line : body.split("\\R", -1)) {
            lines.add(messages.renderTemplate(line, replacements));
        }
        return lines;
    }

    private static void run(Runnable runnable) {
        if (runnable != null) {
            runnable.run();
        }
    }

    private ItemStack buildKitDisplayIcon(Player player, KitDefinition kit) {
        List<String> lore = new ArrayList<>();

        KitViewState state = kitService.getViewState(player, kit);
        switch (state) {
            case AVAILABLE -> lore.addAll(settings.loreForState("available"));
            case COOLDOWN -> lore.addAll(settings.loreForState("cooldown"));
            case NO_PERMISSION, WORLD_BLOCKED -> lore.addAll(settings.loreForState("no-permission"));
            case CLAIMED_ONCE -> lore.addAll(settings.loreForState("claimed-once"));
            case DISABLED -> lore.addAll(settings.loreForState("disabled"));
        }

        long remaining = kitService.remainingMillis(player, kit);
        Map<String, String> replacements = Map.of(
                "{time_remaining}", TimeUtil.formatDuration(remaining)
        );

        List<String> renderedLore = new ArrayList<>();
        for (String line : lore) {
            renderedLore.add(messages.renderTemplate(line, replacements));
        }

        ItemStack item = resolveBaseKitDisplayItem(state, kit);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (!meta.hasDisplayName()) {
                meta.setDisplayName(messages.renderTemplate("{theme}" + kit.getDisplayOrKey(), Map.of()));
            }
            meta.setLore(renderedLore);
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack resolveBaseKitDisplayItem(KitViewState state, KitDefinition kit) {
        if ((state == KitViewState.CLAIMED_ONCE || state == KitViewState.COOLDOWN)
                && kit.getClaimedDisplayItem() != null
                && !kit.getClaimedDisplayItem().getType().isAir()) {
            return kit.getClaimedDisplayItem().clone();
        }
        if (kit.getIconItem() != null && !kit.getIconItem().getType().isAir()) {
            return kit.getIconItem().clone();
        }
        Material icon = kit.getIconMaterial() == null ? Material.CHEST : kit.getIconMaterial();
        ItemStack fallback = new ItemStack(icon);
        if (kit.getIconCustomModelData() != null) {
            ItemMeta meta = fallback.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(kit.getIconCustomModelData());
                fallback.setItemMeta(meta);
            }
        }
        return fallback;
    }

    private ItemStack buildAdminKitItem(KitDefinition kit) {
        List<String> lore = new ArrayList<>();
        lore.add("{white}Left click: open kit settings");
        lore.add("{white}Right click: preview kit");
        lore.add("{white}Shift+Right click: delete kit");
        lore.add("");
        lore.add("{muted}Order: {theme}" + kit.getOrderIndex());
        lore.add("{muted}Mode: {theme}" + kit.getClaimMode().name().toLowerCase(Locale.ROOT));
        lore.add("{muted}Cooldown: {theme}" + TimeUtil.formatDuration(kit.getCooldownMillis()));
        lore.add("{muted}Permission: {theme}" + (kit.getRequiredPermission().isBlank() ? "none" : kit.getRequiredPermission()));

        return makeDisplayItem(resolveBaseKitDisplayItem(KitViewState.AVAILABLE, kit), "{theme}" + kit.getDisplayOrKey(), lore);
    }

    private void saveEditorInventoryIntoKit(Inventory inventory, KitDefinition kit) {
        ItemStack[] armor = new ItemStack[4];
        armor[0] = cloneSlot(inventory, 1);
        armor[1] = cloneSlot(inventory, 2);
        armor[2] = cloneSlot(inventory, 3);
        armor[3] = cloneSlot(inventory, 4);

        ItemStack[] items = new ItemStack[36];
        for (int i = 0; i < 36; i++) {
            items[i] = cloneSlot(inventory, 9 + i);
        }

        kit.setArmorItems(armor);
        kit.setInventoryItems(items);
        kit.setOffhandItem(cloneSlot(inventory, KIT_OFFHAND_SLOT));
        kits.save(kit);
    }

    private void saveClaimedItemIntoKit(Inventory inventory, KitDefinition kit) {
        kit.setClaimedDisplayItem(cloneSlot(inventory, CLAIMED_ITEM_EDIT_SLOT));
        kits.save(kit);
    }

    private void saveIconItemIntoKit(Inventory inventory, KitDefinition kit) {
        kit.setIconItem(cloneSlot(inventory, CLAIMED_ITEM_EDIT_SLOT));
        kits.save(kit);
    }

    private void savePlayerGuiLayout(Inventory inventory) {
        int size = inventory.getSize();
        List<Integer> kitSlots = new ArrayList<>();
        settings.rawConfig().set("player-gui.layout.static-items", null);

        for (int slot = 0; slot < size; slot++) {
            ItemStack item = inventory.getItem(slot);
            if (item == null || item.getType().isAir()) {
                continue;
            }

            if (item.getType() == KIT_SLOT_MARKER_MATERIAL) {
                kitSlots.add(slot);
                continue;
            }

            settings.rawConfig().set("player-gui.layout.static-items." + slot, item);
        }

        settings.rawConfig().set("player-gui.layout.kit-slots", kitSlots);
        plugin.saveConfig();
    }

    private void clearEditorSlots(Inventory inventory) {
        for (Integer slot : ITEM_EDITOR_EDITABLE_SLOTS) {
            inventory.setItem(slot, null);
        }
    }

    private void copyPlayerInventoryIntoEditor(Player player, Inventory editor) {
        clearEditorSlots(editor);

        PlayerInventory source = player.getInventory();
        editor.setItem(1, cloneItem(source.getHelmet()));
        editor.setItem(2, cloneItem(source.getChestplate()));
        editor.setItem(3, cloneItem(source.getLeggings()));
        editor.setItem(4, cloneItem(source.getBoots()));
        editor.setItem(KIT_OFFHAND_SLOT, cloneItem(source.getItemInOffHand()));

        ItemStack[] storage = source.getStorageContents();
        for (int i = 0; i < 36; i++) {
            ItemStack item = i < storage.length ? storage[i] : null;
            editor.setItem(9 + i, cloneItem(item));
        }
    }

    private void shiftItemIntoEditor(InventoryClickEvent event, Player player) {
        ItemStack current = event.getCurrentItem();
        if (current == null || current.getType().isAir() || current.getAmount() <= 0) {
            return;
        }

        int remaining = moveItemIntoEditor(event.getView().getTopInventory(), current);
        if (remaining < current.getAmount()) {
            editorSounds.add(player);
        }
        if (remaining <= 0) {
            event.setCurrentItem(null);
            return;
        }

        ItemStack remainder = current.clone();
        remainder.setAmount(remaining);
        event.setCurrentItem(remainder);
    }

    private int moveItemIntoEditor(Inventory editor, ItemStack source) {
        int remaining = source.getAmount();
        List<Integer> targetSlots = shiftTargetSlots(source);

        for (int slot : targetSlots) {
            ItemStack current = editor.getItem(slot);
            if (current == null || current.getType().isAir() || !current.isSimilar(source)) {
                continue;
            }

            int maxStackSize = Math.min(current.getMaxStackSize(), editor.getMaxStackSize());
            int space = maxStackSize - current.getAmount();
            if (space <= 0) {
                continue;
            }

            int moved = Math.min(space, remaining);
            current.setAmount(current.getAmount() + moved);
            remaining -= moved;
            if (remaining <= 0) {
                return 0;
            }
        }

        for (int slot : targetSlots) {
            ItemStack current = editor.getItem(slot);
            if (current != null && !current.getType().isAir()) {
                continue;
            }

            int moved = Math.min(source.getMaxStackSize(), remaining);
            ItemStack copy = source.clone();
            copy.setAmount(moved);
            editor.setItem(slot, copy);
            remaining -= moved;
            if (remaining <= 0) {
                return 0;
            }
        }

        return remaining;
    }

    private List<Integer> shiftTargetSlots(ItemStack item) {
        List<Integer> targets = new ArrayList<>();
        int armorSlot = armorSlot(item.getType());
        if (armorSlot >= 0) {
            targets.add(armorSlot);
        }
        targets.addAll(ITEM_EDITOR_STORAGE_SLOTS);
        for (int slot : ITEM_EDITOR_EDITABLE_SLOT_ORDER) {
            if (!targets.contains(slot)) {
                targets.add(slot);
            }
        }
        return targets;
    }

    private int armorSlot(Material material) {
        String name = material.name();
        if (name.endsWith("_HELMET") || name.equals("TURTLE_HELMET") || name.equals("CARVED_PUMPKIN")) {
            return 1;
        }
        if (name.endsWith("_CHESTPLATE") || name.equals("ELYTRA")) {
            return 2;
        }
        if (name.endsWith("_LEGGINGS")) {
            return 3;
        }
        if (name.endsWith("_BOOTS")) {
            return 4;
        }
        return -1;
    }

    private ItemStack cloneSlot(Inventory inventory, int slot) {
        ItemStack item = inventory.getItem(slot);
        return cloneItem(item);
    }

    private ItemStack cloneItem(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        return item.clone();
    }

    private boolean isEmpty(ItemStack item) {
        return item == null || item.getType().isAir() || item.getAmount() <= 0;
    }

    private int resolveSlot(int preferred, int size, Set<Integer> occupied) {
        if (preferred >= 0 && preferred < size && !occupied.contains(preferred)) {
            return preferred;
        }
        for (int i = 0; i < size; i++) {
            if (!occupied.contains(i)) {
                return i;
            }
        }
        return -1;
    }

    private String guiTitle(String title) {
        String rendered = messages.renderTemplate(title == null ? "" : title, Map.of());
        String stripped = ChatColor.stripColor(rendered);
        if (stripped == null) {
            stripped = "";
        }
        stripped = LEGACY_AMPERSAND_COLOR_PATTERN.matcher(stripped).replaceAll("");
        return messages.renderTemplate("&8" + stripped, Map.of());
    }

    private void fill(Inventory inventory, Material material) {
        ItemStack filler = makeItem(material, " ", List.of(), null);
        for (int i = 0; i < inventory.getSize(); i++) {
            inventory.setItem(i, filler);
        }
    }

    private ItemStack makeItem(Material material, String displayName, List<String> lore, Integer customModelData) {
        Material safeMaterial = material == null || material.isAir() ? Material.STONE : material;
        ItemStack item = new ItemStack(safeMaterial);
        return makeDisplayItem(item, displayName, lore, customModelData);
    }

    private ItemStack makeDisplayItem(ItemStack base, String displayName, List<String> lore) {
        ItemStack item = cloneItem(base);
        if (item == null) {
            item = new ItemStack(Material.STONE);
        }
        return makeDisplayItem(item, displayName, lore, null);
    }

    private ItemStack makeDisplayItem(ItemStack item, String displayName, List<String> lore, Integer customModelData) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(messages.renderTemplate(displayName, Map.of()));
            List<String> colored = new ArrayList<>();
            for (String line : lore) {
                colored.add(messages.renderTemplate(line, Map.of()));
            }
            meta.setLore(colored);
            if (customModelData != null) {
                meta.setCustomModelData(customModelData);
            }
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack cloneOrNull(ItemStack[] source, int index) {
        if (source == null || index < 0 || index >= source.length || source[index] == null) {
            return null;
        }
        return source[index].clone();
    }

    private boolean matchesSearch(KitDefinition kit, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String normalized = search.toLowerCase(Locale.ROOT);
        String key = kit.getKey().toLowerCase(Locale.ROOT);
        String display = kit.getDisplayOrKey().toLowerCase(Locale.ROOT);
        return key.contains(normalized) || display.contains(normalized);
    }

    private String normalizeSearch(String input) {
        return input == null ? "" : input.trim().toLowerCase(Locale.ROOT);
    }

    private void openLater(Player player, Runnable action) {
        core.scheduler().runForPlayer(player, action);
    }

    private String safe(String input) {
        return input == null ? "" : input;
    }

    private record AdminListState(int page, String search) {
    }
}
