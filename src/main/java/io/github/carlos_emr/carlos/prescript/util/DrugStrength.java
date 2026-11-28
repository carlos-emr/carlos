/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.prescript.util;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.github.carlos_emr.carlos.utility.DoseText;

import java.util.Locale;
import java.util.Set;
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
    // Matches only the primary unit token (retaining a trailing '+' such as "e+" when not followed by a number).
    private static final Pattern UNIT_AFTER_NUMBER = Pattern.compile(
            "\\s*+(international\\s++units?\\b|[\\p{L}%]++(?:\\+(?!\\s*+(?:[0-9]|\\.[0-9])))?+)", UNIT_FLAGS);
    // A concentration, delivery-rate, or per-dosage-form denominator can have spaces around '/' or 'per',
    // while a second active ingredient such as "875mg/125mg" or "875 mg / 125 mg" stops at the first unit.
    private static final Pattern PER_DENOMINATOR = Pattern.compile("\\s*+(?:/|per\\b)\\s*+", UNIT_FLAGS);
    private static final Pattern DENOMINATOR_TOKEN = Pattern.compile("\\s*+([\\p{L}\\u00B22]++)(?!\\p{Nd})", UNIT_FLAGS);
    private static final Pattern PERCENT_BASIS = Pattern.compile("\\s++([wv]/[wv])\\b", UNIT_FLAGS);
    private static final Set<String> GRAM_UNITS = Set.of("g", "gm", "gram", "grams");
    private static final Set<String> DENOMINATOR_UNITS = Set.of(
            "l", "dl", "ml", "mcl", "ul", "\u00B5l", "\u03BCl", "nl", "liter", "liters", "litre", "litres",
            "milliliter", "milliliters", "millilitre", "millilitres",
            "microliter", "microliters", "microlitre", "microlitres", "cc",
            "g", "gm", "gram", "grams", "kg",
            "h", "hr", "hrs", "hour", "hours", "min", "mins", "minute", "minutes",
            "sec", "secs", "day", "days", "wk", "wks", "week", "weeks",
            "m2", "m\u00B2", "cm2", "cm\u00B2",
            "act", "actuation", "actuations", "dose", "doses", "puff", "puffs",
            "spray", "sprays", "inh", "inhalation", "inhalations",
            "patch", "patches", "drop", "drops", "gtt",
            "tablet", "tablets", "tab", "tabs",
            "capsule", "capsules", "cap", "caps",
            "caplet", "caplets", "pill", "pills",
            "lozenge", "lozenges", "troche", "troches",
            "suppository", "suppositories", "supp", "supps",
            "pellet", "pellets", "granule", "granules",
            "film", "films", "strip", "strips", "wafer", "wafers", "gum", "gums",
            "insert", "inserts", "implant", "implants", "disk", "disks", "ring", "rings", "sponge", "sponges",
            "vial", "vials", "amp", "amps", "ampoule", "ampoules", "ampule", "ampules",
            "syringe", "syringes", "sachet", "sachets", "packet", "packets",
            "blister", "blisters", "cartridge", "cartridges", "pen", "pens",
            "bottle", "bottles", "tube", "tubes", "pouch", "pouches",
            "scoop", "scoops", "applicator", "applicators",
            "tsp", "tbsp", "teaspoon", "teaspoons", "tablespoon", "tablespoons");

    /**
     * Reads the leading strength from stored dosage text. For a combination it returns the first
     * component, and for a concentration, delivery rate, or per-dosage-form strength it keeps units
     * such as {@code mg/5 mL}, {@code mg / 5 g}, {@code mcg / hr}, {@code mg/capsule}, or
     * {@code mg/2 tablets} intact. A leading number followed by another number before the unit is
     * ambiguous, for example {@code 1,000 mg}, {@code 1½ mg}, or {@code 5-10 mg}, and returns
     * {@code (null, null)}. Unit-only text that begins with a unit (such as {@code mg/5mL}) is
     * preserved as {@code (null, unit)} rather than {@code (null, null)}.
     *
     * @param dosage String stored strength text, which may be {@code null}
     * @return DrugStrength parsed strength where {@code (null, null)} represents an unknown or
     *     ambiguous strength and {@code (null, unit)} preserves unit-only text
     */
    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
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
        int unitStart = unitMatch.start(1);
        int unitEnd = unitMatch.end(1);
        String baseUnit = text.substring(unitStart, unitEnd);
        if ("%".equals(baseUnit)) {
            Matcher basis = PERCENT_BASIS.matcher(text).region(unitEnd, text.length());
            if (basis.lookingAt()) {
                unitEnd = basis.end();
            }
        }
        while (unitEnd < text.length()) {
            Matcher separator = PER_DENOMINATOR.matcher(text).region(unitEnd, text.length());
            if (!separator.lookingAt()) {
                break;
            }
            int amountStart = separator.end();
            int amountEnd = DoseText.numberEnd(text, amountStart);
            Matcher denominator = DENOMINATOR_TOKEN.matcher(text).region(amountEnd, text.length());
            if (!denominator.lookingAt()) {
                break;
            }
            String denomUnit = denominator.group(1);
            if (!DENOMINATOR_UNITS.contains(denomUnit.toLowerCase(Locale.ROOT))) {
                break;
            }
            // Same-unit mass pairs with a second amount (e.g., "4g/0.5g") name a second ingredient.
            if (amountEnd > amountStart && isSameMassUnit(baseUnit, denomUnit)) {
                break;
            }
            unitEnd = denominator.end();
        }
        return new DrugStrength(strength, text.substring(unitStart, unitEnd));
    }

    // FindSecBugs IMPROPER_UNICODE: case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision. See docs/static-analysis-workflows.md
    @SuppressFBWarnings(value = "IMPROPER_UNICODE", justification = "case-insensitive comparison of an internal/domain value (status/flag/enum/MIME/code); not a security or authorization decision")
    private static boolean isSameMassUnit(String baseUnit, String denomUnit) {
        String base = baseUnit.toLowerCase(Locale.ROOT);
        String denom = denomUnit.toLowerCase(Locale.ROOT);
        return (GRAM_UNITS.contains(base) && GRAM_UNITS.contains(denom))
                || ("kg".equals(base) && "kg".equals(denom));
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

    /**
     * Formats the supplied strength fields for storage, omitting whichever field is missing:
     * returns {@code ""} for {@code (null, null)}, the unit text for {@code (null, unit)},
     * the plain decimal for {@code (amount, null)} or an empty unit, and {@code "<amount> <unit>"}
     * when both are present.
     *
     * @return String formatted dosage text, never {@code null}
     */
    public String dosageText() {
        if (amount == null) {
            return unit == null ? "" : unit;
        }
        String number = DoseText.of(amount.floatValue());
        return unit == null || unit.isEmpty() ? number : number + " " + unit;
    }
}
