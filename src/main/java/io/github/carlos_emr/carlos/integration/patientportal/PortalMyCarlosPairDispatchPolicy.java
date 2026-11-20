/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import java.util.Objects;

/**
 * Pure, unwired policy sketch. Reports are explicit adapter/test-double inputs, not verifier proof.
 * Neither decoded context nor any report can produce an HTTP permit. Production authenticated
 * classification/artifact/approval/scope/capability adapters and owner/lease binding are absent.
 */
final class PortalMyCarlosPairDispatchPolicy {
    enum Operation { STAGE, CLAIM, TRANSMIT, RESUME, REFRESH_RETRY, HISTORICAL_RECONCILIATION }
    enum Role { ORIGINAL, READING_AID }
    enum Classification { UNKNOWN, ORDINARY_ORIGINAL, MANAGED_READING_AID }
    enum Report { UNAVAILABLE, MATCH, CONFLICT }

    /**
     * Answer 1 (independent copies): a retry re-sends that copy's own retained bytes. It never reads
     * the current raw source document, and an original's retry never touches its aid.
     */
    enum RetrySourcePolicy { RETRY_OWN_RETAINED_BYTES }
    enum Disposition { HOLD, RECONCILIATION_ONLY, DEFER_TO_EXISTING_ORDINARY_PATH }
    enum Reason {
        HISTORICAL_ONLY, ORDINARY_PATH_REQUIRES_EXISTING_ADMISSION, CLASSIFICATION_UNAVAILABLE, CLASSIFICATION_CONFLICT,
        CONTEXT_UNAVAILABLE, CONTEXT_CONFLICT, PAIR_EVIDENCE_UNAVAILABLE, PAIR_CONFLICT,
        ARTIFACT_EVIDENCE_UNAVAILABLE, ARTIFACT_CONFLICT, APPROVAL_EVIDENCE_UNAVAILABLE,
        APPROVAL_CONFLICT, SCOPE_EVIDENCE_UNAVAILABLE, SCOPE_CONFLICT, CAPABILITY_EVIDENCE_UNAVAILABLE,
        CAPABILITY_CONFLICT, SOURCE_EVIDENCE_UNAVAILABLE, SOURCE_CHANGED_DURING_PREPARATION,
        SIGNER_EVIDENCE_UNAVAILABLE, SIGNER_CHANGED, RETRY_POLICY_UNRESOLVED, PRODUCTION_ADAPTERS_UNAVAILABLE
    }

    record Assessment(Disposition disposition, Reason reason) { }

    /**
     * An ORIGINAL is never held for any aid reason: its classification is ordinary and it takes the
     * existing path whatever the aid evidence says (the original is never held back by its aid; the
     * aid is staged when its inputs exist). Only a READING_AID is held on missing aid evidence.
     * Never deserialized from context/JSON. MATCH is only a report for exercising policy rules;
     * it is not an authenticated capability, signature result or approval. Even all MATCH inputs
     * for a managed reading aid remain HOLD here. Future adapters must establish exact custody.
     */
    record Observations(Classification classification, PortalMyCarlosManagedPairContext retainedContext,
            Report pair, Report artifacts, Report approval, Report scope, Report capabilities,
            String sourceFingerprint, String signerKid) {
        Observations {
            Objects.requireNonNull(classification);
            for (Report report : new Report[] {pair, artifacts, approval, scope, capabilities}) Objects.requireNonNull(report);
        }

        static Observations unavailable() {
            return new Observations(Classification.UNKNOWN, null, Report.UNAVAILABLE, Report.UNAVAILABLE,
                    Report.UNAVAILABLE, Report.UNAVAILABLE, Report.UNAVAILABLE, null, null);
        }

        @Override public String toString() { return "ManagedPairObservations[REDACTED]"; }
    }

    private PortalMyCarlosPairDispatchPolicy() { }

    /**
     * Historical reconciliation is distinct from new upload. Its result still requires existing
     * authentication, receipt verification and routing rules; it never permits uploading C.
     */
    static Assessment assess(Operation operation, Role role, PortalMyCarlosManagedPairContext intent,
            Observations observed, RetrySourcePolicy retryPolicy) {
        Objects.requireNonNull(operation);
        Objects.requireNonNull(role);
        if (operation == Operation.HISTORICAL_RECONCILIATION) {
            return new Assessment(Disposition.RECONCILIATION_ONLY, Reason.HISTORICAL_ONLY);
        }
        if (observed == null) observed = Observations.unavailable();
        if (observed.classification() == Classification.UNKNOWN) return hold(Reason.CLASSIFICATION_UNAVAILABLE);
        if (observed.classification() == Classification.ORDINARY_ORIGINAL) {
            if (role != Role.ORIGINAL) return hold(Reason.CLASSIFICATION_CONFLICT);
            return new Assessment(Disposition.DEFER_TO_EXISTING_ORDINARY_PATH, Reason.ORDINARY_PATH_REQUIRES_EXISTING_ADMISSION);
        }
        if (role != Role.READING_AID) return hold(Reason.CLASSIFICATION_CONFLICT);
        if (intent == null || observed.retainedContext() == null) return hold(Reason.CONTEXT_UNAVAILABLE);
        if (!intent.equals(observed.retainedContext())) return hold(Reason.CONTEXT_CONFLICT);
        Assessment conflict = check(observed.pair(), Reason.PAIR_EVIDENCE_UNAVAILABLE, Reason.PAIR_CONFLICT);
        if (conflict != null) return conflict;
        conflict = check(observed.artifacts(), Reason.ARTIFACT_EVIDENCE_UNAVAILABLE, Reason.ARTIFACT_CONFLICT);
        if (conflict != null) return conflict;
        conflict = check(observed.approval(), Reason.APPROVAL_EVIDENCE_UNAVAILABLE, Reason.APPROVAL_CONFLICT);
        if (conflict != null) return conflict;
        conflict = check(observed.scope(), Reason.SCOPE_EVIDENCE_UNAVAILABLE, Reason.SCOPE_CONFLICT);
        if (conflict != null) return conflict;
        conflict = check(observed.capabilities(), Reason.CAPABILITY_EVIDENCE_UNAVAILABLE, Reason.CAPABILITY_CONFLICT);
        if (conflict != null) return conflict;
        if (observed.signerKid() == null) return hold(Reason.SIGNER_EVIDENCE_UNAVAILABLE);
        if (!intent.capture().preparedSignerKid().equals(observed.signerKid())) return hold(Reason.SIGNER_CHANGED);
        if (operation != Operation.STAGE) {
            // No raw-source comparison on a retry: each copy re-sends its own retained bytes.
            if (retryPolicy != RetrySourcePolicy.RETRY_OWN_RETAINED_BYTES) return hold(Reason.RETRY_POLICY_UNRESOLVED);
        } else {
            // Preserve the reference contract's preparation/capture check, separately from retry.
            if (observed.sourceFingerprint() == null) return hold(Reason.SOURCE_EVIDENCE_UNAVAILABLE);
            if (!intent.capture().sourceFingerprint().equals(observed.sourceFingerprint())) {
                return hold(Reason.SOURCE_CHANGED_DURING_PREPARATION);
            }
        }
        return hold(Reason.PRODUCTION_ADAPTERS_UNAVAILABLE);
    }

    private static Assessment check(Report report, Reason unavailable, Reason conflict) {
        return switch (report) {
            case UNAVAILABLE -> hold(unavailable);
            case CONFLICT -> hold(conflict);
            case MATCH -> null;
        };
    }

    private static Assessment hold(Reason reason) { return new Assessment(Disposition.HOLD, reason); }
}
