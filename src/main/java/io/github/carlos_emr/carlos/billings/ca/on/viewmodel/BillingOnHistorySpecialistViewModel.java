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

import java.util.Collections;
import java.util.List;

/**
 * Immutable view model for {@code billingONHistorySpec.jsp}, the
 * service-code-filtered billing-history popup.
 *
 * <p>Populated by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingOnHistorySpecialistViewModelAssembler#assemble}
 * (invoked from
 * {@link io.github.carlos_emr.carlos.billings.ca.on.web.ViewBillingOnHistorySpecialist2Action})
 * and exposed to the JSP as request attribute {@code historySpecModel}.</p>
 *
 * <p>Captures the request parameter echoes (demographic_no, demo_name,
 * day, serviceCode), the resolved date-range strings rendered in the
 * search header, and the pre-filtered list of bill rows.</p>
 *
 * @since 2026-04-25
 */
public final class BillingOnHistorySpecialistViewModel {

    /** A single billing-history row already filtered by service code. */
    public record HistoryRow(
            String invoiceNo,
            String billingDate,
            String billType,
            String serviceCode,
            String dx,
            String total) { }

    private final String demographicNo;
    private final String demoName;
    private final String day;
    private final String serviceCodeFilter;
    private final String todayStr;
    private final String startDayStr;
    private final List<HistoryRow> rows;
    private final int itemCount;
    private final boolean partial;

    private BillingOnHistorySpecialistViewModel(Builder b) {
        this.demographicNo = nullToEmpty(b.demographicNo);
        this.demoName = nullToEmpty(b.demoName);
        this.day = nullToEmpty(b.day);
        this.serviceCodeFilter = nullToEmpty(b.serviceCodeFilter);
        this.todayStr = nullToEmpty(b.todayStr);
        this.startDayStr = nullToEmpty(b.startDayStr);
        this.rows = b.rows == null ? Collections.emptyList() : List.copyOf(b.rows);
        this.itemCount = b.itemCount;
        this.partial = b.partial;
    }

    private static String nullToEmpty(String s) { return s == null ? "" : s; }

    public static Builder builder() { return new Builder(); }

    public String getDemographicNo() { return demographicNo; }
    public String getDemoName() { return demoName; }
    public String getDay() { return day; }
    public String getServiceCodeFilter() { return serviceCodeFilter; }
    public String getTodayStr() { return todayStr; }
    public String getStartDayStr() { return startDayStr; }
    public List<HistoryRow> getRows() { return rows; }
    public int getItemCount() { return itemCount; }
    /**
     * @return {@code true} when the loader caught an exception mid-iteration
     *         and returned a partial list. The JSP renders a "data may be
     *         incomplete" banner so the operator doesn't conclude the patient
     *         has fewer historical bills than were issued.
     */
    public boolean isPartial() { return partial; }

    public static final class Builder {
        private String demographicNo;
        private String demoName;
        private String day;
        private String serviceCodeFilter;
        private String todayStr;
        private String startDayStr;
        private List<HistoryRow> rows;
        private int itemCount;
        private boolean partial;

        public Builder demographicNo(String v) { this.demographicNo = v; return this; }
        public Builder demoName(String v) { this.demoName = v; return this; }
        public Builder day(String v) { this.day = v; return this; }
        public Builder serviceCodeFilter(String v) { this.serviceCodeFilter = v; return this; }
        public Builder todayStr(String v) { this.todayStr = v; return this; }
        public Builder startDayStr(String v) { this.startDayStr = v; return this; }
        public Builder rows(List<HistoryRow> v) { this.rows = v; return this; }
        public Builder itemCount(int v) { this.itemCount = v; return this; }
        public Builder partial(boolean v) { this.partial = v; return this; }

        public BillingOnHistorySpecialistViewModel build() { return new BillingOnHistorySpecialistViewModel(this); }
    }
}
