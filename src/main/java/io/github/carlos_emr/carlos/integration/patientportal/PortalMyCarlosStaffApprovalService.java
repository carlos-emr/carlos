/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Bytes;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Kind;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosDeviceApprovalTypes.Scope;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFSigningConfig;
import io.github.carlos_emr.carlos.utility.PDFSigningUtil;
import jakarta.persistence.EntityManagerFactory;
import java.net.URI;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Service;

/** Staff application boundary: explicit polling and durable local approval before portal publication. */
@Service
public final class PortalMyCarlosStaffApprovalService {
    private static final String ENABLED = "patient_portal.mycarlos_approval.enabled";
    private final PortalMyCarlosDeviceApprovalTransactions transactions;
    private final EntityManagerFactory factory;
    private final SecurityInfoManager security;
    private final PortalMyCarlosApprovalJournal journal;
    private final java.util.function.Supplier<PortalMyCarlosDeviceApprovalService.Current> suppliedCurrent;
    private final java.util.function.BiFunction<PortalMyCarlosDeviceApprovalService.SourceAuthorization,
            java.util.function.Supplier<PortalMyCarlosDeviceApprovalService.Current>, PortalMyCarlosDeviceApprovalService> suppliedCore;
    private final java.util.function.Function<PatientPortalService, PortalMyCarlosApprovalClient> suppliedClient;
    private final SecureRandom random = new SecureRandom();

    @org.springframework.beans.factory.annotation.Autowired
    public PortalMyCarlosStaffApprovalService(PortalMyCarlosDeviceApprovalTransactions transactions,
            EntityManagerFactory factory, SecurityInfoManager security, PropertyDao properties) {
        this(transactions, factory, security, properties, null, null, null);
    }
    PortalMyCarlosStaffApprovalService(PortalMyCarlosDeviceApprovalTransactions transactions,
            EntityManagerFactory factory, SecurityInfoManager security, PropertyDao properties,
            java.util.function.Supplier<PortalMyCarlosDeviceApprovalService.Current> suppliedCurrent,
            java.util.function.BiFunction<PortalMyCarlosDeviceApprovalService.SourceAuthorization,
                    java.util.function.Supplier<PortalMyCarlosDeviceApprovalService.Current>, PortalMyCarlosDeviceApprovalService> suppliedCore,
            java.util.function.Function<PatientPortalService, PortalMyCarlosApprovalClient> suppliedClient) {
        this.suppliedCurrent = suppliedCurrent;
        this.suppliedCore = suppliedCore;
        this.suppliedClient = suppliedClient;
        this.transactions = Objects.requireNonNull(transactions);
        this.factory = Objects.requireNonNull(factory);
        this.security = Objects.requireNonNull(security);
        this.journal = new PortalMyCarlosApprovalJournal(transactions, properties);
    }

    public record Pending(String challengeId, String challengeSha256, String fingerprint, Instant expiresAt,
            boolean firstDevice, boolean expired) {}
    public record Device(String deviceKeyId, String fingerprint, Instant acceptedAt, Instant removedAt) {}
    public record State(List<Pending> pending, List<Device> devices, List<String> refusedRequests) {
        public State { pending = List.copyOf(pending);
            devices = List.copyOf(devices);
            refusedRequests = List.copyOf(refusedRequests); }
    }

    private void requireStaff(LoggedInInfo info, int patient, String level) {
        if (patient <= 0 || info == null
                || !security.hasPrivilege(info, "_demographic", SecurityInfoManager.READ, Integer.toString(patient))
                || !security.isAllowedAccessToPatientRecord(info, patient)
                || !security.hasPrivilege(info, PortalMyCarlosDeviceApprovalService.OBJECT, level, Integer.toString(patient))) {
            throw new SecurityException("missing required sec object (_portal.mycarlos.device_approval)");
        }
    }

    public static boolean isEnabled() {
        String enabled = PatientPortalSettings.rawProperty(ENABLED);
        if (enabled == null || "false".equals(enabled)) return false;
        if (!"true".equals(enabled)) throw new PatientPortalConfigurationException(ENABLED + " must be true or false");
        if (!PatientPortalSettings.isConfigured() || !PortalMyCarlosDeliverySettings.isEnabled()) return false;
        canonicalDemoPublicBase(PatientPortalSettings.rawProperty(PortalInviteSettings.PUBLIC_BASE_URL_KEY));
        return true;
    }

    private PortalMyCarlosDeviceApprovalService.Current current() {
        if (suppliedCurrent != null) return Objects.requireNonNull(suppliedCurrent.get());
        if (!isEnabled()) throw new IllegalStateException("approval_unavailable");
        String publicBase = canonicalDemoPublicBase(PatientPortalSettings.rawProperty(PortalInviteSettings.PUBLIC_BASE_URL_KEY));
        return PortalMyCarlosDeviceApprovalService.Current.fromValidated(
                PatientPortalSettings.fromCarlosProperties(), publicBase, true, true);
    }

    /** The first synthetic milestone deliberately enables only this exact canonical URL subset. */
    static String canonicalDemoPublicBase(String raw) {
        try {
            if (raw == null || raw.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 1024
                    || !raw.matches("https://localhost(?::[1-9][0-9]{0,4})?(?:/[A-Za-z0-9_-]++)*+")) {
                throw new IllegalArgumentException();
            }
            URI uri = URI.create(raw);
            if (uri.getPort() == 443 || uri.getPort() > 65535) throw new IllegalArgumentException();
            return raw;
        } catch (IllegalArgumentException failure) {
            throw new PatientPortalConfigurationException("myCarlos approval requires an already canonical localhost HTTPS public base URL");
        }
    }

    private PortalMyCarlosDeviceApprovalService core(PortalMyCarlosDeviceApprovalService.SourceAuthorization source) {
        return core(source, this::current);
    }
    private PortalMyCarlosDeviceApprovalService core(PortalMyCarlosDeviceApprovalService.SourceAuthorization source,
            java.util.function.Supplier<PortalMyCarlosDeviceApprovalService.Current> transactionalSource) {
        if (suppliedCore != null) return Objects.requireNonNull(suppliedCore.apply(source, transactionalSource));
        return new PortalMyCarlosDeviceApprovalService(transactions, new PortalMyCarlosPatientBindingDao(factory),
                new PortalMyCarlosDeviceApprovalChallengeDao(factory), new PortalMyCarlosAcceptedDeviceDao(factory),
                new PortalMyCarlosDeviceApprovalOutboxDao(factory), security, transactionalSource, source,
                new ClinicSigner(), this::nonce, UUID::randomUUID, Clock.systemUTC(), event -> {
                    // Accepted/removed rows retain the actor and immutable exact signed evidence
                    // in the same transaction. Application log content never carries device keys.
                });
    }
    private Bytes nonce() { byte[] value = new byte[32];
        random.nextBytes(value);
        return new Bytes(value); }
    private static PortalMyCarlosDeviceApprovalService.SourceAuthorization noImport() {
        return patient -> { throw new IllegalStateException("authenticated approval import required"); };
    }

    public State read(LoggedInInfo info, int patient) {
        requireStaff(info, patient, SecurityInfoManager.READ);
        var service = core(noImport());
        var pending = service.readPendingForStaff(info, patient).stream().map(row -> new Pending(
                PortalMyCarlosDeviceApprovalCodec.base64(row.challengeId().copy()),
                HexFormat.of().formatHex(row.challengeHash().copy()), fingerprint(row.candidateKid()),
                row.expiresAt(), "staff_first".equals(row.kind()), !Instant.now().isBefore(row.expiresAt()))).toList();
        var history = service.readCurrentAndRetainedHistory(info, patient, 0, 50);
        if (history.more()) throw new IllegalStateException("approval history requires pagination");
        return new State(pending, history.devices().stream().map(row -> new Device(row.kid(), fingerprint(row.kid()), row.acceptedAt(), row.removedAt())).toList(), List.of());
    }

    /** Explicit staff operation. Clinic-wide routing is filtered before local challenge creation. */
    public State refresh(LoggedInInfo info, int patient, PatientPortalService portal) {
        requireStaff(info, patient, SecurityInfoManager.WRITE);
        var initial = current();
        requireClientTarget(portal, initial, PortalJob.PERMISSION_APPROVAL_REQUESTS_READ, "GET",
                "/internal/carlos/mycarlos/device-approval-requests?after=0&limit=20");
        var client = client(portal);
        var refused = new java.util.ArrayList<String>();
        long after = 0;
        boolean complete = false;
        for (int pageNo = 0; pageNo < 50; pageNo++) {
            var binding = current();
            String path = "/internal/carlos/mycarlos/device-approval-requests?after=" + after + "&limit=20";
            Runnable fence = () -> {
                requireClientTarget(portal, binding, PortalJob.PERMISSION_APPROVAL_REQUESTS_READ, "GET", path);
                if (!binding.hash().equals(current().hash())) throw new IllegalStateException("approval configuration changed");
            };
            fence.run();
            PortalMyCarlosApprovalClient.Page<PortalMyCarlosApprovalClient.Request> page;
            try { page = client.requests(after); }
            catch (PortalContractException failure) {
                throw PatientPortalException.ofMalformedResponse(200, "/internal/carlos/mycarlos/device-approval-requests", failure);
            }
            fence.run();
            for (var request : page.records()) {
                var challenge = prepareRequest(info, patient, request, refused, binding, fence);
                if (challenge == null) continue;
                try {
                    publish(challenge, "challenge", client);
                    publish(challenge, "decision", client);
                } catch (PublicationPending pending) {
                    refused.add("publication_pending");
                } catch (PatientPortalException failure) {
                    if (!retainPublicationRefusal(request, failure, binding, fence)) throw failure;
                    refused.add("challenge_expired");
                }
            }
            if (page.nextCursor() == null) { complete = true;
                break; }
            after = page.nextCursor();
        }
        if (!complete) throw new IllegalStateException("approval request polling limit reached");
        var state = read(info, patient);
        return new State(state.pending(), state.devices(), refused);
    }

    /** A durable refusal is an explicit processed outcome, so an old expired request cannot block newer ones. */
    PortalMyCarlosDeviceApprovalChallenge prepareRequest(LoggedInInfo info, int patient,
            PortalMyCarlosApprovalClient.Request request, List<String> refused) {
        return prepareRequest(info, patient, request, refused, current(), () -> {});
    }
    private PortalMyCarlosDeviceApprovalChallenge prepareRequest(LoggedInInfo info, int patient,
            PortalMyCarlosApprovalClient.Request request, List<String> refused,
            PortalMyCarlosDeviceApprovalService.Current binding, Runnable fence) {
        fence.run();
        if (request.demographicNo() != patient) return null;
        requireStaff(info, patient, SecurityInfoManager.WRITE);
        String terminal = journal.publication(binding, request, null);
        if (terminal != null) { refused.add(terminal);
            return null; }
        String saved = journal.record(binding, request, null);
        if (saved != null && !"prepared".equals(saved)) { refused.add(saved);
            return null; }
        var service = core(expected -> {
            fence.run();
            if (expected != patient) throw new SecurityException("approval patient routing changed");
            requireStaff(info, patient, SecurityInfoManager.WRITE);
        });
        PortalMyCarlosDeviceApprovalChallenge challenge;
        try {
            challenge = service.createOrReadExactChallenge(patient, request.intent());
        } catch (PortalMyCarlosDeviceApprovalService.Refusal failure) {
            if ("approval_unavailable".equals(failure.code)) throw failure;
            fence.run();
            if ("prepared".equals(journal.record(binding, request, failure.code))) throw failure;
            refused.add(failure.code);
            return null;
        }
        fence.run();
        journal.record(binding, request, "prepared");
        return challenge;
    }

    boolean retainPublicationRefusal(PortalMyCarlosApprovalClient.Request request, PatientPortalException failure) {
        return retainPublicationRefusal(request, failure, current(), () -> {});
    }
    private boolean retainPublicationRefusal(PortalMyCarlosApprovalClient.Request request, PatientPortalException failure,
            PortalMyCarlosDeviceApprovalService.Current binding, Runnable fence) {
        if (failure.statusCode() != 410 || !"approval_expired".equals(failure.detail())) return false;
        fence.run();
        journal.publication(binding, request, "challenge_expired");
        return true;
    }

    public State approveFirst(LoggedInInfo info, int patient, String challengeId, String expectedHash,
            String exactFingerprint, PatientPortalService portal) {
        requireStaff(info, patient, SecurityInfoManager.WRITE);
        var binding = current();
        // The core rechecks this captured scope after entering its inventory transaction and row locks.
        java.util.function.Supplier<PortalMyCarlosDeviceApprovalService.Current> transactionalSource = () -> {
            var latest = current();
            if (!latest.enabled || !binding.hash().equals(latest.hash())) throw new IllegalStateException("approval configuration changed");
            requireClientTarget(portal, binding, PortalJob.PERMISSION_APPROVAL_REQUESTS_READ, "GET",
                    "/internal/carlos/mycarlos/device-approval-requests?after=0&limit=20");
            return binding;
        };
        transactionalSource.get();
        var challenge = new Bytes(PortalMyCarlosDeviceApprovalCodec.exact32(challengeId));
        if (expectedHash == null || !expectedHash.matches("[0-9a-f]{64}")) throw new IllegalArgumentException("invalid challenge digest");
        var result = core(noImport(), transactionalSource).acceptFirstFromStaff(info, patient, challenge,
                new Bytes(HexFormat.of().parseHex(expectedHash)), exactFingerprint);
        var row = transactions.withApprovalState(() -> new PortalMyCarlosDeviceApprovalChallengeDao(factory)
                .findByChallengeForUpdate(result.device().challengeId()).orElseThrow());
        var client = client(portal);
        publish(row, "challenge", client);
        publish(row, "decision", client);
        return read(info, patient);
    }

    private PortalMyCarlosApprovalClient client(PatientPortalService portal) {
        if (suppliedClient != null) return Objects.requireNonNull(suppliedClient.apply(portal));
        var job = PortalJobAssertionSigner.from(PortalJobKey.fromCarlosProperties(PortalJob.DEVICE_APPROVAL_SYNC)
                .orElseThrow(() -> new IllegalStateException("approval sync job key is unavailable")));
        return client(portal, job);
    }

    /** The same production HTTP fence, with an explicit server-owned job signer for focused tests. */
    PortalMyCarlosApprovalClient client(PatientPortalService portal, PortalJobAssertionSigner job) {
        var binding = current();
        return new PortalMyCarlosApprovalClient((permission, method, path, body, status) -> {
            if (!binding.hash().equals(current().hash())) throw new IllegalStateException("approval configuration changed");
            requireClientTarget(portal, binding, permission, method, path);
            Runnable responseFence = () -> {
                requireClientTarget(portal, binding, permission, method, path);
                if (!binding.hash().equals(current().hash())) throw new IllegalStateException("approval configuration changed");
            };
            String response;
            try {
                response = portal.exchangeJob(job, permission, method, path, body, status);
            } catch (RuntimeException | Error exchangeFailure) {
                try {
                    responseFence.run();
                } catch (RuntimeException | Error fenceFailure) {
                    // A changed target still takes precedence, while retaining the HTTP failure.
                    if (fenceFailure != exchangeFailure) fenceFailure.addSuppressed(exchangeFailure);
                    throw fenceFailure;
                }
                throw exchangeFailure;
            }
            responseFence.run();
            return response;
        });
    }

    static void requireClientTarget(PatientPortalService portal, PortalMyCarlosDeviceApprovalService.Current cfg,
            String permission, String method, String path) {
        var actual = portal.myCarlosJobTarget(permission, method, path);
        String expected = URI.create(cfg.internalOrigin + path).toASCIIString();
        if (!cfg.clinic.equals(actual.clinicId()) || !expected.equals(actual.uri().toASCIIString())) {
            throw new IllegalStateException("approval client configuration changed");
        }
    }

    /** Claim a bounded durable lease, then cross HTTP with no database transaction held. */
    private void publish(PortalMyCarlosDeviceApprovalChallenge challenge, String kind, PortalMyCarlosApprovalClient client) {
        var dao = new PortalMyCarlosDeviceApprovalOutboxDao(factory);
        var claim = transactions.withApprovalState(() -> {
            var prior = dao.findMessageForUpdate(challenge.challengeId(), kind);
            if (prior.isEmpty() || prior.get().publishedAt() != null) return null;
            var row = prior.get();
            var cfg = current();
            if (!cfg.hash().equals(row.configHash()) || !cfg.clinic.equals(row.clinicId())) throw new IllegalStateException("approval configuration changed");
            String expectedPath = "/internal/carlos/mycarlos/device-approval-requests/" + challenge.requestId() + "/" + kind;
            if (!URI.create(cfg.internalOrigin).resolve(expectedPath).toASCIIString().equals(row.targetUri())) throw new IllegalStateException("approval target changed");
            Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            if (row.nextAttemptAt().isAfter(now) || row.leaseExpiresAt() != null && row.leaseExpiresAt().isAfter(now)) throw new PublicationPending();
            var leased = state(row, row.failures(), now, nonce(), now.plusSeconds(60), null);
            dao.updateLeaseState(row, leased);
            return leased;
        });
        if (claim == null) return;
        try {
            if (!current().hash().equals(claim.configHash())) throw new IllegalStateException("approval configuration changed");
            try { client.publish(challenge.requestId(), kind, claim.body().copy()); }
            catch (PortalContractException failure) {
                throw PatientPortalException.ofMalformedResponse(200, "/internal/carlos/mycarlos/device-approval-requests/{request_id}/" + kind, failure);
            }
        } catch (RuntimeException failure) {
            finish(dao, claim, false);
            throw failure;
        }
        finish(dao, claim, true);
    }
    private void finish(PortalMyCarlosDeviceApprovalOutboxDao dao, PortalMyCarlosDeviceApprovalOutboxEntry claim, boolean success) {
        transactions.withApprovalState(() -> {
            var row = dao.findMessageForUpdate(claim.challengeId(), claim.messageKind()).orElseThrow();
            if (!Objects.equals(row.leaseToken(), claim.leaseToken()) || row.version() != claim.version()) throw new IllegalStateException("approval publication lease changed");
            Instant now = Instant.now().truncatedTo(ChronoUnit.SECONDS);
            dao.updateLeaseState(row, state(row, success ? row.failures() : Math.addExact(row.failures(), 1),
                    success ? now : now.plusSeconds(30), null, null, success ? now : null));
            return null;
        });
    }
    private static PortalMyCarlosDeviceApprovalOutboxEntry state(PortalMyCarlosDeviceApprovalOutboxEntry row,
            int failures, Instant next, Bytes token, Instant lease, Instant published) {
        return new PortalMyCarlosDeviceApprovalOutboxEntry(row.id(), row.challengeId(), row.messageKind(), row.clinicId(),
                row.targetUri(), row.configHash(), row.body(), row.bodyHash(), row.createdAt(), next, failures,
                token, lease, published, Math.addExact(row.version(), 1));
    }
    private static String fingerprint(String kid) { return HexFormat.of().formatHex(PortalMyCarlosDeviceApprovalCodec.exact32(kid)); }

    private static final class PublicationPending extends IllegalStateException {
        PublicationPending() { super("approval publication pending retry"); }
    }

    private static final class ClinicSigner implements PortalMyCarlosDeviceApprovalService.Signer {
        public PortalMyCarlosDeviceApprovalService.SigningIdentity currentIdentity() {
            var key = PortalMyCarlosSigningIdentity.currentPublicKey();
            return new PortalMyCarlosDeviceApprovalService.SigningIdentity(key.kid(), key.spki());
        }
        public String sign(Kind kind, Map<String, Object> claims, Scope scope,
                PortalMyCarlosDeviceApprovalService.SigningIdentity expected) {
            try {
                byte[] payload = PortalMyCarlosDeviceApprovalCodec.canonicalPayload(kind, claims, scope);
                byte[] signature = PDFSigningUtil.signMyCarlosPayload(payload, expected.kid(), PDFSigningConfig.fromCarlosProperties());
                return PortalMyCarlosDeviceApprovalCodec.base64(payload) + "." + PortalMyCarlosDeviceApprovalCodec.base64(signature);
            } catch (java.io.IOException failure) { throw new IllegalStateException("approval signing unavailable"); }
        }
    }
}
