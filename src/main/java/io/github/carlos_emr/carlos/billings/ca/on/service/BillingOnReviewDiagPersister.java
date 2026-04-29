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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import java.util.Date;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.commn.dao.DxresearchDAO;
import io.github.carlos_emr.carlos.commn.model.Dxresearch;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.MiscUtils;
/**
 * Optional clinical write triggered from {@code billing/CA/ON/ViewBillingONReview}:
 * if the user checked "add this dx to the patient's disease registry", insert
 * a {@link Dxresearch} row attributed to the logged-in provider before the
 * review-page assembler runs.
 *
 * <p>Lives outside {@link BillingOnReviewViewModelAssembler} so the assembler
 * remains a pure read of request state into a view model. Callers must run
 * {@link #persistIfRequested} <em>before</em> assembling the view model so
 * any failure (audit-trail violation, malformed input) propagates through
 * the action's standard error path rather than rendering a misleadingly
 * "successful" review page.</p>
 *
 * @since 2026-04-25
 */
@org.springframework.stereotype.Service
@org.springframework.transaction.annotation.Transactional
public class BillingOnReviewDiagPersister {

    private final DxresearchDAO dxresearchDAO;

    /**
     * Constructor-injection ctor used by Spring. Public so Struts2's
     * {@code SpringObjectFactory} can also instantiate the bean directly.
     */
    public BillingOnReviewDiagPersister(DxresearchDAO dxresearchDAO) {
        this.dxresearchDAO = dxresearchDAO;
    }

    /**
     * Inserts a {@link Dxresearch} row when the request opted in via the
     * {@code addToPatientDx=yes} hidden field. No-op for any other request
     * shape. Throws {@link BillingValidationException} if the request opted in
     * but {@code demographic_no} is non-numeric — silent drops on a clinical
     * write are an audit-trail gap.
     *
     * @param request the current request
     * @param userNo  the logged-in provider number to attribute the insert to
     */
    public void persistIfRequested(HttpServletRequest request, String userNo) {
        if (!"yes".equals(request.getParameter("addToPatientDx"))) {
            return;
        }
        // The user opted in via addToPatientDx=yes — a deliberate clinical
        // write. Any path that drops the write here would tell the operator
        // "saved" while doing nothing; that's the exact silent failure this
        // persister extraction was created to eliminate. Throw on every
        // missing-input branch so the validation-error JSP surfaces the
        // reason rather than the form quietly returning OK.
        String demoNo = nullToEmpty(request.getParameter("demographic_no"));
        if (demoNo.isEmpty()) {
            MiscUtils.getLogger().error(
                    "addToPatientDx requested without demographic_no");
            throw new BillingValidationException(
                    "Add-to-patient-dx requested but demographic_no is missing — "
                    + "no record was saved.");
        }
        String dxCode = nullToEmpty(request.getParameter("dxCode"));
        String dxCodeMatch = nullToEmpty(request.getParameter("codeMatchToPatientDx"));
        String dxCodeAdd = dxCodeMatch.isEmpty() ? dxCode : dxCodeMatch;
        if (dxCodeAdd.isEmpty()) {
            // demographic_no kept out of the log line per CARLOS PHI policy
            // (demographic_no correlates 1:1 to a patient via the demographic
            // table, so it's PHI-adjacent in catalina.out). Operators
            // correlate via the UI session, not by greping logs for IDs.
            MiscUtils.getLogger().error(
                    "addToPatientDx requested without a dx code");
            throw new BillingValidationException(
                    "Add-to-patient-dx requested but no diagnostic code was supplied — "
                    + "no record was saved.");
        }

        Integer demoNoInt;
        try {
            demoNoInt = Integer.valueOf(demoNo);
        } catch (NumberFormatException nfe) {
            // Log a generic error (no demographic_no) for PHI hygiene; the
            // user-facing BVE message includes the malformed value so the
            // operator can see what they typed and self-correct.
            MiscUtils.getLogger().error(
                    "addToPatientDx requested with non-numeric demographic_no");
            throw new BillingValidationException(
                    "addToPatientDx requested with non-numeric demographic_no: "
                    + LogSanitizer.sanitizeForDisplay(demoNo), nfe);
        }
        Date now = new Date();
        Dxresearch dx = new Dxresearch(
                demoNoInt,
                now,
                now,
                'A',
                dxCodeAdd,
                "icd9",
                (byte) 0,
                userNo);
        try {
            dxresearchDAO.save(dx);
        } catch (org.springframework.dao.DataIntegrityViolationException dive) {
            // Duplicate (demoNo, dxCode, status='A') row, FK violation, or
            // similar constraint failure. Surface as BillingValidationException
            // so the user sees the friendly "submission rejected" page rather
            // than the generic CARLOS Error 500. The save was a clinical
            // write — the operator needs to know it was rejected.
            // demographic_no kept out of the log per CARLOS PHI policy.
            // Operators correlate via the UI; the dx code is sufficient
            // forensic context.
            MiscUtils.getLogger().error(
                    "addToPatientDx: data-integrity violation persisting dx {}",
                    LogSanitizer.sanitize(dxCodeAdd), dive);
            throw new BillingValidationException(
                    "Could not save dx (" + LogSanitizer.sanitizeForDisplay(dxCodeAdd)
                    + ") for the patient: it may already be in the registry.", dive);
        } catch (org.hibernate.NonUniqueObjectException nuoe) {
            MiscUtils.getLogger().error(
                    "addToPatientDx: NonUniqueObjectException for dx {}",
                    LogSanitizer.sanitize(dxCodeAdd), nuoe);
            throw new BillingValidationException(
                    "Could not save dx (" + LogSanitizer.sanitizeForDisplay(dxCodeAdd)
                    + ") for the patient: a session conflict occurred. Please reload the chart and retry.", nuoe);
        } catch (RuntimeException rtEx) {
            // Catch-all for transient JDBC outages, lock-wait timeouts, etc.
            // Without this, the user sees the generic CARLOS Error 500 page
            // and has no signal that the dx was *not* added — same audit-trail
            // gap the targeted catches above close. Translate to BVE so the
            // operator gets the friendly "retry" message.
            MiscUtils.getLogger().error(
                    "addToPatientDx: unexpected save failure for dx {}",
                    LogSanitizer.sanitize(dxCodeAdd), rtEx);
            throw new BillingValidationException(
                    "Could not save dx (" + LogSanitizer.sanitizeForDisplay(dxCodeAdd)
                    + ") for the patient — please retry, then contact support if the problem persists.", rtEx);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
