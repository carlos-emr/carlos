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
    private final PropertyDao properties;
    private final java.util.function.Supplier<PortalMyCarlosDeviceApprovalService.Current> suppliedCurrent;
    private final java.util.function.BiFunction<PortalMyCarlosDeviceApprovalService.SourceAuthorization,
            java.util.function.Supplier<PortalMyCarlosDeviceApprovalService.Current>, PortalMyCarlosDeviceApprovalService> suppliedCore;
    private final java.util.function.Function<PatientPortalService, PortalMyCarlosApprovalClient> suppliedClient;
    private final SecureRandom random = new SecureRandom();
    /** The device-code job's time (code issue, match and email times); the system clock outside tests. */
    private final Clock codeClock;

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
        this(transactions, factory, security, properties, suppliedCurrent, suppliedCore, suppliedClient, Clock.systemUTC());
    }
    PortalMyCarlosStaffApprovalService(PortalMyCarlosDeviceApprovalTransactions transactions,
            EntityManagerFactory factory, SecurityInfoManager security, PropertyDao properties,
            java.util.function.Supplier<PortalMyCarlosDeviceApprovalService.Current> suppliedCurrent,
            java.util.function.BiFunction<PortalMyCarlosDeviceApprovalService.SourceAuthorization,
                    java.util.function.Supplier<PortalMyCarlosDeviceApprovalService.Current>, PortalMyCarlosDeviceApprovalService> suppliedCore,
            java.util.function.Function<PatientPortalService, PortalMyCarlosApprovalClient> suppliedClient, Clock codeClock) {
        this.codeClock = Objects.requireNonNull(codeClock);
        this.suppliedCurrent = suppliedCurrent;
        this.suppliedCore = suppliedCore;
        this.suppliedClient = suppliedClient;
        this.transactions = Objects.requireNonNull(transactions);
        this.factory = Objects.requireNonNull(factory);
        this.security = Objects.requireNonNull(security);
        this.journal = new PortalMyCarlosApprovalJournal(transactions, properties);
        this.properties = Objects.requireNonNull(properties);
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
                row.expiresAt(), "staff_first".equals(row.kind())
                        || "code".equals(row.kind()) && staffMayApproveCode(row),
                !Instant.now().isBefore(row.expiresAt()))).toList();
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
                new Bytes(HexFormat.of().parseHex(expectedHash)), exactFingerprint, codeMatchedFor("code_and_staff"));
        var row = transactions.withApprovalState(() -> new PortalMyCarlosDeviceApprovalChallengeDao(factory)
                .findByChallengeForUpdate(result.device().challengeId()).orElseThrow());
        var client = client(portal);
        publish(row, "challenge", client);
        publish(row, "decision", client);
        return read(info, patient);
    }

    // ---- Clinic code v1 (mycarlos#141): the unattended device-code job ----------------------------

    private static final org.apache.logging.log4j.Logger LOGGER = io.github.carlos_emr.carlos.utility.MiscUtils.getLogger();
    private static final com.fasterxml.jackson.databind.ObjectMapper CLAIMS = new com.fasterxml.jackson.databind.ObjectMapper();
    private static final String CODE_CURSOR = "mycarlos.approval.code.cursor.";
    private static final String REQUEST_CURSOR = "mycarlos.approval.code.requests.";
    /** Pages of 20 each pass reads in one run; the rest waits for the next run. */
    static final int PAGES_PER_RUN = 50;
    /** How far back the follow-up pass looks for code requests to move on (a request lives 10 minutes). */
    private static final java.time.Duration FOLLOW_UP_WINDOW = java.time.Duration.ofMinutes(15);
    private static final int FOLLOW_UP_LIMIT = 200;
    /** Why a first device goes to staff, as the job's log line says it (no patient or address). */
    static final String STAFF_ONLY = "policy staff_only";
    static final String NO_USABLE_EMAIL = "no usable chart email";
    static final String NO_CONSENT = "no email consent";
    static final String SENDER_INACTIVE = "sender account not active";
    static final String FOOTER_MISSING = "clinic footer missing";
    static final String RECORD_RESTRICTED = "record access restricted";

    /**
     * Sends tried per code request before giving up: each failed send leaves a failed row in the
     * patient's email history, so they stay few.
     */
    static final int MAX_SENDS = 3;
    /** Failed sends per code request and when to try again: 30 s, then doubling, at most 5 minutes. */
    private final java.util.Map<Bytes, EmailRetry> emailRetries = new java.util.concurrent.ConcurrentHashMap<>();
    private record EmailRetry(int failures, Instant notBefore) {}

    /** What the device-code job needs from CARLOS email. Neither method may log a code. */
    interface CodeMail {
        /** Where a code may go for this patient, or why the request goes to staff instead. */
        CodeReach reach(int patient);

        /** @return whether the mail provider accepted the email carrying {@code displayCode} */
        boolean send(int patient, String address, String displayCode, long minutes);
    }

    /** The chart email a code may go to, or the reason (one of the constants above) it may not. */
    record CodeReach(String address, String fallback) {
        CodeReach {
            if ((address == null) == (fallback == null)) throw new IllegalArgumentException("address or fallback");
        }
        static CodeReach to(String address) {
            return new CodeReach(Objects.requireNonNull(address), null);
        }
        static CodeReach staff(String reason) {
            return new CodeReach(null, Objects.requireNonNull(reason));
        }
        @Override public String toString() { return "CodeReach[REDACTED]"; }
    }

    /** Counts from one run, for the job's log line: no patient, request or code values. */
    record CodeSyncResult(int challenged, int emailed, int matched, int wrong, int accepted, int refused, int ended) {}

    /**
     * One run of the device-code job, with no staff login, in three passes:
     * <ol>
     * <li>the codes devices sent after the saved code cursor: each is compared in constant time and
     * counted in the same transaction that moves the cursor past it, so it is never counted twice;
     * then it is marked used, and the request is accepted on a match (policy {@code code}; under
     * {@code code_and_staff} staff approve next) or refused with {@code code_attempts} after the
     * fifth wrong code. Codes come first, so no backlog of new requests can hold them back;</li>
     * <li>new requests after the saved request cursor: a first device of a patient CARLOS can email
     * gets a {@code code} challenge, its code is emailed and the challenge published; any other
     * first device goes to staff, as the log says once;</li>
     * <li>code requests of the last minutes: a code not yet sent is sent again (backing off a failing
     * send), challenges and decisions not yet published are published, and a request whose code
     * was made before this server started ends as {@code expired} ({@code challenge_expired}).</li>
     * </ol>
     * Each pass reads at most {@value #PAGES_PER_RUN} pages per run. Callers must not hold a
     * database transaction.
     */
    CodeSyncResult syncCodes(PatientPortalService portal, PortalMyCarlosApprovalPolicy policy, CodeMail mail) {
        Objects.requireNonNull(policy);
        Objects.requireNonNull(mail);
        var binding = current();
        Runnable fence = () -> {
            requireClientTarget(portal, binding, PortalJob.PERMISSION_APPROVAL_REQUESTS_READ, "GET",
                    "/internal/carlos/mycarlos/device-approval-requests?after=0&limit=20");
            if (!binding.hash().equals(current().hash())) throw new IllegalStateException("approval configuration changed");
        };
        fence.run();
        var client = client(portal);
        int[] counts = new int[7];
        readCodes(binding, fence, client, counts);
        readRequests(binding, fence, client, policy, mail, counts);
        followUp(binding, fence, client, policy, mail, counts);
        return new CodeSyncResult(counts[0], counts[1], counts[2], counts[3], counts[4], counts[5], counts[6]);
    }

    private void readCodes(PortalMyCarlosDeviceApprovalService.Current binding, Runnable fence,
            PortalMyCarlosApprovalClient client, int[] counts) {
        long after = cursor(CODE_CURSOR, binding);
        for (int pageNo = 0; pageNo < PAGES_PER_RUN; pageNo++) {
            fence.run();
            PortalMyCarlosApprovalClient.Page<PortalMyCarlosApprovalClient.Code> page;
            try { page = client.codes(after); }
            catch (PortalContractException failure) {
                throw PatientPortalException.ofMalformedResponse(200, "/internal/carlos/mycarlos/device-approval-codes", failure);
            } catch (PatientPortalException failure) {
                if (!resetOnInvalidCursor(failure, CODE_CURSOR, binding, after)) throw failure;
                return;
            }
            fence.run();
            for (var sent : page.records()) {
                var challenge = settleCode(binding, sent, after, counts);
                after = sent.sequence();
                try {
                    client.consumed(sent.requestId(), sent.attempt());
                } catch (PatientPortalException | PortalContractException failure) {
                    // Already counted, and the cursor is past it, so it is never counted again. The
                    // portal erases it anyway once the request is decided or expires.
                }
                if (challenge != null) finishCode(challenge, fence, client, counts);
            }
            if (page.nextCursor() == null) return;
        }
    }

    /**
     * Compares one sent code with its request's stored hash and moves the code cursor past it, in
     * one transaction.
     *
     * @return the code challenge it was sent for, or null when it is not one of this clinic's
     */
    private PortalMyCarlosDeviceApprovalChallenge settleCode(PortalMyCarlosDeviceApprovalService.Current binding,
            PortalMyCarlosApprovalClient.Code sent, long after, int[] counts) {
        var codes = new PortalMyCarlosDeviceApprovalCodeDao(factory);
        var challenges = new PortalMyCarlosDeviceApprovalChallengeDao(factory);
        var key = PortalMyCarlosClinicCode.serverKey();
        Instant now = codeClock.instant().truncatedTo(ChronoUnit.SECONDS);
        return transactions.withApprovalState(() -> {
            PortalMyCarlosDeviceApprovalChallenge result = null;
            // Challenge first, then its code: the order the accept path locks them in.
            var found = challenges.findByRequestForUpdate(binding.clinic, sent.requestId());
            if (found.isPresent() && "code".equals(found.get().kind())
                    && PortalMyCarlosDeviceApprovalCodec.base64(found.get().challengeId().copy()).equals(sent.challengeId())) {
                var row = found.get();
                var code = codes.findByChallengeForUpdate(row.challengeId());
                if (code.isPresent()) {
                    result = row;
                    var current = code.get();
                    // A code hashed under a key this server no longer has can't be checked, so it is
                    // not counted as wrong: the follow-up ends that request instead.
                    if (current.open() && sent.codeR() != null && row.pending() && now.isBefore(row.expiresAt())
                            && PortalMyCarlosClinicCode.sameKey(key, current.keyId().copy())) {
                        boolean right = PortalMyCarlosClinicCode.matches(key, current.keyId().copy(),
                                current.mac().copy(), row.requestId().toString(), sent.codeR());
                        var next = right ? current.matched(now) : current.wrong(now);
                        codes.update(current, next);
                        counts[right ? 2 : 3]++;
                    }
                }
            }
            writeCursor(CODE_CURSOR, binding, after, sent.sequence());
            return result;
        });
    }

    /**
     * Moves one code request on as far as it can go now: accept a matched code (policy
     * {@code code}), refuse after five wrong ones, end one whose code this server can't check, then
     * publish the queued decision. A lasting refusal (the patient was linked another way, say) leaves
     * the request as it is; approval switched off or keys unavailable stop the run.
     */
    private void finishCode(PortalMyCarlosDeviceApprovalChallenge challenge, Runnable fence,
            PortalMyCarlosApprovalClient client, int[] counts) {
        var codes = new PortalMyCarlosDeviceApprovalCodeDao(factory);
        var challenges = new PortalMyCarlosDeviceApprovalChallengeDao(factory);
        var code = transactions.withApprovalState(() -> codes.findByChallengeForUpdate(challenge.challengeId()).orElse(null));
        if (code == null) return;
        var key = PortalMyCarlosClinicCode.serverKey();
        int patient = challenge.demographicNo();
        try {
            var core = core(systemSource(patient, fence));
            if (code.matchedAt() != null && "code".equals(code.policy())) {
                if (!core.acceptFromCode(patient, challenge.challengeId(), codeMatchedFor("code")).historical()) {
                    counts[4]++;
                    auditCode("code.accepted", challenge);
                }
            } else if (code.refusedAt() != null) {
                if (core.endCodeChallenge(patient, challenge.challengeId(), "code_attempts")) {
                    counts[5]++;
                    auditCode("code.refused", challenge);
                }
            } else if (code.open() && code.emailedAt() != null && !PortalMyCarlosClinicCode.sameKey(key, code.keyId().copy())) {
                if (core.endCodeChallenge(patient, challenge.challengeId(), "challenge_expired")) {
                    counts[6]++;
                    auditCode("code.expired", challenge);
                }
            }
        } catch (PortalMyCarlosDeviceApprovalService.Refusal refusal) {
            if ("approval_unavailable".equals(refusal.code)) throw refusal;
            return;
        }
        var latest = transactions.withApprovalState(() -> challenges.findByChallengeForUpdate(challenge.challengeId()).orElseThrow());
        try {
            publish(latest, "decision", client);
        } catch (PatientPortalException | PublicationPending failure) {
            // The decision stays queued here; the follow-up pass publishes it again.
        }
    }

    private void readRequests(PortalMyCarlosDeviceApprovalService.Current binding, Runnable fence,
            PortalMyCarlosApprovalClient client, PortalMyCarlosApprovalPolicy policy, CodeMail mail, int[] counts) {
        long after = cursor(REQUEST_CURSOR, binding);
        for (int pageNo = 0; pageNo < PAGES_PER_RUN; pageNo++) {
            fence.run();
            PortalMyCarlosApprovalClient.Page<PortalMyCarlosApprovalClient.Request> page;
            try { page = client.requests(after); }
            catch (PortalContractException failure) {
                throw PatientPortalException.ofMalformedResponse(200, "/internal/carlos/mycarlos/device-approval-requests", failure);
            } catch (PatientPortalException failure) {
                if (!resetOnInvalidCursor(failure, REQUEST_CURSOR, binding, after)) throw failure;
                return;
            }
            fence.run();
            for (var request : page.records()) {
                try {
                    issueCode(binding, fence, client, policy, mail, request, counts);
                } catch (PatientPortalException failure) {
                    // Only publishing a challenge reaches the portal here. An expired one is recorded;
                    // anything else is published again by the follow-up pass.
                    retainPublicationRefusal(request, failure, binding, fence);
                } catch (PortalMyCarlosDeviceApprovalService.Refusal refusal) {
                    // One request that can no longer go ahead (its patient was linked another way, say)
                    // must not stop the others; staff see it on the patient's page.
                    if ("approval_unavailable".equals(refusal.code)) throw refusal;
                }
                // Looked at once: what it still needs is the follow-up pass's.
                long previous = after;
                transactions.withApprovalState(() -> {
                    writeCursor(REQUEST_CURSOR, binding, previous, request.sequence());
                    return null;
                });
                after = request.sequence();
            }
            if (page.nextCursor() == null) return;
        }
    }

    private void issueCode(PortalMyCarlosDeviceApprovalService.Current binding, Runnable fence,
            PortalMyCarlosApprovalClient client, PortalMyCarlosApprovalPolicy policy, CodeMail mail,
            PortalMyCarlosApprovalClient.Request request, int[] counts) {
        // A re-link or recovery keeps its existing device's approval and never needs a code.
        if (!firstDevice(request.intent())) return;
        int patient = request.demographicNo();
        if (journal.publication(binding, request, null) != null) return;
        String saved = journal.record(binding, request, null);
        if (saved != null && !"prepared".equals(saved)) return;
        if (saved == null) {
            String fallback = policy.byCode() ? mail.reach(patient).fallback() : STAFF_ONLY;
            if (fallback != null) {
                // Fixed text and a fixed reason only: no address, patient or request.
                LOGGER.info("myCarlos device approval goes to staff: {}", fallback);
                return;
            }
        }
        PortalMyCarlosDeviceApprovalChallenge challenge;
        try {
            challenge = core(systemSource(patient, fence)).createOrReadExactChallenge(patient, request.intent(), true);
        } catch (PortalMyCarlosDeviceApprovalService.Refusal failure) {
            if ("approval_unavailable".equals(failure.code)) throw failure;
            fence.run();
            if ("prepared".equals(journal.record(binding, request, failure.code))) throw failure;
            return;
        }
        fence.run();
        journal.record(binding, request, "prepared");
        // A challenge staff made first stays theirs to approve.
        if (!"code".equals(challenge.kind())) return;
        if (saved == null) counts[0]++;
        var code = emailCode(challenge, policy, mail, counts);
        // The device is asked for the code only once it was sent.
        if (code == null || code.emailedAt() == null) return;
        try {
            publish(challenge, "challenge", client);
        } catch (PublicationPending pending) {
            // The follow-up pass publishes it once its retry time comes.
        }
    }

    /**
     * Code requests of the last minutes, oldest first: send a code not yet sent (after its backoff),
     * publish an emailed request's challenge, then finish what its code decided.
     */
    private void followUp(PortalMyCarlosDeviceApprovalService.Current binding, Runnable fence,
            PortalMyCarlosApprovalClient client, PortalMyCarlosApprovalPolicy policy, CodeMail mail, int[] counts) {
        var codes = new PortalMyCarlosDeviceApprovalCodeDao(factory);
        var challenges = new PortalMyCarlosDeviceApprovalChallengeDao(factory);
        Instant now = codeClock.instant().truncatedTo(ChronoUnit.SECONDS);
        // Kept only while a request still waits for its code to go out.
        emailRetries.keySet().removeIf(id -> transactions.withApprovalState(() -> codes.findByChallengeForUpdate(id)
                .map(code -> !code.open() || code.emailedAt() != null || !now.isBefore(code.expiresAt())).orElse(true)));
        var ids = transactions.withApprovalState(() -> codes.recentChallengeIds(binding.clinic, now.minus(FOLLOW_UP_WINDOW), FOLLOW_UP_LIMIT));
        for (var id : ids) {
            fence.run();
            var challenge = transactions.withApprovalState(() -> challenges.findByChallengeForUpdate(id).orElse(null));
            if (challenge == null) continue;
            try {
                if (challenge.pending()) {
                    var code = emailCode(challenge, policy, mail, counts);
                    if (code != null && code.emailedAt() != null) publish(challenge, "challenge", client);
                }
            } catch (PatientPortalException | PublicationPending failure) {
                // Tried again by the next run.
            }
            finishCode(challenge, fence, client, counts);
        }
    }

    /**
     * Makes sure an open code request's code was emailed: a code never emailed is replaced by a new
     * one (its value lived only in a run that ended) and sent, unless a failing send is backing off.
     *
     * @return the code row as far as it got, or null when none could be made
     */
    private PortalMyCarlosDeviceApprovalCode emailCode(PortalMyCarlosDeviceApprovalChallenge challenge,
            PortalMyCarlosApprovalPolicy policy, CodeMail mail, int[] counts) {
        var codes = new PortalMyCarlosDeviceApprovalCodeDao(factory);
        var challenges = new PortalMyCarlosDeviceApprovalChallengeDao(factory);
        var key = PortalMyCarlosClinicCode.serverKey();
        Instant now = codeClock.instant().truncatedTo(ChronoUnit.SECONDS);
        String[] codeR = new String[1];
        var row = transactions.withApprovalState(() -> {
            var locked = challenges.findByChallengeForUpdate(challenge.challengeId()).orElseThrow();
            var found = codes.findByChallengeForUpdate(challenge.challengeId());
            if (found.isPresent() && found.get().emailedAt() != null) return found.get();
            if (!locked.pending() || !now.isBefore(locked.expiresAt())) return found.orElse(null);
            var retry = emailRetries.get(challenge.challengeId());
            if (retry != null && (retry.failures() >= MAX_SENDS || now.isBefore(retry.notBefore()))) return found.orElse(null);
            // A request made while codes were on, now under staff_only: no new code is made for it.
            if (found.isEmpty() && !policy.byCode()) return null;
            codeR[0] = PortalMyCarlosClinicCode.newCodeR();
            var mac = new Bytes(PortalMyCarlosClinicCode.mac(key, locked.requestId().toString(), codeR[0]));
            if (found.isPresent()) {
                var next = found.get().reissued(new Bytes(key.id()), mac);
                codes.reissue(found.get(), next);
                return next;
            }
            var created = new PortalMyCarlosDeviceApprovalCode(locked.challengeId(), locked.clinicId(), locked.requestId(),
                    policy.value(), new Bytes(key.id()), mac, 0, locked.issuedAt(), locked.expiresAt(), null, null, null, 0);
            codes.insert(created);
            return created;
        });
        if (row == null || row.emailedAt() != null || codeR[0] == null) return row;
        var reach = mail.reach(challenge.demographicNo());
        boolean sent = false;
        if (reach.address() != null) {
            String code = PortalMyCarlosClinicCode.code(signingKid(challenge.signedChallenge()), challenge.candidateKid(),
                    challenge.requestId().toString(), codeR[0]);
            long minutes = (java.time.Duration.between(now, challenge.expiresAt()).getSeconds() + 59) / 60;
            sent = mail.send(challenge.demographicNo(), reach.address(), PortalMyCarlosClinicCode.display(code), minutes);
        }
        if (!sent) {
            var retry = emailRetries.merge(challenge.challengeId(), new EmailRetry(1, now.plusSeconds(30)), (old, first) ->
                    new EmailRetry(old.failures() + 1, now.plusSeconds(Math.min(300L, 30L << Math.min(old.failures(), 4)))));
            // Fixed text and a fixed reason only. The request stays unanswered and expires; the
            // device was never asked for a code.
            if (retry.failures() >= MAX_SENDS) {
                LOGGER.info("myCarlos device code email was not sent {} times; no more tries for this request", MAX_SENDS);
            } else if (reach.address() == null) {
                // Email stopped being possible after the request was taken.
                LOGGER.info("myCarlos device code email was not sent: {}", reach.fallback());
            } else {
                LOGGER.info("myCarlos device code email was not sent: the send failed; it is tried again later");
            }
            return row;
        }
        emailRetries.remove(challenge.challengeId());
        counts[1]++;
        auditCode("code.emailed", challenge);
        return transactions.withApprovalState(() -> {
            var latest = codes.findByChallengeForUpdate(challenge.challengeId()).orElseThrow();
            if (latest.emailedAt() != null || !latest.mac().equals(row.mac())) return latest;
            var next = latest.emailed(codeClock.instant().truncatedTo(ChronoUnit.SECONDS));
            codes.update(latest, next);
            return next;
        });
    }

    /** Read under the challenge's lock: whether its code matched and was issued under {@code policy}. */
    private java.util.function.Predicate<PortalMyCarlosDeviceApprovalChallenge> codeMatchedFor(String policy) {
        var codes = new PortalMyCarlosDeviceApprovalCodeDao(factory);
        return row -> codes.findByChallengeForUpdate(row.challengeId())
                .map(code -> code.matchedAt() != null && policy.equals(code.policy())).orElse(false);
    }

    private boolean codeEmailed(PortalMyCarlosDeviceApprovalChallenge challenge) {
        var codes = new PortalMyCarlosDeviceApprovalCodeDao(factory);
        return transactions.withApprovalState(() -> codes.findByChallengeForUpdate(challenge.challengeId())
                .map(code -> code.emailedAt() != null).orElse(false));
    }

    private boolean staffMayApproveCode(PortalMyCarlosDeviceApprovalChallenge row) {
        return transactions.withApprovalState(() -> codeMatchedFor("code_and_staff").test(row));
    }

    /**
     * The job's stand-in for staff on the patient's page. The patient comes from the portal's request
     * (to make the challenge) or from the local challenge (to decide); the code itself is the proof,
     * as it reached only that patient's chart email.
     */
    private PortalMyCarlosDeviceApprovalService.SourceAuthorization systemSource(int patient, Runnable fence) {
        return expected -> {
            fence.run();
            if (expected != patient) throw new SecurityException("approval patient routing changed");
        };
    }

    private static boolean firstDevice(String intent) {
        try {
            var claims = CLAIMS.readTree(PortalMyCarlosDeviceApprovalCodec.envelope(intent).payload().copy());
            var predecessor = claims.get("predecessor_device_key_id");
            return predecessor == null || predecessor.isNull();
        } catch (java.io.IOException | IllegalArgumentException failure) {
            return false;
        }
    }

    /** The kid that signed the challenge, which the code's check digits are bound to. */
    private static String signingKid(String signedChallenge) {
        try {
            var kid = CLAIMS.readTree(PortalMyCarlosDeviceApprovalCodec.envelope(signedChallenge).payload().copy()).get("kid");
            if (kid == null || !kid.isTextual()) throw new IllegalStateException("approval challenge has no signing key id");
            return kid.textValue();
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("approval challenge is unreadable");
        }
    }

    /** The portal no longer has rows up to a saved cursor (its database was replaced): start over. */
    private boolean resetOnInvalidCursor(PatientPortalException failure, String prefix,
            PortalMyCarlosDeviceApprovalService.Current cfg, long after) {
        if (failure.statusCode() != 400 || !"invalid_cursor".equals(failure.detail()) || after == 0) return false;
        transactions.withApprovalState(() -> {
            writeCursor(prefix, cfg, after, 0);
            return null;
        });
        return true;
    }

    private long cursor(String prefix, PortalMyCarlosDeviceApprovalService.Current cfg) {
        return transactions.withApprovalState(() -> {
            var rows = cursorRows(prefix, cfg);
            if (rows.isEmpty()) return 0L;
            String value = rows.getFirst().getValue();
            if (value == null || !value.matches("0|[1-9][0-9]{0,18}")) throw new IllegalStateException("invalid approval job cursor");
            return Long.parseLong(value);
        });
    }

    /** Moves a cursor inside the caller's approval transaction, only from the value it was read at. */
    private void writeCursor(String prefix, PortalMyCarlosDeviceApprovalService.Current cfg, long expected, long next) {
        var rows = cursorRows(prefix, cfg);
        if (rows.isEmpty()) {
            if (expected != 0) throw new IllegalStateException("approval job cursor changed");
            var row = new io.github.carlos_emr.carlos.commn.model.Property(cursorKey(prefix, cfg));
            row.setValue(Long.toString(next));
            properties.persist(row);
            return;
        }
        var row = rows.getFirst();
        if (!Long.toString(expected).equals(row.getValue())) throw new IllegalStateException("approval job cursor changed");
        row.setValue(Long.toString(next));
        properties.merge(row);
    }

    private List<io.github.carlos_emr.carlos.commn.model.Property> cursorRows(String prefix,
            PortalMyCarlosDeviceApprovalService.Current cfg) {
        var rows = properties.findByNameForUpdate(cursorKey(prefix, cfg)).stream()
                .filter(row -> row.getProviderNo() == null || row.getProviderNo().isBlank()).toList();
        if (rows.size() > 1) throw new IllegalStateException("ambiguous approval job cursor");
        return rows;
    }

    /** One cursor per clinic and portal configuration: a new portal starts from its first row. */
    private static String cursorKey(String prefix, PortalMyCarlosDeviceApprovalService.Current cfg) {
        return prefix + PortalMyCarlosDeviceApprovalCrypto.sha256(cfg.hash().copy());
    }

    /** One audit row as the system provider; the request id only, never a code or device key. */
    private static void auditCode(String event, PortalMyCarlosDeviceApprovalChallenge challenge) {
        var row = new io.github.carlos_emr.carlos.commn.model.OscarLog();
        row.setProviderNo(io.github.carlos_emr.carlos.commn.model.Provider.SYSTEM_PROVIDER_NO);
        row.setAction(PortalJob.DEVICE_APPROVAL_SYNC.logAction(event));
        row.setContent("system: device code");
        row.setContentId(challenge.requestId().toString());
        row.setDemographicId(challenge.demographicNo());
        row.setData("");
        io.github.carlos_emr.carlos.log.LogAction.addLogSynchronous(row);
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
        // A device must never be asked for a code nobody received (mycarlos#141): whoever publishes
        // (staff refresh or the code job), a code challenge goes out only once its code was emailed.
        if ("challenge".equals(kind) && "code".equals(challenge.kind()) && !codeEmailed(challenge)) return;
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
