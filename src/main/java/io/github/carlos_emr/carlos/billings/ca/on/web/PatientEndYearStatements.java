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
package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

import jakarta.servlet.http.HttpServletRequest;

import org.apache.logging.log4j.Logger;

import io.github.carlos_emr.carlos.billings.ca.on.service.PatientEndYearStatementService;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Shared end-year-statement request helpers for echoing form state, parsing
 * optional date filters, and logging typed service failures without patient
 * names or other PHI.
 *
 * @since 2026-04-27
 */
final class PatientEndYearStatements {

    private static final Logger LOG = MiscUtils.getLogger();

    private PatientEndYearStatements() {}

    /**
     * Echoes first/last name request params back as request attributes the
     * JSP renders through {@code <carlos:encode>} — the action is the
     * canonical input gate so the JSP body can stay free of {@code ${param.*}}
     * (which the OWASP-encoding lint flags as unencoded).
     */
    static void echoNames(HttpServletRequest request) {
        String firstName = request.getParameter("firstNameParam");
        String lastName = request.getParameter("lastNameParam");
        // Also echo demographicNoParam so the form's hidden field round-trips
        // through the demographic-search redirect (otherwise Create Statement
        // sees an empty selection and the JS validator rejects the form).
        String demographicNo = request.getParameter("demographicNoParam");
        if (demographicNo == null || demographicNo.isEmpty()) {
            demographicNo = request.getParameter("demographic_no");
        }
        request.setAttribute("firstNameParamEcho", firstName == null ? "" : firstName);
        request.setAttribute("lastNameParamEcho", lastName == null ? "" : lastName);
        request.setAttribute("demographicNoParamEcho", demographicNo == null ? "" : demographicNo);
        StringBuilder displayName = new StringBuilder();
        if (firstName != null && !firstName.isEmpty() && lastName != null && !lastName.isEmpty()) {
            displayName.append(firstName).append(' ').append(lastName);
        }
        request.setAttribute("patientNameDisplay", displayName.toString());
    }

    /**
     * Logs a typed {@link PatientEndYearStatementService.Failure} with only
     * the internal demographic identifier. PATIENT_NOT_FOUND /
     * PATIENT_NOT_UNIQUE are user-input outcomes (empty form, ambiguous
     * selection) and log at INFO; DATABASE_ERROR / IO_ERROR are real failures
     * and stay at ERROR.
     * Caller is responsible for adding the i18n action error and returning
     * {@code "failure"} — this just centralises the branch logging.
     */
    static void logFailure(PatientEndYearStatementService.Failure failure, String demographicNo) {
        String safeDemographicNo = LogSanitizer.sanitize(demographicNo);
        switch (failure.reason()) {
            case PATIENT_NOT_FOUND:
                LOG.info("end-year-statement: lookup returned no candidates for demographicNo={}",
                        safeDemographicNo);
                break;
            case PATIENT_NOT_UNIQUE:
                LOG.info("end-year-statement: lookup returned multiple candidates for demographicNo={}",
                        safeDemographicNo);
                break;
            case DATABASE_ERROR:
            case IO_ERROR:
                LOG.error("end-year-statement failure: reason={}, demographicNo={}",
                        failure.reason(), safeDemographicNo, failure.getCause());
                break;
            default:
                LOG.error("end-year-statement: unexpected reason={}, demographicNo={}",
                        failure.reason(), safeDemographicNo, failure.getCause());
                break;
        }
    }

    /** Lenient ISO-8601 date parser — returns null on malformed/empty input. */
    static Date parseIso(String value) {
        if (value == null || value.isEmpty()) return null;
        try {
            return new SimpleDateFormat("yyyy-MM-dd").parse(value);
        } catch (ParseException ex) {
            return null;
        }
    }
}
