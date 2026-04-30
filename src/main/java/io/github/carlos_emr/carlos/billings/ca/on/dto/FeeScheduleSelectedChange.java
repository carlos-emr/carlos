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
package io.github.carlos_emr.carlos.billings.ca.on.dto;

import io.github.carlos_emr.carlos.billings.ca.on.BillingMoney;

import java.math.BigDecimal;

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
