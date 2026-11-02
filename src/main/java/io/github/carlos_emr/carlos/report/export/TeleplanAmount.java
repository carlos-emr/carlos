/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report.export;

import java.math.BigDecimal;

/** Teleplan's implied-cents amount representation, including negative overpunch suffixes. */
public final class TeleplanAmount {
    private TeleplanAmount() {
    }

    /** Decodes Teleplan signed, implied-cents amounts without rounding through double or logging input. */
    public static String decode(String value) {
        if (value == null || value.isBlank()) return "0.00";
        String negativeDigits = "}JKLMNOPQR";
        int last = negativeDigits.indexOf(value.charAt(value.length() - 1));
        String digits = last < 0 ? value : "-" + value.substring(0, value.length() - 1) + last;
        return new BigDecimal(digits).movePointLeft(2).setScale(2).toPlainString();
    }

}
