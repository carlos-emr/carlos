/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.junit.jupiter.api.Assertions.*;

import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Bytes;
import java.time.Instant;
import java.util.Arrays;
import java.util.UUID;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** One emailed code's stored state (clinic code v1, mycarlos#141): attempts, match and refusal. */
@Tag("unit")
class PortalMyCarlosDeviceApprovalCodeUnitTest {
    private static final Instant ISSUED = Instant.ofEpochSecond(1792454400L);

    private static Bytes filled(int size, int value) {
        byte[] bytes = new byte[size];
        Arrays.fill(bytes, (byte) value);
        return new Bytes(bytes);
    }

    private static PortalMyCarlosDeviceApprovalCode fresh() {
        return new PortalMyCarlosDeviceApprovalCode(filled(32, 1), "FAKE-clinic",
                UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e"), "code", filled(16, 2), filled(32, 3), 0,
                ISSUED, ISSUED.plusSeconds(600), null, null, null, 0);
    }

    @Test
    void shouldRefuseOnFifthWrongCode_andStayRefused() {
        var row = fresh().emailed(ISSUED.plusSeconds(1));
        for (int i = 1; i <= 4; i++) {
            row = row.wrong(ISSUED.plusSeconds(10 + i));
            assertEquals(i, row.wrongAttempts());
            assertTrue(row.open());
            assertNull(row.refusedAt());
        }
        row = row.wrong(ISSUED.plusSeconds(20));
        assertEquals(5, row.wrongAttempts());
        assertFalse(row.open());
        assertEquals(ISSUED.plusSeconds(20), row.refusedAt());
        var refused = row;
        assertThrows(IllegalArgumentException.class, () -> refused.wrong(ISSUED.plusSeconds(21)));
        assertThrows(IllegalArgumentException.class, () -> refused.matched(ISSUED.plusSeconds(21)));
    }

    @Test
    void shouldMatchOnce_afterWrongCodes() {
        var row = fresh().emailed(ISSUED.plusSeconds(1)).wrong(ISSUED.plusSeconds(2)).matched(ISSUED.plusSeconds(3));
        assertEquals(1, row.wrongAttempts());
        assertFalse(row.open());
        assertEquals(ISSUED.plusSeconds(3), row.matchedAt());
        assertEquals(3, row.version());
        assertThrows(IllegalArgumentException.class, () -> row.wrong(ISSUED.plusSeconds(4)));
    }

    @Test
    void shouldReissueOnlyCodeNeverEmailed_forRetry() {
        var row = fresh().reissued(filled(16, 4), filled(32, 5));
        assertEquals(filled(32, 5), row.mac());
        assertEquals(1, row.version());
        var sent = row.emailed(ISSUED.plusSeconds(1));
        assertThrows(IllegalArgumentException.class, () -> sent.reissued(filled(16, 6), filled(32, 7)));
        assertThrows(IllegalArgumentException.class, () -> sent.emailed(ISSUED.plusSeconds(2)));
    }

    @Test
    void shouldRefuseInvalidState_forStoredCode() {
        assertThrows(IllegalArgumentException.class, () -> new PortalMyCarlosDeviceApprovalCode(filled(32, 1),
                "FAKE-clinic", UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e"), "staff_only",
                filled(16, 2), filled(32, 3), 0, ISSUED, ISSUED.plusSeconds(600), null, null, null, 0));
        assertThrows(IllegalArgumentException.class, () -> new PortalMyCarlosDeviceApprovalCode(filled(32, 1),
                "FAKE-clinic", UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e"), "code",
                filled(16, 2), filled(32, 3), 0, ISSUED, ISSUED.plusSeconds(601), null, null, null, 0));
        assertThrows(IllegalArgumentException.class, () -> new PortalMyCarlosDeviceApprovalCode(filled(32, 1),
                "FAKE-clinic", UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e"), "code",
                filled(16, 2), filled(32, 3), 4, ISSUED, ISSUED.plusSeconds(600), null, null, ISSUED, 0));
        assertThrows(IllegalArgumentException.class, () -> new PortalMyCarlosDeviceApprovalCode(filled(32, 1),
                "FAKE-clinic", UUID.fromString("0f8fad5b-d9cb-469f-a165-70867728950e"), "code",
                filled(16, 2), filled(32, 3), 6, ISSUED, ISSUED.plusSeconds(600), null, null, null, 0));
        assertEquals("DeviceApprovalCode[REDACTED]", fresh().toString());
    }
}
