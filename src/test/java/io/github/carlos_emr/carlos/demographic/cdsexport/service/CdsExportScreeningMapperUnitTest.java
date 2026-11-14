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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.casemgmt.dao.*;
import io.github.carlos_emr.carlos.casemgmt.model.*;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;

/**
 * Native screenings (non-immunization preventions) leave as legacy-compatible PastHealth
 * procedures through the receiving schema, with every native field accounted for and the
 * complete rows carried for a CARLOS-to-CARLOS transfer.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportScreeningMapperUnitTest {
    static final String SOURCE = "a".repeat(64);
    private final CdsExportScreeningMapper mapper = new CdsExportScreeningMapper();
    @TempDir Path root;

    static CdsExportPreventionClassifier classifier() throws IOException {
        return CdsExportPreventionClassifier.fromCaptured(
                SOURCE,
                ("<preventions><item name='PAP' layout='PAPMAM' headingName='Screenings'/>"
                                + "<item name='OtherB' layout='PAPMAM' headingName='Other'/>"
                                + "<item name='Td' layout='injection' healthCanadaType='Td'/>"
                                + "</preventions>")
                        .getBytes(StandardCharsets.UTF_8),
                List.of());
    }

    static CdsExportCapturedTextRowDto parent(int id, Map<String, String> supplied) {
        var fields = new HashMap<String, String>();
        Plan.PREVENTIONS_FULL.columns().forEach(column -> fields.put(column, null));
        fields.put("id", Integer.toString(id));
        fields.put("demographic_no", "27");
        fields.put("prevention_type", "PAP");
        fields.put("prevention_date", "2024-02-29 00:00:00");
        fields.put("provider_no", "");
        fields.put("deleted", "0");
        fields.put("refused", "0");
        fields.put("never", "0");
        fields.put("creator", "999998");
        fields.put("creation_date", "2024-03-01 09:15:00");
        fields.put("lastUpdateDate", "2024-03-01 09:15:00");
        fields.putAll(supplied);
        return new CdsExportCapturedTextRowDto(
                CdsExportPreventionSources.SCREENINGS,
                CdsExportPatientTableSourceReader.key(id),
                fields);
    }

    static CdsExportCapturedTextRowDto extension(int id, int owner, String key, String value) {
        var fields = new HashMap<String, String>();
        fields.put("id", Integer.toString(id));
        fields.put("prevention_id", Integer.toString(owner));
        fields.put("keyval", key);
        fields.put("val", value);
        return new CdsExportCapturedTextRowDto(
                CdsExportPreventionSources.SCREENING_EXTENSIONS,
                CdsExportPatientTableSourceReader.key(id),
                fields);
    }

    static CdsExportCapturedTextRowDto precision(int id, int owner, String format) {
        var fields = new HashMap<String, String>();
        fields.put("id", Integer.toString(id));
        fields.put("table_name", "4");
        fields.put("table_id", Integer.toString(owner));
        fields.put("field_name", "7");
        fields.put("format", format);
        return new CdsExportCapturedTextRowDto(
                CdsExportPreventionSources.SCREENING_PRECISION,
                CdsExportPatientTableSourceReader.key(id),
                fields);
    }

    private CdsExportScreeningMapper.Result map(
            CdsExportCapturedTextRowDto row,
            List<CdsExportCapturedTextRowDto> extensions,
            List<CdsExportCapturedTextRowDto> precision)
            throws IOException {
        return mapper.map(27, row, extensions, precision, classifier());
    }

    private static byte[] render(CdsExportScreeningDto value) throws Exception {
        return CdsExportNativeHistoryMapperUnitTest.render(
                CdsCategory.PAST_HEALTH, xml -> new CdsExportScreeningXmlWriter().write(value, xml));
    }

    private static String element(byte[] xml, String namespace, String name) throws Exception {
        var factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        var nodes =
                factory.newDocumentBuilder()
                        .parse(new ByteArrayInputStream(xml))
                        .getElementsByTagNameNS(namespace, name);
        return nodes.getLength() == 0 ? null : nodes.item(0).getTextContent();
    }

    private static CdsExportFieldAssessmentDto field(
            CdsExportScreeningMapper.Result result, CdsExportCapturedTextRowDto row, String column) {
        return result.fields()
                .get(new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column));
    }

    @Test
    void shouldExportAScreeningAsTheLegacyPastHealthProcedure_throughTheReceivingSchema()
            throws Exception {
        var row = parent(31, Map.of("refused", "1"));
        var result =
                map(
                        row,
                        List.of(
                                extension(41, 31, "result", "abnormal"),
                                extension(42, 31, "reason", "patient declined café 😀"),
                                extension(43, 31, "comments", "Repeat in\r\nsix months")),
                        List.of());
        assertThat(result.representable()).isTrue();
        var xml = render(result.value());
        assertThat(element(xml, "cds", "PastHealthProblemDescriptionOrProcedures"))
                .isEqualTo("PAP\nRefused\nResult:abnormal\nReason:patient declined café 😀");
        assertThat(element(xml, "cds_dt", "FullDate")).isEqualTo("2024-02-29");
        assertThat(element(xml, "cds", "Notes")).isEqualTo("Repeat in\r\nsix months");
        assertThat(element(xml, "cds_dt", "Name"))
                .isEqualTo(CdsNativeExtensionContract.PREVENTION_SOURCE_CONTEXT);
        var factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        var entry =
                factory.newDocumentBuilder()
                        .parse(new ByteArrayInputStream(xml))
                        .getElementsByTagNameNS("cds", "PastHealth")
                        .item(0)
                        .getChildNodes();
        var order = new ArrayList<String>();
        for (int i = 0; i < entry.getLength(); i++)
            if (entry.item(i).getNodeType() == org.w3c.dom.Node.ELEMENT_NODE)
                order.add(entry.item(i).getLocalName());
        assertThat(order)
                .containsExactly(
                        "ResidualInfo",
                        "PastHealthProblemDescriptionOrProcedures",
                        "ProcedureDate",
                        "Notes");
    }

    @Test
    void shouldStateEachNativeStatus_asTheLegacyDescriptionLine() throws Exception {
        for (var status :
                Map.of("0", "PAP", "2", "PAP\nIneligible", "3", "PAP\nCompleted externally")
                        .entrySet())
            assertThat(
                            map(parent(31, Map.of("refused", status.getKey())), List.of(), List.of())
                                    .value()
                                    .description())
                    .isEqualTo(status.getValue());
    }

    @Test
    void shouldKeepARecordedYearOrMonthPrecision_withoutInventingADay() throws Exception {
        for (var format : Map.of("YYYY", "YearOnly", "YYYY-MM", "YearMonth").entrySet()) {
            var result =
                    map(
                            parent(31, Map.of("prevention_date", "2024-01-01 00:00:00")),
                            List.of(),
                            List.of(precision(51, 31, format.getKey())));
            assertThat(result.representable()).isTrue();
            var xml = render(result.value());
            assertThat(element(xml, "cds_dt", format.getValue()))
                    .isEqualTo(format.getKey().equals("YYYY") ? "2024" : "2024-01");
            assertThat(element(xml, "cds_dt", "FullDate")).isNull();
        }
    }

    @Test
    void shouldExportTheCalendarDate_whenANativeClockTimeIsRecorded() throws Exception {
        var row = parent(31, Map.of("prevention_date", "2024-11-03 01:30:00"));
        var result = map(row, List.of(), List.of());
        assertThat(element(render(result.value()), "cds_dt", "FullDate")).isEqualTo("2024-11-03");
        assertThat(context(result).path("prevention").path("prevention_date").asText())
                .isEqualTo("2024-11-03 01:30:00");
        assertThat(field(result, row, "prevention_date").destination())
                .isEqualTo(CdsExportScreeningMapper.PROCEDURE_DATE);
    }

    @Test
    void shouldOmitProcedureDate_whenNoNativeDateIsRecorded() throws Exception {
        var row = new HashMap<String, String>();
        row.put("prevention_date", null);
        var result = map(parent(31, row), List.of(), List.of());
        assertThat(result.value().procedureDate().precision())
                .isEqualTo(CdsExportDateDto.Precision.ABSENT);
        assertThat(element(render(result.value()), "cds", "ProcedureDate")).isNull();
    }

    @Test
    void shouldCarryEveryNativeRowExactly_inTheResidualWithNullAndEmptyDistinct() throws Exception {
        var row = parent(31, Map.of("provider_name", "Dr  Two   Spaces", "never", "1"));
        var result =
                map(
                        row,
                        List.of(
                                extension(42, 31, "location", ""),
                                extension(41, 31, "result", null),
                                extension(43, 31, "workflowId", " padded ")),
                        List.of(precision(51, 31, "")));
        assertThat(result.representable()).isTrue();
        var xml = render(result.value());
        String content = element(xml, "cds_dt", "Content");
        assertThat(content).doesNotContain(" ", "\t", "\r", "\n");
        var json = new ObjectMapper().readTree(content);
        assertThat(json.path("version").asInt()).isEqualTo(1);
        assertThat(json.path("prevention").path("provider_name").asText())
                .isEqualTo("Dr  Two   Spaces");
        assertThat(json.path("prevention").path("next_date").isNull()).isTrue();
        assertThat(json.path("prevention").path("provider_no").asText()).isEmpty();
        assertThat(json.path("prevention").path("never").asText()).isEqualTo("1");
        var extensions = json.path("extensions");
        assertThat(extensions).hasSize(3);
        assertThat(extensions.get(0).path("keyval").asText()).isEqualTo("result");
        assertThat(extensions.get(0).path("val").isNull()).isTrue();
        assertThat(extensions.get(1).path("val").isTextual()).isTrue();
        assertThat(extensions.get(1).path("val").asText()).isEmpty();
        assertThat(extensions.get(2).path("val").asText()).isEqualTo(" padded ");
        assertThat(json.path("precision").get(0).path("format").asText()).isEmpty();
        assertThat(element(xml, "cds", "PastHealthProblemDescriptionOrProcedures"))
                .isEqualTo("PAP");
    }

    @Test
    void shouldAccountForEveryCapturedField_withExactlyOneDispositionEach() throws Exception {
        var row = parent(31, Map.of());
        var extensions =
                List.of(
                        extension(41, 31, "result", "normal"),
                        extension(42, 31, "comments", "Seen"));
        var dates = List.of(precision(51, 31, "YYYY-MM"));
        var result = map(row, extensions, dates);
        assertThat(result.fields()).hasSize(16 + 4 * 2 + 5);
        var checked =
                new CdsExportNativeFieldAssessmentValidator()
                        .validate(result.rows(), result.fields(), 1000, () -> {});
        assertThat(checked.representable()).isTrue();
        assertThat(checked.mapped()).isEqualTo(29);
        assertThat(field(result, row, "prevention_type").destination())
                .isEqualTo(CdsExportScreeningMapper.DESCRIPTION);
        assertThat(field(result, extensions.get(0), "val").destination())
                .isEqualTo(CdsExportScreeningMapper.DESCRIPTION);
        assertThat(field(result, extensions.get(1), "val").destination())
                .isEqualTo(CdsExportScreeningMapper.NOTES);
        assertThat(field(result, dates.getFirst(), "format").destination())
                .isEqualTo(CdsExportScreeningMapper.PROCEDURE_DATE);
        assertThat(field(result, row, "provider_name").destination())
                .isEqualTo(CdsExportScreeningMapper.RESIDUAL);
    }

    @Test
    void shouldMoveAnOverlongResultToNotes_insteadOfClippingTheDescription() throws Exception {
        var row = parent(31, Map.of());
        String result = "R".repeat(300);
        var extensions =
                List.of(
                        extension(41, 31, "result", result),
                        extension(42, 31, "comments", "Follow up"));
        var mapped = map(row, extensions, List.of());
        assertThat(mapped.value().description()).isEqualTo("PAP");
        assertThat(mapped.value().notes()).isEqualTo("Result:" + result + "\nFollow up");
        assertThat(field(mapped, extensions.getFirst(), "val").destination())
                .isEqualTo(CdsExportScreeningMapper.NOTES);
        render(mapped.value());
    }

    // Superseded or removed versions (deleted = "1") are skipped by the occurrence service and
    // never reach the mapper there; a legacy NULL flag is the current version. Only an unknown
    // flag still holds the patient (the earlier rule held "1" and NULL as well).
    @Test
    void shouldHoldOnlyAnUnknownDeletedFlag_forReview() throws Exception {
        var values = new HashMap<String, String>();
        values.put("deleted", null);
        var current = map(parent(31, values), List.of(), List.of());
        assertThat(current.representable()).isTrue();
        var unknown = parent(31, Map.of("deleted", "2"));
        var result = map(unknown, List.of(), List.of());
        assertThat(result.representable()).isFalse();
        assertThat(field(result, unknown, "deleted").rule())
                .isEqualTo("ARCHIVE_STATE_NEEDS_A_LOSSLESS_CARRIER");
        var superseded = parent(31, Map.of("deleted", "1"));
        assertThat(field(map(superseded, List.of(), List.of()), superseded, "deleted").rule())
                .isEqualTo("SUPERSEDED_OR_REMOVED_VERSION_IS_NOT_CURRENT");
    }

    @Test
    void shouldHoldARowThatIsNotAProvenScreening_forReview() throws Exception {
        for (var type : List.of("Td", "UNKNOWN_TYPE")) {
            var row = parent(31, Map.of("prevention_type", type));
            var result = map(row, List.of(), List.of());
            assertThat(result.representable()).isFalse();
            assertThat(field(result, row, "prevention_type").rule())
                    .isEqualTo("CAPTURED_TYPE_IS_NOT_PROVEN_SCREENING");
        }
    }

    // Without a live program check, a restricted screening is still held.
    @Test
    void shouldHoldAProgramRestrictedScreening_forReview() throws Exception {
        var row = parent(31, Map.of("restrictToProgram", "1", "programNo", "10016"));
        assertThat(map(row, List.of(), List.of()).representable()).isFalse();
    }

    // OPEN-6: a screening restricted to a program is exported when a fresh check says every
    // exporting identity works in it, and its flag and program travel exactly in the residual.
    @Test
    void shouldExportARestrictedScreening_whenItsProgramIsReadableLive() throws Exception {
        var row = parent(31, Map.of("restrictToProgram", "1", "programNo", "10016"));
        var asked = new ArrayList<String>();
        var result =
                mapper.map(
                        27,
                        row,
                        List.of(),
                        List.of(),
                        classifier(),
                        program -> {
                            asked.add(program);
                            return true;
                        });
        assertThat(asked).containsExactly("10016");
        assertThat(result.representable()).isTrue();
        for (String column : List.of("restrictToProgram", "programNo")) {
            assertThat(field(result, row, column).outcome())
                    .isEqualTo(CdsExportFieldAssessmentDto.Outcome.MAPPED);
            assertThat(field(result, row, column).destination())
                    .isEqualTo(CdsExportScreeningMapper.RESIDUAL);
            assertThat(field(result, row, column).rule()).isEqualTo("PROGRAM_SCOPE_AUTHORIZED");
        }
        assertThat(context(result).path("prevention").path("restrictToProgram").asText())
                .isEqualTo("1");
        assertThat(context(result).path("prevention").path("programNo").asText())
                .isEqualTo("10016");
        var refused =
                mapper.map(27, row, List.of(), List.of(), classifier(), program -> false);
        assertThat(refused.representable()).isFalse();
        assertThat(field(refused, row, "programNo").rule())
                .isEqualTo("PROGRAM_SCOPE_REQUIRES_REVIEW");
    }

    @Test
    void shouldTreatAFlagWithoutAProgramOrAZeroFlag_asUnrestrictedScreening() throws Exception {
        for (var scope :
                List.of(
                        Map.of("restrictToProgram", "1"),
                        Map.of("restrictToProgram", "1", "programNo", "-1"),
                        Map.of("restrictToProgram", "0", "programNo", "10016"))) {
            var row = parent(31, scope);
            var result =
                    mapper.map(
                            27,
                            row,
                            List.of(),
                            List.of(),
                            classifier(),
                            program -> {
                                throw new AssertionError("No program to check");
                            });
            assertThat(result.representable()).isTrue();
        }
        // A malformed flag needs review whatever any program check would say.
        var malformed = parent(31, Map.of("restrictToProgram", "2"));
        assertThat(
                        mapper.map(27, malformed, List.of(), List.of(), classifier(), p -> true)
                                .representable())
                .isFalse();
    }

    @Test
    void shouldHoldAnUnknownStatus_forReview() throws Exception {
        for (String refused : Arrays.asList("9", null)) {
            var values = new HashMap<String, String>();
            values.put("refused", refused);
            var row = parent(31, values);
            var result = map(row, List.of(), List.of());
            assertThat(result.representable()).isFalse();
            assertThat(field(result, row, "refused").rule())
                    .isEqualTo("UNKNOWN_PREVENTION_STATUS_REQUIRES_REVIEW");
        }
    }

    @Test
    void shouldHoldDuplicateResults_forReviewRatherThanChooseOne() throws Exception {
        var result =
                map(
                        parent(31, Map.of()),
                        List.of(
                                extension(41, 31, "result", "normal"),
                                extension(42, 31, "result", "abnormal")),
                        List.of());
        assertThat(result.representable()).isFalse();
    }

    @Test
    void shouldHoldAnUnsupportedDatePrecision_forReview() throws Exception {
        assertThat(
                        map(parent(31, Map.of()), List.of(), List.of(precision(51, 31, "DD")))
                                .representable())
                .isFalse();
        var twoMarkers = List.of(precision(51, 31, "YYYY"), precision(52, 31, "YYYY-MM"));
        assertThat(map(parent(31, Map.of()), List.of(), twoMarkers).representable()).isFalse();
    }

    @Test
    void shouldHoldTextThatXmlCannotCarry_forReviewInsteadOfFailingTheWriter() throws Exception {
        var comments = extension(42, 31, "comments", "bad \uFFFE char");
        var result = map(parent(31, Map.of()), List.of(comments), List.of());
        assertThat(result.representable()).isFalse();
        assertThat(field(result, comments, "val").outcome()).isEqualTo(Outcome.REQUIRES_REVIEW);
    }

    @Test
    void shouldMoveATypeAndStatusTooLongForTheDescription_wholeToNotes() throws Exception {
        String name = "S".repeat(240);
        var definitions =
                CdsExportPreventionClassifier.fromCaptured(
                        SOURCE,
                        ("<preventions><item name='" + name + "' layout='PAPMAM' headingName='Screenings'/></preventions>")
                                .getBytes(StandardCharsets.UTF_8),
                        List.of());
        var row = parent(31, Map.of("prevention_type", name, "refused", "3"));
        var result = mapper.map(27, row, List.of(), List.of(), definitions);
        // Neither clipped nor held: a fixed label names the entry and Notes opens with the type.
        assertThat(result.representable()).isTrue();
        assertThat(field(result, row, "prevention_type").rule())
                .isEqualTo(CdsExportScreeningMapper.OVERLONG_IN_RESIDUAL);
    }

    @Test
    void shouldRefuseRowsOwnedByAnotherPrevention_asAScopeFailure() {
        assertThatThrownBy(
                        () ->
                                map(
                                        parent(31, Map.of()),
                                        List.of(extension(41, 32, "result", "normal")),
                                        List.of()))
                .hasMessage("CDS_EXPORT_SCREENING_MAPPING_SCOPE_INVALID");
        assertThatThrownBy(
                        () -> map(parent(31, Map.of()), List.of(), List.of(precision(51, 32, "YYYY"))))
                .hasMessage("CDS_EXPORT_SCREENING_MAPPING_SCOPE_INVALID");
        assertThatThrownBy(
                        () ->
                                mapper.map(
                                        28, parent(31, Map.of()), List.of(), List.of(), classifier()))
                .hasMessage("CDS_EXPORT_SCREENING_MAPPING_SCOPE_INVALID");
    }

    @Test
    void shouldRefuseAClockTimeOrWhitespaceInTheResidual_beforeWriting() {
        for (var value :
                List.of(
                        new CdsExportScreeningDto(
                                "PAP",
                                new CdsExportDateDto(
                                        CdsExportDateDto.Precision.DATE_TIME,
                                        "2024-02-29T10:00:00-05:00"),
                                null,
                                "{}"),
                        new CdsExportScreeningDto(
                                "PAP",
                                new CdsExportDateDto(CdsExportDateDto.Precision.ABSENT, null),
                                null,
                                "{\"a\": 1}"),
                        new CdsExportScreeningDto(
                                "P".repeat(251),
                                new CdsExportDateDto(CdsExportDateDto.Precision.ABSENT, null),
                                null,
                                "{}")))
            assertThatThrownBy(() -> render(value))
                    .hasMessage("CDS_EXPORT_SCREENING_VALUE_INVALID");
    }

    @Test
    void shouldImportTheExportedScreeningAsPastHealthHistory_throughTheReceivingPersister()
            throws Exception {
        var mapped =
                map(
                        parent(31, Map.of("refused", "2")),
                        List.of(
                                extension(41, 31, "reason", "Hysterectomy"),
                                extension(42, 31, "comments", "Recorded by clinic")),
                        List.of());
        Path directory = Files.createTempDirectory(root, "roundtrip-");
        var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser()
                .parse(
                        Files.write(directory.resolve("patient.xml"), render(mapped.value())),
                        directory.resolve("parsed"),
                        CdsInputLimitsDto.defaults(),
                        () -> false,
                        records::add);
        var record =
                records.stream()
                        .filter(r -> r.section().equals("PastHealth"))
                        .findFirst()
                        .orElseThrow();
        var fields = new CdsFieldParser();
        var preparation = mock(CdsNativePreparationService.class);
        var prepared = new AtomicReference<CdsHistoryNativeDto>();
        doAnswer(
                        call -> {
                            prepared.set(call.getArgument(5));
                            return null;
                        })
                .when(preparation)
                .store(anyString(), anyLong(), anyString(), anyString(), any(), any());
        when(preparation.load(
                        anyString(),
                        anyLong(),
                        anyString(),
                        anyString(),
                        any(),
                        eq(CdsHistoryNativeDto.class)))
                .thenAnswer(call -> prepared.get());
        var notes = mock(CaseManagementNoteDAO.class);
        var written = new AtomicReference<CaseManagementNote>();
        doAnswer(
                        call -> {
                            CaseManagementNote note = call.getArgument(0);
                            note.setId(91L);
                            written.set(note);
                            return null;
                        })
                .when(notes)
                .saveNote(any());
        var issues = mock(IssueDAO.class);
        var definition = new Issue();
        definition.setId(51L);
        definition.setCode("MedHistory");
        when(issues.findCandidatesByCode("MedHistory")).thenReturn(List.of(definition));
        var patientIssues = mock(CaseManagementIssueDAO.class);
        when(patientIssues.findCandidatesByDemographicAndIssue(anyInt(), anyLong()))
                .thenReturn(List.of());
        doAnswer(
                        call -> {
                            CaseManagementIssue issue = call.getArgument(0);
                            issue.setId(41L);
                            return null;
                        })
                .when(patientIssues)
                .saveIssue(any());
        var extensions = mock(CaseManagementNoteExtDAO.class);
        var extensionId = new java.util.concurrent.atomic.AtomicLong(101);
        doAnswer(
                        call -> {
                            CaseManagementNoteExt extension = call.getArgument(0);
                            extension.setId(extensionId.getAndIncrement());
                            return null;
                        })
                .when(extensions)
                .save(any());
        var properties = new Properties();
        properties.setProperty("cds.import.clinicalZone", "America/Toronto");
        var writer =
                new CdsHistoryPersister(
                        fields,
                        new CdsClinicalNormalizationService(fields),
                        new CdsImportConfigurationService(properties),
                        mock(CdsProviderResolver.class),
                        preparation,
                        notes,
                        patientIssues,
                        issues,
                        extensions);
        var command =
                new CdsImportCommand(
                        directory,
                        "roundtrip",
                        "999998",
                        1,
                        null,
                        "native-export",
                        "omd-5.2",
                        false,
                        false,
                        false,
                        0,
                        null,
                        false);
        writer.prepare("run", 1, "unit", record, command);
        var references =
                writer.write("run", 1, "unit", 28, record, command, ignored -> {});
        assertThat(written.get().getNote()).isEqualTo("PAP\nIneligible\nReason:Hysterectomy");
        assertThat(written.get().isArchived()).isFalse();
        assertThat(written.get().getObservation_date()).isNotNull();
        assertThat(prepared.get().issueCode()).isEqualTo("MedHistory");
        assertThat(prepared.get().observationField()).isEqualTo("ProcedureDate");
        assertThat(prepared.get().observationDate().original()).isEqualTo("2024-02-29");
        assertThat(references).containsKeys("casemgmt_note", "casemgmt_issue");
        assertThat(
                        fields.firstToken(
                                record, "ResidualInfo/DataElement/Name", 64))
                .isEqualTo(CdsNativeExtensionContract.PREVENTION_SOURCE_CONTEXT);
        assertThat(fields.firstText(record, "Notes", 64)).isEqualTo("Recorded by clinic");
    }

    private static JsonNode context(CdsExportScreeningMapper.Result result) throws IOException {
        return new ObjectMapper().readTree(result.value().sourceContext());
    }
}
