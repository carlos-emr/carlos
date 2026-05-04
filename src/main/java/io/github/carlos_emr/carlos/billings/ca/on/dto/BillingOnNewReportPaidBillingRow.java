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
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.dto;

/** Billing total row used to seed the ON new-report paid RA lookup. */
public record BillingOnNewReportPaidBillingRow(String billingNo, String total) {
    public BillingOnNewReportPaidBillingRow {
        billingNo = empty(billingNo);
        total = empty(total);
    }

    private static String empty(String value) {
        return value == null ? "" : value;
    }
}
