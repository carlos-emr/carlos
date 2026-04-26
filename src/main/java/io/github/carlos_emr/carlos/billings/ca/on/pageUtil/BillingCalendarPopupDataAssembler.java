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
package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;

import io.github.carlos_emr.DateInMonthTable;
import io.github.carlos_emr.carlos.billings.ca.on.data.BillingCalendarPopupViewModel;

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
public final class BillingCalendarPopupDataAssembler {

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
        int year = parseIntOrZero(yearParam);
        int month = parseIntOrZero(monthParam);
        int delta = parseIntOrZero(deltaParam);
        String type = typeParam == null ? "" : typeParam;

        // Mirror the legacy scriptlet: build a calendar at year/month-1/1, apply
        // the month delta, then read the resolved year/month back.
        GregorianCalendar cal = new GregorianCalendar(year, month - 1, 1);
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

    private static int parseIntOrZero(String s) {
        if (s == null || s.isEmpty()) return 0;
        try { return Integer.parseInt(s); } catch (NumberFormatException nfe) { return 0; }
    }
}
