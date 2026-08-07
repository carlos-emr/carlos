/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * Now maintained by the CARLOS EMR Project (2026+).
 * https://github.com/carlos-emr/carlos
 * CARLOS has no affiliation with OSCAR or McMaster University.
 */
package io.github.carlos_emr.carlos.utility;

/**
 * Shared formatting utilities for DTO display methods. Centralizes the
 * "LastName, FirstName" and "YYYY-MM-DD" formatting logic used across
 * Provider and Demographic DTOs.
 *
 * @since 2026-04-11
 */
public final class DtoFormatUtils {

    private DtoFormatUtils() {
    }

    /**
     * Formats a name as "LastName, FirstName", handling nulls gracefully.
     *
     * @param lastName String the last name (may be null)
     * @param firstName String the first name (may be null)
     * @param fallback String the value to return when both names are null
     * @return String the formatted name, or fallback if both names are null
     */
    public static String formatName(String lastName, String firstName, String fallback) {
        String trimmedLast = trimToNull(lastName);
        String trimmedFirst = trimToNull(firstName);
        if (trimmedLast == null && trimmedFirst == null) {
            return fallback;
        }
        StringBuilder sb = new StringBuilder();
        if (trimmedLast != null) {
            sb.append(trimmedLast);
        }
        if (trimmedFirst != null) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(trimmedFirst);
        }
        return sb.toString();
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    /**
     * Formats a date of birth from separate components as "YYYY-MM-DD".
     * Returns empty string if any component is null or blank.
     *
     * @param yearOfBirth String the year component (may be null)
     * @param monthOfBirth String the month component (may be null)
     * @param dateOfBirth String the day component (may be null)
     * @return String the formatted date, or empty string if any component is missing
     */
    public static String formatDob(String yearOfBirth, String monthOfBirth, String dateOfBirth) {
        if (yearOfBirth == null || yearOfBirth.trim().isEmpty()
                || monthOfBirth == null || monthOfBirth.trim().isEmpty()
                || dateOfBirth == null || dateOfBirth.trim().isEmpty()) {
            return "";
        }
        return yearOfBirth.trim() + "-" + monthOfBirth.trim() + "-" + dateOfBirth.trim();
    }
}
