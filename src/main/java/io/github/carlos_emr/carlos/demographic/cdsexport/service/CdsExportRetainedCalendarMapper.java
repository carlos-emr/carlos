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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import java.time.LocalDate;
import java.io.IOException;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/** Shared effective calendar handling for imported dates and later native edits. */
final class CdsExportRetainedCalendarMapper {
    enum Rule {
        UNCHANGED_SOURCE,
        PREPARED_EFFECTIVE,
        EQUIVALENT_NATIVE_CALENDAR,
        CURRENT_NATIVE,
        EDITED_SOURCE_OFFSET_REQUIRES_REVIEW
    }

    record Decision(String lexical, Rule rule) {
        @Override
        public String toString() {
            return "CdsExportRetainedCalendarMapper.Decision[protected]";
        }
    }

    Decision map(
            String source, LocalDate prepared, LocalDate current, boolean unchanged, String path)
            throws IOException {
        Objects.requireNonNull(source);
        Objects.requireNonNull(prepared);
        Objects.requireNonNull(current);
        var original = CdsTemporal.parse(null, null, source, null, path);
        if (current.equals(prepared)) {
            int days = Math.toIntExact(ChronoUnit.DAYS.between(original.earliest(), prepared));
            var effective =
                    new CdsExportPreparedTemporalMapper()
                            .map(CdsTemporal.shift(original, days, path), false);
            return new Decision(
                    effective.lexical(),
                    days != 0
                            ? Rule.PREPARED_EFFECTIVE
                            : unchanged ? Rule.UNCHANGED_SOURCE : Rule.EQUIVALENT_NATIVE_CALENDAR);
        }
        return source.length() != 10
                ? new Decision(null, Rule.EDITED_SOURCE_OFFSET_REQUIRES_REVIEW)
                : new Decision(current.toString(), Rule.CURRENT_NATIVE);
    }
}
