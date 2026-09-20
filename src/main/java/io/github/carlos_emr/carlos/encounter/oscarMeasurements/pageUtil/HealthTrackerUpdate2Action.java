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

package io.github.carlos_emr.carlos.encounter.oscarMeasurements.pageUtil;

import io.github.carlos_emr.carlos.commn.dao.FlowSheetCustomizationDao;
import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dao.SecRoleDao;
import io.github.carlos_emr.carlos.commn.model.FlowSheetCustomization;
import io.github.carlos_emr.carlos.casemgmt.service.CaseManagementManager;
import io.github.carlos_emr.carlos.encounter.data.EctProgram;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementFlowSheet;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.MeasurementTemplateFlowSheetConfig;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.healthtracker.HealthTrackerMeasurementPersister;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.healthtracker.HealthTrackerMeasurementPersister.ValidationFailure;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.healthtracker.HealthTrackerNoteComposer;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.healthtracker.HealthTrackerSubmissionParser;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.healthtracker.HealthTrackerSubmissionResult;
import io.github.carlos_emr.carlos.encounter.oscarMeasurements.healthtracker.HealthTrackerSubmissionService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.apache.logging.log4j.Logger;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.owasp.encoder.Encode;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * POST-only save endpoint for the CARLOS Health Tracker
 * ({@code /encounter/oscarMeasurements/HealthTrackerUpdate}).
 *
 * <p>Replaces the GPL2-only {@code FormUpdate2Action} the Health Tracker used to
 * share with the retired Indivica DiabFlowSheet page. All domain logic lives in
 * {@code io.github.carlos_emr.carlos.encounter.oscarMeasurements.healthtracker};
 * this class only does the web-layer work: reject unsafe HTTP methods, enforce
 * {@code _measurement w}, resolve the flowsheet the request named, and translate
 * the result into a Struts result.
 *
 * <p><b>Privilege choice.</b> The gate is {@code _measurement w}, matching the
 * sibling {@link EctMeasurements2Action} and the endpoint this replaces. The
 * Health Tracker page additionally hides its Save controls behind
 * {@code _flowsheet w}, but that is a UI affordance: what this endpoint writes is
 * measurement rows, so measurement-write is the privilege that governs it.
 *
 * <p><b>Outcome handling.</b> A clean save redirects (POST/Redirect/GET) back to
 * the tracker so a browser refresh cannot re-post; duplicate suppression in
 * {@link HealthTrackerMeasurementPersister} is the second line of defence there.
 * A save with rejected values forwards back to the page instead, because the
 * {@code testOutput} request attribute that drives the validation alert would not
 * survive a redirect.
 *
 * @since 2026-09-20
 */
public class HealthTrackerUpdate2Action extends ActionSupport {

    private static final Logger logger = MiscUtils.getLogger();

    private final SecurityInfoManager securityInfoManager;
    private final FlowSheetCustomizationDao flowSheetCustomizationDao;
    private final HealthTrackerSubmissionService submissionService;

    /**
     * No-arg constructor for the Struts object factory. Resolves its collaborators
     * through {@link SpringUtils} because Struts instantiates this class per
     * request; see the package-private constructor for the test seam.
     */
    public HealthTrackerUpdate2Action() {
        this(SpringUtils.getBean(SecurityInfoManager.class),
             SpringUtils.getBean(FlowSheetCustomizationDao.class),
             new HealthTrackerSubmissionService(
                     new HealthTrackerSubmissionParser(),
                     new HealthTrackerMeasurementPersister(SpringUtils.getBean(MeasurementDao.class)),
                     new HealthTrackerNoteComposer(),
                     SpringUtils.getBean(CaseManagementManager.class),
                     SpringUtils.getBean(SecRoleDao.class)));
    }

    HealthTrackerUpdate2Action(SecurityInfoManager securityInfoManager,
                               FlowSheetCustomizationDao flowSheetCustomizationDao,
                               HealthTrackerSubmissionService submissionService) {
        this.securityInfoManager = securityInfoManager;
        this.flowSheetCustomizationDao = flowSheetCustomizationDao;
        this.submissionService = submissionService;
    }

    // FindSecBugs UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL.
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (the HTTP method name); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = {"UNVALIDATED_REDIRECT", "IMPROPER_UNICODE"},
            justification = "UNVALIDATED_REDIRECT: redirect target is a same-origin application path or validated internal path, not an attacker-controlled external URL. IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (the HTTP method name); not a security or authorization decision")
    @Override
    public String execute() throws IOException {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            logger.warn("Rejected Health Tracker save with method {} from {}",
                    LogSafe.sanitize(String.valueOf(request.getMethod())),
                    LogSafe.sanitize(String.valueOf(request.getRemoteAddr())));
            response.setHeader("Allow", "POST");
            response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            return NONE;
        }

        if (!securityInfoManager.hasPrivilege(
                LoggedInInfo.getLoggedInInfoFromSession(request), "_measurement", "w", null)) {
            throw new SecurityException("missing required sec object (_measurement)");
        }

        HttpSession session = request.getSession();
        String providerNo = (String) session.getAttribute("user");
        String template = request.getParameter("template");

        int demographicNo;
        try {
            demographicNo = Integer.parseInt(request.getParameter("demographic_no"));
        } catch (NumberFormatException e) {
            // A non-numeric demographic_no can only come from a hand-built request;
            // the page always posts the hidden field it rendered.
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return NONE;
        }

        MeasurementFlowSheet flowSheet = resolveFlowSheet(template, providerNo, demographicNo);
        if (flowSheet == null) {
            response.sendError(HttpServletResponse.SC_NOT_FOUND);
            return NONE;
        }

        String defaultDate = request.getParameter("date");
        if (defaultDate == null || defaultDate.isBlank()) {
            defaultDate = UtilDateUtilities.getToday("yyyy-MM-dd");
        }

        HealthTrackerSubmissionResult result = submissionService.submit(
                flowSheet,
                request::getParameter,
                demographicNo,
                providerNo,
                appointmentNo(session),
                new EctProgram(session).getProgram(providerNo),
                defaultDate);

        if (result.hasRejections()) {
            // Rendered inside the page's validation alert; the JSP encodes it.
            // Struts action errors are deliberately not used: the Health Tracker
            // renders this attribute, not <s:actionerror/>, so anything reported
            // that way would be invisible to the clinician.
            request.setAttribute("testOutput", rejectionReport(result));
            return "failure";
        }

        response.sendRedirect(trackerUrl(request, demographicNo, template));
        return NONE;
    }

    /**
     * Builds the text the page's validation alert shows: the values that were
     * refused, followed by the distinct reasons they were refused for.
     */
    private String rejectionReport(HealthTrackerSubmissionResult result) {
        String reasons = result.failures().stream()
                .map(this::describe)
                .distinct()
                .collect(Collectors.joining("\n"));
        String rejected = String.join("\n", result.rejected());
        return reasons.isEmpty() ? rejected : rejected + "\n\n" + reasons;
    }

    /**
     * Localizes one validation failure.
     *
     * <p>Falls back to the bare message key if no Struts {@code TextProvider} is
     * bound. The clinician needs to be told which value was refused far more
     * than they need it phrased in their locale, so an i18n lookup must not be
     * able to take the whole failure response down with it.
     */
    private String describe(ValidationFailure failure) {
        try {
            return getText(failure.messageKey(), failure.arguments());
        } catch (RuntimeException e) {
            logger.warn("No text provider bound while reporting Health Tracker validation failure {}",
                    LogSafe.sanitize(failure.messageKey()));
            return failure.messageKey();
        }
    }

    /**
     * Builds the same-origin redirect back to the tracker, carrying the scroll
     * position so the clinician lands where they left off.
     */
    private static String trackerUrl(HttpServletRequest request, int demographicNo, String template) {
        StringBuilder url = new StringBuilder(request.getContextPath())
                .append("/encounter/oscarMeasurements/ViewHealthTracker?demographic_no=")
                .append(demographicNo)
                .append("&template=")
                .append(Encode.forUriComponent(template == null ? "" : template));

        String ycoord = request.getParameter("ycoord");
        if (ycoord != null && ycoord.matches("\\d{1,7}")) {
            url.append("&ycoord=").append(ycoord);
        }
        return url.toString();
    }

    private MeasurementFlowSheet resolveFlowSheet(String template, String providerNo, int demographicNo) {
        if (template == null || template.isBlank()) {
            return null;
        }
        List<FlowSheetCustomization> customizations =
                flowSheetCustomizationDao.getFlowSheetCustomizations(template, providerNo, demographicNo);
        return MeasurementTemplateFlowSheetConfig.getInstance().getFlowSheet(template, customizations);
    }

    private static int appointmentNo(HttpSession session) {
        Object current = session.getAttribute("cur_appointment_no");
        if (current == null) {
            return 0;
        }
        try {
            return Integer.parseInt(current.toString());
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
