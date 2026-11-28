/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.Security;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeliveryOutboxDao.Original;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeliveryOutboxDao.Row;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalService.Current;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Bytes;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.DeviceKeys;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Reading aids in the sender outbox: each aid is its own delivery row, staged for an original that
 * already has a row, with its own lease, retry and fence; the original is never held back by it.
 * Synthetic transaction-owner contract only. Opaque markers are not PDFs, signatures or verified
 * capabilities. The in-memory owner explicitly simulates rollback; real MariaDB rollback,
 * uniqueness, concurrency and crash/recovery proof is still required before wiring this seam.
 */
@Tag("unit")
class PortalMyCarlosDeliveryAidStageUnitTest {
    private static final String B_ID = "00000000-0000-4000-8000-000000000001";
    private static final String C_ID = "00000000-0000-4000-8000-000000000002";
    private static final String D_ID = "00000000-0000-4000-8000-000000000003";
    private static final String CLINIC = "clinic-a";
    private static final String RECIPIENT = "recipient-a";
    private static final String PUBLIC_BASE = "https://portal.example.test";
    private static final String TARGET = PUBLIC_BASE + "/internal/carlos/patients/123/mycarlos/deliveries";
    private static final long CREATED = 1792454400;
    private static final String SIGNER = base64(marker(3));
    private static final String RAW_MAPPING = base64(marker(4));
    private static final Bytes ACCEPTANCE_HASH = new Bytes(marker(5));
    private final Fixture f = new Fixture();

    @AfterEach void noProviderSendUnlessAsked() { }

    @Test void shouldStageAnAidAsItsOwnRow_andLeaveTheOriginalRowUntouched() {
        var b = f.original(B_ID, 11); long originalRow = f.service.stageOriginal(f.staff, b.copy(), b.snapshot());
        var before = f.rows.get(originalRow);
        var c = f.aid(C_ID, 12, b);
        long aidRow = f.service.stageAid(f.staff, c.copy(), c.snapshot());
        assertNotEquals(originalRow, aidRow);
        assertEquals(before, f.rows.get(originalRow));
        var saved = f.service.load(f.rows.get(aidRow));
        assertTrue(saved.readingAid());
        assertEquals(B_ID, saved.pairedOriginalDeliveryId());
        assertArrayEquals(c.snapshot().encode(), saved.encode());
        assertEquals(2, f.originals.size());
    }

    @Test void shouldAcceptAnAid_whenItsOriginalIsAlreadyPickedUp() {
        var b = f.original(B_ID, 11); long originalRow = f.service.stageOriginal(f.staff, b.copy(), b.snapshot());
        f.rows.put(originalRow, withState(f.rows.get(originalRow), "picked_up"));
        var c = f.aid(C_ID, 12, b);
        assertTrue(f.service.stageAid(f.staff, c.copy(), c.snapshot()) > 0);
    }

    @Test void shouldRefuseAnAid_whenItsOriginalIsAbsentOrNotLive() {
        var b = f.original(B_ID, 11); var c = f.aid(C_ID, 12, b);
        assertThrows(IllegalStateException.class, () -> f.service.stageAid(f.staff, c.copy(), c.snapshot()));
        assertTrue(f.rows.isEmpty()); assertEquals(0, f.rowInserts);
        long originalRow = f.service.stageOriginal(f.staff, b.copy(), b.snapshot());
        for (String dead : List.of("fenced", "error", "removed", "expired")) {
            f.rows.put(originalRow, withState(f.rows.get(originalRow), dead));
            assertThrows(IllegalStateException.class, () -> f.service.stageAid(f.staff, c.copy(), c.snapshot()), dead);
        }
        assertEquals(1, f.rowInserts); assertEquals(1, f.originalInserts);
    }

    @Test void shouldRefuseAnAid_whoseClaimsOrScopeDoNotMatchTheOriginal() {
        var b = f.original(B_ID, 11); f.service.stageOriginal(f.staff, b.copy(), b.snapshot());
        var other = f.original(D_ID, 13);
        for (var c : List.of(
                f.aid(C_ID, 12, b, RECIPIENT, SIGNER, RAW_MAPPING, f.deviceKeys, CREATED + 3600, other.snapshot().documentSha256()),
                f.aid(C_ID, 12, b, RECIPIENT, SIGNER, RAW_MAPPING, f.deviceKeys, CREATED + 7200, b.snapshot().documentSha256()),
                f.aid(C_ID, 12, b, RECIPIENT, SIGNER, base64(marker(9)), f.deviceKeys, CREATED + 3600, b.snapshot().documentSha256()),
                f.aid(C_ID, 12, b, RECIPIENT, base64(marker(8)), RAW_MAPPING, f.deviceKeys, CREATED + 3600, b.snapshot().documentSha256()),
                f.aid(C_ID, 12, b, RECIPIENT, SIGNER, RAW_MAPPING, keys(21, 22), CREATED + 3600, b.snapshot().documentSha256()))) {
            assertThrows(RuntimeException.class, () -> f.service.stageAid(f.staff, c.copy(), c.snapshot()));
        }
        assertEquals(1, f.rowInserts); assertEquals(1, f.originalInserts);
    }

    @Test void shouldReturnTheSameAidRow_onExactRetry_andRefuseChangedBytes() {
        var b = f.original(B_ID, 11); f.service.stageOriginal(f.staff, b.copy(), b.snapshot());
        var c = f.aid(C_ID, 12, b);
        long first = f.service.stageAid(f.staff, c.copy(), c.snapshot());
        assertEquals(first, f.service.stageAid(f.staff, c.copy(), c.snapshot()));
        assertEquals(2, f.rowInserts);
        var changed = f.aid(C_ID, 13, b);
        assertThrows(PortalMyCarlosDeliveryOutboxService.ImmutableConflict.class,
                () -> f.service.stageAid(f.staff, changed.copy(), changed.snapshot()));
        assertEquals(2, f.rowInserts);
    }

    @Test void shouldNeverTouchTheAid_whenTheOriginalRetriesOrIsStagedAgain() {
        var b = f.original(B_ID, 11); var c = f.aid(C_ID, 12, b);
        long originalRow = f.service.stageOriginal(f.staff, b.copy(), b.snapshot());
        long aidRow = f.service.stageAid(f.staff, c.copy(), c.snapshot());
        var aidBefore = f.rows.get(aidRow);
        assertEquals(originalRow, f.service.stageOriginal(f.staff, b.copy(), b.snapshot()));
        assertEquals(aidBefore, f.rows.get(aidRow));
        assertEquals(2, f.rowInserts);
    }

    @Test void shouldRefuseToStageAnAidAsAnOriginal_andAnOriginalAsAnAid() {
        var b = f.original(B_ID, 11); var c = f.aid(C_ID, 12, b);
        assertThrows(IllegalArgumentException.class, () -> f.service.stage(f.staff, c.copy(), c.snapshot()));
        assertThrows(IllegalArgumentException.class, () -> f.service.stageOriginal(f.staff, c.copy(), c.snapshot()));
        assertThrows(IllegalArgumentException.class, () -> f.service.stageAid(f.staff, b.copy(), b.snapshot()));
        assertEquals(0, f.owners);
    }

    @Test void shouldStageTheOriginalAndKeepItWhenItsAidIsRefused() {
        var b = f.original(B_ID, 11);
        var tooLate = f.aid(C_ID, 12, b, RECIPIENT, SIGNER, RAW_MAPPING, f.deviceKeys, CREATED + 7200, b.snapshot().documentSha256());
        var result = f.service.stageWithAid(f.staff, b.copy(), b.snapshot(), tooLate.copy(), tooLate.snapshot());
        assertEquals(PortalMyCarlosDeliveryOutboxService.AidResult.REFUSED, result.aid());
        assertEquals(PortalMyCarlosDeliveryOutboxService.AidRefusal.CONFLICT, result.reason());
        assertEquals(1, f.rows.size());
        assertEquals("staged", f.rows.get(result.originalRowId()).state());
    }

    @Test void shouldStageOriginalAlone_orWithItsAid_inSeparateOwners() {
        var b = f.original(B_ID, 11);
        var alone = f.service.stageWithAid(f.staff, b.copy(), b.snapshot(), null, null);
        assertEquals(PortalMyCarlosDeliveryOutboxService.AidResult.NOT_REQUESTED, alone.aid());
        assertEquals(1, f.owners);
        var c = f.aid(C_ID, 12, b);
        var both = f.service.stageWithAid(f.staff, b.copy(), b.snapshot(), c.copy(), c.snapshot());
        assertEquals(PortalMyCarlosDeliveryOutboxService.AidResult.STAGED, both.aid());
        assertEquals(alone.originalRowId(), both.originalRowId());
        assertEquals(3, f.owners);
        assertEquals(2, f.rows.size());
    }

    @Test void shouldFenceTheAid_butNotTheOriginal_whenTheOriginalIsFenced() {
        var b = f.original(B_ID, 11); var c = f.aid(C_ID, 12, b);
        long originalRow = f.service.stageOriginal(f.staff, b.copy(), b.snapshot());
        long aidRow = f.service.stageAid(f.staff, c.copy(), c.snapshot());
        f.rows.put(originalRow, withState(f.rows.get(originalRow), "fenced"));
        assertNull(f.service.claim(aidRow));
        assertEquals("fenced", f.rows.get(aidRow).state());
    }

    @Test void shouldLeaveTheOriginalClaimable_whenOnlyTheAidIsFenced() {
        var b = f.original(B_ID, 11); var c = f.aid(C_ID, 12, b);
        long originalRow = f.service.stageOriginal(f.staff, b.copy(), b.snapshot());
        long aidRow = f.service.stageAid(f.staff, c.copy(), c.snapshot());
        f.rows.put(aidRow, withState(f.rows.get(aidRow), "fenced"));
        assertNotNull(f.service.claim(originalRow));
        assertEquals("fenced", f.rows.get(aidRow).state());
    }

    @Test void shouldRetryTheAidOnItsOwn_withoutTouchingTheOriginalRow() {
        var b = f.original(B_ID, 11); var c = f.aid(C_ID, 12, b);
        long originalRow = f.service.stageOriginal(f.staff, b.copy(), b.snapshot());
        long aidRow = f.service.stageAid(f.staff, c.copy(), c.snapshot());
        var originalBefore = f.rows.get(originalRow);
        when(f.client.upload(anyInt(), anyString(), anyString(), any(byte[].class)))
                .thenThrow(PatientPortalException.ofTransportFailure("/x/{id}", new java.io.IOException("down")));
        assertEquals(PortalMyCarlosDeliveryOutboxService.SendResult.RETRY_PENDING, f.service.send(aidRow));
        assertEquals("staged", f.rows.get(aidRow).state());
        assertTrue(f.rows.get(aidRow).retryAfter() > CREATED);
        assertEquals(originalBefore, f.rows.get(originalRow));
        // The retry re-sends the aid's own retained bytes, not the original's.
        verify(f.client).upload(eq(123), eq(C_ID), eq(f.deviceKeys.kid()), eq(c.snapshot().body().copy()));
        verifyNoMoreInteractions(f.client);
    }

    @Test void shouldRefuseAnAid_whoseSignedOriginalIdIsNotTheSnapshotsPairedOriginal() {
        var b = f.original(B_ID, 11); f.service.stageOriginal(f.staff, b.copy(), b.snapshot());
        // The manifest names another original than the snapshot does: the device would file under one
        // relation and CARLOS fence by the other.
        var c = f.aid(C_ID, 12, b, RECIPIENT, SIGNER, RAW_MAPPING, f.deviceKeys, CREATED + 3600,
                b.snapshot().documentSha256(), D_ID);
        assertThrows(IllegalArgumentException.class, () -> f.service.stageAid(f.staff, c.copy(), c.snapshot()));
        assertEquals(1, f.rowInserts);
    }

    @Test void shouldKeepTheOriginalAndReportWhy_whenTheAidHalfFailsUnexpectedly() {
        var b = f.original(B_ID, 11); var c = f.aid(C_ID, 12, b);
        f.failSecondInsert = true;
        var result = f.service.stageWithAid(f.staff, b.copy(), b.snapshot(), c.copy(), c.snapshot());
        assertEquals(PortalMyCarlosDeliveryOutboxService.AidResult.FAILED, result.aid());
        assertEquals(PortalMyCarlosDeliveryOutboxService.AidRefusal.UNEXPECTED, result.reason());
        assertEquals(0, result.readingAidRowId());
        assertEquals("staged", f.rows.get(result.originalRowId()).state());
        assertEquals(1, f.rows.size());
    }

    @Test void shouldRefuseASecondAidWithTheSameContent_butAllowAChangedOneOrOneAfterTheFirstDied() {
        var b = f.original(B_ID, 11); f.service.stageOriginal(f.staff, b.copy(), b.snapshot());
        var first = f.aid(C_ID, 12, b);
        long firstRow = f.service.stageAid(f.staff, first.copy(), first.snapshot());
        // A double submit: the same aid under a fresh delivery ID.
        var twin = f.aid(D_ID, 12, b);
        assertThrows(PortalMyCarlosDeliveryOutboxService.ImmutableConflict.class,
                () -> f.service.stageAid(f.staff, twin.copy(), twin.snapshot()));
        assertEquals(2, f.rowInserts);
        // A changed aid is a newer review: staged; the device decides by the signed review time.
        var changed = f.aid(D_ID, 13, b);
        assertTrue(f.service.stageAid(f.staff, changed.copy(), changed.snapshot()) > 0);
        // Once the first aid died, an aid with its content is allowed again.
        var third = f.aid("00000000-0000-4000-8000-000000000004", 12, b);
        f.rows.put(firstRow, withState(f.rows.get(firstRow), "error"));
        assertTrue(f.service.stageAid(f.staff, third.copy(), third.snapshot()) > 0);
    }

    @Test void shouldFenceTheAidAtClaim_whenItsOriginalIsErroredExpiredOrRemoved() {
        var b = f.original(B_ID, 11); var c = f.aid(C_ID, 12, b);
        long originalRow = f.service.stageOriginal(f.staff, b.copy(), b.snapshot());
        long aidRow = f.service.stageAid(f.staff, c.copy(), c.snapshot());
        for (String dead : List.of("error", "expired", "removed")) {
            f.rows.put(aidRow, withState(f.rows.get(aidRow), "staged"));
            f.rows.put(originalRow, withState(f.rows.get(originalRow), dead));
            assertNull(f.service.claim(aidRow), dead);
            assertEquals("fenced", f.rows.get(aidRow).state(), dead);
        }
    }

    @Test void shouldRefuseSnapshotsWhoseKindAndPairingDisagree() {
        var b = f.original(B_ID, 11); var c = f.aid(C_ID, 12, b);
        var s = c.snapshot();
        // A kind in the outer record without a named original, and a named original without the kind.
        assertThrows(IllegalArgumentException.class, () -> rebuilt(s, null, s.body()));
        assertThrows(IllegalArgumentException.class, () -> rebuilt(s, B_ID, b.snapshot().body()));
        assertThrows(IllegalArgumentException.class, () -> rebuilt(s, C_ID, s.body()));
        assertThrows(IllegalArgumentException.class, () -> new PortalMyCarlosDeliverySnapshot(s.clinicId(), s.demographicNo(),
                s.recipientId(), s.publicBase(), s.internalTarget(), s.configSha256(), s.acceptedDeviceId(), s.acceptanceVersion(),
                s.acceptanceSha256(), s.deliveryId(), s.device(), s.signerKid(), s.documentSha256(), s.ciphertextSha256(),
                s.createdAt(), s.expiresAt(), s.bodySha256(), s.body(), null, B_ID));
        assertEquals(s, PortalMyCarlosDeliverySnapshot.decode(s.encode()));
        assertNull(PortalMyCarlosDeliverySnapshot.decode(b.snapshot().encode()).pairedOriginalDeliveryId());
    }

    private static PortalMyCarlosDeliverySnapshot rebuilt(PortalMyCarlosDeliverySnapshot s, String paired, Bytes body) {
        return new PortalMyCarlosDeliverySnapshot(s.clinicId(), s.demographicNo(), s.recipientId(), s.publicBase(),
                s.internalTarget(), s.configSha256(), s.acceptedDeviceId(), s.acceptanceVersion(), s.acceptanceSha256(),
                s.deliveryId(), s.device(), s.signerKid(), s.documentSha256(), s.ciphertextSha256(), s.createdAt(),
                s.expiresAt(), s.bodySha256(), body, s.sourceDocumentId(), paired);
    }

    @Test void shouldRefuseAmbientTransactionAndUnauthorizedStaff_beforeOwningOrWriting() {
        var b = f.original(B_ID, 11); var c = f.aid(C_ID, 12, b);
        assertThrows(SecurityException.class, () -> f.service.stageAid(null, c.copy(), c.snapshot()));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThrows(IllegalStateException.class, () -> f.service.stageAid(f.staff, c.copy(), c.snapshot())); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        assertEquals(0, f.owners); verifyNoInteractions(f.outbox);
    }

    private record Identity(String clinic, String delivery) { }
    private record Intent(PortalMyCarlosDeliveryComposer.Copy copy, PortalMyCarlosDeliverySnapshot snapshot) { }

    private static final class Fixture {
        final PortalMyCarlosDeviceApprovalTransactions transactions = mock(PortalMyCarlosDeviceApprovalTransactions.class);
        final PortalMyCarlosPatientBindingDao bindings = mock(PortalMyCarlosPatientBindingDao.class);
        final PortalMyCarlosAcceptedDeviceDao devices = mock(PortalMyCarlosAcceptedDeviceDao.class);
        final PortalMyCarlosDeliveryOutboxDao outbox = mock(PortalMyCarlosDeliveryOutboxDao.class);
        final PortalMyCarlosDeliveryClient client = mock(PortalMyCarlosDeliveryClient.class);
        final PortalMyCarlosPatientBinding binding = mock(PortalMyCarlosPatientBinding.class);
        final PortalMyCarlosAcceptedDevice accepted = mock(PortalMyCarlosAcceptedDevice.class);
        final PatientPortalClinicKeysPublishRequest.Key signingKey = mock(PatientPortalClinicKeysPublishRequest.Key.class);
        final PortalMyCarlosDeliveryCipher cipher = new PortalMyCarlosDeliveryCipher(1, Map.of(1, new byte[32]));
        final DeviceKeys deviceKeys = keys(1, 2);
        final LoggedInInfo staff = staff();
        final Map<Identity, Original> originals = new LinkedHashMap<>();
        final Map<Long, Row> rows = new LinkedHashMap<>();
        final IllegalStateException injectedFailure = new IllegalStateException("synthetic second outbox insert failure");
        final Current cfg;
        final PortalMyCarlosDeliveryOutboxService service;
        boolean owned, failSecondInsert;
        int owners, originalInserts, rowInserts;
        long nextOriginalId = 100, nextRowId = 200;
        List<Integer> stateAtFailure;

        Fixture() {
            var settings = mock(PatientPortalSettings.class);
            when(settings.clinicId()).thenReturn(CLINIC); when(settings.baseUrl()).thenReturn(PUBLIC_BASE);
            cfg = Current.fromValidated(settings, PUBLIC_BASE, true, true);
            var security = mock(SecurityInfoManager.class);
            when(security.hasPrivilege(any(), anyString(), anyString(), anyString())).thenReturn(true);
            when(security.isAllowedAccessToPatientRecord(any(), anyInt())).thenReturn(true);
            var portal = mock(PatientPortalService.class);
            when(portal.myCarlosClinicId()).thenReturn(CLINIC);
            when(portal.myCarlosDeliveryTarget(123)).thenReturn(URI.create(TARGET));
            when(binding.id()).thenReturn(11L); when(binding.recipientId()).thenReturn(RECIPIENT);
            when(binding.publicBase()).thenReturn(PUBLIC_BASE); when(binding.configHash()).thenReturn(cfg.hash());
            when(binding.internalOrigin()).thenReturn(cfg.internalOrigin);
            when(accepted.bindingId()).thenReturn(11L); when(accepted.id()).thenReturn(7L);
            when(accepted.version()).thenReturn(4L); when(accepted.current()).thenReturn(true);
            when(accepted.x25519()).thenReturn(deviceKeys.x25519()); when(accepted.ed25519()).thenReturn(deviceKeys.ed25519());
            when(accepted.acceptanceHash()).thenReturn(ACCEPTANCE_HASH);
            when(bindings.findByClinicPatientForUpdate(CLINIC, 123)).thenAnswer(call -> { requireOwner(); return Optional.of(binding); });
            when(devices.findByKidForUpdate(deviceKeys.kid())).thenAnswer(call -> { requireOwner(); return Optional.of(accepted); });
            var inventory = mock(PortalMyCarlosKeyInventory.class);
            var publication = mock(PatientPortalClinicKeysPublishRequest.class);
            when(inventory.clinicId()).thenReturn(CLINIC); when(inventory.publication()).thenReturn(publication);
            when(signingKey.kid()).thenReturn(SIGNER); when(signingKey.status()).thenReturn("active");
            when(publication.keys()).thenReturn(List.of(signingKey));
            doAnswer(call -> {
                assertFalse(owned, "nested transaction owner"); owners++; owned = true;
                var priorOriginals = new LinkedHashMap<>(originals); var priorRows = new LinkedHashMap<>(rows);
                try {
                    Function<Optional<PortalMyCarlosKeyInventory>, Object> operation = call.getArgument(0);
                    return operation.apply(Optional.of(inventory));
                } catch (RuntimeException | Error failure) {
                    originals.clear(); originals.putAll(priorOriginals); rows.clear(); rows.putAll(priorRows);
                    throw failure;
                } finally { owned = false; }
            }).when(transactions).withSigningInventory(any());
            when(outbox.deviceFenced(anyString(), anyString())).thenAnswer(call -> { requireOwner(); return false; });
            when(outbox.original(anyString(), anyString())).thenAnswer(call -> {
                requireOwner(); return Optional.ofNullable(originals.get(new Identity(call.getArgument(0), call.getArgument(1))));
            });
            when(outbox.find(anyString(), anyString(), anyString())).thenAnswer(call -> {
                requireOwner(); return rows.values().stream().filter(row -> row.clinicId().equals(call.getArgument(0))
                        && row.deliveryId().equals(call.getArgument(1)) && row.deviceKid().equals(call.getArgument(2))).findFirst();
            });
            when(outbox.findForDelivery(anyString(), anyString())).thenAnswer(call -> {
                requireOwner(); return rows.values().stream().filter(row -> row.clinicId().equals(call.getArgument(0))
                        && row.deliveryId().equals(call.getArgument(1))).limit(2).toList();
            });
            when(outbox.insertOriginal(anyString(), anyString(), anyString(), any(byte[].class), anyLong())).thenAnswer(call -> {
                requireOwner(); var key = new Identity(call.getArgument(0), call.getArgument(1));
                assertFalse(originals.containsKey(key), "duplicate original identity");
                long id = nextOriginalId++; originalInserts++;
                originals.put(key, new Original(id, call.getArgument(2), new Bytes(call.getArgument(3)))); return id;
            });
            when(outbox.insert(anyLong(), any(PortalMyCarlosDeliverySnapshot.class), any(byte[].class), anyString())).thenAnswer(call -> {
                requireOwner(); rowInserts++;
                if (failSecondInsert && rowInserts == 2) {
                    stateAtFailure = List.of(originals.size(), rows.size()); throw injectedFailure;
                }
                PortalMyCarlosDeliverySnapshot s = call.getArgument(1);
                assertTrue(rows.values().stream().noneMatch(row -> row.clinicId().equals(s.clinicId())
                        && row.deliveryId().equals(s.deliveryId()) && row.deviceKid().equals(s.device().kid())), "duplicate outbox identity");
                long id = nextRowId++;
                rows.put(id, new Row(id, call.getArgument(0), s.clinicId(), s.deliveryId(), s.device().kid(),
                        s.demographicNo(), s.acceptedDeviceId(), s.acceptanceVersion(), s.configSha256(),
                        new Bytes(call.getArgument(2)), call.getArgument(3), "staged", null, null, 0, 0, null, null, null));
                return id;
            });
            when(outbox.find(anyLong())).thenAnswer(call -> Optional.ofNullable(rows.get((Long) call.getArgument(0))));
            doAnswer(call -> { Row row = call.getArgument(0); rows.put(row.id(), withState(row, call.getArgument(1))); return null; })
                    .when(outbox).terminal(any(Row.class), anyString());
            doAnswer(call -> {
                Row row = call.getArgument(0);
                rows.put(row.id(), new Row(row.id(), row.originalId(), row.clinicId(), row.deliveryId(), row.deviceKid(), row.demographicNo(),
                        row.acceptedDeviceId(), row.acceptanceVersion(), row.configHash(), row.encryptedSnapshot(), row.snapshotHash(),
                        row.state(), call.getArgument(1), call.getArgument(2), row.version() + 1, row.retryAfter(), null, null, null));
                return null;
            }).when(outbox).claim(any(Row.class), anyString(), anyLong(), anyLong());
            when(outbox.finish(anyLong(), anyString(), anyLong(), anyString(), anyLong(), any())).thenAnswer(call -> {
                Row row = rows.get((Long) call.getArgument(0));
                rows.put(row.id(), new Row(row.id(), row.originalId(), row.clinicId(), row.deliveryId(), row.deviceKid(), row.demographicNo(),
                        row.acceptedDeviceId(), row.acceptanceVersion(), row.configHash(), row.encryptedSnapshot(), row.snapshotHash(),
                        call.getArgument(3), null, null, row.version() + 1, call.getArgument(4), null, null, null));
                return true;
            });
            when(outbox.historyReferences(anyString(), anyInt(), anyLong(), anyInt())).thenAnswer(call -> {
                requireOwner(); long requested = call.getArgument(2); long before = requested == 0L ? Long.MAX_VALUE : requested;
                return rows.values().stream().filter(row -> row.id() < before).sorted((x, y) -> Long.compare(y.id(), x.id()))
                        .limit((int) call.getArgument(3))
                        .map(row -> new PortalMyCarlosDeliveryOutboxDao.Reference(row.id(), row.deliveryId(), row.deviceKid())).toList();
            });
            when(transactions.withApprovalState(any())).thenAnswer(call -> {
                boolean outer = owned; owned = true;
                try { return ((java.util.function.Supplier<?>) call.getArgument(0)).get(); }
                finally { owned = outer; }
            });
            service = new PortalMyCarlosDeliveryOutboxService(transactions, bindings, devices, outbox, cipher,
                    () -> cfg, portal, client, security, Clock.fixed(Instant.ofEpochSecond(CREATED), ZoneOffset.UTC));
        }

        private void requireOwner() { assertTrue(owned, "DAO operation outside the single owner"); }
        Intent original(String id, int marker) { return intent(id, marker, RECIPIENT, SIGNER, RAW_MAPPING, deviceKeys); }
        Intent aid(String id, int marker, Intent original) {
            return aid(id, marker, original, RECIPIENT, SIGNER, RAW_MAPPING, deviceKeys, CREATED + 3600, original.snapshot().documentSha256());
        }
        /** An aid whose signed manifest claims name {@code claimedHash} for its original, with its own lifetime. */
        Intent aid(String id, int marker, Intent original, String recipient, String signer, String source, DeviceKeys keys,
                long expires, String claimedHash) {
            return aid(id, marker, original, recipient, signer, source, keys, expires, claimedHash, original.snapshot().deliveryId());
        }
        Intent aid(String id, int marker, Intent original, String recipient, String signer, String source, DeviceKeys keys,
                long expires, String claimedHash, String claimedOriginalId) {
            String claims = "{\"pair\":{\"original_delivery_id\":\"" + claimedOriginalId
                    + "\",\"original_document_sha256\":\"" + claimedHash + "\"}}";
            String compact = base64(claims.getBytes(StandardCharsets.US_ASCII)) + "." + base64(new byte[64]);
            return build(id, marker, recipient, signer, source, keys, expires, "reading-aid/1",
                    original.snapshot().deliveryId(), compact, ("FAKE reading aid JSON " + marker).getBytes(StandardCharsets.US_ASCII));
        }
        Intent intent(String id, int marker, String recipient, String signer, String source, DeviceKeys keys) {
            return build(id, marker, recipient, signer, source, keys, CREATED + 3600, null, null, "FAKE unverified manifest",
                    ("FAKE opaque PDF marker " + marker).getBytes(StandardCharsets.US_ASCII));
        }
        private Intent build(String id, int marker, String recipient, String signer, String source, DeviceKeys keys,
                long expires, String kind, String paired, String manifest, byte[] pdf) {
            byte[] sealed = new byte[49]; sealed[0] = (byte) marker;
            String ciphertextHash = PortalMyCarlosDeviceApprovalCrypto.sha256(sealed);
            var outer = new LinkedHashMap<String, Object>();
            outer.put("v", 1L); outer.put("suite", Map.of("kem_id", 32L, "kdf_id", 1L, "aead_id", 3L));
            outer.put("delivery_id", id); outer.put("recipient_id", recipient); outer.put("device_key_id", keys.kid());
            outer.put("size", (long) sealed.length); outer.put("ciphertext_sha256", ciphertextHash);
            outer.put("created_at", CREATED); outer.put("expires_at", expires);
            if (kind != null) outer.put("kind", kind);
            byte[] body = PortalMyCarlosDeviceApprovalCrypto.frame(PortalMyCarlosDeliveryCodec.canonical(outer), sealed);
            String documentHash = PortalMyCarlosDeviceApprovalCrypto.sha256(pdf);
            String bodyHash = PortalMyCarlosDeviceApprovalCrypto.sha256(body);
            var copy = new PortalMyCarlosDeliveryComposer.Copy(id, recipient, keys, signer, CREATED, expires,
                    new Bytes(pdf), documentHash, manifest, new Bytes(sealed), new Bytes(body), ciphertextHash, bodyHash);
            var snapshot = new PortalMyCarlosDeliverySnapshot(CLINIC, 123, recipient, PUBLIC_BASE, TARGET,
                    HexFormat.of().formatHex(cfg.hash().copy()), 7, 4, HexFormat.of().formatHex(ACCEPTANCE_HASH.copy()),
                    id, keys, signer, documentHash, ciphertextHash, CREATED, expires, bodyHash, new Bytes(body), source, paired);
            return new Intent(copy, snapshot);
        }
    }

    private static Row withState(Row row, String state) {
        return new Row(row.id(), row.originalId(), row.clinicId(), row.deliveryId(), row.deviceKid(), row.demographicNo(),
                row.acceptedDeviceId(), row.acceptanceVersion(), row.configHash(), row.encryptedSnapshot(), row.snapshotHash(),
                state, row.leaseId(), row.leaseUntil(), row.version(), row.retryAfter(), row.encryptedReceipt(), row.receiptHash(), row.receiptJti());
    }
    private static Row withDevice(Row row, long id, String kid) {
        return new Row(id, row.originalId(), row.clinicId(), row.deliveryId(), kid, row.demographicNo(),
                row.acceptedDeviceId(), row.acceptanceVersion(), row.configHash(), row.encryptedSnapshot(), row.snapshotHash(),
                row.state(), row.leaseId(), row.leaseUntil(), row.version(), row.retryAfter(), row.encryptedReceipt(), row.receiptHash(), row.receiptJti());
    }
    private static byte[] marker(int value) { byte[] bytes = new byte[32]; bytes[0] = (byte) value; return bytes; }
    private static String base64(byte[] bytes) { return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
    private static DeviceKeys keys(int x, int e) {
        return new DeviceKeys(PortalMyCarlosDeviceApprovalCrypto.deviceKeyId(marker(x), marker(e)), new Bytes(marker(x)), new Bytes(marker(e)));
    }
    private static LoggedInInfo staff() {
        var staff = new LoggedInInfo(); var provider = new Provider(); provider.setProviderNo("FAKE");
        var security = new Security(); security.setSecurityNo(1);
        staff.setLoggedInProvider(provider); staff.setLoggedInSecurity(security); return staff;
    }
}
