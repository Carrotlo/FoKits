package me.foesio.foKits.gui;

import me.foesio.core.gui.publicgui.GuiLoadReport;
import me.foesio.core.gui.publicgui.PublicGuiContract;
import me.foesio.core.gui.publicgui.PublicGuiDefinition;
import me.foesio.core.gui.publicgui.PublicGuiRenderInput;
import me.foesio.core.gui.publicgui.PublicGuiService;
import me.foesio.core.message.FoMessageService;
import me.foesio.core.sound.FoGuiSounds;
import me.foesio.foKits.model.KitDefinition;
import me.foesio.foKits.model.KitViewState;
import me.foesio.foKits.service.KitService;
import me.foesio.foKits.storage.KitRepository;
import me.foesio.foKits.util.TimeUtil;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;

import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

/** Core-owned, read-only kit selector and preview. Kit data and claim rules stay plugin-owned. */
final class PublicKitGui implements AutoCloseable {
    private static final String MAIN = "kits.main";
    private static final String PREVIEW = "kits.preview";

    private final GuiManager parent;
    private final KitRepository kits;
    private final KitService kitService;
    private final FoMessageService messages;
    private final FoGuiSounds sounds;
    private final PublicGuiService service;
    private final Path mainFile;
    private final Map<UUID, Context> contexts = new HashMap<>();

    PublicKitGui(JavaPlugin plugin, GuiManager parent, KitRepository kits, KitService kitService,
                 FoMessageService messages, FoGuiSounds sounds) {
        this.parent = parent;
        this.kits = kits;
        this.kitService = kitService;
        this.messages = messages;
        this.sounds = sounds;
        Path guiRoot = plugin.getDataFolder().toPath().resolve("guis");
        mainFile = guiRoot.resolve("player-kits.yml");
        service = new PublicGuiService(plugin)
                .menu(MAIN, mainFile, new PublicGuiContract(
                        Set.of("kits.page.previous", "kits.page.next", "kits.claim", "kits.preview"),
                        Map.of(), Set.of("kit", "kit_key", "material", "material_sprite",
                                "time_remaining", "permission", "world", "page", "pages",
                                "previous_page", "next_page"), PublicGuiContract.InteractionMode.CORE))
                .menu(PREVIEW, guiRoot.resolve("kit-preview.yml"), new PublicGuiContract(
                        Set.of("kits.preview.back"), Map.of(), Set.of("kit"),
                        PublicGuiContract.InteractionMode.CORE))
                .entryPoint("fokits.command", MAIN)
                .entryPoint("fokits.admin.preview", PREVIEW)
                .navigation("kits.preview", PREVIEW)
                .navigation("kits.preview.back", MAIN)
                .requireEntry(MAIN, "kits", 0)
                .requireEntryLoreState(MAIN, "kits", "available")
                .requireEntryLoreState(MAIN, "kits", "cooldown")
                .requireEntryLoreState(MAIN, "kits", "no-permission")
                .requireEntryLoreState(MAIN, "kits", "world-blocked")
                .requireEntryLoreState(MAIN, "kits", "claimed-once")
                .requireEntryLoreState(MAIN, "kits", "disabled")
                .requireEntry(PREVIEW, "contents", 41)
                .requireButton(MAIN, "previous-page")
                .requireButton(MAIN, "next-page")
                .requireButton(PREVIEW, "back")
                .action("kits.page.previous", click -> changePage(click.player(), -1))
                .action("kits.page.next", click -> changePage(click.player(), 1))
                .action("kits.claim", click -> claim(click.player(), click.entryId()))
                .action("kits.preview", click -> previewEntry(click.player(), click.entryId()))
                .action("kits.preview.back", click -> back(click.player()));
        service.start();
        reload();
    }

    GuiLoadReport reload() {
        return service.reload();
    }

    GuiLoadReport validateInstalled() {
        return service.validateInstalled();
    }

    PublicGuiDefinition mainDefinition() {
        return service.snapshot() == null ? null : service.snapshot().require(MAIN);
    }

    Set<Integer> layoutProtectedSlots() {
        PublicGuiDefinition definition = mainDefinition();
        if (definition == null) {
            return Set.of();
        }
        Set<Integer> protectedSlots = new java.util.HashSet<>();
        definition.buttons().values().stream().filter(PublicGuiDefinition.Button::enabled)
                .forEach(button -> protectedSlots.add(button.slot()));
        definition.decorations().values().forEach(item -> protectedSlots.addAll(item.slots()));
        definition.entries().forEach((id, entry) -> {
            if (!id.equals("kits") && !id.equals("static-items")) {
                protectedSlots.addAll(entry.slots());
            }
        });
        return Set.copyOf(protectedSlots);
    }

    boolean updateMain(Consumer<YamlConfiguration> edit) {
        byte[] original = null;
        Path staged = null;
        try {
            original = Files.readAllBytes(mainFile);
            YamlConfiguration yaml = new YamlConfiguration();
            yaml.loadFromString(new String(original, StandardCharsets.UTF_8));
            edit.accept(yaml);
            staged = Files.createTempFile(mainFile.getParent(), ".kits-gui-editor-", ".staged");
            Files.writeString(staged, yaml.saveToString(), StandardCharsets.UTF_8);
            replace(staged, mainFile);
            staged = null;
            if (service.reload().successful()) {
                return true;
            }
        } catch (Exception failure) {
            // The last valid Core cache remains live; restore the owner's file below.
        } finally {
            if (staged != null) {
                try {
                    Files.deleteIfExists(staged);
                } catch (IOException ignored) {
                }
            }
        }
        if (original != null) {
            try {
                Path rollback = Files.createTempFile(mainFile.getParent(), ".kits-gui-rollback-", ".staged");
                Files.write(rollback, original);
                replace(rollback, mainFile);
            } catch (IOException failure) {
                throw new IllegalStateException("Could not restore player-kits.yml after invalid editor change", failure);
            }
        }
        return false;
    }

    private static void replace(Path staged, Path target) throws IOException {
        try {
            Files.move(staged, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
            Files.move(staged, target, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    void openMain(Player player, boolean playSound, int requestedPage) {
        if (service.snapshot() == null) {
            unavailable(player);
            return;
        }
        PublicGuiDefinition definition = service.snapshot().require(MAIN);
        List<KitDefinition> sorted = new ArrayList<>(kits.getAll());
        sorted.sort(Comparator.comparingInt(KitDefinition::getOrderIndex).thenComparing(KitDefinition::getKey));
        List<Integer> slots = definition.entries().get("kits").slots();
        int pages = PublicKitMenuLayout.pageCount(sorted.size(), slots.size());
        int page = PublicKitMenuLayout.clampPage(requestedPage, pages);
        Map<Integer, PublicGuiRenderInput.EntryValue> kitEntries = new LinkedHashMap<>();
        int index = PublicKitMenuLayout.pageStart(page, slots.size());
        for (int slot : slots) {
            if (index >= sorted.size()) {
                break;
            }
            KitDefinition kit = sorted.get(index++);
            KitViewState state = kitService.getViewState(player, kit);
            ItemStack source = baseIcon(state, kit);
            Map<String, String> values = new LinkedHashMap<>(
                    FoMessageService.materialPlaceholders(kit.getDisplayOrKey(), spriteMaterial(source.getType())));
            values.put("kit", kit.getDisplayOrKey());
            values.put("kit_key", kit.getKey());
            values.put("time_remaining", TimeUtil.formatDuration(
                    kitService.remainingMillis(player, kit), messages.config()));
            values.put("permission", kit.getRequiredPermission());
            values.put("world", player.getWorld().getName());
            kitEntries.put(slot, new PublicGuiRenderInput.EntryValue(
                    kit.getKey(), source, values, stateId(state)));
        }
        Map<String, Map<Integer, PublicGuiRenderInput.EntryValue>> entries = new LinkedHashMap<>();
        entries.put("kits", kitEntries);
        if (definition.entries().containsKey("static-items")) {
            List<Integer> positions = definition.entries().get("static-items").slots();
            entries.put("static-items", staticEntries(parent.staticItemsConfig(), positions));
        }
        Map<String, String> values = new HashMap<>(messages.tokenValues());
        // Owner templates can use any declared placeholder in a title, button,
        // filler or empty slot as well as an individual kit entry.
        for (String key : List.of("kit", "kit_key", "material", "material_sprite",
                "time_remaining", "permission")) {
            values.put(key, "");
        }
        values.put("world", player.getWorld().getName());
        values.put("page", String.valueOf(page + 1));
        values.put("pages", String.valueOf(pages));
        values.put("previous_page", String.valueOf(page));
        values.put("next_page", String.valueOf(page + 2));
        Map<String, String> buttonStates = new HashMap<>();
        if (definition.buttons().get("previous-page").enabled() && page == 0) {
            buttonStates.put("previous-page", "unavailable");
        }
        if (definition.buttons().get("next-page").enabled() && page + 1 >= pages) {
            buttonStates.put("next-page", "unavailable");
        }
        if (playSound) {
            sounds.open(player);
        }
        parent.forgetPublicSession(player);
        service.openEntryPoint(player, "fokits.command",
                new PublicGuiRenderInput(values, buttonStates, Map.of(), entries));
        contexts.put(player.getUniqueId(), new Context(page, "", false));
    }

    void openPreview(Player player, KitDefinition kit, boolean backToAdmin, int playerPage) {
        if (service.snapshot() == null) {
            unavailable(player);
            return;
        }
        PublicGuiDefinition definition = service.snapshot().require(PREVIEW);
        List<Integer> slots = definition.entries().get("contents").slots();
        Map<Integer, PublicGuiRenderInput.EntryValue> content = new LinkedHashMap<>();
        ItemStack[] armor = kit.getArmorItems();
        for (int index = 0; index < 4; index++) {
            put(content, slots, index, armor != null && index < armor.length ? armor[index] : null);
        }
        put(content, slots, 4, kit.getOffhandItem());
        ItemStack[] storage = kit.getInventoryItems();
        for (int index = 0; index < 36; index++) {
            put(content, slots, 5 + index,
                    storage != null && index < storage.length ? storage[index] : null);
        }
        if (!backToAdmin) {
            sounds.open(player);
        }
        parent.forgetPublicSession(player);
        Map<String, String> values = new HashMap<>(messages.tokenValues());
        values.put("kit", kit.getDisplayOrKey());
        service.open(player, PREVIEW, new PublicGuiRenderInput(
                values, Map.of(), Map.of(), Map.of("contents", content)));
        contexts.put(player.getUniqueId(), new Context(playerPage, kit.getKey(), backToAdmin));
    }

    private static void put(Map<Integer, PublicGuiRenderInput.EntryValue> entries, List<Integer> slots,
                            int position, ItemStack item) {
        int slot = contentSlot(slots, position);
        if (slot >= 0 && item != null && !item.getType().isAir()) {
            entries.put(slot, new PublicGuiRenderInput.EntryValue(
                    "content-" + position, item, Map.of()));
        }
    }

    static Map<Integer, PublicGuiRenderInput.EntryValue> staticEntries(
            ConfigurationSection configured, List<Integer> positions) {
        Map<Integer, PublicGuiRenderInput.EntryValue> entries = new LinkedHashMap<>();
        if (configured == null) {
            return entries;
        }
        for (int slot : positions) {
            ItemStack item = configured.getItemStack(Integer.toString(slot));
            if (item != null && !item.getType().isAir()) {
                entries.put(slot, new PublicGuiRenderInput.EntryValue(
                        "static-" + slot, item, Map.of()));
            }
        }
        return entries;
    }

    static int contentSlot(List<Integer> configuredSlots, int contentIndex) {
        return contentIndex >= 0 && contentIndex < configuredSlots.size()
                ? configuredSlots.get(contentIndex) : -1;
    }

    private void changePage(Player player, int offset) {
        Context context = contexts.get(player.getUniqueId());
        if (context == null) {
            return;
        }
        if (offset < 0) {
            sounds.previousPage(player);
        } else {
            sounds.nextPage(player);
        }
        openMain(player, false, context.page() + offset);
    }

    private void claim(Player player, String key) {
        Context context = contexts.get(player.getUniqueId());
        if (context == null || key == null) {
            return;
        }
        KitDefinition kit = kits.get(key).orElse(null);
        if (kit == null) {
            sounds.error(player);
        } else {
            kitService.sendClaimFeedback(player, kitService.claim(player, kit));
        }
        openMain(player, false, context.page());
    }

    private void previewEntry(Player player, String key) {
        Context context = contexts.get(player.getUniqueId());
        KitDefinition kit = key == null ? null : kits.get(key).orElse(null);
        if (context == null || kit == null) {
            sounds.error(player);
            return;
        }
        openPreview(player, kit, false, context.page());
    }

    private void back(Player player) {
        Context context = contexts.get(player.getUniqueId());
        if (context == null) {
            return;
        }
        sounds.back(player);
        if (context.backToAdmin()) {
            parent.openAdminKitSettings(player, context.kitKey());
        } else {
            openMain(player, false, context.page());
        }
    }

    private void unavailable(Player player) {
        Object configured = messages.config().get("gui-unavailable");
        if (configured instanceof String text && !text.isEmpty()) {
            messages.send(player, "gui-unavailable", "");
        }
    }

    private static String stateId(KitViewState state) {
        return switch (state) {
            case AVAILABLE -> "available";
            case COOLDOWN -> "cooldown";
            case NO_PERMISSION -> "no-permission";
            case WORLD_BLOCKED -> "world-blocked";
            case CLAIMED_ONCE -> "claimed-once";
            case DISABLED -> "disabled";
        };
    }

    static Material spriteMaterial(Material iconMaterial) {
        return iconMaterial == Material.CHEST ? Material.CHEST_MINECART : iconMaterial;
    }

    private static ItemStack baseIcon(KitViewState state, KitDefinition kit) {
        if ((state == KitViewState.CLAIMED_ONCE || state == KitViewState.COOLDOWN)
                && kit.getClaimedDisplayItem() != null && !kit.getClaimedDisplayItem().getType().isAir()) {
            return kit.getClaimedDisplayItem().clone();
        }
        if (kit.getIconItem() != null && !kit.getIconItem().getType().isAir()) {
            return kit.getIconItem().clone();
        }
        Material material = kit.getIconMaterial() == null ? Material.CHEST : kit.getIconMaterial();
        ItemStack item = new ItemStack(material);
        if (kit.getIconCustomModelData() != null) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null) {
                meta.setCustomModelData(kit.getIconCustomModelData());
                item.setItemMeta(meta);
            }
        }
        return item;
    }

    @Override
    public void close() {
        service.close();
        contexts.clear();
    }

    private record Context(int page, String kitKey, boolean backToAdmin) {
    }
}
