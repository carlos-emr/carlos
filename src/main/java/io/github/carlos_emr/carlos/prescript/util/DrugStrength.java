/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.prescript.util;

import io.github.carlos_emr.carlos.utility.DoseText;

import org.apache.commons.lang3.Strings;

import java.util.List;

/**
 * The single amount and unit that the legacy medication API can carry.
 *
 * @param amount Float the leading numeric strength amount, or {@code null} when absent or unstructured
 * @param unit String the strength unit text, or {@code null} when absent
 * @since 2026-11-26
 */
public record DrugStrength(Float amount, String unit) {
    private static final List<String> STRENGTH_UNITS = List.of(
            "units", "unit", "mmol", "mcg", "meq", "mg", "kg", "µg", "μg", "ug", "ml", "iu", "g", "l", "%");
    private static final List<String> CONCENTRATION_BASES = List.of(
            "actuations", "actuation", "sprays", "spray", "drops", "drop", "doses", "dose",
            "hours", "hour", "days", "day", "patch", "min", "ml", "dl", "hr", "l", "h", "d");

    /**
     * Normalizes strength tokens in {@code text} for comparison only; never rewrites stored medication names,
     * directions, or dosages.
     *
     * <p>Trims leading/trailing whitespace, normalizes plain ASCII decimal strength amounts via
     * {@link DoseText#ofNumericText(String)}, and removes horizontal whitespace between each matched number and
     * strength unit (such as {@code 250.0 mg/5.0 ml} &rarr; {@code 250mg/5ml}) while leaving fractions, ranges,
     * grouped numbers, ratios, and signed values untouched.</p>
     *
     * @param text String medication name, dosage, or strength text to normalize, or {@code null}
     * @return String normalized comparison text, or {@code null} when {@code text} is {@code null}
     */
    public static String identityText(String text) {
        if (text == null) return null;
        String value = text.strip();
        StringBuilder result = null;
        int copied = 0;
        int at = 0;
        while (at < value.length()) {
            int numberEnd = plainDecimalEnd(value, at);
            if (numberEnd > at) {
                if (isStrengthBoundaryBefore(value, at)) {
                    int unitStart = skipHorizontalSpace(value, numberEnd);
                    int unitEnd = matchStrengthUnit(value, unitStart);
                    if (unitEnd > unitStart) {
                        if (result == null) result = new StringBuilder(value.length());
                        String amount = DoseText.ofNumericText(value.substring(at, numberEnd));
                        result.append(value, copied, at).append(amount).append(value, unitStart, unitEnd);
                        copied = unitEnd;
                        at = unitEnd;
                        continue;
                    }
                }
                at = numberEnd;
                continue;
            }
            at += Character.charCount(value.codePointAt(at));
        }
        return result == null ? value : result.append(value, copied, value.length()).toString();
    }

    private static int plainDecimalEnd(String value, int start) {
        int end = start;
        while (end < value.length() && isAsciiDigit(value.charAt(end))) end++;
        if (end < value.length() && value.charAt(end) == '.'
                && end + 1 < value.length() && isAsciiDigit(value.charAt(end + 1))) {
            end++;
            while (end < value.length() && isAsciiDigit(value.charAt(end))) end++;
        }
        return end;
    }

    private static boolean isStrengthBoundaryBefore(String value, int start) {
        if (start == 0) return true;
        int immediate = value.codePointBefore(start);
        if (Character.isLetterOrDigit(immediate) || immediate == '.') return false;
        int previous = skipWhitespaceBackward(value, start - 1);
        if (previous < 0) return true;
        char prevChar = value.charAt(previous);
        if (Character.isDigit(prevChar) || prevChar == '.') return false;
        if (",/–-:+".indexOf(prevChar) >= 0) {
            int beforeSeparator = skipWhitespaceBackward(value, previous - 1);
            if (beforeSeparator < 0) return false;
            char beforeChar = value.charAt(beforeSeparator);
            return !Character.isDigit(beforeChar) && ",./–-:+".indexOf(beforeChar) < 0;
        }
        return true;
    }

    private static int skipWhitespaceBackward(String value, int index) {
        int i = index;
        while (i >= 0 && (Character.isWhitespace(value.charAt(i)) || Character.isSpaceChar(value.charAt(i)))) {
            i--;
        }
        return i;
    }

    private static int skipHorizontalSpace(String value, int start) {
        int i = start;
        while (i < value.length()) {
            char ch = value.charAt(i);
            if (ch == ' ' || ch == '\t' || Character.getType(ch) == Character.SPACE_SEPARATOR) {
                i++;
            } else {
                break;
            }
        }
        return i;
    }

    private static int matchStrengthUnit(String value, int start) {
        int remaining = value.length() - start;
        for (String unit : STRENGTH_UNITS) {
            int len = unit.length();
            if (remaining >= len && Strings.CI.equals(value.substring(start, start + len), unit)) {
                int end = start + len;
                if (end == value.length() || !Character.isLetterOrDigit(value.codePointAt(end))) {
                    return end;
                }
            }
        }
        return start;
    }

    private static boolean isAsciiDigit(char ch) {
        return ch >= '0' && ch <= '9';
    }

    /**
     * Compares two medication text values case-insensitively after normalizing plain ASCII decimal strength
     * amounts and removing spaces between matched numbers and strength units via {@link #identityText(String)}.
     *
     * @param a String first medication text to compare, or {@code null}
     * @param b String second medication text to compare, or {@code null}
     * @return boolean {@code true} if both inputs are {@code null} or normalize to equal case-insensitive identity text
     */
    public static boolean sameIdentityText(String a, String b) {
        return Strings.CI.equals(identityText(a), identityText(b));
    }

    private static final DrugStrength UNKNOWN = new DrugStrength(null, null);

    /**
     * Reads the leading strength from stored dosage text. For a combination it returns the first
     * component, and for a concentration it keeps both compact ({@code mg/5mL}, {@code mg/mL}) and
     * spaced ({@code mg / mL}, {@code mg/5 mL}) denominator units intact without merging separate
     * ingredient strengths. A leading number followed by another number before the unit is ambiguous,
     * for example {@code 1,000 mg}, {@code 1½ mg}, or {@code 5-10 mg}. A unit with no amount is kept
     * whole when it starts the text, as in {@code mg/5mL}.
     *
     * @param dosage String stored dosage or strength text, or {@code null}
     * @return DrugStrength the extracted leading amount and unit, or a {@code (null, null)} instance when unstructured
     */
    public static DrugStrength fromDosage(String dosage) {
        if (dosage == null) {
            return UNKNOWN;
        }
        String text = dosage.strip();
        int end = DoseText.numberEnd(text, 0);
        if (end == 0) {
            return startsWithUnitChar(text, 0) ? new DrugStrength(null, text) : UNKNOWN;
        }
        if (anotherNumberFollows(text, end)) {
            return UNKNOWN;
        }
        float strength = Float.parseFloat(text.substring(0, end));
        if (!Float.isFinite(strength)) {
            return UNKNOWN;
        }
        int unitStart = skipHorizontalSpace(text, end);
        if (!startsWithUnitChar(text, unitStart)) {
            return new DrugStrength(strength, null);
        }
        int unitEnd = scanUnitToken(text, unitStart);
        int slashIndex = skipHorizontalSpace(text, unitEnd);
        if (slashIndex < text.length() && text.charAt(slashIndex) == '/') {
            int denomStart = skipHorizontalSpace(text, slashIndex + 1);
            if (startsWithUnitChar(text, denomStart)) {
                unitEnd = scanUnitToken(text, denomStart);
            } else {
                int denomNumberEnd = DoseText.numberEnd(text, denomStart);
                if (denomNumberEnd > denomStart && !anotherNumberFollows(text, denomNumberEnd)) {
                    int denomUnitStart = skipHorizontalSpace(text, denomNumberEnd);
                    if (startsWithUnitChar(text, denomUnitStart)) {
                        int denomUnitEnd = scanUnitToken(text, denomUnitStart);
                        if (isConcentrationBase(text.substring(unitStart, unitEnd),
                                text.substring(denomUnitStart, denomUnitEnd))) {
                            unitEnd = denomUnitEnd;
                        }
                    }
                }
            }
        }
        return new DrugStrength(strength, text.substring(unitStart, unitEnd));
    }

    private static boolean startsWithUnitChar(String text, int index) {
        if (index >= text.length()) return false;
        int codePoint = text.codePointAt(index);
        return Character.isLetter(codePoint) || codePoint == '%';
    }

    private static int scanUnitToken(String text, int start) {
        int index = start;
        while (index < text.length()) {
            int codePoint = text.codePointAt(index);
            if (Character.isWhitespace(codePoint) || Character.isSpaceChar(codePoint)
                    || "/,;+".indexOf(codePoint) >= 0) {
                break;
            }
            index += Character.charCount(codePoint);
        }
        return index;
    }

    private static final List<String> MASS_CONCENTRATION_BASES = List.of("g", "gm", "gram", "grams", "kg");

    private static boolean isConcentrationBase(String numeratorUnit, String unitToken) {
        for (String base : CONCENTRATION_BASES) {
            if (Strings.CI.equals(unitToken, base)) {
                return true;
            }
        }
        return isMassConcentrationBase(unitToken) && !isMassConcentrationBase(numeratorUnit);
    }

    private static boolean isMassConcentrationBase(String unitToken) {
        for (String base : MASS_CONCENTRATION_BASES) {
            if (Strings.CI.equals(unitToken, base)) {
                return true;
            }
        }
        return false;
    }

    private static boolean anotherNumberFollows(String text, int end) {
        for (int next = end; next < text.length();) {
            int codePoint = text.codePointAt(next);
            int type = Character.getType(codePoint);
            if (type == Character.DECIMAL_DIGIT_NUMBER || type == Character.LETTER_NUMBER
                    || type == Character.OTHER_NUMBER) {
                return true;
            }
            if (Character.isLetter(codePoint) || codePoint == '%') {
                return false;
            }
            next += Character.charCount(codePoint);
        }
        return false;
    }

    /**
     * Formats the supplied fields for storage, omitting whichever field is missing or empty.
     *
     * @return String plain decimal amount and unit joined by a space, the present field alone, or {@code ""} if both are absent
     */
    public String dosageText() {
        if (amount == null) {
            return unit == null ? "" : unit;
        }
        String number = DoseText.of(amount.floatValue());
        return unit == null || unit.isEmpty() ? number : number + " " + unit;
    }
}
