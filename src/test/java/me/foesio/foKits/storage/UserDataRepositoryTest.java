package me.foesio.foKits.storage;

import me.foesio.foKits.model.KitDefinition;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UserDataRepositoryTest {
    @TempDir Path dataFolder;

    @Test
    void cooldownAndOneTimeResetsPersistIndependently() {
        UUID player = UUID.randomUUID();
        KitDefinition kit = new KitDefinition("starter");
        UserDataRepository first = repository();
        first.setClaim(player, kit, 12345L);

        assertEquals(UserDataRepository.ResetStatus.RESET, first.resetCooldownChecked(player, "starter"));
        UserDataRepository afterCooldown = repository();
        assertEquals(0L, afterCooldown.getLastClaim(player, "starter"));
        assertTrue(afterCooldown.hasClaimedOnce(player, "starter"));

        assertEquals(UserDataRepository.ResetStatus.RESET,
                afterCooldown.resetOneTimeClaimChecked(player, "starter"));
        UserDataRepository afterOneTime = repository();
        assertFalse(afterOneTime.hasClaimedOnce(player, "starter"));
        assertEquals(UserDataRepository.ResetStatus.UNCHANGED,
                afterOneTime.resetOneTimeClaimChecked(player, "starter"));
    }

    @Test
    void failedResetKeepsCachedClaimState() throws Exception {
        UUID player = UUID.randomUUID();
        UserDataRepository repository = repository();
        repository.setClaim(player, new KitDefinition("starter"), 12345L);

        Path file = dataFolder.resolve("userdata").resolve(player + ".yml");
        Files.delete(file);
        Files.createDirectory(file);
        Files.writeString(file.resolve("blocker"), "block replacement");

        assertEquals(UserDataRepository.ResetStatus.SAVE_FAILED,
                repository.resetCooldownChecked(player, "starter"));
        assertEquals(12345L, repository.getLastClaim(player, "starter"));
        assertEquals(UserDataRepository.ResetStatus.SAVE_FAILED,
                repository.resetOneTimeClaimChecked(player, "starter"));
        assertTrue(repository.hasClaimedOnce(player, "starter"));
    }

    @Test
    void batchResetClearsEveryCooldownButKeepsOneTimeFlags() {
        UUID player = UUID.randomUUID();
        UserDataRepository repository = repository();
        repository.setClaim(player, new KitDefinition("starter"), 12345L);
        repository.setClaim(player, new KitDefinition("daily"), 67890L);

        UserDataRepository.ResetBatch batch = repository.resetCooldownsChecked(player);
        assertEquals(UserDataRepository.ResetStatus.RESET, batch.status());
        assertEquals(2, batch.resetCount());

        UserDataRepository afterRestart = repository();
        assertEquals(0L, afterRestart.getLastClaim(player, "starter"));
        assertEquals(0L, afterRestart.getLastClaim(player, "daily"));
        assertTrue(afterRestart.hasClaimedOnce(player, "starter"));
        assertTrue(afterRestart.hasClaimedOnce(player, "daily"));
        assertEquals(UserDataRepository.ResetStatus.UNCHANGED,
                afterRestart.resetCooldownsChecked(player).status());
    }

    private UserDataRepository repository() {
        Logger logger = Logger.getAnonymousLogger();
        logger.setUseParentHandlers(false);
        UserDataRepository repository = new UserDataRepository(dataFolder.toFile(), logger);
        repository.ensureFolder();
        return repository;
    }
}
