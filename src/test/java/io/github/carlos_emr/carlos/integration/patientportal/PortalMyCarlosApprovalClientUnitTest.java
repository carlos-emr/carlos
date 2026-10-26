/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class PortalMyCarlosApprovalClientUnitTest {
    @Test void shouldPreserveEmptyCheckpoint_whenNoNewRequests() {
        var page = PortalMyCarlosApprovalClient.requests(
                "{\"requests\":[],\"last_sequence\":\"19\",\"next_cursor\":null}", 19);
        assertTrue(page.records().isEmpty());
        assertEquals(19, page.lastSequence());
        assertNull(page.nextCursor());
    }
    @Test void shouldRefuseCursorSkips_whenEmptyPageAdvancesCheckpoint() {
        assertThrows(PortalContractException.class, () -> PortalMyCarlosApprovalClient.requests(
                "{\"requests\":[],\"last_sequence\":\"20\",\"next_cursor\":null}", 19));
    }
    @Test void shouldRefuseAmbiguousEnvelope_whenDuplicateFieldsArrive() {
        assertThrows(PortalContractException.class, () -> PortalMyCarlosApprovalClient.requests(
                "{\"requests\":[],\"last_sequence\":\"0\",\"last_sequence\":\"1\",\"next_cursor\":null}", 0));
    }
    @Test void shouldRefuseCoercions_whenCursorIsNotCanonicalText() {
        for (String cursor : new String[]{"0", "true", "\"00\"", "\"+0\"", "\"9223372036854775808\""}) {
            assertThrows(PortalContractException.class, () -> PortalMyCarlosApprovalClient.requests(
                    "{\"requests\":[],\"last_sequence\":" + cursor + ",\"next_cursor\":null}", 0));
        }
    }
    @Test void shouldRefuseUnknownFields_whenPortalChangesContract() {
        assertThrows(PortalContractException.class, () -> PortalMyCarlosApprovalClient.approvals(
                "{\"approvals\":[],\"last_sequence\":\"0\",\"next_cursor\":null,\"ignored\":true}", 0));
    }
    @Test void shouldRefuseSecondDocument_whenTrailingJsonArrives() {
        assertThrows(PortalContractException.class, () -> PortalMyCarlosApprovalClient.requests(
                "{\"requests\":[],\"last_sequence\":\"0\",\"next_cursor\":null} {}", 0));
    }
    @Test void shouldRefuseLoopingCursor_whenEmptyPageClaimsAnotherPage() {
        assertThrows(PortalContractException.class, () -> PortalMyCarlosApprovalClient.approvals(
                "{\"approvals\":[],\"last_sequence\":\"0\",\"next_cursor\":\"0\"}", 0));
    }
    @Test void shouldBindCanonicalQuery_whenReadingRequestPage() {
        AtomicReference<String> target = new AtomicReference<>();
        var client = new PortalMyCarlosApprovalClient((permission, method, path, body, status) -> {
            assertEquals(PortalJob.PERMISSION_APPROVAL_REQUESTS_READ, permission);
            assertEquals("GET", method);
            assertEquals(0, body.length);
            assertEquals(200, status);
            target.set(path);
            return "{\"requests\":[],\"last_sequence\":\"7\",\"next_cursor\":null}";
        });
        client.requests(7);
        assertEquals("/internal/carlos/mycarlos/device-approval-requests?after=7&limit=20", target.get());
    }
    @Test void shouldSeparateJobs_whenRegisteringApprovalAndDeliveryPermissions() {
        assertFalse(PortalJob.DELIVERY_SYNC.permissions().contains(PortalJob.PERMISSION_APPROVAL_DECISIONS_PUBLISH));
        assertFalse(PortalJob.DEVICE_APPROVAL_SYNC.permissions().contains(PortalJob.PERMISSION_DELIVERY_UPLOAD));
        assertEquals(5, PortalJob.DEVICE_APPROVAL_SYNC.permissions().size());
        assertEquals(3, PortalJob.DELIVERY_SYNC.permissions().size());
    }
    @Test void shouldParseRealIntent_whenSharedFixtureArrivesOnNonemptyPage() throws Exception {
        var json = new com.fasterxml.jackson.databind.ObjectMapper();
        try (var input = getClass().getResourceAsStream("/patientportal/device-approval-contract-v1.json")) {
            assertNotNull(input);
            String intent = json.readTree(input).path("flows").path("first").path("intent").path("compact").textValue();
            var pageJson = json.createObjectNode();
            pageJson.putArray("requests").addObject().put("sequence", "8").put("demographic_no", 6001).put("intent", intent);
            pageJson.put("last_sequence", "8").putNull("next_cursor");
            var page = PortalMyCarlosApprovalClient.requests(pageJson.toString(), 7);
            assertEquals(1, page.records().size());
            assertEquals(intent, page.records().getFirst().intent());
            assertEquals(6001, page.records().getFirst().demographicNo());
            assertEquals(8, page.lastSequence());
        }
    }
    @Test void shouldPublishCommittedBytes_whenDecisionResponseConfirmsStored() {
        byte[] saved = "{\"status\":\"accepted\"}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        var request = java.util.UUID.fromString("12a58cad-c07c-40af-848a-106dc90732e2");
        var client = new PortalMyCarlosApprovalClient((permission, method, path, body, status) -> {
            assertEquals(PortalJob.PERMISSION_APPROVAL_DECISIONS_PUBLISH, permission);
            assertEquals("PUT", method);
            assertEquals("/internal/carlos/mycarlos/device-approval-requests/" + request + "/decision", path);
            assertArrayEquals(saved, body);
            assertNotSame(saved, body);
            return "{\"status\":\"stored\"}";
        });
        client.publish(request, "decision", saved);
        var dishonest = new PortalMyCarlosApprovalClient((p, m, u, b, s) -> "{\"status\":\"accepted\"}");
        assertThrows(PortalContractException.class, () -> dishonest.publish(request, "decision", saved));
    }

}
