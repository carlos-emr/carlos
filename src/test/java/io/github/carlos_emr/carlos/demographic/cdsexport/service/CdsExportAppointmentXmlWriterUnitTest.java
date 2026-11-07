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

import static org.assertj.core.api.Assertions.*;
import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.*;
import java.io.*;
import java.nio.file.*;
import java.time.ZoneId;
import java.util.*;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit") @Tag("cds-export")
class CdsExportAppointmentXmlWriterUnitTest {
    @TempDir Path root;
    @Test void shouldKeepTimeMinutesProviderAndText_throughThePinnedSchemaAndActualReceivingParser() throws Exception {
        var fields = new LinkedHashMap<String, String>(); CdsExportDemographicRowDto.COLUMNS.forEach(column -> fields.put(column, null));
        fields.putAll(Map.of("first_name", "Synthetic", "last_name", "Fixture", "year_of_birth", "1980", "month_of_birth", "01", "date_of_birth", "01", "patient_status", "AC", "sex", "U"));
        var demographic = new CdsExportDemographicMapper().map(new CdsExportDemographicRowDto(27, fields));
        var mapped = new CdsExportAppointmentMapper().map(27,
                CdsExportAppointmentMapperUnitTest.appointment(CdsExportAppointmentMapperUnitTest.baseline()),
                CdsExportAppointmentMapperUnitTest.lookups(), ZoneId.of("America/Toronto"));
        assertThat(mapped.nativeRowRepresentable()).isTrue(); var appointment = mapped.value();
        var sections = List.of(new CdsExportXmlComposer.Section(new CdsExportIdentityDto("a".repeat(64), CdsCategory.DEMOGRAPHICS, "b".repeat(64)),
                        xml -> new CdsExportDemographicXmlWriter().write(demographic, xml)),
                new CdsExportXmlComposer.Section(new CdsExportIdentityDto("a".repeat(64), CdsCategory.APPOINTMENTS, "c".repeat(64)),
                        xml -> new CdsExportAppointmentXmlWriter().write(appointment, xml)));
        var output = new ByteArrayOutputStream(); var categories = Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.APPOINTMENTS);
        new CdsExportXmlComposer().compose(output, "d".repeat(64), "a".repeat(64), "2026.08.0-test", CdsLogicalProfile.ONTARIO_MD_5_2,
                categories, sections.iterator(), ignored -> {}, 1L << 20, () -> {});
        new CdsExportXmlValidator().validate(new ByteArrayInputStream(output.toByteArray()), "27", categories, "2026.08.0-test",
                CdsLogicalProfile.ONTARIO_MD_5_2, CdsExportXmlLimitsDto.defaults(), ignored -> {}, () -> {});
        var file = Files.write(root.resolve("patient.xml"), output.toByteArray()); var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser().parse(file, root.resolve("parsed"), CdsInputLimitsDto.defaults(), () -> false, records::add);
        var record = records.get(1); var receiving = new CdsFieldParser();
        assertThat(record.section()).isEqualTo("Appointments");
        for (var expected : Map.of("AppointmentTime", "09:00:30-05:00", "Duration", "75", "AppointmentStatus", "Exact booked Ω", "AppointmentDate/FullDate", "2024-02-29",
                "Provider/Name/FirstName", "Zoë😀", "Provider/Name/LastName", "D’Arcy", "Provider/OHIPPhysicianId", "001234", "AppointmentPurpose", "Exact purpose",
                "AppointmentNotes", "Literal café 😀\r\nnotes").entrySet()) {
            assertThat(receiving.firstText(record, expected.getKey(), 32768)).isEqualTo(expected.getValue());
        }
    }
    @Test void dictionaryNoteLimitIsStricterThanTheBroaderWireSchemaWithoutChangingImportLimits()throws Exception {
        var timing=new CdsExportAppointmentTimingMapper().map("2024-02-29", "09:00:00", "10:00:00", ZoneId.of("America/Toronto"));
        assertThat(new CdsExportAppointmentDto(timing, null, null, null, "😀".repeat(32000)).notes().codePointCount(0, 64000)).isEqualTo(32000);
        for (int length:List.of(32001, 32768))assertThatThrownBy(()-> new CdsExportAppointmentDto(timing, null, null, null, "x".repeat(length))).hasMessage("CDS_EXPORT_APPOINTMENT_VALUES_REQUIRES_REVIEW");
    }
    @Test void anUnboundSourceClockCannotBeSerializedWithAnInventedOffset() {
        var date=new CdsExportDateDto(CdsExportDateDto.Precision.DATE, "2024-02-29"); var timing=new CdsExportAppointmentTimingDto(date, "09:00:00", 60); var value=new CdsExportAppointmentDto(timing, null, null, null, null);
        assertThatThrownBy(()-> CdsExportAppointmentXmlWriter.validate(value)).hasMessage("CDS_EXPORT_APPOINTMENT_TIMING_REQUIRES_REVIEW");
    }
    @Test void shouldRefuseCapacityAndTokenLoss_insteadOfTrimmingOrTruncating() throws Exception {
        var timing = new CdsExportAppointmentTimingMapper().map("2024-02-29", "09:00:00", "10:00:00", ZoneId.of("America/Toronto"));
        assertThatThrownBy(() -> new CdsExportAppointmentDto(timing, null, null, "x".repeat(251), null))
                .hasMessage("CDS_EXPORT_APPOINTMENT_VALUES_REQUIRES_REVIEW");
        assertThatThrownBy(() -> new CdsExportPublicProviderDto("x".repeat(51), "Last", null)).hasMessage("CDS_EXPORT_PROVIDER_VALUES_REQUIRES_REVIEW");
        assertThatThrownBy(() -> new CdsExportPublicProviderDto("First", "Last", " 01234")).hasMessage("CDS_EXPORT_PROVIDER_VALUES_REQUIRES_REVIEW");
    }
}
