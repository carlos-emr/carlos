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

import java.io.IOException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Enumeration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.MyDateFormat;
import io.github.carlos_emr.carlos.billing.CA.dao.BillingDetailDao;
import io.github.carlos_emr.carlos.billing.CA.dao.BillingInrDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillingDetail;
import io.github.carlos_emr.carlos.billing.CA.model.BillingInr;
import io.github.carlos_emr.carlos.commn.dao.BillingDao;
import io.github.carlos_emr.carlos.commn.model.Billing;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Generates legacy {@link Billing}/{@link BillingDetail} rows from accepted
 * INR billing entries (the non-OHIP-claim path). Replaces the former
 * {@code billing/CA/ON/inr/genINRbilling.jsp} controller-in-a-JSP.
 *
 * <p>POST-only. For each {@code inrbilling<id>} request parameter, looks
 * up the matching {@link BillingInr}/{@link Demographic} pair via
 * {@link BillingInrDao#search_inrbilling_dt_billno}, persists a
 * {@link Billing} header through {@link BillingDao#persist}, marks the
 * INR row {@code A}ccepted, and persists a single {@link BillingDetail}
 * line. Closes the popup window via a {@code self.close()} script — the
 * legacy contract callers expect.</p>
 *
 * @since 2026-04-26
 */
public class ViewInrBillingGeneration2Action extends ActionSupport {

    private static final Pattern INR_BILLING_PARAM = Pattern.compile("^inrbilling(\\d+)$");

    private final SecurityInfoManager securityInfoManager;
    private final BillingInrDao billingInrDao;
    private final BillingDao billingDao;
    private final BillingDetailDao billingDetailDao;

    public ViewInrBillingGeneration2Action(SecurityInfoManager securityInfoManager,
                                BillingInrDao billingInrDao,
                                BillingDao billingDao,
                                BillingDetailDao billingDetailDao) {
        this.securityInfoManager = securityInfoManager;
        this.billingInrDao = billingInrDao;
        this.billingDao = billingDao;
        this.billingDetailDao = billingDetailDao;
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    @Override
    public String execute() {
        HttpServletRequest request = ServletActionContext.getRequest();
        HttpServletResponse response = ServletActionContext.getResponse();
        LoggedInInfo loggedInInfo = LoggedInInfo.getLoggedInInfoFromSession(request);

        if (loggedInInfo == null) {
            throw new SecurityException("missing session");
        }
        if (!securityInfoManager.hasPrivilege(loggedInInfo, "_billing", "w", null)) {
            throw new SecurityException("missing required sec object (_billing)");
        }

        if (!"POST".equalsIgnoreCase(request.getMethod())) {
            response.setHeader("Allow", "POST");
            try {
                response.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            } catch (IOException ignore) {
                // Container is shutting down or response already committed.
            }
            return NONE;
        }

        String clinicNo = request.getParameter("clinic_no");
        String clinicRefCode = request.getParameter("xml_location");
        String creator = requireLoggedInProviderNo(loggedInInfo);
        String curDate = request.getParameter("curDate");
        String appointmentDate = request.getParameter("xml_appointment_date");

        SimpleDateFormat timeFormatter = new SimpleDateFormat("HH:mm");

        Enumeration<String> paramNames = request.getParameterNames();
        Integer clinicNoValue = null;
        while (paramNames.hasMoreElements()) {
            String name = paramNames.nextElement();
            Integer billingInrNo = inrBillingNoFromParam(name);
            if (billingInrNo == null) {
                continue;
            }
            if (clinicNoValue == null) {
                clinicNoValue = parseRequiredInt(clinicNo, "clinic_no");
            }

            for (Object[] row : billingInrDao.search_inrbilling_dt_billno(billingInrNo)) {
                BillingInr inr = (BillingInr) row[0];
                Demographic demo = (Demographic) row[1];
                persistOneBilling(inr, demo, billingInrNo, clinicNoValue, clinicRefCode,
                                  creator, curDate, appointmentDate, timeFormatter);
            }
        }

        response.setContentType("text/html");
        response.setCharacterEncoding("UTF-8");
        try {
            response.getWriter().print("<script>self.close();</script>");
        } catch (IOException e) {
            MiscUtils.getLogger().warn("Failed to write window-close response", e);
        }
        return NONE;
    }

    private void persistOneBilling(BillingInr inr, Demographic demo, int billingInrNo,
                                   int clinicNo, String clinicRefCode,
                                   String creator, String curDate, String appointmentDate,
                                   SimpleDateFormat timeFormatter) {
        Integer demographicNo = inr.getDemographicNo();
        if (demographicNo == null) {
            throw new BillingValidationException("INR billing row has no demographic number");
        }
        String demoNo = String.valueOf(demographicNo);
        String demoName = inr.getDemographicName();
        String demoHin = demo.getHin() + demo.getVer();
        String demoDob = demo.getYearOfBirth() + demo.getMonthOfBirth() + demo.getDateOfBirth();
        String providerNo = String.valueOf(inr.getProviderNo());
        String providerOhipNo = inr.getProviderOhipNo();
        String providerRmaNo = inr.getProviderRmaNo();
        String diagnosticCode = inr.getDiagnosticCode();
        String serviceCode = inr.getServiceCode();
        String serviceDesc = inr.getServiceDesc();
        String billingAmount = stripDecimalPoint(inr.getBillingAmount());
        String billingUnit = inr.getBillingUnit();

        Billing billing = new Billing();
        billing.setClinicNo(clinicNo);
        billing.setDemographicNo(demographicNo);
        billing.setProviderNo(providerNo);
        billing.setAppointmentNo(0);
        billing.setOrganizationSpecCode("V03");
        billing.setDemographicName(demoName);
        billing.setHin(demoHin);
        billing.setUpdateDate(MyDateFormat.getSysDate(curDate));
        try {
            billing.setUpdateTime(timeFormatter.parse("00:00"));
            billing.setBillingTime(timeFormatter.parse("00:00"));
        } catch (ParseException e) {
            // "00:00" is hard-coded; this should never throw.
            throw new IllegalStateException("Unparseable hard-coded time", e);
        }
        billing.setBillingDate(MyDateFormat.getSysDate(appointmentDate));
        billing.setClinicRefCode(clinicRefCode);
        billing.setContent("");
        billing.setTotal(billingAmount);
        billing.setStatus("O");
        billing.setDob(demoDob);
        billing.setVisitDate(MyDateFormat.getSysDate(appointmentDate));
        billing.setVisitType("00");
        billing.setProviderOhipNo(providerOhipNo);
        billing.setProviderRmaNo(providerRmaNo);
        billing.setApptProviderNo("");
        billing.setAsstProviderNo("");
        billing.setCreator(creator);

        billingDao.persist(billing);

        Integer billNo = billingDao.search_billing_no_by_appt(demographicNo, 0);
        if (billNo == null) {
            throw new BillingValidationException("Unable to resolve generated INR billing number");
        }

        BillingInr inrToUpdate = billingInrDao.find(billingInrNo);
        if (inrToUpdate != null && !"D".equals(inrToUpdate.getStatus())) {
            inrToUpdate.setStatus("A");
            inrToUpdate.setCreateDateTime(ConversionUtils.fromDateString(appointmentDate));
            billingInrDao.merge(inrToUpdate);
        }

        BillingDetail detail = new BillingDetail();
        detail.setBillingNo(billNo);
        detail.setServiceCode(serviceCode);
        detail.setServiceDesc(serviceDesc);
        detail.setBillingAmount(billingAmount);
        detail.setDiagnosticCode(diagnosticCode);
        detail.setAppointmentDate(MyDateFormat.getSysDate(appointmentDate));
        detail.setStatus("O");
        detail.setBillingUnit(billingUnit);
        billingDetailDao.persist(detail);
    }

    /**
     * Removes the decimal point from a dollar amount, e.g. {@code "33.70"}
     * → {@code "3370"}. Matches the legacy JSP's wire format for
     * {@link Billing#setTotal(String)}.
     */
    private static String stripDecimalPoint(String amount) {
        int dot = amount.indexOf('.');
        if (dot < 0) {
            return amount;
        }
        return amount.substring(0, dot) + amount.substring(dot + 1);
    }

    private static Integer inrBillingNoFromParam(String name) {
        Matcher matcher = INR_BILLING_PARAM.matcher(name);
        if (!matcher.matches()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }

    private static String requireLoggedInProviderNo(LoggedInInfo loggedInInfo) {
        String providerNo = loggedInInfo.getLoggedInProviderNo();
        if (providerNo == null || providerNo.isBlank()) {
            throw new SecurityException("missing logged-in provider number");
        }
        return providerNo;
    }

    private static int parseRequiredInt(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BillingValidationException("Missing required numeric field: " + fieldName);
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException nfe) {
            throw new BillingValidationException("Invalid numeric field: " + fieldName, nfe);
        }
    }
}
