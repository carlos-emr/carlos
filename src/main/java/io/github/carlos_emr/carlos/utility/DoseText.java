/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.utility;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    /**
     * A number with a fraction inside a longer text. The look-behind starts a match only at the
     * first digit of a run and the quantifiers are possessive, so the scan is linear in the text.
     */
    private static final Pattern DECIMAL_IN_TEXT = Pattern.compile("(?<!\\d)\\d++\\.\\d++");

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
        } catch (NumberFormatException notOneNumber) {
            return text;
        }
        // long arithmetic: precision and scale are ints, and a scale near Integer.MIN_VALUE
        // ("1E2147483647") would overflow their difference.
        long integerDigits = (long) value.precision() - value.scale();
        if (integerDigits > MAX_PLAIN_DIGITS || value.scale() > MAX_PLAIN_DIGITS) {
            return text;
        }
        return of(value);
    }

    /**
     * Rewrites every decimal number inside a text as its plain decimal, for comparing two texts
     * that may differ only in how a number was written.
     *
     * <p>Until #6185 the product strength was stored with DrugRef's trailing zero in the generic
     * name and the dosage of a prescription ("amoxicillin 250.0mg capsule", "250.0mg"); since then
     * it is stored without ("amoxicillin 250mg capsule", "250mg"). Both name the same product, and
     * both give the same text here. Different strengths stay different: "250mg", "25mg" and
     * "2.5mg" are unchanged.
     *
     * <p>This is for comparison. Text shown to a person is shown as it is stored.
     *
     * @param text the text, which may be {@code null}
     * @return the text with each number that has a fraction rewritten by
     *     {@link #ofNumericText(String)}; {@code null} for {@code null}
     */
    public static String withPlainNumbers(String text) {
        if (text == null) {
            return null;
        }
        Matcher number = DECIMAL_IN_TEXT.matcher(text);
        if (!number.find()) {
            return text;
        }
        StringBuilder plain = new StringBuilder(text.length());
        int copied = 0;
        do {
            plain.append(text, copied, number.start()).append(ofNumericText(number.group()));
            copied = number.end();
        } while (number.find());
        return plain.append(text, copied, text.length()).toString();
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
