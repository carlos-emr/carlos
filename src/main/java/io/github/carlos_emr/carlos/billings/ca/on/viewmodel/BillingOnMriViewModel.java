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
package io.github.carlos_emr.carlos.billings.ca.on.viewmodel;

import io.github.carlos_emr.carlos.billings.ca.on.support.BillingViewStrings;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Immutable view model for {@code billingONMRI.jsp}, the OHIP claim-file
 * generation page (entry point for "Generate OHIP File" — Medical Records
 * Interchange / OHIP claim diskette, NOT magnetic resonance imaging).
 *
 * <p>Captures the page form state (selected year, archive year list, current
 * provider, default bill center, service date range), the provider dropdown
 * options, bill-center dropdown options, the per-provider bill-center JS
 * map, and two report tables: current-year MRI disk records (one row per
 * provider per disk) plus older BillActivity records.</p>
 *
 * <p>Populated by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnMriViewModelAssembler#assemble}
 * (invoked from
 * {@link io.github.carlos_emr.carlos.billings.ca.on.web.ViewBillingOnMri2Action})
 * and exposed to the JSP as request attribute {@code mriModel}.</p>
 *
 * <p>Eliminates the 4 inline {@code SpringUtils.getBean} lookups the JSP
 * used to perform across its scriptlet body (ProviderDao, BillActivityDao,
 * ProviderDataDao, ProviderBillCenterDao).</p>
 *
 * @since 2026-04-26
 */
public final class BillingOnMriViewModel {

    private final String selectedYear;
    private final List<String> archiveYears;
    private final String currentYearColor;
    private final String monthCode;
    private final String currentTimestamp;
    private final String userProviderNo;
    private final String defaultBillCenter;
    private final String serviceDateStart;
    private final String serviceDateEnd;
    private final boolean useProviderMOHChecked;

    private final List<ProviderEntry> providerOptions;
    private final List<BillCenterEntry> billCenterOptions;
    private final Map<String, String> providerBillCenterMap;

    private final List<MriRow> mriRows;
    private final List<BillActivityRow> billActivityRows;

    /** One option in the provider dropdown ({@code value=no, label=last,first}). */
    public record ProviderEntry(String providerNo, String lastName, String firstName) {}

    /** One option in the billing-center dropdown. */
    public record BillCenterEntry(String code, String label, boolean selected) {}

    /**
     * One row in the current-year MRI disk-records table. Carries the
     * disk record's provider, dates, claim/record counts, total, file
     * download links, and the row's alternating background color.
     */
    public record MriRow(int diskId,
                         String providerName,
                         String updateDate,
                         String claimRecord,
                         String total,
                         String ohipFile,
                         String htmlFile,
                         String rowBgColor) {}

    /** One row in the older BillActivity records table. */
    public record BillActivityRow(String providerName,
                                  String updateDate,
                                  String claimRecord,
                                  String formattedTotal,
                                  String ohipFile,
                                  String htmlFile,
                                  String rowBgColor) {}

    private BillingOnMriViewModel(Builder b) {
        this.selectedYear = BillingViewStrings.nullToEmpty(b.selectedYear);
        this.archiveYears = b.archiveYears == null
                ? Collections.emptyList() : List.copyOf(b.archiveYears);
        this.currentYearColor = BillingViewStrings.nullToEmpty(b.currentYearColor);
        this.monthCode = BillingViewStrings.nullToEmpty(b.monthCode);
        this.currentTimestamp = BillingViewStrings.nullToEmpty(b.currentTimestamp);
        this.userProviderNo = BillingViewStrings.nullToEmpty(b.userProviderNo);
        this.defaultBillCenter = BillingViewStrings.nullToEmpty(b.defaultBillCenter);
        this.serviceDateStart = BillingViewStrings.nullToEmpty(b.serviceDateStart);
        this.serviceDateEnd = BillingViewStrings.nullToEmpty(b.serviceDateEnd);
        this.useProviderMOHChecked = b.useProviderMOHChecked;
        this.providerOptions = b.providerOptions == null
                ? Collections.emptyList() : List.copyOf(b.providerOptions);
        this.billCenterOptions = b.billCenterOptions == null
                ? Collections.emptyList() : List.copyOf(b.billCenterOptions);
        this.providerBillCenterMap = b.providerBillCenterMap == null
                ? Collections.emptyMap() : Map.copyOf(b.providerBillCenterMap);
        this.mriRows = b.mriRows == null
                ? Collections.emptyList() : List.copyOf(b.mriRows);
        this.billActivityRows = b.billActivityRows == null
                ? Collections.emptyList() : List.copyOf(b.billActivityRows);
    }

    public static Builder builder() { return new Builder(); }

    public String getSelectedYear() { return selectedYear; }
    public List<String> getArchiveYears() { return archiveYears; }
    public String getCurrentYearColor() { return currentYearColor; }
    public String getMonthCode() { return monthCode; }
    public String getCurrentTimestamp() { return currentTimestamp; }
    public String getUserProviderNo() { return userProviderNo; }
    public String getDefaultBillCenter() { return defaultBillCenter; }
    public String getServiceDateStart() { return serviceDateStart; }
    public String getServiceDateEnd() { return serviceDateEnd; }
    public boolean isUseProviderMOHChecked() { return useProviderMOHChecked; }
    public List<ProviderEntry> getProviderOptions() { return providerOptions; }
    public List<BillCenterEntry> getBillCenterOptions() { return billCenterOptions; }
    public Map<String, String> getProviderBillCenterMap() { return providerBillCenterMap; }
    public List<MriRow> getMriRows() { return mriRows; }
    public List<BillActivityRow> getBillActivityRows() { return billActivityRows; }

    public static final class Builder {
        private String selectedYear;
        private List<String> archiveYears;
        private String currentYearColor;
        private String monthCode;
        private String currentTimestamp;
        private String userProviderNo;
        private String defaultBillCenter;
        private String serviceDateStart;
        private String serviceDateEnd;
        private boolean useProviderMOHChecked;
        private List<ProviderEntry> providerOptions;
        private List<BillCenterEntry> billCenterOptions;
        private Map<String, String> providerBillCenterMap;
        private List<MriRow> mriRows;
        private List<BillActivityRow> billActivityRows;

        public Builder selectedYear(String v) { this.selectedYear = v; return this; }
        public Builder archiveYears(List<String> v) { this.archiveYears = v == null ? null : List.copyOf(v); return this; }
        public Builder currentYearColor(String v) { this.currentYearColor = v; return this; }
        public Builder monthCode(String v) { this.monthCode = v; return this; }
        public Builder currentTimestamp(String v) { this.currentTimestamp = v; return this; }
        public Builder userProviderNo(String v) { this.userProviderNo = v; return this; }
        public Builder defaultBillCenter(String v) { this.defaultBillCenter = v; return this; }
        public Builder serviceDateStart(String v) { this.serviceDateStart = v; return this; }
        public Builder serviceDateEnd(String v) { this.serviceDateEnd = v; return this; }
        public Builder useProviderMOHChecked(boolean v) { this.useProviderMOHChecked = v; return this; }
        // Defensive copies at the setter — mirrors BillingOnFormViewModel pattern.
        public Builder providerOptions(List<ProviderEntry> v) { this.providerOptions = v == null ? null : List.copyOf(v); return this; }
        public Builder billCenterOptions(List<BillCenterEntry> v) { this.billCenterOptions = v == null ? null : List.copyOf(v); return this; }
        public Builder providerBillCenterMap(Map<String, String> v) { this.providerBillCenterMap = v == null ? null : Map.copyOf(v); return this; }
        public Builder mriRows(List<MriRow> v) { this.mriRows = v == null ? null : List.copyOf(v); return this; }
        public Builder billActivityRows(List<BillActivityRow> v) { this.billActivityRows = v == null ? null : List.copyOf(v); return this; }

        public BillingOnMriViewModel build() { return new BillingOnMriViewModel(this); }
    }
}
