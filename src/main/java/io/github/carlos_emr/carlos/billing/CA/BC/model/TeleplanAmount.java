/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billing.CA.BC.model;

import java.math.BigDecimal;

/**
 * Exact BC monetary parsing, including Teleplan's implied cents and negative overpunch suffixes.
 *
 * @since 2026-11-02
 */
public final class TeleplanAmount {
    /** Parses stored decimal dollars in one pass; invalid syntax and exponent scales stay unknown. */
    public static BigDecimal knownDecimal(String source) {
        if (source == null || source.isEmpty()) return null;
        int index = source.charAt(0) == '+' || source.charAt(0) == '-' ? 1 : 0;
        boolean hasDigit = false;
        boolean hasPoint = false;
        for (; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value >= '0' && value <= '9') {
                hasDigit = true;
            } else if (value == '.' && !hasPoint) {
                hasPoint = true;
            } else {
                return null;
            }
        }
        return hasDigit ? new BigDecimal(source) : null;
    }

    private TeleplanAmount() {
    }

    /**
     * Decodes signed, implied-cents amounts without double rounding or input logging.
     *
     * @param value String integer cents, optionally padded or ending in a negative }JKLMNOPQR overpunch
     * @return String plain decimal CAD with two places; null or blank inputs become 0.00
     * @throws NumberFormatException if the amount is not numeric after decoding
     */
    public static String decode(String value) {
        if (value == null || value.isBlank()) return "0.00";
        value = value.strip();
        String negativeDigits = "}JKLMNOPQR";
        int last = negativeDigits.indexOf(value.charAt(value.length() - 1));
        String digits = last < 0 ? value : "-" + value.substring(0, value.length() - 1) + last;
        if (!digits.matches("-?\\d+")) throw new NumberFormatException("Invalid Teleplan amount");
        return new BigDecimal(digits).movePointLeft(2).setScale(2).toPlainString();
    }

}
