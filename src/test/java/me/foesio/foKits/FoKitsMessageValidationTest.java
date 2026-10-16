package me.foesio.foKits;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FoKitsMessageValidationTest {
    @TempDir Path temporary;

    @Test
    void releasedTwoPointZeroChestDefaultChangesWithoutOverwritingOwnerText() throws Exception {
        YamlConfiguration oldDefaults = bundled("migrations/messages-v9.yml");
        YamlConfiguration newest = bundled("messages.yml");
        YamlConfiguration owner = bundled("migrations/messages-v9.yml");

        Map<String, Object> changes = FoKits.planMessageUpgrade(owner, newest, oldDefaults);
        assertEquals(newest.getString("claim-no-space"), changes.get("claim-no-space"));
        assertEquals(newest.getString("duration.zero"), changes.get("duration.zero"));

        owner.set("claim-no-space", "{prefix}§cInventar voll");
        assertFalse(FoKits.planMessageUpgrade(owner, newest, oldDefaults).containsKey("claim-no-space"));
        owner.set("claim-no-space", null);
        assertFalse(FoKits.planMessageUpgrade(owner, newest, oldDefaults).containsKey("claim-no-space"));
    }

    private static YamlConfiguration bundled(String resource) throws Exception {
        YamlConfiguration yaml = new YamlConfiguration();
        try (InputStream stream = FoKitsMessageValidationTest.class.getClassLoader()
                .getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException("Missing test resource " + resource);
            }
            yaml.load(new InputStreamReader(stream, StandardCharsets.UTF_8));
        }
        return yaml;
    }

    @Test
    void currentOptionalRemovalsAndUtf8TextStayValid() {
        YamlConfiguration bundled = new YamlConfiguration();
        bundled.set("claim-success", "Claimed");
        bundled.set("tokens.theme", "#ffffff");
        YamlConfiguration owner = new YamlConfiguration();
        owner.set("claim-success", List.of("§aKlaar ✓"));

        assertDoesNotThrow(() -> FoKits.validateMessageTypes(owner, bundled));
    }

    @Test
    void invalidReloadFieldIsRejectedWithoutRepairingOwnerValue() {
        YamlConfiguration bundled = new YamlConfiguration();
        bundled.set("duration.second.one", "{count}s");
        YamlConfiguration owner = new YamlConfiguration();
        owner.set("duration.second.one", 42);

        assertThrows(IllegalStateException.class, () -> FoKits.validateMessageTypes(owner, bundled));
        org.junit.jupiter.api.Assertions.assertEquals(42, owner.getInt("duration.second.one"));
    }

    @Test
    void upgradeRepairsInvalidFieldAndAddsOnlyNewFieldWhileKeepingOwnerRemoval() {
        YamlConfiguration bundled = new YamlConfiguration();
        bundled.set("claim-success", "Claimed");
        bundled.set("claim-cooldown", "Wait");
        bundled.set("tokens.theme", "#ffffff");
        bundled.set("duration.second.one", "{count}s");
        YamlConfiguration owner = new YamlConfiguration();
        owner.set("claim-success", "§aKlaar ✓");
        owner.set("tokens.theme", List.of("invalid scalar"));

        Map<String, Object> changes = FoKits.planMessageUpgrade(owner, bundled);

        assertEquals(Map.of("tokens.theme", "#ffffff", "duration.second.one", "{count}s"), changes);
        assertFalse(changes.containsKey("claim-cooldown"));
        assertEquals("§aKlaar ✓", owner.getString("claim-success"));
    }

    @Test
    void messageUpgradeBackupIsByteExactAndNeverOverwritten() throws Exception {
        Path installed = temporary.resolve("messages.yml");
        byte[] original = "# eigenaar\nclaim-success: '§aKlaar ✓'\n".getBytes(StandardCharsets.UTF_8);
        Files.write(installed, original);

        FoKits.backupMessageForUpgrade(installed, 7, Files.readAllBytes(installed));
        Path backup = temporary.resolve("messages.yml.gui-from-config-v7-backup");
        assertArrayEquals(original, Files.readAllBytes(backup));

        Files.writeString(installed, "claim-success: Changed\n");
        FoKits.backupMessageForUpgrade(installed, 7, Files.readAllBytes(installed));
        assertArrayEquals(original, Files.readAllBytes(backup));
    }

    @Test
    void failedMessageUpgradeRestoresPreAttemptBytes() throws Exception {
        Path installed = temporary.resolve("messages.yml");
        byte[] original = "# eigenaar\nclaim-success: '§aKlaar ✓'\n".getBytes(StandardCharsets.UTF_8);
        Files.writeString(installed, "claim-success: temporary default\n");

        FoKits.replaceBytes(installed, original);

        assertArrayEquals(original, Files.readAllBytes(installed));
    }

    @Test
    void invalidOlderDurationSectionCanUseNewestUnitDefaults() {
        YamlConfiguration bundled = new YamlConfiguration();
        bundled.set("duration.zero", "0s");
        bundled.set("duration.second.one", "{count}s");
        YamlConfiguration owner = new YamlConfiguration();
        owner.set("duration", 42);
        owner.set("claim-success", "§aKlaar");

        FoKits.planMessageUpgrade(owner, bundled).forEach(owner::set);

        assertEquals("0s", owner.getString("duration.zero"));
        assertEquals("{count}s", owner.getString("duration.second.one"));
        assertEquals("§aKlaar", owner.getString("claim-success"));
    }
}
