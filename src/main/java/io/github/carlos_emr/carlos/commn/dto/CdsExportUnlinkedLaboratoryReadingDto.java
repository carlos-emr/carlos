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
package io.github.carlos_emr.carlos.commn.dto;

import java.util.Objects;

/**
 * One patient's laboratory readings that are not exported because the HL7 report they were filed
 * from is no longer routed to the chart (owner decision 2026-11-14): an HL7 result
 * {@code Hl7textResultsData.fileMeasurements} filed as a measurement whose {@code lab_no} names no
 * HL7 report of the patient, for example after the lab was unlinked or moved to another patient.
 * They are counted by test name and observation date only; no result value is read. The
 * encrypted package ReadMe lists them for the patient.
 *
 * @param testName the reading's test name as filed (its {@code name} extension), empty when it
 *     has none
 * @param observed the date the reading was observed ({@code yyyy-MM-dd}), empty when none is
 *     stored
 * @param readings how many such readings share the test name and date
 * @since 2026-11-14
 */
public record CdsExportUnlinkedLaboratoryReadingDto(String testName, String observed, long readings) {
    public CdsExportUnlinkedLaboratoryReadingDto {
        Objects.requireNonNull(testName, "testName");
        Objects.requireNonNull(observed, "observed");
        if (readings < 1 || !observed.isEmpty() && !observed.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}"))
            throw new IllegalArgumentException("CDS_EXPORT_UNLINKED_LABORATORY_READING_INVALID");
    }

    @Override
    public String toString() {
        return "CdsExportUnlinkedLaboratoryReadingDto[protected]";
    }
}
