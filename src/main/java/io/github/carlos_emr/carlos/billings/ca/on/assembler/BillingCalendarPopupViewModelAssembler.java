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
import java.util.GregorianCalendar;
import java.util.List;

import io.github.carlos_emr.DateInMonthTable;
import io.github.carlos_emr.carlos.billings.ca.on.viewmodel.BillingCalendarPopupViewModel;

/**
 * Assembles {@link BillingCalendarPopupViewModel} for
 * {@code billing/CA/ON/billingCalendarPopup.jsp}. Hoists the inline date-math
 * scriptlet logic the JSP body used to perform: {@code year}/{@code month}/{@code delta}
 * parsing, {@link GregorianCalendar} delta application, and the
 * {@link DateInMonthTable#getMonthDateGrid} lookup that produces the
 * row-of-weeks grid the JSP renders.
 *
 * @since 2026-04-25
 */
@org.springframework.stereotype.Service
public class BillingCalendarPopupViewModelAssembler {

    /**
     * Build the view model from raw request parameters.
     *
     * @param yearParam {@code year} request parameter (legacy: 0 when missing)
     * @param monthParam {@code month} request parameter (legacy: 0 when missing)
     * @param deltaParam {@code delta} request parameter (legacy: 0 when missing — month delta to apply)
     * @param typeParam {@code type} request parameter ({@code "admission"} → typeInDate JS callback)
     */
    public BillingCalendarPopupViewModel assemble(String yearParam, String monthParam,
                                                   String deltaParam, String typeParam) {
        int year = parseIntOrZero(yearParam, "year");
        int month = parseIntOrZero(monthParam, "month");
        int delta = parseIntOrZero(deltaParam, "delta");
        String type = typeParam == null ? "" : typeParam;

        // Mirror the legacy scriptlet: build a calendar at year/month-1/1, apply
        // the month delta, then read the resolved year/month back. Fall back to
        // "today" when year or month is missing/unparseable — the legacy code
        // produced a year-1-BC December date for the (0, 0) input shape, which
        // is never what the calendar popup wants.
        GregorianCalendar cal;
        if (year <= 0 || month <= 0 || month > 12) {
            cal = new GregorianCalendar();
            cal.set(Calendar.DAY_OF_MONTH, 1);
        } else {
            cal = new GregorianCalendar(year, month - 1, 1);
        }
        cal.add(Calendar.MONTH, delta);
        int resolvedYear = cal.get(Calendar.YEAR);
        int resolvedMonth = cal.get(Calendar.MONTH) + 1;

        DateInMonthTable aDate = new DateInMonthTable(resolvedYear, resolvedMonth - 1, 1);
        int[][] dateGrid = aDate.getMonthDateGrid();

        List<BillingCalendarPopupViewModel.WeekRow> weeks = new ArrayList<>(dateGrid.length);
        for (int[] row : dateGrid) {
            List<BillingCalendarPopupViewModel.DayCell> cells = new ArrayList<>(7);
            for (int j = 0; j < 7; j++) {
                cells.add(new BillingCalendarPopupViewModel.DayCell(row[j]));
            }
            weeks.add(new BillingCalendarPopupViewModel.WeekRow(cells));
        }

        return BillingCalendarPopupViewModel.builder()
                .year(resolvedYear)
                .month(resolvedMonth)
                .type(type)
                .weeks(weeks)
                .build();
    }

    private static int parseIntOrZero(String s, String parameterName) {
        if (s == null || s.isEmpty()) return 0;
        try {
            return Integer.parseInt(s);
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException("Invalid " + parameterName + " parameter", nfe);
        }
    }
}
