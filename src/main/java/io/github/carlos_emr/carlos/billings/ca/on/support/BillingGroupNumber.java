/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.support;

import java.util.regex.Pattern;

/**
 * The Ontario MOH billing group number as the OHIP claim file carries it:
 * four upper-case letters or digits, {@code 0000} for a solo provider.
 *
 * <p>The provider record stores the group number as free text
 * ({@code <xml_p_billinggroup_no>} in {@code provider.comments}); OSCAR 19
 * constrained that field at entry ({@code maxlength="4"},
 * {@code pattern="[A-Z\d]{4}"}), and every consumer downstream of it assumed
 * the stored value already had the MOH shape. CARLOS reads that field through
 * {@link #normalize(String)} instead, so one key serves the batch header, the
 * disk file name and the batch counter lookup: a short all-digit value such as
 * {@code 123} (a leading zero lost on entry or import) becomes {@code 0123},
 * stray whitespace and lower case are repaired, and nothing else is guessed.
 * A value that is still not well formed after normalization is reported per
 * provider by the generation flow rather than silently mapped to an empty
 * group, which is what produced the colliding {@code H<month>.001} file names
 * of issue #4277.</p>
 *
 * @since 2026-10-08
 */
public final class BillingGroupNumber {

    /** The MOH group number of a provider who bills solo. */
    public static final String SOLO = "0000";

    /** Fixed width of the group number field in the OHIP batch header. */
    public static final int LENGTH = 4;

    /** ASCII only on purpose: full-width digits must not pass as a group number. */
    private static final Pattern WELL_FORMED = Pattern.compile("[A-Z0-9]{" + LENGTH + "}");
    private static final Pattern SHORT_NUMERIC = Pattern.compile("[0-9]{1," + (LENGTH - 1) + "}");

    private BillingGroupNumber() {
    }

    /**
     * Normalizes a stored group number to the MOH form where that is
     * unambiguous.
     *
     * <ul>
     *   <li>{@code null}, blank and the legacy literal {@code "null"} are the
     *       solo group {@link #SOLO}.</li>
     *   <li>Surrounding whitespace is removed and ASCII letters are
     *       upper-cased.</li>
     *   <li>One to three ASCII digits are left-padded with zeros.</li>
     * </ul>
     *
     * <p>Anything else is returned as trimmed and upper-cased; callers decide
     * with {@link #isWellFormed(String)} whether it can be submitted.</p>
     *
     * @param raw the stored value, possibly {@code null}
     * @return the normalized value, never {@code null}
     */
    public static String normalize(String raw) {
        if (raw == null) {
            return SOLO;
        }
        String value = asciiUpperCase(raw.strip());
        if (value.isEmpty() || "null".equals(raw.strip())) {
            return SOLO;
        }
        if (SHORT_NUMERIC.matcher(value).matches()) {
            return "0".repeat(LENGTH - value.length()) + value;
        }
        return value;
    }

    /**
     * Whether a (normalized) value has the shape the OHIP batch header needs.
     *
     * @param groupNo the value to check, normally the result of
     *                {@link #normalize(String)}
     * @return {@code true} for exactly four ASCII upper-case letters or digits
     */
    public static boolean isWellFormed(String groupNo) {
        return groupNo != null && WELL_FORMED.matcher(groupNo).matches();
    }

    /**
     * Whether a stored value denotes solo billing once normalized.
     *
     * @param raw the stored value, possibly {@code null}
     * @return {@code true} when the provider has no billing group
     */
    public static boolean isSolo(String raw) {
        return SOLO.equals(normalize(raw));
    }

    // Locale-free by construction: only ASCII a-z is folded, which is all the
    // MOH alphabet contains, so no case-folding detector (IMPROPER_UNICODE)
    // and no Turkish-locale surprise applies here.
    private static String asciiUpperCase(String value) {
        StringBuilder out = new StringBuilder(value.length());
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            out.append(c >= 'a' && c <= 'z' ? (char) (c - ('a' - 'A')) : c);
        }
        return out.toString();
    }
}
