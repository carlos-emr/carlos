/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeliveryCodec.invalid;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeliveryOutboxDao.Row;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalService.Current;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Arrays;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Dedicated encrypted durable sender staging and short leases. This core is deliberately
 * unregistered until schema/keyring/operator setup is complete; no scheduler or default-on switch.
 */
final class PortalMyCarlosDeliveryOutboxService {
    static final String OBJECT = "_mycarlos_delivery";
    private final PortalMyCarlosDeviceApprovalTransactions transactions;
    private final PortalMyCarlosPatientBindingDao bindings;
    private final PortalMyCarlosAcceptedDeviceDao devices;
    private final PortalMyCarlosDeliveryOutboxDao outbox;
    private final PortalMyCarlosDeliveryCipher cipher;
    private final Supplier<Current> current;
    private final PatientPortalService portal;
    private final PortalMyCarlosDeliveryClient client;
    private final SecurityInfoManager security;
    private final Clock clock;

    PortalMyCarlosDeliveryOutboxService(PortalMyCarlosDeviceApprovalTransactions transactions,
            PortalMyCarlosPatientBindingDao bindings, PortalMyCarlosAcceptedDeviceDao devices,
            PortalMyCarlosDeliveryOutboxDao outbox, PortalMyCarlosDeliveryCipher cipher,
            Supplier<Current> current, PatientPortalService portal, PortalMyCarlosDeliveryClient client,
            SecurityInfoManager security, Clock clock) {
        this.transactions = Objects.requireNonNull(transactions);
        this.bindings = Objects.requireNonNull(bindings);
        this.devices = Objects.requireNonNull(devices);
        this.outbox = Objects.requireNonNull(outbox);
        this.cipher = Objects.requireNonNull(cipher);
        this.current = Objects.requireNonNull(current);
        this.portal = Objects.requireNonNull(portal);
        this.client = Objects.requireNonNull(client);
        this.security = Objects.requireNonNull(security);
        this.clock = Objects.requireNonNull(clock);
    }

    void requireStaff(LoggedInInfo session, int patient) {
        if (patient <= 0 || session == null || session.getLoggedInSecurity() == null
                || session.getLoggedInProviderNo() == null || session.getLoggedInSecurity().getSecurityNo() == null
                || session.getLoggedInSecurity().getSecurityNo() <= 0
                || !security.hasPrivilege(session, "_demographic", SecurityInfoManager.READ, Integer.toString(patient))
                || !security.isAllowedAccessToPatientRecord(session, patient)
                || !security.hasPrivilege(session, OBJECT, SecurityInfoManager.WRITE, Integer.toString(patient))) {
            throw new SecurityException("missing required sec object (_mycarlos_delivery)");
        }
    }

    /** Original and per-device immutable body commit together, encrypted, before any HTTP. */
    long stage(LoggedInInfo session, PortalMyCarlosDeliveryComposer.Copy copy, PortalMyCarlosDeliverySnapshot snapshot) {
        if (snapshot == null || snapshot.readingAid()) throw invalid();
        requireStaff(session, snapshot.demographicNo());
        standalone();
        validateCopy(copy, snapshot);
        return transactions.withSigningInventory(inventory -> {
            eligible(snapshot);
            requireSigner(inventory.orElseThrow(() -> unavailable()), snapshot);
            return stageWithinOwner(copy, snapshot);
        });
    }

    /**
     * The original's own staging. It never waits for, or looks at, a reading aid: the original is
     * never held back by its aid (answer 1).
     */
    long stageOriginal(LoggedInInfo session, PortalMyCarlosDeliveryComposer.Copy copy, PortalMyCarlosDeliverySnapshot snapshot) {
        return stage(session, copy, snapshot);
    }

    /** What happened to the aid of a send: it never changes what happened to the original. */
    enum AidResult { NOT_REQUESTED, STAGED, REFUSED }
    record Staged(long originalRowId, long readingAidRowId, AidResult aid) { }

    /**
     * Stages the original, then its reading aid as a delivery of its own, in separate owner
     * transactions. A refused or failed aid leaves the original staged: there is no both-or-neither.
     * Pass a null {@code aid} for an original alone (no approval, or the tick left out).
     */
    Staged stageWithAid(LoggedInInfo session, PortalMyCarlosDeliveryComposer.Copy original,
            PortalMyCarlosDeliverySnapshot originalSnapshot, PortalMyCarlosDeliveryComposer.Copy aid,
            PortalMyCarlosDeliverySnapshot aidSnapshot) {
        long originalRow = stageOriginal(session, original, originalSnapshot);
        if (aid == null || aidSnapshot == null) return new Staged(originalRow, 0, AidResult.NOT_REQUESTED);
        try {
            return new Staged(originalRow, stageAid(session, aid, aidSnapshot), AidResult.STAGED);
        } catch (ImmutableConflict | IllegalArgumentException | Ineligible refused) {
            return new Staged(originalRow, 0, AidResult.REFUSED);
        }
    }

    /**
     * Stages a reading aid as its own outbox row under its own delivery ID, for an original that
     * already has a row for this clinic, device and raw source document, whether that row is still
     * staged or already sent. Until the outbox has pair columns (V1.0.85, with P4b), the aid is keyed
     * by its own (clinic, delivery ID, device) like any row, and its relation to the original lives
     * inside its encrypted snapshot (kind in the outer record, {@code pairedOriginalDeliveryId}) and
     * in the signed manifest's pair claims. Each row keeps its own lease, retry and fence. This does
     * not authenticate the aid or authorize sending it: the approval check at send time does.
     */
    long stageAid(LoggedInInfo session, PortalMyCarlosDeliveryComposer.Copy copy, PortalMyCarlosDeliverySnapshot snapshot) {
        if (snapshot == null || !snapshot.readingAid()) throw invalid();
        requireStaff(session, snapshot.demographicNo());
        standalone();
        validateCopy(copy, snapshot);
        if (!copy.recipientId().equals(snapshot.recipientId())) throw invalid();
        return transactions.withSigningInventory(inventory -> {
            var signer = inventory.orElseThrow(() -> unavailable());
            eligible(snapshot);
            requireSigner(signer, snapshot);
            var original = outbox.find(snapshot.clinicId(), snapshot.pairedOriginalDeliveryId(), snapshot.device().kid())
                    .orElseThrow(() -> conflict());
            var originalSnapshot = load(original);
            if (originalSnapshot.readingAid() || !originalSnapshot.documentSha256().equals(claimedOriginalHash(copy))
                    || (originalSnapshot.createdAt() != snapshot.createdAt() || originalSnapshot.expiresAt() != snapshot.expiresAt())
                    || !pairable(original.state())) throw conflict();
            requirePairScope(originalSnapshot, snapshot);
            return stageWithinOwner(copy, snapshot);
        });
    }

    /** The original's PDF hash the signed aid manifest names in its pair claims. */
    private static String claimedOriginalHash(PortalMyCarlosDeliveryComposer.Copy copy) {
        var claims = PortalMyCarlosDeviceApprovalCodec.envelope(copy.manifest()).payload().copy();
        var pair = PortalMyCarlosDeliveryCodec.object(claims, 8192).get("pair");
        if (pair == null) throw invalid();
        return PortalMyCarlosDeliveryCodec.text(pair, "original_document_sha256");
    }

    /** An aid may follow an original that is waiting for pickup or already picked up, never a dead one. */
    private static boolean pairable(String state) {
        return switch (state) {
            case "staged", "waiting", "picked_up" -> true;
            default -> false;
        };
    }

    /** Aid and original must share clinic, patient, recipient, device, signer and raw source document. */
    private static void requirePairScope(PortalMyCarlosDeliverySnapshot b, PortalMyCarlosDeliverySnapshot c) {
        if (b.deliveryId().equals(c.deliveryId()) || b.sourceDocumentId() == null
                || !b.sourceDocumentId().equals(c.sourceDocumentId())
                || !b.deliveryId().equals(c.pairedOriginalDeliveryId())
                || !b.clinicId().equals(c.clinicId()) || b.demographicNo() != c.demographicNo()
                || !b.recipientId().equals(c.recipientId()) || !b.publicBase().equals(c.publicBase())
                || !b.internalTarget().equals(c.internalTarget()) || !b.configSha256().equals(c.configSha256())
                || b.acceptedDeviceId() != c.acceptedDeviceId() || b.acceptanceVersion() != c.acceptanceVersion()
                || !b.acceptanceSha256().equals(c.acceptanceSha256()) || !b.device().equals(c.device())
                || !b.signerKid().equals(c.signerKid())) throw invalid();
    }

    /**
     * Fencing an original fences its aid when the aid next claims or transmits, with no pair
     * column to query: a row that names an original checks that original's row first. Fencing an aid
     * never touches the original.
     */
    private void requireOriginalNotFenced(PortalMyCarlosDeliverySnapshot s) {
        if (!s.readingAid()) return;
        var original = outbox.find(s.clinicId(), s.pairedOriginalDeliveryId(), s.device().kid());
        if (original.isEmpty() || "fenced".equals(original.get().state())) throw unavailable();
    }

    private static void validateCopy(PortalMyCarlosDeliveryComposer.Copy copy, PortalMyCarlosDeliverySnapshot snapshot) {
        if (copy == null || !copy.deliveryId().equals(snapshot.deliveryId())
                || !copy.bodySha256().equals(snapshot.bodySha256()) || !copy.device().equals(snapshot.device())
                || !copy.signerKid().equals(snapshot.signerKid()) || !copy.documentSha256().equals(snapshot.documentSha256())
                || !copy.uploadBody().equals(snapshot.body())
                || !PortalMyCarlosDeviceApprovalCrypto.sha256(copy.originalPdf().copy()).equals(snapshot.documentSha256())) throw invalid();
    }

    /** Caller owns the existing signing-inventory transaction; never opens a nested owner. */
    private long stageWithinOwner(PortalMyCarlosDeliveryComposer.Copy copy, PortalMyCarlosDeliverySnapshot snapshot) {
        long originalId;
        var priorOriginal = outbox.original(snapshot.clinicId(), snapshot.deliveryId());
        if (priorOriginal.isPresent()) {
            var original = priorOriginal.get();
            if (!original.hash().equals(snapshot.documentSha256()) || !Arrays.equals(copy.originalPdf().copy(),
                    cipher.open(context(snapshot, "original"), original.encrypted().copy()))) throw conflict();
            originalId = original.id();
        } else {
            originalId = outbox.insertOriginal(snapshot.clinicId(), snapshot.deliveryId(), snapshot.documentSha256(),
                    cipher.seal(context(snapshot, "original"), copy.originalPdf().copy()), snapshot.createdAt());
        }
        var prior = outbox.find(snapshot.clinicId(), snapshot.deliveryId(), snapshot.device().kid());
        if (prior.isPresent()) {
            Row row = prior.get();
            if (row.originalId() != originalId || !Arrays.equals(load(row).encode(), snapshot.encode())) throw conflict();
            return row.id();
        }
        byte[] exact = snapshot.encode();
        return outbox.insert(originalId, snapshot, cipher.seal(context(snapshot, "outbox"), exact),
                PortalMyCarlosDeviceApprovalCrypto.sha256(exact));
    }

    enum SendResult { WAITING, EXPIRED, REMOVED, RETRY_PENDING, FENCED, ERROR, NOT_CLAIMED, STALE }
    record Lease(long rowId, String token, long version, long until, PortalMyCarlosDeliverySnapshot snapshot) {
        @Override public String toString() { return "DeliveryLease[REDACTED]"; }
    }

    SendResult send(long rowId) {
        standalone();
        Lease lease = claim(rowId);
        if (lease == null) return SendResult.NOT_CLAIMED;
        return transmit(lease);
    }

    /** The complete network boundary also permits a worker to resume an already claimed lease. */
    SendResult transmit(Lease lease) {
        standalone();
        Objects.requireNonNull(lease);
        // Network initiation follows the second local fence. Concurrent removal after this point
        // races with the portal's deterministic device lock; no cross-system atomicity is claimed.
        if (!beforeHttp(lease)) return SendResult.FENCED;
        String state = "waiting", failure = null;
        long retry = 0;
        SendResult result = SendResult.WAITING;
        try {
            var s = lease.snapshot();
            String transport = client.upload(s.demographicNo(), s.deliveryId(), s.device().kid(), s.body().copy());
            if ("expired".equals(transport) || "removed".equals(transport)) {
                state = transport;
                result = "expired".equals(transport) ? SendResult.EXPIRED : SendResult.REMOVED;
            }
        } catch (PatientPortalException remote) {
            String code = remote.detail();
            if ("device_removed".equals(code)) {
                state = "fenced";
                failure = "device_removed";
                result = SendResult.FENCED;
            } else if (remote.kind() == PatientPortalException.Kind.CONFLICT || remote.statusCode() == 400
                    || remote.statusCode() == 401 || remote.statusCode() == 403 || remote.statusCode() == 404 || remote.statusCode() == 413) {
                state = "error";
                failure = remote.statusCode() == 413 ? "invalid_delivery"
                        : "delivery_conflict".equals(code) ? "delivery_conflict" : "portal_failure";
                result = SendResult.ERROR;
            } else {
                state = "staged";
                retry = plus(now(), 60);
                failure = remote.statusCode() == 429 ? "mailbox_quota_exceeded" : "transport_unknown";
                result = SendResult.RETRY_PENDING;
            }
        } catch (PortalRequestPreparationException locallyRefused) {
            state = "fenced";
            failure = "local_ineligible";
            result = SendResult.FENCED;
        } catch (IllegalArgumentException malformedSuccess) {
            // The endpoint parser refused a success body after the portal may have accepted it.
            state = "staged";
            retry = plus(now(), 60);
            failure = "transport_unknown";
            result = SendResult.RETRY_PENDING;
        }
        String completionState = state, completionFailure = failure;
        long retryAt = retry;
        boolean changed = transactions.withApprovalState(() -> outbox.finish(lease.rowId(), lease.token(),
                lease.version(), completionState, retryAt, completionFailure));
        return changed ? result : SendResult.STALE;
    }

    Lease claim(long rowId) {
        standalone();
        return transactions.withSigningInventory(inventory -> {
            Row row = outbox.find(rowId).orElseThrow(() -> unavailable());
            long now = now();
            if (!"staged".equals(row.state()) || row.retryAfter() > now || (row.leaseUntil() != null && row.leaseUntil() > now)) return null;
            var snapshot = load(row);
            try {
                eligible(snapshot);
                requireSigner(inventory.orElseThrow(() -> unavailable()), snapshot);
            } catch (Ineligible noLongerEligible) {
                outbox.terminal(row, "fenced");
                return null;
            }
            String token = UUID.randomUUID().toString();
            long until = plus(now, 30);
            outbox.claim(row, token, until, now);
            return new Lease(row.id(), token, row.version() + 1, until, snapshot);
        });
    }

    private boolean beforeHttp(Lease lease) {
        return transactions.withApprovalState(() -> {
            Row row = outbox.find(lease.rowId()).orElseThrow(() -> unavailable());
            if (!lease.token().equals(row.leaseId()) || lease.version() != row.version()
                    || !"staged".equals(row.state()) || lease.until() <= now()) return false;
            if (!load(row).equals(lease.snapshot())) throw invalid();
            try {
                eligible(lease.snapshot());
                return true;
            } catch (Ineligible noLongerEligible) {
                outbox.terminal(row, "fenced");
                return false;
            }
        });
    }

    private void eligible(PortalMyCarlosDeliverySnapshot s) {
        Current cfg = current.get();
        if (cfg == null || !cfg.enabled || !cfg.clinic.equals(s.clinicId()) || !cfg.publicBase.equals(s.publicBase())
                || !java.util.HexFormat.of().formatHex(cfg.hash().copy()).equals(s.configSha256())
                || !portal.myCarlosClinicId().equals(s.clinicId())
                || !portal.myCarlosDeliveryTarget(s.demographicNo()).toASCIIString().equals(s.internalTarget())) throw unavailable();
        if (outbox.deviceFenced(s.clinicId(), s.device().kid())) throw unavailable();
        requireOriginalNotFenced(s);
        var binding = bindings.findByClinicPatientForUpdate(s.clinicId(), s.demographicNo()).orElseThrow(() -> unavailable());
        var device = devices.findByKidForUpdate(s.device().kid()).orElseThrow(() -> unavailable());
        if (!binding.recipientId().equals(s.recipientId()) || !binding.publicBase().equals(s.publicBase())
                || !java.util.HexFormat.of().formatHex(binding.configHash().copy()).equals(s.configSha256())
                || !binding.internalOrigin().equals(cfg.internalOrigin) || device.bindingId() != binding.id()
                || device.id() != s.acceptedDeviceId() || device.version() != s.acceptanceVersion() || !device.current()
                || !device.x25519().equals(s.device().x25519()) || !device.ed25519().equals(s.device().ed25519())
                || !java.util.HexFormat.of().formatHex(device.acceptanceHash().copy()).equals(s.acceptanceSha256())) throw unavailable();
    }

    PortalMyCarlosDeliverySnapshot load(Row row) {
        var c = new PortalMyCarlosDeliveryCipher.Context(row.clinicId(), row.deliveryId(), row.deviceKid(), "outbox");
        byte[] exact = cipher.open(c, row.encryptedSnapshot().copy());
        if (!PortalMyCarlosDeviceApprovalCrypto.sha256(exact).equals(row.snapshotHash())) throw invalid();
        var s = PortalMyCarlosDeliverySnapshot.decode(exact);
        if (!s.clinicId().equals(row.clinicId()) || !s.deliveryId().equals(row.deliveryId()) || !s.device().kid().equals(row.deviceKid())
                || s.demographicNo() != row.demographicNo() || s.acceptedDeviceId() != row.acceptedDeviceId()
                || s.acceptanceVersion() != row.acceptanceVersion() || !s.configSha256().equals(row.configHash())) throw invalid();
        return s;
    }

    /** Caller holds the delivery/event transaction. Current removal never invalidates historical proof. */
    void keepReceipt(Row row, String exactCompact) {
        var proof = checkReceipt(row, exactCompact);
        if ("picked_up".equals(row.state())) return;
        var snapshot = load(row);
        byte[] exact = exactCompact.getBytes(StandardCharsets.US_ASCII);
        outbox.pickedUp(row, cipher.seal(context(snapshot, "receipt"), exact),
                PortalMyCarlosDeviceApprovalCrypto.sha256(exact), proof.jti());
    }

    PortalMyCarlosDeliveryReceiptVerifier.Verified checkReceipt(Row row, String exactCompact) {
        var snapshot = load(row);
        var proof = PortalMyCarlosDeliveryReceiptVerifier.verify(exactCompact, snapshot.receiptBinding());
        byte[] exact = exactCompact.getBytes(StandardCharsets.US_ASCII);
        if ("picked_up".equals(row.state()) && (!PortalMyCarlosDeviceApprovalCrypto.sha256(exact).equals(row.receiptHash())
                || !proof.jti().equals(row.receiptJti())
                || !Arrays.equals(exact, cipher.open(context(snapshot, "receipt"), row.encryptedReceipt().copy())))) throw conflict();
        return proof;
    }

    enum DeliveryStatus { STAGED, WAITING, PICKED_UP, EXPIRED, REMOVED, FENCED, ERROR }

    /** Staff status derives pickup from the retained independently verified original proof. */
    DeliveryStatus status(LoggedInInfo session, int patient, String deliveryId, String deviceKid) {
        requireStaff(session, patient);
        standalone();
        return transactions.withApprovalState(() -> {
            Row row = outbox.find(portal.myCarlosClinicId(), deliveryId, deviceKid).orElseThrow(() -> unavailable());
            if (row.demographicNo() != patient) throw unavailable();
            var snapshot = load(row);
            if ("picked_up".equals(row.state())) {
                byte[] exact = cipher.open(context(snapshot, "receipt"), row.encryptedReceipt().copy());
                String compact = new String(exact, StandardCharsets.US_ASCII);
                var verified = PortalMyCarlosDeliveryReceiptVerifier.verify(compact, snapshot.receiptBinding());
                if (!PortalMyCarlosDeviceApprovalCrypto.sha256(exact).equals(row.receiptHash())
                        || !verified.jti().equals(row.receiptJti())) throw invalid();
                return DeliveryStatus.PICKED_UP;
            }
            return switch (row.state()) {
                case "staged" -> DeliveryStatus.STAGED;
                case "waiting" -> DeliveryStatus.WAITING;
                case "expired" -> DeliveryStatus.EXPIRED;
                case "removed" -> DeliveryStatus.REMOVED;
                case "fenced" -> DeliveryStatus.FENCED;
                case "error" -> DeliveryStatus.ERROR;
                default -> throw invalid();
            };
        });
    }

    private static void requireSigner(PortalMyCarlosKeyInventory inventory, PortalMyCarlosDeliverySnapshot s) {
        if (!inventory.clinicId().equals(s.clinicId()) || inventory.publication().keys().stream().noneMatch(
                key -> key.kid().equals(s.signerKid()) && "active".equals(key.status()))) throw unavailable();
    }
    private static PortalMyCarlosDeliveryCipher.Context context(PortalMyCarlosDeliverySnapshot s, String kind) {
        return new PortalMyCarlosDeliveryCipher.Context(s.clinicId(), s.deliveryId(), "original".equals(kind) ? "" : s.device().kid(), kind);
    }
    private long now() { long time = clock.instant().getEpochSecond(); if (time < 0) throw
        invalid();
        return time; }
    private static long plus(long value, long seconds) { if (value > Long.MAX_VALUE - seconds) throw invalid();
        return value + seconds; }
    private static final class Ineligible extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        Ineligible() { super("myCarlos delivery is unavailable or binding changed"); }
    }
    private static Ineligible unavailable() { return new Ineligible(); }
    static final class ImmutableConflict extends IllegalStateException {
        private static final long serialVersionUID = 1L;
        ImmutableConflict() { super("myCarlos delivery immutable identity conflicts"); }
    }
    private static ImmutableConflict conflict() { return new ImmutableConflict(); }
    private static void standalone() {
        if (TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException("myCarlos network work requires an independent transaction boundary");
    }
}
