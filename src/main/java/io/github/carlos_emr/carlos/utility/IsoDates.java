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
package io.github.carlos_emr.carlos.utility;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;

/** Preserves the legacy Ant ISO date parser's leniency, prefix parsing and JVM defaults. */
public final class IsoDates {
    public static final String ISO8601_DATE_PATTERN = "yyyy-MM-dd";

    private IsoDates() {
    }

    /** Strict API date boundary; the legacy parser below remains available to other callers. */
    public static Date parseStrictDate(String value) throws ParseException {
        try {
            if (value == null || !value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) {
                throw new java.time.DateTimeException("Expected date-only value");
            }
            return Date.from(java.time.LocalDate.parse(value).atStartOfDay(
                    java.time.ZoneId.systemDefault()).toInstant());
        } catch (java.time.DateTimeException invalid) {
            throw new ParseException("Date must use yyyy-MM-dd", 0);
        }
    }

    /**
     * Creates a formatter per call so changes to the default locale/time zone still apply.
     * Trailing text and dates outside their nominal month/day ranges remain accepted.
     */
    public static Date parseIso8601Date(String value) throws ParseException {
        return new SimpleDateFormat(ISO8601_DATE_PATTERN).parse(value);
    }
}
