/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalStateTestSupport.JSON;
import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalStateTestSupport.compact;
import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalStateTestSupport.key;
import static org.junit.jupiter.api.Assertions.*;

import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalStateTestSupport.Fixture;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Flow;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Kind;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.TrustedKeys;
import java.util.HexFormat;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Clinic code v1 (mycarlos#141) through the real approval core, over the shared FAKE contract
 * fixture's keys and in-memory rows: a {@code code} challenge, its acceptance with no staff, the
 * {@code code_attempts} refusal, and staff approval after a matched code ({@code code_and_staff}).
 */
@Tag("unit")
class PortalMyCarlosDeviceApprovalCodeFlowUnitTest {

    private static PortalMyCarlosDeviceApprovalChallenge codeChallenge(Fixture f) {
        return f.service.createOrReadExactChallenge(1, compact(f.first, "intent"), true);
    }

    private static TrustedKeys trusted(Fixture f) {
        var clinic = f.data.path("keys").path("clinic");
        String kid = clinic.path("kid").asText();
        return new TrustedKeys(f.scope, Map.of(kid, new PatientPortalClinicKeysPublishRequest.Key(kid,
                clinic.path("spki").asText(), "active", null, null)), null);
    }

    private static String fingerprint(PortalMyCarlosDeviceApprovalChallenge row) {
        return HexFormat.of().formatHex(PortalMyCarlosDeviceApprovalCodec.exact32(row.candidateKid()));
    }

    @Test
    void shouldSignCodeChallengeWithoutPredecessor_whenAskedForByCode() throws Exception {
        var f = new Fixture();
        var row = codeChallenge(f);
        assertEquals("code", row.kind());
        assertNull(row.predecessorId());
        assertNull(row.predecessorKid());
        assertNull(row.predecessorVersion());
        var claims = PortalMyCarlosDeviceApprovalCodec.decode(Kind.CHALLENGE, row.signedChallenge(), f.scope).claims();
        assertEquals("code", claims.text("approval_kind"));
        assertNull(claims.values().get("predecessor_device_key_id"));
        assertNull(claims.values().get("predecessor_version"));
        // The kid the code's check digits are bound to is the one that signed this challenge.
        assertEquals(f.data.path("keys").path("clinic").path("kid").asText(), claims.text("kid"));
        // Read again, the same challenge comes back unchanged.
        assertEquals(row, codeChallenge(f));
        assertEquals(1, f.challengeRows.size());
    }

    @Test
    void shouldKeepStaffFirst_whenNotAskedForByCode() throws Exception {
        var f = new Fixture();
        var row = f.service.createOrReadExactChallenge(1, compact(f.first, "intent"));
        assertEquals("staff_first", row.kind());
        var refusal = assertThrows(PortalMyCarlosDeviceApprovalService.Refusal.class,
                () -> f.service.acceptFromCode(1, row.challengeId(), challenge -> true));
        assertEquals("staff_required", refusal.code);
        assertTrue(f.deviceRows.isEmpty());
    }

    @Test
    void shouldAcceptByCodeWithoutStaff_whenCodeMatched() throws Exception {
        var f = new Fixture();
        var row = codeChallenge(f);
        var decision = f.service.acceptFromCode(1, row.challengeId(), challenge -> true);
        assertFalse(decision.historical());
        var device = decision.device();
        assertEquals("code", device.kind());
        assertNull(device.providerNo());
        assertNull(device.securityNo());
        assertNull(device.staffMethod());
        assertNull(device.predecessorApproval());
        assertNotNull(f.binding);
        assertEquals(device.kid(), f.binding.rootKid());

        var receipt = PortalMyCarlosDeviceApprovalCodec.decode(Kind.ACCEPTANCE, device.acceptance(), f.scope).claims();
        assertEquals("code", receipt.text("approval_kind"));
        assertNull(receipt.values().get("approval_sha256"));
        assertNull(receipt.values().get("predecessor_device_key_id"));
        // The whole signed chain checks out as a code flow: no predecessor approval in it.
        assertNotNull(PortalMyCarlosDeviceApprovalCrypto.verifyFlow(
                new Flow(row.intent(), row.signedChallenge(), null, device.acceptance()), trusted(f)));

        var body = JSON.readTree(f.outboxRows.get(key(row.challengeId(), "decision")).body().copy());
        assertEquals("accepted", body.path("status").asText());
        assertTrue(body.path("reason").isNull());

        // A repeat is answered from the stored evidence, with nothing new signed or queued.
        int signed = f.signs.get();
        var again = f.service.acceptFromCode(1, row.challengeId(), challenge -> true);
        assertTrue(again.historical());
        assertEquals(device.kid(), again.device().kid());
        assertEquals(signed, f.signs.get());
    }

    @Test
    void shouldNotAccept_whenCodeHasNotMatched() throws Exception {
        var f = new Fixture();
        var row = codeChallenge(f);
        var refusal = assertThrows(PortalMyCarlosDeviceApprovalService.Refusal.class,
                () -> f.service.acceptFromCode(1, row.challengeId(), challenge -> false));
        assertEquals("staff_required", refusal.code);
        assertTrue(f.deviceRows.isEmpty());
        assertNull(f.binding);
        assertNull(f.outboxRows.get(key(row.challengeId(), "decision")));
    }

    @Test
    void shouldRefuseWithCodeAttempts_onceAndForGood() throws Exception {
        var f = new Fixture();
        var row = codeChallenge(f);
        assertTrue(f.service.refuseCodeAttempts(1, row.challengeId()));
        var cancelled = f.challengeRows.get(row.challengeId());
        assertNotNull(cancelled.cancelledAt());
        assertEquals("code_attempts", cancelled.cancellationReason());
        var body = JSON.readTree(f.outboxRows.get(key(row.challengeId(), "decision")).body().copy());
        assertEquals("refused", body.path("status").asText());
        assertEquals("code_attempts", body.path("reason").asText());
        assertTrue(body.path("acceptance").isNull());

        // Again: nothing changes. A late right code cannot approve the device any more: the refusal
        // is already queued, as for any decided challenge.
        assertFalse(f.service.refuseCodeAttempts(1, row.challengeId()));
        var refusal = assertThrows(PortalMyCarlosDeviceApprovalService.Refusal.class,
                () -> f.service.acceptFromCode(1, row.challengeId(), challenge -> true));
        assertEquals("approval_conflict", refusal.code);
        assertTrue(f.deviceRows.isEmpty());
    }

    @Test
    void shouldNotRefuseWithCodeAttempts_forStaffChallenge() throws Exception {
        var f = new Fixture();
        var row = f.service.createOrReadExactChallenge(1, compact(f.first, "intent"));
        var refusal = assertThrows(PortalMyCarlosDeviceApprovalService.Refusal.class,
                () -> f.service.refuseCodeAttempts(1, row.challengeId()));
        assertEquals("approval_conflict", refusal.code);
        assertTrue(f.challengeRows.get(row.challengeId()).pending());
    }

    @Test
    void shouldLetStaffApproveCodeChallenge_onlyAfterCodeMatched() throws Exception {
        var f = new Fixture();
        var row = codeChallenge(f);
        var refusal = assertThrows(PortalMyCarlosDeviceApprovalService.Refusal.class,
                () -> f.service.acceptFirstFromStaff(f.session, 1, row.challengeId(), row.challengeHash(),
                        fingerprint(row), challenge -> false));
        assertEquals("staff_required", refusal.code);
        // The plain staff path never approves a code challenge.
        refusal = assertThrows(PortalMyCarlosDeviceApprovalService.Refusal.class,
                () -> f.service.acceptFirstFromStaff(f.session, 1, row.challengeId(), row.challengeHash(), fingerprint(row)));
        assertEquals("staff_required", refusal.code);

        var device = f.service.acceptFirstFromStaff(f.session, 1, row.challengeId(), row.challengeHash(),
                fingerprint(row), challenge -> true).device();
        assertEquals("code", device.kind());
        assertEquals("123", device.providerNo());
        assertEquals(7, device.securityNo());
        assertEquals("in_person_device_comparison", device.staffMethod());
        var receipt = PortalMyCarlosDeviceApprovalCodec.decode(Kind.ACCEPTANCE, device.acceptance(), f.scope).claims();
        assertEquals("code", receipt.text("approval_kind"));
        assertNull(receipt.values().get("approval_sha256"));
    }
}
