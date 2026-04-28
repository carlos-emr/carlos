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
package io.github.carlos_emr.carlos.billings.ca.on.service;

import io.github.carlos_emr.carlos.billings.ca.on.BillingMoney;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

public record FeeScheduleChange(
        String feeCode,
        BigDecimal oldPrice,
        BigDecimal newPrice,
        BigDecimal diff,
        String prices,
        String effectiveDate,
        String terminationDate,
        String description,
        int numCodes,
        boolean newCode) {

    public FeeScheduleChange {
        newPrice = BillingMoney.amount(newPrice.toPlainString());
        if (oldPrice != null) {
            oldPrice = BillingMoney.amount(oldPrice.toPlainString());
        }
        if (diff != null) {
            diff = BillingMoney.amount(diff.toPlainString());
        }
    }

    public Map<String, Object> toWarningMap() {
        Map<String, Object> warning = new LinkedHashMap<>();
        warning.put("newprice", newPrice);
        warning.put("feeCode", feeCode);
        warning.put("prices", prices);
        warning.put("effectiveDate", effectiveDate);
        warning.put("terminactionDate", terminationDate);
        warning.put("description", description);
        warning.put("oldprice", newCode ? "--" : oldPrice);
        warning.put("diff", newCode ? "" : diff);
        if (!newCode) {
            warning.put("numCodes", Integer.toString(numCodes));
        }
        return warning;
    }

    public static FeeScheduleChange fromWarningMap(Map<?, ?> warning) {
        Object oldPriceValue = warning.get("oldprice");
        boolean newCode = "--".equals(String.valueOf(oldPriceValue));
        BigDecimal oldPrice = newCode ? null : BillingMoney.amount(String.valueOf(oldPriceValue));
        BigDecimal newPrice = BillingMoney.amount(String.valueOf(warning.get("newprice")));
        BigDecimal diff = newCode ? null : newPrice.subtract(oldPrice);
        return new FeeScheduleChange(
                String.valueOf(warning.get("feeCode")),
                oldPrice,
                newPrice,
                diff,
                String.valueOf(warning.get("prices")),
                String.valueOf(warning.get("effectiveDate")),
                String.valueOf(warning.get("terminactionDate")),
                String.valueOf(warning.get("description")),
                0,
                newCode);
    }
}
