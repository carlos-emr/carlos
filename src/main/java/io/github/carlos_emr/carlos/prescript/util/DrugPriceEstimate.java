/*
 * Copyright (c) 2026 CARLOS Contributors.
 * SPDX-License-Identifier: GPL-2.0-or-later
 */
package io.github.carlos_emr.carlos.prescript.util;

import java.math.BigDecimal;
import java.text.NumberFormat;
import java.util.Locale;
import java.util.regex.Pattern;

/** A Canadian-dollar estimate from a formulary unit price and an optional dispense quantity. */
public record DrugPriceEstimate(String amount, String quantity) {

    private static final String DECIMAL = "(?:[0-9]+(?:\\.[0-9]+)?|\\.[0-9]+)";
    private static final Pattern PLAIN_DECIMAL = Pattern.compile(DECIMAL);
    private static final Pattern COUNT = Pattern.compile(
            "(" + DECIMAL + ")(?:\\s*(tab(?:let)?s?|cap(?:sule)?s?)\\.?)?", Pattern.CASE_INSENSITIVE);

    /**
     * Returns a unit-price estimate when the dispense quantity is unavailable.
     * A missing or invalid formulary price yields no estimate.
     */
    public static DrugPriceEstimate from(String unitPrice, String quantity) {
        String price = decimal(unitPrice);
        if (price == null) {
            return null;
        }
        String count = positiveDecimal(quantity);
        BigDecimal amount = new BigDecimal(price);
        if (count != null) {
            amount = amount.multiply(new BigDecimal(count));
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
        var count = COUNT.matcher(quantityText.strip());
        if (!count.matches()) {
            return null;
        }
        String unit = count.group(2);
        if (unit != null) {
            String expectedForm = unit.toLowerCase(Locale.ROOT).startsWith("tab") ? "tablet" : "capsule";
            if (drugForm == null || !drugForm.strip().toLowerCase(Locale.ROOT).startsWith(expectedForm)) {
                return null;
            }
        }
        return positiveDecimal(count.group(1));
    }

    /** Resolves the Qty/Mitte display assembled from an existing prescription or calculation. */
    public static String dispenseQuantity(String quantity, String unitName, String drugForm) {
        if (unitName == null || unitName.isBlank() || "null".equalsIgnoreCase(unitName.strip())) {
            return dispenseQuantity(quantity, drugForm);
        }
        return dispenseQuantity(quantity + " " + unitName.strip(), drugForm);
    }

    private static String positiveDecimal(String value) {
        String number = decimal(value);
        return number != null && new BigDecimal(number).signum() > 0 ? number : null;
    }

    private static String decimal(String value) {
        if (value == null) {
            return null;
        }
        String number = value.strip();
        return PLAIN_DECIMAL.matcher(number).matches() ? number : null;
    }
}
