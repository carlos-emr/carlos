/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementIssueDAO;
import io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO;
import io.github.carlos_emr.carlos.casemgmt.model.CaseManagementIssue;
import io.github.carlos_emr.carlos.casemgmt.model.Issue;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportNativeHistoryFixture;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsNativeIssuesDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
@Tag("cds-import")
@org.junit.jupiter.api.extension.ExtendWith(
        io.github.carlos_emr.carlos.test.mocks.MockInvocationReleaseExtension.class)
class CdsNativeIssuesImportServiceUnitTest {
    @TempDir Path directory;
    private final CdsFieldParser fields = new CdsFieldParser();
    private final CdsNativePreparationService preparation = mock(CdsNativePreparationService.class);
    private final CaseManagementIssueDAO patientIssues = mock(CaseManagementIssueDAO.class);
    private final IssueDAO catalogue = mock(IssueDAO.class);
    private final AtomicReference<CdsNativeIssuesDto> prepared = new AtomicReference<>();
    private final List<CaseManagementIssue> saved = new ArrayList<>();
    private final CdsNativeIssuesImportService service =
            new CdsNativeIssuesImportService(fields, preparation, patientIssues, catalogue);
    private Issue definition;

    @BeforeEach
    void setup() throws Exception {
        definition = new Issue();
        definition.setId(151L);
        definition.setCode("OMeds");
        definition.setType("system");
        definition.setRole("all");
        when(catalogue.findCandidatesByCode("OMeds")).thenReturn(List.of(definition));
        doAnswer(call -> { prepared.set(call.getArgument(5)); return null; })
                .when(preparation).store(anyString(), anyLong(), anyString(), anyString(), any(), any());
        when(preparation.load(anyString(), anyLong(), anyString(), anyString(), any(), eq(CdsNativeIssuesDto.class)))
                .thenAnswer(call -> prepared.get());
        doAnswer(call -> {
            CaseManagementIssue issue = call.getArgument(0);
            issue.setId(1001L + saved.size());
            saved.add(issue);
            return null;
        }).when(patientIssues).insertCdsMarker(any());
    }

    private CdsImportCommand command(Integer program, int shift) {
        return new CdsImportCommand(directory, "marker-fixture", "100", program, null,
                "marker-source", "ontario-md-5.2", false, false, false, shift,
                shift == 0 ? null : 1, shift != 0);
    }

    private List<CdsRecordDto> parse(byte[] xml) throws Exception {
        Path root = Files.createTempDirectory(directory, "marker-");
        var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser().parse(Files.write(root.resolve("patient.xml"), xml),
                root.resolve("parsed"), CdsInputLimitsDto.defaults(), () -> false, records::add);
        return records.stream().filter(r -> r.section().equals("NewCategory")).toList();
    }

    private CdsRecordDto marker(String program) throws Exception {
        return parse(CdsExportNativeHistoryFixture.markerDocument(41, program)).getFirst();
    }

    @Test
    void shouldRestoreEveryPatientColumn_fromTheSchemaValidatedExport() throws Exception {
        var record = marker(null);
        var command = command(null, 0);
        service.validate(record, command, null, "patient.xml");
        service.prepare("run", 1, "unit", record, command, null, "patient.xml");
        var refs = service.write("run", 1, "unit", 227, record, command);
        assertThat(refs.keySet()).containsExactly("demographic", "casemgmt_issue");
        assertThat(refs).containsEntry("demographic", "227").containsEntry("casemgmt_issue", "1001");
        var row = saved.getFirst();
        assertThat(row.getDemographic_no()).isEqualTo(227);
        assertThat(row.getIssue_id()).isEqualTo(151);
        assertThat(row.isAcute()).isTrue();
        assertThat(row.isCertain()).isFalse();
        assertThat(row.isMajor()).isTrue();
        assertThat(row.isResolved()).isFalse();
        assertThat(row.getType()).isEqualTo("general  ");
        assertThat(row.getProgram_id()).isNull();
        assertThat(row.getUpdate_date()).isEqualTo(Timestamp.valueOf("2026-09-01 12:34:56"));
        assertThat(prepared.get().marker().catalogue()).containsEntry("description", "Medication  marker\t")
                .containsEntry("priority", null);
        verify(catalogue, never()).saveIssue(any());
    }

    @Test
    void shouldKeepTheGlobalProgramSentinel_withoutMakingItRestricted() throws Exception {
        for (String program : List.of("0")) {
            var record = marker(program);
            service.prepare("run", 1, "unit", record, command(9, 0), null, "patient.xml");
            assertThat(prepared.get().marker().programId()).isEqualTo(Integer.valueOf(program));
        }
    }

    @Test
    void shouldRequireAnExplicitProgramMapping_beforeRestoringARestrictedMarker() throws Exception {
        var record = marker("77");
        assertThatThrownBy(() -> service.validate(record, command(null, 0), null, "patient.xml"))
                .hasMessage("CDS_NATIVE_ISSUE_PROGRAM_REQUIRES_REVIEW");
        service.prepare("run", 1, "unit", record, command(9, 0), null, "patient.xml");
        assertThat(prepared.get().marker().programId()).isEqualTo(9);
        assertThat(saved).isEmpty();
    }

    @Test
    void shouldShiftOnlySyntheticCopies_byTheRequestedDays() throws Exception {
        var record = marker(null);
        service.prepare("run", 1, "unit", record, command(null, 31), null, "patient.xml");
        assertThat(prepared.get().marker().updated()).isEqualTo("2026-10-02T12:34:56");
    }

    @Test
    void shouldRefuseMissingOrAmbiguousCatalogueEntries_beforePreparation() throws Exception {
        var record = marker(null);
        for (List<Issue> entries : List.of(List.<Issue>of(), List.of(definition, definition))) {
            when(catalogue.findCandidatesByCode("OMeds")).thenReturn(entries);
            assertThatThrownBy(() -> service.prepare("run", 1, "unit", record, command(null, 0), null, "patient.xml"))
                    .hasMessage("CDS_NATIVE_ISSUE_CATALOGUE_REQUIRES_REVIEW");
        }
        assertThat(prepared.get()).isNull();
        assertThat(saved).isEmpty();
    }

    @Test
    void shouldRefuseAChangedAccessRole_afterPreparationAndBeforeWriting() throws Exception {
        var record = marker(null);
        var command = command(null, 0);
        service.prepare("run", 1, "unit", record, command, null, "patient.xml");
        definition.setRole("another-role");
        assertThatThrownBy(() -> service.write("run", 1, "unit", 227, record, command))
                .hasMessage("CDS_NATIVE_ISSUE_CATALOGUE_CHANGED");
        assertThat(saved).isEmpty();
    }

    @Test
    void shouldLeaveEarlierCarriersSourceOnly_soDeletedMarkersCannotReturn() throws Exception {
        String xml = new String(CdsExportNativeHistoryFixture.markerDocument(41, null), StandardCharsets.UTF_8)
                .replace(CdsNativeExtensionContract.NATIVE_ISSUES_CATEGORY,
                        CdsNativeExtensionContract.NATIVE_ISSUES_SOURCE_CATEGORY);
        var record = parse(xml.getBytes(StandardCharsets.UTF_8)).getFirst();
        var command = command(null, 0);
        service.prepare("run", 1, "unit", record, command, null, "patient.xml");
        assertThat(service.write("run", 1, "unit", 227, record, command))
                .containsExactlyEntriesOf(java.util.Map.of("demographic", "227"));
        assertThat(saved).isEmpty();
    }

    @Test
    void shouldRestoreEveryDuplicateMarker_withoutOneLargeReceiptOrLastRecordSelection() throws Exception {
        String xml = new String(CdsExportNativeHistoryFixture.markerDocument(41, null), StandardCharsets.UTF_8);
        int start = xml.indexOf("<NewCategory>"), end = xml.indexOf("</NewCategory>") + "</NewCategory>".length();
        assertThat(start).isNotNegative();
        String carrier = xml.substring(start, end);
        var records = parse((xml.substring(0, start) + carrier.repeat(200) + xml.substring(end))
                .getBytes(StandardCharsets.UTF_8));
        assertThat(records).hasSize(200);
        var command = command(null, 0);
        for (var record : records) {
            String unit = Long.toString(record.ordinal());
            service.prepare("run", 1, unit, record, command, null, "patient.xml");
            assertThat(service.write("run", 1, unit, 227, record, command)).hasSize(2);
        }
        assertThat(saved).hasSize(200).extracting(CaseManagementIssue::getId).doesNotHaveDuplicates();
    }

    @Test
    void shouldRefuseMalformedMarkerValues_withoutSavingAPartialRow() throws Exception {
        String xml = new String(CdsExportNativeHistoryFixture.markerDocument(41, null), StandardCharsets.UTF_8);
        for (var replacement : List.of(
                java.util.Map.entry("\"version\":1", "\"version\":2"),
                java.util.Map.entry("\"acute\":\"1\"", "\"acute\":\"2\""),
                java.util.Map.entry("\"sortOrderId\":\"0\"", "\"sortOrderId\":\"bad\""),
                java.util.Map.entry("2026-09-01", "2026-02-30"),
                java.util.Map.entry("\"code\":\"OMeds\"", "\"code\":null"))) {
            assertThat(xml).contains(replacement.getKey());
            var record = parse(xml.replace(replacement.getKey(), replacement.getValue())
                    .getBytes(StandardCharsets.UTF_8)).getFirst();
            assertThatThrownBy(() -> service.validate(record, command(null, 0), null, "patient.xml"))
                    .hasMessage("CDS_NATIVE_ISSUES_MALFORMED");
        }
        assertThat(saved).isEmpty();
    }
}
