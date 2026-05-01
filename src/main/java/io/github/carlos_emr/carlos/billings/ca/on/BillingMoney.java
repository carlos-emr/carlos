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
import java.util.Currency;
import java.util.Objects;

import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.utility.MiscUtils;

/**
 * Central money parsing for Ontario billing.
 *
 * @since 2026-04-28
 */
public record BillingMoney(BigDecimal amount, Currency currency) implements Comparable<BillingMoney> {
    public static final int MONEY_SCALE = 2;
    public static final Currency CAD = Currency.getInstance("CAD");
    private static final int OHIP_FEE_SCALE = 4;
    private static final BigDecimal ZERO = BigDecimal.ZERO.setScale(MONEY_SCALE);

    public BillingMoney {
        amount = Objects.requireNonNull(amount, "amount").setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        if (amount.signum() < 0) {
            throw new BillingValidationException("BillingMoney: amount cannot be negative [" + amount + "]");
        }
        currency = Objects.requireNonNull(currency, "currency");
    }

    public static BillingMoney cad(BigDecimal amount) {
        return new BillingMoney(amount, CAD);
    }

    public static BillingMoney cad(String raw) {
        return parseNonNegative(raw, "amount");
    }

    public static BillingMoney parseNonNegative(String raw, String fieldName) {
        return cad(parseNonNegativeAmount(raw, fieldName));
    }

    public static BillingMoney storedCents(String raw, String fieldName) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new BillingValidationException(
                    "BillingMoney: " + fieldName + " is null or blank");
        }
        BigDecimal cents;
        try {
            cents = new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            throw new BillingValidationException(
                    "BillingMoney: malformed " + fieldName + " [" + raw + "]", e);
        }
        if (cents.signum() < 0) {
            throw new BillingValidationException(
                    "BillingMoney: " + fieldName + " cannot be negative [" + raw + "]");
        }
        return cad(cents.movePointLeft(MONEY_SCALE));
    }

    public String toStoredCents() {
        return amount.movePointRight(MONEY_SCALE)
                .setScale(0, RoundingMode.HALF_UP)
                .toPlainString();
    }

    public BillingMoney plus(BillingMoney other) {
        requireSameCurrency(other);
        return new BillingMoney(amount.add(other.amount), currency);
    }

    public BillingMoney minus(BillingMoney other) {
        requireSameCurrency(other);
        return new BillingMoney(amount.subtract(other.amount), currency);
    }

    public String format() {
        return format(amount);
    }

    @Override
    public int compareTo(BillingMoney other) {
        requireSameCurrency(other);
        return amount.compareTo(other.amount);
    }

    @Override
    public String toString() {
        return currency.getCurrencyCode() + " " + format();
    }

    private void requireSameCurrency(BillingMoney other) {
        Objects.requireNonNull(other, "other");
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException(
                    "Currency mismatch: " + currency.getCurrencyCode() + " vs " + other.currency.getCurrencyCode());
        }
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
            MiscUtils.getLogger().error(
                    "BillingMoney.amountOrZero: malformed amount=\"{}\", returning ZERO",
                    raw, e);
            return BigDecimal.ZERO.setScale(scale);
        }
    }

    /**
     * Strict variant of {@link #amountOrZero(String, int)} that propagates
     * the parse failure to the caller. Use this on billing-mutating paths
     * where silently substituting zero would persist the wrong amount.
     *
     * @param raw   String the user/DB-supplied numeric token (must be non-blank)
     * @param scale int target {@link BigDecimal} scale (typically {@link #MONEY_SCALE})
     * @return BigDecimal parsed amount, scaled and half-up rounded
     * @throws NumberFormatException when {@code raw} is null, blank, or unparseable
     */
    public static BigDecimal amountOrThrow(String raw, int scale) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new NumberFormatException("BillingMoney.amountOrThrow: amount is null or blank");
        }
        try {
            return amount(raw, scale);
        } catch (NumberFormatException e) {
            throw new NumberFormatException(
                    "BillingMoney.amountOrThrow: malformed amount=\"" + raw + "\"");
        }
    }

    /**
     * Convenience overload of {@link #amountOrThrow(String, int)} using
     * {@link #MONEY_SCALE}.
     *
     * @param raw String the user/DB-supplied numeric token
     * @return BigDecimal parsed amount at {@link #MONEY_SCALE}
     * @throws NumberFormatException when {@code raw} is null, blank, or unparseable
     */
    public static BigDecimal amountOrThrow(String raw) {
        return amountOrThrow(raw, MONEY_SCALE);
    }

    /**
     * Strict-but-blank-tolerant variant. Treats null/blank as a legitimate
     * empty cell (returns zero) but throws on any other unparseable input.
     * Use this on billing-mutating paths where blank cells are a normal part
     * of user input but typos must surface to the caller.
     *
     * @param raw   String the user/DB-supplied numeric token (null/blank → zero)
     * @param scale int target {@link BigDecimal} scale
     * @return BigDecimal parsed amount, or {@code ZERO} when {@code raw} is null/blank
     * @throws NumberFormatException when {@code raw} is non-blank and unparseable
     */
    public static BigDecimal amountStrictOrZero(String raw, int scale) {
        if (raw == null || raw.trim().isEmpty()) {
            return BigDecimal.ZERO.setScale(scale);
        }
        return amountOrThrow(raw, scale);
    }

    /**
     * Convenience overload of {@link #amountStrictOrZero(String, int)} using
     * {@link #MONEY_SCALE}.
     *
     * @param raw String the user/DB-supplied numeric token (null/blank → zero)
     * @return BigDecimal parsed amount at {@link #MONEY_SCALE}, or {@code ZERO} when blank
     * @throws NumberFormatException when {@code raw} is non-blank and unparseable
     */
    public static BigDecimal amountStrictOrZero(String raw) {
        return amountStrictOrZero(raw, MONEY_SCALE);
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

    /**
     * Parse a money amount that must be non-negative. Throws
     * {@link BillingValidationException} (which Struts maps to
     * {@code billingValidationError.jsp}) on null/blank/unparseable/negative
     * input, so the operator sees a form-validation banner rather than the
     * generic 500 page that {@code BillingONCHeader1.setTotal}'s raw
     * {@link IllegalArgumentException} would otherwise produce.
     *
     * @param raw       String the user/DB-supplied numeric token
     * @param fieldName String diagnostic name embedded in the throw message
     * @return BigDecimal parsed amount at {@link #MONEY_SCALE}
     * @throws BillingValidationException on invalid input
     */
    public static BigDecimal parseNonNegativeAmount(String raw, String fieldName) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new BillingValidationException(
                    "BillingMoney: " + fieldName + " is null or blank");
        }
        BigDecimal value;
        try {
            value = amount(raw, MONEY_SCALE);
        } catch (NumberFormatException e) {
            throw new BillingValidationException(
                    "BillingMoney: malformed " + fieldName + " [" + raw + "]", e);
        }
        if (value.signum() < 0) {
            throw new BillingValidationException(
                    "BillingMoney: " + fieldName + " cannot be negative [" + raw + "]");
        }
        return value;
    }
}
