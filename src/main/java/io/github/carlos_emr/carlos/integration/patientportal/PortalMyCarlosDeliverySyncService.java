/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeliveryCodec.invalid;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalService.Current;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.DeviceKeys;
import java.time.Clock;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.model.Property;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.Set;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** One authoritative clinic collector; proof, exact pages and checkpoint commit BEFORE ack. */
final class PortalMyCarlosDeliverySyncService {
    private final PortalMyCarlosDeviceApprovalTransactions transactions;
    private final PortalMyCarlosDeliveryEventDao events;
    private final PortalMyCarlosDeliveryOutboxDao outbox;
    private final PortalMyCarlosDeliveryOutboxService sender;
    private final PortalMyCarlosAcceptedDeviceDao devices;
    private final PortalMyCarlosPatientBindingDao bindings;
    private final PortalMyCarlosDeliveryClient client;
    private final PortalMyCarlosDeliveryCipher cipher;
    private final Supplier<Current> configuration;
    private final Clock clock;
    private final PropertyDao properties;
    private static final String COLLECTOR_BINDING = "mycarlos.delivery.collector.binding.v1";

    PortalMyCarlosDeliverySyncService(PortalMyCarlosDeviceApprovalTransactions transactions,
            PortalMyCarlosDeliveryEventDao events, PortalMyCarlosDeliveryOutboxDao outbox,
            PortalMyCarlosDeliveryOutboxService sender, PortalMyCarlosAcceptedDeviceDao devices,
            PortalMyCarlosPatientBindingDao bindings, PortalMyCarlosDeliveryClient client,
            PortalMyCarlosDeliveryCipher cipher, Supplier<Current> configuration, Clock clock, PropertyDao properties) {
        this.transactions = Objects.requireNonNull(transactions); this.events = Objects.requireNonNull(events);
        this.outbox = Objects.requireNonNull(outbox); this.sender = Objects.requireNonNull(sender);
        this.devices = Objects.requireNonNull(devices); this.bindings = Objects.requireNonNull(bindings);
        this.client = Objects.requireNonNull(client); this.cipher = Objects.requireNonNull(cipher);
        this.configuration = Objects.requireNonNull(configuration); this.clock = Objects.requireNonNull(clock);
        this.properties = Objects.requireNonNull(properties);
    }

    enum Result { BUSY, COLLECTED, RECOVERED_ACK, QUARANTINED }
    private record Writer(String clinic, String token, long through, long acknowledged, String configHash) {}
    private record Collected(long through, boolean quarantined) {}
    private record Prepared(Runnable apply, boolean proofVerified) {}
    private static final class Untrusted extends IllegalArgumentException {
        private static final long serialVersionUID = 1L;
        final String code;
        Untrusted(String code) { super("myCarlos delivery evidence requires reconciliation"); this.code = code; }
    }

    Result poll() {
        if (TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException("myCarlos sync HTTP requires an independent transaction boundary");
        Current cfg = configuration.get();
        if (cfg == null || !cfg.enabled || !client.configuredFor(cfg.clinic, cfg.internalOrigin)) throw new IllegalStateException("myCarlos delivery sync is disabled");
        Writer writer = transactions.withApprovalState(() -> {
            var checkpoint = events.checkpoint(cfg.clinic);
            requireCollectorBinding(cfg, checkpoint);
            if (checkpoint.writerUntil() != null && checkpoint.writerUntil() > now()) return null;
            String token = UUID.randomUUID().toString();
            if (now() > Long.MAX_VALUE - 60) throw invalid();
            events.writer(cfg.clinic, token, now() + 60);
            return new Writer(cfg.clinic, token, checkpoint.through(), checkpoint.acknowledged(), java.util.HexFormat.of().formatHex(cfg.hash().copy()));
        });
        if (writer == null) return Result.BUSY;
        // A lost ack reply never causes re-verification, duplicate receipt storage, or a cursor reset.
        if (writer.through() > writer.acknowledged()) {
            acknowledge(writer, writer.through());
            return Result.RECOVERED_ACK;
        }
        transactions.withApprovalState(() -> { requireWriter(writer); return null; });
        byte[] exact = client.fetchEvents(writer.through()); // No transaction spans this HTTP.
        PortalMyCarlosDeliveryClient.Page page;
        try {
            page = PortalMyCarlosDeliveryClient.page(exact, writer.through());
        } catch (IllegalArgumentException malformed) {
            transactions.withApprovalState(() -> {
                requireWriter(writer);
                keepPage(writer, exact, "invalid_page");
                events.releaseWriter(writer.clinic(), writer.token());
                return null;
            });
            return Result.QUARANTINED;
        }
        Collected collected = transactions.withApprovalState(() -> {
            requireWriter(writer);
            long pageId = keepPage(writer, exact, null);
            long through = writer.through();
            String failure = null;
            for (var event : page.events()) {
                if (through == Long.MAX_VALUE || event.sequence() != through + 1) { failure = "event_gap"; break; }
                Prepared prepared;
                try { prepared = prepare(writer.clinic(), event); }
                catch (Untrusted invalid) { failure = invalid.code; break; }
                prepared.apply().run();
                events.event(writer.clinic(), event, pageId, prepared.proofVerified());
                through = event.sequence();
            }
            if (failure != null) events.quarantinePage(pageId, failure);
            events.commitThrough(writer.clinic(), writer.token(), writer.through(), through);
            return new Collected(through, failure != null);
        });
        // Only the durable prefix may leave CARLOS as an acknowledgment. A quarantined suffix
        // keeps the feed visibly paused until reconciled and can never become Picked up by status.
        if (collected.through() > writer.through() || !collected.quarantined()) acknowledge(writer, collected.through());
        else transactions.withApprovalState(() -> { events.releaseWriter(writer.clinic(), writer.token()); return null; });
        return collected.quarantined() ? Result.QUARANTINED : Result.COLLECTED;
    }

    private void acknowledge(Writer writer, long through) {
        transactions.withApprovalState(() -> { requireWriter(writer, through); return null; });
        client.acknowledge(through); // A collection acknowledgment is never a patient receipt.
        transactions.withApprovalState(() -> {
            requireWriter(writer, through);
            events.acknowledged(writer.clinic(), writer.token(), through);
            return null;
        });
    }

    private Prepared prepare(String clinic, PortalMyCarlosDeliveryClient.Event event) {
        if (event.deliveryId() == null) {
            var device = devices.findByKidForUpdate(event.deviceKid()).orElseThrow(() -> new Untrusted("unknown_binding"));
            var binding = bindings.findByClinicRecipientForUpdate(clinic, event.recipientId()).orElseThrow(() -> new Untrusted("unknown_binding"));
            if (device.bindingId() != binding.id()) throw new Untrusted("unknown_binding");
            var keys = new DeviceKeys(device.kid(), device.x25519(), device.ed25519());
            if ("anchor".equals(event.kind())) {
                try {
                    var proof = PortalMyCarlosDeviceEvidenceVerifier.verify(event.requestAssertion(), keys, event.recipientId());
                    if (!event.vaultHeadLink().equals(proof.anchor())) throw invalid();
                } catch (IllegalArgumentException failure) { throw new Untrusted("invalid_proof"); }
                return new Prepared(() -> {}, true);
            }
            return new Prepared(() -> outbox.fenceDevice(clinic, event.deviceKid()), false);
        }
        var row = outbox.find(clinic, event.deliveryId(), event.deviceKid()).orElseThrow(() -> new Untrusted("unknown_binding"));
        PortalMyCarlosDeliverySnapshot s;
        try {
            s = sender.load(row);
            if (!s.recipientId().equals(event.recipientId()) || !s.ciphertextSha256().equals(event.ciphertextSha256())
                    || s.createdAt() != event.createdAt() || s.expiresAt() != event.expiresAt()
                    || s.sealedSize() != event.size()) throw invalid();
            if ("picked_up".equals(event.kind())) {
                sender.checkReceipt(row, event.receipt());
                PortalMyCarlosDeviceEvidenceVerifier.verify(event.requestAssertion(), s.device(), s.recipientId());
                // A retained receipt anchors its filing head. A fresh retry request may anchor a
                // later current head; both independently signed original proofs are retained.
            }
        } catch (IllegalArgumentException | IllegalStateException failure) { throw new Untrusted("invalid_proof"); }
        if ("picked_up".equals(event.kind())) return new Prepared(() -> sender.keepReceipt(row, event.receipt()), true);
        return new Prepared(() -> outbox.terminal(row, event.kind()), false);
    }

    private long keepPage(Writer writer, byte[] exact, String failure) {
        String hash = PortalMyCarlosDeviceApprovalCrypto.sha256(exact);
        var context = new PortalMyCarlosDeliveryCipher.Context(writer.clinic(), hash, "", "event_page");
        return events.keepPage(writer.clinic(), writer.through(), hash, cipher.seal(context, exact), now(), failure);
    }
    private void requireWriter(Writer writer) { requireWriter(writer, writer.through()); }
    private void requireWriter(Writer writer, long through) {
        requireConfiguration(writer);
        var checkpoint = events.checkpoint(writer.clinic());
        requireCollectorBinding(configuration.get(), checkpoint);
        if (!writer.token().equals(checkpoint.writerId()) || checkpoint.writerUntil() == null
                || checkpoint.writerUntil() <= now() || checkpoint.through() != through) throw new IllegalStateException("myCarlos delivery writer lease changed");
    }
    /** A restored/repointed clinic cursor may never be adopted by another portal feed. */
    private void requireCollectorBinding(Current cfg, PortalMyCarlosDeliveryEventDao.Checkpoint checkpoint) {
        var rows = properties.findByNameForUpdate(COLLECTOR_BINDING).stream()
                .filter(row -> row.getProviderNo() == null || row.getProviderNo().isBlank()).toList();
        if (rows.size() > 1) throw new IllegalStateException("myCarlos collector binding is ambiguous");
        String hash = java.util.HexFormat.of().formatHex(cfg.hash().copy());
        if (rows.isEmpty()) {
            if (checkpoint.through() != 0 || checkpoint.acknowledged() != 0 || events.hasCollectedData(cfg.clinic))
                throw new IllegalStateException("myCarlos collector binding requires reconciliation");
            var row = new Property(COLLECTOR_BINDING);
            row.setValue(new String(PortalMyCarlosDeliveryCodec.canonical(Map.of("v", 1L,
                    "clinic_id", cfg.clinic, "config_sha256", hash)), StandardCharsets.UTF_8));
            properties.persist(row);
            return;
        }
        var stored = PortalMyCarlosDeliveryCodec.object(PortalMyCarlosDeviceApprovalCodec.utf8(rows.getFirst().getValue()), 4096);
        PortalMyCarlosDeliveryCodec.fields(stored, Set.of("v", "clinic_id", "config_sha256"));
        if (PortalMyCarlosDeliveryCodec.epoch(stored, "v") != 1
                || !cfg.clinic.equals(PortalMyCarlosDeliveryCodec.text(stored, "clinic_id"))
                || !hash.equals(PortalMyCarlosDeliveryCodec.text(stored, "config_sha256")))
            throw new IllegalStateException("myCarlos collector target changed; reconciliation is required");
    }

    private void requireConfiguration(Writer writer) {
        Current cfg = configuration.get();
        if (cfg == null || !cfg.enabled || !writer.clinic().equals(cfg.clinic)
                || !writer.configHash().equals(java.util.HexFormat.of().formatHex(cfg.hash().copy()))
                || !client.configuredFor(cfg.clinic, cfg.internalOrigin)) throw new IllegalStateException("myCarlos delivery sync configuration changed");
    }
    private long now() { long value = clock.instant().getEpochSecond(); if (value < 0) throw invalid(); return value; }
}
