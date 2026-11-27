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
    private static final Pattern STRENGTH_IN_TEXT = Pattern.compile(
            "(?i)(?<![\\p{L}\\p{N}.])([0-9]++(?:\\.[0-9]++)?+|\\.[0-9]++)\\h*+"
                    + "(mcg|mg|kg|µg|μg|ug|g|ml|l|meq|mmol|iu|units?|%)(?![\\p{L}\\p{N}])");

    /** Comparison only. Never rewrite the stored medication name, directions or dosage. */
    public static String identityText(String text) {
        if (text == null) return null;
        String value = text.strip();
        Matcher strengths = STRENGTH_IN_TEXT.matcher(value);
        StringBuilder result = new StringBuilder();
        while (strengths.find()) {
            int previous = strengths.start() - 1;
            while (previous >= 0 && (Character.isWhitespace(value.charAt(previous))
                    || Character.isSpaceChar(value.charAt(previous)))) previous--;
            if (previous >= 0 && (Character.isDigit(value.charAt(previous))
                    || ",./–-:+".indexOf(value.charAt(previous)) >= 0)) continue;
            String amount = DoseText.ofNumericText(strengths.group(1));
            strengths.appendReplacement(result, Matcher.quoteReplacement(amount + strengths.group(2)));
        }
        return strengths.appendTail(result).toString();
    }

    public static boolean sameIdentityText(String a, String b) {
        return org.apache.commons.lang3.StringUtils.equalsIgnoreCase(identityText(a), identityText(b));
    }

    private static final DrugStrength UNKNOWN = new DrugStrength(null, null);
    private static final Pattern UNIT_AFTER_NUMBER =
            Pattern.compile("\\s*+([\\p{L}%]\\S*+)", Pattern.UNICODE_CHARACTER_CLASS);

    /**
     * Reads the leading strength from stored dosage text. For a combination it returns the first
     * component, and for a concentration it keeps a compact unit such as {@code mg/5mL} intact.
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
        float strength = Float.parseFloat(text.substring(0, end));
        if (!Float.isFinite(strength)) {
            return UNKNOWN;
        }
        Matcher unitMatch = UNIT_AFTER_NUMBER.matcher(text).region(end, text.length());
        if (!unitMatch.lookingAt()) {
            return new DrugStrength(strength, null);
        }
        int unitEnd = unitMatch.end(1);
        // A separator before a later ingredient can be attached to the first unit.
        while ("/,;".indexOf(text.charAt(unitEnd - 1)) >= 0) {
            unitEnd--;
        }
        return new DrugStrength(strength, text.substring(unitMatch.start(1), unitEnd));
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
