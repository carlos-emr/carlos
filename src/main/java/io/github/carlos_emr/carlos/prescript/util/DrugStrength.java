/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.prescript.util;

import io.github.carlos_emr.carlos.utility.DoseText;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The single amount and unit that the legacy medication API can carry.
 *
 * @since 2026-11-26
 */
public record DrugStrength(Float amount, String unit) {

    private static final DrugStrength UNKNOWN = new DrugStrength(null, null);
    private static final int UNIT_FLAGS = Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CHARACTER_CLASS;
    private static final Pattern UNIT_AFTER_NUMBER =
            Pattern.compile("\\s*+(international\\s++units?\\b|[\\p{L}%]++)\\S*+", UNIT_FLAGS);
    // A volume denominator can contain spaces on either side of the slash or its amount.
    // A second ingredient such as "5 mg / 20 mg" still ends the first ingredient's unit.
    private static final Pattern PER_VOLUME = Pattern.compile("\\s*+(?:/|per\\b)\\s*+", UNIT_FLAGS);
    private static final Pattern VOLUME_UNIT =
            Pattern.compile("\\s*+(?:[mµμun]?l|(?:milli|micro)?lit(?:er|re)s?)\\b\\S*+", UNIT_FLAGS);
    // A plus can belong to the unit or to an exponent. It separates ingredients when it introduces another amount.
    private static final Pattern INGREDIENT_SEPARATOR =
            Pattern.compile("[,;]|(?<![0-9]e)\\+(?=\\s*+(?:[0-9]|\\.[0-9]))", UNIT_FLAGS);

    /**
     * Reads the leading strength from stored dosage text. For a combination it returns the first
     * component, and for a concentration it keeps a unit such as {@code mg/5 mL} intact.
     * A leading number followed by another number before the unit is ambiguous, for example
     * {@code 1,000 mg}, {@code 1½ mg}, or {@code 5-10 mg}. A unit with no amount is kept whole
     * when it starts the text, as in {@code mg/5mL}.
     */
    public static DrugStrength fromDosage(String dosage) {
        if (dosage == null) {
            return UNKNOWN;
        }
        String text = dosage.strip();
        int end = DoseText.numberEnd(text, 0);
        if (end == 0) {
            return UNIT_AFTER_NUMBER.matcher(text).lookingAt() ? new DrugStrength(null, text) : UNKNOWN;
        }
        if (anotherNumberFollows(text, end)) {
            return UNKNOWN;
        }
        String number = text.substring(0, end);
        float strength = Float.parseFloat(number);
        if (!Float.isFinite(strength) || (strength == 0f && hasNonzeroSignificand(number))) {
            return UNKNOWN;
        }
        Matcher unitMatch = UNIT_AFTER_NUMBER.matcher(text).region(end, text.length());
        if (!unitMatch.lookingAt()) {
            return new DrugStrength(strength, null);
        }
        int unitEnd = unitMatch.end();
        Matcher separator = PER_VOLUME.matcher(text).region(unitMatch.end(1), text.length());
        if (separator.lookingAt()) {
            int amountEnd = DoseText.numberEnd(text, separator.end());
            Matcher volume = VOLUME_UNIT.matcher(text).region(amountEnd, text.length());
            if (volume.lookingAt()) unitEnd = Math.max(unitEnd, volume.end());
        }
        Matcher nextIngredient = INGREDIENT_SEPARATOR.matcher(text).region(unitMatch.end(1), text.length());
        if (nextIngredient.find() && nextIngredient.start() < unitEnd) {
            unitEnd = nextIngredient.start();
        }
        // A trailing slash may be attached to the first unit before the next ingredient.
        while (text.charAt(unitEnd - 1) == '/') {
            unitEnd--;
        }
        return new DrugStrength(strength, text.substring(unitMatch.start(1), unitEnd));
    }

    /** Distinguishes underflow from a written zero in a number already recognized by DoseText. */
    private static boolean hasNonzeroSignificand(String number) {
        for (int index = 0; index < number.length(); index++) {
            char digit = number.charAt(index);
            if (digit == 'e' || digit == 'E') {
                return false;
            }
            if (digit != '0' && digit != '.') {
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

    /** Formats the supplied fields for storage, leaving out whichever field is missing. */
    public String dosageText() {
        if (amount == null) {
            return unit == null ? "" : unit;
        }
        String number = DoseText.of(amount.floatValue());
        return unit == null || unit.isEmpty() ? number : number + " " + unit;
    }
}
