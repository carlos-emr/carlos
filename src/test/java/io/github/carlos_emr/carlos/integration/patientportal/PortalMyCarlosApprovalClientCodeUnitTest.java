/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The portal's device code routes as the approval job reads them (clinic code v1, mycarlos#141). */
@Tag("unit")
class PortalMyCarlosApprovalClientCodeUnitTest {
    private static final String REQUEST = "0f8fad5b-d9cb-469f-a165-70867728950e";
    private static final String CHALLENGE = "A".repeat(43);

    private static String page(String rows, String last, String next) {
        return "{\"codes\":[" + rows + "],\"last_sequence\":\"" + last + "\",\"next_cursor\":" + next + "}";
    }

    private static String row(String sequence, int attempt, String codeR) {
        return "{\"sequence\":\"" + sequence + "\",\"request_id\":\"" + REQUEST + "\",\"challenge_id\":\"" + CHALLENGE
                + "\",\"attempt\":" + attempt + ",\"code_r\":" + codeR + ",\"submitted_at\":1761660000}";
    }

    @Test
    void shouldReadSentCodes_inSequence() {
        var page = PortalMyCarlosApprovalClient.codes(page(row("7", 1, "\"482193\"") + "," + row("8", 2, "null"), "8", "null"), 6);
        assertEquals(2, page.records().size());
        var first = page.records().getFirst();
        assertEquals(7, first.sequence());
        assertEquals(UUID.fromString(REQUEST), first.requestId());
        assertEquals(CHALLENGE, first.challengeId());
        assertEquals(1, first.attempt());
        assertEquals("482193", first.codeR());
        assertEquals(1761660000L, first.submittedAt());
        // Used or decided: no code any more.
        assertNull(page.records().get(1).codeR());
        assertEquals("ApprovalCode[REDACTED]", first.toString());
        assertEquals(8, page.lastSequence());
        assertNull(page.nextCursor());
    }

    @Test
    void shouldRefuseMalformedCodeRows_fromPortal() {
        for (String bad : new String[] {
                row("7", 1, "\"48219\""), row("7", 1, "482193"), row("7", 1, "\"48219a\""), row("7", 0, "\"482193\""),
                row("7", 6, "\"482193\""), row("07", 1, "\"482193\""), row("7", 1, "\"482193\"").replace("}", ",\"x\":1}"),
                row("7", 1, "\"482193\"").replace("\"submitted_at\":1761660000", "\"submitted_at\":\"1761660000\""),
                row("7", 1, "\"482193\"").replace(CHALLENGE, "short")}) {
            assertThrows(PortalContractException.class, () -> PortalMyCarlosApprovalClient.codes(page(bad, "7", "null"), 6), bad);
        }
        // Out of order, or a cursor that skips.
        assertThrows(PortalContractException.class,
                () -> PortalMyCarlosApprovalClient.codes(page(row("6", 1, "null"), "6", "null"), 6));
        assertThrows(PortalContractException.class, () -> PortalMyCarlosApprovalClient.codes(page("", "9", "null"), 6));
    }

    @Test
    void shouldCallExactRoutes_forReadingAndConsumingCodes() {
        AtomicReference<String> seen = new AtomicReference<>();
        var client = new PortalMyCarlosApprovalClient((permission, method, path, body, status) -> {
            assertEquals(0, body.length);
            assertEquals(200, status);
            seen.set(permission + " " + method + " " + path);
            return path.contains("?") ? page("", "3", "null") : "{\"status\":\"consumed\"}";
        });
        client.codes(3);
        assertEquals(PortalJob.PERMISSION_APPROVAL_CODES_READ
                + " GET /internal/carlos/mycarlos/device-approval-codes?after=3&limit=20", seen.get());
        client.consumed(UUID.fromString(REQUEST), 5);
        assertEquals(PortalJob.PERMISSION_APPROVAL_CODES_CONSUME + " PUT /internal/carlos/mycarlos/device-approval-requests/"
                + REQUEST + "/codes/5/consumed", seen.get());
        assertThrows(PortalContractException.class, () -> client.consumed(UUID.fromString(REQUEST), 6));
    }

    @Test
    void shouldRefuseOtherAnswer_whenMarkingCodeUsed() {
        var client = new PortalMyCarlosApprovalClient((permission, method, path, body, status) -> "{\"status\":\"stored\"}");
        assertThrows(PortalContractException.class, () -> client.consumed(UUID.fromString(REQUEST), 1));
    }
}
