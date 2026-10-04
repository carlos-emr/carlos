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
package io.github.carlos_emr.carlos.commn.dao;

import io.github.carlos_emr.carlos.util.DateUtils;
import java.util.Calendar;
import java.util.Date;

/**
 * Shared calendar boundaries for selecting payment-report invoices and their payments.
 * @since 2026-10-04
 */
final class BillingPaymentDateRange {
    private BillingPaymentDateRange() { }

    /** Returns next local midnight for an inclusive End Date without changing the input. */
    static Date endExclusive(Date endDate) {
        if (endDate == null) return null;
        Calendar end = DateUtils.setToBeginningOfDay(DateUtils.toCalendar(endDate));
        // Calendar arithmetic also handles 23/25-hour daylight-saving days.
        end.add(Calendar.DATE, 1);
        return end.getTime();
    }
}
