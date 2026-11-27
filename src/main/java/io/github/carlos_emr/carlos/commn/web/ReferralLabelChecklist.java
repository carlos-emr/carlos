// SPDX-License-Identifier: GPL-2.0-or-later
package io.github.carlos_emr.carlos.commn.web;

import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import jakarta.servlet.http.HttpSession;
import java.util.List;

/** Session state shared by the referral directory and PDF action. Callers publish under the session mutex. */
final class ReferralLabelChecklist {
    static final String SELECTION = "billingReferralAdminCheckList";
    static final String REVISION_HEADER = "X-Carlos-Checklist-Revision";
    private static final String REVISION = "billingReferralChecklistRevision";

    private ReferralLabelChecklist() { }

    static long revision(HttpSession session) {
        Long version = (Long) session.getAttribute(REVISION);
        return version == null ? 0L : version;
    }

    static long publish(HttpSession session, List<ProfessionalSpecialist> selection) {
        long version = revision(session) + 1;
        session.setAttribute(SELECTION, selection);
        session.setAttribute(REVISION, version);
        return version;
    }
}
