/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billing.CA.BC.model;

import java.math.BigDecimal;

/**
 * Exact BC monetary parsing, including Teleplan's implied cents and negative overpunch suffixes.
 *
 * @since 2026-11-02
 */
public final class TeleplanAmount {
    private static final java.util.regex.Pattern DECIMAL =
            java.util.regex.Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)");

    /** Parses stored decimal dollars; invalid syntax remains unknown and exponents are never expanded. */
    public static BigDecimal knownDecimal(String source) {
        if (source == null || !DECIMAL.matcher(source).matches()) return null;
        try { return new BigDecimal(source); }
        catch (NumberFormatException _) { return null; }
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
