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
                    f.owner.factory, f.security, properties, () -> cfg, (source, transactionalSource) -> {
                        consumers.incrementAndGet();
                return f.service;
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
            network.incrementAndGet();
            return new PatientPortalHttpResponse(200, "{}");
        }, PortalStaffAssertionSigner.from(settings.staffAssertionPrivateKey()), () -> true);
        var cfg = PortalMyCarlosDeviceApprovalService.Current.fromValidated(settings, "https://localhost:8455/patient", true, true);
        assertDoesNotThrow(() -> PortalMyCarlosStaffApprovalService.requireClientTarget(portal, cfg,
                PortalJob.PERMISSION_APPROVAL_REQUESTS_READ, "GET",
                "/internal/carlos/mycarlos/device-approval-requests?after=17&limit=20"));
        assertEquals(0, network.get());
    }
    @Test void shouldAllowOnlyExactCodeRoutes_forDeviceCodeJob() {
        var settings = settings("https://localhost:8455", "clinic-a");
        var portal = new PatientPortalService(settings, request -> new PatientPortalHttpResponse(200, "{}"),
                PortalStaffAssertionSigner.from(settings.staffAssertionPrivateKey()), () -> true);
        var cfg = PortalMyCarlosDeviceApprovalService.Current.fromValidated(settings, "https://localhost:8455/patient", true, true);
        String request = "0f8fad5b-d9cb-469f-a165-70867728950e";
        assertDoesNotThrow(() -> PortalMyCarlosStaffApprovalService.requireClientTarget(portal, cfg,
                PortalJob.PERMISSION_APPROVAL_CODES_READ, "GET", "/internal/carlos/mycarlos/device-approval-codes?after=3&limit=20"));
        assertDoesNotThrow(() -> PortalMyCarlosStaffApprovalService.requireClientTarget(portal, cfg,
                PortalJob.PERMISSION_APPROVAL_CODES_CONSUME, "PUT",
                "/internal/carlos/mycarlos/device-approval-requests/" + request + "/codes/5/consumed"));
        for (String[] bad : new String[][] {
                {PortalJob.PERMISSION_APPROVAL_CODES_READ, "PUT", "/internal/carlos/mycarlos/device-approval-codes?after=3&limit=20"},
                {PortalJob.PERMISSION_APPROVAL_CODES_READ, "GET", "/internal/carlos/mycarlos/device-approval-codes?after=03&limit=20"},
                {PortalJob.PERMISSION_APPROVAL_CODES_READ, "GET", "/internal/carlos/mycarlos/device-approval-codes?after=3&limit=21"},
                {PortalJob.PERMISSION_APPROVAL_REQUESTS_READ, "GET", "/internal/carlos/mycarlos/device-approval-codes?after=3&limit=20"},
                {PortalJob.PERMISSION_APPROVAL_CODES_CONSUME, "PUT", "/internal/carlos/mycarlos/device-approval-requests/" + request + "/codes/6/consumed"},
                {PortalJob.PERMISSION_APPROVAL_CODES_CONSUME, "PUT", "/internal/carlos/mycarlos/device-approval-requests/" + request + "/codes/0/consumed"},
                {PortalJob.PERMISSION_APPROVAL_CODES_CONSUME, "POST", "/internal/carlos/mycarlos/device-approval-requests/" + request + "/codes/1/consumed"},
                {PortalJob.PERMISSION_APPROVAL_CODES_CONSUME, "PUT", "/internal/carlos/mycarlos/device-approval-requests/" + request.toUpperCase(java.util.Locale.ROOT) + "/codes/1/consumed"}}) {
            assertThrows(PortalRequestPreparationException.class,
                    () -> PortalMyCarlosStaffApprovalService.requireClientTarget(portal, cfg, bad[0], bad[1], bad[2]),
                    String.join(" ", bad));
        }
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
                f.owner.factory, f.security, properties, current::get, (source, transactionalSource) -> {
                    consumers.incrementAndGet();
            return f.service;
                }, ignored -> client);
        assertThrows(IllegalStateException.class, () -> staff.refresh(f.session, 1, portal));
        assertEquals(1, network.get());
        assertEquals(0, consumers.get());
        verifyNoInteractions(properties);
    }

    @Test void shouldLeaveNoAcceptedRoot_whenConfigurationSwitchesBeforeInventoryOrAfterRowLocks() throws Exception {
        for (boolean beforeInventory : new boolean[]{true, false}) {
            var f = new PortalMyCarlosDeviceApprovalStateTestSupport.Fixture();
            var old = settings("https://localhost:8455", f.scope.clinicId());
            var original = PortalMyCarlosDeviceApprovalService.Current.fromValidated(old, f.scope.canonicalPublicBase(), true, true);
            var changed = f.current;
            var current = new java.util.concurrent.atomic.AtomicReference<>(original);
            // Before inventory: an exact, otherwise acceptable challenge already belongs to B.
            // After row locks: an exact A challenge becomes stale while the operation holds locks.
            if (!beforeInventory) f.current = original;
            var row = f.install(f.first, false);
            if (beforeInventory) {
                Runnable snapshot = f.owner.manager.onBegin;
                f.owner.manager.onBegin = () -> { snapshot.run();
                    current.set(changed); };
            } else {
                f.lastLock = () -> current.set(changed);
            }
            var transactions = new PortalMyCarlosDeviceApprovalTransactions(f.owner.transactions,
                    new PortalMyCarlosKeyInventoryWorker(f.properties));
            var key = PortalMyCarlosDeviceApprovalStateTestSupport.privateKey(f.data.path("keys").path("clinic"),
                    "FAKE_private_pkcs8_b64url", "EC");
            var signer = new PortalMyCarlosDeviceApprovalService.Signer() {
                public PortalMyCarlosDeviceApprovalService.SigningIdentity currentIdentity() {
                    return new PortalMyCarlosDeviceApprovalService.SigningIdentity(
                            f.data.path("keys").path("clinic").path("kid").asText(),
                            f.data.path("keys").path("clinic").path("spki").asText());
                }
                public String sign(PortalMyCarlosDeviceApprovalTypes.Kind kind, Map<String, Object> claims,
                        PortalMyCarlosDeviceApprovalTypes.Scope scope, PortalMyCarlosDeviceApprovalService.SigningIdentity identity) {
                    f.signs.incrementAndGet();
                    return PortalMyCarlosDeviceApprovalCrypto.sign(kind, claims, scope, key);
                }
            };
            AtomicInteger network = new AtomicInteger();
            var portal = new PatientPortalService(old, request -> {
                network.incrementAndGet();
                return new PatientPortalHttpResponse(200, "{\"status\":\"stored\"}");
            }, PortalStaffAssertionSigner.from(old.staffAssertionPrivateKey()), () -> true);
            var staff = new PortalMyCarlosStaffApprovalService(transactions, f.owner.factory, f.security,
                    mock(PropertyDao.class), current::get, (source, transactionalSource) ->
                    new PortalMyCarlosDeviceApprovalService(transactions, f.bindings, f.challenges, f.devices,
                            f.outbox, f.security, transactionalSource, source, signer, () -> f.nextChallenge,
                            java.util.UUID::randomUUID, f.clock, f.audits::add), ignored -> {
                                throw new AssertionError("publication must not follow a refused acceptance");
                            });
            var refusal = assertThrows(RuntimeException.class, () -> staff.approveFirst(f.session, 1,
                    PortalMyCarlosDeviceApprovalCodec.base64(row.challengeId().copy()),
                    java.util.HexFormat.of().formatHex(row.challengeHash().copy()),
                    java.util.HexFormat.of().formatHex(PortalMyCarlosDeviceApprovalCodec.exact32(row.candidateKid())), portal));
            assertNull(f.binding);
            assertTrue(f.deviceRows.isEmpty());
            assertTrue(f.outboxRows.isEmpty());
            assertTrue(f.challengeRows.get(row.challengeId()).pending());
            assertEquals(1, f.owner.manager.rollbacks);
            assertEquals(0, f.owner.manager.commits);
            assertEquals(0, f.signs.get());
            assertEquals(0, network.get());
            assertInstanceOf(IllegalStateException.class, refusal);
        }
    }

    private static PortalJobAssertionSigner job() {
        return PortalJobAssertionSigner.from(new PortalJobKey(PortalJob.DEVICE_APPROVAL_SYNC,
                PortalSecret.of(PortalTestKeys.JOB_PRIVATE_KEY), "FAKE-approval-job"));
    }

    @Test void shouldFenceProductionPutBeforeHttp_whenCapturedConfigurationOrCachedTargetChanges() throws Exception {
        for (String kind : new String[]{"challenge", "decision"}) {
            for (boolean staleCachedTarget : new boolean[]{false, true}) {
                var old = settings("https://localhost:8455", "clinic-a");
                var newer = settings("https://localhost:9555", "clinic-a");
                var original = PortalMyCarlosDeviceApprovalService.Current.fromValidated(old, "https://localhost:8455/patient", true, true);
                var changed = PortalMyCarlosDeviceApprovalService.Current.fromValidated(newer, "https://localhost:8455/patient", true, true);
                var current = new java.util.concurrent.atomic.AtomicReference<>(staleCachedTarget ? changed : original);
                AtomicInteger network = new AtomicInteger();
                var portal = new PatientPortalService(old, request -> {
                    network.incrementAndGet();
                    return new PatientPortalHttpResponse(200, "{\"status\":\"stored\"}");
                }, PortalStaffAssertionSigner.from(old.staffAssertionPrivateKey()), () -> true);
                var f = new PortalMyCarlosDeviceApprovalStateTestSupport.Fixture();
                var staff = new PortalMyCarlosStaffApprovalService(mock(PortalMyCarlosDeviceApprovalTransactions.class),
                        f.owner.factory, f.security, mock(PropertyDao.class), current::get, null, null);
                var client = staff.client(portal, job());
                current.set(changed);
                assertThrows(IllegalStateException.class, () -> client.publish(
                        java.util.UUID.fromString("39d10c50-6ddb-4164-8824-f4d540c1589c"), kind, new byte[]{123, 125}));
                assertEquals(0, network.get());
            }
        }
    }

    @Test void shouldFenceProductionPutAfterHttp_whenConfigurationChangesDuringSuccessOrFailure() throws Exception {
        for (String kind : new String[]{"challenge", "decision"}) {
            for (int status : new int[]{200, 410}) {
                var old = settings("https://localhost:8455", "clinic-a");
                var newer = settings("https://localhost:9555", "clinic-a");
                var original = PortalMyCarlosDeviceApprovalService.Current.fromValidated(old, "https://localhost:8455/patient", true, true);
                var changed = PortalMyCarlosDeviceApprovalService.Current.fromValidated(newer, "https://localhost:8455/patient", true, true);
                var current = new java.util.concurrent.atomic.AtomicReference<>(original);
                AtomicInteger network = new AtomicInteger();
                var id = java.util.UUID.fromString("39d10c50-6ddb-4164-8824-f4d540c1589c");
                var portal = new PatientPortalService(old, request -> {
                    network.incrementAndGet();
                    assertEquals("PUT", request.getMethod());
                    assertEquals("https://localhost:8455/internal/carlos/mycarlos/device-approval-requests/" + id + "/" + kind,
                            assertDoesNotThrow(request::getUri).toASCIIString());
                    current.set(changed);
                    return new PatientPortalHttpResponse(status, status == 200 ? "{\"status\":\"stored\"}" : "{\"code\":\"approval_expired\"}");
                }, PortalStaffAssertionSigner.from(old.staffAssertionPrivateKey()), () -> true);
                var f = new PortalMyCarlosDeviceApprovalStateTestSupport.Fixture();
                var staff = new PortalMyCarlosStaffApprovalService(mock(PortalMyCarlosDeviceApprovalTransactions.class),
                        f.owner.factory, f.security, mock(PropertyDao.class), current::get, null, null);
                var client = staff.client(portal, job());
                var failure = assertThrows(IllegalStateException.class,
                        () -> client.publish(id, kind, new byte[]{123, 125}));
                assertEquals(status == 410 ? 1 : 0, failure.getSuppressed().length);
                if (status == 410) {
                    var exchangeFailure = assertInstanceOf(PatientPortalException.class, failure.getSuppressed()[0]);
                    assertEquals(410, exchangeFailure.statusCode());
                }
                assertEquals(1, network.get());
            }
        }
    }

    @Test void shouldRetainOriginalHttpFailure_whenPostResponseConfigurationStaysBound() throws Exception {
        var old = settings("https://localhost:8455", "clinic-a");
        var cfg = PortalMyCarlosDeviceApprovalService.Current.fromValidated(old, "https://localhost:8455/patient", true, true);
        AtomicInteger network = new AtomicInteger();
        var portal = new PatientPortalService(old, request -> {
            network.incrementAndGet();
            return new PatientPortalHttpResponse(410, "{\"code\":\"approval_expired\"}");
        }, PortalStaffAssertionSigner.from(old.staffAssertionPrivateKey()), () -> true);
        var f = new PortalMyCarlosDeviceApprovalStateTestSupport.Fixture();
        var staff = new PortalMyCarlosStaffApprovalService(mock(PortalMyCarlosDeviceApprovalTransactions.class),
                f.owner.factory, f.security, mock(PropertyDao.class), () -> cfg, null, null);
        var client = staff.client(portal, job());
        for (String kind : new String[]{"challenge", "decision"}) {
            var failure = assertThrows(PatientPortalException.class, () -> client.publish(
                    java.util.UUID.fromString("39d10c50-6ddb-4164-8824-f4d540c1589c"), kind, new byte[]{123, 125}));
            assertEquals(410, failure.statusCode());
            assertEquals(0, failure.getSuppressed().length);
        }
        assertEquals(2, network.get());
    }

    @Test void shouldPublishThroughProductionPutFence_whenConfigurationStaysBound() throws Exception {
        var old = settings("https://localhost:8455", "clinic-a");
        var cfg = PortalMyCarlosDeviceApprovalService.Current.fromValidated(old, "https://localhost:8455/patient", true, true);
        AtomicInteger network = new AtomicInteger();
        var portal = new PatientPortalService(old, request -> {
            network.incrementAndGet();
            return new PatientPortalHttpResponse(200, "{\"status\":\"stored\"}");
        }, PortalStaffAssertionSigner.from(old.staffAssertionPrivateKey()), () -> true);
        var f = new PortalMyCarlosDeviceApprovalStateTestSupport.Fixture();
        var staff = new PortalMyCarlosStaffApprovalService(mock(PortalMyCarlosDeviceApprovalTransactions.class),
                f.owner.factory, f.security, mock(PropertyDao.class), () -> cfg, null, null);
        var client = staff.client(portal, job());
        for (String kind : new String[]{"challenge", "decision"}) {
            assertDoesNotThrow(() -> client.publish(java.util.UUID.fromString("39d10c50-6ddb-4164-8824-f4d540c1589c"),
                    kind, new byte[]{123, 125}));
        }
        assertEquals(2, network.get());
    }
}
