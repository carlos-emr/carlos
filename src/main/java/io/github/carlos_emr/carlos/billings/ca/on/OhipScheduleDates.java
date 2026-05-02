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
package io.github.carlos_emr.carlos.billings.ca.on;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * OHIP Schedule of Benefits date normalizers that include file-format
 * sentinels not shared by the generic Ontario billing date utility.
 */
public final class OhipScheduleDates {
    private static final DateTimeFormatter SERVICE_DATE = DateTimeFormatter.ISO_LOCAL_DATE;

    private OhipScheduleDates() {
    }

    public static String terminationDate(String raw) {
        if ("99999999".equals(raw)) {
            return "9999-12-31";
        }
        return parseOhipDate(raw, true).format(SERVICE_DATE);
    }

    private static LocalDate parseOhipDate(String raw, boolean normalizeZeroDay) {
        String value = raw == null ? "" : raw.trim();
        if (!value.matches("\\d{8}")) {
            throw new IllegalArgumentException("Expected OHIP date in yyyyMMdd format: " + raw);
        }

        int year = Integer.parseInt(value.substring(0, 4));
        int month = Integer.parseInt(value.substring(4, 6));
        int day = Integer.parseInt(value.substring(6, 8));
        if (normalizeZeroDay && day == 0) {
            day = 1;
        }
        return LocalDate.of(year, month, day);
    }
}
