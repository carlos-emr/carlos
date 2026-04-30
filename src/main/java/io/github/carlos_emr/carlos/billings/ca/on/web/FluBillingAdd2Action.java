/**
 * Copyright (c) 2001-2002. Department of Family Medicine, McMaster University. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * This software was written for the
 * Department of Family Medicine
 * McMaster University
 * Hamilton
 * Ontario, Canada

 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */

package io.github.carlos_emr.carlos.billings.ca.on.web;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.carlos.billing.CA.dao.BillingDetailDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillingDetail;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.dao.BillingServiceDao;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.commn.model.BillingService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.utility.SafeEncode;

/**
 * Struts2 action for adding a flu billing record.
 *
 * <p>Migrated from
 * {@code billing/CA/ON/specialtyBilling/fluBilling/dbAddFluBilling.jsp}. Accepts
 * POST only and enforces {@code _admin.billing} write privilege. Builds the XML
 * {@code content} field, persists a {@link Billing} and a linked {@link BillingDetail},
 * then either redirects to the prevention page (when {@code goPrev} is set) or sets
 * the {@code billSaved} request attribute and returns {@link #SUCCESS}.
 *
 * @since 2026-04-05
 */
public class FluBillingAdd2Action extends ActionSupport {

    private static final long serialVersionUID = 1L;

    private final SecurityInfoManager securityInfoManager;
    private final BillingDao billingDao;
    private final BillingDetailDao billingDetailDao;
    private final BillingServiceDao billingServiceDao;
    private final io.github.carlos_emr.carlos.billings.ca.on.service.FluBillingPersistenceService fluBillingPersistenceService;

    public FluBillingAdd2Action(SecurityInfoManager securityInfoManager,
                                BillingDao billingDao,
                                BillingDetailDao billingDetailDao,
                                BillingServiceDao billingServiceDao,
                                io.github.carlos_emr.carlos.billings.ca.on.service.FluBillingPersistenceService fluBillingPersistenceService) {
        this.securityInfoManager = securityInfoManager;
        this.billingDao = billingDao;
        this.billingDetailDao = billingDetailDao;
        this.billingServiceDao = billingServiceDao;
        this.fluBillingPersistenceService = fluBillingPersistenceService;
    }

    /**
     * Creates and persists a flu billing record and its detail line.
     *
     * <p>If the {@code goPrev} parameter equals {@code "goPrev"} and the bill was saved
     * successfully, the response is redirected to the prevention page and {@link #NONE} is
     * returned. Otherwise {@code billSaved} is set as a request attribute and
     * {@link #SUCCESS} is returned.
     *
     * @return {@link #SUCCESS}, {@link #ERROR} for validation failures, or {@link #NONE} for a redirect or invalid method
     * @throws Exception if an unexpected error occurs during persistence
     */
    @Override
    public String execute() throws Exception {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();

        if (!BillingRequestGuards.requirePost(request, response)) {
            return NONE;
        }

        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);
        // Explicit null-session guard matches the sibling 2Actions in this PR.
        // hasPrivilege(null, ...) reaches SecurityInfoManagerImpl and emits a
        // noisy internal ERROR before returning false; fail fast with a clean
        // signal instead.
        if (loggedInInfo == null) {
            throw new SecurityException("missing session");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_admin.billing", "w", null)) {
            throw new SecurityException("missing required security object: _admin.billing");
        }

        // Legacy parameter name "functionid" carries the demographic (patient) number
        String demoNo = request.getParameter("functionid");
        String rd = "null".equals(request.getParameter("rd")) ? "" : request.getParameter("rd");
        String rdohip = "null".equals(request.getParameter("rdohip")) ? "000000" : request.getParameter("rdohip");
        String hctype = request.getParameter("demo_hctype");
        if ("null".equals(hctype) || hctype == null || hctype.isEmpty()) {
            hctype = "ON";
        }

        // Build XML content field — escape values for safe XML embedding
        String demoSex = request.getParameter("demo_sex") != null ? request.getParameter("demo_sex") : "";
        String content = "<rdohip>" + escapeXml(rdohip) + "</rdohip>"
                + "<rd>" + escapeXml(rd) + "</rd>"
                + "<hctype>" + escapeXml(hctype) + "</hctype>"
                + "<demosex>" + escapeXml(demoSex) + "</demosex>"
                + "<specialty>flu</specialty>";

        // Resolve service code description and price
        String svcDesc = null;
        String svcPrice = null;
        List<BillingService> bsList = billingServiceDao.findByServiceCode(request.getParameter("svcCode"));
        for (BillingService bs : bsList) {
            svcDesc = bs.getDescription();
            svcPrice = bs.getValue();
        }

        if (svcPrice == null || !svcPrice.contains(".")) {
            MiscUtils.getLogger().error("FluBillingAdd2Action: svcPrice is null or has no decimal for svcCode={}", LogSanitizer.sanitize(request.getParameter("svcCode"))); // NOSONAR javasecurity:S5145 — sanitized with LogSanitizer
            addActionError("Service code price could not be resolved.");
            return ERROR;
        }

        // Strip decimal point for use as integer-like amount string
        String sPrice = svcPrice.substring(0, svcPrice.indexOf("."))
                + svcPrice.substring(svcPrice.indexOf(".") + 1);

        // Parse the providers parameter safely (format: "OHIP##providerNo")
        String providers = request.getParameter("providers");
        String providerOhipNo = "";
        String providerNo = "";
        if (providers == null || !providers.contains("##") || providers.length() < 8) {
            MiscUtils.getLogger().error("FluBillingAdd2Action: invalid providers format: {}", LogSanitizer.sanitize(providers)); // NOSONAR javasecurity:S5145 — sanitized with LogSanitizer
            addActionError("Invalid provider selection. Expected format: OHIP##providerNo.");
            return ERROR;
        }
        providerOhipNo = providers.substring(0, 6);
        providerNo = providers.substring(7);

        if (demoNo == null || demoNo.trim().isEmpty()) {
            addActionError("Missing demographic number (functionid).");
            return ERROR;
        }

        int clinicNoInt;
        int demoNoInt;
        int appointmentNoInt;
        try {
            clinicNoInt = Integer.parseInt(request.getParameter("clinicNo"));
            demoNoInt = Integer.parseInt(demoNo.trim());
            appointmentNoInt = Integer.parseInt(request.getParameter("appointment_no"));
        } catch (NumberFormatException | NullPointerException e) {
            MiscUtils.getLogger().error("FluBillingAdd2Action: invalid numeric parameter", e);
            addActionError("Invalid numeric parameter (clinicNo, functionid, or appointment_no).");
            return ERROR;
        }

        Billing b = new Billing();
        b.setClinicNo(clinicNoInt);
        b.setDemographicNo(demoNoInt);
        b.setProviderNo(providerNo);
        b.setAppointmentNo(appointmentNoInt);
        b.setOrganizationSpecCode("V03G");
        b.setDemographicName(request.getParameter("demo_name"));
        b.setHin(request.getParameter("demo_hin"));
        b.setUpdateDate(MyDateFormat.getSysDate(request.getParameter("docdate")));
        b.setUpdateTime(new java.util.Date());
        b.setBillingDate(MyDateFormat.getSysDate(request.getParameter("apptDate")));
        b.setBillingTime(MyDateFormat.getSysTime(request.getParameter("start_time")));
        b.setClinicRefCode(request.getParameter("clinic_ref_code"));
        b.setContent(content);
        b.setTotal(svcPrice);
        b.setStatus(request.getParameter("xml_billtype"));
        b.setDob(request.getParameter("demo_dob"));
        b.setVisitDate(null);
        b.setVisitType(request.getParameter("xml_visittype"));
        b.setProviderOhipNo(providerOhipNo);
        b.setProviderRmaNo("");
        b.setApptProviderNo(request.getParameter("apptProvider"));
        b.setAsstProviderNo("0");
        b.setCreator(request.getParameter("doccreator"));

        BillingDetail bd = new BillingDetail();
        bd.setServiceCode(request.getParameter("svcCode"));
        bd.setServiceDesc(svcDesc);
        bd.setBillingAmount(sPrice);
        bd.setDiagnosticCode(request.getParameter("dxCode"));
        bd.setAppointmentDate(MyDateFormat.getSysDate(request.getParameter("apptDate")));
        bd.setStatus(request.getParameter("xml_billtype"));
        bd.setBillingUnit("1");

        // Atomic persist via @Transactional service. Pre-fix the two
        // billingDao.persist + billingDetailDao.persist calls ran inline with
        // no tx boundary — a detail-insert failure left an orphan parented-
        // only Billing row.
        fluBillingPersistenceService.persistFluBilling(b, bd);

        boolean billSaved = b.getId() != null && b.getId() > 0;

        String goPrev = request.getParameter("goPrev");
        if ("goPrev".equals(goPrev) && billSaved) {
            String ctx = request.getContextPath();
            response.sendRedirect(ctx + "/prevention/ViewAddPreventionData?prevention=Flu&demographic_no="
                    + SafeEncode.forUriComponent(demoNo));
            return NONE;
        }

        request.setAttribute("billSaved", billSaved);
        return SUCCESS;
    }

    /**
     * Escapes XML special characters ({@code <}, {@code >}, {@code &}, {@code "}, {@code '})
     * for safe embedding in XML content strings.
     *
     * @param value the raw string value
     * @return the XML-escaped string
     */
    private static String escapeXml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
