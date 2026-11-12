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
package io.github.carlos_emr.carlos.demographic.cdsexport.dto;

import java.time.LocalDate;
import java.util.Set;

/** One evidenced enrolment event. Archive update times never supply its clinical dates. */
public record CdsExportEnrolmentEventDto(String status, LocalDate start, LocalDate end,
        String terminationReason, CdsExportPublicProviderDto physician) {
    // CT-010 in both supplied OntarioMD migration dictionaries and the pinned wire schema.
    public static final Set<String> TERMINATION_REASONS = Set.of("12", "14", "24", "30", "32", "33", "35", "36",
            "37", "38", "39", "40", "41", "42", "44", "51", "53", "54", "56", "57", "59", "60", "61", "62",
            "73", "74", "82", "84", "90", "91");
    public CdsExportEnrolmentEventDto {
        if (!("1".equals(status) || "0".equals(status)) || !date(start)
                || end != null && (!date(end) || end.isBefore(start))
                || "1".equals(status) && (end != null || terminationReason != null)
                || "0".equals(status) && end == null
                || terminationReason != null && !TERMINATION_REASONS.contains(terminationReason)
                || physician != null && physician.cpsoNumber() != null) {
            throw new IllegalArgumentException("CDS_EXPORT_ENROLMENT_EVENT_INVALID");
        }
    }
    private static boolean date(LocalDate value) { return value != null && value.getYear() > 0 && value.getYear() <= 9999; }
    @Override public String toString() { return "CdsExportEnrolmentEventDto[protected]"; }
}
