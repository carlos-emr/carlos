/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.utility;

import java.math.BigDecimal;

/**
 * Turns a dose, strength or quantity number into the text a person reads.
 *
 * <p>ISMP Canada's <i>Do Not Use</i> list bans two numeric forms in medication text: a trailing
 * zero ("1.0 mg" is read as 10 mg) and a missing leading zero (".5 mg" is read as 5 mg). Java's own
 * conversions produce the first for every whole number: {@code Float.toString},
 * {@code Double.toString}, {@code String.valueOf(float)} and {@code "" + dose} all print
 * {@code 1.0}, and print {@code 1.0E7} for ten million. Every method here writes the exact plain
 * decimal instead (#6185):
 *
 * <ul>
 *   <li>no trailing zero ({@code 1}, never {@code 1.0});</li>
 *   <li>always a leading zero ({@code 0.5}, never {@code .5});</li>
 *   <li>no exponent ({@code 10000000}, never {@code 1.0E7});</li>
 *   <li>no digit grouping and a dot as the decimal separator, whatever the server's locale;</li>
 *   <li>no rounding: a stored {@code 0.0625} is written as {@code 0.0625}.</li>
 * </ul>
 *
 * <p>Use this class wherever such a number becomes text, rather than formatting it again at the
 * call site. Text a prescriber typed (the instruction line, a free-text strength) is not a number
 * and does not belong here: it is shown as written.
 *
 * @since 2026-11-26
 */
public final class DoseText {

    /** The longest text {@link #ofNumericText(String)} reads as a number. */
    private static final int MAX_NUMERIC_TEXT_LENGTH = 64;

    /** The most digits {@link #ofNumericText(String)} writes on either side of the decimal point. */
    private static final int MAX_PLAIN_DIGITS = 64;

    private DoseText() {
    }

    /**
     * Writes a dose held as a {@code float}, the type of the {@code drugs.takemin} and
     * {@code drugs.takemax} columns.
     *
     * <p>The text is the shortest decimal that identifies the stored float, so a dose typed as
     * {@code 0.1} is written as {@code 0.1} and not as the float's binary expansion
     * ({@code 0.100000001490116...}). It reads back to the same float. A dose that has no finite
     * decimal form, such as a third of a tablet, is written to the float's full precision
     * ({@code 0.33333334}).
     *
     * @param value the stored dose
     * @return the plain decimal; {@code NaN}, {@code Infinity} or {@code -Infinity} for a value that
     *     is not a finite number, so that it is never mistaken for a dose
     */
    public static String of(float value) {
        if (!Float.isFinite(value)) {
            return Float.toString(value);
        }
        return of(new BigDecimal(Float.toString(value)));
    }

    /**
     * Writes a strength or quantity held as a {@code double}, the type DrugRef serves an
     * ingredient strength in.
     *
     * <p>Pass a {@code float} to {@link #of(float)} instead of widening it: a widened
     * {@code 0.1f} is {@code 0.10000000149011612} as a double, and would be written that way.
     *
     * @param value the number
     * @return the plain decimal; {@code NaN}, {@code Infinity} or {@code -Infinity} for a value that
     *     is not a finite number
     */
    public static String of(double value) {
        if (!Double.isFinite(value)) {
            return Double.toString(value);
        }
        return of(new BigDecimal(Double.toString(value)));
    }

    /**
     * Writes an exact decimal.
     *
     * @param value the number; never {@code null}, because a missing dose must not print as the
     *     word "null" or as zero without the caller deciding so
     * @return the plain decimal
     * @throws NullPointerException if {@code value} is {@code null}
     */
    public static String of(BigDecimal value) {
        // Zero is special-cased: it carries a scale of its own ("0E+3", "0.00"), and the result
        // must be the single digit whatever that scale is.
        if (value.signum() == 0) {
            return "0";
        }
        return value.stripTrailingZeros().toPlainString();
    }

    /**
     * Rewrites a number that arrives as text, and leaves any other text alone.
     *
     * <p>Some numbers reach CARLOS already written out: DrugRef sends an ingredient strength as
     * the text of a float ("250.0" for 250 mg), and a prescriber types a quantity. When the text
     * is one decimal number it is rewritten as that number's plain decimal ("250", "0.5" for
     * ".5", "10000000" for "1.0E7"). Text that is anything else is returned exactly as given: a
     * strength with its unit ("2.5 mg/mL"), a fraction ("1/2"), a range ("1-2"), grouped digits
     * ("1,000"), a word, blank text or {@code null}.
     *
     * <p>A number longer than {@value #MAX_NUMERIC_TEXT_LENGTH} characters, or with more than
     * {@value #MAX_PLAIN_DIGITS} digits on either side of the decimal point, is not a dose and is
     * also returned as given. "1E999999999" is a valid decimal whose plain form is a billion
     * digits; text from another system must not be able to make the server write it out.
     *
     * @param text the text, which may be {@code null}
     * @return the plain decimal when {@code text} is one decimal number, otherwise {@code text}
     */
    public static String ofNumericText(String text) {
        if (text == null) {
            return null;
        }
        String candidate = text.strip();
        if (candidate.isEmpty() || candidate.length() > MAX_NUMERIC_TEXT_LENGTH) {
            return text;
        }
        BigDecimal value;
        try {
            value = new BigDecimal(candidate);
        } catch (NumberFormatException _) {
            // Not one number: a strength with its unit, a fraction, a range, a word.
            return text;
        }
        if (value.signum() == 0) {
            return "0";
        }
        // Reject extreme positive exponents before stripTrailingZeros() so subtracting stripped
        // zeros from a scale near Integer.MIN_VALUE cannot underflow int.
        if (value.scale() < -MAX_PLAIN_DIGITS) {
            return text;
        }
        BigDecimal normalized = value.stripTrailingZeros();
        // long arithmetic: precision and scale are ints, and a scale near Integer.MIN_VALUE
        // ("1E2147483647") would overflow their difference.
        long integerDigits = (long) normalized.precision() - normalized.scale();
        if (integerDigits > MAX_PLAIN_DIGITS || normalized.scale() > MAX_PLAIN_DIGITS) {
            return text;
        }
        return normalized.toPlainString();
    }

    /**
     * Rewrites every decimal number inside a text as its plain decimal, for comparing two texts
     * that may differ only in how a number was written.
     *
     * <p>Until #6185 the product strength was stored with DrugRef's trailing zero in the generic
     * name and the dosage of a prescription ("amoxicillin 250.0mg capsule", "250.0mg"), or without
     * a leading zero (".5mg"); since then it is stored as a plain decimal ("250mg", "0.5mg"). Both
     * name the same product, and both give the same text here, as do "1.0E7unit" and "10000000unit".
     * Different strengths stay different: "250mg", "25mg" and "2.5mg" are unchanged.
     *
     * <p>This is for comparison. Text shown to a person is shown as it is stored.
     *
     * @param text the text, which may be {@code null}
     * @return the text with each number that has a fraction or exponent rewritten by
     *     {@link #ofNumericText(String)}; {@code null} for {@code null}
     */
    public static String withPlainNumbers(String text) {
        if (text == null) {
            return null;
        }
        // A plain scan rather than a pattern: each character is read once, so the cost is linear
        // in the text whatever it holds (a pattern here has to be argued free of backtracking).
        StringBuilder plain = null;
        int copied = 0;
        int at = 0;
        while (at < text.length()) {
            int end = decimalEnd(text, at);
            if (end == at) {
                // No number with a fraction or exponent starts here. Step over this character, or over the
                // whole run of digits, so that no number is read from the middle of a longer one.
                at = Math.max(at + 1, digitsEnd(text, at));
            } else if (isFractionAt(text, end)) {
                // Multi-dot token (e.g. "1.0.5", "12.50.0", "1.5E3.20"): leave the entire dotted run alone.
                at = end;
                while (isFractionAt(text, at)) {
                    at = exponentEnd(text, digitsEnd(text, at + 1));
                }
            } else {
                if (plain == null) {
                    plain = new StringBuilder(text.length());
                }
                plain.append(text, copied, at).append(ofNumericText(text.substring(at, end)));
                copied = end;
                at = end;
            }
        }
        return plain == null ? text : plain.append(text, copied, text.length()).toString();
    }

    /**
     * The index after a number with a fraction or exponent that starts at {@code index}
     * ("250.0", ".5", "1.0E7", "1E7"). Returns {@code index} for text that starts with a
     * non-number, a whole number that needs no decimal normalization, or a comma-grouped
     * continuation ("1,000.0").
     */
    private static int decimalEnd(String text, int index) {
        int integerEnd = digitsEnd(text, index);
        if (integerEnd == index) {
            if (isFractionAt(text, index) && canStartLeadingFraction(text, index)) {
                return exponentEnd(text, digitsEnd(text, index + 1));
            }
            return index;
        }
        if (isPartOfGroupedOrDottedToken(text, index)) {
            return index;
        }
        if (isFractionAt(text, integerEnd)) {
            return exponentEnd(text, digitsEnd(text, integerEnd + 1));
        }
        int end = exponentEnd(text, integerEnd);
        return end == integerEnd ? index : end;
    }

    /** Whether digits at {@code index} continue a multi-dot ("1.0.5") or comma-grouped ("1,000.0") number. */
    private static boolean isPartOfGroupedOrDottedToken(String text, int index) {
        if (index == 0) {
            return false;
        }
        char previous = text.charAt(index - 1);
        return previous == '.' || (previous == ',' && index >= 2 && isDigit(text, index - 2));
    }

    /** A leading decimal point starts a number only when not attached to a word, digit or dot. */
    private static boolean canStartLeadingFraction(String text, int index) {
        if (index == 0) {
            return true;
        }
        int before = text.codePointBefore(index);
        return !Character.isLetterOrDigit(before) && before != '.';
    }

    /**
     * Where the number that starts at {@code start} ends, for reading a number out of stored text.
     *
     * <p>A number is digits with an optional fraction ("250", "2.5"), or a fraction written
     * without its leading zero (".5"), followed by the exponent a float's own text may carry
     * ("1.0E7", "1.0E-4"). An "e" with no digits after it is not an exponent: "5 each" and "5e"
     * end after the 5. A decimal point with no digit after it is not part of the number.
     *
     * @param text the text to read; never {@code null}
     * @param start the index to read from, from 0 to the length of the text
     * @return the index after the number, or {@code start} when no number starts there
     */
    public static int numberEnd(String text, int start) {
        int end = digitsEnd(text, start);
        if (isFractionAt(text, end)) {
            end = digitsEnd(text, end + 1);
        }
        return end == start ? start : exponentEnd(text, end);
    }

    /** Whether the character at {@code index} exists and is one of 0 to 9. */
    private static boolean isDigit(String text, int index) {
        return index < text.length() && text.charAt(index) >= '0' && text.charAt(index) <= '9';
    }

    /** The index after the run of digits that starts at {@code index}. */
    private static int digitsEnd(String text, int index) {
        int end = index;
        while (isDigit(text, end)) {
            end++;
        }
        return end;
    }

    /** Whether a decimal point with a digit after it is at {@code index}. */
    private static boolean isFractionAt(String text, int index) {
        return index < text.length() && text.charAt(index) == '.' && isDigit(text, index + 1);
    }

    /**
     * The index after the exponent that starts at {@code index} ("E7", "e-4", "E+7"), or
     * {@code index} itself when no complete exponent is there: an "e" with no digits after it is
     * the start of a word ("2.5 each"), not part of the number.
     */
    private static int exponentEnd(String text, int index) {
        if (index >= text.length() || (text.charAt(index) != 'e' && text.charAt(index) != 'E')) {
            return index;
        }
        int digits = index + 1;
        if (digits < text.length() && (text.charAt(digits) == '+' || text.charAt(digits) == '-')) {
            digits++;
        }
        return isDigit(text, digits) ? digitsEnd(text, digits) : index;
    }

    /**
     * Writes a dose range such as "take 1-2 tablets".
     *
     * <p>The bounds are written in the order they are stored. A legacy row can hold a low bound
     * above the high one; sorting them here would hide that from the reader.
     *
     * @param low the stored lower bound ({@code takemin})
     * @param high the stored upper bound ({@code takemax})
     * @return {@code low-high}, or the single dose when both bounds are the same number
     */
    public static String range(float low, float high) {
        // Compare the numbers, not the text: NaN is never equal to itself, and such a pair is
        // written as a range so that neither bound is dropped.
        if (low == high) {
            return of(low);
        }
        return of(low) + "-" + of(high);
    }
}
