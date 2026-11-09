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

    /** Normalizes explicitly identified creatinine units; missing or ambiguous units cannot support dosing. */
    public static double creatinineMicromoles(String observation, String units) {
        double value = positiveNumber(observation);
        if (!Double.isFinite(value) || units == null) return Double.NaN;
        String normalized = normalizeUnits(units);
        return switch (normalized) {
            case "umol/l", "inumol/l", "micromol/l", "micromoles/l" -> value;
            case "mg/dl", "inmg/dl" -> Double.isFinite(value * 88.4) ? value * 88.4 : Double.NaN;
            default -> Double.NaN;
        };
    }

    /** Normalizes explicitly recorded weight units without guessing from the magnitude. */
    public static double weightKilograms(String observation, String units) {
        double value = positiveNumber(observation);
        if (!Double.isFinite(value) || units == null) return Double.NaN;
        return switch (normalizeUnits(units)) {
            case "kg", "inkg", "kilograms" -> value;
            case "lb", "lbs", "inlb", "inlbs" -> value * 0.45359237;
            default -> Double.NaN;
        };
    }

    /**
     * Conservative qualification range for automatic adult highlighting, not a normal-reference range.
     * Outside it the complete dosing table remains available for manual clinical assessment.
     */
    public static boolean supportsAutomaticHighlight(int age, double kilograms, double micromoles) {
        return supportsAge(age) && supportsWeight(kilograms) && supportsCreatinine(micromoles);
    }

    public static boolean supportsAge(int age) { return age >= 18 && age <= 120; }
    public static boolean supportsWeight(double value) { return Double.isFinite(value) && value >= 20 && value <= 500; }
    public static boolean supportsCreatinine(double value) { return Double.isFinite(value) && value >= 10 && value <= 3000;
    }

    /** Fold only ASCII unit letters and explicitly supported micro signs; reject Unicode look-alikes. */
    private static String normalizeUnits(String units) {
        StringBuilder normalized = new StringBuilder();
        for (char value : units.trim().toCharArray()) {
            if (value == ' ') continue;
            if (value >= 'A' && value <= 'Z') value = (char) (value + ('a' - 'A'));
            if (value == 'µ' || value == 'μ') value = 'u';
            normalized.append(value);
        }
        return normalized.toString();
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
