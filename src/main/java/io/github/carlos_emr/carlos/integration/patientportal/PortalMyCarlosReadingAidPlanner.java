/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import java.util.Objects;
import java.util.Optional;

/**
 * Decides, at send time, whether an original travels with its reading aid (carlos-portal delivery
 * contract, "Reading aids"; the maintainer's answers 1, 2 and 3). The decision never holds the
 * original back: every outcome lets the original go, and only the aid is included, left out or
 * dropped. Persistence and the audit log are adapters, so this stays a pure, unit-testable rule.
 *
 * <ul>
 *   <li>Answer 2: an aid is sent only for an approval that names the same original hash and the
 *       same summary version as the one being sent now; anything else drops the aid.
 *   <li>Answer 3: when an approved summary exists the aid is included by default, and staff may
 *       tick it out; either choice leaves an audit row. With no approved summary nothing is offered
 *       and nothing is audited.
 * </ul>
 */
final class PortalMyCarlosReadingAidPlanner {
    /** The stored clinician approval a send may use. Implemented over ReadingAidApprovalStore. */
    interface Approvals {
        /** The newest approval for this document whose original hash equals {@code originalSha256}, if any. */
        Optional<Offer> current(int demographicNo, int documentNo, String originalSha256);

        /**
         * Throws {@link IllegalStateException} unless {@code approvalId} exists and names exactly this
         * patient, document, original hash and summary version.
         */
        void requireMatching(String approvalId, int demographicNo, int documentNo, String originalSha256, String summaryVersion);
    }

    /** One audit row per send that had an offer. Carries ids only: no reviewer, model or document text. */
    interface Audit {
        void readingAid(int demographicNo, int documentNo, String approvalId, Action action);
    }

    enum Action { INCLUDED, LEFT_OUT, NOT_INCLUDED_APPROVAL_CHANGED }

    /** What the send form shows: an approval and the summary version it covers. */
    record Offer(String approvalId, String summaryVersion) {
        Offer {
            if (approvalId == null || approvalId.isBlank() || summaryVersion == null || summaryVersion.isBlank()) {
                throw new IllegalArgumentException("approval and summary version are required");
            }
        }
        @Override public String toString() { return "ReadingAidOffer[REDACTED]"; }
    }

    enum Outcome { NO_APPROVED_SUMMARY, INCLUDE, LEFT_OUT, APPROVAL_NO_LONGER_MATCHES }

    record Plan(Outcome outcome, String approvalId) {
        boolean includesAid() { return outcome == Outcome.INCLUDE; }
    }

    private final Approvals approvals;
    private final Audit audit;

    PortalMyCarlosReadingAidPlanner(Approvals approvals, Audit audit) {
        this.approvals = Objects.requireNonNull(approvals);
        this.audit = Objects.requireNonNull(audit);
    }

    /** For the send form: the offer to show with its tick on by default, or empty when there is none. */
    Optional<Offer> offer(int demographicNo, int documentNo, String originalSha256) {
        return approvals.current(demographicNo, documentNo, originalSha256);
    }

    /**
     * @param shown the offer the form displayed, or null when it displayed none
     * @param leaveOut true when staff ticked the aid out; the default (false) includes it
     * @param originalSha256 the hash of the original being sent now, not of the one shown earlier
     */
    Plan plan(int demographicNo, int documentNo, String originalSha256, Offer shown, boolean leaveOut) {
        if (shown == null) return new Plan(Outcome.NO_APPROVED_SUMMARY, null);
        if (leaveOut) {
            audit.readingAid(demographicNo, documentNo, shown.approvalId(), Action.LEFT_OUT);
            return new Plan(Outcome.LEFT_OUT, null);
        }
        try {
            approvals.requireMatching(shown.approvalId(), demographicNo, documentNo, originalSha256, shown.summaryVersion());
        } catch (IllegalStateException noLongerMatches) {
            // The document or its summary changed after the form was shown. The original still goes.
            audit.readingAid(demographicNo, documentNo, shown.approvalId(), Action.NOT_INCLUDED_APPROVAL_CHANGED);
            return new Plan(Outcome.APPROVAL_NO_LONGER_MATCHES, null);
        }
        audit.readingAid(demographicNo, documentNo, shown.approvalId(), Action.INCLUDED);
        return new Plan(Outcome.INCLUDE, shown.approvalId());
    }
}
