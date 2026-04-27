/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.web;
import org.springframework.beans.factory.annotation.Autowired;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import io.github.carlos_emr.carlos.billing.CA.dao.BillingInrDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillingInr;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingClaimHeader1Data;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingDataHlp;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingItemData;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingONClaimPersistenceService;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;

/**
 * Generates Ontario MOH claims from accepted INR billing rows. Replaces the
 * former {@code billing/CA/ON/inr/onGenINRbilling.jsp} controller-in-a-JSP.
 *
 * <p>POST-only. For each {@code inrbilling<id>} request parameter, looks up
 * the matching {@link BillingInr}/{@link Demographic} pair via
 * {@link BillingInrDao#search_inrbilling_dt_billno}, builds a claim header,
 * persists it through {@link BillingONClaimPersistenceService}, marks the
 * INR row {@code A}ccepted, and persists a single billing item line.
 * Redirects to the INR report on completion.</p>
 *
 * @since 2026-04-26
 */
public class ViewInrOnGenINRbilling2Action extends ActionSupport {

    private static final DateTimeFormatter UPDATE_TS_FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final SecurityInfoManager securityInfoManager;
    private final BillingInrDao billingInrDao;
    private final BillingONClaimPersistenceService persistenceService;

    /** Constructor injection used by Spring + Struts2's SpringObjectFactory. */
    @Autowired
    public ViewInrOnGenINRbilling2Action(SecurityInfoManager securityInfoManager,
                                  BillingInrDao billingInrDao,
                                  BillingONClaimPersistenceService persistenceService) {
        this.securityInfoManager = securityInfoManager;
        this.billingInrDao = billingInrDao;
        this.persistenceService = persistenceService;
    }

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
            if (name.indexOf("inrbilling") == -1) {
                continue;
            }

            int billingInrNo = Integer.parseInt(name.substring(10));
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

        BillingClaimHeader1Data header = new BillingClaimHeader1Data();
        header.setTransc_id(BillingDataHlp.CLAIMHEADER1_TRANSACTIONIDENTIFIER);
        header.setRec_id(BillingDataHlp.CLAIMHEADER1_REORDIDENTIFICATION);
        header.setHin(demo.getHin());
        header.setVer(demo.getVer());
        header.setDob(demoDob);
        header.setPay_program("ON".equals(hcType) ? "HCP" : "RMB");
        header.setPayee(BillingDataHlp.CLAIMHEADER1_PAYEE);
        header.setRef_num("");
        header.setFacilty_num(clinicRefCode);
        header.setAdmission_date("");
        header.setRef_lab_num("");
        header.setMan_review("");
        header.setLocation(clinicNo);
        header.setDemographic_no(demoNo);
        header.setProviderNo(providerNo);
        header.setAppointment_no("0");
        header.setDemographic_name(demoName);
        header.setSex(sex);
        header.setProvince(hcType);
        header.setBilling_date(appointmentDate);
        header.setBilling_time("00:00:00");
        header.setUpdate_datetime(LocalDateTime.now().format(UPDATE_TS_FMT));
        header.setTotal(billingAmount);
        header.setPaid("");
        header.setStatus("O");
        header.setComment("");
        header.setVisittype("00");
        header.setProvider_ohip_no(providerOhipNo);
        header.setProvider_rma_no(providerRmaNo);
        header.setApptProvider_no("");
        header.setAsstProvider_no("");
        header.setCreator(creator);

        int billNo = persistenceService.addOneClaimHeaderRecord(header);

        BillingInr inrToUpdate = billingInrDao.find(billingInrNo);
        if (inrToUpdate != null && !"D".equals(inrToUpdate.getStatus())) {
            inrToUpdate.setStatus("A");
            inrToUpdate.setCreateDateTime(ConversionUtils.fromDateString(appointmentDate));
            billingInrDao.merge(inrToUpdate);
        }

        BillingItemData item = new BillingItemData();
        item.setTransc_id(BillingDataHlp.ITEM_TRANSACTIONIDENTIFIER);
        item.setRec_id(BillingDataHlp.ITEM_REORDIDENTIFICATION);
        item.setService_code(serviceCode);
        item.setFee(billingAmount);
        item.setSer_num(billingUnit);
        item.setService_date(appointmentDate);
        item.setDx(diagnosticCode);
        item.setDx1("");
        item.setDx2("");
        item.setStatus("O");

        List<BillingItemData> items = new ArrayList<>(1);
        items.add(item);
        persistenceService.addItemRecord(items, billNo);
    }
}
