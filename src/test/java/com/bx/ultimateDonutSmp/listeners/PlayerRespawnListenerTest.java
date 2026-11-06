package com.bx.ultimateDonutSmp.listeners;

import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PlayerRespawnListenerTest {

    @Test
    void testFoliaRespawnCanOnlyBeClaimedOnce() {
        Map<UUID, UUID> pending = new ConcurrentHashMap<>();
        UUID playerId = UUID.randomUUID();
        pending.put(playerId, UUID.randomUUID());

        assertTrue(PlayerRespawnListener.claimPendingRespawn(pending, playerId));
        assertFalse(PlayerRespawnListener.claimPendingRespawn(pending, playerId));
    }

    @Test
    void testStalePollCannotClaimNewerDeath() {
        Map<UUID, UUID> pending = new ConcurrentHashMap<>();
        UUID playerId = UUID.randomUUID();
        UUID oldToken = UUID.randomUUID();
        UUID newToken = UUID.randomUUID();
        pending.put(playerId, newToken);

        assertFalse(PlayerRespawnListener.claimPendingRespawn(pending, playerId, oldToken));
        assertTrue(pending.containsKey(playerId));
        assertTrue(PlayerRespawnListener.claimPendingRespawn(pending, playerId, newToken));
    }

    @Test
    void testAnchorSpawnAlwaysPreservesVanillaLocation() {
        assertTrue(PlayerRespawnListener.shouldPreserveVanillaRespawn(true, false, false, false));
    }

    @Test
    void testBedRespawnStillRespectsConfiguration() {
        assertTrue(PlayerRespawnListener.shouldPreserveVanillaRespawn(false, true, true, true));
        assertFalse(PlayerRespawnListener.shouldPreserveVanillaRespawn(false, true, false, true));
    }

    @Test
    void testUnflaggedConfiguredRespawnIsPreservedEvenWhenBedRespawnIsDisabled() {
        assertTrue(PlayerRespawnListener.shouldPreserveVanillaRespawn(false, false, false, true));
    }

    @Test
    void testUnmatchedSpawnUsesConfiguredServerSpawn() {
        assertFalse(PlayerRespawnListener.shouldPreserveVanillaRespawn(false, false, true, false));
    }

    @Test
    void testPendingPearlDoesNotOverrideAnchorRespawn() {
        assertFalse(PlayerRespawnListener.shouldUsePendingPearlTeleport(true, true));
    }

    @Test
    void testPendingPearlStillAppliesWhenVanillaDidNotChooseAnchor() {
        assertTrue(PlayerRespawnListener.shouldUsePendingPearlTeleport(false, true));
        assertFalse(PlayerRespawnListener.shouldUsePendingPearlTeleport(false, false));
    }

    @Test
    void testScheduleChainmailKitWithNullsDoesNotThrow() {
        assertDoesNotThrow(() -> PlayerRespawnListener.scheduleChainmailKit(null, null, 0L));
    }
}
