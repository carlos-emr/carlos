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
 * Immutable view model for {@code billing/CA/ON/billingCalendarPopup.jsp},
 * the generic month-grid date-picker popup. Built by
 * {@link io.github.carlos_emr.carlos.billings.ca.on.assembler.BillingCalendarPopupViewModelAssembler}
 * after the action validates the {@code year}/{@code month}/{@code delta}/{@code type}
 * request parameters.
 *
 * <p>Captures the resolved year/month (after applying delta), the type echo
 * (drives the typeInDate vs typeSrvDate JS callback), and the per-week list
 * of cells. Each week row is exactly 7 cells; an empty cell carries day=0
 * to mirror the legacy {@code int[][] dateGrid} layout from
 * {@code DateInMonthTable}.</p>
 *
 * @since 2026-04-25
 */
public final class BillingCalendarPopupViewModel {

    /** A single cell in a calendar week row. {@code day == 0} denotes an empty cell. */
    public record DayCell(int day) {
        public boolean isEmpty() { return day == 0; }
    }

    /** A week row — always 7 cells. */
    public record WeekRow(List<DayCell> cells) {
        public WeekRow {
            cells = cells == null ? Collections.emptyList() : List.copyOf(cells);
        }
    }

    private final int year;
    private final int month;
    private final String type;
    private final List<WeekRow> weeks;

    private BillingCalendarPopupViewModel(Builder b) {
        this.year = b.year;
        this.month = b.month;
        this.type = b.type == null ? "" : b.type;
        this.weeks = b.weeks == null ? Collections.emptyList() : List.copyOf(b.weeks);
    }

    public int getYear() { return year; }
    public int getMonth() { return month; }
    public String getType() { return type; }
    public List<WeekRow> getWeeks() { return weeks; }
    public int getYearMinusOne() { return year - 1; }
    public int getYearPlusOne() { return year + 1; }
    /** True when the picker should call {@code typeInDate} (admission) on click. */
    public boolean isAdmission() { return "admission".equals(type); }

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private int year;
        private int month;
        private String type;
        private List<WeekRow> weeks;

        public Builder year(int v) { this.year = v; return this; }
        public Builder month(int v) { this.month = v; return this; }
        public Builder type(String v) { this.type = v; return this; }
        public Builder weeks(List<WeekRow> v) { this.weeks = v; return this; }

        public BillingCalendarPopupViewModel build() {
            return new BillingCalendarPopupViewModel(this);
        }
    }
}
