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
package io.github.carlos_emr.carlos.billings.ca.on.assembler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import jakarta.servlet.http.HttpServletRequest;

import org.springframework.stereotype.Service;

import io.github.carlos_emr.carlos.billings.ca.on.service.GstReportService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingDataLoadException;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.GstReportViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnLookupService;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.DateUtils;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * Assembles {@link GstReportViewModel} for {@code admin/gstreport.jsp}.
 * Pulls request-param echoes, the GST-by-service-date row list, the
 * provider drop-down options (privacy-aware via
 * {@link BillingOnLookupService}), and the running totals.
 *
 * <p>Replaces the ~70-line scriptlet block at the head of the legacy JSP.
 * The assembler is invoked from {@link io.github.carlos_emr.carlos.billings.ca.on.web.GstReport2Action}
 * and exposes the model as request attribute {@code gstReportModel}.</p>
 *
 * @since 2026-04-27
 */
@Service
public class GstReportViewModelAssembler {

    private final SecurityInfoManager securityInfoManager;
    private final GstReportService gstReport;
    private final BillingOnLookupService lookupService;

    public GstReportViewModelAssembler(SecurityInfoManager securityInfoManager,
                                  GstReportService gstReport,
                                  BillingOnLookupService lookupService) {
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

        List<io.github.carlos_emr.carlos.billings.ca.on.dto.ProviderDropdownEntry> rawProviders;
        if (isTeamPrivacy) {
            rawProviders = lookupService.getCurTeamProviderStr(curProvider);
        } else if (isSitePrivacy) {
            rawProviders = lookupService.getCurSiteProviderStr(curProvider);
        } else {
            rawProviders = lookupService.getCurProviderStr();
        }
        if (rawProviders == null) {
            rawProviders = java.util.Collections.emptyList();
        }

        List<GstReportViewModel.ProviderOption> providerOptions = new ArrayList<>(rawProviders.size());
        for (io.github.carlos_emr.carlos.billings.ca.on.dto.ProviderDropdownEntry p : rawProviders) {
            providerOptions.add(new GstReportViewModel.ProviderOption(p.providerNo(), p.lastName(), p.firstName()));
        }

        // Aggregate the GST rows. Filter to rows where gst > 0 (matches the
        // scriptlet's `if (gst.doubleValue() > 0)` wrap around the <TR>).
        List<Properties> rawRows = gstReport.getGST(loggedInInfo, providerNo, startDate, endDate);
        List<GstReportViewModel.Row> rows = new ArrayList<>();
        BigDecimal gstTotal = BigDecimal.ZERO;
        BigDecimal earnedTotal = BigDecimal.ZERO;
        BigDecimal billedTotal = BigDecimal.ZERO;

        for (Properties row : rawRows) {
            BigDecimal billed = parseScaledOrZero(row.getProperty("total"), "total", row);
            BigDecimal gst = parseScaledOrZero(row.getProperty("gst"), "gst", row);
            BigDecimal earned = billed.subtract(gst);

            billedTotal = billedTotal.add(billed);
            gstTotal = gstTotal.add(gst);
            earnedTotal = earnedTotal.add(earned);

            // Preserve the legacy report contract: totals are computed over the
            // full query result, but the visible table only includes rows with
            // positive GST.
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

    private static BigDecimal parseScaledOrZero(String value, String field, Properties row) {
        if (value == null || value.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(value.trim()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            throw new BillingDataLoadException(
                    "Malformed GST report amount",
                    e,
                    BillingDataLoadException.Phase.DAO_QUERY,
                    Map.of("field", field,
                            "serviceDate", nullToEmpty(row.getProperty("date")),
                            "demographicNo", nullToEmpty(row.getProperty("demographic_no"))));
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
