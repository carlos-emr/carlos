/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeliveryMariaIntegrationTest.*;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import jakarta.persistence.EntityManagerFactory;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Actual owning Maria transactions; network responses and accepted state are explicit FAKE fixtures. */
@Tag("integration")
class PortalMyCarlosDeliverySyncMariaIntegrationTest {
    private final List<Long> ownedFenceProperties = new ArrayList<>();
    @BeforeAll static void startDatabase() throws Exception { PortalMyCarlosDeliveryMariaIntegrationTest.start(); }
    @AfterAll static void closeDatabase() { PortalMyCarlosDeliveryMariaIntegrationTest.close(); }
    @AfterEach void restoreOwnedFixture() throws Exception {
        new PortalMyCarlosDeliveryMariaIntegrationTest().restoreOnlyOwnedAcceptanceFixture();
        for (long id : ownedFenceProperties) {
            try (var c = source.getConnection(); var q = c.prepareStatement("DELETE FROM property WHERE id=? AND name LIKE 'mycarlos.delivery.removed.%'")) {
                q.setLong(1, id); assertEquals(1, q.executeUpdate());
            }
        }
        try (var c = source.getConnection(); var q = c.prepareStatement("UPDATE mycarlos_delivery_checkpoint SET writer_id=NULL,writer_until=NULL WHERE clinic_id='clinic-a'")) { q.executeUpdate(); }
    }

    @Test void shouldCommitExactProofAndCheckpointBeforeAck_whenReceiptIsIndependentlyValid() throws Exception {
        var h = harness(); var p = pair(); h.sender.stage(staff(), p.copy(), p.snapshot());
        long before = checkpoint(h).through();
        String proof = receipt(p.snapshot(), UUID.randomUUID().toString());
        h.exchange.response = page(List.of(event(p.snapshot(), before + 1, proof)), before + 1);
        assertEquals(PortalMyCarlosDeliverySyncService.Result.COLLECTED, h.sync.poll());
        assertEquals(List.of(before + 1), h.exchange.acks);
        assertEquals(before + 1, checkpoint(h).acknowledged());
        assertEquals(PortalMyCarlosDeliveryOutboxService.DeliveryStatus.PICKED_UP,
                h.sender.status(staff(), 123, p.snapshot().deliveryId(), p.snapshot().device().kid()));
        assertExactPageRetained(h.exchange.response);
    }

    @Test void shouldKeepOnlyDurableContiguousPrefix_whenLaterEventHasWrongBinding() throws Exception {
        var h = harness(); var good = pair(); var bad = pair();
        h.sender.stage(staff(), good.copy(), good.snapshot()); h.sender.stage(staff(), bad.copy(), bad.snapshot());
        long before = checkpoint(h).through();
        var first = event(good.snapshot(), before + 1, receipt(good.snapshot(), UUID.randomUUID().toString()));
        var second = event(bad.snapshot(), before + 2, receipt(bad.snapshot(), UUID.randomUUID().toString()));
        second.put("recipient_id", "rcp-FAKE-other-patient");
        h.exchange.response = page(List.of(first, second), before + 2);
        assertEquals(PortalMyCarlosDeliverySyncService.Result.QUARANTINED, h.sync.poll());
        assertEquals(List.of(before + 1), h.exchange.acks);
        assertEquals(before + 1, checkpoint(h).through());
        assertEquals(PortalMyCarlosDeliveryOutboxService.DeliveryStatus.PICKED_UP,
                h.sender.status(staff(), 123, good.snapshot().deliveryId(), good.snapshot().device().kid()));
        assertEquals(PortalMyCarlosDeliveryOutboxService.DeliveryStatus.STAGED,
                h.sender.status(staff(), 123, bad.snapshot().deliveryId(), bad.snapshot().device().kid()));
        assertExactPageRetained(h.exchange.response);
        assertEquals("invalid_proof", pageFailure(h.exchange.response));
    }

    @Test void shouldQuarantineExactUnknownOrGappedEvent_withoutAdvancingOrAcknowledging() throws Exception {
        for (boolean gap : List.of(false, true)) {
            var h = harness(); var p = pair();
            if (gap) h.sender.stage(staff(), p.copy(), p.snapshot());
            long before = checkpoint(h).through();
            h.exchange.response = page(List.of(event(p.snapshot(), before + (gap ? 2 : 1),
                    receipt(p.snapshot(), UUID.randomUUID().toString()))), before + (gap ? 2 : 1));
            assertEquals(PortalMyCarlosDeliverySyncService.Result.QUARANTINED, h.sync.poll());
            assertTrue(h.exchange.acks.isEmpty());
            assertEquals(before, checkpoint(h).through());
            assertEquals(gap ? "event_gap" : "unknown_binding", pageFailure(h.exchange.response));
            assertExactPageRetained(h.exchange.response);
        }
    }

    @Test void shouldRetryOnlyCommittedAckAfterRestart_whenAckReplyIsLost() throws Exception {
        var h = harness(); var p = pair(); long id = h.sender.stage(staff(), p.copy(), p.snapshot());
        long before = checkpoint(h).through();
        h.exchange.response = page(List.of(event(p.snapshot(), before + 1,
                receipt(p.snapshot(), UUID.randomUUID().toString()))), before + 1);
        h.exchange.loseFirstAck = true;
        assertThrows(PatientPortalException.class, () -> h.sync.poll());
        assertEquals(before + 1, checkpoint(h).through());
        assertEquals(before, checkpoint(h).acknowledged());
        var retained = tx.withApprovalState(() -> dao.find(id).orElseThrow());
        NOW.addAndGet(61);
        var restarted = harness(h.exchange);
        assertEquals(PortalMyCarlosDeliverySyncService.Result.RECOVERED_ACK, restarted.sync.poll());
        assertEquals(1, h.exchange.gets);
        assertEquals(List.of(before + 1, before + 1), h.exchange.acks);
        assertEquals(retained, tx.withApprovalState(() -> dao.find(id).orElseThrow()));
        assertEquals(before + 1, checkpoint(h).acknowledged());
    }

    @Test void shouldRollBackPickupAndPage_whenEventInsertFailsBeforeCheckpointCommit() throws Exception {
        var h = harness(); var p = pair(); long id = h.sender.stage(staff(), p.copy(), p.snapshot());
        long before = checkpoint(h).through();
        var data = event(p.snapshot(), before + 1, receipt(p.snapshot(), UUID.randomUUID().toString()));
        h.exchange.response = page(List.of(data), before + 1);
        var parsed = PortalMyCarlosDeliveryClient.event(JSON.writeValueAsBytes(data));
        // Controlled fault: one exact future event row makes the later INSERT fail. The row is
        // owned by this test and removed by its exact key in finally; no unknown rows are touched.
        tx.withApprovalState(() -> {
            byte[] marker = "{\"FAKE\":\"injected duplicate event\"}".getBytes(StandardCharsets.UTF_8);
            String hash = PortalMyCarlosDeviceApprovalCrypto.sha256(marker);
            var cipher = new PortalMyCarlosDeliveryCipher(1, Map.of(1, new byte[32]));
            long pageId = h.events.keepPage("clinic-a", before, hash,
                    cipher.seal(new PortalMyCarlosDeliveryCipher.Context("clinic-a", hash, "", "event_page"), marker), NOW.get(), null);
            h.events.event("clinic-a", parsed, pageId, true); return null;
        });
        try {
            assertThrows(RuntimeException.class, () -> h.sync.poll());
            assertEquals(before, checkpoint(h).through()); assertTrue(h.exchange.acks.isEmpty());
            assertEquals("staged", tx.withApprovalState(() -> dao.find(id).orElseThrow().state()));
            assertNull(pageFailure(h.exchange.response)); // The new page transaction rolled back.
        } finally {
            try (var c = source.getConnection(); var q = c.prepareStatement("DELETE FROM mycarlos_delivery_event WHERE clinic_id='clinic-a' AND sequence=?")) {
                q.setLong(1, before + 1); assertEquals(1, q.executeUpdate());
            }
        }
    }

    @Test void shouldRetainMalformedPageAndHoldCursor_whenJsonSchemaCannotBeParsed() throws Exception {
        var h = harness(); long before = checkpoint(h).through();
        h.exchange.response = "{\"events\":[],\"last_sequence\":false,\"next_cursor\":null}";
        assertEquals(PortalMyCarlosDeliverySyncService.Result.QUARANTINED, h.sync.poll());
        assertTrue(h.exchange.acks.isEmpty()); assertEquals(before, checkpoint(h).through());
        assertEquals("invalid_page", pageFailure(h.exchange.response)); assertExactPageRetained(h.exchange.response);
    }

    @Test void shouldRefuseRepointedFeedAfterRestart_beforeAnyFetchOrAck() throws Exception {
        var original = harness();
        var document = pair(); original.sender.stage(staff(), document.copy(), document.snapshot());
        long initialThrough = checkpoint(original).through();
        original.exchange.response = page(List.of(event(document.snapshot(), initialThrough + 1,
                receipt(document.snapshot(), UUID.randomUUID().toString()))), initialThrough + 1);
        assertEquals(PortalMyCarlosDeliverySyncService.Result.COLLECTED, original.sync.poll());
        long before = checkpoint(original).through();
        assertEquals(initialThrough + 1, before);
        var target = PatientPortalSettings.fromProperties(Map.of(PatientPortalSettings.BASE_URL_KEY, "https://another-portal.example.test",
                PatientPortalSettings.CLINIC_ID_KEY, "clinic-a", PatientPortalSettings.SERVICE_TOKEN_KEY, "FAKE-service-token-only-00000001",
                PatientPortalSettings.STAFF_ASSERTION_KEY, PortalTestKeys.PRIVATE_KEY, PatientPortalSettings.STAFF_ASSERTION_KEY_ID, "FAKE-staff",
                PatientPortalSettings.CERTIFICATE_PINS_KEY, PortalTestKeys.UNUSED_TLS_PIN));
        current.set(PortalMyCarlosDeviceApprovalService.Current.fromValidated(target, "https://portal.example.test", true, true));
        var exchange = new SyncExchange(); var repointed = harness(exchange, target);
        assertThrows(IllegalStateException.class, () -> repointed.sync.poll());
        assertEquals(0, exchange.gets); assertTrue(exchange.acks.isEmpty());
        assertEquals(before, checkpoint(original).through());
    }

    @Test void shouldFenceFutureStaging_whenRemovedPortalDeviceHadNoQueuedCopies() throws Exception {
        var h = harness(); long before = checkpoint(h).through();
        String otherKid = "UfKEw1hvufYaSTpPEAC3iFECGAQJg1ULkDzYhZGsAVQ";
        try (var c = source.getConnection(); var q = c.prepareStatement("SELECT COUNT(*) FROM mycarlos_delivery_outbox WHERE device_key_id=?")) {
            q.setString(1, otherKid); try (var r = q.executeQuery()) { assertTrue(r.next()); assertEquals(0, r.getLong(1)); }
        }
        var removed = JSON.createObjectNode(); removed.put("sequence", Long.toString(before + 1)); removed.put("kind", "device_removed");
        removed.put("occurred_at", NOW.get()); removed.put("recipient_id", "rcp-FAKE0001aaaaaaaaaaaa"); removed.put("device_key_id", otherKid);
        removed.putNull("delivery_id"); removed.putObject("payload");
        h.exchange.response = page(List.of(removed), before + 1);
        assertEquals(PortalMyCarlosDeliverySyncService.Result.COLLECTED, h.sync.poll());
        try (var c = source.getConnection(); var q = c.prepareStatement("SELECT id FROM property WHERE name=?")) {
            q.setString(1, "mycarlos.delivery.removed." + otherKid);
            try (var r = q.executeQuery()) { assertTrue(r.next()); ownedFenceProperties.add(r.getLong(1)); }
        }
        var fixture = PortalMyCarlosDeliveryProducerUnitTest.fixture();
        ((com.fasterxml.jackson.databind.node.ObjectNode) fixture.at("/sender/manifest_claims")).put("delivery_id", UUID.randomUUID().toString());
        ((com.fasterxml.jackson.databind.node.ObjectNode) fixture.get("keys")).set("device", fixture.at("/keys/other_device").deepCopy());
        var copy = PortalMyCarlosDeliveryProducerUnitTest.compose(fixture);
        var snapshot = new PortalMyCarlosDeliverySnapshot("clinic-a", 123, copy.recipientId(), "https://portal.example.test",
                "https://portal.example.test/internal/carlos/patients/123/mycarlos/deliveries", credentials.get("config_sha256").textValue(),
                2, 0, credentials.get("acceptance_sha256").textValue(), copy.deliveryId(), copy.device(), copy.signerKid(), copy.documentSha256(),
                copy.ciphertextSha256(), copy.createdAt(), copy.expiresAt(), copy.bodySha256(), copy.uploadBody());
        assertThrows(IllegalStateException.class, () -> harness(h.exchange).sender.stage(staff(), copy, snapshot));
        assertEquals(0, count("mycarlos_delivery_outbox", snapshot.deliveryId()));
    }

    @Test void shouldVerifyBothOriginalProofs_whenFreshRequestAnchorsLaterHistoryHead() throws Exception {
        var h = harness(); var p = pair(); h.sender.stage(staff(), p.copy(), p.snapshot());
        long before = checkpoint(h).through();
        var data = event(p.snapshot(), before + 1, receipt(p.snapshot(), UUID.randomUUID().toString()));
        var payload = (com.fasterxml.jackson.databind.node.ObjectNode) data.get("payload");
        var envelope = PortalMyCarlosDeviceApprovalCodec.envelope(payload.get("request_assertion").textValue());
        var claims = (com.fasterxml.jackson.databind.node.ObjectNode) JSON.readTree(envelope.payload().copy());
        claims.put("vault_head_link", "c".repeat(64));
        payload.put("request_assertion", sign(JSON.writeValueAsBytes(claims)));
        h.exchange.response = page(List.of(data), before + 1);
        assertEquals(PortalMyCarlosDeliverySyncService.Result.COLLECTED, h.sync.poll());
        assertEquals(PortalMyCarlosDeliveryOutboxService.DeliveryStatus.PICKED_UP,
                h.sender.status(staff(), 123, p.snapshot().deliveryId(), p.snapshot().device().kid()));
        assertExactPageRetained(h.exchange.response);
    }

    record Harness(PortalMyCarlosDeliverySyncService sync, PortalMyCarlosDeliveryOutboxService sender,
                   PortalMyCarlosDeliveryEventDao events, SyncExchange exchange) {}
    static Harness harness() { return harness(new SyncExchange()); }
    static Harness harness(SyncExchange exchange) { return harness(exchange, settings); }
    static Harness harness(SyncExchange exchange, PatientPortalSettings target) {
        var portal = new PatientPortalService(target, exchange, PortalStaffAssertionSigner.from(target.staffAssertionPrivateKey()), () -> true);
        var job = PortalJobAssertionSigner.from(new PortalJobKey(PortalJob.DELIVERY_SYNC, PortalSecret.of(PortalTestKeys.JOB_PRIVATE_KEY), "FAKE-delivery-job"));
        var client = new PortalMyCarlosDeliveryClient(portal, job);
        var cipher = new PortalMyCarlosDeliveryCipher(1, Map.of(1, new byte[32]));
        var sender = new PortalMyCarlosDeliveryOutboxService(tx, bindings, devices, dao, cipher, current::get, portal, client, STAFF, CLOCK);
        var events = new PortalMyCarlosDeliveryEventDao(context.getBean(EntityManagerFactory.class));
        return new Harness(new PortalMyCarlosDeliverySyncService(tx, events, dao, sender, devices, bindings, client, cipher, current::get, CLOCK, context.getBean(io.github.carlos_emr.carlos.commn.dao.PropertyDao.class)), sender, events, exchange);
    }
    static PortalMyCarlosDeliveryEventDao.Checkpoint checkpoint(Harness h) { return tx.withApprovalState(() -> h.events.checkpoint("clinic-a")); }
    static com.fasterxml.jackson.databind.node.ObjectNode event(PortalMyCarlosDeliverySnapshot s, long sequence, String receipt) throws Exception {
        String assertion = sign(PortalMyCarlosDeliveryCodec.canonical(Map.of("aud", "carlos-patient-portal-device-api", "iss", "mycarlos",
                "iat", NOW.get(), "exp", NOW.get() + 60, "jti", UUID.randomUUID().toString(), "kid", s.device().kid(),
                "recipient_id", s.recipientId(), "request_hash", "b".repeat(64), "vault_head_link", "a".repeat(64))));
        var e = JSON.createObjectNode(); e.put("sequence", Long.toString(sequence)); e.put("kind", "picked_up");
        e.put("occurred_at", NOW.get()); e.put("recipient_id", s.recipientId()); e.put("device_key_id", s.device().kid()); e.put("delivery_id", s.deliveryId());
        var p = e.putObject("payload"); p.put("ciphertext_sha256", s.ciphertextSha256()); p.put("size", s.sealedSize());
        p.put("created_at", s.createdAt()); p.put("expires_at", s.expiresAt()); p.put("receipt", receipt); p.put("request_assertion", assertion);
        return e;
    }
    static String page(List<?> events, long last) throws Exception { return JSON.writeValueAsString(Map.of("events", events, "last_sequence", Long.toString(last)))
            .replaceFirst("}$", ",\"next_cursor\":null}"); }
    static String pageFailure(String exact) throws Exception {
        String hash = PortalMyCarlosDeviceApprovalCrypto.sha256(exact.getBytes(StandardCharsets.UTF_8));
        try (var c = source.getConnection(); var q = c.prepareStatement("SELECT failure_code FROM mycarlos_delivery_event_page WHERE clinic_id='clinic-a' AND page_sha256=?")) {
            q.setBytes(1, java.util.HexFormat.of().parseHex(hash)); try (var r = q.executeQuery()) { return r.next() ? r.getString(1) : null; }
        }
    }
    static void assertExactPageRetained(String exact) throws Exception {
        String hash = PortalMyCarlosDeviceApprovalCrypto.sha256(exact.getBytes(StandardCharsets.UTF_8));
        try (var c = source.getConnection(); var q = c.prepareStatement("SELECT encrypted_page FROM mycarlos_delivery_event_page WHERE clinic_id='clinic-a' AND page_sha256=?")) {
            q.setBytes(1, java.util.HexFormat.of().parseHex(hash)); try (var r = q.executeQuery()) { assertTrue(r.next());
                var cipher = new PortalMyCarlosDeliveryCipher(1, Map.of(1, new byte[32]));
                assertArrayEquals(exact.getBytes(StandardCharsets.UTF_8), cipher.open(new PortalMyCarlosDeliveryCipher.Context("clinic-a", hash, "", "event_page"), r.getBytes(1))); }
        }
    }
    static final class SyncExchange implements PatientPortalHttpExchange {
        String response; boolean loseFirstAck; int gets;
        final List<Long> acks = new ArrayList<>();
        public PatientPortalHttpResponse send(ClassicHttpRequest request) throws IOException {
            assertFalse(TransactionSynchronizationManager.isActualTransactionActive());
            assertFalse(TransactionSynchronizationManager.isSynchronizationActive());
            if ("GET".equals(request.getMethod())) { gets++; return new PatientPortalHttpResponse(200, response); }
            long through = Long.parseLong(JSON.readTree(request.getEntity().getContent()).get("through").textValue());
            // Separate connection observes COMMITTED checkpoint before any network acknowledgment.
            try (var c = source.getConnection(); var q = c.createStatement();
                 var r = q.executeQuery("SELECT committed_through FROM mycarlos_delivery_checkpoint WHERE clinic_id='clinic-a'")) {
                assertTrue(r.next()); assertEquals(through, r.getLong(1));
            } catch (Exception failure) { throw new AssertionError("ack must follow durable checkpoint", failure); }
            acks.add(through);
            if (loseFirstAck && acks.size() == 1) throw new IOException("FAKE lost ack reply after server acceptance");
            return new PatientPortalHttpResponse(200, "{\"acked_through\":\"" + through + "\"}");
        }
    }
}
