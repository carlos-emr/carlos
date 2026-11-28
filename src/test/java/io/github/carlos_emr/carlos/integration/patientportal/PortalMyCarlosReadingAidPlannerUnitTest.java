/* SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 */
package io.github.carlos_emr.carlos.integration.patientportal;

import static org.junit.jupiter.api.Assertions.*;

import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosReadingAidPlanner.Action;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosReadingAidPlanner.Offer;
import io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosReadingAidPlanner.Outcome;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The send-time rule for a reading aid: same original and summary version, default on, one audit row. */
@Tag("unit")
class PortalMyCarlosReadingAidPlannerUnitTest {
    private static final int PATIENT = 123;
    private static final int DOCUMENT = 11;
    private static final String ORIGINAL = "a".repeat(64);
    private static final String VERSION = "b".repeat(64);
    private static final Offer OFFER = new Offer("apr-abcdefghijklmnopqrstuvwx", VERSION);

    private record Row(int patient, int document, String approval, Action action) { }

    private final List<Row> rows = new ArrayList<>();
    private String storedOriginal = ORIGINAL;
    private String storedVersion = VERSION;
    private final PortalMyCarlosReadingAidPlanner planner = new PortalMyCarlosReadingAidPlanner(
            new PortalMyCarlosReadingAidPlanner.Approvals() {
                @Override public Optional<Offer> current(int patient, int document, String originalSha256) {
                    return originalSha256.equals(storedOriginal) ? Optional.of(new Offer(OFFER.approvalId(), storedVersion)) : Optional.empty();
                }
                @Override public void requireMatching(String id, int patient, int document, String original, String version) {
                    if (!OFFER.approvalId().equals(id) || patient != PATIENT || document != DOCUMENT
                            || !storedOriginal.equals(original) || !storedVersion.equals(version)) throw new IllegalStateException("no match");
                }
            }, (patient, document, approval, action) -> rows.add(new Row(patient, document, approval, action)));

    @Test void shouldOfferTheApprovedSummary_whenOneExistsForTheOriginalBeingSent() {
        assertEquals(Optional.of(OFFER), planner.offer(PATIENT, DOCUMENT, ORIGINAL));
        assertEquals(Optional.empty(), planner.offer(PATIENT, DOCUMENT, "c".repeat(64)));
    }

    @Test void shouldIncludeTheAidByDefault_andAuditIt() {
        var plan = planner.plan(PATIENT, DOCUMENT, ORIGINAL, OFFER, false);
        assertEquals(Outcome.INCLUDE, plan.outcome());
        assertTrue(plan.includesAid());
        assertEquals(OFFER.approvalId(), plan.approvalId());
        // Nothing is audited as included until staging has accepted the aid.
        assertTrue(rows.isEmpty());
        planner.included(PATIENT, DOCUMENT, plan, true);
        assertEquals(List.of(new Row(PATIENT, DOCUMENT, OFFER.approvalId(), Action.INCLUDED)), rows);
    }

    @Test void shouldAuditThatTheAidWasNotIncluded_whenStagingRefusedIt() {
        var plan = planner.plan(PATIENT, DOCUMENT, ORIGINAL, OFFER, false);
        planner.included(PATIENT, DOCUMENT, plan, false);
        assertEquals(List.of(new Row(PATIENT, DOCUMENT, OFFER.approvalId(), Action.NOT_INCLUDED_STAGING_REFUSED)), rows);
        assertThrows(IllegalArgumentException.class,
                () -> planner.included(PATIENT, DOCUMENT, planner.plan(PATIENT, DOCUMENT, ORIGINAL, OFFER, true), true));
    }

    @Test void shouldLeaveTheAidOut_andAuditTheOmission_whenStaffTicksItOut() {
        var plan = planner.plan(PATIENT, DOCUMENT, ORIGINAL, OFFER, true);
        assertEquals(Outcome.LEFT_OUT, plan.outcome());
        assertFalse(plan.includesAid());
        assertNull(plan.approvalId());
        assertEquals(List.of(new Row(PATIENT, DOCUMENT, OFFER.approvalId(), Action.LEFT_OUT)), rows);
    }

    @Test void shouldOfferNothingAndAuditNothing_whenThereIsNoApprovedSummary() {
        var plan = planner.plan(PATIENT, DOCUMENT, ORIGINAL, null, false);
        assertEquals(Outcome.NO_APPROVED_SUMMARY, plan.outcome());
        assertFalse(plan.includesAid());
        assertTrue(rows.isEmpty());
    }

    @Test void shouldDropTheAidButNotTheOriginal_whenTheOriginalChangedSinceTheFormWasShown() {
        storedOriginal = "c".repeat(64);
        var plan = planner.plan(PATIENT, DOCUMENT, ORIGINAL, OFFER, false);
        assertEquals(Outcome.APPROVAL_NO_LONGER_MATCHES, plan.outcome());
        assertFalse(plan.includesAid());
        assertEquals(List.of(new Row(PATIENT, DOCUMENT, OFFER.approvalId(), Action.NOT_INCLUDED_APPROVAL_CHANGED)), rows);
    }

    @Test void shouldDropTheAid_whenTheSummaryVersionChangedSinceTheFormWasShown() {
        storedVersion = "d".repeat(64);
        assertEquals(Outcome.APPROVAL_NO_LONGER_MATCHES, planner.plan(PATIENT, DOCUMENT, ORIGINAL, OFFER, false).outcome());
    }

    @Test void shouldDropTheAid_whenTheApprovalIsForAnotherPatientOrDocument() {
        assertEquals(Outcome.APPROVAL_NO_LONGER_MATCHES, planner.plan(PATIENT + 1, DOCUMENT, ORIGINAL, OFFER, false).outcome());
        assertEquals(Outcome.APPROVAL_NO_LONGER_MATCHES, planner.plan(PATIENT, DOCUMENT + 1, ORIGINAL, OFFER, false).outcome());
    }

    @Test void shouldRefuseABlankOfferAndNeverPrintItsIds() {
        assertThrows(IllegalArgumentException.class, () -> new Offer("", VERSION));
        assertThrows(IllegalArgumentException.class, () -> new Offer(OFFER.approvalId(), null));
        assertFalse(OFFER.toString().contains(OFFER.approvalId()));
    }
}
