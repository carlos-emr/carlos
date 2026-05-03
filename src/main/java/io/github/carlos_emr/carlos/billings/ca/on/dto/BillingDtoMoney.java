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
import io.github.carlos_emr.carlos.utility.LogSanitizer;

import java.math.BigDecimal;

/**
 * Shared parsing bridge for legacy DTOs that still expose string money
 * accessors while storing normalized money internally.
 */
final class BillingDtoMoney {

    private BillingDtoMoney() {
    }

    static BillingMoney parseNonNegativeStoredCentsOrDecimal(String raw, String fieldName) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        String value = raw.trim();
        // OHIP claims-error report amount tokens are six digits of stored
        // cents. Shorter digit-only DAO/JSP values are decimal dollars.
        return value.matches("\\d{6}")
                ? BillingMoney.storedCents(value, fieldName)
                : BillingMoney.parseNonNegative(value, fieldName);
    }

    static BigDecimal parseSignedDecimal(String raw, String fieldName) {
        if (raw == null || raw.trim().isEmpty()) {
            return null;
        }
        try {
            return BillingMoney.amount(raw);
        } catch (NumberFormatException e) {
            throw new BillingValidationException(
                    "BillingMoney: malformed " + fieldName + " ["
                            + LogSanitizer.sanitizeForDisplay(raw) + "]", e);
        }
    }

    static String format(BillingMoney money) {
        return money == null ? null : money.format();
    }

    static String format(BigDecimal amount) {
        return amount == null ? null : BillingMoney.format(amount);
    }
}
