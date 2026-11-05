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
    void testScheduleChainmailKitWithNullsDoesNotThrow() {
        assertDoesNotThrow(() -> PlayerRespawnListener.scheduleChainmailKit(null, null, 0L));
    }
}
