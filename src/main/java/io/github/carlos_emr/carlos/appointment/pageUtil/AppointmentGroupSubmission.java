/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.appointment.pageUtil;

import java.io.IOException;
import java.util.Set;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.utility.FormSubmissionGuard;

/**
 * One save per rendering of the Group Appointment page ({@code appointment/appointmentgrouprecords}).
 *
 * <p>The page renders a {@link FormSubmissionGuard} token ({@link FormSubmissionGuard.Scope#APPOINTMENT_GROUP}).
 * Its gate, {@code ViewAppointmentSelfPost2Action}, claims that token when one of the page's group
 * actions ({@link #OPERATIONS}) is posted, before the page validates or writes anything. A repeat of
 * a rendering that already saved (a double click, a re-click while a slow save is out, a reload of the
 * result) is answered with that save's own result page ({@link #SAVED_RESULT}) and writes nothing.
 * This matters most for Group Update, which removes the appointments it names and books them again:
 * without the token its second click either booked twice (no version on the form) or was refused as
 * another user's change (the version no longer matched), although the user's own first click had
 * saved. A post without a token keeps the page's previous behaviour.</p>
 *
 * <p>The group actions still write from the page's scriptlet. The gate therefore runs the page for a
 * claimed submission itself ({@link #render}), with the claim in a request attribute, and closes the
 * claim once the page has run. The page tells the claim when its writes start
 * ({@link #storageStarted}) and that they were saved ({@link #saved}); both do nothing when the request
 * carries no claim.</p>
 *
 * @since 2026-11-13
 */
public final class AppointmentGroupSubmission {

    /** The page's buttons (the posted {@code groupappt} value) that write appointments. */
    public static final Set<String> OPERATIONS =
            Set.of("Add Group Appointment", "Group Update", "Group Cancel", "Group Delete");

    /** Struts result of a repeat whose rendering already saved: the first save's own result page. */
    public static final String SAVED_RESULT = "groupSaved";

    /** The page; also the {@code success} result of {@code appointment/appointmentgrouprecords}. */
    public static final String VIEW = "/WEB-INF/jsp/appointment/appointmentgrouprecords.jsp";

    /** The request parameter naming the group action. */
    static final String OPERATION_PARAMETER = "groupappt";

    /** The request attribute that hands the claim from the gate to the page. */
    static final String CLAIM_ATTRIBUTE = AppointmentGroupSubmission.class.getName() + ".claim";

    private AppointmentGroupSubmission() { }

    /**
     * @param request a post to the page
     * @return the group action it asks for when that action writes, otherwise {@code null}
     */
    public static String operation(HttpServletRequest request) {
        String operation = request.getParameter(OPERATION_PARAMETER);
        return operation != null && OPERATIONS.contains(operation) ? operation : null;
    }

    /**
     * Runs the page for a claimed submission. The caller closes the claim afterwards, so it records
     * what the page reported however the page ended.
     *
     * @param request the claimed post
     * @param response its response
     * @param claim the claim the page reports its writes to
     * @throws ServletException propagated from the page
     * @throws IOException propagated from the page
     */
    public static void render(HttpServletRequest request, HttpServletResponse response,
                              FormSubmissionGuard.Claim claim) throws ServletException, IOException {
        request.setAttribute(CLAIM_ATTRIBUTE, claim);
        try {
            request.getRequestDispatcher(VIEW).forward(request, response);
        } finally {
            request.removeAttribute(CLAIM_ATTRIBUTE);
        }
    }

    /**
     * Called by the page before its first write, or inside its transaction before that commits, so
     * the claim learns whether the writes committed or rolled back. A group action that never gets
     * here wrote nothing, and its form may be submitted again.
     *
     * @param request the page's request
     */
    public static void storageStarted(HttpServletRequest request) {
        FormSubmissionGuard.Claim claim = claim(request);
        if (claim != null) {
            claim.storageStarted();
        }
    }

    /**
     * Called by the page once its writes are durable: a repeat of this rendering is then answered with
     * the same result page, as long as it asks for the same group action.
     *
     * @param request the page's request
     */
    public static void saved(HttpServletRequest request) {
        FormSubmissionGuard.Claim claim = claim(request);
        if (claim != null) {
            claim.saved(request.getParameter(OPERATION_PARAMETER));
        }
    }

    private static FormSubmissionGuard.Claim claim(HttpServletRequest request) {
        return request.getAttribute(CLAIM_ATTRIBUTE) instanceof FormSubmissionGuard.Claim claim ? claim : null;
    }
}
