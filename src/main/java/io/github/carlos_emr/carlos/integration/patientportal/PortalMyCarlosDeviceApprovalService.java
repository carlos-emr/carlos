/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.persistence.EntityManagerFactory;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Calendar;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TimeZone;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.hibernate.Session;
import org.hibernate.jdbc.ReturningWork;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Local approval authority. Constructor ports are server-owned; HTTP adapters keep network work
 * outside these transactions and staff actions retain their separate method/CSRF gates.
 */
final class PortalMyCarlosDeviceApprovalService {
    static final String OBJECT = "_portal.mycarlos.device_approval";
    private static final ObjectMapper JSON = new ObjectMapper();
    private final PortalMyCarlosDeviceApprovalTransactions transactions;
    private final EntityManagerFactory factory;
    private final PortalMyCarlosPatientBindingDao bindings;
    private final PortalMyCarlosDeviceApprovalChallengeDao challenges;
    private final PortalMyCarlosAcceptedDeviceDao devices;
    private final PortalMyCarlosDeviceApprovalOutboxDao outbox;
    private final SecurityInfoManager security;
    private final Supplier<Current> settings;
    private final SourceAuthorization source;
    private final Signer signer;
    private final Supplier<Bytes> challengeIds;
    private final Supplier<UUID> removalIds;
    private final Clock clock;
    private final Consumer<Audit> audit;

    /** No activation here. Future server nonce suppliers must use CSPRNGs; fixed FAKE values are test-only. */
    PortalMyCarlosDeviceApprovalService(PortalMyCarlosDeviceApprovalTransactions transactions,
            PortalMyCarlosPatientBindingDao bindings, PortalMyCarlosDeviceApprovalChallengeDao challenges,
            PortalMyCarlosAcceptedDeviceDao devices, PortalMyCarlosDeviceApprovalOutboxDao outbox,
            SecurityInfoManager security, Supplier<Current> settings, SourceAuthorization source,
            Signer signer, Supplier<Bytes> challengeIds, Supplier<UUID> removalIds, Clock clock, Consumer<Audit> audit) {
        this.transactions = Objects.requireNonNull(transactions);
        this.bindings = Objects.requireNonNull(bindings);
        this.challenges = Objects.requireNonNull(challenges);
        this.devices = Objects.requireNonNull(devices);
        this.outbox = Objects.requireNonNull(outbox);
        factory = Objects.requireNonNull(bindings.factory);
        if (challenges.factory != factory || devices.factory != factory || outbox.factory != factory) throw new IllegalArgumentException("all approval DAOs require the same EntityManagerFactory");
        this.security = Objects.requireNonNull(security);
        this.settings = Objects.requireNonNull(settings);
        this.source = Objects.requireNonNull(source);
        this.signer = Objects.requireNonNull(signer);
        this.challengeIds = Objects.requireNonNull(challengeIds);
        this.removalIds = Objects.requireNonNull(removalIds);
        this.clock = Objects.requireNonNull(clock);
        this.audit = Objects.requireNonNull(audit);
    }

    @FunctionalInterface interface SourceAuthorization {
        /** Independent authenticated server import boundary; never a portal-supplied boolean. */
        void requireTrustedSource(int demographicNo);
    }
    interface Signer {
        SigningIdentity currentIdentity();
        /** Select configured private material for this exact public identity; no network or DB work. */
        String sign(Kind kind, Map<String, Object> claims, Scope scope, SigningIdentity expectedIdentity);
    }
    record SigningIdentity(String kid, String spki) {
        SigningIdentity { Checks.keyId(kid);
            Checks.text(spki, 1024);
        }
        @Override public String toString() { return "ApprovalSigner[REDACTED]";
        }
    }
    /** Built only from the existing validated internal settings and already QR-canonical public base. */
    static final class Current {
        final String clinic;
        final String publicBase;
        final String internalOrigin;
        final boolean enabled;
        private Current(PatientPortalSettings settings, String canonicalPublicBase, boolean delivery, boolean approval) {
            clinic = settings.clinicId();
            publicBase = canonicalPublicBase;
            internalOrigin = settings.baseUrl();
            Checks.scope(clinic, "scope-validation", publicBase);
            enabled = delivery && approval;
        }
        static Current fromValidated(PatientPortalSettings settings, String canonicalPublicBase,
                boolean deliveryEnabled, boolean approvalEnabled) {
            return new Current(Objects.requireNonNull(settings), canonicalPublicBase, deliveryEnabled, approvalEnabled);
        }
        Scope scope(String recipient) { return new Scope(clinic, recipient, publicBase);
        }
        Bytes hash() {
            return Checks.hashBytes(PortalMyCarlosDeviceApprovalCrypto.frame(
                    Checks.utf8(clinic), Checks.utf8(internalOrigin), Checks.utf8(publicBase)));
        }
        @Override public String toString() { return "ApprovalConfiguration[REDACTED]";
        }
    }
    record Decision(PortalMyCarlosAcceptedDevice device, boolean historical) {
        Decision { Objects.requireNonNull(device);
        }
        @Override public String toString() { return "ApprovalDecision[REDACTED]";
        }
    }
    record History(List<PortalMyCarlosAcceptedDevice> devices, boolean more) {
        History { devices = List.copyOf(devices);
        }
        @Override public String toString() { return "ApprovalHistory[REDACTED]";
        }
    }
    record Audit(String clinic, long bindingId, long deviceId, String operation, String provider) {}
    record Actor(String provider, int securityNo) {
        Actor { Checks.text(provider, 6);
            Checks.positive(securityNo);
        }
        @Override public String toString() { return "ApprovalActor[REDACTED]";
        }
    }
    static final class Refusal extends IllegalStateException {
        final String code;
        Refusal(String code) { super(code);
            this.code = code;
        }
    }
    private static Refusal refuse(String code) { return new Refusal(code);
    }
    private Current current() {
        Current value = settings.get();
        if (value == null || !value.enabled) throw refuse("approval_unavailable");
        return value;
    }
    private void standalone() {
        if (TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.isSynchronizationActive()
                || TransactionSynchronizationManager.hasResource(factory)
                || TransactionSynchronizationManager.getResourceMap().values().stream().anyMatch(value -> value instanceof EntityManagerHolder)) throw new IllegalStateException("standalone approval operation required");
    }
    private Instant now() {
        Instant value = clock.instant().truncatedTo(ChronoUnit.SECONDS);
        Checks.second(value);
        return value;
    }
    private Actor staff(LoggedInInfo session, int patient, String right) {
        Checks.positive(patient);
        if (session == null || session.getLoggedInSecurity() == null || session.getLoggedInProviderNo() == null
                || session.getLoggedInSecurity().getSecurityNo() == null || session.getLoggedInSecurity().getSecurityNo() <= 0
                || !security.hasPrivilege(session, "_demographic", SecurityInfoManager.READ, Integer.toString(patient))
                || !security.isAllowedAccessToPatientRecord(session, patient)
                || !security.hasPrivilege(session, OBJECT, right, Integer.toString(patient))) throw new SecurityException("missing device approval authority");
        return new Actor(session.getLoggedInProviderNo(), session.getLoggedInSecurity().getSecurityNo());
    }
    private static Map<String, PatientPortalClinicKeysPublishRequest.Key> catalogue(Optional<PortalMyCarlosKeyInventory> value, Current cfg) {
        var inventory = value.orElseThrow(() -> refuse("approval_unavailable"));
        if (!inventory.clinicId().equals(cfg.clinic)) throw refuse("binding_changed");
        var keys = new LinkedHashMap<String, PatientPortalClinicKeysPublishRequest.Key>();
        inventory.publication().keys().forEach(key -> keys.put(key.kid(), key));
        return Map.copyOf(keys);
    }
    private SigningIdentity activeSigner(Map<String, PatientPortalClinicKeysPublishRequest.Key> keys) {
        SigningIdentity identity = Objects.requireNonNull(signer.currentIdentity());
        var key = keys.get(identity.kid());
        if (key == null || !"active".equals(key.status()) || !key.spki().equals(identity.spki())) throw refuse("approval_unavailable");
        return identity;
    }
    private static void binding(Current cfg, PortalMyCarlosPatientBinding binding, int patient, String recipient) {
        if (!binding.clinicId().equals(cfg.clinic) || binding.demographicNo() != patient
                || !binding.recipientId().equals(recipient) || !binding.publicBase().equals(cfg.publicBase)
                || !binding.internalOrigin().equals(cfg.internalOrigin) || !binding.configHash().equals(cfg.hash())) throw refuse("binding_changed");
    }
    private static void challenge(Current cfg, PortalMyCarlosDeviceApprovalChallenge row, int patient) {
        if (!row.clinicId().equals(cfg.clinic) || row.demographicNo() != patient
                || !row.publicBase().equals(cfg.publicBase) || !row.configHash().equals(cfg.hash())) throw refuse("binding_changed");
        Scope scope = cfg.scope(row.recipientId());
        Decoded i = PortalMyCarlosDeviceApprovalCodec.decode(Kind.INTENT, row.intent(), scope);
        Decoded c = PortalMyCarlosDeviceApprovalCodec.decode(Kind.CHALLENGE, row.signedChallenge(), scope);
        for (String field : new String[] {"clinic_id", "recipient_id", "public_portal_base_url", "request_id",
                "candidate_device_key_id", "candidate_x25519_public_key", "candidate_ed25519_public_key", "predecessor_device_key_id"}) {
            requireEqual(i.claims().values().get(field), c.claims().values().get(field));
        }
        requireEqual(i.candidate(), new DeviceKeys(row.candidateKid(), row.candidateX(), row.candidateEd()));
        requireEqual(c.claims().text("request_id"), row.requestId().toString());
        requireEqual(c.claims().text("challenge_id"), PortalMyCarlosDeviceApprovalCodec.base64(row.challengeId().copy()));
        requireEqual(c.claims().text("candidate_intent_sha256"), PortalMyCarlosDeviceApprovalCrypto.compactHash(row.intent()));
        requireEqual(c.claims().text("approval_kind"), row.kind());
        requireEqual(c.claims().text("predecessor_device_key_id"), row.predecessorKid());
        requireEqual(c.claims().text("predecessor_version"), row.predecessorVersion() == null ? null : row.predecessorVersion().toString());
        requireEqual(c.claims().epoch("issued_at"), row.issuedAt().getEpochSecond());
        requireEqual(c.claims().epoch("expires_at"), row.expiresAt().getEpochSecond());
        long issued = c.claims().epoch("issued_at");
        requireEqual(c.claims().epoch("expires_at"), Math.min(issued > Long.MAX_VALUE - 600 ? Long.MAX_VALUE : issued + 600, i.claims().epoch("exp")));
    }
    private static void requireEqual(Object left, Object right) {
        if (!Objects.equals(left, right)) throw refuse("invalid_proof");
    }
    private static Decoded verified(Kind kind, String compact, Scope scope,
            Map<String, PatientPortalClinicKeysPublishRequest.Key> clinicKeys, PortalMyCarlosAcceptedDevice predecessor) {
        try {
            DeviceKeys device = predecessor == null ? null : new DeviceKeys(predecessor.kid(), predecessor.x25519(), predecessor.ed25519());
            return PortalMyCarlosDeviceApprovalCrypto.verify(kind, compact, new TrustedKeys(scope, clinicKeys, device));
        } catch (IllegalArgumentException failure) { throw refuse("invalid_proof");
        }
    }
    private static void fresh(Decoded object, Instant now) {
        try { PortalMyCarlosDeviceApprovalCodec.requireFreshForNewAction(object, now.getEpochSecond());
        }
        catch (IllegalArgumentException failure) { throw refuse("challenge_expired");
        }
    }
    private PortalMyCarlosAcceptedDevice predecessor(PortalMyCarlosPatientBinding binding, PortalMyCarlosDeviceApprovalChallenge challenge) {
        var result = devices.findByKidForUpdate(challenge.predecessorKid()).orElseThrow(() -> refuse("predecessor_not_accepted"));
        if (result.bindingId() != binding.id() || result.id() != challenge.predecessorId()) throw refuse("predecessor_not_accepted");
        if (!result.current()) throw refuse("predecessor_removed");
        if (result.version() != challenge.predecessorVersion()) throw refuse("stale_predecessor");
        return result;
    }

    PortalMyCarlosDeviceApprovalChallenge createOrReadExactChallenge(int patient, String exactIntent) {
        return createOrReadExactChallenge(patient, exactIntent, false);
    }

    /**
     * @param byCode whether a first device (no predecessor) is to be approved with an emailed clinic
     *        code (kind {@code code}, mycarlos#141) instead of by staff; a re-link with a predecessor
     *        is {@code accepted_device} either way, and an existing challenge is returned unchanged
     */
    PortalMyCarlosDeviceApprovalChallenge createOrReadExactChallenge(int patient, String exactIntent, boolean byCode) {
        standalone();
        Checks.positive(patient);
        current();
        source.requireTrustedSource(patient);
        Memo memo = new Memo();
        return transactions.withSigningInventory(inventory -> {
            Current cfg = current();
            // Strict intent decoding supplies the recipient only as a candidate binding to recheck.
            String recipient;
            Decoded intent;
            try {
                var untrusted = PortalMyCarlosDeviceApprovalCodec.envelope(exactIntent);
                recipient = JSON.readTree(untrusted.payload().copy()).path("recipient_id").asText();
                intent = verified(Kind.INTENT, exactIntent, cfg.scope(recipient), Map.of(), null);
            } catch (java.io.IOException | IllegalArgumentException failure) { throw refuse("invalid_proof"); }
            UUID request = UUID.fromString(intent.claims().text("request_id"));
            var bound = bindings.findByClinicPatientForUpdate(cfg.clinic, patient);
            var prior = challenges.findByRequestForUpdate(cfg.clinic, request);
            if (prior.isPresent()) {
                challenge(cfg, prior.get(), patient);
                requireEqual(prior.get().intent(), exactIntent);
                if (bound.isPresent()) binding(cfg, bound.get(), patient, recipient);
                if (prior.get().bindingId() != null && (bound.isEmpty() || prior.get().bindingId() != bound.get().id())) throw refuse("binding_changed");
                source.requireTrustedSource(patient);
                requireEqual(cfg.hash(), current().hash());
                return prior.get();
            }
            var pending = challenges.findOpenForPatientForUpdate(cfg.clinic, patient);
            String predecessorKid = intent.claims().text("predecessor_device_key_id");
            PortalMyCarlosAcceptedDevice predecessor = null;
            if (bound.isEmpty()) {
                if (predecessorKid != null) throw refuse("predecessor_not_accepted");
                if (bindings.findByClinicRecipientForUpdate(cfg.clinic, recipient).isPresent()) throw refuse("binding_changed");
            } else {
                binding(cfg, bound.get(), patient, recipient);
                if (predecessorKid == null) throw refuse("staff_required");
                predecessor = devices.findByKidForUpdate(predecessorKid).orElseThrow(() -> refuse("predecessor_not_accepted"));
                if (predecessor.bindingId() != bound.get().id()) throw refuse("predecessor_not_accepted");
                if (!predecessor.current()) throw refuse("predecessor_removed");
            }
            if (devices.findByKidForUpdate(intent.candidate().kid()).isPresent()) throw refuse("approval_conflict");
            source.requireTrustedSource(patient);
            requireEqual(cfg.hash(), current().hash());
            for (var open : pending) {
                challenge(cfg, open, patient);
                if (outbox.findMessageForUpdate(open.challengeId(), "decision").isPresent()) throw refuse("approval_conflict");
            }
            var keys = catalogue(inventory, cfg);
            SigningIdentity identity = activeSigner(keys);
            Long predecessorVersion = predecessor == null ? null : predecessor.version();
            var context = List.of("challenge", cfg.hash(), exactIntent, patient, predecessor == null ? "" : predecessor, byCode,
                    identity, inventory.orElseThrow().generation(), inventory.orElseThrow().toStoredJson());
            Bytes nextId = memo.challenge(context, challengeIds);
            if (challenges.findByChallengeForUpdate(nextId).isPresent()
                    || outbox.findMessageForUpdate(nextId, "challenge").isPresent()) throw refuse("approval_conflict");
            source.requireTrustedSource(patient);
            requireEqual(cfg.hash(), current().hash());
            requireEqual(identity, activeSigner(keys));
            Instant now = now();
            fresh(intent, now);
            int live = 0;
            for (var open : pending) {
                challenge(cfg, open, patient);
                if (!now.isBefore(open.expiresAt())) cancel(open, now, "challenge_expired", cfg);
                else {
                    live++;
                    if (open.candidateKid().equals(intent.candidate().kid())) throw refuse("approval_conflict");
                }
            }
            if (live >= 3) throw refuse("approval_unavailable");
            String kind = predecessor != null ? "accepted_device" : byCode ? "code" : "staff_first";
            Material material = memo.material(context, () -> {
                Bytes id = nextId;
                long expiry = Math.min(now.getEpochSecond() > Long.MAX_VALUE - 600 ? Long.MAX_VALUE : now.getEpochSecond() + 600, intent.claims().epoch("exp"));
                var claims = common(Kind.CHALLENGE, cfg.scope(recipient), intent.candidate());
                claims.put("request_id", request.toString());
                claims.put("challenge_id", PortalMyCarlosDeviceApprovalCodec.base64(id.copy()));
                claims.put("candidate_intent_sha256", PortalMyCarlosDeviceApprovalCrypto.compactHash(exactIntent));
                claims.put("approval_kind", kind);
                claims.put("predecessor_device_key_id", predecessorKid);
                claims.put("predecessor_version", predecessorVersion == null ? null : predecessorVersion.toString());
                claims.put("issued_at", now.getEpochSecond());
                claims.put("expires_at", expiry);
                claims.put("kid", identity.kid());
                String compact = sign(Kind.CHALLENGE, claims, cfg.scope(recipient), identity, keys);
                return new Material(id, compact, now, Instant.ofEpochSecond(expiry));
            });
            if (!now.isBefore(material.expires()) || material.at().isAfter(now.plusSeconds(30))) throw refuse("challenge_expired");
            var row = new PortalMyCarlosDeviceApprovalChallenge(material.id(), request, bound.map(PortalMyCarlosPatientBinding::id).orElse(null),
                    cfg.clinic, patient, recipient, cfg.publicBase, cfg.hash(), intent.candidate().kid(), intent.candidate().x25519(), intent.candidate().ed25519(),
                    exactIntent, Checks.hash(exactIntent), material.compact(), Checks.hash(material.compact()), kind,
                    predecessor == null ? null : predecessor.id(), predecessorKid, predecessor == null ? null : predecessor.version(),
                    material.at(), material.expires(), null, null, null, null, null, null);
            challenge(cfg, row, patient);
            challenges.insert(row);
            enqueue(row, "challenge", json("intent", row.intent(), "challenge", row.signedChallenge()), cfg, now);
            return row;
        });
    }

    Decision acceptFirstFromStaff(LoggedInInfo session, int patient, Bytes challengeId, Bytes expectedChallengeHash, String exactFingerprint) {
        return acceptFirstFromStaff(session, patient, challengeId, expectedChallengeHash, exactFingerprint, row -> false);
    }

    /**
     * @param codeMatched read under the challenge's lock: whether a {@code code} challenge's emailed
     *        code already matched and the clinic's policy also asks for staff ({@code code_and_staff})
     */
    Decision acceptFirstFromStaff(LoggedInInfo session, int patient, Bytes challengeId, Bytes expectedChallengeHash,
            String exactFingerprint, java.util.function.Predicate<PortalMyCarlosDeviceApprovalChallenge> codeMatched) {
        standalone();
        current();
        staff(session, patient, SecurityInfoManager.WRITE);
        Memo memo = new Memo();
        return transactions.withSigningInventory(inventory -> {
            Current cfg = current();
            var row = challenges.findByChallengeForUpdate(challengeId).orElseThrow(() -> refuse("approval_not_found"));
            var bound = bindings.findByClinicPatientForUpdate(cfg.clinic, patient);
            challenge(cfg, row, patient);
            var candidate = devices.findByKidForUpdate(row.candidateKid());
            var recipientBinding = bindings.findByClinicRecipientForUpdate(cfg.clinic, row.recipientId());
            var priorDecision = outbox.findMessageForUpdate(row.challengeId(), "decision");
            Actor actor = staff(session, patient, SecurityInfoManager.WRITE);
            requireEqual(cfg.hash(), current().hash());
            requireEqual(row.challengeHash(), expectedChallengeHash);
            // A code challenge reaches staff only once its code matched under code_and_staff (mycarlos#141).
            if ("code".equals(row.kind()) ? !codeMatched.test(row) : !"staff_first".equals(row.kind())) throw refuse("staff_required");
            String fingerprint = HexFormat.of().formatHex(PortalMyCarlosDeviceApprovalCodec.exact32(row.candidateKid()));
            if (!fingerprint.equals(exactFingerprint)) throw new SecurityException("full device fingerprint confirmation required");
            if (row.consumedAt() != null) return historical(cfg, patient, row, bound, candidate, null, priorDecision);
            if (bound.isPresent()) throw refuse("staff_required");
            if (priorDecision.isPresent()) throw refuse("approval_conflict");
            if (row.cancelledAt() != null) throw refuse("challenge_cancelled");
            if (candidate.isPresent() || recipientBinding.isPresent()) throw refuse("binding_changed");
            var keys = catalogue(inventory, cfg);
            SigningIdentity identity = activeSigner(keys);
            Instant now = now();
            verifyNew(row, keys, null, cfg, now);
            Material acceptance = acceptance(row, null, actor, cfg, inventory.orElseThrow(), identity, keys, now, memo);
            var newBinding = bindings.insert(new PortalMyCarlosPatientBinding(0, cfg.clinic, patient, row.recipientId(),
                    cfg.publicBase, cfg.internalOrigin, cfg.hash(), row.candidateKid(), now, 0));
            return commitAcceptance(row, newBinding, null, actor, acceptance, cfg, now);
        });
    }

    /**
     * Accepts a first device whose emailed clinic code matched (policy {@code code}, mycarlos#141).
     * No staff member is involved: the code went only to the patient's chart email. The caller's
     * source authorization (the device-code job) stands in for staff on the patient's page.
     *
     * @param codeMatched read under the challenge's lock: whether its code matched and the policy
     *        needs no staff step
     */
    Decision acceptFromCode(int patient, Bytes challengeId,
            java.util.function.Predicate<PortalMyCarlosDeviceApprovalChallenge> codeMatched) {
        standalone();
        Checks.positive(patient);
        current();
        source.requireTrustedSource(patient);
        Memo memo = new Memo();
        return transactions.withSigningInventory(inventory -> {
            Current cfg = current();
            var row = challenges.findByChallengeForUpdate(challengeId).orElseThrow(() -> refuse("approval_not_found"));
            var bound = bindings.findByClinicPatientForUpdate(cfg.clinic, patient);
            challenge(cfg, row, patient);
            var candidate = devices.findByKidForUpdate(row.candidateKid());
            var recipientBinding = bindings.findByClinicRecipientForUpdate(cfg.clinic, row.recipientId());
            var priorDecision = outbox.findMessageForUpdate(row.challengeId(), "decision");
            source.requireTrustedSource(patient);
            requireEqual(cfg.hash(), current().hash());
            if (!"code".equals(row.kind()) || !codeMatched.test(row)) throw refuse("staff_required");
            if (row.consumedAt() != null) return historical(cfg, patient, row, bound, candidate, null, priorDecision);
            if (bound.isPresent()) throw refuse("staff_required");
            if (priorDecision.isPresent()) throw refuse("approval_conflict");
            if (row.cancelledAt() != null) throw refuse("challenge_cancelled");
            if (candidate.isPresent() || recipientBinding.isPresent()) throw refuse("binding_changed");
            var keys = catalogue(inventory, cfg);
            SigningIdentity identity = activeSigner(keys);
            Instant now = now();
            verifyNew(row, keys, null, cfg, now);
            Material acceptance = acceptance(row, null, null, cfg, inventory.orElseThrow(), identity, keys, now, memo);
            var newBinding = bindings.insert(new PortalMyCarlosPatientBinding(0, cfg.clinic, patient, row.recipientId(),
                    cfg.publicBase, cfg.internalOrigin, cfg.hash(), row.candidateKid(), now, 0));
            return commitAcceptance(row, newBinding, null, null, acceptance, cfg, now);
        });
    }

    /**
     * Ends an open clinic code request for good (mycarlos#141): the challenge is cancelled and its
     * decision queued. {@code code_attempts} after the fifth wrong code ({@code refused});
     * {@code challenge_expired} when its code can no longer be checked here, as after a restart
     * ({@code expired}, and the device offers a new request). Repeating it once done changes nothing.
     *
     * @return whether this call ended the request (false when it already had ended so)
     */
    boolean endCodeChallenge(int patient, Bytes challengeId, String reason) {
        standalone();
        Checks.positive(patient);
        Checks.require("code_attempts".equals(reason) || "challenge_expired".equals(reason));
        current();
        source.requireTrustedSource(patient);
        return transactions.withApprovalState(() -> {
            Current cfg = current();
            var row = challenges.findByChallengeForUpdate(challengeId).orElseThrow(() -> refuse("approval_not_found"));
            challenge(cfg, row, patient);
            var priorDecision = outbox.findMessageForUpdate(row.challengeId(), "decision");
            source.requireTrustedSource(patient);
            requireEqual(cfg.hash(), current().hash());
            if (!"code".equals(row.kind())) throw refuse("approval_conflict");
            if (!row.pending()) {
                if (reason.equals(row.cancellationReason())) return false;
                throw refuse("approval_conflict");
            }
            if (priorDecision.isPresent()) throw refuse("approval_conflict");
            cancel(row, now(), reason, cfg);
            return true;
        });
    }

    /** Why a clinic code request was cancelled when its code could not be emailed (mycarlos#141). */
    static final String CODE_NOT_SENT = "code_not_sent";

    /**
     * Cancels a clinic code request whose code could not be emailed (mycarlos#141), reason
     * {@value #CODE_NOT_SENT}. The portal never got its challenge and refuses a decision for a
     * request without one, so none is queued: the portal request runs out by itself, and the
     * patient's next first-device request goes to staff. Repeating it once done changes nothing.
     *
     * @return whether this call ended the request (false when it already had ended so)
     */
    boolean endUnsentCodeChallenge(int patient, Bytes challengeId) {
        standalone();
        Checks.positive(patient);
        current();
        source.requireTrustedSource(patient);
        return transactions.withApprovalState(() -> {
            Current cfg = current();
            var row = challenges.findByChallengeForUpdate(challengeId).orElseThrow(() -> refuse("approval_not_found"));
            challenge(cfg, row, patient);
            var challengeMessage = outbox.findMessageForUpdate(row.challengeId(), "challenge");
            var priorDecision = outbox.findMessageForUpdate(row.challengeId(), "decision");
            source.requireTrustedSource(patient);
            requireEqual(cfg.hash(), current().hash());
            if (!"code".equals(row.kind())) throw refuse("approval_conflict");
            if (!row.pending()) {
                if (CODE_NOT_SENT.equals(row.cancellationReason())) return false;
                throw refuse("approval_conflict");
            }
            // A challenge the portal may already have must end with a decision instead.
            if (priorDecision.isPresent() || challengeMessage.isPresent()
                    && (challengeMessage.get().publishedAt() != null || challengeMessage.get().leaseToken() != null)) {
                throw refuse("approval_conflict");
            }
            challenges.terminal(row, row.cancelled(now(), CODE_NOT_SENT));
            return true;
        });
    }

    Decision acceptFromCurrentPredecessor(int patient, Bytes challengeId, String exactApproval) {
        standalone();
        Checks.positive(patient);
        current();
        source.requireTrustedSource(patient);
        Checks.text(exactApproval, 8192);
        Checks.require(exactApproval.chars().allMatch(c -> c < 128));
        Memo memo = new Memo();
        return transactions.withSigningInventory(inventory -> {
            Current cfg = current();
            var row = challenges.findByChallengeForUpdate(challengeId).orElseThrow(() -> refuse("approval_not_found"));
            var bound = bindings.findByClinicPatientForUpdate(cfg.clinic, patient);
            challenge(cfg, row, patient);
            var candidate = devices.findByKidForUpdate(row.candidateKid());
            var priorDecision = outbox.findMessageForUpdate(row.challengeId(), "decision");
            source.requireTrustedSource(patient);
            requireEqual(cfg.hash(), current().hash());
            if (!"accepted_device".equals(row.kind())) throw refuse("staff_required");
            if (row.consumedAt() != null) return historical(cfg, patient, row, bound, candidate, exactApproval, priorDecision);
            if (row.cancelledAt() != null) throw refuse("challenge_cancelled");
            if (priorDecision.isPresent()) throw refuse("approval_conflict");
            var binding = bound.orElseThrow(() -> refuse("predecessor_not_accepted"));
            binding(cfg, binding, patient, row.recipientId());
            if (row.bindingId() == null || row.bindingId() != binding.id()) throw refuse("binding_changed");
            var predecessor = predecessor(binding, row);
            var keys = catalogue(inventory, cfg);
            var proof = verified(Kind.APPROVAL, exactApproval, cfg.scope(row.recipientId()), keys, predecessor);
            compareApproval(row, proof);
            UUID jti = UUID.fromString(proof.claims().text("jti"));
            if (candidate.isPresent() && candidate.get().bindingId() != binding.id()) throw refuse("binding_changed");
            if (challenges.findByConsumedJtiForUpdate(predecessor.id(), jti).isPresent() || candidate.isPresent()) throw refuse("approval_conflict");
            outbox.findMessageForUpdate(row.challengeId(), "decision");
            source.requireTrustedSource(patient);
            requireEqual(cfg.hash(), current().hash());
            SigningIdentity identity = activeSigner(keys);
            Instant now = now();
            verifyNew(row, keys, predecessor, cfg, now);
            Material acceptance = acceptance(row, exactApproval, null, cfg, inventory.orElseThrow(), identity, keys, now, memo);
            return commitAcceptance(row, binding, exactApproval, null, acceptance, cfg, now);
        });
    }

    private static void compareApproval(PortalMyCarlosDeviceApprovalChallenge row, Decoded proof) {
        var p = proof.claims();
        requireEqual(p.text("challenge_id"), PortalMyCarlosDeviceApprovalCodec.base64(row.challengeId().copy()));
        requireEqual(p.text("challenge_sha256"), PortalMyCarlosDeviceApprovalCrypto.compactHash(row.signedChallenge()));
        requireEqual(p.text("candidate_intent_sha256"), PortalMyCarlosDeviceApprovalCrypto.compactHash(row.intent()));
        requireEqual(proof.candidate(), new DeviceKeys(row.candidateKid(), row.candidateX(), row.candidateEd()));
        requireEqual(p.text("predecessor_device_key_id"), row.predecessorKid());
        requireEqual(p.text("predecessor_version"), row.predecessorVersion().toString());
    }
    private static void verifyNew(PortalMyCarlosDeviceApprovalChallenge row,
            Map<String, PatientPortalClinicKeysPublishRequest.Key> keys, PortalMyCarlosAcceptedDevice predecessor, Current cfg, Instant now) {
        fresh(verified(Kind.INTENT, row.intent(), cfg.scope(row.recipientId()), keys, null), now);
        fresh(verified(Kind.CHALLENGE, row.signedChallenge(), cfg.scope(row.recipientId()), keys, predecessor), now);
        if (!now.isBefore(row.expiresAt()) || now.isBefore(row.issuedAt())) throw refuse("challenge_expired");
    }
    private Material acceptance(PortalMyCarlosDeviceApprovalChallenge row, String approval, Actor actor, Current cfg,
            PortalMyCarlosKeyInventory inventory, SigningIdentity identity,
            Map<String, PatientPortalClinicKeysPublishRequest.Key> keys, Instant now, Memo memo) {
        var context = List.of("acceptance", cfg.hash(), row, approval == null ? "" : approval, actor == null ? "" : actor,
                identity, inventory.generation(), inventory.toStoredJson());
        Material material = memo.material(context, () -> {
            var claims = common(Kind.ACCEPTANCE, cfg.scope(row.recipientId()), new DeviceKeys(row.candidateKid(), row.candidateX(), row.candidateEd()));
            claims.put("request_id", row.requestId().toString());
            claims.put("challenge_id", PortalMyCarlosDeviceApprovalCodec.base64(row.challengeId().copy()));
            claims.put("challenge_sha256", PortalMyCarlosDeviceApprovalCrypto.compactHash(row.signedChallenge()));
            claims.put("approval_kind", row.kind());
            claims.put("predecessor_device_key_id", row.predecessorKid());
            claims.put("approval_sha256", approval == null ? null : PortalMyCarlosDeviceApprovalCrypto.compactHash(approval));
            claims.put("accepted_at", now.getEpochSecond());
            claims.put("accepted_version", "0");
            claims.put("kid", identity.kid());
            return new Material(row.challengeId(), sign(Kind.ACCEPTANCE, claims, cfg.scope(row.recipientId()), identity, keys), now, row.expiresAt());
        });
        if (!now.isBefore(material.expires()) || material.at().isAfter(now.plusSeconds(30))) throw refuse("challenge_expired");
        return material;
    }
    private String sign(Kind kind, Map<String, Object> claims, Scope scope, SigningIdentity identity,
            Map<String, PatientPortalClinicKeysPublishRequest.Key> keys) {
        String compact = signer.sign(kind, java.util.Collections.unmodifiableMap(new LinkedHashMap<>(claims)), scope, identity);
        var decoded = verified(kind, compact, scope, keys, null);
        requireEqual(decoded.claims().values(), claims);
        return compact;
    }
    private Decision commitAcceptance(PortalMyCarlosDeviceApprovalChallenge row, PortalMyCarlosPatientBinding binding,
            String approval, Actor actor, Material acceptance, Current cfg, Instant now) {
        UUID jti = approval == null ? null : UUID.fromString(PortalMyCarlosDeviceApprovalCodec.decode(Kind.APPROVAL, approval, cfg.scope(row.recipientId())).claims().text("jti"));
        challenges.terminal(row, row.consumed(binding.id(), approval, jti, now));
        var device = devices.insert(new PortalMyCarlosAcceptedDevice(0, binding.id(), row.candidateKid(), row.candidateX(), row.candidateEd(),
                row.kind(), row.challengeId(), actor == null ? null : actor.provider(), actor == null ? null : actor.securityNo(),
                actor == null ? null : "in_person_device_comparison", row.predecessorId(), row.predecessorVersion(), approval,
                acceptance.compact(), Checks.hash(acceptance.compact()), acceptance.at(), null, null, null, null, 0));
        enqueue(row, "decision", json("challenge_id", PortalMyCarlosDeviceApprovalCodec.base64(row.challengeId().copy()),
                "status", "accepted", "acceptance", acceptance.compact(), "reason", null), cfg, now);
        afterCommit(new Audit(cfg.clinic, binding.id(), device.id(), "accepted", actor == null ? null : actor.provider()));
        return new Decision(device, false);
    }
    private Decision historical(Current cfg, int patient, PortalMyCarlosDeviceApprovalChallenge challenge,
            Optional<PortalMyCarlosPatientBinding> bound, Optional<PortalMyCarlosAcceptedDevice> candidate, String approval,
            Optional<PortalMyCarlosDeviceApprovalOutboxEntry> decision) {
        var binding = bound.orElseThrow(() -> refuse("binding_changed"));
        binding(cfg, binding, patient, challenge.recipientId());
        var accepted = candidate.orElseThrow(() -> refuse("approval_conflict"));
        if (challenge.bindingId() == null || challenge.bindingId() != binding.id() || accepted.bindingId() != binding.id()) throw refuse("binding_changed");
        requireEqual(accepted.kid(), challenge.candidateKid());
        requireEqual(accepted.x25519(), challenge.candidateX());
        requireEqual(accepted.ed25519(), challenge.candidateEd());
        if (!challenge.kind().equals("accepted_device")) requireEqual(binding.rootKid(), accepted.kid());
        requireEqual(accepted.challengeId(), challenge.challengeId());
        requireEqual(accepted.kind(), challenge.kind());
        requireEqual(accepted.predecessorId(), challenge.predecessorId());
        requireEqual(accepted.predecessorVersion(), challenge.predecessorVersion());
        if (!Objects.equals(approval, challenge.consumedApproval()) || !Objects.equals(approval, accepted.predecessorApproval())) throw refuse("approval_conflict");
        var receipt = PortalMyCarlosDeviceApprovalCodec.decode(Kind.ACCEPTANCE, accepted.acceptance(), cfg.scope(challenge.recipientId()));
        requireEqual(receipt.candidate(), new DeviceKeys(accepted.kid(), accepted.x25519(), accepted.ed25519()));
        requireEqual(receipt.claims().text("request_id"), challenge.requestId().toString());
        requireEqual(receipt.claims().text("challenge_id"), PortalMyCarlosDeviceApprovalCodec.base64(challenge.challengeId().copy()));
        requireEqual(receipt.claims().text("challenge_sha256"), PortalMyCarlosDeviceApprovalCrypto.compactHash(challenge.signedChallenge()));
        requireEqual(receipt.claims().text("approval_kind"), challenge.kind());
        requireEqual(receipt.claims().text("predecessor_device_key_id"), challenge.predecessorKid());
        requireEqual(receipt.claims().text("approval_sha256"), approval == null ? null : PortalMyCarlosDeviceApprovalCrypto.compactHash(approval));
        requireEqual(receipt.claims().text("accepted_version"), "0");
        requireEqual(receipt.claims().epoch("accepted_at"), accepted.acceptedAt().getEpochSecond());
        var saved = decision.orElseThrow(() -> refuse("approval_unavailable"));
        Bytes body = json("challenge_id", PortalMyCarlosDeviceApprovalCodec.base64(challenge.challengeId().copy()),
                "status", "accepted", "acceptance", accepted.acceptance(), "reason", null);
        if (!sameMessage(saved, message(challenge, "decision", body, cfg, accepted.acceptedAt()))) throw refuse("approval_conflict");
        // Saved evidence was authenticated at original admission. No fresh signer/predecessor/expiry
        // requirement, no remint, no outbox duplicate, and removed state remains removed.
        return new Decision(accepted, true);
    }

    PortalMyCarlosAcceptedDevice removeFromStaff(LoggedInInfo session, int patient, String kid) {
        standalone();
        current();
        staff(session, patient, SecurityInfoManager.WRITE);
        Checks.keyId(kid);
        Memo memo = new Memo();
        return transactions.withApprovalState(() -> {
            Current cfg = current();
            var binding = bindings.findByClinicPatientForUpdate(cfg.clinic, patient).orElseThrow(() -> refuse("approval_not_found"));
            binding(cfg, binding, patient, binding.recipientId());
            var pending = challenges.findOpenForPatientForUpdate(cfg.clinic, patient);
            var device = devices.findByKidForUpdate(kid).orElseThrow(() -> refuse("approval_not_found"));
            if (device.bindingId() != binding.id()) throw refuse("approval_not_found");
            var original = challenges.findByChallengeForUpdate(device.challengeId()).orElseThrow(() -> refuse("approval_unavailable"));
            challenge(cfg, original, patient);
            var originalRemoval = outbox.findMessageForUpdate(original.challengeId(), "removal");
            for (var open : pending) {
                challenge(cfg, open, patient);
                outbox.findMessageForUpdate(open.challengeId(), "decision");
            }
            Actor actor = staff(session, patient, SecurityInfoManager.WRITE);
            requireEqual(cfg.hash(), current().hash());
            if (!device.current()) {
                Bytes savedBody = removalBody(device.removalId(), patient, binding.recipientId(), kid, device.removedAt());
                if (originalRemoval.isEmpty() || !sameMessage(originalRemoval.get(), message(original, "removal", savedBody, cfg, device.removedAt()))) throw refuse("approval_conflict");
                return device;
            }
            if (originalRemoval.isPresent()) throw refuse("approval_conflict");
            Instant now = now();
            RemovalMaterial material = memo.removal(List.of("removal", cfg.hash(), device, actor), removalIds, now);
            if (material.at().isAfter(now.plusSeconds(30))) throw refuse("approval_unavailable");
            UUID command = material.id();
            var removed = device.removed(command, actor.provider(), material.at());
            devices.markRemoved(device, removed);
            for (var open : pending) if (Objects.equals(open.predecessorKid(), kid) || open.candidateKid().equals(kid)) cancel(open, now, "predecessor_removed", cfg);
            enqueue(original, "removal", removalBody(command, patient, binding.recipientId(), kid, material.at()), cfg, material.at());
            afterCommit(new Audit(cfg.clinic, binding.id(), device.id(), "removed", actor.provider()));
            return removed;
        });
    }
    History readCurrentAndRetainedHistory(LoggedInInfo session, int patient, long afterId, int limit) {
        standalone();
        current();
        staff(session, patient, SecurityInfoManager.READ);
        Checks.nonnegative(afterId);
        Checks.require(limit >= 1 && limit <= 50);
        return transactions.withApprovalState(() -> {
            Current cfg = current();
            var binding = bindings.findByClinicPatientForUpdate(cfg.clinic, patient);
            if (binding.isEmpty()) { staff(session, patient, SecurityInfoManager.READ);
                requireEqual(cfg.hash(), current().hash());
                return new History(List.of(), false);
            }
            binding(cfg, binding.get(), patient, binding.get().recipientId());
            var rows = devices.historyForUpdate(binding.get().id(), afterId, limit + 1);
            staff(session, patient, SecurityInfoManager.READ);
            requireEqual(cfg.hash(), current().hash());
            return new History(rows.subList(0, Math.min(limit, rows.size())), rows.size() > limit);
        });
    }
    List<PortalMyCarlosDeviceApprovalChallenge> readPendingForStaff(LoggedInInfo session, int patient) {
        standalone();
        current();
        staff(session, patient, SecurityInfoManager.READ);
        return transactions.withApprovalState(() -> {
            Current cfg = current();
            var rows = challenges.findOpenForPatientForUpdate(cfg.clinic, patient);
            for (var row : rows) challenge(cfg, row, patient);
            staff(session, patient, SecurityInfoManager.READ);
            requireEqual(cfg.hash(), current().hash());
            return List.copyOf(rows);
        });
    }

    private void cancel(PortalMyCarlosDeviceApprovalChallenge row, Instant now, String reason, Current cfg) {
        challenges.terminal(row, row.cancelled(now, reason));
        enqueue(row, "decision", json("challenge_id", PortalMyCarlosDeviceApprovalCodec.base64(row.challengeId().copy()),
                "status", reason.equals("challenge_expired") ? "expired" : reason.equals("code_attempts") ? "refused" : "cancelled",
                "acceptance", null, "reason", reason), cfg, now);
    }
    private static Bytes removalBody(UUID command, int patient, String recipient, String kid, Instant at) {
        return json("removal_id", command.toString(), "demographic_no", patient, "recipient_id", recipient,
                "device_key_id", kid, "authorized_at", at.getEpochSecond(), "reason", "staff");
    }
    private static PortalMyCarlosDeviceApprovalOutboxEntry message(PortalMyCarlosDeviceApprovalChallenge row,
            String kind, Bytes body, Current cfg, Instant now) {
        if (!cfg.hash().equals(row.configHash())) throw refuse("binding_changed");
        String path = kind.equals("removal") ? "/internal/carlos/mycarlos/device-removals"
                : "/internal/carlos/mycarlos/device-approval-requests/" + row.requestId() + (kind.equals("challenge") ? "/challenge" : "/decision");
        String target = URI.create(cfg.internalOrigin).resolve(path).toASCIIString();
        return new PortalMyCarlosDeviceApprovalOutboxEntry(0, row.challengeId(), kind, cfg.clinic, target,
                cfg.hash(), body, Checks.hashBytes(body.copy()), now, now, 0, null, null, null, 0);
    }
    private static boolean sameMessage(PortalMyCarlosDeviceApprovalOutboxEntry saved, PortalMyCarlosDeviceApprovalOutboxEntry expected) {
        return saved.challengeId().equals(expected.challengeId()) && saved.messageKind().equals(expected.messageKind())
                && saved.clinicId().equals(expected.clinicId()) && saved.targetUri().equals(expected.targetUri())
                && saved.configHash().equals(expected.configHash()) && saved.body().equals(expected.body()) && saved.bodyHash().equals(expected.bodyHash());
    }
    private void enqueue(PortalMyCarlosDeviceApprovalChallenge row, String kind, Bytes body, Current cfg, Instant now) {
        var expected = message(row, kind, body, cfg, now);
        var prior = outbox.findMessageForUpdate(row.challengeId(), kind);
        if (prior.isPresent()) {
            if (!sameMessage(prior.get(), expected)) throw refuse("approval_conflict");
        } else outbox.insert(expected);
    }
    private static LinkedHashMap<String, Object> common(Kind kind, Scope scope, DeviceKeys candidate) {
        var claims = new LinkedHashMap<String, Object>();
        claims.put("v", 1L);
        claims.put("typ", kind.typ);
        claims.put("aud", kind.audience);
        claims.put("clinic_id", scope.clinicId());
        claims.put("recipient_id", scope.recipientId());
        claims.put("public_portal_base_url", scope.canonicalPublicBase());
        claims.put("candidate_device_key_id", candidate.kid());
        claims.put("candidate_x25519_public_key", PortalMyCarlosDeviceApprovalCodec.base64(candidate.x25519().copy()));
        claims.put("candidate_ed25519_public_key", PortalMyCarlosDeviceApprovalCodec.base64(candidate.ed25519().copy()));
        return claims;
    }
    private static Bytes json(Object... fields) {
        var value = new LinkedHashMap<String, Object>();
        for (int i = 0; i < fields.length; i += 2) value.put((String) fields[i], fields[i + 1]);
        try {
            byte[] bytes = JSON.writeValueAsBytes(value);
            Checks.require(bytes.length <= 32768);
            return new Bytes(bytes);
        } catch (java.io.IOException failure) { throw refuse("approval_unavailable");
        }
    }
    private void afterCommit(Audit event) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override public void afterCommit() { audit.accept(event);
            }
        });
    }
    private record Material(Bytes id, String compact, Instant at, Instant expires) {}
    private record RemovalMaterial(UUID id, Instant at) {}
    /** Public exact bytes only; new authority/CAS/freshness checks always precede lookup. */
    private static final class Memo {
        private List<?> context;
        private Material material;
        private RemovalMaterial removal;
        private Bytes challenge;
        private void context(List<?> supplied) {
            if (context == null) context = List.copyOf(supplied);
            else if (!context.equals(supplied)) throw refuse("approval_conflict");
        }
        Material material(List<?> supplied, Supplier<Material> prepare) {
            context(supplied);
            if (material == null) {
                try { material = Objects.requireNonNull(prepare.get());
                }
                catch (RuntimeException | Error failure) { PortalMyCarlosInventoryTransactions.preparationFailed();
                    throw failure;
                }
            }
            return material;
        }
        Bytes challenge(List<?> supplied, Supplier<Bytes> prepare) {
            context(supplied);
            if (challenge == null) {
                try { challenge = prepare.get();
                    Checks.bytes(challenge, 32);
                }
                catch (RuntimeException | Error failure) { PortalMyCarlosInventoryTransactions.preparationFailed();
                    throw failure;
                }
            }
            return challenge;
        }
        RemovalMaterial removal(List<?> supplied, Supplier<UUID> prepare, Instant now) {
            context(supplied);
            if (removal == null) {
                try { UUID id = prepare.get();
                    Checks.uuid(id);
                    removal = new RemovalMaterial(id, now);
                }
                catch (RuntimeException | Error failure) { PortalMyCarlosInventoryTransactions.preparationFailed();
                    throw failure;
                }
            }
            return removal;
        }
    }

    /** Pure row validation and conversions; no storage, authority, settings or network access. */
    static final class Checks {
        private Checks() {}
        static void require(boolean condition) { if (!condition) throw new IllegalArgumentException("device approval state is invalid");
        }
        static void positive(long value) { require(value > 0);
        }
        static void nonnegative(long value) { require(value >= 0);
        }
        static void nullablePositive(Long value) { if (value != null) positive(value);
        }
        static byte[] utf8(String text) { return PortalMyCarlosDeviceApprovalCodec.utf8(text);
        }
        static void text(String value, int maxBytes) { require(value != null && !value.isEmpty() && utf8(value).length <= maxBytes);
        }
        static void scope(String clinic, String recipient, String base) { new Scope(clinic, recipient, base);
        }
        static void bytes(Bytes value, int count) { require(value != null && value.size() == count);
        }
        static void keyId(String value) { PortalMyCarlosDeviceApprovalCodec.exact32(value);
        }
        static void keys(String kid, Bytes x, Bytes ed) { new DeviceKeys(kid, x, ed);
        }
        static void approvalKind(String kind) { require("staff_first".equals(kind) || "accepted_device".equals(kind) || "code".equals(kind));
        }
        static void uuid(UUID value) { require(value != null && value.version() == 4 && value.variant() == 2);
        }
        static void time(Instant value) { require(value != null && value.getEpochSecond() >= 0 && value.getNano() % 1000 == 0);
        }
        static void second(Instant value) { time(value);
            require(value.getNano() == 0);
        }
        static void compact(String value) { PortalMyCarlosDeviceApprovalCodec.envelope(value);
        }
        static Bytes hash(String compact) { return new Bytes(HexFormat.of().parseHex(PortalMyCarlosDeviceApprovalCrypto.compactHash(compact)));
        }
        static Bytes hashBytes(byte[] value) { return new Bytes(HexFormat.of().parseHex(PortalMyCarlosDeviceApprovalCrypto.sha256(value)));
        }
        static void digest(String compact, Bytes expected) { bytes(expected, 32);
            require(hash(compact).equals(expected));
        }
    }
    /** Shared connection policy only. SQL/table statements live exclusively in their four DAOs. */
    static final class Jdbc {
        private Jdbc() {}
        @FunctionalInterface interface Parameters { void bind(PreparedStatement statement) throws SQLException;
        }
        static <T> T owned(EntityManagerFactory factory, ReturningWork<T> work) {
            PortalMyCarlosInventoryTransactions.requireApprovalOwnedTransaction();
            Object resource = TransactionSynchronizationManager.getResource(factory);
            if (!(resource instanceof EntityManagerHolder holder) || !holder.isSynchronizedWithTransaction() || !holder.getEntityManager().isOpen()) throw new IllegalStateException("transaction-owned approval EntityManager required");
            if (TransactionSynchronizationManager.getResourceMap().values().stream().filter(value -> value instanceof EntityManagerHolder).count() != 1) throw new IllegalStateException("one transaction-owned approval EntityManager required");
            return holder.getEntityManager().unwrap(Session.class).doReturningWork(work);
        }
        static Calendar utc() { return Calendar.getInstance(TimeZone.getTimeZone("UTC"));
        }
        static Instant instant(ResultSet result, String name) throws SQLException {
            Timestamp value = result.getTimestamp(name, utc());
            return value == null ? null : value.toInstant();
        }
        static void timestamp(PreparedStatement statement, int index, Instant value) throws SQLException {
            statement.setTimestamp(index, value == null ? null : Timestamp.from(value), utc());
        }
        static Bytes bytes(byte[] value) { return value == null ? null : new Bytes(value);
        }
        static byte[] raw(Bytes value) { return value == null ? null : value.copy();
        }
        static String ascii(byte[] value) {
            if (value == null) return null;
            for (byte b : value) Checks.require((b & 0x80) == 0);
            return new String(value, StandardCharsets.US_ASCII);
        }
        static byte[] ascii(String value) {
            if (value == null) return null;
            Checks.require(value.chars().allMatch(c -> c < 128));
            return value.getBytes(StandardCharsets.US_ASCII);
        }
        static UUID uuid(byte[] value) {
            if (value == null) return null;
            Checks.require(value.length == 16);
            var bytes = ByteBuffer.wrap(value);
            UUID uuid = new UUID(bytes.getLong(), bytes.getLong());
            Checks.uuid(uuid);
            return uuid;
        }
        static byte[] uuid(UUID value) {
            return value == null ? null : ByteBuffer.allocate(16).putLong(value.getMostSignificantBits()).putLong(value.getLeastSignificantBits()).array();
        }
        static void changed(int count) { if (count != 1) throw refuse("approval_conflict");
        }
    }
}
