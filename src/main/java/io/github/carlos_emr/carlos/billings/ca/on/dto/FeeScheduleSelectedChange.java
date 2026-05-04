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
package io.github.carlos_emr.carlos.billings.ca.on.dto;

import io.github.carlos_emr.carlos.billings.ca.on.BillingMoney;

import java.math.BigDecimal;

/**
 * Subset of previewed fee-schedule rows the operator chose to apply.
 *
 * <p>The review page posts selected rows back as delimited text, so this
 * record owns both the normalized typed form and the parser for that posted
 * representation.</p>
 */
public record FeeScheduleSelectedChange(
        String feeCode,
        BigDecimal newPrice,
        String effectiveDate,
        String terminationDate,
        String description) {

    public FeeScheduleSelectedChange {
        // Reject null upfront with a clear message rather than NPEing inside
        // toPlainString() — every caller path supplies a parsed BigDecimal,
        // but a future caller passing null would otherwise crash with no
        // useful diagnostic.
        java.util.Objects.requireNonNull(newPrice, "newPrice must not be null");
        newPrice = BillingMoney.amount(newPrice.toPlainString());
    }

    /** Parse one selected preview row posted back from the legacy review form. */
    public static FeeScheduleSelectedChange fromSubmittedValue(String submittedValue) {
        String[] fields = submittedValue == null ? new String[0] : submittedValue.split("\\|", 5);
        if (fields.length != 5) {
            throw new IllegalArgumentException("Expected selected fee schedule change with 5 fields");
        }
        return new FeeScheduleSelectedChange(
                fields[0],
                BillingMoney.amount(fields[1]),
                fields[2],
                fields[3],
                fields[4]);
    }
}
