/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.prescript.util;

import java.math.BigDecimal;
import java.time.DateTimeException;
import java.time.LocalDate;

/** Validation of stored observations before calculating a renal dose. */
public final class RenalDosingInput {
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
        try {
            // BigDecimal enforces decimal syntax without regex backtracking or Java's
            // hexadecimal literals and float/double suffixes.
            double value = new BigDecimal(observation.trim()).doubleValue();
            return Double.isFinite(value) && value > 0 ? value : Double.NaN;
        } catch (NumberFormatException _) {
            return Double.NaN;
        }
    }

    /**
     * Validates the complete birth date before the demographic age calculation.
     *
     * @param year String containing the recorded birth year, possibly null
     * @param month String containing the recorded birth month, possibly null
     * @param day String containing the recorded birth day, possibly null
     * @return boolean true when the components describe a complete valid birth date
     */
    public static boolean validBirthDate(String year, String month, String day) {
        if (year == null || month == null || day == null) {
            return false;
        }
        try {
            LocalDate birthDate = LocalDate.of(Integer.parseInt(year.trim()),
                    Integer.parseInt(month.trim()), Integer.parseInt(day.trim()));
            return birthDate.getYear() >= 1 && birthDate.getYear() <= 9999;
        } catch (NumberFormatException | DateTimeException _) {
            return false;
        }
    }
}
