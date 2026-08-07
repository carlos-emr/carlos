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

import io.github.carlos_emr.carlos.billings.ca.on.BillingMoney;

import java.math.BigDecimal;

/**
 * One row from the {@code billing_service} table for OHIP code lookup —
 * the service code, its description, fee value, percentage, effective date,
 * and GST flag.
 *
 * <p>Consumers can read only the attributes they need. Calculation paths
 * should use {@link #valueMoney()} or {@link #percentageAmount()} so malformed
 * schedule values fail at the billing boundary instead of being reparsed
 * inconsistently by each caller.</p>
 *
 * @since 2026-05-01
 */
public record BillingCodeAttribute(
        String serviceCode,
        String description,
        String value,
        String percentage,
        String billingServiceDate,
        String gstFlag) {
    public BillingCodeAttribute {
        serviceCode = serviceCode == null ? "" : serviceCode;
        description = description == null ? "" : description;
        value = value == null ? "" : value;
        percentage = percentage == null ? "" : percentage;
        billingServiceDate = billingServiceDate == null ? "" : billingServiceDate;
        gstFlag = gstFlag == null ? "" : gstFlag;
    }

    /** Parsed service-code fee for calculation paths that should not reparse the raw string ad hoc. */
    public BillingMoney valueMoney() {
        return value.isBlank() ? null : BillingMoney.parseNonNegative(value, "value");
    }

    /** Parsed percentage multiplier for percentage-line calculations. */
    public BigDecimal percentageAmount() {
        return percentage.isBlank()
                ? BigDecimal.ZERO.setScale(BillingMoney.MONEY_SCALE)
                : BillingMoney.parseNonNegativeAmount(percentage, "percentage");
    }
}
