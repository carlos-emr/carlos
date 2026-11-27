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
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.Set;
import jakarta.servlet.http.HttpServletRequest;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.billing.CA.dao.BillActivityDao;
import io.github.carlos_emr.carlos.billing.CA.model.BillActivity;
import io.github.carlos_emr.carlos.billings.ca.on.support.BillingOnConstants;
import io.github.carlos_emr.carlos.billings.ca.on.dto.BillingDiskNameDto;
import io.github.carlos_emr.carlos.billings.ca.on.dto.DiskFilenameRow;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingDataLoadException;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingOnMriViewModel;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnLookupService;
import io.github.carlos_emr.carlos.commn.dao.ProviderBillCenterDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderDataDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.ProviderBillCenter;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnFileAccessService;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingOnProviderScope;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.util.ConversionUtils;
import io.github.carlos_emr.carlos.util.UtilDateUtilities;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.LogSafe;
import io.github.carlos_emr.carlos.utility.MiscUtils;
import io.github.carlos_emr.carlos.billings.ca.on.service.BillingReviewLoader;
import org.apache.logging.log4j.Logger;

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

    private static final Logger LOGGER = MiscUtils.getLogger();

    /** Color cycle the legacy JSP used to highlight the selected year row. */
    private static final String[] YEAR_COLORS = {"#CCFFCC", "#BBBBBB", "#CCCCCC", "#DDDDDD", "#EEEEEE"};
    private static final int ARCHIVE_YEARS = 5;

    private final ProviderDao providerDao;
    private final BillActivityDao billActivityDao;
    private final ProviderDataDao providerDataDao;
    private final ProviderBillCenterDao providerBillCenterDao;
    private final SecurityInfoManager securityInfoManager;
    private final BillingReviewLoader reviewLoader;
    private final BillingOnLookupService lookupService;
    private final BillingOnFileAccessService fileAccess;

    public BillingOnMriViewModelAssembler(ProviderDao providerDao,
                              BillActivityDao billActivityDao,
                              ProviderDataDao providerDataDao,
                              ProviderBillCenterDao providerBillCenterDao,
                              SecurityInfoManager securityInfoManager,
                              BillingReviewLoader reviewLoader,
                              BillingOnLookupService lookupService, BillingOnFileAccessService fileAccess) {
        this.providerDao = providerDao;
        this.billActivityDao = billActivityDao;
        this.providerDataDao = providerDataDao;
        this.providerBillCenterDao = providerBillCenterDao;
        this.securityInfoManager = securityInfoManager;
        this.reviewLoader = reviewLoader;
        this.lookupService = lookupService;
        this.fileAccess = fileAccess;
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

        BillingOnProviderScope scope = BillingOnProviderScope.forLogin(securityInfoManager, loggedInInfo);

        GregorianCalendar now = new GregorianCalendar();
        int curYear = now.get(Calendar.YEAR);
        int curMonth = now.get(Calendar.MONTH) + 1;
        String currentTimestamp = UtilDateUtilities.DateToString(new java.util.Date(), "yyyy-MM-dd HH:mm:ss");

        String selectedYear = request.getParameter("year");
        if (selectedYear == null || selectedYear.isEmpty()) {
            selectedYear = String.valueOf(curYear);
        }
        if (!selectedYear.matches("\\d{4}")) {
            throw new BillingDataLoadException(
                    "Invalid OHIP archive year",
                    BillingDataLoadException.Phase.DATE_PARSE,
                    Map.of("year", LogSafe.sanitize(selectedYear)));
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

        Optional<Set<String>> visibleProviders = scope.historyProviderNumbers(providerDataDao, userProviderNo);
        List<BillingOnMriViewModel.ProviderEntry> options = scope.selectableProviders(reviewLoader, userProviderNo).stream()
                .map(row -> new BillingOnMriViewModel.ProviderEntry(row.providerNo(), row.lastName(), row.firstName()))
                .toList();

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
                .useProviderMOHChecked("true".equals(request.getParameter("useProviderMOH"))
                        || "on".equals(request.getParameter("useProviderMOH")));

        b.providerOptions(options);
        b.billCenterOptions(loadBillCenterOptions());
        b.providerBillCenterMap(loadProviderBillCenterMap(scope.restricted()
                ? Optional.of(options.stream().map(BillingOnMriViewModel.ProviderEntry::providerNo).collect(Collectors.toSet()))
                : Optional.empty()));
        if (visibleProviders.isEmpty() || !visibleProviders.get().isEmpty()) {
            b.mriRows(loadMriRows(selectedYear, currentYearColor, visibleProviders));
            b.billActivityRows(loadBillActivityRows(selectedYear, currentYearColor, visibleProviders));
        }

        return b.build();
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

    private Map<String, String> loadProviderBillCenterMap(Optional<Set<String>> selectable) {
        Map<String, String> map = new HashMap<>();
        for (Provider p : providerDao.getBillableProviders()) {
            String providerNo = p.getProviderNo();
            if (selectable.isPresent() && !selectable.get().contains(providerNo)) continue;
            ProviderBillCenter pbc = providerBillCenterDao.find(providerNo);
            if (pbc != null) {
                String billCenterCode = pbc.getBillCenterCode();
                if (billCenterCode == null || billCenterCode.isBlank()) {
                    LOGGER.warn("Billable provider is missing bill-center code; excluding providerNo={} from MRI bill-center map",
                            LogSafe.sanitize(providerNo));
                    continue;
                }
                map.put(providerNo, billCenterCode);
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
    private List<BillingOnMriViewModel.MriRow> loadMriRows(String selectedYear,
                                                            String currentYearColor,
                                                            Optional<Set<String>> visibleProviders) {
        List<BillingDiskNameDto> mriList = reviewLoader.getMRIList(selectedYear + "-01-01 00:00:01",
                selectedYear + "-12-31 23:59:59", "U");
        Properties proName = lookupService.getPropProviderName();

        List<BillingOnMriViewModel.MriRow> rows = new ArrayList<>();
        if (mriList == null) {
            return rows;
        }
        Optional<Map<String, Boolean>> permittedFiles = visibleProviders.map(providers -> {
            Set<String> filenames = new HashSet<>();
            for (BillingDiskNameDto disk : mriList) {
                if (disk.getFilenames() == null) continue;
                for (DiskFilenameRow row : disk.getFilenames()) {
                    if (providers.contains(row.providerNo())) {
                        filenames.add(disk.getOhipfilename());
                        filenames.add(row.htmlFilename());
                    }
                }
            }
            return filenames.isEmpty() ? Map.of() : fileAccess.filePermissions(filenames, providers);
        });
        int count = 0;
        for (BillingDiskNameDto data : mriList) {
            String oFile = data.getOhipfilename();
            String updateDate = data.getUpdatedatetime();
            String createDate = data.getCreatedatetime();
            List<DiskFilenameRow> filenameRows = data.getFilenames();

            int providerCount = filenameRows == null ? 0 : filenameRows.size();
            boolean mayRegenerate = permittedFiles.isEmpty() || (!permittedFilename(oFile, permittedFiles).isEmpty()
                    && providerCount > 0 && filenameRows.stream().allMatch(row -> !permittedFilename(row.htmlFilename(), permittedFiles).isEmpty()));
            for (int j = 0; j < providerCount; j++) {
                DiskFilenameRow row = filenameRows.get(j);
                String proNo = row.providerNo();
                if (visibleProviders.isPresent() && !visibleProviders.get().contains(proNo)) {
                    continue;
                }
                count++;
                String cr = row.claimRecord();
                String hFile = row.htmlFilename();
                String total = row.total();
                String name = proName.getProperty(proNo, "");
                String bgColor = (count % 2 == 0) ? currentYearColor : "ivory";
                if (updateDate != null && createDate != null && !updateDate.equals(createDate)) {
                    bgColor = "silver";
                }
                String trimmedDate = updateDate == null ? "" : updateDate.substring(0, Math.min(16, updateDate.length()));
                // BillingDiskNameDto.getId() returns the disk id as a String.
                // Invalid ids used to become diskId=0, which produced rows that
                // looked clickable but could not resolve to a real disk record.
                Integer diskId = parseDiskId(data.getId());
                if (diskId == null) {
                    LOGGER.warn("BillingOnMri: dropping MRI row with invalid disk id [{}]",
                            LogSafe.sanitize(data.getId()));
                    continue;
                }
                rows.add(new BillingOnMriViewModel.MriRow(
                        diskId,
                        nullToEmpty(name),
                        trimmedDate,
                        nullToEmpty(cr),
                        nullToEmpty(total),
                        permittedFilename(oFile, permittedFiles),
                        permittedFilename(hFile, permittedFiles),
                        bgColor, mayRegenerate));
            }
        }
        return rows;
    }

    /**
     * Load the older BillActivity records table. The legacy code mapped
     * providers by OHIP number (not provider_no), so we build a separate
     * lookup here. Sorting keeps the legacy newest-first ordering while making
     * null update timestamps sort last instead of failing the whole page.
     */
    private List<BillingOnMriViewModel.BillActivityRow> loadBillActivityRows(String selectedYear,
                                                                              String currentYearColor, Optional<Set<String>> visibleProviders) {
        Properties ohipNameMap = new Properties();
        for (Provider p : providerDao.getActiveProviders()) {
            if (p.getOhipNo() != null && !p.getOhipNo().isEmpty()) {
                ohipNameMap.setProperty(p.getOhipNo(), p.getLastName() + ", " + p.getFirstName());
            }
        }

        java.util.Date startDate = ConversionUtils.fromTimestampString(selectedYear + "-01-01 00:00:00");
        java.util.Date endDate = ConversionUtils.fromTimestampString(selectedYear + "-12-31 23:59:59");
        List<BillActivity> bas = billActivityDao.findCurrentByDateRange(startDate, endDate);
        if (bas == null || bas.isEmpty()) {
            return Collections.emptyList();
        }
        bas = new ArrayList<>(bas);
        // Use a null-safe comparator — updateDateTime can be null for in-progress claims.
        bas.sort(Comparator.comparing(BillActivity::getUpdateDateTime, Comparator.nullsLast(Comparator.reverseOrder())));

        Map<String, String> legacyOwners = visibleProviders.isPresent()
                ? fileAccess.uniqueLegacyOwners(bas.stream().map(BillActivity::getProviderOhipNo).toList()) : Map.of();
        if (visibleProviders.isPresent()) {
            bas.removeIf(activity -> {
                String ohip = activity.getProviderOhipNo();
                String owner = ohip == null ? null : legacyOwners.get(ohip);
                return owner == null || owner.isBlank() || !visibleProviders.get().contains(owner);
            });
        }
        if (bas.isEmpty()) return List.of();
        Set<String> filenames = new HashSet<>();
        if (visibleProviders.isPresent()) {
            for (BillActivity activity : bas) {
                filenames.add(activity.getHtmlFilename());
                filenames.add(activity.getOhipFilename());
            }
        }
        Optional<Map<String, Boolean>> permittedFiles = visibleProviders.map(providers -> fileAccess.filePermissions(filenames, providers));
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
                    permittedFilename(ba.getOhipFilename(), permittedFiles),
                    permittedFilename(ba.getHtmlFilename(), permittedFiles),
                    bgColor));
        }
        return rows;
    }

    private static String permittedFilename(String filename, Optional<Map<String, Boolean>> permissions) {
        if (filename == null || filename.isBlank()) return "";
        return permissions.isEmpty() || Boolean.TRUE.equals(permissions.get().get(filename)) ? filename : "";
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

    private static Integer parseDiskId(String s) {
        if (s == null) return null;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
