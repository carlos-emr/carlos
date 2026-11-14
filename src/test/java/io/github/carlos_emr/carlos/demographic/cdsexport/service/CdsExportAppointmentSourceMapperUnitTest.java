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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportAppointmentLookupsDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The per-appointment NewCategory carrier CARLOS.Appointment.Source.v1. */
@Tag("unit")
@Tag("cds-export")
class CdsExportAppointmentSourceMapperUnitTest {
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void shouldCarryTheCompleteStoredRowItsStatusDefinitionsAndPublicProvider_inFixedOrder()
            throws Exception {
        var fields = CdsExportAppointmentMapperUnitTest.baseline();
        fields.put("location", "Main Clinic");
        fields.put("remarks", null);
        var row = CdsExportAppointmentMapperUnitTest.appointment(fields);
        var lookups = CdsExportAppointmentMapperUnitTest.lookups();
        var source =
                CdsExportAppointmentSourceMapper.map(
                        3, row, lookups, Map.of("AppointmentNotes", "complete text"));
        // xs:token cannot alter the content: no literal whitespace remains.
        assertThat(source.json()).doesNotContain(" ").doesNotContain("\n").doesNotContain("\t");
        var json = JSON.readTree(source.json());
        var names = new java.util.ArrayList<String>();
        json.fieldNames().forEachRemaining(names::add);
        assertThat(names)
                .containsExactly(
                        "version",
                        "appointment",
                        "fields",
                        "complete",
                        "statusDefinitions",
                        "publicProvider");
        assertThat(json.path("version").asInt()).isEqualTo(1);
        assertThat(json.path("appointment").asLong()).isEqualTo(3);
        assertThat(json.path("fields").size()).isEqualTo(row.fields().size());
        assertThat(json.path("fields").path("location").asText()).isEqualTo("Main Clinic");
        // SQL NULL stays distinct from empty text.
        assertThat(json.path("fields").path("remarks").isNull()).isTrue();
        assertThat(json.path("complete").path("AppointmentNotes").asText())
                .isEqualTo("complete text");
        assertThat(json.path("statusDefinitions")).hasSize(1);
        assertThat(json.path("statusDefinitions").get(0).path("status").asText())
                .isEqualTo(fields.get("status"));
        assertThat(json.path("publicProvider").path("provider_no").asText())
                .isEqualTo(fields.get("provider_no"));
        // Every column of every carried row is decided, and decided as carried here.
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(source.rows(), source.fields(), 1_000_000, () -> {});
        assertThat(source.rows()).hasSize(3);
        assertThat(source.fields().values())
                .allMatch(
                        decision ->
                                decision.outcome() == Outcome.MAPPED
                                        && decision.destination()
                                                .equals("NewCategory/ResidualInfo"));
    }

    @Test
    void shouldCarryTextNoXmlDocumentCanHold_asJsonEscapes() throws Exception {
        var fields = CdsExportAppointmentMapperUnitTest.baseline();
        String stored = "a\u0001b￾c￿d e";
        fields.put("notes", stored);
        fields.put("status", null);
        fields.put("provider_no", "GONE");
        var source =
                CdsExportAppointmentSourceMapper.map(
                        1,
                        CdsExportAppointmentMapperUnitTest.appointment(fields),
                        new CdsExportAppointmentLookupsDto(
                                "a".repeat(64), Map.of(), Map.of(), Map.of(), Set.of()),
                        Map.of());
        assertThat(CdsExportNativeFieldAssessments.legal(source.json(), Integer.MAX_VALUE))
                .isTrue();
        var json = JSON.readTree(source.json());
        assertThat(json.path("fields").path("notes").asText()).isEqualTo(stored);
        assertThat(json.path("statusDefinitions")).isEmpty();
        assertThat(json.path("publicProvider").isNull()).isTrue();
    }

    @Test
    void shouldRefuseARowThatIsNotAnAppointment_orAPositionBeforeTheFirstElement() {
        var row =
                CdsExportAppointmentMapperUnitTest.appointment(
                        CdsExportAppointmentMapperUnitTest.baseline());
        var lookups = CdsExportAppointmentMapperUnitTest.lookups();
        assertThatThrownBy(() -> CdsExportAppointmentSourceMapper.map(0, row, lookups, Map.of()))
                .hasMessage("CDS_EXPORT_APPOINTMENT_SOURCE_SCOPE_INVALID");
        var other = new CdsExportCapturedTextRowDto("provider", row.recordKey(), row.fields());
        assertThatThrownBy(() -> CdsExportAppointmentSourceMapper.map(1, other, lookups, Map.of()))
                .hasMessage("CDS_EXPORT_APPOINTMENT_SOURCE_SCOPE_INVALID");
    }
}
