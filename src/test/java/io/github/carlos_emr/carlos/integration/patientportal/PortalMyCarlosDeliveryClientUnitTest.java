/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

@Tag("unit")
class PortalMyCarlosDeliveryClientUnitTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @TestFactory
    List<DynamicTest> shouldHonorFrozenEventSchema_whenReadingAllSharedVectors() throws Exception {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode vector : fixture().get("events")) {
            byte[] exact = HexFormat.of().parseHex(vector.at("/json/hex").textValue());
            tests.add(DynamicTest.dynamicTest(vector.get("name").textValue(), () -> {
                assertEquals(vector.at("/json/sha256").textValue(), PortalMyCarlosDeviceApprovalCrypto.sha256(exact));
                if ("accept".equals(vector.get("expect").textValue())) {
                    var result = PortalMyCarlosDeliveryClient.event(exact);
                    assertEquals(vector.get("name").textValue(), result.kind());
                    String page = "{\"events\":[" + new String(exact, StandardCharsets.UTF_8)
                            + "],\"last_sequence\":\"" + result.sequence() + "\",\"next_cursor\":null}";
                    var parsed = PortalMyCarlosDeliveryClient.page(page.getBytes(StandardCharsets.UTF_8), result.sequence() - 1);
                    assertEquals(result.sequence(), parsed.lastSequence());
                    assertArrayEquals(page.getBytes(StandardCharsets.UTF_8), parsed.exactBytes().copy());
                    assertEquals(result, parsed.events().getFirst());
                } else {
                    assertThrows(IllegalArgumentException.class, () -> PortalMyCarlosDeliveryClient.event(exact));
                }
            }));
        }
        assertEquals(17, tests.size());
        return tests;
    }

    @TestFactory
    List<DynamicTest> shouldHonorCanonicalDecimal_whenReadingSharedCursors() throws Exception {
        List<DynamicTest> tests = new ArrayList<>();
        for (JsonNode vector : fixture().get("cursors")) {
            String value = vector.get("text").textValue();
            tests.add(DynamicTest.dynamicTest("cursor " + value, () -> {
                if ("accept".equals(vector.get("expect").textValue())) {
                    assertEquals(Long.parseLong(value), PortalMyCarlosDeliveryClient.cursor(value));
                } else assertThrows(IllegalArgumentException.class, () -> PortalMyCarlosDeliveryClient.cursor(value));
            }));
        }
        return tests;
    }

    @Test
    void shouldRejectMismatchedCheckpointOrMalformedEnvelope_whenParsingPage() throws Exception {
        var vector = fixture().at("/events/0");
        String event = new String(HexFormat.of().parseHex(vector.at("/json/hex").textValue()), StandardCharsets.UTF_8);
        for (String page : List.of(
                "{\"events\":[" + event + "],\"last_sequence\":\"2\",\"next_cursor\":null}",
                "{\"events\":[" + event + "],\"last_sequence\":\"1\",\"next_cursor\":\"2\"}",
                "{\"events\":[],\"last_sequence\":\"0\",\"next_cursor\":\"0\"}",
                "{\"events\":[],\"last_sequence\":\"0\",\"next_cursor\":null,\"extra\":0}",
                "{\"events\":[],\"last_sequence\":\"0\",\"last_sequence\":\"0\",\"next_cursor\":null}")) {
            assertThrows(IllegalArgumentException.class, () -> PortalMyCarlosDeliveryClient.page(
                    page.getBytes(StandardCharsets.UTF_8), 0));
        }
        var empty = PortalMyCarlosDeliveryClient.page(
                "{\"events\":[],\"last_sequence\":\"7\",\"next_cursor\":null}".getBytes(StandardCharsets.UTF_8), 7);
        assertEquals(7, empty.lastSequence());
        assertNull(empty.nextCursor());
    }

    @Test
    void shouldCountOriginalEventWhitespace_whenCheckingExactEventCap() throws Exception {
        String event = new String(HexFormat.of().parseHex(fixture().at("/events/0/json/hex").textValue()), StandardCharsets.UTF_8);
        String padded = "{" + " ".repeat(16384 - event.length()) + event.substring(1);
        String page = "{\"events\":[" + padded + "],\"last_sequence\":\"1\",\"next_cursor\":null}";
        assertEquals(16384, padded.length());
        assertEquals(1, PortalMyCarlosDeliveryClient.page(page.getBytes(StandardCharsets.UTF_8), 0).events().size());
        String tooLarge = page.replace("{ ", "{  ");
        assertThrows(IllegalArgumentException.class,
                () -> PortalMyCarlosDeliveryClient.page(tooLarge.getBytes(StandardCharsets.UTF_8), 0));
    }

    @Test
    void shouldRejectWrongUploadIdentityAndUnknownStates_whenReadingRealFacadeReply() throws Exception {
        String id = "6f1c2a7e-0b9d-4e53-8a41-2d7c9b0e5f13";
        String kid = "Ebz2sH2jVoqcdFhl1BmIOBRm1xuT-zW5bvFBcCeERPY";
        String valid = "{\"delivery_id\":\"" + id + "\",\"device_key_id\":\"" + kid + "\",\"status\":\"waiting\"}";
        for (String malformed : List.of(valid.replace(id, "00000000-0000-4000-8000-000000000000"),
                valid.replace(kid, "A".repeat(43)), valid.replace("waiting", "sent"), valid.replace("waiting", "picked_up"))) {
            var transport = new Exchange(201, malformed);
            assertThrows(IllegalArgumentException.class, () -> client(transport).upload(123, id, kid, new byte[] {0, -1}));
        }
        assertEquals("waiting", client(new Exchange(201, valid)).upload(123, id, kid, new byte[] {0, -1}));
        // This string is transport information only; the client has no staff pickup-state mutator.
        assertEquals("picked_up", client(new Exchange(200, valid.replace("waiting", "picked_up")))
                .upload(123, id, kid, new byte[] {0, -1}));
    }

    @Test
    void shouldBindPagingAndCommitAckBody_whenUsingProductionTransport() throws Exception {
        Exchange events = new Exchange(200, "{\"events\":[],\"last_sequence\":\"7\",\"next_cursor\":null}");
        assertEquals(7, client(events).events(7).lastSequence());
        assertEquals("/internal/carlos/mycarlos/delivery-events?after=7&limit=25", events.sent.getFirst().getRequestUri());
        Exchange ack = new Exchange(200, "{\"acked_through\":\"7\"}");
        assertEquals(7, client(ack).acknowledge(7));
        assertEquals("/internal/carlos/mycarlos/delivery-events/ack", ack.sent.getFirst().getRequestUri());
        assertArrayEquals("{\"through\":\"7\"}".getBytes(StandardCharsets.UTF_8),
                ack.sent.getFirst().getEntity().getContent().readAllBytes());
    }

    @Test
    void shouldRefuseAckMismatchAndWrongTypes_whenServerClaimsAnotherCheckpoint() {
        for (String response : List.of("{\"acked_through\":\"07\"}", "{\"acked_through\":7}",
                "{\"acked_through\":\"8\"}", "{\"acked_through\":\"6\"}",
                "{\"acked_through\":\"7\",\"extra\":0}")) {
            assertThrows(IllegalArgumentException.class, () -> client(new Exchange(200, response)).acknowledge(7));
        }
    }

    private static final class Exchange implements PatientPortalHttpExchange {
        final List<ClassicHttpRequest> sent = new ArrayList<>();
        final PatientPortalHttpResponse response;
        Exchange(int status, String body) { response = new PatientPortalHttpResponse(status, body); }
        public PatientPortalHttpResponse send(ClassicHttpRequest request) { sent.add(request);
            return response; }
    }
    private static PortalMyCarlosDeliveryClient client(Exchange exchange) {
        var settings = PatientPortalSettings.fromProperties(Map.of(
                PatientPortalSettings.BASE_URL_KEY, "https://portal.example.test",
                PatientPortalSettings.CLINIC_ID_KEY, "clinic-a",
                PatientPortalSettings.SERVICE_TOKEN_KEY, "FAKE-service-token-only-00000001",
                PatientPortalSettings.STAFF_ASSERTION_KEY, PortalTestKeys.PRIVATE_KEY,
                PatientPortalSettings.STAFF_ASSERTION_KEY_ID, "FAKE-staff-key",
                PatientPortalSettings.CERTIFICATE_PINS_KEY, PortalTestKeys.UNUSED_TLS_PIN));
        var service = new PatientPortalService(settings, exchange,
                PortalStaffAssertionSigner.from(settings.staffAssertionPrivateKey()), () -> true);
        var signer = PortalJobAssertionSigner.from(new PortalJobKey(PortalJob.DELIVERY_SYNC,
                PortalSecret.of(PortalTestKeys.JOB_PRIVATE_KEY), "FAKE-delivery-job"));
        return new PortalMyCarlosDeliveryClient(service, signer);
    }

    private static JsonNode fixture() throws Exception {
        try (var stream = PortalMyCarlosDeliveryClientUnitTest.class.getResourceAsStream("/patientportal/mailbox-contract-v1.json")) {
            return JSON.readTree(stream);
        }
    }
}
