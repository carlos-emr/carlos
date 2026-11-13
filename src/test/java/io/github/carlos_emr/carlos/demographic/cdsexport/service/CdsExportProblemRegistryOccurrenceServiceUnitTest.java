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

import static io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportProblemRegistryMapperUnitTest.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.*;
import org.w3c.dom.Element;

/**
 * Every captured current Disease Registry entry and episode becomes exactly one ProblemList
 * occurrence with a full receipt, through the schema-validating composer; deleted ones are
 * accounted and not exported; a missing, unwitnessed or re-imported row never passes.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportProblemRegistryOccurrenceServiceUnitTest {
    private static final String SOURCE = "a".repeat(64), PATIENT = "b".repeat(64);
    private final CdsExportNativeCaptureService captures = mock(CdsExportNativeCaptureService.class);
    private final CdsExportNativeRowReceiptReader witnessReader =
            mock(CdsExportNativeRowReceiptReader.class);
    private final CdsExportCapturedTextRowsReader rows = mock(CdsExportCapturedTextRowsReader.class);
    private final CdsExportCohortService.Patient patient = mock(CdsExportCohortService.Patient.class);
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private final List<CdsExportClinicalOccurrenceService.Receipt> receipts = new ArrayList<>();
    private List<CdsExportCapturedTextRowDto> parents, auxiliary;
    private List<CdsExportNativeRowReceiptDto> witnesses;
    private Set<String> applied = Set.of();
    private Map<String, List<CdsExportRetainedIndexDto.Reference>> references = Map.of();
    private Set<String> tables;
    private long emitted;

    @BeforeEach
    void prepare() {
        when(patient.patient()).thenReturn(27);
        parents =
                new ArrayList<>(
                        List.of(
                                episode(6, Map.of("status", "Complete", "endDate", "2024-09-30")),
                                registry(5, Map.of()),
                                registry(7, Map.of("status", "C", "coding_system", "SnomedCore", "dxresearch_code", "44054006")),
                                registry(8, Map.of("status", "D"))));
        auxiliary =
                new ArrayList<>(
                        List.of(
                                reference("icd9", "2500", "icd9", "1301", "2500", "DIABETES MELLITUS"),
                                precision(9, 5, "4", "YYYY-MM"),
                                precision(10, 8, "4", "YYYY")));
        witnesses = new ArrayList<>();
        for (var row : parents) witnesses.add(witness(row));
        for (var row : auxiliary)
            if (!row.table().equals(CdsExportProblemRegistrySources.REFERENCES))
                witnesses.add(witness(row));
    }

    static CdsExportNativeRowReceiptDto witness(CdsExportCapturedTextRowDto row) {
        var columns = new ArrayList<CdsNativeRowDigest.Column>();
        var cells = new ArrayList<CdsNativeRowState.Cell>();
        for (var field : new TreeMap<>(row.fields()).entrySet()) {
            columns.add(new CdsNativeRowDigest.Column(field.getKey(), "VARCHAR", 255, 0));
            var text = field.getValue();
            cells.add(
                    new CdsNativeRowState.Cell(
                            text == null,
                            text == null ? 0 : text.getBytes(StandardCharsets.UTF_8).length,
                            text == null
                                    ? null
                                    : HexFormat.of()
                                            .formatHex(
                                                    CdsImportKeys.digest()
                                                            .digest(
                                                                    text.getBytes(
                                                                            StandardCharsets
                                                                                    .UTF_8)))));
        }
        String id =
                row.fields()
                        .get(
                                row.table().equals(CdsExportProblemRegistrySources.REGISTRY)
                                        ? "dxresearch_no"
                                        : "id");
        var state = new CdsNativeRowState(row.table(), id, columns, cells);
        return new CdsExportNativeRowReceiptDto(
                row.table(), id, row.recordKey(), state.digest(), state);
    }

    private CdsExportProblemRegistryOccurrenceService service() {
        return new CdsExportProblemRegistryOccurrenceService(captures, witnessReader, rows);
    }

    private long count(String table, List<CdsExportCapturedTextRowDto> source) {
        return source.stream().filter(row -> row.table().equals(table)).count();
    }

    private CdsExportPatientCaptureDto capture() throws IOException {
        var scopes = new TreeMap<String, CdsExportCapturedTableDto>();
        scopes.put(
                CdsExportProblemRegistrySources.REGISTRY,
                new CdsExportCapturedTableDto(
                        Set.copyOf(Plan.DISEASE_REGISTRY.columns()),
                        count(CdsExportProblemRegistrySources.REGISTRY, parents),
                        SOURCE));
        scopes.put(
                CdsExportProblemRegistrySources.EPISODES,
                new CdsExportCapturedTableDto(
                        Set.copyOf(Plan.EPISODES.columns()),
                        count(CdsExportProblemRegistrySources.EPISODES, parents),
                        SOURCE));
        scopes.put(
                CdsExportProblemRegistrySources.PRECISION,
                new CdsExportCapturedTableDto(
                        Set.copyOf(Plan.DISEASE_REGISTRY_PRECISION.columns()),
                        count(CdsExportProblemRegistrySources.PRECISION, auxiliary),
                        SOURCE));
        scopes.put(
                CdsExportProblemRegistrySources.REFERENCES,
                new CdsExportCapturedTableDto(
                        CdsExportDiagnosisCodeReferenceSourceReader.COLUMNS,
                        count(CdsExportProblemRegistrySources.REFERENCES, auxiliary),
                        SOURCE));
        tables = Set.copyOf(scopes.keySet());
        var generation = mock(CdsExportNativeCaptureDto.class);
        when(generation.tables()).thenReturn(scopes);
        when(generation.values())
                .thenReturn(new CdsExportStagedFileDto("1".repeat(64), "SOURCE_VALUES", SOURCE, 1));
        when(generation.index())
                .thenReturn(new CdsExportStagedFileDto("2".repeat(64), "SOURCE_INDEX", SOURCE, 1));
        when(rows.forEachTable(
                        any(), any(), anySet(), anySet(), any(), anyLong(), anyLong(), anyInt(),
                        any(), any()))
                .thenAnswer(
                        call -> {
                            Set<String> wanted = call.getArgument(3);
                            var all = new ArrayList<>(parents);
                            all.addAll(auxiliary);
                            for (var row : all)
                                if (wanted.contains(row.table()))
                                    call.<CdsExportCapturedTextRowsReader.Rows>getArgument(9)
                                            .accept(row);
                            return generation;
                        });
        when(witnessReader.scan(any(), any(), anySet(), any(), anyLong(), anyLong(), any(), any()))
                .thenAnswer(
                        call -> {
                            for (var witness : witnesses)
                                call.<CdsExportNativeRowReceiptReader.Receipts>getArgument(7)
                                        .accept(witness);
                            return new CdsExportNativeRowReceiptsDto(
                                    SOURCE,
                                    witnesses.stream()
                                            .collect(
                                                    Collectors.groupingBy(
                                                            CdsExportNativeRowReceiptDto::table,
                                                            Collectors.counting())),
                                    witnesses.size(),
                                    0,
                                    Set.of());
                        });
        return new CdsExportPatientCaptureDto(
                generation,
                new CdsExportStagedFileDto("3".repeat(64), "SOURCE_MANIFEST", SOURCE, 1));
    }

    private org.w3c.dom.Document compose() throws Exception {
        var source = capture();
        var categories = EnumSet.of(CdsCategory.DEMOGRAPHICS, CdsCategory.PROBLEM_LIST);
        var command =
                new CdsExportCommand(
                        "registry-assembly",
                        "999998",
                        null,
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportCommand.Selection.SELECTED_PATIENTS,
                        List.of(),
                        List.of(27),
                        categories,
                        "receiver",
                        "audit");
        var lease =
                new CdsExportLeaseDto(
                        UUID.randomUUID().toString(),
                        1,
                        SOURCE,
                        new CdsExportJobDocumentDto(
                                2,
                                new CdsExportActorDto("agent", "999998", null, null),
                                command,
                                SOURCE,
                                "sender",
                                SOURCE,
                                SOURCE,
                                new CdsExportStorageBindingDto("primary", "primary", SOURCE)));
        var index =
                new CdsExportRetainedIndexDto(
                        SOURCE,
                        0,
                        0,
                        Map.of("cds_import_unit", Map.of(), "cds_import_field", Map.of()),
                        applied,
                        references,
                        Map.of());
        var output = new ByteArrayOutputStream();
        new CdsExportXmlComposer()
                .composeFromSource(
                        output,
                        SOURCE,
                        PATIENT,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        categories,
                        emitter -> {
                            emitter.emit(demographic());
                            emitted =
                                    service()
                                            .append(
                                                    lease,
                                                    patient,
                                                    PATIENT,
                                                    tables,
                                                    source,
                                                    index,
                                                    new CdsExportClinicalOccurrenceService.Bounds(
                                                            100000, 8L << 20, 1000, 8L << 20, 100,
                                                            1000),
                                                    control,
                                                    emitter,
                                                    receipts::add);
                        },
                        ignored -> {},
                        8L << 20,
                        () -> {});
        new CdsExportXmlValidator()
                .validate(
                        new ByteArrayInputStream(output.toByteArray()),
                        "27",
                        categories,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
        var factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        return factory.newDocumentBuilder().parse(new ByteArrayInputStream(output.toByteArray()));
    }

    private static CdsExportXmlComposer.Section demographic() {
        return new CdsExportXmlComposer.Section(
                new CdsExportIdentityDto(PATIENT, CdsCategory.DEMOGRAPHICS, "f".repeat(64)),
                xml -> {
                    xml.start("cds", "Names");
                    xml.start("cds_dt", "LegalName");
                    xml.attribute("", "namePurpose", "L");
                    for (var part : List.of(Map.entry("FirstName", "GIV"), Map.entry("LastName", "FAMC"))) {
                        xml.start("cds_dt", part.getKey());
                        xml.element("cds_dt", "Part", "Fixture");
                        xml.element("cds_dt", "PartType", part.getValue());
                        xml.end();
                    }
                    xml.end();
                    xml.end();
                    xml.element("cds", "DateOfBirth", "1980-01-02");
                    xml.element("cds", "Gender", "U");
                    xml.element("cds", "UniqueVendorIdSequence", "27");
                    xml.start("cds", "PersonStatusCode");
                    xml.element("cds", "PersonStatusAsEnum", "A");
                    xml.end();
                });
    }

    private static CdsExportIdentityDto identity(String table, String id) {
        return new CdsExportIdentityDto(
                PATIENT,
                CdsCategory.PROBLEM_LIST,
                CdsImportKeys.hash("carlos-native-occurrence-v1", table, id));
    }

    private static String text(Element parent, String namespace, String name) {
        var found = parent.getElementsByTagNameNS(namespace, name);
        return found.getLength() == 0 ? null : found.item(0).getTextContent();
    }

    @Test
    void shouldEmitEveryCurrentEntryAndEpisodeAsOneProblem_withCompleteReceipts() throws Exception {
        var xml = compose();
        assertThat(emitted).isEqualTo(3);
        assertThat(receipts)
                .extracting(CdsExportClinicalOccurrenceService.Receipt::identity)
                .containsExactly(
                        identity("Episode", "6"), identity("dxresearch", "5"), identity("dxresearch", "7"));
        assertThat(receipts)
                .allSatisfy(
                        receipt -> {
                            assertThat(receipt.importedUnit()).isNull();
                            assertThat(receipt.fields().values())
                                    .noneMatch(
                                            field ->
                                                    field.outcome()
                                                            == CdsExportFieldAssessmentDto.Outcome
                                                                    .REQUIRES_REVIEW);
                        });
        // The entry's own fields, its precision row and the code-table row are all accounted.
        assertThat(receipts.get(1).fields()).hasSize(9 + 5 + 6);
        var problems = xml.getElementsByTagNameNS("cds", "ProblemList");
        assertThat(problems.getLength()).isEqualTo(3);
        var episode = (Element) problems.item(0);
        assertThat(text(episode, "cds", "ProblemDiagnosisDescription")).isEqualTo("Normal");
        assertThat(text(episode, "cds", "ProblemStatus")).isEqualTo("Resolved");
        assertThat(text(episode, "cds_dt", "StandardCodingSystem")).isEqualTo("SnomedCore");
        var registry = (Element) problems.item(1);
        assertThat(text(registry, "cds", "ProblemDiagnosisDescription"))
                .isEqualTo("DIABETES MELLITUS");
        assertThat(text(registry, "cds_dt", "StandardCode")).isEqualTo("250.0");
        assertThat(text(registry, "cds_dt", "YearMonth")).isEqualTo("2024-03");
        assertThat(text(registry, "cds", "ProblemStatus")).isEqualTo("Active");
        var resolved = (Element) problems.item(2);
        assertThat(text(resolved, "cds_dt", "StandardCode")).isEqualTo("44054006");
        assertThat(text(resolved, "cds_dt", "StandardCodeDescription")).isEmpty();
        assertThat(
                        resolved.getElementsByTagNameNS("cds", "ResolutionDate")
                                .item(0)
                                .getTextContent())
                .isEqualTo("2024-06-01");
        verify(captures).authorizeSources(any(), eq(patient), eq(tables), eq(control));
        verify(control, atLeastOnce()).authorizeCategory(27, CdsCategory.PROBLEM_LIST, false);
    }

    @Test
    void shouldExportNothingAndHoldNoOne_forADeletedEntryOnly() throws Exception {
        parents = List.of(registry(8, Map.of("status", "D")));
        auxiliary = List.of(precision(10, 8, "4", "YYYY"));
        witnesses = new ArrayList<>(List.of(witness(parents.get(0)), witness(auxiliary.get(0))));
        var xml = compose();
        assertThat(emitted).isZero();
        assertThat(receipts).isEmpty();
        assertThat(xml.getElementsByTagNameNS("cds", "ProblemList").getLength()).isZero();
    }

    @Test
    void shouldRefuseAnUnwitnessedEntry_insteadOfASmallerPackage() {
        witnesses.removeIf(witness -> witness.id().equals("7"));
        assertThatThrownBy(this::compose)
                .hasMessage("CDS_EXPORT_PROBLEM_REGISTRY_OCCURRENCE_SCOPE_INVALID");
    }

    @Test
    void shouldRefuseAWitnessThatDisagrees_withTheCapturedEntry() {
        witnesses.removeIf(witness -> witness.id().equals("5"));
        witnesses.add(witness(registry(5, Map.of("status", "C"))));
        assertThatThrownBy(this::compose)
                .hasMessage("CDS_EXPORT_PROBLEM_REGISTRY_OCCURRENCE_SCOPE_INVALID");
    }

    @Test
    void shouldRefuseAPrecisionRowOfNoCapturedEntry_forOccurrenceScope() {
        auxiliary.add(precision(11, 99, "4", "YYYY"));
        witnesses.add(witness(auxiliary.getLast()));
        assertThatThrownBy(this::compose)
                .hasMessage("CDS_EXPORT_PROBLEM_REGISTRY_OCCURRENCE_SCOPE_INVALID");
    }

    @Test
    void shouldRefuseACodeTableRowNoExportedItemNamed_forOccurrenceScope() {
        auxiliary.add(reference("icd9", "4019", "icd9", "88", "4019", "HYPERTENSION NOS"));
        assertThatThrownBy(this::compose)
                .hasMessage("CDS_EXPORT_PROBLEM_REGISTRY_OCCURRENCE_SCOPE_INVALID");
    }

    @Test
    void shouldHoldAnEntryAnImportUnitAlreadyNames_insteadOfExportingItTwice() {
        applied = Set.of("run:unit");
        references =
                Map.of("run:unit", List.of(new CdsExportRetainedIndexDto.Reference("dxresearch", "5", "c".repeat(64))));
        assertThatThrownBy(this::compose).hasMessage("CDS_EXPORT_AMBIGUOUS_SOURCE");
        assertThat(receipts).isEmpty();
    }

    @Test
    void shouldHoldAnEntryWithAnUnknownStatus_withItsSpecificReviewCode() {
        parents.set(1, registry(5, Map.of("status", "I")));
        witnesses.set(1, witness(parents.get(1)));
        assertThatThrownBy(this::compose)
                .hasMessage("CDS_EXPORT_NATIVE_PROBLEM_REGISTRY_STATUS_REQUIRES_REVIEW");
    }
}
