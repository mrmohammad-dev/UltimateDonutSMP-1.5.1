package com.bx.ultimateDonutSmp.staff;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StaffModeSessionTest {

    @Test
    void toolInitializationDistinguishesFirstEntryFromSessionRebuilds() {
        StaffModeSession session = new StaffModeSession(UUID.randomUUID());
        assertFalse(session.areToolsInitialized());

        session.setToolsInitialized(true);
        assertTrue(session.areToolsInitialized());
    }
}
