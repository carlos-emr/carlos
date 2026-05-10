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

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billing.CA.dao.BillActivityDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillActivity;
import io.github.carlos_emr.carlos.billings.ca.on.support.BillingOnConstants;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingDiskNameDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.DiskFilenameRow;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnMriViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnLookupService;
import io.github.carlos_emr.carlos.commn.dao.ProviderBillCenterDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderDataDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderBillCenter;
import io.github.carlos_emr.carlos.commn.model.ProviderData;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingReviewLoader;

/**
 * Assembles {@link BillingOnMriViewModel} for {@code billingONMRI.jsp}, the
 * "Generate OHIP File" page (Medical Records Interchange / OHIP claim
 * diskette — not magnetic resonance imaging). Owns the 4 inline
 * {@code SpringUtils.getBean} lookups the JSP body used to perform
 * (ProviderDao, BillActivityDao, ProviderDataDao, ProviderBillCenterDao).
 *
 * <p>The legacy JSP scriptlet split provider lookup across two passes: one
 * for the JS provider→bill-center map (using {@code getBillableProviders})
 * and one for the older-records OHIP-number→name lookup (using
 * {@code getActiveProviders}). Both are consolidated here.</p>
 *
 * <p>The {@code BillingReviewLoader} and {@code BillingOnLookupService}
 * helpers are constructor-injected like the rest of the dependency graph.</p>
 *
 * @since 2026-04-26
 */
@org.springframework.stereotype.Service
public class BillingOnMriViewModelAssembler {

    /** Color cycle the legacy JSP used to highlight the selected year row. */
    private static final String[] YEAR_COLORS = {"#CCFFCC", "#BBBBBB", "#CCCCCC", "#DDDDDD", "#EEEEEE"};
    private static final int ARCHIVE_YEARS = 5;

    private final ProviderDao providerDao;
    private final BillActivityDao billActivityDao;
    private final ProviderDataDao providerDataDao;
    private final ProviderBillCenterDao providerBillCenterDao;
    private final SecurityInfoManager securityInfoManager;
    private final BillingReviewLoader reviewPrep;
    private final BillingOnLookupService lookupService;

    public BillingOnMriViewModelAssembler(ProviderDao providerDao,
                              BillActivityDao billActivityDao,
                              ProviderDataDao providerDataDao,
                              ProviderBillCenterDao providerBillCenterDao,
                              SecurityInfoManager securityInfoManager,
                              BillingReviewLoader reviewPrep,
                              BillingOnLookupService lookupService) {
        this.providerDao = providerDao;
        this.billActivityDao = billActivityDao;
        this.providerDataDao = providerDataDao;
        this.providerBillCenterDao = providerBillCenterDao;
        this.securityInfoManager = securityInfoManager;
        this.reviewPrep = reviewPrep;
        this.lookupService = lookupService;
    }

    /**
     * Build the OHIP-file-generation view model.
     *
     * @param request in-flight request — supplies the {@code year},
     *                {@code xml_vdate}, {@code xml_appointment_date},
     *                {@code useProviderMOH} parameters
     * @param loggedInInfo session principal — drives the multisite /
     *                     team-billing privilege flags that filter the
     *                     provider dropdown and the MRI rows
     * @return populated view model
     */
    public BillingOnMriViewModel assemble(HttpServletRequest request, LoggedInInfo loggedInInfo) {
        String userProviderNo = loggedInInfo == null || loggedInInfo.getLoggedInProviderNo() == null
                ? "" : loggedInInfo.getLoggedInProviderNo();

        boolean isTeamBillingOnly = hasPrivilege(loggedInInfo, "_team_billing_only");
        boolean isSiteAccessPrivacy = hasPrivilege(loggedInInfo, "_site_access_privacy");
        boolean isTeamAccessPrivacy = hasPrivilege(loggedInInfo, "_team_access_privacy");

        GregorianCalendar now = new GregorianCalendar();
        int curYear = now.get(Calendar.YEAR);
        int curMonth = now.get(Calendar.MONTH) + 1;
        String currentTimestamp = UtilDateUtilities.DateToString(new java.util.Date(), "yyyy-MM-dd HH:mm:ss");

        String selectedYear = request.getParameter("year");
        if (selectedYear == null || selectedYear.isEmpty()) {
            selectedYear = String.valueOf(curYear);
        }

        List<String> archiveYears = new ArrayList<>();
        String currentYearColor = "";
        for (int i = 0; i < ARCHIVE_YEARS; i++) {
            String y = String.valueOf(curYear - i);
            archiveYears.add(y);
            if (y.equals(selectedYear)) {
                currentYearColor = YEAR_COLORS[i];
            }
        }

        String monthCode = BillingOnConstants.propMonthCode.getProperty(String.valueOf(curMonth));

        // Provider-set used for multisite filtering of the MRI rows.
        // Mirrors the legacy `providerMap.get(pro_no) == null` skip.
        Set<String> visibleProviderSet = new HashSet<>();
        if (isSiteAccessPrivacy) {
            for (ProviderData pd : providerDataDao.findByProviderSite(userProviderNo)) {
                visibleProviderSet.add(pd.getId());
            }
        } else if (isTeamAccessPrivacy) {
            for (ProviderData pd : providerDataDao.findByProviderTeam(userProviderNo)) {
                visibleProviderSet.add(pd.getId());
            }
        }
        boolean filterByVisibleProviders = isSiteAccessPrivacy || isTeamAccessPrivacy;

        BillingOnMriViewModel.Builder b = BillingOnMriViewModel.builder()
                .selectedYear(selectedYear)
                .archiveYears(archiveYears)
                .currentYearColor(currentYearColor)
                .monthCode(monthCode == null ? "" : monthCode)
                .currentTimestamp(currentTimestamp)
                .userProviderNo(userProviderNo)
                .defaultBillCenter(CarlosProperties.getInstance().getProperty("billcenter", ""))
                .serviceDateStart(nullToEmpty(request.getParameter("xml_vdate")))
                .serviceDateEnd(nullToEmptyDefault(request.getParameter("xml_appointment_date"),
                        UtilDateUtilities.DateToString(new java.util.Date(), "yyyy-MM-dd")))
                .useProviderMOHChecked("true".equals(request.getParameter("useProviderMOH")));

        b.providerOptions(loadProviderOptions(userProviderNo, isTeamBillingOnly, isSiteAccessPrivacy, isTeamAccessPrivacy));
        b.billCenterOptions(loadBillCenterOptions());
        b.providerBillCenterMap(loadProviderBillCenterMap());
        b.mriRows(loadMriRows(selectedYear, currentYearColor, visibleProviderSet, filterByVisibleProviders));
        b.billActivityRows(loadBillActivityRows(selectedYear, currentYearColor));

        return b.build();
    }

    private boolean hasPrivilege(LoggedInInfo loggedInInfo, String objectName) {
        return loggedInInfo != null && securityInfoManager.hasPrivilege(loggedInInfo, objectName, "r", null);
    }

    /**
     * Load the provider dropdown options. Three modes mirror the legacy
     * scriptlet: team-billing-only / site-access-privacy each restrict
     * to the user's team or site; otherwise show all billable providers.
     * The returned list is in {@code BillingReviewLoader}'s pipe-delimited
     * "no|last|first" format — split here into structured records.
     */
    private List<BillingOnMriViewModel.ProviderEntry> loadProviderOptions(String userProviderNo,
                                                                           boolean isTeamBillingOnly,
                                                                           boolean isSiteAccessPrivacy,
                                                                           boolean isTeamAccessPrivacy) {
        List<io.github.carlos_emr.carlos.billings.ca.on.dto.ProviderDropdownEntry> providerStrs;
        if (isTeamBillingOnly || isTeamAccessPrivacy) {
            providerStrs = reviewPrep.getTeamProviderBillingStr(userProviderNo);
        } else if (isSiteAccessPrivacy) {
            providerStrs = reviewPrep.getSiteProviderBillingStr(userProviderNo);
        } else {
            providerStrs = reviewPrep.getProviderBillingStr();
        }
        List<BillingOnMriViewModel.ProviderEntry> options = new ArrayList<>();
        if (providerStrs == null) {
            return options;
        }
        for (io.github.carlos_emr.carlos.billings.ca.on.dto.ProviderDropdownEntry row : providerStrs) {
            options.add(new BillingOnMriViewModel.ProviderEntry(
                    row.providerNo(), row.lastName(), row.firstName()));
        }
        return options;
    }

    private List<BillingOnMriViewModel.BillCenterEntry> loadBillCenterOptions() {
        String defaultBc = CarlosProperties.getInstance().getProperty("billcenter", "");
        List<BillingOnMriViewModel.BillCenterEntry> out = new ArrayList<>();
        Enumeration<?> e = BillingOnConstants.propBillingCenter.propertyNames();
        while (e.hasMoreElements()) {
            String code = (String) e.nextElement();
            String label = BillingOnConstants.propBillingCenter.getProperty(code, code);
            out.add(new BillingOnMriViewModel.BillCenterEntry(code, label, code.equals(defaultBc)));
        }
        return out;
    }

    private Map<String, String> loadProviderBillCenterMap() {
        Map<String, String> map = new HashMap<>();
        for (Provider p : providerDao.getBillableProviders()) {
            String providerNo = p.getProviderNo();
            ProviderBillCenter pbc = providerBillCenterDao.find(providerNo);
            if (pbc != null) {
                // Map.copyOf() in BillingOnMriViewModel.Builder rejects null values —
                // coerce to empty string so a provider with an unset bill-center code
                // does not cause a NullPointerException when the view model is built.
                map.put(providerNo, nullToEmpty(pbc.getBillCenterCode()));
            }
        }
        return map;
    }

    /**
     * Load the current-year MRI disk-records table. Each
     * {@link BillingDiskNameDto} carries parallel vectors per provider —
     * flatten into one row per (disk, provider) pair, applying the
     * site/team multisite filter.
     */
    @SuppressWarnings("rawtypes")
    private List<BillingOnMriViewModel.MriRow> loadMriRows(String selectedYear,
                                                            String currentYearColor,
                                                            Set<String> visibleProviderSet,
                                                            boolean filterByVisibleProviders) {
        List mriList = reviewPrep.getMRIList(selectedYear + "-01-01 00:00:01",
                selectedYear + "-12-31 23:59:59", "U");
        Properties proName = lookupService.getPropProviderName();

        List<BillingOnMriViewModel.MriRow> rows = new ArrayList<>();
        if (mriList == null) {
            return rows;
        }
        int count = 0;
        for (Object obj : mriList) {
            BillingDiskNameDto data = (BillingDiskNameDto) obj;
            String oFile = data.getOhipfilename();
            String updateDate = data.getUpdatedatetime();
            String createDate = data.getCreatedatetime();
            List<DiskFilenameRow> filenameRows = data.getFilenames();

            int providerCount = filenameRows == null ? 0 : filenameRows.size();
            for (int j = 0; j < providerCount; j++) {
                DiskFilenameRow row = filenameRows.get(j);
                count++;
                String proNo = row.providerNo();
                if (filterByVisibleProviders && !visibleProviderSet.contains(proNo)) {
                    continue;
                }
                String cr = row.claimRecord();
                String hFile = row.htmlFilename();
                String total = row.total();
                String name = proName.getProperty(proNo, "");
                String bgColor = (count % 2 == 0) ? currentYearColor : "ivory";
                if (updateDate != null && createDate != null && !updateDate.equals(createDate)) {
                    bgColor = "silver";
                }
                String trimmedDate = updateDate == null ? "" : updateDate.substring(0, Math.min(16, updateDate.length()));
                // BillingDiskNameDto.getId() returns the disk id as a String;
                // legacy JSP injected it raw into a JS onclick. Parse it now
                // so the view model is type-clean.
                int diskId = parseIntOrZero(data.getId());
                rows.add(new BillingOnMriViewModel.MriRow(
                        diskId,
                        nullToEmpty(name),
                        trimmedDate,
                        nullToEmpty(cr),
                        nullToEmpty(total),
                        nullToEmpty(oFile),
                        nullToEmpty(hFile),
                        bgColor));
            }
        }
        return rows;
    }

    /**
     * Load the older BillActivity records table. The legacy code mapped
     * providers by OHIP number (not provider_no), so we build a separate
     * lookup here. Sorting by update-date matches the legacy
     * {@code Collections.sort(bas, BillActivity.UpdateDateTimeComparator)}.
     */
    private List<BillingOnMriViewModel.BillActivityRow> loadBillActivityRows(String selectedYear,
                                                                              String currentYearColor) {
        Properties ohipNameMap = new Properties();
        for (Provider p : providerDao.getActiveProviders()) {
            if (p.getOhipNo() != null && !p.getOhipNo().isEmpty()) {
                ohipNameMap.setProperty(p.getOhipNo(), p.getLastName() + ", " + p.getFirstName());
            }
        }

        java.util.Date startDate = ConversionUtils.fromTimestampString(selectedYear + "-01-01 00:00:00");
        java.util.Date endDate = ConversionUtils.fromTimestampString(selectedYear + "-12-31 23:59:59");
        List<BillActivity> bas = billActivityDao.findCurrentByDateRange(startDate, endDate);
        if (bas == null) {
            return Collections.emptyList();
        }
        // Use a null-safe comparator — updateDateTime can be null for in-progress claims.
        bas.sort(Comparator.comparing(BillActivity::getUpdateDateTime, Comparator.nullsLast(Comparator.reverseOrder())));

        List<BillingOnMriViewModel.BillActivityRow> rows = new ArrayList<>();
        int count = 0;
        for (BillActivity ba : bas) {
            count++;
            String name = ohipNameMap.getProperty(nullToEmpty(ba.getProviderOhipNo()), "");
            String updateDate = ConversionUtils.toDateString(ba.getUpdateDateTime());
            String total = ba.getTotal() == null ? "0.00" : ba.getTotal();
            // Legacy formatted total cuts everything past the second decimal.
            // Mirrors total.substring(0, total.indexOf("."))+total.substring(total.indexOf("."), total.indexOf(".")+3).
            String formattedTotal = formatTotal(total);
            String bgColor = (count % 2 == 0) ? currentYearColor : "white";
            rows.add(new BillingOnMriViewModel.BillActivityRow(
                    name,
                    updateDate,
                    nullToEmpty(ba.getClaimRecord()),
                    formattedTotal,
                    nullToEmpty(ba.getOhipFilename()),
                    nullToEmpty(ba.getHtmlFilename()),
                    bgColor));
        }
        return rows;
    }

    private static String formatTotal(String total) {
        if (total == null) return "0.00";
        int dot = total.indexOf(".");
        if (dot < 0 || total.length() < dot + 3) {
            return total;
        }
        return total.substring(0, dot) + total.substring(dot, dot + 3);
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    private static String nullToEmptyDefault(String s, String fallback) {
        return s == null || s.isEmpty() ? fallback : s;
    }

    private static int parseIntOrZero(String s) {
        if (s == null) return 0;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            MiscUtils.getLogger().warn("BillingOnMri: invalid integer [{}]; using 0",
                    LogSanitizer.sanitize(s), e);
            return 0;
        }
    }
}
