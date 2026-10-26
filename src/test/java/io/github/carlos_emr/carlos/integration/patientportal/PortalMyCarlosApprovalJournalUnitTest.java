/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.Property;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
class PortalMyCarlosApprovalJournalUnitTest {
    @Test void shouldKeepExactRejectedEvidence_whenExpiredRequestIsSeenAgain() throws Exception {
        var transactions = mock(PortalMyCarlosDeviceApprovalTransactions.class);
        when(transactions.withApprovalState(any())).thenAnswer(call -> ((Supplier<?>)call.getArgument(0)).get());
        var properties = mock(PropertyDao.class);
        Map<String, Property> rows = new HashMap<>();
        when(properties.findByNameForUpdate(anyString())).thenAnswer(call -> rows.containsKey(call.getArgument(0))
                ? List.of(rows.get(call.getArgument(0))) : List.of());
        doAnswer(call -> { Property row = call.getArgument(0);
            rows.put(row.getName(), row);
            return null;
        }).when(properties).persist(any());
        var fixture = new PortalMyCarlosDeviceApprovalStateTestSupport.Fixture();
        var request = new PortalMyCarlosApprovalClient.Request(1, 1, "FAKE exact expired intent bytes");
        var journal = new PortalMyCarlosApprovalJournal(transactions, properties);
        assertNull(journal.record(fixture.current, request, null));
        assertEquals("challenge_expired", journal.record(fixture.current, request, "challenge_expired"));
        assertEquals("challenge_expired", journal.record(fixture.current, request, null));
        assertEquals(1, rows.size());
        assertThrows(IllegalStateException.class, () -> journal.record(fixture.current,
                new PortalMyCarlosApprovalClient.Request(1, 1, "changed same sequence"), null));
    }
    @Test void shouldReachFreshRequestOnRepeatedPolls_whenOlderIntentExpiredBeforeImport() throws Exception {
        var f = new PortalMyCarlosDeviceApprovalStateTestSupport.Fixture();
        var transactions = new PortalMyCarlosDeviceApprovalTransactions(f.owner.transactions,
                new PortalMyCarlosKeyInventoryWorker(f.properties));
        var properties = mock(PropertyDao.class);
        Map<String, Property> rows = new HashMap<>();
        when(properties.findByNameForUpdate(anyString())).thenAnswer(call -> rows.containsKey(call.getArgument(0))
                ? List.of(rows.get(call.getArgument(0))) : List.of());
        doAnswer(call -> { Property row = call.getArgument(0);
            rows.put(row.getName(), row);
            return null;
        }).when(properties).persist(any());
        var staff = new PortalMyCarlosStaffApprovalService(transactions, f.owner.factory, f.security, properties,
                () -> f.current, (source, transactionalSource) -> f.service, ignored -> null);
        var claims = PortalMyCarlosDeviceApprovalStateTestSupport.claims(f.first.path("intent").path("claims"));
        claims.put("request_id", "39d10c50-6ddb-4164-8824-f4d540c1589c");
        claims.put("iat", f.clock.instant().minusSeconds(1000).getEpochSecond());
        claims.put("exp", f.clock.instant().minusSeconds(400).getEpochSecond());
        var key = PortalMyCarlosDeviceApprovalStateTestSupport.privateKey(f.data.path("keys").path("first"),
                "FAKE_ed25519_private_pkcs8_b64url", "Ed25519");
        String expired = PortalMyCarlosDeviceApprovalCrypto.sign(PortalMyCarlosDeviceApprovalTypes.Kind.INTENT,
                claims, f.scope, key);
        var old = new PortalMyCarlosApprovalClient.Request(1, 1, expired);
        var fresh = new PortalMyCarlosApprovalClient.Request(2, 1, f.first.path("intent").path("compact").textValue());
        for (int poll = 0; poll < 2; poll++) {
            var refused = new java.util.ArrayList<String>();
            assertNull(staff.prepareRequest(f.session, 1, old, refused));
            assertEquals(List.of("challenge_expired"), refused);
            var challenge = staff.prepareRequest(f.session, 1, fresh, refused);
            assertNotNull(challenge);
            assertEquals(fresh.intent(), challenge.intent());
        }
        assertEquals(2, rows.size());
        assertEquals(1, f.challengeRows.size());
        assertEquals(1, f.signs.get());
    }

    @Test void shouldKeepMovingAfterPreparedChallengeExpires_whenFirstPublicationWasNeverAdmitted() throws Exception {
        var f = new PortalMyCarlosDeviceApprovalStateTestSupport.Fixture();
        var transactions = new PortalMyCarlosDeviceApprovalTransactions(f.owner.transactions,
                new PortalMyCarlosKeyInventoryWorker(f.properties));
        var properties = mock(PropertyDao.class);
        Map<String, Property> rows = new HashMap<>();
        when(properties.findByNameForUpdate(anyString())).thenAnswer(call -> rows.containsKey(call.getArgument(0))
                ? List.of(rows.get(call.getArgument(0))) : List.of());
        doAnswer(call -> { Property row = call.getArgument(0);
            rows.put(row.getName(), row);
            return null;
        }).when(properties).persist(any());
        var staff = new PortalMyCarlosStaffApprovalService(transactions, f.owner.factory, f.security, properties,
                () -> f.current, (source, transactionalSource) -> f.service, ignored -> null);
        var old = new PortalMyCarlosApprovalClient.Request(1, 1, f.first.path("intent").path("compact").textValue());
        var refused = new java.util.ArrayList<String>();
        assertNotNull(staff.prepareRequest(f.session, 1, old, refused));
        // The first HTTP attempt never reached the portal. A later, authenticated retry is
        // explicitly refused at new challenge admission after its deadline.
        f.clock.value = f.clock.value.plusSeconds(601);
        assertTrue(staff.retainPublicationRefusal(old,
                PatientPortalException.ofStatus(410, "/internal/carlos/mycarlos/device-approval-requests/{request_id}/challenge", "approval_expired")));
        var claims = PortalMyCarlosDeviceApprovalStateTestSupport.claims(f.first.path("intent").path("claims"));
        claims.put("request_id", "ab7c68f9-2093-4e8e-a865-d592e550e4c4");
        claims.put("iat", f.clock.instant().getEpochSecond());
        claims.put("exp", f.clock.instant().plusSeconds(590).getEpochSecond());
        var key = PortalMyCarlosDeviceApprovalStateTestSupport.privateKey(f.data.path("keys").path("first"),
                "FAKE_ed25519_private_pkcs8_b64url", "Ed25519");
        var fresh = new PortalMyCarlosApprovalClient.Request(2, 1,
                PortalMyCarlosDeviceApprovalCrypto.sign(PortalMyCarlosDeviceApprovalTypes.Kind.INTENT, claims, f.scope, key));
        f.nextChallenge = new PortalMyCarlosDeviceApprovalTypes.Bytes(java.util.HexFormat.of().parseHex("47".repeat(32)));
        for (int poll = 0; poll < 2; poll++) {
            refused.clear();
            assertNull(staff.prepareRequest(f.session, 1, old, refused));
            assertEquals(List.of("challenge_expired"), refused);
            assertNotNull(staff.prepareRequest(f.session, 1, fresh, refused));
        }
        assertEquals(3, rows.size());
        assertEquals(2, f.challengeRows.size());
        assertEquals(2, f.signs.get());
    }

    @Test void shouldJournalMalformedRecipientAndContinue_whenOldSignedIntentHasInvalidScope() throws Exception {
        for (String recipient : List.of("", "x".repeat(65))) {
            var f = new PortalMyCarlosDeviceApprovalStateTestSupport.Fixture();
            var transactions = new PortalMyCarlosDeviceApprovalTransactions(f.owner.transactions,
                    new PortalMyCarlosKeyInventoryWorker(f.properties));
            var properties = mock(PropertyDao.class);
            Map<String, Property> rows = new HashMap<>();
            when(properties.findByNameForUpdate(anyString())).thenAnswer(call -> rows.containsKey(call.getArgument(0))
                    ? List.of(rows.get(call.getArgument(0))) : List.of());
            doAnswer(call -> { Property row = call.getArgument(0);
                rows.put(row.getName(), row);
                return null;
            }).when(properties).persist(any());
            var staff = new PortalMyCarlosStaffApprovalService(transactions, f.owner.factory, f.security, properties,
                    () -> f.current, (source, transactionalSource) -> f.service, ignored -> null);
            var claims = PortalMyCarlosDeviceApprovalStateTestSupport.claims(f.first.path("intent").path("claims"));
            claims.put("recipient_id", recipient);
            byte[] payload = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsBytes(claims);
            var key = PortalMyCarlosDeviceApprovalStateTestSupport.privateKey(f.data.path("keys").path("first"),
                    "FAKE_ed25519_private_pkcs8_b64url", "Ed25519");
            var signer = java.security.Signature.getInstance("Ed25519");
            signer.initSign(key);
            signer.update(payload);
            String invalid = PortalMyCarlosDeviceApprovalCodec.base64(payload) + "." + PortalMyCarlosDeviceApprovalCodec.base64(signer.sign());
            var old = new PortalMyCarlosApprovalClient.Request(1, 1, invalid);
            var fresh = new PortalMyCarlosApprovalClient.Request(2, 1, f.first.path("intent").path("compact").textValue());
            for (int poll = 0; poll < 2; poll++) {
                var refused = new java.util.ArrayList<String>();
                assertNull(staff.prepareRequest(f.session, 1, old, refused));
                assertEquals(List.of("invalid_proof"), refused);
                assertNotNull(staff.prepareRequest(f.session, 1, fresh, refused));
            }
            assertEquals(2, rows.size());
            assertEquals(1, f.challengeRows.size());
        }
    }
    @Test void shouldRefuseNoncanonicalOperatorBases_whenDemoApprovalIsEnabled() {
        assertEquals("https://localhost:8455/patient", PortalMyCarlosStaffApprovalService.canonicalDemoPublicBase("https://localhost:8455/patient"));
        for (String raw : new String[]{"https://LOCALHOST:8455/patient", "https://localhost:443/patient",
                "https://localhost:8455/a/../patient", "https://localhost:8455/patient/", "https://localhost:8455/é",
                "https://localhost:8455/%70atient", "https://example.test/patient", "https://localhost:65536/patient",
                "https://localhost:8455/" + "x".repeat(1024)}) {
            assertThrows(PatientPortalConfigurationException.class, () -> PortalMyCarlosStaffApprovalService.canonicalDemoPublicBase(raw));
        }
    }

}
