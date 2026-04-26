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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billing.CA.dao.BillingInrDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillingInr;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingInrReportViewModel;
import io.github.carlos_emr.carlos.commn.dao.ClinicLocationDao;
import io.github.carlos_emr.carlos.commn.model.ClinicLocation;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

/**
 * Assembles a {@link BillingInrReportViewModel} for the
 * {@code billing/CA/ON/inr/reportINR.jsp} INR Batch Billing report.
 *
 * <p>Replaces the legacy JSP's inline DAO usage and the dead JDBC
 * {@code ResultSet rsclinic} placeholder. The bill-row provider lookup is
 * batched into a single name map keyed by {@code provider_no} to avoid the
 * legacy N+1 {@code providerDao.getProvider} call inside the row loop.</p>
 *
 * @since 2026-04-26
 */
public final class BillingInrReportDataAssembler {

    private final ProviderDao providerDao;
    private final BillingInrDao billingInrDao;
    private final ClinicLocationDao clinicLocationDao;

    public BillingInrReportDataAssembler() {
        this(SpringUtils.getBean(ProviderDao.class),
             SpringUtils.getBean(BillingInrDao.class),
             SpringUtils.getBean(ClinicLocationDao.class));
    }

    BillingInrReportDataAssembler(ProviderDao providerDao,
                                  BillingInrDao billingInrDao,
                                  ClinicLocationDao clinicLocationDao) {
        this.providerDao = providerDao;
        this.billingInrDao = billingInrDao;
        this.clinicLocationDao = clinicLocationDao;
    }

    public BillingInrReportViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        String userNo = loggedInInfo == null || loggedInInfo.getLoggedInProviderNo() == null
                ? "" : loggedInInfo.getLoggedInProviderNo();
        String providerView = request.getParameter("provider_no");
        if (providerView == null) {
            providerView = "";
        }

        CarlosProperties props = CarlosProperties.getInstance();
        String clinicView = nullToEmpty(props.getProperty("clinic_view"));
        String clinicNo = nullToEmpty(props.getProperty("clinic_no"));
        boolean newOnBilling = "true".equals(props.getProperty("isNewONbilling", ""));
        String contextPath = nullToEmpty(request.getContextPath());
        String inrBillingActionUrl = contextPath
                + (newOnBilling
                ? "/billing/CA/ON/ViewInrOnGenINRbilling"
                : "/billing/CA/ON/ViewInrGenINRbilling");

        GregorianCalendar now = new GregorianCalendar();
        int curYear = now.get(Calendar.YEAR);
        int curMonth = now.get(Calendar.MONTH) + 1;
        int curDay = now.get(Calendar.DAY_OF_MONTH);
        String nowDate = curYear + "/" + curMonth + "/" + curDay;
        String nowTime = now.get(Calendar.HOUR_OF_DAY) + ":"
                + now.get(Calendar.MINUTE) + ":"
                + now.get(Calendar.SECOND);
        String defaultServiceDate = curYear + "-" + curMonth + "-" + curDay;

        List<BillingInrReportViewModel.ProviderOption> providers = buildProviderOptions();
        List<BillingInrReportViewModel.ClinicLocationOption> clinicLocations = buildClinicLocationOptions();
        List<BillingInrReportViewModel.BillRow> billRows = buildBillRows(providerView);

        return BillingInrReportViewModel.builder()
                .userNo(userNo)
                .providerView(providerView)
                .clinicView(clinicView)
                .clinicNo(clinicNo)
                .inrBillingActionUrl(inrBillingActionUrl)
                .nowDate(nowDate)
                .nowTime(nowTime)
                .curYear(curYear)
                .curMonth(curMonth)
                .curDay(curDay)
                .defaultServiceDate(defaultServiceDate)
                .providers(providers)
                .clinicLocations(clinicLocations)
                .billRows(billRows)
                .build();
    }

    private List<BillingInrReportViewModel.ProviderOption> buildProviderOptions() {
        List<BillingInrReportViewModel.ProviderOption> opts = new ArrayList<>();
        if (providerDao == null) {
            return opts;
        }
        for (Provider p : providerDao.getActiveProviders()) {
            String ohip = p.getOhipNo();
            if (ohip == null || ohip.isEmpty()) {
                continue;
            }
            opts.add(new BillingInrReportViewModel.ProviderOption(
                    nullToEmpty(p.getProviderNo()),
                    nullToEmpty(p.getFirstName()),
                    nullToEmpty(p.getLastName())));
        }
        return opts;
    }

    private List<BillingInrReportViewModel.ClinicLocationOption> buildClinicLocationOptions() {
        List<BillingInrReportViewModel.ClinicLocationOption> opts = new ArrayList<>();
        if (clinicLocationDao == null) {
            return opts;
        }
        for (ClinicLocation cl : clinicLocationDao.findByClinicNo(1)) {
            opts.add(new BillingInrReportViewModel.ClinicLocationOption(
                    nullToEmpty(cl.getClinicLocationNo()),
                    nullToEmpty(cl.getClinicLocationName())));
        }
        return opts;
    }

    private List<BillingInrReportViewModel.BillRow> buildBillRows(String providerView) {
        List<BillingInrReportViewModel.BillRow> rows = new ArrayList<>();
        if (billingInrDao == null) {
            return rows;
        }
        // The legacy JSP routed "all" to a SQL wildcard "%". Preserve the
        // contract; dao implementation expands it via LIKE.
        String daoArg = "all".equals(providerView) ? "%" : providerView;
        if (daoArg == null || daoArg.isEmpty()) {
            return rows;
        }

        List<BillingInr> bills = billingInrDao.findCurrentByProviderNo(daoArg);
        Map<String, String> providerNameByNo = lookupProviderNames(bills);

        for (BillingInr b : bills) {
            String demoNo = String.valueOf(b.getDemographicNo());
            String demoName = nullToEmpty(b.getDemographicName());
            String providerNo = nullToEmpty(b.getProviderNo());
            String providerName = providerNameByNo.getOrDefault(providerNo, "");
            String serviceCode = nullToEmpty(b.getServiceCode());
            String billingAmount = nullToEmpty(b.getBillingAmount());
            String diagnosticCode = nullToEmpty(b.getDiagnosticCode());
            String billStatus = nullToEmpty(b.getStatus());
            String billDate = b.getCreateDateTime() == null
                    ? "" : nullToEmpty(ConversionUtils.toDateString(b.getCreateDateTime()));
            String lastBillLabel = "A".equals(billStatus) && billDate.length() >= 10
                    ? billDate.substring(0, 10)
                    : "Not Available";

            rows.add(new BillingInrReportViewModel.BillRow(
                    b.getId() == null ? "" : b.getId().toString(),
                    demoNo,
                    demoName,
                    URLEncoder.encode(demoName, StandardCharsets.UTF_8),
                    providerName,
                    URLEncoder.encode(providerName, StandardCharsets.UTF_8),
                    serviceCode,
                    billingAmount,
                    diagnosticCode,
                    lastBillLabel));
        }
        return rows;
    }

    private Map<String, String> lookupProviderNames(List<BillingInr> bills) {
        Map<String, String> out = new HashMap<>();
        if (providerDao == null) {
            return out;
        }
        for (BillingInr b : bills) {
            String pno = b.getProviderNo();
            if (pno == null || pno.isEmpty() || out.containsKey(pno)) {
                continue;
            }
            Provider p = providerDao.getProvider(pno);
            if (p != null) {
                out.put(pno, nullToEmpty(p.getFirstName()) + " " + nullToEmpty(p.getLastName()));
            } else {
                out.put(pno, "");
            }
        }
        return out;
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }
}
