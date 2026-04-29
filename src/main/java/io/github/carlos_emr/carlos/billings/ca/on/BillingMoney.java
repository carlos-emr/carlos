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
package io.github.carlos_emr.carlos.billings.ca.on;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Central money parsing for Ontario billing.
 *
 * @since 2026-04-28
 */
public final class BillingMoney {
    public static final int MONEY_SCALE = 2;
    private static final int OHIP_FEE_SCALE = 4;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE);

    private BillingMoney() {
    }

    public static BigDecimal zero() {
        return ZERO;
    }

    public static BigDecimal amount(String raw) {
        return amount(raw, MONEY_SCALE);
    }

    public static BigDecimal amount(String raw, int scale) {
        return decimal(raw).setScale(scale, RoundingMode.HALF_UP);
    }

    public static BigDecimal amountOrZero(String raw) {
        return amountOrZero(raw, MONEY_SCALE);
    }

    public static BigDecimal amountOrZero(String raw, int scale) {
        if (raw == null || raw.trim().isEmpty()) {
            return BigDecimal.ZERO.setScale(scale);
        }
        try {
            return amount(raw, scale);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO.setScale(scale);
        }
    }

    public static BigDecimal ohipFeeAmount(String raw) {
        return decimal(raw).movePointLeft(OHIP_FEE_SCALE).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static boolean isNonZero(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) != 0;
    }

    public static boolean isPositive(String raw) {
        return decimal(raw).compareTo(BigDecimal.ZERO) > 0;
    }

    public static String format(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal decimal(String raw) {
        return new BigDecimal(raw.trim());
    }
}
