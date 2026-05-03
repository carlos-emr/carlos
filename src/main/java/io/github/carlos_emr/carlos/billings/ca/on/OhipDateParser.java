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

import java.time.DateTimeException;
import java.time.LocalDate;

import io.github.carlos_emr.carlos.utility.LogSanitizer;

/**
 * Package-private parser for OHIP {@code yyyyMMdd} date tokens shared by
 * Ontario billing date utilities and Schedule of Benefits import logic.
 *
 * <p>Zero-day tokens are policy-controlled. Live billing dates reject day
 * {@code 00} because there is no valid service date to submit. Schedule of
 * Benefits extracts may use day {@code 00} as a month-level effective marker;
 * that import path normalizes it to the first day of the month so fee lookups
 * have a concrete lower bound.</p>
 *
 * @since 2026-05-01
 */
final class OhipDateParser {

    /** Controls whether OHIP {@code yyyyMM00} values are rejected or normalized. */
    enum ZeroDayPolicy {
        REJECT_ZERO_DAY,
        NORMALIZE_ZERO_DAY_TO_FIRST
    }

    private OhipDateParser() {
    }

    static LocalDate parse(String raw, ZeroDayPolicy zeroDayPolicy) {
        String value = raw == null ? "" : raw.trim();
        if (!value.matches("\\d{8}")) {
            throw new IllegalArgumentException("Expected OHIP date in yyyyMMdd format: "
                    + LogSanitizer.sanitizeForDisplay(raw));
        }

        int year = Integer.parseInt(value.substring(0, 4));
        int month = Integer.parseInt(value.substring(4, 6));
        int day = Integer.parseInt(value.substring(6, 8));
        if (zeroDayPolicy == ZeroDayPolicy.NORMALIZE_ZERO_DAY_TO_FIRST && day == 0) {
            day = 1;
        }
        try {
            return LocalDate.of(year, month, day);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Invalid OHIP date in yyyyMMdd format: "
                    + LogSanitizer.sanitizeForDisplay(raw), e);
        }
    }
}
