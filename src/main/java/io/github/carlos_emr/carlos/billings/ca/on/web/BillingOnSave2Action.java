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
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.logging.log4j.LogManager;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;


import io.github.carlos_emr.carlos.billings.ca.on.service.BillingClaimSubmissionService;

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
 * <p>Migrated from {@code billing/CA/ON/billingONSave.jsp}.
 *
 * @since 2026
 */
public class BillingOnSave2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;
    private final UserPropertyDAO userPropertyDAO;
    private final BillingDao billingDao;
    private final BillingClaimSubmissionService bObj;
    private final io.github.carlos_emr.carlos.billings.ca.on.service.BillingCorrectionRecordService correctionPrep;

    @org.springframework.beans.factory.annotation.Autowired
    public BillingOnSave2Action(SecurityInfoManager securityInfoManager,
                                UserPropertyDAO userPropertyDAO,
                                BillingDao billingDao,
                                BillingClaimSubmissionService bObj,
                                io.github.carlos_emr.carlos.billings.ca.on.service.BillingCorrectionRecordService correctionPrep) {
        this.securityInfoManager = securityInfoManager;
        this.userPropertyDAO = userPropertyDAO;
        this.billingDao = billingDao;
        this.bObj = bObj;
        this.correctionPrep = correctionPrep;
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
            LogManager.getLogger(BillingOnSave2Action.class).warn("Rejected url_back parameter: {}", LogSanitizer.sanitize(rawUrlBack)); // NOSONAR javasecurity:S5145 — sanitized with LogSanitizer
        }
        request.setAttribute("safeUrlBack", safeUrlBack);

        String submit = request.getParameter("submit");
        String button = request.getParameter("button");

        if ("Back to Edit".equals(button)) {
            return "backToEdit";
        }

        if (submit == null || (!submit.equals("Settle & Print Invoice")
                && !submit.equals("Save & Print Invoice")
                && !submit.equals("Save")
                && !submit.equals("Save and Back")
                && !submit.equals("Save & Add Another Bill"))) {
            return SUCCESS;
        }

        BillingClaimSubmissionService.BillingClaimSubmission submission = bObj.getSubmission(request);
        String xmlBillType = request.getParameter("xml_billtype");

        String payeeValue = request.getParameter("payeename");
        if (payeeValue == null || payeeValue.trim().isEmpty()) {
            payeeValue = request.getParameter("payeename1");
        }

        // Single @Transactional call: header + items + 3rd-party/OHIP-trans
        // + payee ext all run inside one tx. Pre-fix these were four
        // sequential service calls, each in its own tx; a payee-write
        // failure after the header committed orphaned the bill row.
        boolean ret;
        int billingNo;
        try {
            BillingClaimSubmissionService.SaveResult saveResult =
                    bObj.saveBillingWithExtAndPayee(submission, request, xmlBillType, payeeValue);
            ret = saveResult.saved();
            billingNo = saveResult.billingId();
        } catch (io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException e) {
            // Service rolled back. Stash the typed-exception narrative on
            // the request so the failure JSP can render it next to "Save
            // Failed!" — pre-fix this just logged the message and rendered
            // a generic banner with no operator-actionable detail.
            MiscUtils.getLogger().error("Bill save rejected and rolled back: {}", e.getMessage());
            request.setAttribute("billingFailureReason", e.getMessage());
            ret = false;
            billingNo = 0;
        } catch (BillingClaimSubmissionService.BillingItemPersistenceException e) {
            MiscUtils.getLogger().error("Bill save rolled back: {}", e.getMessage());
            request.setAttribute("billingFailureReason", e.getMessage());
            ret = false;
            billingNo = 0;
        }

        if (ret) {
            if (apptNo != null && apptNo.length() > 0 && !apptNo.equals("0")) {
                String apptCurStatus = bObj.getApptStatus(apptNo);
                ApptStatusData as = new ApptStatusData();
                String billStatus = as.billStatus(apptCurStatus);
                bObj.updateApptStatus(apptNo, billStatus, loggedInInfo.getLoggedInProviderNo());
            }

            // Replace the old <jsp:include page="billingDeleteWithBillNo.jsp"/> with a direct call
            BillingDeleteWithBillNo2Action.deleteBillingByBillNo(request, loggedInInfo.getLoggedInProviderNo(), billingDao, correctionPrep);

            request.setAttribute("billingNo", billingNo);

            if ("Save & Print Invoice".equals(submit) || "Settle & Print Invoice".equals(submit)) {
                return "printInvoice";
            }

            if ("Save & Add Another Bill".equals(submit)) {
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

        request.setAttribute("billingFailed", Boolean.TRUE);
        return "failure";
    }
}
