/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.appointment.gate;

import java.io.IOException;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.carlos.appointment.pageUtil.AppointmentGroupSubmission;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.FormSubmissionGuard;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Self-posting view gate for appointment forms that contain scriptlet
 * mutations inside the JSP ({@code appointmentgrouprecords},
 * {@code appointmentrepeatbooking}, {@code appointmenteditrepeatbooking},
 * {@code appointmentaddrecordprint}). Requires {@code _appointment w}; allows
 * GET, HEAD, and POST for the read/form-render path, but forces POST when the
 * request carries mutation-intent — either the {@code groupappt} parameter is
 * present (triggers persist in the three group-records JSPs) or the request
 * targets {@code appointmentaddrecordprint} (which persists unconditionally
 * on every hit). This prevents GET-triggered mutations and cross-origin link
 * abuse even for users that hold {@code _appointment w}. Scriptlet extraction
 * into dedicated {@code *2Action} classes is flagged for follow-up — in-JSP
 * {@code <security:oscarSec>} taglibs remain as second-line defense.
 *
 * <p>On the Group Appointment page ({@code appointmentgrouprecords}) a group
 * action that writes is also claimed against the page's one-time submission
 * token ({@link FormSubmissionGuard}, see {@link AppointmentGroupSubmission})
 * before the page validates or writes anything, so a double click, a slow
 * re-click or a reload of the result saves once and ends on the first save's
 * result page. A post without a token keeps the previous behaviour.</p>
 *
 * @since 2026-04-14
 */
public final class ViewAppointmentSelfPost2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager =
            SpringUtils.getBean(SecurityInfoManager.class);

    /**
     * Checks {@code _appointment w} then permits GET, HEAD, or POST — except
     * when mutation-intent params or a mutation-only action URI require POST.
     *
     * @return {@link #SUCCESS} when authorized and the method is allowed;
     *         {@link #NONE} after sending 405 for unsupported methods or
     *         mutation intent over GET/HEAD, or after running the Group
     *         Appointment page for a claimed group action;
     *         {@link AppointmentGroupSubmission#SAVED_RESULT} or
     *         {@link FormSubmissionGuard#REFUSED_RESULT} for a group action
     *         whose token was already spent
     * @throws SecurityException when the session is missing or the caller
     *         lacks {@code _appointment w}
     * @throws Exception propagated from Struts I/O
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (loggedInInfo == null) {
            MiscUtils.getLogger().warn("Denied appointment self-post: no session");
            throw new SecurityException("missing required sec object (_appointment w)");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_appointment", "w", null)) {
            MiscUtils.getLogger().warn("Denied appointment self-post: provider={} lacks _appointment w",
                    loggedInInfo.getLoggedInProviderNo());
            throw new SecurityException("missing required sec object (_appointment w)");
        }

        String method = request.getMethod();
        if (!"GET".equalsIgnoreCase(method)
                && !"HEAD".equalsIgnoreCase(method)
                && !"POST".equalsIgnoreCase(method)) {
            response.setHeader("Allow", "GET, HEAD, POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        // Block GET/HEAD when the request would trigger a mutation:
        //  - appointmentaddrecordprint always persists on every hit
        //  - the three group-records JSPs persist when the "groupappt" param is present
        String uri = request.getRequestURI();
        boolean alwaysMutates = uri != null && uri.endsWith("/appointmentaddrecordprint");
        boolean groupapptMutation = request.getParameter("groupappt") != null;
        if ((alwaysMutates || groupapptMutation) && !"POST".equalsIgnoreCase(method)) {
            MiscUtils.getLogger().warn("Denied appointment self-post: mutation intent on {} requires POST, got {}",
                    uri, method);
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED, "Mutation requires POST");
            return NONE;
        }
        // Only the Group Appointment page carries this token; the repeat-booking pages keep their own
        // handling of a repeated group action (RecurringAppointmentService).
        String groupOperation = uri != null && uri.endsWith("/appointmentgrouprecords")
                ? AppointmentGroupSubmission.operation(request) : null;
        if (groupOperation != null) {
            return groupWrite(request, response, groupOperation);
        }
        return SUCCESS;
    }

    /**
     * Claims the Group Appointment page's token for a group action that writes, after the method
     * and privilege checks and before the page validates or writes anything (issue #4225).
     *
     * <ul>
     *   <li>no token: the page runs as before ({@link #SUCCESS});</li>
     *   <li>claimed: the page runs here, reporting its writes to the claim, and the claim is closed
     *       however the page ended, so a failure that wrote nothing frees the form again;</li>
     *   <li>this rendering already saved this action: nothing runs, and the first save's result page
     *       answers ({@link AppointmentGroupSubmission#SAVED_RESULT});</li>
     *   <li>this rendering already saved another action, the first save's outcome is unknown or still
     *       running, or the token is not one this session issued for the page: nothing runs, HTTP 409
     *       ({@link FormSubmissionGuard#refuse}).</li>
     * </ul>
     *
     * <p>A rendering that has not saved still reaches the page's own stale-form check, so a form
     * another user's change overtook is refused with the appointment conflict page as before.</p>
     */
    private String groupWrite(HttpServletRequest request, HttpServletResponse response, String operation)
            throws ServletException, IOException {
        FormSubmissionGuard.Attempt attempt = FormSubmissionGuard.attempt(request.getSession(),
                request.getParameter(FormSubmissionGuard.PARAMETER), FormSubmissionGuard.Scope.APPOINTMENT_GROUP,
                null, FormSubmissionGuard.DEFAULT_WAIT_MILLIS);
        switch (attempt.verdict()) {
            case ABSENT -> {
                return SUCCESS;
            }
            case PROCEED -> { }
            case ALREADY_SAVED -> {
                // Another button of a saved rendering is not a repeat: nothing of it was saved, and
                // the first action's success page would report it as done.
                return operation.equals(attempt.result()) ? AppointmentGroupSubmission.SAVED_RESULT
                        : FormSubmissionGuard.refuse(request, response, FormSubmissionGuard.Verdict.STALE);
            }
            default -> {
                return FormSubmissionGuard.refuse(request, response, attempt.verdict());
            }
        }
        try (FormSubmissionGuard.Claim claim = attempt.claim()) {
            AppointmentGroupSubmission.render(request, response, claim);
        }
        return NONE;
    }
}
