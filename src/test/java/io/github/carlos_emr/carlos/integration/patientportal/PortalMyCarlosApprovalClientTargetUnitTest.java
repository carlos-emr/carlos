/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class PortalMyCarlosApprovalClientTargetUnitTest {
    private static PatientPortalSettings settings(String origin, String clinic) {
        return PatientPortalSettings.fromProperties(Map.of(
                PatientPortalSettings.BASE_URL_KEY, origin,
                PatientPortalSettings.CLINIC_ID_KEY, clinic,
                PatientPortalSettings.SERVICE_TOKEN_KEY, "FAKE-service-token-only-00000001",
                PatientPortalSettings.STAFF_ASSERTION_KEY, PortalTestKeys.PRIVATE_KEY,
                PatientPortalSettings.STAFF_ASSERTION_KEY_ID, "FAKE-staff-key",
                PatientPortalSettings.CERTIFICATE_PINS_KEY, PortalTestKeys.UNUSED_TLS_PIN));
    }
    @Test void shouldRefuseOldCachedClientBeforePollingOrImport_whenCurrentClinicOrOriginChanges() throws Exception {
        var old = settings("https://localhost:8455", "clinic-a");
        AtomicInteger network = new AtomicInteger();
        var portal = new PatientPortalService(old, request -> {
            network.incrementAndGet();
            return new PatientPortalHttpResponse(200, "{\"requests\":[],\"last_sequence\":\"0\",\"next_cursor\":null}");
        }, PortalStaffAssertionSigner.from(old.staffAssertionPrivateKey()), () -> true);
        for (var newer : new PatientPortalSettings[]{settings("https://localhost:9555", "clinic-a"),
                settings("https://localhost:8455", "clinic-b")}) {
            var f = new PortalMyCarlosDeviceApprovalStateTestSupport.Fixture();
            var properties = mock(PropertyDao.class);
            AtomicInteger consumers = new AtomicInteger();
            var cfg = PortalMyCarlosDeviceApprovalService.Current.fromValidated(newer, "https://localhost:8455/patient", true, true);
            var staff = new PortalMyCarlosStaffApprovalService(mock(PortalMyCarlosDeviceApprovalTransactions.class),
                    f.owner.factory, f.security, properties, () -> cfg, source -> {
                        consumers.incrementAndGet(); return f.service;
                    }, ignored -> { throw new AssertionError("client must be fenced before constructing a poll"); });
            assertThrows(IllegalStateException.class, () -> staff.refresh(f.session, 1, portal));
            assertEquals(0, consumers.get());
            verifyNoInteractions(properties);
        }
        assertEquals(0, network.get());
    }
    @Test void shouldAcceptExactCachedTargetWithoutHttp_whenCurrentTargetMatches() {
        var settings = settings("https://localhost:8455", "clinic-a");
        AtomicInteger network = new AtomicInteger();
        var portal = new PatientPortalService(settings, request -> {
            network.incrementAndGet(); return new PatientPortalHttpResponse(200, "{}");
        }, PortalStaffAssertionSigner.from(settings.staffAssertionPrivateKey()), () -> true);
        var cfg = PortalMyCarlosDeviceApprovalService.Current.fromValidated(settings, "https://localhost:8455/patient", true, true);
        assertDoesNotThrow(() -> PortalMyCarlosStaffApprovalService.requireClientTarget(portal, cfg,
                PortalJob.PERMISSION_APPROVAL_REQUESTS_READ, "GET",
                "/internal/carlos/mycarlos/device-approval-requests?after=17&limit=20"));
        assertEquals(0, network.get());
    }
    @Test void shouldRefuseFetchedOldPageBeforeImport_whenConfigurationChangesDuringHttp() throws Exception {
        var old = settings("https://localhost:8455", "clinic-a");
        var newer = settings("https://localhost:9555", "clinic-a");
        var original = PortalMyCarlosDeviceApprovalService.Current.fromValidated(old, "https://localhost:8455/patient", true, true);
        var changed = PortalMyCarlosDeviceApprovalService.Current.fromValidated(newer, "https://localhost:8455/patient", true, true);
        var current = new java.util.concurrent.atomic.AtomicReference<>(original);
        var f = new PortalMyCarlosDeviceApprovalStateTestSupport.Fixture();
        var json = new com.fasterxml.jackson.databind.ObjectMapper().createObjectNode();
        json.putArray("requests").addObject().put("sequence", "1").put("demographic_no", 1)
                .put("intent", f.first.path("intent").path("compact").textValue());
        json.put("last_sequence", "1").putNull("next_cursor");
        AtomicInteger network = new AtomicInteger();
        var portal = new PatientPortalService(old, request -> {
            network.incrementAndGet();
            current.set(changed);
            return new PatientPortalHttpResponse(200, json.toString());
        }, PortalStaffAssertionSigner.from(old.staffAssertionPrivateKey()), () -> true);
        var job = PortalJobAssertionSigner.from(new PortalJobKey(PortalJob.DEVICE_APPROVAL_SYNC,
                PortalSecret.of(PortalTestKeys.JOB_PRIVATE_KEY), "FAKE-approval-job"));
        var client = new PortalMyCarlosApprovalClient((permission, method, path, body, status) ->
                portal.exchangeJob(job, permission, method, path, body, status));
        var properties = mock(PropertyDao.class);
        AtomicInteger consumers = new AtomicInteger();
        var staff = new PortalMyCarlosStaffApprovalService(mock(PortalMyCarlosDeviceApprovalTransactions.class),
                f.owner.factory, f.security, properties, current::get, source -> {
                    consumers.incrementAndGet(); return f.service;
                }, ignored -> client);
        assertThrows(IllegalStateException.class, () -> staff.refresh(f.session, 1, portal));
        assertEquals(1, network.get());
        assertEquals(0, consumers.get());
        verifyNoInteractions(properties);
    }

}
