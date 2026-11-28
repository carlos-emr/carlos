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
 * Synthetic transaction-owner contract only. Opaque markers are not PDFs, signatures or verified
 * capabilities. The in-memory owner explicitly simulates rollback; real MariaDB rollback,
 * uniqueness, concurrency and crash/recovery proof is still required before wiring this seam.
 */
@Tag("unit")
class PortalMyCarlosDeliveryPairStageUnitTest {
    private static final String B_ID = "00000000-0000-4000-8000-000000000001";
    private static final String C_ID = "00000000-0000-4000-8000-000000000002";
    private static final String CLINIC = "clinic-a";
    private static final String RECIPIENT = "recipient-a";
    private static final String PUBLIC_BASE = "https://portal.example.test";
    private static final String TARGET = PUBLIC_BASE + "/internal/carlos/patients/123/mycarlos/deliveries";
    private static final long CREATED = 1792454400;
    private static final String SIGNER = base64(marker(3));
    private static final String RAW_MAPPING = base64(marker(4));
    private static final Bytes ACCEPTANCE_HASH = new Bytes(marker(5));
    private final Fixture f = new Fixture();

    @AfterEach void noProviderSend() { verifyNoInteractions(f.client); }

    @Test void shouldStageBothExactIntents_underOneExistingOwner() {
        var b = f.intent(B_ID, 11); var c = f.intent(C_ID, 12);
        var pair = f.stage(b, c);
        assertEquals(1, f.owners);
        assertEquals(2, f.originals.size());
        assertEquals(2, f.rows.size());
        assertNotEquals(pair.originalRowId(), pair.readingAidRowId());
        assertArrayEquals(b.snapshot().encode(), f.service.load(f.rows.get(pair.originalRowId())).encode());
        assertArrayEquals(c.snapshot().encode(), f.service.load(f.rows.get(pair.readingAidRowId())).encode());
        for (var intent : List.of(b, c)) {
            var s = intent.snapshot(); var saved = f.originals.get(new Identity(s.clinicId(), s.deliveryId()));
            assertArrayEquals(intent.copy().originalPdf().copy(), f.cipher.open(
                    new PortalMyCarlosDeliveryCipher.Context(s.clinicId(), s.deliveryId(), "", "original"),
                    saved.encrypted().copy()));
        }
    }

    @Test void shouldRollBackBothItemsInSimulatedOwner_whenSecondInsertFailsAfterPartialWork() {
        f.failSecondInsert = true;
        assertSame(f.injectedFailure, assertThrows(IllegalStateException.class,
                () -> f.stage(f.intent(B_ID, 11), f.intent(C_ID, 12))));
        assertEquals(1, f.owners);
        assertEquals(List.of(2, 1), f.stateAtFailure);
        assertEquals(Map.of(), f.originals);
        assertEquals(Map.of(), f.rows);
        assertFalse(f.owned);
    }

    @Test void shouldReturnSameRowsWithoutChangingCiphertext_whenBothItemsRetryExactly() {
        var b = f.intent(B_ID, 11); var c = f.intent(C_ID, 12);
        var first = f.stage(b, c);
        var originals = Map.copyOf(f.originals); var rows = Map.copyOf(f.rows);
        assertEquals(first, f.stage(b, c));
        assertEquals(2, f.owners);
        assertEquals(2, f.originalInserts); assertEquals(2, f.rowInserts);
        assertEquals(originals, f.originals); assertEquals(rows, f.rows);
    }

    @Test void shouldRefuseChangedOriginalPdf_underAnExistingDeliveryIdentity() {
        var b = f.intent(B_ID, 11); var c = f.intent(C_ID, 12); f.stage(b, c);
        var originals = Map.copyOf(f.originals); var rows = Map.copyOf(f.rows);
        assertThrows(PortalMyCarlosDeliveryOutboxService.ImmutableConflict.class,
                () -> f.stage(b, f.intent(C_ID, 13)));
        assertEquals(originals, f.originals); assertEquals(rows, f.rows);
        assertEquals(2, f.originalInserts); assertEquals(2, f.rowInserts);
    }

    @Test void shouldRefuseChangedSavedSnapshot_evenWithSamePdfAndDeliveryIdentity() {
        var b = f.intent(B_ID, 11); var c = f.intent(C_ID, 12); f.stage(b, c);
        var originals = Map.copyOf(f.originals); var rows = Map.copyOf(f.rows);
        var changed = f.intent(C_ID, 12, RECIPIENT, SIGNER, base64(marker(9)), f.deviceKeys);
        // A coherent pair with a different shared raw-source mapping is still not an exact retry.
        var changedB = f.intent(B_ID, 11, RECIPIENT, SIGNER, base64(marker(9)), f.deviceKeys);
        assertThrows(PortalMyCarlosDeliveryOutboxService.ImmutableConflict.class, () -> f.stage(changedB, changed));
        assertEquals(originals, f.originals); assertEquals(rows, f.rows);
    }

    @Test void shouldRefuseEitherPartialPreexistingItem_withoutAddingItsPartner() {
        var b = f.intent(B_ID, 11); var c = f.intent(C_ID, 12);
        f.service.stage(f.staff, c.copy(), c.snapshot());
        var originals = Map.copyOf(f.originals); var rows = Map.copyOf(f.rows);
        assertThrows(PortalMyCarlosDeliveryOutboxService.ImmutableConflict.class, () -> f.stage(b, c));
        assertEquals(originals, f.originals); assertEquals(rows, f.rows);
        f.originals.clear(); f.rows.clear();
        f.service.stage(f.staff, b.copy(), b.snapshot());
        originals = Map.copyOf(f.originals); rows = Map.copyOf(f.rows);
        assertThrows(PortalMyCarlosDeliveryOutboxService.ImmutableConflict.class, () -> f.stage(b, c));
        assertEquals(originals, f.originals); assertEquals(rows, f.rows);
    }

    @Test void shouldRefuseAnOrphanOriginal_beforeAnyPairInserts() {
        var b = f.intent(B_ID, 11); var c = f.intent(C_ID, 12);
        f.service.stage(f.staff, b.copy(), b.snapshot()); f.rows.clear();
        var originals = Map.copyOf(f.originals);
        assertThrows(PortalMyCarlosDeliveryOutboxService.ImmutableConflict.class, () -> f.stage(b, c));
        assertEquals(originals, f.originals); assertTrue(f.rows.isEmpty());
        assertEquals(1, f.originalInserts); assertEquals(1, f.rowInserts);
    }

    @Test void shouldRefuseIdentityAlreadyBoundToAnotherDevice_orToMultipleRows() {
        var b = f.intent(B_ID, 11); var c = f.intent(C_ID, 12); var pair = f.stage(b, c);
        var correct = f.rows.get(pair.originalRowId());
        var otherDevice = withDevice(correct, correct.id(), keys(21, 22).kid());
        f.rows.put(correct.id(), otherDevice);
        var originals = Map.copyOf(f.originals); var rows = Map.copyOf(f.rows);
        assertThrows(PortalMyCarlosDeliveryOutboxService.ImmutableConflict.class, () -> f.stage(b, c));
        assertEquals(originals, f.originals); assertEquals(rows, f.rows);
        f.rows.put(correct.id(), correct); f.rows.put(99L, withDevice(correct, 99, otherDevice.deviceKid()));
        rows = Map.copyOf(f.rows);
        assertThrows(PortalMyCarlosDeliveryOutboxService.ImmutableConflict.class, () -> f.stage(b, c));
        assertEquals(rows, f.rows); assertEquals(2, f.rowInserts);
    }

    @Test void shouldRefuseCrossRecipientSignerDeviceAndRawMapping_beforeEnteringOwner() {
        var b = f.intent(B_ID, 11);
        for (var c : List.of(
                f.intent(C_ID, 12, "recipient-b", SIGNER, RAW_MAPPING, f.deviceKeys),
                f.intent(C_ID, 12, RECIPIENT, base64(marker(8)), RAW_MAPPING, f.deviceKeys),
                f.intent(C_ID, 12, RECIPIENT, SIGNER, RAW_MAPPING, keys(21, 22)),
                f.intent(C_ID, 12, RECIPIENT, SIGNER, base64(marker(9)), f.deviceKeys))) {
            assertThrows(IllegalArgumentException.class, () -> f.stage(b, c));
        }
        assertEquals(0, f.owners); assertTrue(f.rows.isEmpty()); assertTrue(f.originals.isEmpty());
    }

    @Test void shouldRefuseDuplicateDeliveryIdAndMissingRawMapping_withoutEnteringOwner() {
        var b = f.intent(B_ID, 11);
        assertThrows(IllegalArgumentException.class, () -> f.stage(b, f.intent(B_ID, 12)));
        var legacy = f.intent(C_ID, 12, RECIPIENT, SIGNER, null, f.deviceKeys);
        assertThrows(IllegalArgumentException.class, () -> f.stage(b, legacy));
        assertEquals(0, f.owners);
    }

    @Test void shouldRefuseCopySnapshotMismatch_beforeEnteringOwner() {
        var b = f.intent(B_ID, 11); var c = f.intent(C_ID, 12); var saved = c.copy();
        var tampered = new PortalMyCarlosDeliveryComposer.Copy(saved.deliveryId(), saved.recipientId(), saved.device(),
                saved.signerKid(), saved.createdAt(), saved.expiresAt(), new Bytes("FAKE changed PDF".getBytes(StandardCharsets.US_ASCII)),
                saved.documentSha256(), saved.manifest(), saved.sealed(), saved.uploadBody(), saved.ciphertextSha256(), saved.bodySha256());
        assertThrows(IllegalArgumentException.class,
                () -> f.service.stagePair(f.staff, b.copy(), b.snapshot(), tampered, c.snapshot()));
        assertEquals(0, f.owners); assertEquals(0, f.originalInserts); assertEquals(0, f.rowInserts);
    }

    @Test void shouldRefuseCurrentRecipientOrConfigChange_beforeInsertingEitherItem() {
        var b = f.intent(B_ID, 11); var c = f.intent(C_ID, 12);
        when(f.binding.recipientId()).thenReturn("recipient-changed");
        assertThrows(IllegalStateException.class, () -> f.stage(b, c));
        when(f.binding.recipientId()).thenReturn(RECIPIENT);
        when(f.binding.configHash()).thenReturn(new Bytes(marker(23)));
        assertThrows(IllegalStateException.class, () -> f.stage(b, c));
        assertTrue(f.originals.isEmpty()); assertTrue(f.rows.isEmpty());
        assertEquals(0, f.originalInserts); assertEquals(0, f.rowInserts);
    }

    @Test void shouldRefuseRevokedOrChangedAcceptedKey_beforeInsertingEitherItem() {
        var b = f.intent(B_ID, 11); var c = f.intent(C_ID, 12);
        when(f.accepted.current()).thenReturn(false);
        assertThrows(IllegalStateException.class, () -> f.stage(b, c));
        when(f.accepted.current()).thenReturn(true);
        when(f.accepted.x25519()).thenReturn(new Bytes(marker(24)));
        assertThrows(IllegalStateException.class, () -> f.stage(b, c));
        assertEquals(0, f.originalInserts); assertEquals(0, f.rowInserts);
    }

    @Test void shouldFenceImmutableRetry_whenSignerIsNoLongerActive() {
        var b = f.intent(B_ID, 11); var c = f.intent(C_ID, 12); f.stage(b, c);
        var originals = Map.copyOf(f.originals); var rows = Map.copyOf(f.rows);
        when(f.signingKey.status()).thenReturn("revoked");
        assertThrows(IllegalStateException.class, () -> f.stage(b, c));
        assertEquals(originals, f.originals); assertEquals(rows, f.rows);
        assertEquals(2, f.originalInserts); assertEquals(2, f.rowInserts);
    }

    @Test void shouldKeepLegacySingleItemStagingIdempotent_withNoRawMapping() {
        var legacy = f.intent(B_ID, 11, RECIPIENT, SIGNER, null, f.deviceKeys);
        long id = f.service.stage(f.staff, legacy.copy(), legacy.snapshot());
        assertEquals(id, f.service.stage(f.staff, legacy.copy(), legacy.snapshot()));
        assertEquals(2, f.owners); assertEquals(1, f.originalInserts); assertEquals(1, f.rowInserts);
        assertNull(f.service.load(f.rows.get(id)).sourceDocumentId());
    }

    @Test void shouldRefuseAmbientTransactionAndUnauthorizedStaff_beforeOwningOrWriting() {
        var b = f.intent(B_ID, 11); var c = f.intent(C_ID, 12);
        assertThrows(SecurityException.class, () -> f.service.stagePair(null, b.copy(), b.snapshot(), c.copy(), c.snapshot()));
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try { assertThrows(IllegalStateException.class, () -> f.stage(b, c)); }
        finally { TransactionSynchronizationManager.setActualTransactionActive(false); }
        TransactionSynchronizationManager.initSynchronization();
        try { assertThrows(IllegalStateException.class, () -> f.stage(b, c)); }
        finally { TransactionSynchronizationManager.clearSynchronization(); }
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
            service = new PortalMyCarlosDeliveryOutboxService(transactions, bindings, devices, outbox, cipher,
                    () -> cfg, portal, client, security, Clock.fixed(Instant.ofEpochSecond(CREATED), ZoneOffset.UTC));
        }

        private void requireOwner() { assertTrue(owned, "DAO operation outside the single owner"); }
        PortalMyCarlosDeliveryOutboxService.StagedPair stage(Intent b, Intent c) {
            return service.stagePair(staff, b.copy(), b.snapshot(), c.copy(), c.snapshot());
        }
        Intent intent(String id, int marker) { return intent(id, marker, RECIPIENT, SIGNER, RAW_MAPPING, deviceKeys); }
        Intent intent(String id, int marker, String recipient, String signer, String source, DeviceKeys keys) {
            byte[] pdf = ("FAKE opaque PDF marker " + marker).getBytes(StandardCharsets.US_ASCII);
            byte[] sealed = new byte[49]; sealed[0] = (byte) marker;
            String ciphertextHash = PortalMyCarlosDeviceApprovalCrypto.sha256(sealed);
            var outer = new LinkedHashMap<String, Object>();
            outer.put("v", 1L); outer.put("suite", Map.of("kem_id", 32L, "kdf_id", 1L, "aead_id", 3L));
            outer.put("delivery_id", id); outer.put("recipient_id", recipient); outer.put("device_key_id", keys.kid());
            outer.put("size", (long) sealed.length); outer.put("ciphertext_sha256", ciphertextHash);
            outer.put("created_at", CREATED); outer.put("expires_at", CREATED + 3600);
            byte[] body = PortalMyCarlosDeviceApprovalCrypto.frame(PortalMyCarlosDeliveryCodec.canonical(outer), sealed);
            String documentHash = PortalMyCarlosDeviceApprovalCrypto.sha256(pdf);
            String bodyHash = PortalMyCarlosDeviceApprovalCrypto.sha256(body);
            var copy = new PortalMyCarlosDeliveryComposer.Copy(id, recipient, keys, signer, CREATED, CREATED + 3600,
                    new Bytes(pdf), documentHash, "FAKE unverified manifest", new Bytes(sealed), new Bytes(body), ciphertextHash, bodyHash);
            var snapshot = new PortalMyCarlosDeliverySnapshot(CLINIC, 123, recipient, PUBLIC_BASE, TARGET,
                    HexFormat.of().formatHex(cfg.hash().copy()), 7, 4, HexFormat.of().formatHex(ACCEPTANCE_HASH.copy()),
                    id, keys, signer, documentHash, ciphertextHash, CREATED, CREATED + 3600, bodyHash, new Bytes(body), source);
            return new Intent(copy, snapshot);
        }
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
