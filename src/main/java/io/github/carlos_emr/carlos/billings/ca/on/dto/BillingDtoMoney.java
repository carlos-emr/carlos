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
import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.utility.LogSafe;

import java.math.BigDecimal;

/**
 * Shared parsing bridge for legacy DTOs that still expose string money
 * accessors while storing normalized money internally.
 */
final class BillingDtoMoney {

    private BillingDtoMoney() {
    }

    static BillingMoney parseNonNegativeDecimal(String raw, String fieldName) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        return BillingMoney.parseNonNegative(raw, fieldName);
    }

    static BillingMoney parseNonNegativeStoredCents(String raw, String fieldName) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        return BillingMoney.storedCents(raw, fieldName);
    }

    /**
     * Parses RA money fields, which can legitimately be signed because
     * remittance adjustments include reversals and negative transactions.
     */
    static BigDecimal parseSignedDecimal(String raw, String fieldName) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return BillingMoney.amount(raw);
        } catch (NumberFormatException e) {
            throw new BillingValidationException(
                    "BillingMoney: malformed " + fieldName + " ["
                            + LogSafe.sanitizeForDisplay(raw) + "]", e);
        }
    }

    static String format(BillingMoney money) {
        return money == null ? null : money.format();
    }

    static String format(BigDecimal amount) {
        return amount == null ? null : BillingMoney.format(amount);
    }
}
