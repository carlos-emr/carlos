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
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

@Tag("unit")
@Tag("cds-export")
class CdsExportCareOccurrenceServiceUnitTest {
    private static final String PATH = "/OmdCds[1]/PatientRecord[1]/CareElements[1]/Weight[1]";
    private Map<String, String> observation;
    private List<CdsExportCapturedTextRowDto> extensions;
    private CdsCareElementDto prepared;

    @BeforeEach
    void setup() {
        observation =
                new HashMap<>(
                        Map.of(
                                "id",
                                "7",
                                "demographicNo",
                                "27",
                                "type",
                                "WT",
                                "dataField",
                                "75",
                                "measuringInstruction",
                                "in kg",
                                "comments",
                                "",
                                "dateObserved",
                                "2020-02-28 05:00:00.0",
                                "appointmentNo",
                                "0"));
        prepared =
                new CdsCareElementDto(
                        "Weight",
                        "WT",
                        "75",
                        "kg",
                        null,
                        CdsTemporal.parse(null, null, "2020-02-28", null, "Date"),
                        false,
                        false,
                        null,
                        List.of());
        extensions = new ArrayList<>();
        extension("cdsCareElement", "Weight");
        extension("date_precision", "FULL_DATE");
        extension("cdsSourceDate", "2020-02-28");
        extension("cdsSourcePath", PATH);
        extension("unit", "kg");
    }

    private void extension(String key, String value) {
        var fields = new HashMap<String, String>();
        fields.put("id", Integer.toString(extensions.size() + 20));
        fields.put("measurement_id", "7");
        fields.put("keyval", key);
        fields.put("val", value);
        extensions.add(
                new CdsExportCapturedTextRowDto(
                        "measurementsExt",
                        CdsExportPatientTableSourceReader.key(extensions.size() + 20),
                        fields));
    }

    private void bind() throws IOException {
        CdsExportCareOccurrenceService.bind(
                27,
                new CdsExportCapturedTextRowDto(
                        "measurements", CdsExportPatientTableSourceReader.key(7), observation),
                extensions,
                prepared,
                PATH);
    }

    @Test
    void shouldRequireTheWholeSealedObservation_andAllTemporalAndUnitMetadata() {
        assertThatCode(this::bind).doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(
            strings = {"demographicNo", "type", "dataField", "measuringInstruction", "comments"})
    void shouldRefuseChangedNativeFields_insteadOfReplayingSource(String column) {
        observation.put(column, "Changed");
        assertThatThrownBy(this::bind).isInstanceOf(IOException.class);
    }

    @Test
    void shouldRefuseMissingNativeDate_forCareOccurrenceServiceContract() {
        observation.put("dateObserved", null);
        assertThatThrownBy(this::bind).isInstanceOf(IOException.class);
    }

    @Test
    void shouldRefuseAddedAppointmentContext_forCareOccurrenceServiceContract() {
        observation.put("appointmentNo", "91");
        assertThatThrownBy(this::bind).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRefuseMissingPrecisionMetadata_forCareOccurrenceServiceContract() {
        extensions.removeIf(row -> row.fields().get("keyval").equals("date_precision"));
        assertThatThrownBy(this::bind).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRefuseUnknownExtraMetadata_forCareOccurrenceServiceContract() {
        extension("unmappedClinicalContext", "Additional context");
        assertThatThrownBy(this::bind).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRefuseDuplicateMetadata_forCareOccurrenceServiceContract() {
        extension("unit", "kg");
        assertThatThrownBy(this::bind).isInstanceOf(IOException.class);
    }

    @Test
    void shouldRefuseNullDuplicateMetadata_beforeAnOtherwiseCorrectValue() {
        var last = extensions.removeLast();
        extension("unit", null);
        extensions.add(last);
        assertThatThrownBy(this::bind).isInstanceOf(IOException.class);
    }

    @Test
    void shouldRefuseForeignObservationExtension_forCareOccurrenceServiceContract() {
        var row = extensions.getFirst();
        var f = new HashMap<>(row.fields());
        f.put("measurement_id", "8");
        extensions.set(0, new CdsExportCapturedTextRowDto(row.table(), row.recordKey(), f));
        assertThatThrownBy(this::bind).isInstanceOf(IOException.class);
    }

    @Test
    void shouldRefuseChangedSourcePathAssociation_forCareOccurrenceServiceContract() {
        extensions.removeIf(row -> row.fields().get("keyval").equals("cdsSourcePath"));
        extension("cdsSourcePath", PATH.replace("Weight[1]", "Weight[2]"));
        assertThatThrownBy(this::bind).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldKeepEntryProviderAsLocalProvenance_withoutInventingASourceAuthor() {
        observation.put("providerNo", "999998");
        assertThatCode(this::bind).doesNotThrowAnyException();
    }

    @Test
    void shouldRequireOriginalRecordedShift_whenPresentInPreparation() {
        prepared =
                new CdsCareElementDto(
                        "Weight",
                        "WT",
                        "75",
                        "kg",
                        null,
                        CdsTemporal.shift(prepared.date(), 2, "Date"),
                        false,
                        false,
                        null,
                        List.of());
        assertThatThrownBy(this::bind).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
        extension("cdsShiftDays", "2");
        assertThatCode(this::bind).doesNotThrowAnyException();
    }
}
