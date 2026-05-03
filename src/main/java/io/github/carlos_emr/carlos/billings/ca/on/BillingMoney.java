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
import java.util.Optional;

import io.github.carlos_emr.carlos.billings.ca.on.validator.BillingValidationException;
import io.github.carlos_emr.carlos.utility.LogSanitizer;

/**
 * Immutable Ontario billing money value with CAD currency invariants,
 * non-negative scale-2 storage, arithmetic helpers, and legacy
 * {@link BigDecimal}-based parser/formatter bridges.
 *
 * @since 2026-04-28
 */
public record BillingMoney(BigDecimal amount, Currency currency) implements Comparable<BillingMoney> {
    public static final int MONEY_SCALE = 2;
    public static final Currency CAD = Currency.getInstance("CAD");
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
        String trimmed = raw.trim();
        if (trimmed.startsWith("-")) {
            throw new BillingValidationException(
                    "BillingMoney: " + fieldName + " cannot be negative ["
                            + LogSanitizer.sanitizeForDisplay(raw) + "]");
        }
        if (!trimmed.matches("\\d+")) {
            throw new BillingValidationException(
                    "BillingMoney: malformed " + fieldName + " ["
                            + LogSanitizer.sanitizeForDisplay(raw) + "]");
        }
        BigDecimal cents = new BigDecimal(trimmed);
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

    /**
     * @deprecated use {@link #minusExact(BillingMoney)} for throwing
     * behavior, or {@link #tryMinus(BillingMoney)} when overpayment is an
     * expected branch.
     */
    @Deprecated(forRemoval = false)
    public BillingMoney minus(BillingMoney other) {
        return minusExact(other);
    }

    public BillingMoney minusExact(BillingMoney other) {
        requireSameCurrency(other);
        BigDecimal difference = amount.subtract(other.amount);
        if (difference.signum() < 0) {
            throw new BillingValidationException(
                    "BillingMoney: subtraction result cannot be negative ["
                            + format(amount) + " - " + format(other.amount) + " = " + format(difference) + "]");
        }
        return new BillingMoney(difference, currency);
    }

    public Optional<BillingMoney> tryMinus(BillingMoney other) {
        requireSameCurrency(other);
        BigDecimal difference = amount.subtract(other.amount);
        if (difference.signum() < 0) {
            return Optional.empty();
        }
        return Optional.of(new BillingMoney(difference, currency));
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
            throw new BillingValidationException(
                    "BillingMoney: currency mismatch ["
                            + currency.getCurrencyCode() + " vs " + other.currency.getCurrencyCode() + "]");
        }
    }

    public static BigDecimal zeroAmount() {
        return ZERO;
    }

    public static BigDecimal amount(String raw) {
        return BillingAmounts.amount(raw);
    }

    public static BigDecimal amount(String raw, int scale) {
        return BillingAmounts.amount(raw, scale);
    }

    /**
     * @deprecated use {@link #amountOrZeroLegacyBigDecimal(String)} while
     * migrating legacy BigDecimal call sites toward {@link BillingMoney}.
     */
    @Deprecated(forRemoval = false)
    public static BigDecimal amountOrZero(String raw) {
        return amountOrZeroLegacyBigDecimal(raw);
    }

    /**
     * @deprecated use {@link #amountOrZeroLegacyBigDecimal(String, int)} while
     * migrating legacy BigDecimal call sites toward {@link BillingMoney}.
     */
    @Deprecated(forRemoval = false)
    public static BigDecimal amountOrZero(String raw, int scale) {
        return amountOrZeroLegacyBigDecimal(raw, scale);
    }

    /** Legacy BigDecimal bridge for report/import paths that still need zero-on-malformed behavior. */
    public static BigDecimal amountOrZeroLegacyBigDecimal(String raw) {
        return BillingAmounts.amountOrZero(raw);
    }

    /** Legacy BigDecimal bridge for report/import paths that still need zero-on-malformed behavior. */
    public static BigDecimal amountOrZeroLegacyBigDecimal(String raw, int scale) {
        return BillingAmounts.amountOrZero(raw, scale);
    }

    /**
     * @deprecated use {@link #ohipFeeAmountLegacyBigDecimal(String)} while
     * migrating fee-schedule import paths toward {@link BillingMoney}.
     */
    @Deprecated(forRemoval = false)
    public static BigDecimal ohipFeeAmount(String raw) {
        return ohipFeeAmountLegacyBigDecimal(raw);
    }

    /** Legacy BigDecimal bridge for fixed-width OHIP fee amounts. */
    public static BigDecimal ohipFeeAmountLegacyBigDecimal(String raw) {
        return BillingAmounts.ohipFeeAmount(raw);
    }

    public static boolean isNonZero(BigDecimal amount) {
        return amount != null && amount.compareTo(BigDecimal.ZERO) != 0;
    }

    public static boolean isPositive(String raw) {
        return BillingAmounts.isPositive(raw);
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
                    "BillingMoney: malformed " + fieldName + " ["
                            + LogSanitizer.sanitizeForDisplay(raw) + "]", e);
        }
        if (value.signum() < 0) {
            throw new BillingValidationException(
                    "BillingMoney: " + fieldName + " cannot be negative ["
                            + LogSanitizer.sanitizeForDisplay(raw) + "]");
        }
        return value;
    }

    /**
     * Parse an optional money amount that may be blank but, when populated,
     * must be a non-negative money token. Null and blank inputs return scale-2
     * zero; malformed or negative values throw {@link BillingValidationException}.
     *
     * @param raw       String the user/DB-supplied numeric token
     * @param fieldName String diagnostic name embedded in the throw message
     * @return BigDecimal parsed amount at {@link #MONEY_SCALE}, or zero when blank
     * @throws BillingValidationException on malformed or negative input
     */
    public static BigDecimal parseOptionalNonNegativeAmount(String raw, String fieldName) {
        if (raw == null || raw.trim().isEmpty()) {
            return ZERO;
        }
        return parseNonNegativeAmount(raw, fieldName);
    }
}
