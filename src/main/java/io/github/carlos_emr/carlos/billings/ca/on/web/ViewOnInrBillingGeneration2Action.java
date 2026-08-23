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
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.billing.CA.dao.BillingInrDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillingInr;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimHeaderDto;
import io.github.carlos_emr.carlos.billings.ca.on.support.BillingOnConstants;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingClaimItemDto;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnClaimPersister;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;

/**
 * Generates Ontario MOH claims from accepted INR billing rows. Replaces the
 * former {@code billing/CA/ON/inr/onGenINRbilling.jsp} controller-in-a-JSP.
 *
 * <p>POST-only. For each {@code inrbilling<id>} request parameter, looks up
 * the matching {@link BillingInr}/{@link Demographic} pair via
 * {@link BillingInrDao#search_inrbilling_dt_billno}, builds a claim header,
 * persists it through {@link BillingOnClaimPersister}, marks the
 * INR row {@code A}ccepted, and persists a single billing item line.
 * Redirects to the INR report on completion.</p>
 *
 * @since 2026-04-26
 */
public class ViewOnInrBillingGeneration2Action extends ActionSupport {

    private static final Pattern INR_BILLING_PARAM = Pattern.compile("^inrbilling(\\d+)$");

    private static final DateTimeFormatter UPDATE_TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SecurityInfoManager securityInfoManager;
    private final BillingInrDao billingInrDao;
    private final BillingOnClaimPersister persistenceService;

    public ViewOnInrBillingGeneration2Action(SecurityInfoManager securityInfoManager,
                                  BillingInrDao billingInrDao,
                                  BillingOnClaimPersister persistenceService) {
        this.securityInfoManager = securityInfoManager;
        this.billingInrDao = billingInrDao;
        this.persistenceService = persistenceService;
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
        String creator = request.getParameter("curUser");
        String appointmentDate = request.getParameter("xml_appointment_date");

        Enumeration<String> paramNames = request.getParameterNames();
        while (paramNames.hasMoreElements()) {
            String name = paramNames.nextElement();
            Integer billingInrNo = inrBillingNoFromParam(name);
            if (billingInrNo == null) {
                continue;
            }

            for (Object[] row : billingInrDao.search_inrbilling_dt_billno(billingInrNo)) {
                BillingInr inr = (BillingInr) row[0];
                Demographic demo = (Demographic) row[1];
                persistOneClaim(inr, demo, billingInrNo, clinicNo, clinicRefCode,
                                creator, appointmentDate);
            }
        }

        return SUCCESS;
    }

    private void persistOneClaim(BillingInr inr, Demographic demo, int billingInrNo,
                                 String clinicNo, String clinicRefCode,
                                 String creator, String appointmentDate) {
        String demoNo = demo.getDemographicNo().toString();
        String demoName = inr.getDemographicName();
        String demoDob = demo.getYearOfBirth() + demo.getMonthOfBirth() + demo.getDateOfBirth();
        String providerNo = String.valueOf(inr.getProviderNo());
        String providerOhipNo = inr.getProviderOhipNo();
        String providerRmaNo = inr.getProviderRmaNo();
        String diagnosticCode = inr.getDiagnosticCode();
        String serviceCode = inr.getServiceCode();
        String billingAmount = inr.getBillingAmount();
        String billingUnit = inr.getBillingUnit();
        String hcType = demo.getHcType();
        String sex = demo.getSex();

        BillingClaimHeaderDto header = new BillingClaimHeaderDto();
        header = header.withTransactionId(BillingOnConstants.CLAIMHEADER1_TRANSACTIONIDENTIFIER);
        header = header.withRecordId(BillingOnConstants.CLAIMHEADER1_REORDIDENTIFICATION);
        header = header.withHin(demo.getHin());
        header = header.withVer(demo.getVer());
        header = header.withDob(demoDob);
        header = header.withPayProgram("ON".equals(hcType) ? "HCP" : "RMB");
        header = header.withPayee(BillingOnConstants.CLAIMHEADER1_PAYEE);
        header = header.withReferralNumber("");
        header = header.withFacilityNumber(clinicRefCode);
        header = header.withAdmissionDate("");
        header = header.withReferringLabNumber("");
        header = header.withManualReview("");
        header = header.withLocation(clinicNo);
        header = header.withDemographicNo(demoNo);
        header = header.withProviderNo(providerNo);
        header = header.withAppointmentNo("0");
        header = header.withDemographicName(demoName);
        header = header.withSex(sex);
        header = header.withProvince(hcType);
        header = header.withBillingDate(appointmentDate);
        header = header.withBillingTime("00:00:00");
        header = header.withUpdateDateTime(LocalDateTime.now().format(UPDATE_TS_FMT));
        header = header.withTotal(billingAmount);
        header = header.withPaid("");
        header = header.withStatus("O");
        header = header.withComment("");
        header = header.withVisitType("00");
        header = header.withProviderOhipNo(providerOhipNo);
        header = header.withProviderRmaNo(providerRmaNo);
        header = header.withAppointmentProviderNo("");
        header = header.withAssistantProviderNo("");
        header = header.withCreator(creator);

        int billNo = persistenceService.addOneClaimHeaderRecord(header);

        BillingInr inrToUpdate = billingInrDao.find(billingInrNo);
        if (inrToUpdate != null && !"D".equals(inrToUpdate.getStatus())) {
            inrToUpdate.setStatus("A");
            inrToUpdate.setCreateDateTime(ConversionUtils.fromDateString(appointmentDate));
            billingInrDao.merge(inrToUpdate);
        }

        BillingClaimItemDto item = new BillingClaimItemDto();
        item = item.withTransactionId(BillingOnConstants.ITEM_TRANSACTIONIDENTIFIER);
        item = item.withRecordId(BillingOnConstants.ITEM_REORDIDENTIFICATION);
        item = item.withServiceCode(serviceCode);
        item = item.withFee(billingAmount);
        item = item.withServiceNumber(billingUnit);
        item = item.withServiceDate(appointmentDate);
        item = item.withDx(diagnosticCode);
        item = item.withDx1("");
        item = item.withDx2("");
        item = item.withStatus("O");

        List<BillingClaimItemDto> items = new ArrayList<>(1);
        items.add(item);
        persistenceService.addItemRecord(items, billNo);
    }

    private static Integer inrBillingNoFromParam(String name) {
        Matcher matcher = INR_BILLING_PARAM.matcher(name);
        if (!matcher.matches()) {
            return null;
        }
        return Integer.parseInt(matcher.group(1));
    }
}
