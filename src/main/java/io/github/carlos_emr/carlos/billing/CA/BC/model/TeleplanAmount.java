/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.billing.CA.BC.model;

import java.math.BigDecimal;

/**
 * Exact BC monetary parsing, including Teleplan's implied cents and negative overpunch suffixes.
 *
 * @since 2026-11-02
 */
public final class TeleplanAmount {
    /** Parses stored decimal dollars in one pass; invalid syntax and exponent scales stay unknown. */
    public static BigDecimal knownDecimal(String source) {
        if (source == null || source.isEmpty()) return null;
        int index = source.charAt(0) == '+' || source.charAt(0) == '-' ? 1 : 0;
        boolean hasDigit = false;
        boolean hasPoint = false;
        for (; index < source.length(); index++) {
            char value = source.charAt(index);
            if (value >= '0' && value <= '9') {
                hasDigit = true;
            } else if (value == '.' && !hasPoint) {
                hasPoint = true;
            } else {
                return null;
            }
        }
        return hasDigit ? new BigDecimal(source) : null;
    }

    private TeleplanAmount() {
    }

    /**
     * Decodes signed, implied-cents amounts without double rounding or input logging.
     *
     * @param value String integer cents, optionally padded or ending in a negative }JKLMNOPQR overpunch
     * @return String plain decimal CAD with two places; null or blank inputs become 0.00
     * @throws NumberFormatException if the amount is not numeric after decoding
     */
    public static String decode(String value) {
        if (value == null || value.isBlank()) return "0.00";
        value = value.strip();
        String negativeDigits = "}JKLMNOPQR";
        int last = negativeDigits.indexOf(value.charAt(value.length() - 1));
        String digits = last < 0 ? value : "-" + value.substring(0, value.length() - 1) + last;
        if (!digits.matches("-?\\d+")) throw new NumberFormatException("Invalid Teleplan amount");
        return new BigDecimal(digits).movePointLeft(2).setScale(2).toPlainString();
    }

    /**
     * How a report shows a stored field that is not a Teleplan amount: this label and the field's
     * source text. The BC remittance CSV exports and PDF reports share the wording.
     */
    public static final String UNPARSED_LABEL = "Unparsed Teleplan amount: ";

    /**
     * Decodes a stored amount for a report that must still render when one field is corrupt. A PDF
     * template calls this instead of {@link #decode(String)}, whose exception would fail the whole
     * document for one bad row.
     *
     * @param value String stored Teleplan amount field
     * @return BigDecimal exact dollars; {@code 0.00} for a null or blank field, which is what Teleplan
     *         sends for a line with no amount (a held or unpaid claim); null when the field is not a
     *         Teleplan amount. Never throws.
     */
    public static BigDecimal known(String value) {
        try {
            return new BigDecimal(decode(value));
        } catch (NumberFormatException invalid) {
            return null;
        }
    }

    /**
     * Tells a malformed field from a real zero, so a report never counts the one as the other.
     *
     * @param value String stored Teleplan amount field
     * @return boolean true when the field is not blank and is not a Teleplan amount
     */
    public static boolean isUnparsed(String value) {
        return known(value) == null;
    }

    /**
     * The amount a report total may add: totals are of known amounts only, and
     * {@link #unparsedCount(String...)} says how many fields were left out.
     *
     * @param value String stored Teleplan amount field
     * @return BigDecimal the decoded dollars, or zero for a field that is not a Teleplan amount
     */
    public static BigDecimal knownOrZero(String value) {
        BigDecimal amount = known(value);
        return amount == null ? BigDecimal.ZERO.setScale(2) : amount;
    }

    /**
     * Counts the fields a report total left out.
     *
     * @param values String stored Teleplan amount fields of one row
     * @return int how many of them are not Teleplan amounts
     */
    public static int unparsedCount(String... values) {
        int count = 0;
        for (String value : values) {
            if (isUnparsed(value)) count++;
        }
        return count;
    }

    /**
     * Text of an amount cell that is wide enough to wrap the label.
     *
     * @param value String stored Teleplan amount field
     * @return String {@code $12.34}, or the label and the field's source text when it is not an amount
     */
    public static String dollars(String value) {
        BigDecimal amount = known(value);
        return amount == null ? UNPARSED_LABEL + value : "$" + amount.toPlainString();
    }

    /**
     * What a report prints in an amount cell too narrow for the label. The cell must not show the
     * source text alone ({@code 12.5} would read as money), so the row's note line carries it:
     * see {@link #unparsedNote(String...)}.
     */
    public static final String UNPARSED_MARK = "?";

    /**
     * Text of a narrow amount cell.
     *
     * @param value String stored Teleplan amount field
     * @return String {@code $12.34}, or {@link #UNPARSED_MARK} when the field is not a Teleplan amount
     */
    public static String dollarsOrMark(String value) {
        BigDecimal amount = known(value);
        return amount == null ? UNPARSED_MARK : "$" + amount.toPlainString();
    }

    /**
     * The line a report prints under a row whose narrow amount cells cannot hold the label.
     *
     * @param labelsAndValues String pairs of a column label and its stored field; an empty label is
     *                        left out, for a row with one amount column
     * @return String the label followed by each malformed field's column and source text, separated
     *         by semicolons; null when every field of the row is an amount or blank
     */
    public static String unparsedNote(String... labelsAndValues) {
        StringBuilder note = new StringBuilder();
        for (int pair = 0; pair + 1 < labelsAndValues.length; pair += 2) {
            String label = labelsAndValues[pair];
            String value = labelsAndValues[pair + 1];
            if (!isUnparsed(value)) continue;
            note.append(note.isEmpty() ? UNPARSED_LABEL : "; ");
            if (label != null && !label.isEmpty()) note.append(label).append(' ');
            note.append(value);
        }
        return note.isEmpty() ? null : note.toString();
    }

}
