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
package io.github.carlos_emr.carlos.util;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.time.temporal.ChronoField;
import java.util.Locale;
import java.util.Date;

/** Converts native SQL scalars at boundaries that still use legacy JDBC date values. */
// Legacy DAO/view-model contracts require Date; java.time inputs are adapted only at this boundary.
@SuppressWarnings("java:S2143")
public final class NativeQueryValues {
    private static final DateTimeFormatter JDBC_TIMESTAMP = new DateTimeFormatterBuilder()
            .appendPattern("uuuu-MM-dd HH:mm:ss")
            .appendFraction(ChronoField.NANO_OF_SECOND, 1, 9, true)
            .toFormatter(Locale.ROOT);

    private NativeQueryValues() { }

    /**
     * Keeps database wall-clock values unchanged when adapting Hibernate 7 java.time
     * results to legacy Date consumers. SQL NULL stays null; unsupported types fail explicitly.
     */
    public static Date asDate(Object value) {
        if (value == null) return null;
        if (value instanceof Date date) return date;
        if (value instanceof LocalDateTime dateTime) return Timestamp.valueOf(dateTime);
        if (value instanceof LocalDate date) return java.sql.Date.valueOf(date);
        throw new IllegalArgumentException("Unsupported native SQL date type: " + value.getClass().getName());
    }

    /** Preserves JDBC's space-separated timestamp text and SQL NULL for legacy parsers. */
    public static String asString(Object value) {
        if (value == null) return null;
        if (value instanceof LocalDateTime dateTime) return JDBC_TIMESTAMP.format(dateTime);
        return value.toString();
    }
}
