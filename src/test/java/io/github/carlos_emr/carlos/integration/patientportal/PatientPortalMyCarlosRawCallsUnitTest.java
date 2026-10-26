/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("patient-portal")
class PatientPortalMyCarlosRawCallsUnitTest {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final String UPLOAD = "/internal/carlos/patients/123/mycarlos/deliveries";
    private static final String EVENTS = "/internal/carlos/mycarlos/delivery-events?after=0&limit=25";

    @Test
    void shouldSignAndSendIdenticalBinaryBytes_whenUploadingPersistedFrame() throws Exception {
        Exchange exchange = new Exchange(201, "{\"status\":\"waiting\"}");
        PatientPortalService service = service(exchange, true);
        byte[] saved = new byte[] {0, (byte) 0xff, (byte) 0x80, 13, 10, 0, 92, 34};
        service.exchangeMyCarlosJob(signer(PortalJob.DELIVERY_SYNC), PortalJob.PERMISSION_DELIVERY_UPLOAD,
                "POST", UPLOAD, saved, Set.of(200, 201));
        ClassicHttpRequest sent = exchange.requests.getFirst();
        byte[] entity = sent.getEntity().getContent().readAllBytes();
        assertArrayEquals(saved, entity);
        saved[0] = 42;
        assertEquals(0, sent.getEntity().getContent().readAllBytes()[0]);
        assertEquals("application/octet-stream", sent.getEntity().getContentType());
        assertNull(sent.getFirstHeader(PortalStaffAssertionSigner.HEADER));
        String compact = sent.getFirstHeader(PortalJobAssertionSigner.HEADER).getValue();
        assertTrue(PortalTestKeys.verifies(compact, PortalTestKeys.JOB_PUBLIC_KEY));
        JsonNode claims = JSON.readTree(Base64.getUrlDecoder().decode(compact.split("\\.")[0]));
        assertEquals("delivery-sync", claims.get("job").textValue());
        assertEquals("portal.mycarlos.delivery.upload", claims.get("permission").textValue());
        assertEquals(PortalStaffAssertionSigner.requestHash("POST", sent.getUri(), entity),
                claims.get("request_hash").textValue());
        assertEquals(service.myCarlosDeliveryTarget(123), sent.getUri());
    }

    @Test
    void shouldBindExactQueryAndUseJobOnly_whenApprovalAdapterCallsFacade() throws Exception {
        Exchange exchange = new Exchange(200, "{\"requests\":[],\"last_sequence\":\"0\",\"next_cursor\":null}");
        var service = service(exchange, true);
        var job = signer(PortalJob.DEVICE_APPROVAL_SYNC);
        var client = new PortalMyCarlosApprovalClient((permission, method, path, body, status) ->
                service.exchangeJob(job, permission, method, path, body, status));
        assertTrue(client.requests(0).records().isEmpty());
        var sent = exchange.requests.getFirst();
        assertNull(sent.getEntity());
        assertEquals("/internal/carlos/mycarlos/device-approval-requests?after=0&limit=20", sent.getRequestUri());
        String compact = sent.getFirstHeader(PortalJobAssertionSigner.HEADER).getValue();
        JsonNode claims = JSON.readTree(Base64.getUrlDecoder().decode(compact.split("\\.")[0]));
        assertEquals(PortalStaffAssertionSigner.requestHash("GET", sent.getUri(), new byte[0]),
                claims.get("request_hash").textValue());
    }

    @Test
    void shouldRefuseDisabledWrongJobWrongPermissionAndEscapingRoute_beforeHttp() {
        Exchange exchange = new Exchange(200, "{}");
        var on = service(exchange, true);
        var off = service(exchange, false);
        var delivery = signer(PortalJob.DELIVERY_SYNC);
        assertThrows(PortalRequestPreparationException.class, () -> off.exchangeJob(delivery,
                PortalJob.PERMISSION_DELIVERY_UPLOAD, "POST", UPLOAD, new byte[1], 201));
        assertThrows(PortalRequestPreparationException.class, () -> on.exchangeJob(signer(PortalJob.KEY_PUBLISH),
                PortalJob.PERMISSION_DELIVERY_UPLOAD, "POST", UPLOAD, new byte[1], 201));
        assertThrows(PortalRequestPreparationException.class, () -> on.exchangeJob(delivery,
                PortalJob.PERMISSION_APPROVAL_REQUESTS_READ, "GET", EVENTS, new byte[0], 200));
        for (String path : List.of("//evil.test/x", UPLOAD + "/../clinic-keys", UPLOAD + "?x=1",
                "/internal/carlos/patients/0/mycarlos/deliveries")) {
            assertThrows(PortalRequestPreparationException.class, () -> on.exchangeJob(delivery,
                    PortalJob.PERMISSION_DELIVERY_UPLOAD, "POST", path, new byte[1], 201));
        }
        assertThrows(PortalRequestPreparationException.class, () -> on.exchangeJob(delivery,
                PortalJob.PERMISSION_DELIVERY_UPLOAD, "GET", UPLOAD, new byte[0], 200));
        assertTrue(exchange.requests.isEmpty());
    }

    @Test
    void shouldKeepKnownMachineFailuresAndRedactRemoteText_whenPortalRefusesCall() {
        for (String code : List.of("device_removed", "delivery_conflict", "cursor_too_old", "approval_conflict")) {
            var service = service(new Exchange(409, "{\"code\":\"" + code + "\"}"), true);
            var failure = assertThrows(PatientPortalException.class, () -> service.exchangeJob(
                    signer(PortalJob.DELIVERY_SYNC), PortalJob.PERMISSION_DELIVERY_EVENTS_READ, "GET", EVENTS,
                    new byte[0], 200));
            assertEquals(code, failure.detail());
        }
        for (String body : List.of("{\"code\":\"FAKE-patient-secret\"}",
                "{\"code\":\"device_removed\",\"secret\":\"FAKE-patient-secret\"}",
                "{\"code\":\"delivery_conflict\",\"code\":\"device_removed\"}")) {
            var service = service(new Exchange(409, body), true);
            var failure = assertThrows(PatientPortalException.class, () -> service.exchangeJob(
                    signer(PortalJob.DELIVERY_SYNC), PortalJob.PERMISSION_DELIVERY_EVENTS_READ, "GET", EVENTS,
                    new byte[0], 200));
            assertNull(failure.detail());
            assertFalse(failure.toString().contains("FAKE-patient-secret"));
        }
    }

    @Test
    void shouldEnforceUtf8ResponseCapAndExactStatus_whenTransportReturnsSuccess() {
        for (Exchange exchange : List.of(new Exchange(202, "{}"),
                new Exchange(200, "é".repeat(524289)))) {
            var failure = assertThrows(PatientPortalException.class, () -> service(exchange, true).exchangeJob(
                    signer(PortalJob.DELIVERY_SYNC), PortalJob.PERMISSION_DELIVERY_EVENTS_READ, "GET", EVENTS,
                    new byte[0], 200));
            assertEquals(PatientPortalException.Kind.MALFORMED_RESPONSE, failure.kind());
        }
        assertDoesNotThrow(() -> service(new Exchange(200, "x".repeat(1048576)), true).exchangeJob(
                signer(PortalJob.DELIVERY_SYNC), PortalJob.PERMISSION_DELIVERY_EVENTS_READ, "GET", EVENTS,
                new byte[0], 200));
    }

    private static final class Exchange implements PatientPortalHttpExchange {
        final List<ClassicHttpRequest> requests = new ArrayList<>();
        final PatientPortalHttpResponse response;
        Exchange(int status, String body) { response = new PatientPortalHttpResponse(status, body); }
        public PatientPortalHttpResponse send(ClassicHttpRequest request) {
            requests.add(request);
            return response;
        }
    }
    private static PortalJobAssertionSigner signer(PortalJob job) {
        return PortalJobAssertionSigner.from(new PortalJobKey(job,
                PortalSecret.of(PortalTestKeys.JOB_PRIVATE_KEY), "FAKE-" + job.jobName()));
    }
    private static PatientPortalService service(Exchange exchange, boolean enabled) {
        var settings = PatientPortalSettings.fromProperties(Map.of(
                PatientPortalSettings.BASE_URL_KEY, "https://portal.example.test",
                PatientPortalSettings.CLINIC_ID_KEY, "clinic-a",
                PatientPortalSettings.SERVICE_TOKEN_KEY, "FAKE-service-token-only-00000001",
                PatientPortalSettings.STAFF_ASSERTION_KEY, PortalTestKeys.PRIVATE_KEY,
                PatientPortalSettings.STAFF_ASSERTION_KEY_ID, "FAKE-staff-key",
                PatientPortalSettings.CERTIFICATE_PINS_KEY, PortalTestKeys.UNUSED_TLS_PIN));
        return new PatientPortalService(settings, exchange,
                PortalStaffAssertionSigner.from(settings.staffAssertionPrivateKey()), () -> enabled);
    }
}
