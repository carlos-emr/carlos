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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsDemographicDetailsDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedFieldDto;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.StringReader;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportRetainedEnrolmentSourceUnitTest {
    private static final String RUN = "11111111-1111-4111-8111-111111111111",
            ROOT = "/OmdCds[1]/PatientRecord[1]/Demographics[1]/",
            HASH = "a".repeat(64);
    private final List<CdsExportRetainedFieldDto> fields = new ArrayList<>();
    private final CdsExportRetainedSectionWriter.Values values =
            mock(CdsExportRetainedSectionWriter.Values.class);
    private CdsDemographicDetailsDto preparation;
    private int shift;

    @BeforeEach
    void setup() throws Exception {
        fields.clear();
        shift = 0;
        history(1, "0", "2018-01-02", "2019-01-02");
        history(2, "1", "2020-01-02", null);
        prepare("RO", "2020-01-02", null, 2);
        when(values.text(any()))
                .thenAnswer(
                        call -> {
                            CdsExportRetainedFieldDto field = call.getArgument(0);
                            return new StringReader(
                                    field.logicalInline() == null
                                            ? field.originalInline()
                                            : field.logicalInline());
                        });
    }

    private void prepare(String status, String start, String end, long count) {
        preparation =
                new CdsDemographicDetailsDto(
                        null,
                        null,
                        null,
                        null,
                        new CdsDemographicDetailsDto.Enrollment(status, start, end, null, null),
                        count,
                        List.of());
    }

    private CdsExportRetainedEnrolmentSource.Result read() throws Exception {
        var options =
                new CdsExportRetainedImportOptionsReader.Options(
                        RUN, HASH, HASH, HASH, "omd-5.2", shift != 0, shift);
        return new CdsExportRetainedEnrolmentSource()
                .read(fields, values, options, preparation, () -> {});
    }

    private void history(int ordinal, String status, String start, String end) {
        add(ordinal, "EnrollmentStatus", status);
        add(ordinal, "EnrollmentDate", start);
        if (end != null) add(ordinal, "EnrollmentTerminationDate", end);
    }

    private void add(int ordinal, String leaf, String value) {
        String path =
                "Enrolment[1]/EnrolmentHistory["
                        + ordinal
                        + "]/"
                        + Arrays.stream(leaf.split("/"))
                                .map(part -> part + "[1]")
                                .collect(java.util.stream.Collectors.joining("/"));
        var type =
                CdsWireSchema.schemaModel()
                        .element("cds", "OmdCds")
                        .child("cds", "PatientRecord")
                        .child("cds", "Demographics");
        String namespace = "cds";
        for (String segment : path.split("/")) {
            String name = segment.substring(0, segment.indexOf('['));
            var candidate =
                    type.childNames().stream()
                            .filter(child -> child.local().equals(name))
                            .findFirst()
                            .orElseThrow();
            namespace = candidate.namespace();
            type = type.child(namespace, name);
        }
        fields.add(
                new CdsExportRetainedFieldDto(
                        CdsImportKeys.hash(path, Integer.toString(fields.size())),
                        RUN,
                        HASH,
                        HASH,
                        CdsCategory.DEMOGRAPHICS,
                        ROOT + path,
                        1,
                        namespace,
                        "{" + type.getTypeNamespace() + "}" + type.getTypeName(),
                        null,
                        value,
                        value,
                        null));
    }

    @Test
    void shouldPreserveEveryOriginalHistory_whenTheImportUsedNoShift() throws Exception {
        var result = read();
        assertThat(result.histories()).hasSize(2);
        assertThat(result.sourceFields()).hasSize(5);
        assertThat(result.selectedOrdinal()).isEqualTo(2);
        assertThat(result.changes()).isEmpty();
    }

    @Test
    void shouldShiftEveryHistoricalDate_fromTheVerifiedOriginalOptions() throws Exception {
        shift = 365;
        prepare("RO", "2021-01-01", null, 2);
        var result = read();
        assertThat(result.histories().getFirst().start()).hasToString("2019-01-02");
        assertThat(result.histories().getFirst().end()).hasToString("2020-01-02");
        assertThat(result.histories().getLast().start()).hasToString("2021-01-01");
        assertThat(result.changes()).hasSize(3);
        var output = mock(CdsExportXmlComposer.ContentWriter.class);
        result.changes().getFirst().content().write(output);
        verify(output).text("2019-01-02");
    }

    @Test
    void shouldSupportNegativeShifts_withoutInferringThemFromTheLatestRoster() throws Exception {
        shift = -365;
        prepare("RO", "2019-01-02", null, 2);
        assertThat(read().histories().getFirst().start()).hasToString("2017-01-02");
    }

    @Test
    void shouldKeepSourceOffsets_whenApplyingTheOriginalShift() throws Exception {
        fields.clear();
        history(1, "1", "2020-01-02-05:00", null);
        shift = 365;
        prepare("RO", "2021-01-01", null, 1);
        var result = read();
        var output = mock(CdsExportXmlComposer.ContentWriter.class);
        result.changes().getFirst().content().write(output);
        verify(output).text("2021-01-01-05:00");
    }

    @Test
    void shouldUseAcceptedLogicalCorrections_beforeApplyingTheOriginalShift() throws Exception {
        fields.clear();
        history(1, "1", "2020-01-02", null);
        var f = fields.getLast();
        fields.set(
                fields.size() - 1,
                new CdsExportRetainedFieldDto(
                        f.id(),
                        f.runId(),
                        f.sourcePatientKey(),
                        f.fieldUnitKey(),
                        f.category(),
                        f.sourcePath(),
                        f.occurrence(),
                        f.namespace(),
                        f.valueType(),
                        f.precision(),
                        f.originalInline(),
                        "2022-03-01",
                        null));
        shift = 365;
        prepare("RO", "2023-03-01", null, 1);
        assertThat(read().histories().getFirst().start()).hasToString("2023-03-01");
    }

    @Test
    void shouldRejectMissingSourceHistories_whenTheSealedPreparationRetainsTheirCount() {
        fields.subList(0, 3).clear();
        assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_ENROLMENT_SOURCE_COUNT_MISMATCH");
    }

    @Test
    void shouldRefuseASelectionMismatch_withTheSealedNativePreparation() {
        prepare("RO", "2022-01-01", null, 2);
        assertThatThrownBy(this::read)
                .hasMessage("CDS_EXPORT_ENROLMENT_SOURCE_PREPARATION_MISMATCH");
    }

    @Test
    void shouldRejectConflictingLatestEvents_insteadOfChoosingOneByIterationOrder() {
        fields.clear();
        history(1, "1", "2020-01-02", null);
        history(2, "0", "2018-01-02", "2020-01-02");
        assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_ENROLMENT_SOURCE_AMBIGUOUS_HISTORY");
    }

    @Test
    void shouldKeepIdenticalLatestDuplicates_asSeparateSourceOccurrences() throws Exception {
        fields.clear();
        history(1, "1", "2020-01-02", null);
        history(2, "1", "2020-01-02", null);
        assertThat(read().histories()).hasSize(2);
    }

    @Test
    void shouldAllowEarlierTiedHistory_whenThereIsAnUnambiguousLaterEvent() throws Exception {
        fields.clear();
        history(1, "1", "2019-01-02", null);
        history(2, "0", "2018-01-02", "2019-01-02");
        history(3, "1", "2020-01-02", null);
        prepare("RO", "2020-01-02", null, 3);
        assertThat(read().selectedOrdinal()).isEqualTo(3);
    }

    @Test
    void shouldPreserveUnprojectedPhysicianIdentity_inTheRetainedSource() throws Exception {
        add(1, "EnrolledToPhysician/Name/FirstName", "Historical");
        add(1, "EnrolledToPhysician/Name/LastName", "Physician");
        add(1, "EnrolledToPhysician/OHIPPhysicianId", "000123");
        var result = read();
        assertThat(result.sourceFields()).hasSize(8);
        assertThat(result.histories().getFirst().values())
                .containsEntry("EnrolledToPhysician/OHIPPhysicianId", "000123");
    }

    @Test
    void shouldRejectDuplicateSourcePaths_evenWithDistinctFieldIds() {
        add(2, "EnrollmentStatus", "1");
        assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_ENROLMENT_SOURCE_INVALID");
    }

    @Test
    void shouldRejectAHistoricalGap_insteadOfClaimingCompleteSourceCoverage() {
        fields.clear();
        history(2, "1", "2020-01-02", null);
        prepare("RO", "2020-01-02", null, 1);
        assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_ENROLMENT_SOURCE_INVALID");
    }

    @Test
    void shouldRejectMissingOrConflictingConditionalDates_underTheMigrationRules() {
        for (String[] item :
                List.of(
                        new String[] {"0", "2020-01-02", null},
                        new String[] {"1", "2020-01-02", "2021-01-02"},
                        new String[] {"0", "2021-01-02", "2020-01-02"})) {
            fields.clear();
            history(1, item[0], item[1], item[2]);
            prepare("RO", "2020-01-02", null, 1);
            assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_ENROLMENT_SOURCE_INVALID");
        }
    }

    @Test
    void shouldRejectOverflowAndMalformedDates_withoutReturningPartialChanges() {
        fields.clear();
        history(1, "1", "9999-12-31", null);
        shift = 1;
        prepare("RO", "2020-01-02", null, 1);
        assertThatThrownBy(this::read).isInstanceOf(java.io.IOException.class);
    }

    @Test
    void shouldKeepAnAbsentSourceEnrolment_absentWhenThePreparationHasNoRoster() throws Exception {
        fields.clear();
        preparation = CdsDemographicDetailsDto.empty();
        assertThat(read().histories()).isEmpty();
        assertThat(read().selectedOrdinal()).isZero();
    }
}
