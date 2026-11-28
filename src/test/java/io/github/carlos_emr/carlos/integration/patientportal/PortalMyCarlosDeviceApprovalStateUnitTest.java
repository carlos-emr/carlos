/* SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.integration.patientportal;

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalStateTestSupport.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalService.Decision;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalService.Refusal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** State/owner unit behavior only; real schema, HTTP CSRF and durable crash proof stay separate. */
class PortalMyCarlosDeviceApprovalStateUnitTest {
    @TestFactory List<DynamicTest> consumesAll18FrozenLocalStateExpectations() throws Exception {
        var tests = new ArrayList<DynamicTest>();
        for (JsonNode vector : fixture().path("state_cases")) tests.add(DynamicTest.dynamicTest(vector.path("id").asText(), () -> {
            var f = new Fixture();
            boolean later = vector.path("flow").asText().equals("later");
            JsonNode state = vector.path("local_state");
            if (later || state.path("root_ever_existed").asBoolean()) f.seedPredecessor();
            var row = f.install(later ? f.later : f.first, later);
            String proof = compact(f.later, "approval");
            if (state.path("consumed").asBoolean()) f.service.acceptFromCurrentPredecessor(1, row.challengeId(), proof);
            f.write = state.path("staff_write").asBoolean(true);
            f.patientAccess = state.path("patient_access").asBoolean(true);
            if (state.has("now")) f.clock.value = Instant.ofEpochSecond(state.path("now").longValue());
            String predecessorKid = f.data.path("keys").path("predecessor").path("device_key_id").asText();
            if (state.has("predecessor_locally_accepted") && !state.path("predecessor_locally_accepted").asBoolean()) f.deviceRows.remove(predecessorKid);
            if (state.path("predecessor_removed").asBoolean()) {
                var predecessor = f.deviceRows.get(predecessorKid);
                if (predecessor != null) f.deviceRows.put(predecessorKid, predecessor.removed(UUID.fromString("fa758d81-aeea-4af1-8a28-627168f61c29"), "123", f.clock.value));
            }
            if (state.has("predecessor_version")) f.deviceRows.put(predecessorKid, withVersion(f.deviceRows.get(predecessorKid), Long.parseLong(state.path("predecessor_version").asText())));
            if (state.path("unknown_challenge").asBoolean()) f.challengeRows.remove(row.challengeId());
            if (state.path("binding_changed").asBoolean()) {
                var configured = mock(PatientPortalSettings.class);
                when(configured.clinicId()).thenReturn(f.scope.clinicId());
                when(configured.baseUrl()).thenReturn("https://changed.example.invalid");
                f.current = PortalMyCarlosDeviceApprovalService.Current.fromValidated(configured, f.scope.canonicalPublicBase(), true, true);
            }
            if (state.path("candidate_bound_elsewhere").asBoolean()) {
                var old = f.deviceRows.get(predecessorKid);
                f.deviceRows.put(row.candidateKid(), new PortalMyCarlosAcceptedDevice(2, 2, row.candidateKid(), row.candidateX(), row.candidateEd(),
                        old.kind(), row.challengeId(), old.providerNo(), old.securityNo(), old.staffMethod(), old.predecessorId(), old.predecessorVersion(),
                        old.predecessorApproval(), old.acceptance(), old.acceptanceHash(), old.acceptedAt(), null, null, null, null, 0));
            }
            if (state.path("candidate_removed").asBoolean()) {
                var accepted = f.deviceRows.get(row.candidateKid());
                f.deviceRows.put(accepted.kid(), accepted.removed(UUID.fromString("9e1383b5-a8c1-4164-afee-390da19d888b"), "123", f.clock.value));
            }
            if (state.has("same_original_proof") && !state.path("same_original_proof").asBoolean()) proof += "x";
            int acceptedBefore = f.deviceRows.size();
            String actual;
            try {
                // These fixture flags model a rejected SERVER boundary only. No production csrf
                // boolean exists, and this test does not claim the real CSRFGuard HTTP gate.
                if (state.has("csrf") && !state.path("csrf").asBoolean()) throw new SecurityException("FAKE boundary refused before core");
                Decision decision;
                if (later) decision = f.service.acceptFromCurrentPredecessor(1, row.challengeId(), proof);
                else if (state.has("full_fingerprint_confirmed") && !state.path("full_fingerprint_confirmed").asBoolean()) {
                    decision = f.service.acceptFirstFromStaff(f.session, 1, row.challengeId(), row.challengeHash(), "not-the-full-fingerprint");
                } else decision = f.acceptFirst(row);
                actual = decision.historical() ? decision.device().current() ? "idempotent" : "removed_historical" : "accepted";
            } catch (SecurityException denied) { actual = "forbidden";
            }
            catch (Refusal denied) { actual = denied.code;
            }
            assertEquals(vector.path("expected").asText(), actual);
            if (!actual.equals("accepted")) assertEquals(acceptedBefore, f.deviceRows.size(), "refusal/retry creates no new acceptance");
        }));
        assertEquals(18, tests.size());
        return tests;
    }
    private static PortalMyCarlosAcceptedDevice withVersion(PortalMyCarlosAcceptedDevice d, long version) {
        return new PortalMyCarlosAcceptedDevice(d.id(), d.bindingId(), d.kid(), d.x25519(), d.ed25519(), d.kind(), d.challengeId(),
                d.providerNo(), d.securityNo(), d.staffMethod(), d.predecessorId(), d.predecessorVersion(), d.predecessorApproval(),
                d.acceptance(), d.acceptanceHash(), d.acceptedAt(), d.removedAt(), d.removalId(), d.removalReason(), d.removedBy(), version);
    }

    @Test void disabledBeforeOwnerAndMissingWriteBeforeAnyStorage() throws Exception {
        var f = new Fixture();
        var row = f.install(f.first, false);
        f.enabled = false;
        assertEquals("approval_unavailable", assertThrows(Refusal.class, () -> f.acceptFirst(row)).code);
        assertEquals(0, f.owner.manager.begins);
        f.enabled = true;
        f.write = false;
        assertThrows(SecurityException.class, () -> f.acceptFirst(row));
        assertEquals(0, f.owner.manager.begins);
        assertNull(f.binding);
        assertTrue(f.deviceRows.isEmpty());
        assertEquals(0, f.signs.get());
    }
    @Test void expiryAndPermissionAreRecheckedAfterTheFinalRowLock() throws Exception {
        var f = new Fixture();
        var row = f.install(f.first, false);
        f.lastLock = () -> f.clock.value = row.expiresAt();
        assertEquals("challenge_expired", assertThrows(Refusal.class, () -> f.acceptFirst(row)).code);
        assertEquals(0, f.signs.get());
        assertNull(f.binding);
        f.clock.value = row.issuedAt();
        f.lastLock = () -> f.write = false;
        assertThrows(SecurityException.class, () -> f.acceptFirst(row));
        assertNull(f.binding);
        assertEquals(0, f.signs.get());
    }
    @Test void confirmedUnitRollbackReusesExactReceiptAndBodyWithoutAnotherSigningCall() throws Exception {
        var f = new Fixture();
        var row = f.install(f.first, false);
        f.failNextOutbox = true;
        var originalRollback = f.owner.manager.onRollback;
        f.owner.manager.onRollback = () -> { originalRollback.run();
            f.clock.value = f.clock.value.plusSeconds(1);
        };
        Decision decision = f.acceptFirst(row);
        assertFalse(decision.historical());
        assertEquals(1, f.signs.get());
        assertEquals(1, f.owner.manager.rollbacks);
        assertEquals(2, f.owner.manager.begins);
        assertEquals(1, f.owner.manager.commits);
        assertEquals(2, f.attemptedBodies.size());
        assertEquals(f.attemptedBodies.get(0), f.attemptedBodies.get(1));
        assertEquals(1, f.deviceRows.size());
        assertEquals(1, f.audits.size());
        assertNotSame(f.owner.manager.entityManagers.get(0), f.owner.manager.entityManagers.get(1));
    }
    @Test void throwingPreparationDisablesAutomaticOwnerReplay() throws Exception {
        var f = new Fixture();
        var row = f.install(f.first, false);
        f.signerFailure = conflict();
        assertSame(f.signerFailure, assertThrows(RuntimeException.class, () -> f.acceptFirst(row)));
        assertEquals(1, f.owner.manager.begins);
        assertEquals(1, f.owner.manager.rollbacks);
        assertEquals(1, f.signs.get());
        assertNull(f.binding);
        assertTrue(f.deviceRows.isEmpty());
        assertTrue(f.audits.isEmpty());
    }
    @Test void auditFailureAfterCommitDoesNotReplayAndLostReplyReadsOriginalHistory() throws Exception {
        var f = new Fixture();
        var row = f.install(f.first, false);
        f.failAudit = true;
        assertThrows(RuntimeException.class, () -> f.acceptFirst(row));
        assertEquals(1, f.owner.manager.begins);
        assertEquals(1, f.owner.manager.commits);
        String original = f.deviceRows.get(row.candidateKid()).acceptance();
        f.failAudit = false;
        f.keyActive = false;
        f.clock.value = row.expiresAt().plusSeconds(5);
        var retry = f.acceptFirst(row);
        assertTrue(retry.historical());
        assertEquals(original, retry.device().acceptance());
        assertEquals(1, f.signs.get());
        assertEquals(1, f.audits.size());
    }
    @Test void removalIsMonotonicRetainsEvidenceAndNeverTouchesInventory() throws Exception {
        var f = new Fixture();
        var row = f.install(f.first, false);
        var first = f.acceptFirst(row);
        clearInvocations(f.properties, f.coordination);
        var removed = f.service.removeFromStaff(f.session, 1, row.candidateKid());
        assertFalse(removed.current());
        assertEquals(first.device().acceptance(), removed.acceptance());
        assertEquals(1, removed.version());
        assertEquals(row.candidateX(), removed.x25519());
        assertEquals(removed, f.service.removeFromStaff(f.session, 1, row.candidateKid()));
        verifyNoInteractions(f.properties);
        verify(f.coordination, times(2)).lockRoutingReport(PortalMyCarlosDeviceApprovalTransactions.COORDINATION_KEY);
        verify(f.coordination, never()).lockRoutingReport(PortalMyCarlosKeyInventoryService.COORDINATION_KEY);
        f.keyActive = false;
        f.clock.value = row.expiresAt().plusSeconds(1);
        var retry = f.acceptFirst(row);
        assertTrue(retry.historical());
        assertFalse(retry.device().current());
        assertEquals(1, f.signs.get());
        assertEquals(2, f.audits.size());
    }
    @Test void changedChallengeHashCannotAuthorize() throws Exception {
        var f = new Fixture();
        var row = f.install(f.first, false);
        var wrongHash = new PortalMyCarlosDeviceApprovalTypes.Bytes(new byte[32]);
        assertThrows(Refusal.class, () -> f.service.acceptFirstFromStaff(f.session, 1, row.challengeId(), wrongHash,
                java.util.HexFormat.of().formatHex(PortalMyCarlosDeviceApprovalCodec.exact32(row.candidateKid()))));
        assertEquals(0, f.signs.get());
        assertNull(f.binding);
    }
}
