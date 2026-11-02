/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.export;

import java.math.BigDecimal;

/**
 * Teleplan's implied-cents amount representation, including negative overpunch suffixes.
 *
 * @since 2026-11-02
 */
public final class TeleplanAmount {
    private TeleplanAmount() {
    }

    /**
     * Decodes signed, implied-cents amounts without double rounding or input logging.
     *
     * @param value String integer cents, optionally padded or ending in a negative }JKLMNOPQR overpunch
     * @return String plain decimal CAD with two places; null or blank inputs become 0.00
     * @throws NumberFormatException if the amount is not numeric after decoding
     * @throws ArithmeticException if the input contains fractional cents
     */
    public static String decode(String value) {
        if (value == null || value.isBlank()) return "0.00";
        value = value.strip();
        String negativeDigits = "}JKLMNOPQR";
        int last = negativeDigits.indexOf(value.charAt(value.length() - 1));
        String digits = last < 0 ? value : "-" + value.substring(0, value.length() - 1) + last;
        return new BigDecimal(digits).movePointLeft(2).setScale(2).toPlainString();
    }

}
