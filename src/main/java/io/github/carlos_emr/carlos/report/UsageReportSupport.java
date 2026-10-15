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
package io.github.carlos_emr.carlos.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Calendar;
import java.util.Date;

/**
 * Date boundaries and display formatting for the provider usage report.
 *
 * @since 2026-10-02
 */
public final class UsageReportSupport {
    private UsageReportSupport() { }

    /**
     * Returns the midnight after the selected end date in the application's time zone.
     * Calendar arithmetic preserves local dates across daylight-saving transitions.
     * @param endDate selected report end date
     * @return exclusive upper boundary, or null for a null input
     */
    public static Date exclusiveEnd(Date endDate) {
        if (endDate == null) {
            return null;
        }
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(endDate);
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
        calendar.add(Calendar.DATE, 1);
        return calendar.getTime();
    }

    /**
     * Adapts a selected end date for existing DAO queries with an inclusive upper boundary.
     * @param endDate selected report end date
     * @return last millisecond of that calendar date, or null for a null input
     */
    public static Date inclusiveEnd(Date endDate) {
        Date nextDay = exclusiveEnd(endDate);
        return nextDay == null ? null : new Date(nextDay.getTime() - 1);
    }

    /**
     * Formats a percentage with at most two decimal places and no floating-point noise.
     * @param total denominator
     * @param count numerator
     * @return percentage without a percent sign, or --- when the denominator is zero
     */
    public static String percentage(int total, int count) {
        if (total == 0) {
            return "---";
        }
        return BigDecimal.valueOf(count).multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 2, RoundingMode.HALF_UP)
                .stripTrailingZeros().toPlainString();
    }
}
