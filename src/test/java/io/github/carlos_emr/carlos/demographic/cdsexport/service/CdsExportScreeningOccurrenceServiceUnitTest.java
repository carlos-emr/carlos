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

import static io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportScreeningMapperUnitTest.*;
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

/**
 * Every captured screening becomes exactly one PastHealth occurrence with a full receipt, or the
 * patient is refused; a missing, duplicated, unwitnessed or re-imported row never passes.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportScreeningOccurrenceServiceUnitTest {
    private static final String PATIENT = "b".repeat(64);
    private final CdsExportNativeCaptureService captures = mock(CdsExportNativeCaptureService.class);
    private final CdsExportNativeRowReceiptReader witnessReader =
            mock(CdsExportNativeRowReceiptReader.class);
    private final CdsExportImmunizationCaptureValidator closure =
            mock(CdsExportImmunizationCaptureValidator.class);
    private final CdsExportPreventionConfigurationService configurations =
            mock(CdsExportPreventionConfigurationService.class);
    private final CdsExportCapturedTextRowsReader rows = mock(CdsExportCapturedTextRowsReader.class);
    private final CdsExportCohortService.Patient patient = mock(CdsExportCohortService.Patient.class);
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private final List<CdsExportClinicalOccurrenceService.Receipt> receipts = new ArrayList<>();
    private List<CdsExportCapturedTextRowDto> parents, auxiliary;
    private List<CdsExportNativeRowReceiptDto> witnesses;
    private Set<Integer> selection = Set.of(31, 32);
    private Set<String> applied = Set.of();
    private Map<String, List<CdsExportRetainedIndexDto.Reference>> references = Map.of();
    private Set<CdsCategory> categories;
    private CdsExportPatientCaptureDto source;
    private Set<String> tables;
    private long emitted;

    @BeforeEach
    void prepare() throws Exception {
        categories = EnumSet.copyOf(CdsExportNoteSourceReader.CATEGORIES);
        categories.add(CdsCategory.DEMOGRAPHICS);
        when(patient.patient()).thenReturn(27);
        parents =
                List.of(
                        parent(31, Map.of("refused", "1")),
                        parent(32, Map.of("prevention_type", "OtherB")));
        auxiliary =
                List.of(
                        extension(41, 31, "result", "abnormal"),
                        extension(42, 31, "comments", "Repeat"),
                        precision(51, 32, "YYYY"));
        witnesses = new ArrayList<>();
        for (var row : parents) witnesses.add(witness(row));
        for (var row : auxiliary) witnesses.add(witness(row));
        var definitions = classifier();
        when(configurations.readEmbedded(any(), any(), anySet(), any(), anyLong(), anyLong(), any()))
                .thenReturn(definitions);
        when(closure.verifyEmbedded(any(), any(), anySet(), any(), anyLong(), anyLong(), any()))
                .thenAnswer(
                        call ->
                                new CdsExportPreventionScopePlanner.Result(
                                        27,
                                        SOURCE,
                                        definitions.configurationSha256(),
                                        definitions.catalogueSha256(),
                                        SOURCE,
                                        Set.of(),
                                        selection,
                                        Map.of(),
                                        selection.size()));
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
    }

    private CdsExportScreeningOccurrenceService service() {
        return new CdsExportScreeningOccurrenceService(
                captures, witnessReader, closure, configurations, rows);
    }

    private void capture() {
        var scopes = new TreeMap<String, CdsExportCapturedTableDto>();
        for (String table : CdsExportPreventionSources.SHARED)
            scopes.put(table, new CdsExportCapturedTableDto(Set.of("id"), 0, SOURCE));
        scopes.put(
                CdsExportPreventionSources.SCREENINGS,
                new CdsExportCapturedTableDto(
                        Set.copyOf(Plan.PREVENTIONS_FULL.columns()), parents.size(), SOURCE));
        scopes.put(
                CdsExportPreventionSources.SCREENING_EXTENSIONS,
                new CdsExportCapturedTableDto(
                        Set.copyOf(Plan.PREVENTIONS_EXT.columns()),
                        count(CdsExportPreventionSources.SCREENING_EXTENSIONS),
                        SOURCE));
        scopes.put(
                CdsExportPreventionSources.SCREENING_PRECISION,
                new CdsExportCapturedTableDto(
                        Set.copyOf(Plan.PREVENTION_PRECISION.columns()),
                        count(CdsExportPreventionSources.SCREENING_PRECISION),
                        SOURCE));
        tables = Set.copyOf(scopes.keySet());
        var generation = mock(CdsExportNativeCaptureDto.class);
        when(generation.tables()).thenReturn(scopes);
        when(generation.values())
                .thenReturn(new CdsExportStagedFileDto("1".repeat(64), "SOURCE_VALUES", SOURCE, 1));
        when(generation.index())
                .thenReturn(new CdsExportStagedFileDto("2".repeat(64), "SOURCE_INDEX", SOURCE, 1));
        source =
                new CdsExportPatientCaptureDto(
                        generation, new CdsExportStagedFileDto("3".repeat(64), "SOURCE_MANIFEST", SOURCE, 1));
        try {
            when(rows.forEachTable(
                            any(), any(), anySet(), anySet(), any(), anyLong(), anyLong(), anyInt(),
                            any(), any()))
                    .thenAnswer(
                            call -> {
                                for (var row : auxiliary)
                                    call.<CdsExportCapturedTextRowsReader.Rows>getArgument(9)
                                            .accept(row);
                                return generation;
                            });
            when(rows.forEach(
                            any(), any(), anySet(), anyString(), any(), anyLong(), anyLong(),
                            anyInt(), any(), any()))
                    .thenAnswer(
                            call -> {
                                for (var row : parents)
                                    call.<CdsExportCapturedTextRowsReader.Rows>getArgument(9)
                                            .accept(row);
                                return generation;
                            });
        } catch (IOException impossible) {
            throw new UncheckedIOException(impossible);
        }
    }

    private long count(String table) {
        return auxiliary.stream().filter(row -> row.table().equals(table)).count();
    }

    private byte[] compose() throws Exception {
        capture();
        var command =
                new CdsExportCommand(
                        "screening-assembly",
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
        var selected = Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.PAST_HEALTH);
        var output = new ByteArrayOutputStream();
        new CdsExportXmlComposer()
                .composeFromSource(
                        output,
                        SOURCE,
                        PATIENT,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        selected,
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
                        selected,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        CdsExportXmlLimitsDto.defaults(),
                        ignored -> {},
                        () -> {});
        return output.toByteArray();
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
        var state = new CdsNativeRowState(row.table(), row.fields().get("id"), columns, cells);
        return new CdsExportNativeRowReceiptDto(
                row.table(), state.id(), row.recordKey(), state.digest(), state);
    }

    private static CdsExportIdentityDto identity(String id) {
        return new CdsExportIdentityDto(
                PATIENT,
                CdsCategory.PAST_HEALTH,
                CdsImportKeys.hash("carlos-native-occurrence-v1", "preventions", id));
    }

    @Test
    void shouldEmitEveryCapturedScreeningAsOnePastHealthOccurrence_withCompleteReceipts()
            throws Exception {
        var xml = compose();
        assertThat(emitted).isEqualTo(2);
        assertThat(receipts)
                .extracting(CdsExportClinicalOccurrenceService.Receipt::identity)
                .containsExactly(identity("31"), identity("32"));
        assertThat(receipts.get(0).fields()).hasSize(16 + 4 * 2);
        assertThat(receipts.get(1).fields()).hasSize(16 + 5);
        assertThat(receipts)
                .allSatisfy(
                        receipt -> {
                            assertThat(receipt.table()).isEqualTo("preventions");
                            assertThat(receipt.importedUnit()).isNull();
                            assertThat(receipt.fields().values())
                                    .noneMatch(
                                            field ->
                                                    field.outcome()
                                                            == CdsExportFieldAssessmentDto.Outcome
                                                                    .REQUIRES_REVIEW);
                        });
        var factory = DocumentBuilderFactory.newDefaultInstance();
        factory.setNamespaceAware(true);
        var document = factory.newDocumentBuilder().parse(new ByteArrayInputStream(xml));
        var entries = document.getElementsByTagNameNS("cds", "PastHealth");
        assertThat(entries.getLength()).isEqualTo(2);
        assertThat(
                        document.getElementsByTagNameNS(
                                        "cds", "PastHealthProblemDescriptionOrProcedures")
                                .item(0)
                                .getTextContent())
                .isEqualTo("PAP\nRefused\nResult:abnormal");
        assertThat(document.getElementsByTagNameNS("cds_dt", "YearOnly").item(0).getTextContent())
                .isEqualTo("2024");
        verify(captures).authorizeSources(any(), eq(patient), eq(tables), eq(control));
    }

    @Test
    void shouldRefuseAScreeningLeftOutOfTheCapture_insteadOfASmallerPackage() {
        selection = Set.of(31, 32, 33);
        assertThatThrownBy(this::compose)
                .hasMessage("CDS_EXPORT_SCREENING_OCCURRENCE_SCOPE_INVALID");
    }

    @Test
    void shouldRefuseADuplicatedScreeningRow_insteadOfTwoOccurrences() {
        parents = List.of(parents.get(0), parents.get(0), parents.get(1));
        assertThatThrownBy(this::compose)
                .hasMessage("CDS_EXPORT_SCREENING_OCCURRENCE_SCOPE_INVALID");
        assertThat(receipts).isEmpty();
    }

    @Test
    void shouldRefuseAnUnwitnessedScreeningRow_forScreeningOccurrenceContract() {
        witnesses.removeIf(witness -> witness.id().equals("42"));
        assertThatThrownBy(this::compose)
                .hasMessage("CDS_EXPORT_SCREENING_OCCURRENCE_SCOPE_INVALID");
    }

    @Test
    void shouldRefuseAWitnessThatDisagrees_withTheCapturedRow() {
        witnesses.removeIf(witness -> witness.id().equals("31"));
        witnesses.add(witness(parent(31, Map.of("refused", "0"))));
        assertThatThrownBy(this::compose)
                .hasMessage("CDS_EXPORT_IMMUNIZATION_PROJECTION_EVIDENCE_INVALID");
    }

    @Test
    void shouldRefuseAnExtensionOwnedByAnUnselectedPrevention_forScreeningOccurrenceContract() {
        auxiliary = List.of(extension(41, 30, "result", "abnormal"));
        assertThatThrownBy(this::compose)
                .hasMessage("CDS_EXPORT_SCREENING_OCCURRENCE_SCOPE_INVALID");
    }

    @Test
    void shouldHoldADeletedScreening_forReview() {
        parents = List.of(parent(31, Map.of("deleted", "1")), parents.get(1));
        witnesses.removeIf(witness -> witness.id().equals("31"));
        witnesses.add(witness(parents.get(0)));
        assertThatThrownBy(this::compose).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
        assertThat(receipts).isEmpty();
    }

    @Test
    void shouldHoldAScreeningThatAnImportRecordedAsAnotherCategory_forReview() {
        applied = Set.of("run:unit");
        references =
                Map.of(
                        "run:unit",
                        List.of(new CdsExportRetainedIndexDto.Reference("preventions", "31", SOURCE)));
        assertThatThrownBy(this::compose).hasMessage("CDS_EXPORT_AMBIGUOUS_SOURCE");
    }

    @Test
    void shouldRefuse_whenPastHealthWasNotRequested() {
        categories = EnumSet.of(CdsCategory.DEMOGRAPHICS, CdsCategory.IMMUNIZATIONS);
        assertThatThrownBy(this::compose)
                .hasMessage("CDS_EXPORT_SCREENING_OCCURRENCE_SCOPE_INVALID");
        verifyNoInteractions(closure);
    }

    @Test
    void shouldEmitNothingAndSucceed_forAPatientWithoutScreenings() throws Exception {
        selection = Set.of();
        parents = List.of();
        auxiliary = List.of();
        witnesses.clear();
        compose();
        assertThat(emitted).isZero();
        assertThat(receipts).isEmpty();
    }
}
