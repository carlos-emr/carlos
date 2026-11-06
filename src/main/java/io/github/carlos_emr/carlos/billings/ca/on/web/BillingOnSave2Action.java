/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Copyright (c) 2006-. OSCARservice, OpenSoft System. All Rights Reserved.
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

import io.github.carlos_emr.carlos.appt.ApptStatusData;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.FormSubmissionGuard;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;


import io.github.carlos_emr.carlos.billings.ca.on.service.BillingClaimSubmissionService;

import java.util.Set;

/**
 * Struts 2Action for Ontario billing save and post-save routing.
 *
 * <p>Processes billing form submissions (Save, Save &amp; Add Another, Settle &amp; Print Invoice,
 * Save &amp; Print Invoice). After saving, closes the window or redirects based on
 * submit action and workload management settings.
 *
 * <p>The old {@code <jsp:include page="billingDeleteWithBillNo.jsp"/>} pattern is replaced
 * by a direct call to {@link BillingDeleteWithBillNo2Action#deleteBillingByBillNo}.
 *
 * <p>One rendered review page saves one claim (issue #4225): the review carries a
 * {@link FormSubmissionGuard} token bound to the patient, so a re-click during a slow save, a double
 * Enter or a reload of the result answers with the first save's own result instead of writing a
 * second header and item set. A failed save that rolled back leaves the token free for a retry, and
 * opening the review again (Save &amp; Add Another, Back to Edit, a new bill) issues a new one.
 *
 * <p>Migrated from {@code billing/CA/ON/billingONSave.jsp}.
 *
 * @since 2026-04-08
 */
public class BillingOnSave2Action extends ActionSupport {

    private static final Set<String> VALID_SAVE_ACTIONS = Set.of(
            "SAVE",
            "SAVE_ADD_ANOTHER",
            "SAVE_PRINT",
            "SETTLE_PRINT"
    );

    private final SecurityInfoManager securityInfoManager;
    private final UserPropertyDAO userPropertyDAO;
    private final BillingDao billingDao;
    private final BillingClaimSubmissionService claimSubmissionService;
    private final io.github.carlos_emr.carlos.billings.ca.on.service.BillingCorrectionRecordService correctionPrep;
    private final PlatformTransactionManager transactionManager;

    @org.springframework.beans.factory.annotation.Autowired
    public BillingOnSave2Action(SecurityInfoManager securityInfoManager,
                                UserPropertyDAO userPropertyDAO,
                                BillingDao billingDao,
                                BillingClaimSubmissionService claimSubmissionService,
                                io.github.carlos_emr.carlos.billings.ca.on.service.BillingCorrectionRecordService correctionPrep,
                                PlatformTransactionManager transactionManager) {
        this.securityInfoManager = securityInfoManager;
        this.userPropertyDAO = userPropertyDAO;
        this.billingDao = billingDao;
        this.claimSubmissionService = claimSubmissionService;
        this.correctionPrep = correctionPrep;
        this.transactionManager = transactionManager;
    }
    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }
        if (!BillingRequestGuards.requirePost(request, response)) {
            return NONE;
        }

        String apptNo = request.getParameter("appointment_no");

        // Validate url_back to prevent open redirect (CWE-601)
        String rawUrlBack = request.getParameter("url_back");
        String safeUrlBack = (rawUrlBack != null
                && rawUrlBack.startsWith("/")
                && !rawUrlBack.startsWith("//")
                && !rawUrlBack.contains("..")
                && !rawUrlBack.contains("\\")
                && !rawUrlBack.contains("\r")
                && !rawUrlBack.contains("\n")) ? rawUrlBack : "";
        if (rawUrlBack != null && safeUrlBack.isEmpty()) {
            LogManager.getLogger(BillingOnSave2Action.class).warn("Rejected url_back parameter: {}", LogSafe.sanitize(rawUrlBack)); // NOSONAR javasecurity:S5145 — sanitized with LogSafe
        }
        request.setAttribute("safeUrlBack", safeUrlBack);

        String billingAction = request.getParameter("billingAction");

        if ("BACK_TO_EDIT".equals(billingAction)) {
            return "backToEdit";
        }

        if (!VALID_SAVE_ACTIONS.contains(billingAction)) {
            LogManager.getLogger(BillingOnSave2Action.class).error(
                    billingAction == null ? "Missing billingAction parameter" : "Invalid billingAction parameter");
            request.setAttribute("billingFailed", Boolean.TRUE);
            request.setAttribute("billingFailureReason", "Invalid billing action. Please try again.");
            return "failure";
        }

        String payeeValue = request.getParameter("payeename");
        if (payeeValue == null || payeeValue.trim().isEmpty()) {
            payeeValue = request.getParameter("payeename1");
        }

        // One claim per rendered review page (issue #4225). The repeat of a saved review ends on
        // the first save's own result page; it does not write, update the appointment or delete
        // the corrected bill again. A post without a token keeps the previous behaviour.
        FormSubmissionGuard.Attempt attempt = FormSubmissionGuard.attempt(request.getSession(),
                request.getParameter(FormSubmissionGuard.PARAMETER), FormSubmissionGuard.Scope.BILLING_ON_SAVE,
                request.getParameter("demographic_no"), FormSubmissionGuard.DEFAULT_WAIT_MILLIS);
        switch (attempt.verdict()) {
            case PROCEED, ABSENT -> { }
            case ALREADY_SAVED -> {
                int savedBillingNo = io.github.carlos_emr.carlos.util.ConversionUtils.fromIntString(attempt.result());
                if (savedBillingNo <= 0) {
                    return FormSubmissionGuard.refuse(request, response, FormSubmissionGuard.Verdict.UNCONFIRMED);
                }
                request.setAttribute("billingNo", savedBillingNo);
                return routeSavedBill(request, loggedInInfo, billingAction, safeUrlBack);
            }
            default -> {
                return FormSubmissionGuard.refuse(request, response, attempt.verdict());
            }
        }
        try (FormSubmissionGuard.Claim claim = attempt.claim()) {
            return saveBill(request, loggedInInfo, claim, apptNo, billingAction, safeUrlBack, payeeValue);
        }
    }

    /**
     * Saves the reviewed claim and dependent writes atomically, then routes. Header, items,
     * third-party/OHIP transaction, payee extension, appointment and corrected-bill cleanup commit
     * in one transaction that the claim
     * observes, so a rolled-back save leaves the review submittable again and a committed one is
     * never written twice.
     */
    private String saveBill(HttpServletRequest request, LoggedInInfo loggedInInfo, FormSubmissionGuard.Claim claim,
                            String apptNo, String billingAction, String safeUrlBack, String payeeValue) {
        // Single @Transactional call: header + items + 3rd-party/OHIP-trans
        // + payee ext all run inside one tx, so a payee-write failure
        // rolls the header back instead of orphaning the bill row.
        boolean ret;
        int billingNo;
        try {
            BillingClaimSubmissionService.BillingClaimSubmission submission = claimSubmissionService.getSubmission(request);
            String xmlBillType = request.getParameter("xml_billtype");
            BillingClaimSubmissionService.SaveResult saveResult =
                    new TransactionTemplate(transactionManager).execute(status -> {
                        if (claim != null) {
                            claim.storageStarted();
                        }
                        BillingClaimSubmissionService.SaveResult result =
                                claimSubmissionService.saveBillingWithExtAndPayee(submission, request, xmlBillType, payeeValue);
                        if (result == null || !result.saved()) {
                            // Nothing usable was stored: end the transaction as a rollback so the
                            // claim leaves this review page submittable again.
                            status.setRollbackOnly();
                            return result;
                        }
                        // The saved token describes the whole operation. Appointment/archive and
                        // corrected-bill writes must commit with the claim, or all roll back.
                        if (apptNo != null && apptNo.length() > 0 && !apptNo.equals("0")) {
                            String apptCurStatus = claimSubmissionService.getApptStatus(apptNo);
                            ApptStatusData as = new ApptStatusData();
                            String billStatus = as.billStatus(apptCurStatus);
                            if (!claimSubmissionService.updateApptStatus(apptNo, billStatus, loggedInInfo.getLoggedInProviderNo())) {
                                throw new io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException("The appointment could not be updated. The bill was not saved.");
                            }
                        }

                        // Replace the old <jsp:include page="billingDeleteWithBillNo.jsp"/> with a direct call
                        String cleanup = BillingDeleteWithBillNo2Action.deleteBillingByBillNo(
                                request, loggedInInfo, securityInfoManager, billingDao, correctionPrep);
                        if (!SUCCESS.equals(cleanup)) {
                            throw new io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException("The original bill could not be replaced. The new bill was not saved.");
                        }
                        return result;
                    });
            ret = saveResult != null && saveResult.saved();
            billingNo = ret ? saveResult.billingId() : 0;
        } catch (io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException e) {
            // Service rolled back. Stash the typed-exception narrative on
            // the request so the failure JSP can render it next to "Save
            // Failed!" rather than a generic banner with no operator-
            // actionable detail.
            MiscUtils.getLogger().error("Bill save rolled back: {}", e.getMessage(), e);
            request.setAttribute("billingFailureReason", e.getMessage());
            ret = false;
            billingNo = 0;
        }

        if (ret) {
            if (claim != null) {
                claim.saved(String.valueOf(billingNo));
            }
            request.setAttribute("billingNo", billingNo);
            return routeSavedBill(request, loggedInInfo, billingAction, safeUrlBack);
        }

        request.setAttribute("billingFailed", Boolean.TRUE);
        return "failure";
    }

    /**
     * The result of a saved bill ({@code billingNo} request attribute already set): the printed
     * invoice, the next bill form, the workload-management redirect, or the page that closes the
     * window. A repeated submission of an already-saved review ends here too.
     */
    private String routeSavedBill(HttpServletRequest request, LoggedInInfo loggedInInfo,
                                  String billingAction, String safeUrlBack) {
        if (Set.of("SAVE_PRINT", "SETTLE_PRINT").contains(billingAction)) {
            return "printInvoice";
        }

        if ("SAVE_ADD_ANOTHER".equals(billingAction)) {
            request.setAttribute("safeUrlBack", safeUrlBack);
            // Drives the c:choose branch in billingONSave.jsp without
            // forcing the JSP to read request parameters directly.
            request.setAttribute("addAnotherBill", Boolean.TRUE);
            return "addAnother";
        }

        // Resolve workload management redirect (applies to "Save", "Save and Back")
        String curBilf = request.getParameter("curBillForm");
        String provider = loggedInInfo.getLoggedInProviderNo();
        UserProperty prop = userPropertyDAO.getProp(provider, UserProperty.WORKLOAD_MANAGEMENT);
        String wrkloadmanagement = prop != null ? prop.getValue() : null;

        if (wrkloadmanagement != null && !wrkloadmanagement.isEmpty() && !wrkloadmanagement.equals(curBilf)) {
            if (!safeUrlBack.isEmpty()) {
                String separator = safeUrlBack.contains("?") ? "&" : "?";
                String urlBack = safeUrlBack + separator + "curBillForm=" + SafeEncode.forUriComponent(wrkloadmanagement);
                request.setAttribute("workloadUrlBack", urlBack);
            }
            return "workloadRedirect";
        }

        return SUCCESS;
    }
}
