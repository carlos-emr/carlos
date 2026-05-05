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
package io.github.carlos_emr.carlos.billings.ca.on;

import java.math.BigDecimal;
import java.math.RoundingMode;

import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.utility.LogSanitizer;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Legacy BigDecimal parsing and formatting helpers for Ontario billing.
 *
 * <p>New domain code should prefer {@link BillingMoney}; this class exists
 * for report, import, and JSP bridge code that still exchanges raw
 * {@link BigDecimal} values.</p>
 *
 * @since 2026-05-03
 */
public final class BillingAmounts {
    private static final int MONEY_SCALE = BillingMoney.MONEY_SCALE;
    private static final int OHIP_FEE_SCALE = 4;

    private BillingAmounts() {
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
            MiscUtils.getLogger().error(
                    "BillingAmounts.amountOrZero: malformed amount=\"{}\", returning ZERO",
                    LogSanitizer.sanitize(raw), e);
            return BigDecimal.ZERO.setScale(scale);
        }
    }

    public static BigDecimal ohipFeeAmount(String raw) {
        return decimal(raw).movePointLeft(OHIP_FEE_SCALE)
                .setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    public static boolean isPositive(String raw) {
        return decimal(raw).compareTo(BigDecimal.ZERO) > 0;
    }

    public static String format(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP).toPlainString();
    }

    private static BigDecimal decimal(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new BillingValidationException("BillingMoney: amount is null or blank");
        }
        return new BigDecimal(raw.trim());
    }
}
