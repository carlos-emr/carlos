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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsTemporal;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsWireSchema;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportHistoryRetainedSourceUnitTest {
    private static final String RUN = "11111111-1111-4111-8111-111111111111", HASH = "a".repeat(64);
    private final List<CdsExportRetainedFieldDto> fields = new ArrayList<>();
    private CdsExportRetainedSectionWriter.Values values;
    private CdsCategory category;
    private CdsHistoryNativeDto prepared;
    private int shift;

    @BeforeEach
    void setup() throws Exception {
        fields.clear();
        shift = 0;
        category = CdsCategory.FAMILY_HISTORY;
        values = mock(CdsExportRetainedSectionWriter.Values.class);
        when(values.text(any()))
                .thenAnswer(
                        call -> {
                            CdsExportRetainedFieldDto field = call.getArgument(0);
                            return new StringReader(
                                    field.logicalInline() == null
                                            ? field.originalInline()
                                            : field.logicalInline());
                        });
        add("ProblemDiagnosisProcedureDescription[1]", "Exact <clinical> & literal text");
        add("StartDate[1]/FullDate[1]", "2020-02-28");
        prepare("Exact <clinical> & literal text", "StartDate", temporal("2020-02-28", false));
    }

    @Test
    void shouldRetainStructuredHistoryFields_whenTheirNativeBodyAndObservationAgree()
            throws Exception {
        add("Relationship[1]", "Mother");
        add("Notes[1]", "A separate source note");
        var result = read();
        assertThat(result.sourceFields()).hasSize(4);
        assertThat(result.changes()).isEmpty();
        assertThat(result.dates().get("StartDate[1]").lexical()).isEqualTo("2020-02-28");
    }

    @Test
    void shouldShiftEveryPastHealthDate_andPreferTheOriginalOnsetProjection() throws Exception {
        fields.clear();
        category = CdsCategory.PAST_HEALTH;
        shift = 2;
        add("PastHealthProblemDescriptionOrProcedures[1]", "Procedure");
        add("OnsetOrEventDate[1]/FullDate[1]", "2020-02-28");
        add("ResolvedDate[1]/FullDate[1]", "2020-03-01");
        add("ProcedureDate[1]/FullDate[1]", "2020-02-29");
        prepare("Procedure", "OnsetOrEventDate", temporal("2020-02-28", false));
        var result = read();
        assertThat(result.changes()).hasSize(3);
        assertThat(result.dates().get("ResolvedDate[1]").lexical()).isEqualTo("2020-03-03");
        assertThat(result.dates().get("ProcedureDate[1]").lexical()).isEqualTo("2020-03-02");
        var xml = mock(CdsExportXmlComposer.ContentWriter.class);
        result.changes().getFirst().content().write(xml);
        verify(xml).text("2020-03-01");
        prepare("Procedure", "ProcedureDate", temporal("2020-02-29", false));
        assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_HISTORY_SOURCE_PREPARATION_MISMATCH");
    }

    @Test
    void shouldUseTheProcedureDate_whenNoOnsetWasSupplied() throws Exception {
        fields.clear();
        category = CdsCategory.PAST_HEALTH;
        add("PastHealthProblemDescriptionOrProcedures[1]", "Procedure");
        add("ProcedureDate[1]/FullDate[1]", "2020-02-29");
        prepare("Procedure", "ProcedureDate", temporal("2020-02-29", false));
        assertThat(read().dates().keySet()).containsExactly("ProcedureDate[1]");
    }

    @Test
    void shouldPreserveOffsetsFractionsAndEveryParticipantReviewerDate_whenShiftingClinicalNotes()
            throws Exception {
        fields.clear();
        category = CdsCategory.CLINICAL_NOTES;
        shift = -1;
        add("MyClinicalNotesContent[1]", "Exact clinical narrative");
        add("EventDateTime[1]/FullDateTime[1]", "2020-03-01T12:23:34.120-05:00");
        add("ParticipatingProviders[1]/Name[1]/FirstName[1]", "Synthetic");
        add("ParticipatingProviders[1]/Name[1]/LastName[1]", "Author");
        add(
                "ParticipatingProviders[1]/DateTimeNoteCreated[1]/FullDateTime[1]",
                "2020-03-01T13:00:00Z");
        add("NoteReviewer[1]/Name[1]/FirstName[1]", "Synthetic");
        add("NoteReviewer[1]/Name[1]/LastName[1]", "Reviewer");
        add("NoteReviewer[1]/DateTimeNoteReviewed[1]/FullDate[1]", "2020-03-02");
        prepare(
                "Exact clinical narrative",
                "EventDateTime",
                temporal("2020-03-01T12:23:34.120-05:00", true));
        var result = read();
        assertThat(result.changes()).hasSize(3);
        assertThat(result.sourceFields()).hasSize(8);
        assertThat(result.dates().get("EventDateTime[1]").lexical())
                .isEqualTo("2020-02-29T12:23:34.120-05:00");
        assertThat(result.dates().get("ParticipatingProviders[1]/DateTimeNoteCreated[1]").lexical())
                .isEqualTo("2020-02-29T13:00:00Z");
        assertThat(result.dates().get("NoteReviewer[1]/DateTimeNoteReviewed[1]").lexical())
                .isEqualTo("2020-03-01");
    }

    @Test
    void shouldShiftProblemResolutionRiskEndAndAlertEndDates_inTheirOriginalWireFields()
            throws Exception {
        for (var selection :
                List.of(
                        CdsCategory.PROBLEM_LIST,
                        CdsCategory.RISK_FACTORS,
                        CdsCategory.ALERTS_AND_SPECIAL_NEEDS)) {
            fields.clear();
            category = selection;
            shift = 1;
            String body =
                    selection == CdsCategory.PROBLEM_LIST
                            ? "ProblemDiagnosisDescription"
                            : selection == CdsCategory.RISK_FACTORS
                                    ? "RiskFactor"
                                    : "AlertDescription";
            String start =
                    selection == CdsCategory.PROBLEM_LIST
                            ? "OnsetDate"
                            : selection == CdsCategory.RISK_FACTORS ? "StartDate" : "DateActive";
            String end = selection == CdsCategory.PROBLEM_LIST ? "ResolutionDate" : "EndDate";
            add(body + "[1]", "Narrative");
            add(start + "[1]/FullDate[1]", "2020-02-28");
            add(end + "[1]/FullDate[1]", "2020-03-01");
            prepare("Narrative", start, temporal("2020-02-28", false));
            var result = read();
            assertThat(result.changes()).hasSize(2);
            assertThat(result.dates().get(end + "[1]").lexical()).isEqualTo("2020-03-02");
        }
    }

    @Test
    void shouldPreservePartialDates_withoutInventingTheirUnknownDays() throws Exception {
        fields.removeLast();
        add("StartDate[1]/YearOnly[1]", "2020");
        prepare(
                "Exact <clinical> & literal text",
                "StartDate",
                CdsTemporal.parse("2020", null, null, null, "StartDate"));
        assertThat(read().dates().get("StartDate[1]").precision())
                .isEqualTo(CdsExportDateDto.Precision.YEAR);
        shift = 1;
        assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_HISTORY_SOURCE_DATE_REQUIRES_REVIEW");
    }

    @Test
    void shouldRefuseANonProjectedPartialEndDate_whenItsSyntheticShiftCannotBeEstablished()
            throws Exception {
        add("Treatment[1]", "Separate treatment");
        shift = 1;
        prepare("Exact <clinical> & literal text", "StartDate", temporal("2020-02-28", false));
        fields.clear();
        category = CdsCategory.RISK_FACTORS;
        add("RiskFactor[1]", "Risk");
        add("StartDate[1]/FullDate[1]", "2020-02-28");
        add("EndDate[1]/YearMonth[1]", "2020-03");
        prepare("Risk", "StartDate", temporal("2020-02-28", false));
        assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_HISTORY_SOURCE_DATE_REQUIRES_REVIEW");
    }

    @Test
    void shouldCompareAcceptedLogicalNarrativeCorrections_withTheOriginalPreparedContent()
            throws Exception {
        var f = fields.getFirst();
        fields.set(
                0,
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
                        "An earlier source spelling",
                        f.logicalInline(),
                        null));
        assertThat(read().sourceFields()).hasSize(2);
        prepare("A different native narrative", "StartDate", temporal("2020-02-28", false));
        assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_HISTORY_SOURCE_INVALID");
    }

    @Test
    void
            shouldKeepTheCompleteOversizedNarrative_asSourceWithoutUsingItsNativePlaceholderAsClinicalText()
                    throws Exception {
        String marker =
                "[CDS FamilyHistory: narrative exceeds the bounded native projection. Read the complete original and normalized values in this clinical category's CDS source fields. No narrative excerpt was substituted.]";
        prepared =
                new CdsHistoryNativeDto(
                        marker,
                        "FamHistory",
                        null,
                        null,
                        null,
                        temporal("2020-02-28", false),
                        "StartDate",
                        List.of(
                                new CdsClinicalDiagnosticDto(
                                        "CDS_NOTE_COMPLETE_TEXT_SOURCE_VIEW",
                                        "ProblemDiagnosisProcedureDescription")));
        var result = read();
        assertThat(result.sourceFields()).contains(fields.getFirst().id());
        verify(values, never()).text(fields.getFirst());
        prepared =
                new CdsHistoryNativeDto(
                        "Edited placeholder",
                        "FamHistory",
                        null,
                        null,
                        null,
                        temporal("2020-02-28", false),
                        "StartDate",
                        prepared.diagnostics());
        assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_HISTORY_SOURCE_INVALID");
    }

    @Test
    void shouldSupportPersonalHistoryResiduals_withoutInventingANativeNarrativeOrDate()
            throws Exception {
        fields.clear();
        category = CdsCategory.PERSONAL_HISTORY;
        add("ResidualInfo[1]/DataElement[1]/Name[1]", "Source history");
        add("ResidualInfo[1]/DataElement[1]/DataType[1]", "String");
        add("ResidualInfo[1]/DataElement[1]/Content[1]", "Complete original content");
        prepared =
                new CdsHistoryNativeDto(
                        "[CDS PersonalHistory: no narrative body supplied. Separate clinical source fields retain this occurrence's complete values.]",
                        "SocHistory",
                        null,
                        null,
                        null,
                        null,
                        null,
                        List.of(
                                new CdsClinicalDiagnosticDto(
                                        "CDS_HISTORY_STRUCTURED_SOURCE_FIELDS",
                                        "PersonalHistory")));
        assertThat(read().sourceFields()).hasSize(3);
        assertThat(read().dates()).isEmpty();
        assertThat(read().changes()).isEmpty();
    }

    @Test
    void shouldRefuseSourceDateOrSelectionDifferences_fromTheSealedPreparation() {
        prepare("Exact <clinical> & literal text", "StartDate", temporal("2020-02-29", false));
        assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_HISTORY_SOURCE_PREPARATION_MISMATCH");
    }

    @Test
    void shouldRefuseDuplicatePathsAndMixedPatientOccurrences_beforeProducingChanges() {
        add("StartDate[1]/FullDate[1]", "2020-02-28");
        assertThatThrownBy(this::read).isInstanceOf(IOException.class);
        fields.removeLast();
        var f = fields.getLast();
        fields.set(
                fields.size() - 1,
                new CdsExportRetainedFieldDto(
                        f.id(),
                        f.runId(),
                        "b".repeat(64),
                        f.fieldUnitKey(),
                        f.category(),
                        f.sourcePath(),
                        f.occurrence(),
                        f.namespace(),
                        f.valueType(),
                        f.precision(),
                        f.originalInline(),
                        f.logicalInline(),
                        f.originalPayload()));
        assertThatThrownBy(this::read).isInstanceOf(IOException.class);
    }

    @Test
    void shouldPropagateAuthenticatedStreamFailure_insteadOfAcceptingAPreparationPrefix()
            throws Exception {
        when(values.text(fields.getFirst()))
                .thenReturn(
                        new StringReader(prepared.content()) {
                            @Override
                            public void close() {
                                throw new IllegalStateException("Synthetic authentication failure");
                            }
                        });
        assertThatThrownBy(this::read).hasMessage("Synthetic authentication failure");
    }

    @Test
    void shouldRefuseStalledStreams_andRespectCallbackLifetimeRevocation() throws Exception {
        when(values.text(fields.getLast()))
                .thenReturn(
                        new Reader() {
                            public int read(char[] b, int o, int n) {
                                return 0;
                            }

                            public void close() {}
                        });
        assertThatThrownBy(this::read).hasMessage("CDS_EXPORT_HISTORY_SOURCE_INVALID");
        doThrow(new IOException("Synthetic scope closed")).when(values).requireActive();
        assertThatThrownBy(this::read).hasMessage("Synthetic scope closed");
    }

    @Test
    void shouldRefuseAnotherPatientOrNote_beforeReadingRetainedClinicalText() throws Exception {
        var index = index();
        String address = RUN + ":" + HASH;
        var control =
                mock(
                        io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl
                                .class);
        for (var wrong :
                List.of(
                        new CdsExportNoteAccessDto(91, 28, "17", "1"),
                        new CdsExportNoteAccessDto(92, 27, "17", "1")))
            assertThatThrownBy(
                            () ->
                                    new CdsExportHistoryRetainedSource()
                                            .read(
                                                    27, category, address, index, null, fields,
                                                    wrong, values, control))
                    .hasMessage("CDS_EXPORT_HISTORY_SOURCE_INVALID");
        verify(control, never()).authorizeNote(any(), any());
        verify(values, never()).text(any());
    }

    @Test
    void shouldRequireAnExplicitNoteAuthorizationCapability_beforeInterpretingSourceFields()
            throws Exception {
        var index = index();
        var control =
                new io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl() {
                    public void checkpoint() {}

                    public void captureCheckpoint() {}

                    public void authorizePatient(int patient) {}

                    public void authorizeCategory(
                            int patient, CdsCategory category, boolean sensitive) {}
                };
        assertThatThrownBy(
                        () ->
                                new CdsExportHistoryRetainedSource()
                                        .read(
                                                27,
                                                category,
                                                RUN + ":" + HASH,
                                                index,
                                                null,
                                                fields,
                                                new CdsExportNoteAccessDto(91, 27, "17", "1"),
                                                values,
                                                control))
                .hasMessage("CDS_EXPORT_NOTE_AUTHORIZATION_REQUIRED");
        verify(values, never()).text(any());
    }

    /** Explicit simulated metadata for testing the pre-read access boundary, never an installed import qualification. */
    private CdsExportRetainedIndexDto index() throws Exception {
        var value =
                new CdsHistoryNativeDto(
                        prepared.content(),
                        "FamHistory",
                        null,
                        null,
                        null,
                        prepared.observationDate(),
                        "StartDate",
                        List.of());
        var json =
                new com.fasterxml.jackson.databind.ObjectMapper()
                        .registerModule(new com.fasterxml.jackson.datatype.jsr310.JavaTimeModule())
                        .disable(
                                com.fasterxml.jackson.databind.SerializationFeature
                                        .WRITE_DATES_AS_TIMESTAMPS);
        String payload = json.writeValueAsString(value), id = CdsImportKeys.hash(RUN, HASH);
        var preparation =
                new CdsExportCapturedTextRowDto(
                        "cds_import_preparation",
                        id,
                        Map.of(
                                "run_id",
                                RUN,
                                "unit_key",
                                HASH,
                                "payload_type",
                                CdsHistoryNativeDto.class.getName(),
                                "payload",
                                payload,
                                "source_digest",
                                HASH,
                                "options_digest",
                                HASH,
                                "ruleset_digest",
                                HASH,
                                "digest",
                                io.github.carlos_emr.carlos.demographic.cds.contract
                                        .CdsPreparationSeal.digest(
                                        CdsHistoryNativeDto.class.getName(),
                                        HASH,
                                        HASH,
                                        HASH,
                                        payload)));
        var unit =
                new CdsExportCapturedTextRowDto(
                        "cds_import_unit",
                        id,
                        Map.of(
                                "run_id",
                                RUN,
                                "unit_key",
                                HASH,
                                "source_digest",
                                HASH,
                                "category",
                                category.wireName(),
                                "state",
                                "COMMITTED"));
        var run =
                new CdsExportCapturedTextRowDto(
                        "cds_import_run",
                        RUN,
                        Map.of("id", RUN, "options_digest", HASH, "ruleset_digest", HASH));
        return new CdsExportRetainedIndexDto(
                HASH,
                3,
                payload.length(),
                Map.of(
                        "cds_import_preparation",
                        Map.of(id, preparation),
                        "cds_import_unit",
                        Map.of(id, unit),
                        "cds_import_run",
                        Map.of(RUN, run)),
                Set.of(RUN + ":" + HASH),
                Map.of(
                        RUN + ":" + HASH,
                        List.of(
                                new CdsExportRetainedIndexDto.Reference(
                                        "casemgmt_note", "91", HASH))),
                Map.of());
    }

    private CdsExportHistoryRetainedSource.Result read() throws Exception {
        var options =
                new CdsExportRetainedImportOptionsReader.Options(
                        RUN, HASH, HASH, HASH, "omd-5.2", shift != 0, shift);
        return new CdsExportHistoryRetainedSource()
                .readVerified(category, fields, options, prepared, values, () -> {});
    }

    private CdsTemporalDto temporal(String lexical, boolean clock) {
        return CdsTemporal.shift(
                CdsTemporal.parse(
                        null, null, clock ? null : lexical, clock ? lexical : null, "Date"),
                shift,
                "Date");
    }

    private void prepare(String text, String dateField, CdsTemporalDto date) {
        prepared =
                new CdsHistoryNativeDto(text, null, null, null, null, date, dateField, List.of());
    }

    private void add(String path, String value) {
        var type =
                CdsWireSchema.schemaModel()
                        .element("cds", "OmdCds")
                        .child("cds", "PatientRecord")
                        .child("cds", category.wireName());
        String namespace = "cds";
        if (!path.isEmpty())
            for (String segment : path.split("/")) {
                String name = segment.substring(0, segment.indexOf('['));
                var child =
                        type.childNames().stream()
                                .filter(candidate -> candidate.local().equals(name))
                                .findFirst()
                                .orElseThrow();
                namespace = child.namespace();
                type = type.child(namespace, name);
            }
        String root = "/OmdCds[1]/PatientRecord[1]/" + category.wireName() + "[1]";
        fields.add(
                new CdsExportRetainedFieldDto(
                        CdsImportKeys.hash(path, Integer.toString(fields.size())),
                        RUN,
                        HASH,
                        HASH,
                        category,
                        root + (path.isEmpty() ? "" : "/" + path),
                        1,
                        namespace,
                        "{" + type.getTypeNamespace() + "}" + type.getTypeName(),
                        null,
                        value,
                        value,
                        null));
    }
}
