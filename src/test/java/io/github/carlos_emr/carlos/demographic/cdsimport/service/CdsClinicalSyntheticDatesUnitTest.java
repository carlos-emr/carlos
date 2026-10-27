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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsTemporalDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsClinicalException;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import static org.junit.jupiter.api.Assertions.*;

@Tag("unit")
class CdsClinicalSyntheticDatesUnitTest {
    @TempDir Path directory;
    private final CdsClinicalNormalizationService service = new CdsClinicalNormalizationService(new CdsFieldParser());

    @Test void directWireDateAndDateTimeAreRecognizedWithoutInventingChoiceChildren() throws Exception {
        var record = CdsClinicalFixture.record(directory, "Appointments",
                Map.of("AppointmentDate", "2020-02-29", "DirectTime", "2020-02-29T10:15:00-05:00"));
        assertEquals(LocalDate.of(2020, 2, 29), service.date(record, "AppointmentDate").earliest());
        assertEquals(CdsTemporalDto.Precision.OFFSET_DATE_TIME, service.date(record, "DirectTime").precision());
        var shifted = service.date(record, "AppointmentDate", command(2));
        assertEquals(LocalDate.of(2020, 3, 2), shifted.earliest());
        assertEquals("2020-02-29", shifted.original());
        assertEquals(2, shifted.appliedShiftDays());
    }
    @Test void medicationAllergyAndImmunizationDatesAllUseSyntheticCopyPolicy() throws Exception {
        var medication = CdsClinicalFixture.record(directory, "MedicationsAndTreatments", Map.of(
                "DrugName", "Source drug", "StartDate/FullDate", "2020-02-28",
                "PrescriptionWrittenDate/FullDateTime", "2020-02-28T15:30:00-05:00"));
        var rx = service.medication(medication, command(2));
        assertEquals(LocalDate.of(2020, 3, 1), rx.start().earliest());
        assertEquals(LocalDate.of(2020, 3, 1), rx.written().earliest());
        assertEquals("-05:00", rx.written().offsetDateTime().getOffset().toString());
        assertEquals(15, rx.written().offsetDateTime().getHour());
        assertEquals("2020-02-28", rx.start().original());
        var allergy = CdsClinicalFixture.record(directory, "AllergiesAndAdverseReactions", Map.of(
                "OffendingAgentDescription", "Source agent", "StartDate/FullDate", "2020-02-28",
                "RecordedDate/FullDate", "2020-02-29"));
        var reaction = service.allergy(allergy, command(2));
        assertEquals(LocalDate.of(2020, 3, 1), reaction.start().earliest());
        assertEquals(LocalDate.of(2020, 3, 2), reaction.recorded().earliest());
        var immunization = CdsClinicalFixture.record(directory, "Immunizations", Map.of(
                "ImmunizationName", "Td", "RefusedFlag/ynIndicatorsimple", "N", "Date/FullDate", "2020-02-28"));
        var vaccine = service.immunization(immunization, command(2));
        assertEquals(LocalDate.of(2020, 3, 1), vaccine.date().earliest());
        assertFalse(vaccine.refused());
        assertEquals(2, vaccine.date().appliedShiftDays());
    }
    @Test void nonTeachingDateCallsRemainUnshifted() throws Exception {
        var record = CdsClinicalFixture.record(directory, "CareElements", Map.of("Date", "2020-01-01"));
        assertEquals(LocalDate.of(2020, 1, 1), service.date(record, "Date").earliest());
        assertEquals(0, service.date(record, "Date").appliedShiftDays());
    }
    @Test void syntheticUnknownTimingRemainsUnknown() throws Exception {
        var record = CdsClinicalFixture.record(directory, "Immunizations", Map.of(
                "ImmunizationName", "Td", "RefusedFlag/ynIndicatorsimple", "N"));
        var result = service.immunization(record, command(-365)).date();
        assertEquals(CdsTemporalDto.Precision.UNKNOWN, result.precision());
        assertNull(result.earliest()); assertEquals(0, result.appliedShiftDays());
    }
    @Test void partialDateShiftRequiresReviewRatherThanRelabelingAnArbitraryIntervalAsAYear() throws Exception {
        var record = CdsClinicalFixture.record(directory, "Immunizations", Map.of("Date/YearOnly", "2020"));
        var failure = assertThrows(CdsClinicalException.class, () -> service.date(record, "Date", command(2)));
        assertEquals("CDS_SYNTHETIC_PARTIAL_DATE_SHIFT_REQUIRES_REVIEW", failure.code());
        assertEquals("YYYY", service.date(record, "Date", command(0)).legacyPartialFormat());
    }
    @Test void conflictingDirectAndStructuredDateRequireReview() throws Exception {
        var record = CdsClinicalFixture.record(directory, "CareElements", Map.of(
                "Date", "2020-01-01", "Date/FullDate", "2020-01-02"));
        assertThrows(CdsClinicalException.class, () -> service.date(record, "Date", command(1)));
    }
    @Test void sourceCannotBeShiftedTwiceOrPastNativeCalendar() {
        var date = CdsTemporal.parse(null, null, "2020-01-01", null, "Date");
        var shifted = CdsTemporal.shift(date, 2, "Date");
        assertThrows(CdsClinicalException.class, () -> CdsTemporal.shift(shifted, 2, "Date"));
        var end = CdsTemporal.parse(null, null, "9999-12-31", null, "Date");
        assertThrows(CdsClinicalException.class, () -> CdsTemporal.shift(end, 1, "Date"));
    }
    private CdsImportCommand command(int days) {
        return new CdsImportCommand(directory, "synthetic-date-test", "999998", null, null,
                "synthetic-fixture", "cds-5.2", false, false, false, days, 1, true);
    }
}
