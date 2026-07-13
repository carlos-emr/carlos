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
package io.github.carlos_emr.carlos.billings.ca.on.validator;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.carlos.billings.ca.on.support.BillingOnConstants;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.dao.DiagnosticCodeDao;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.DiagnosticCode;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.util.DateUtils;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Pre-render validator for {@code billingONReview.jsp}. Replaces the
 * three inline DAO-call scriptlet blocks the legacy JSP carried at lines
 * 700-786:
 *
 * <ul>
 *   <li><b>A003A annual-physical guard</b> — if any submitted service
 *       code is {@code A003A} and the bill type is {@code ODP*}, look up
 *       the patient's last OHIP A003A bill and emit a warning if the
 *       service date is within a year of it.</li>
 *   <li><b>Service-code validity</b> — for each submitted
 *       {@code serviceCodeN}, look up in {@code billingservice} and emit
 *       an error if the code isn't found / has been terminated.</li>
 *   <li><b>Diagnostic-code validity</b> — for each submitted
 *       {@code dxCodeN}, look up in {@code diagnostic_code} and emit an
 *       error if the code isn't found.</li>
 * </ul>
 *
 * <p>Returns a {@link Result} record carrying the validation messages
 * (severity + text) and a {@code codeValid} flag the JSP uses to gate
 * the Save button. Pure read; no side effects.</p>
 *
 * @since 2026-04-25
 */
@org.springframework.stereotype.Component
public class BillingOnReviewValidator {

    /**
     * One validation message produced by the validator. Severity drives the
     * CSS class the JSP renders ({@code alert-warning} for WARNING,
     * {@code alert-danger} for ERROR).
     */
    public record Message(Severity severity, String text) {
        public enum Severity { WARNING, ERROR }
    }

    /**
     * Validation result. {@code codeValid} mirrors the legacy scriptlet's
     * {@code codeValid} local — true iff every service / dx code in the
     * request resolves; false if any error message was emitted.
     */
    public record Result(List<Message> messages, boolean codeValid) { }

    private final BillingONCHeader1Dao bCh1Dao;
    private final BillingServiceDao billingServiceDao;
    private final DiagnosticCodeDao diagnosticCodeDao;

    public BillingOnReviewValidator(BillingONCHeader1Dao bCh1Dao,
                             BillingServiceDao billingServiceDao,
                             DiagnosticCodeDao diagnosticCodeDao) {
        this.bCh1Dao = bCh1Dao;
        this.billingServiceDao = billingServiceDao;
        this.diagnosticCodeDao = diagnosticCodeDao;
    }

    /**
     * Validates the submitted service codes / dx codes / A003A guard.
     * The {@code billReferenceDate} is the date used for service-code
     * termination-date filtering ({@code findBillingCodesByCodeAndTerminationDate}).
     */
    public Result validate(HttpServletRequest request, String demoNo, String billReferenceDate) {
        List<Message> messages = new ArrayList<>();
        boolean codeValid = true;

        // A003A annual-physical guard.
        codeValid &= checkA003A(request, demoNo, messages);

        // Service-code validity.
        Date filterDate = resolveFilterDate(billReferenceDate);
        for (int i = 0; i < BillingOnConstants.FIELD_SERVICE_NUM; i++) {
            String serviceCode = nullToEmpty(request.getParameter("serviceCode" + i));
            if (serviceCode.isEmpty()) {
                continue;
            }
            // Replace _ with \_ so SQL LIKE doesn't treat it as wildcard.
            List<Object> svcCodes = billingServiceDao.findBillingCodesByCodeAndTerminationDate(
                    serviceCode.trim().replace("_", "\\_"), filterDate);
            if (svcCodes.isEmpty()) {
                codeValid = false;
                messages.add(new Message(Message.Severity.ERROR,
                        "Service code \"" + serviceCode + "\" is invalid. Please go back to correct it."));
            }
        }

        // Diagnostic-code validity. dxCode is the primary; dxCode1/dxCode2 are
        // the secondary slots.
        for (int i = 0; i < 3; i++) {
            String dxCode = nullToEmpty(i == 0
                    ? request.getParameter("dxCode")
                    : request.getParameter("dxCode" + i));
            if (dxCode.isEmpty()) {
                continue;
            }
            List<DiagnosticCode> dcodes = diagnosticCodeDao.findByDiagnosticCode(dxCode.trim());
            if (dcodes.isEmpty()) {
                codeValid = false;
                messages.add(new Message(Message.Severity.ERROR,
                        "Diagnostic code \"" + dxCode + "\" is invalid. Please go back to correct it."));
            }
        }

        return new Result(List.copyOf(messages), codeValid);
    }

    private Date resolveFilterDate(String billReferenceDate) {
        Date filterDate = ConversionUtils.fromDateString(billReferenceDate);
        return filterDate == null ? new Date() : filterDate;
    }

    /**
     * A003A annual-physical guard: checks whether the patient was already
     * billed A003A within the past year. Legacy semantics are warning-only:
     * the guard never blocks save and skips quietly when required context is
     * missing or unparsable.
     */
    private boolean checkA003A(HttpServletRequest request, String demoNo, List<Message> messages) {
        Integer demoNoInt = parseDemoNo(demoNo);
        if (demoNoInt == null) {
            if (hasA003AAnnualGuardCandidate(request)) {
                MiscUtils.getLogger().warn( // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                        "BillingOnReviewValidator: A003A guard failed — non-numeric demographic_no '{}'",
                        LogSafe.sanitize(demoNo));
                messages.add(new Message(Message.Severity.ERROR,
                        "Invalid demographic number for A003A annual-billing check. Please go back to edit."));
                return false;
            }
            return true;
        }
        for (int i = 0; i < BillingOnConstants.FIELD_SERVICE_NUM; i++) {
            String serviceCode = request.getParameter("serviceCode" + i);
            String billType = request.getParameter("xml_billtype");
            if (!"A003A".equals(serviceCode) || billType == null || !billType.matches("ODP.*")) {
                continue;
            }
            BillingONCHeader1 bCh1 = bCh1Dao.getLastOHIPBillingDateForServiceCode(demoNoInt, "A003A");
            if (bCh1 == null) {
                continue;
            }

            Date serviceDate;
            try {
                serviceDate = DateUtils.parseDate(request.getParameter("service_date"), request.getLocale());
            } catch (java.text.ParseException e) {
                MiscUtils.getLogger().warn( // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                        "BillingOnReviewValidator: A003A guard failed — unparseable service_date '{}'",
                        LogSafe.sanitize(request.getParameter("service_date")), e);
                messages.add(new Message(Message.Severity.ERROR,
                        "Invalid service date for A003A annual-billing check. Please correct the service date."));
                return false;
            }

            Calendar serviceDateCal = Calendar.getInstance();
            serviceDateCal.setTime(serviceDate);
            Calendar nextBillDateCal = Calendar.getInstance();
            nextBillDateCal.setTime(bCh1.getBillingDate());
            nextBillDateCal.add(Calendar.YEAR, 1);
            nextBillDateCal.add(Calendar.DATE, 1);

            if (nextBillDateCal.after(serviceDateCal)) {
                // Legacy semantics: warning only, doesn't block save (the
                // scriptlet sets codeValid=false in a comment but does NOT
                // actually flip the flag).
                messages.add(new Message(Message.Severity.WARNING,
                        "(Invoice No: " + bCh1.getId()
                        + ") A003A — Service code already billed within the past year for this patient."));
            }
        }
        return true;
    }

    private boolean hasA003AAnnualGuardCandidate(HttpServletRequest request) {
        String billType = request.getParameter("xml_billtype");
        if (billType == null || !billType.matches("ODP.*")) {
            return false;
        }
        for (int i = 0; i < BillingOnConstants.FIELD_SERVICE_NUM; i++) {
            if ("A003A".equals(request.getParameter("serviceCode" + i))) {
                return true;
            }
        }
        return false;
    }

    private static Integer parseDemoNo(String demoNo) {
        if (demoNo == null || demoNo.isEmpty()) {
            return null;
        }
        try {
            return Integer.parseInt(demoNo);
        } catch (NumberFormatException nfe) {
            return null;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
