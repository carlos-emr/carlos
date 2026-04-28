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
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingDataHlp;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.BillingONCHeader1Dao;
import io.github.carlos_emr.carlos.commn.dao.BillingONExtDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.BillingONCHeader1;
import io.github.carlos_emr.carlos.commn.model.BillingONExt;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import org.apache.logging.log4j.LogManager;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.owasp.encoder.Encode;
import io.github.carlos_emr.carlos.utility.LogSanitizer;

import java.util.ArrayList;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingSaveService;

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
public class BillingONSave2Action extends ActionSupport {

    private final SecurityInfoManager securityInfoManager;
    private final BillingONCHeader1Dao cheader1Dao;
    private final BillingONExtDao extDao;
    private final UserPropertyDAO userPropertyDAO;
    private final BillingDao billingDao;
    private final BillingSaveService bObj;
    private final io.github.carlos_emr.carlos.billings.ca.on.service.BillingCorrectionRecordService correctionPrep;

    @org.springframework.beans.factory.annotation.Autowired
    public BillingONSave2Action(SecurityInfoManager securityInfoManager,
                                BillingONCHeader1Dao cheader1Dao,
                                BillingONExtDao extDao,
                                UserPropertyDAO userPropertyDAO,
                                BillingDao billingDao,
                                BillingSaveService bObj,
                                io.github.carlos_emr.carlos.billings.ca.on.service.BillingCorrectionRecordService correctionPrep) {
        this.securityInfoManager = securityInfoManager;
        this.cheader1Dao = cheader1Dao;
        this.extDao = extDao;
        this.userPropertyDAO = userPropertyDAO;
        this.billingDao = billingDao;
        this.bObj = bObj;
        this.correctionPrep = correctionPrep;
    }
    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
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
            LogManager.getLogger(BillingONSave2Action.class).warn("Rejected url_back parameter: {}", LogSanitizer.sanitize(rawUrlBack)); // NOSONAR javasecurity:S5145 — sanitized with LogSanitizer
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

        ArrayList vecObj = bObj.getBillingClaimObj(request);
        boolean ret = bObj.addABillingRecord(vecObj);

        String xmlBillType = request.getParameter("xml_billtype");
        if (xmlBillType != null
                && xmlBillType.length() >= 3
                && xmlBillType.substring(0, 3).matches(BillingDataHlp.BILLINGMATCHSTRING_3RDPARTY)) {
            bObj.addPrivateBillExtRecord(request, vecObj);
        } else {
            bObj.addOhipInvoiceTrans(vecObj);
        }

        int billingNo = bObj.getBillingId();

        String value = request.getParameter("payeename");
        if (value == null || value.trim().isEmpty()) {
            value = request.getParameter("payeename1");
        }

        BillingONCHeader1 billing = cheader1Dao.find(billingNo);
        if (billing != null) {
            BillingONExt ext = new BillingONExt();
            ext.setBillingNo(billingNo);
            ext.setDemographicNo(billing.getDemographicNo());
            ext.setKeyVal("payee");
            ext.setValue(value);
            ext.setDateTime(billing.getTimestamp());
            extDao.persist(ext);
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
                    String urlBack = safeUrlBack + separator + "curBillForm=" + Encode.forUriComponent(wrkloadmanagement);
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
