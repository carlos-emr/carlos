/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.prescript.util;

import com.google.common.base.Ascii;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;

/** A Canadian-dollar estimate from a formulary unit price and an optional dispense quantity. */
public record DrugPriceEstimate(String amount, String quantity) {

    /**
     * Returns a unit-price estimate when the dispense quantity is unavailable.
     * A missing or invalid formulary price yields no estimate.
     */
    public static DrugPriceEstimate from(String unitPrice, String quantity) {
        String price = decimal(unitPrice);
        if (price == null) {
            return null;
        }
        String count = decimal(quantity);
        BigDecimal amount = new BigDecimal(price);
        if (count != null) {
            BigDecimal units = new BigDecimal(count);
            if (units.signum() > 0) {
                amount = amount.multiply(units);
            } else {
                count = null;
            }
        }
        return new DrugPriceEstimate(NumberFormat.getCurrencyInstance(Locale.CANADA).format(amount), count);
    }

    /**
     * Resolves a plain quantity or an explicit tablet or capsule count for the matching dosage form.
     * Duration expressions, ranges and other units cannot establish a dispense count.
     */
    public static String dispenseQuantity(String quantityText, String drugForm) {
        if (quantityText == null) {
            return null;
        }
        String text = quantityText.strip();
        int end = decimalEnd(text);
        if (end == 0 || (end < text.length() && !matchesDosageUnit(text.substring(end), drugForm))) {
            return null;
        }
        String count = text.substring(0, end);
        return new BigDecimal(count).signum() > 0 ? count : null;
    }

    /** Resolves the Qty/Mitte display assembled from an existing prescription or calculation. */
    public static String dispenseQuantity(String quantity, String unitName, String drugForm) {
        String unit = unitName == null ? "" : unitName.strip();
        if (unit.isEmpty() || Ascii.equalsIgnoreCase("null", unit)) {
            return dispenseQuantity(quantity, drugForm);
        }
        return dispenseQuantity(quantity + " " + unit, drugForm);
    }

    private static boolean matchesDosageUnit(String suffix, String drugForm) {
        if (drugForm == null) {
            return false;
        }
        String unit = Ascii.toLowerCase(suffix.stripLeading());
        if (unit.endsWith(".")) {
            unit = unit.substring(0, unit.length() - 1);
        }
        String form = Ascii.toLowerCase(drugForm.strip());
        return switch (unit) {
            case "tab", "tabs", "tablet", "tablets" -> form.startsWith("tablet");
            case "cap", "caps", "capsule", "capsules" -> form.startsWith("capsule");
            default -> false;
        };
    }

    private static String decimal(String value) {
        if (value == null) {
            return null;
        }
        String number = value.strip();
        int end = decimalEnd(number);
        return end > 0 && end == number.length() ? number : null;
    }

    /** Returns the end of an ASCII decimal prefix, or zero when the prefix is invalid. */
    private static int decimalEnd(String value) {
        int end = digitsEnd(value, 0);
        if (end < value.length() && value.charAt(end) == '.') {
            int fraction = end + 1;
            end = digitsEnd(value, fraction);
            return end == fraction ? 0 : end;
        }
        return end;
    }

    private static int digitsEnd(String value, int start) {
        int end = start;
        while (end < value.length() && value.charAt(end) >= '0' && value.charAt(end) <= '9') {
            end++;
        }
        return end;
    }
}
