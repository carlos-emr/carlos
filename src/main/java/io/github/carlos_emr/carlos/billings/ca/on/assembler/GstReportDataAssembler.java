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

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import io.github.carlos_emr.carlos.billings.ca.on.administration.GstReport;
import io.github.carlos_emr.carlos.billings.ca.on.data.GstReportViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingONLookupService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.DateUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Assembles {@link GstReportViewModel} for {@code admin/gstreport.jsp}.
 * Pulls request-param echoes, the GST-by-service-date row list, the
 * provider drop-down options (privacy-aware via
 * {@link BillingONLookupService}), and the running totals.
 *
 * <p>Replaces the ~70-line scriptlet block at the head of the legacy JSP.
 * The assembler is invoked from {@link io.github.carlos_emr.carlos.billings.ca.on.administration.GstReport2Action}
 * and exposes the model as request attribute {@code gstReportModel}.</p>
 *
 * @since 2026-04-27
 */
@Service
@Lazy
public class GstReportDataAssembler {

    private final SecurityInfoManager securityInfoManager;
    private final GstReport gstReport;
    private final BillingONLookupService lookupService;

    public GstReportDataAssembler(SecurityInfoManager securityInfoManager,
                                  GstReport gstReport,
                                  BillingONLookupService lookupService) {
        this.securityInfoManager = securityInfoManager;
        this.gstReport = gstReport;
        this.lookupService = lookupService;
    }

    public GstReportViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        String providerNo = nullToEmpty(request.getParameter("providerview"));
        String startDate = nullToEmpty(request.getParameter("xml_vdate"));
        String endDate = nullToEmpty(request.getParameter("xml_appointment_date"));
        String today = DateUtils.sumDate("yyyy-MM-dd", "0");

        // Privacy gating mirrors the legacy JSP: site / team privacy widen
        // the provider list; otherwise the dropdown shows only the logged-in
        // provider.
        String curProvider = loggedInInfo == null ? "" : loggedInInfo.getLoggedInProviderNo();
        boolean isSitePrivacy = loggedInInfo != null
                && securityInfoManager.hasPrivilege(loggedInInfo, "_site_access_privacy", "r", null);
        boolean isTeamPrivacy = loggedInInfo != null
                && securityInfoManager.hasPrivilege(loggedInInfo, "_team_access_privacy", "r", null);

        List<String> rawProviders;
        if (isTeamPrivacy) {
            rawProviders = lookupService.getCurTeamProviderStr(curProvider);
        } else if (isSitePrivacy) {
            rawProviders = lookupService.getCurSiteProviderStr(curProvider);
        } else {
            rawProviders = lookupService.getCurProviderStr();
        }

        List<GstReportViewModel.ProviderOption> providerOptions = new ArrayList<>(rawProviders.size());
        for (String raw : rawProviders) {
            String[] parts = raw.split("\\|");
            if (parts.length >= 3) {
                providerOptions.add(new GstReportViewModel.ProviderOption(parts[0], parts[1], parts[2]));
            }
        }

        // Aggregate the GST rows. Filter to rows where gst > 0 (matches the
        // scriptlet's `if (gst.doubleValue() > 0)` wrap around the <TR>).
        List<Properties> rawRows = gstReport.getGST(loggedInInfo, providerNo, startDate, endDate);
        List<GstReportViewModel.Row> rows = new ArrayList<>();
        BigDecimal gstTotal = BigDecimal.ZERO;
        BigDecimal earnedTotal = BigDecimal.ZERO;
        BigDecimal billedTotal = BigDecimal.ZERO;

        for (Properties row : rawRows) {
            BigDecimal billed = parseScaledOrZero(row.getProperty("total"));
            BigDecimal gst = parseScaledOrZero(row.getProperty("gst"));
            BigDecimal earned = billed.subtract(gst);

            billedTotal = billedTotal.add(billed);
            gstTotal = gstTotal.add(gst);
            earnedTotal = earnedTotal.add(earned);

            if (gst.signum() > 0) {
                rows.add(new GstReportViewModel.Row(
                        nullToEmpty(row.getProperty("date")),
                        nullToEmpty(row.getProperty("demographic_no")),
                        nullToEmpty(row.getProperty("name")),
                        gst, earned, billed));
            }
        }

        return new GstReportViewModel(
                today, startDate, endDate, providerNo,
                providerOptions, rows,
                gstTotal, earnedTotal, billedTotal);
    }

    private static BigDecimal parseScaledOrZero(String value) {
        if (value == null || value.isEmpty()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
