/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prescript.util;

import java.util.regex.Pattern;

/** Validation of stored observations before calculating a renal dose. */
public final class RenalDosingInput {
    private static final Pattern DECIMAL = Pattern.compile("[+-]?(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?");

    private RenalDosingInput() {
    }

    /**
     * Validates a stored observation before using it in a dosing calculation.
     *
     * @param observation String containing the recorded observation, possibly null
     * @return double positive finite observation, or NaN when it cannot support a calculation
     */
    public static double positiveNumber(String observation) {
        if (observation == null) {
            return Double.NaN;
        }
        String normalized = observation.trim();
        if (!DECIMAL.matcher(normalized).matches()) {
            return Double.NaN;
        }
        try {
            double value = Double.parseDouble(normalized);
            return Double.isFinite(value) && value > 0 ? value : Double.NaN;
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }
}
