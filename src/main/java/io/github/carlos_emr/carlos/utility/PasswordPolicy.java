/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.CarlosProperties;

/**
 * Shared configured password-complexity checks for account creation and forced resets.
 * Required values and confirmation are checked by each request handler before this policy.
 * @since 2026-10-03
 */
public final class PasswordPolicy {
    private PasswordPolicy() { }

    /** Default for {@code password_min_length} when the property is absent or malformed. */
    private static final int DEFAULT_POLICY_MIN_LENGTH = 8;
    /** Default for {@code password_min_groups} when the property is absent or malformed. */
    private static final int DEFAULT_POLICY_MIN_GROUPS = 3;
    /** Default for {@code password_group_lower_chars} when the property is absent. */
    private static final String DEFAULT_POLICY_LOWER_CHARS = "abcdefghijklmnopqrstuvwxyz";
    /** Default for {@code password_group_upper_chars} when the property is absent. */
    private static final String DEFAULT_POLICY_UPPER_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ";
    /** Default for {@code password_group_digits} when the property is absent. */
    private static final String DEFAULT_POLICY_DIGIT_CHARS = "0123456789";
    /** Default for {@code password_group_special} when the property is absent. */
    private static final String DEFAULT_POLICY_SPECIAL_CHARS = "! @#$%^&*()_+|~-=`{}[]\\:\";'<>?,./";

    /**
     * Evaluates the clinic's length and character-group requirements without storing credentials.
     * The explicit IGNORE_PASSWORD_REQUIREMENTS setting bypasses complexity checks only.
     * @param password candidate password
     * @param properties clinic configuration
     * @return policy decision with localization keys and a non-sensitive audit reason
     */
    public static Validation validate(String password, CarlosProperties properties) {
        if (Boolean.parseBoolean(properties.getProperty("IGNORE_PASSWORD_REQUIREMENTS"))) {
            return new Validation(null, 0, null, null);
        }
        int length = intProperty(properties, "password_min_length", DEFAULT_POLICY_MIN_LENGTH);
        if (password == null || password.length() < length) {
            return new Validation("password.policy.violation.msgPasswordLengthError", length,
                    "password.policy.violation.msgSymbols", "password_policy_min_length");
        }
        int groups = intProperty(properties, "password_min_groups", DEFAULT_POLICY_MIN_GROUPS);
        if (countGroups(password,
                properties.getProperty("password_group_lower_chars", DEFAULT_POLICY_LOWER_CHARS),
                properties.getProperty("password_group_upper_chars", DEFAULT_POLICY_UPPER_CHARS),
                properties.getProperty("password_group_digits", DEFAULT_POLICY_DIGIT_CHARS),
                properties.getProperty("password_group_special", DEFAULT_POLICY_SPECIAL_CHARS)) < groups) {
            return new Validation("password.policy.violation.msgPasswordStrengthError", groups,
                    "password.policy.violation.msgPasswordGroups", "password_policy_min_groups");
        }
        return new Validation(null, 0, null, null);
    }

    /**
     * A policy decision containing no password or other credential data.
     * @param messageKey localized error prefix, or null for acceptance
     * @param minimum required length or number of groups
     * @param unitMessageKey localized unit following the minimum
     * @param auditReason stable rejection code, or null for acceptance
     */
    public record Validation(String messageKey, int minimum, String unitMessageKey, String auditReason) {
        public boolean isValid() { return auditReason == null; }
    }

    /**
     * Counts configured groups represented by at least one character; groups may overlap.
     * @param password candidate, with null and empty candidates using zero groups
     * @param lowerChars lowercase group characters
     * @param upperChars uppercase group characters
     * @param digitChars numeric group characters
     * @param specialChars special group characters
     * @return number of represented groups, from zero through four
     */
    public static int countGroups(String password, String lowerChars, String upperChars, String digitChars,
                                   String specialChars) {
        if (password == null || password.isEmpty()) {
            return 0;
        }

        boolean lower = false;
        boolean upper = false;
        boolean digit = false;
        boolean special = false;
        for (int i = 0; i < password.length(); i++) {
            char ch = password.charAt(i);
            if (!lower && containsChar(lowerChars, ch)) {
                lower = true;
            }
            if (!upper && containsChar(upperChars, ch)) {
                upper = true;
            }
            if (!digit && containsChar(digitChars, ch)) {
                digit = true;
            }
            if (!special && containsChar(specialChars, ch)) {
                special = true;
            }
        }

        int groups = 0;
        if (lower) {
            groups++;
        }
        if (upper) {
            groups++;
        }
        if (digit) {
            groups++;
        }
        if (special) {
            groups++;
        }
        return groups;
    }

    private static boolean containsChar(String chars, char ch) {
        return chars != null && chars.indexOf(ch) >= 0;
    }

    /**
     * Reads an integer password-policy property with a safe fallback.
     *
     * <p>Misconfigured policy values should not make password changes impossible. Invalid values
     * are logged for operators and the conservative application default remains in force.</p>
     */
    private static int intProperty(CarlosProperties properties, String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value == null) {
            return defaultValue;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException _) {
            MiscUtils.getLogger().warn("Invalid integer property {}={}, using default {}", key, LogSafe.sanitize(value), // NOSONAR javasecurity:S5145 - sanitized with LogSafe
                    defaultValue);
            return defaultValue;
        }
    }

}
