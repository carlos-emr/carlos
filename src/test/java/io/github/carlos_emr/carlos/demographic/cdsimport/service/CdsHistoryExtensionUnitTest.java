/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.casemgmt.dao.CaseManagementNoteExtDAO;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportNativeHistoryFixture;
import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@Tag("unit")
@Tag("cds-import")
class CdsHistoryExtensionUnitTest {
    @TempDir Path directory;
    private final CdsFieldParser fields = new CdsFieldParser();

    private CdsRecordDto parse(byte[] document) throws Exception {
        Path root = Files.createTempDirectory(directory, "extensions-");
        var records = new ArrayList<CdsRecordDto>();
        new CdsXmlParser().parse(Files.write(root.resolve("patient.xml"), document), root.resolve("parsed"),
                CdsInputLimitsDto.defaults(), () -> false, records::add);
        return records.stream().filter(r -> r.section().equals("FamilyHistory")).findFirst().orElseThrow();
    }

    private List<CdsRecordDto> children(CdsRecordDto record) throws Exception {
        var children = new ArrayList<CdsRecordDto>();
        new CdsHistoryExtensionChildren(fields).forEach(record, CdsInputLimitsDto.defaults(), children::add);
        for (var child : children) fields.verifyRecord(child);
        return children;
    }

    private CdsImportCommand command(int shift) {
        return new CdsImportCommand(directory, "cpp-fixture", "999998", null, null, "clinic", "omd-5.2",
                false, false, false, shift, shift == 0 ? null : 9, shift != 0);
    }

    private CdsHistoryExtensionNativeDto value(CdsRecordDto record, int shift) throws Exception {
        return new CdsHistoryExtensionPersister(fields, null, mock(CaseManagementNoteExtDAO.class))
                .normalize(record, command(shift));
    }

    private byte[] document(List<Map<String, String>> rows) throws Exception {
        return CdsExportNativeHistoryFixture.extensionDocument(CdsCategory.FAMILY_HISTORY, "FamHistory", rows);
    }

    private Map<String, String> row(String key, String value, String date) {
        var result = new LinkedHashMap<String, String>();
        result.put("key_val", key);
        result.put("value", value);
        result.put("date_value", date);
        return result;
    }

    @Test
    void shouldRestoreEveryCurrentClinicalExtension_fromTheActualCarlosDocument() throws Exception {
        var rows = List.of(row("Start Date", "YYYY", "2001-07-23"),
                row("Resolution Date", "YYYY-MM", "2005-03-16"), row("Procedure Date", null, "2000-01-02"),
                row("Age at Onset", "childhood", null), row("Treatment", "Exact  treatment\t", null),
                row("Problem Status", "Resolved", null), row("Exposure Details", "Indoor exposure", null),
                row("Relationship", "Mother", null), row("Life Stage", "A", null),
                row("Problem Description", "Long description", null), row("Procedure", "Synthetic procedure", null));
        var parent = parse(document(rows));
        var children = children(parent);
        assertThat(children).hasSize(rows.size());
        for (int i = 0; i < rows.size(); i++) {
            var actual = value(children.get(i), 0);
            var expected = rows.get(i);
            assertThat(actual.parentDigest()).isEqualTo(parent.effectiveSha256());
            assertThat(actual.key()).isEqualTo(expected.get("key_val"));
            assertThat(actual.value()).isEqualTo(expected.get("value"));
            assertThat(actual.date()).isEqualTo(expected.get("date_value") == null ? null : LocalDate.parse(expected.get("date_value")));
            assertThat(actual.apply()).isTrue();
            assertThat(actual.diagnostics()).isEmpty();
        }
    }

    @Test
    void shouldPreserveDuplicateCardinality_withoutGrowingOneUnitReceipt() throws Exception {
        var rows = new ArrayList<Map<String, String>>();
        for (int i = 0; i < 300; i++) rows.add(row("Treatment", i % 2 == 0 ? "First" : "Second", null));
        var children = children(parse(document(rows)));
        assertThat(children).hasSize(300);
        assertThat(children).extracting(CdsRecordDto::sourceXPath).doesNotHaveDuplicates();
        for (int i = 0; i < children.size(); i++) {
            assertThat(children.get(i).ordinal()).isEqualTo(i + 1);
            assertThat(value(children.get(i), 0).value()).isEqualTo(rows.get(i).get("value"));
        }
        assertThat(children.stream().map(CdsRecordDto::fieldsFile).distinct().count()).isEqualTo(1);
        assertThat(children.stream().map(CdsRecordDto::valuesFile).distinct().count()).isEqualTo(1);
    }

    @Test
    void shouldRespectAnEditedStandardValue_insteadOfReplayingTheOldCarrier() throws Exception {
        String xml = new String(document(List.of(row("Treatment", "Taken", null))), StandardCharsets.UTF_8);
        var children = children(parse(xml.replace(">Taken<", ">Reviewed<").getBytes(StandardCharsets.UTF_8)));
        assertThat(children).hasSize(1);
        var actual = value(children.getFirst(), 0);
        assertThat(actual.value()).isEqualTo("Reviewed");
        assertThat(actual.diagnostics()).extracting(CdsClinicalDiagnosticDto::code)
                .containsExactly("CDS_HISTORY_EXTENSION_CARRIER_NOT_APPLIED");
    }

    @Test
    void shouldCreateNativeExtensionValues_forAnOrdinaryCdsSender() throws Exception {
        var parent = CdsClinicalFixture.record(directory, "FamilyHistory", Map.of(
                "ProblemDiagnosisProcedureDescription", "Family item", "Treatment", "Observed", "StartDate/YearOnly", "2001"));
        var children = children(parent);
        assertThat(children).hasSize(2);
        var values = new ArrayList<CdsHistoryExtensionNativeDto>();
        for (var child : children) values.add(value(child, 0));
        assertThat(values).extracting(CdsHistoryExtensionNativeDto::key).containsExactly("Start Date", "Treatment");
        assertThat(values.getFirst().value()).isEqualTo("YYYY");
        assertThat(values.getFirst().date()).isEqualTo(LocalDate.of(2001, 1, 1));
    }

    @Test
    void shouldKeepNullAndEmptyStoredValuesDistinct_whenBothRowsAreCarried() throws Exception {
        var children = children(parse(document(List.of(row("Treatment", null, null), row("Treatment", "", null)))));
        assertThat(children).hasSize(2);
        assertThat(value(children.getFirst(), 0).value()).isNull();
        assertThat(value(children.getLast(), 0).value()).isEmpty();
    }

    @Test
    void shouldShiftFullDatesAndRefusePartialDateShifts_forSyntheticImports() throws Exception {
        var full = children(parse(document(List.of(row("Start Date", null, "2026-01-01"))))).getFirst();
        assertThat(value(full, 2).date()).isEqualTo(LocalDate.of(2026, 1, 3));
        var partial = children(parse(document(List.of(row("Start Date", "YYYY", "2026-01-01"))))).getFirst();
        assertThatThrownBy(() -> value(partial, 2)).hasMessage("HISTORY_EXTENSION_PARTIAL_DATE_SHIFT_REQUIRES_REVIEW");
    }

    @Test
    void shouldReportAnUnrepresentableStoredDate_withoutInventingAnAnchor() throws Exception {
        var child = children(parse(document(List.of(row("Start Date", "YYYY", "0000-00-00"))))).getFirst();
        var actual = value(child, 0);
        assertThat(actual.date()).isNull();
        assertThat(actual.value()).isEqualTo("YYYY");
        assertThat(actual.diagnostics()).extracting(CdsClinicalDiagnosticDto::code)
                .containsExactly("CDS_HISTORY_EXTENSION_DATE_RETAINED_IN_SOURCE");
    }

    @Test
    void shouldRefuseUnavailableDiskSpace_beforePublishingADerivedChild() throws Exception {
        var record = parse(document(List.of(row("Treatment", "Original", null))));
        var children = new ArrayList<CdsRecordDto>();
        var limits = new CdsInputLimitsDto(256, 65536, Long.MAX_VALUE, 24L * 60 * 60 * 1000, 8192);
        assertThatThrownBy(() -> new CdsHistoryExtensionChildren(fields).forEach(record, limits, children::add))
                .isInstanceOf(java.io.IOException.class);
        assertThat(children).isEmpty();
    }
    @Test
    void shouldRefuseAChildWhoseNoteIsNotBound_toItsOwnPatientAndParentUnit() throws Exception {
        var child = children(parse(document(List.of(row("Treatment", "Original", null))))).getFirst();
        var prepared = value(child, 0);
        var preparation = mock(CdsNativePreparationService.class);
        when(preparation.load("run", 1, "child", child.effectiveSha256(), command(0), CdsHistoryExtensionNativeDto.class))
                .thenReturn(prepared);
        var extensions = mock(CaseManagementNoteExtDAO.class);
        var writer = new CdsHistoryExtensionPersister(fields, preparation, extensions);
        var manager = mock(jakarta.persistence.EntityManager.class);
        @SuppressWarnings("unchecked")
        var query = (jakarta.persistence.TypedQuery<io.github.carlos_emr.carlos.commn.model.CdsImportUnit>)
                mock(jakarta.persistence.TypedQuery.class, RETURNS_SELF);
        when(manager.createQuery(org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.eq(io.github.carlos_emr.carlos.commn.model.CdsImportUnit.class))).thenReturn(query);
        var receipts = mock(CdsTargetReceiptService.class);
        when(query.getResultList()).thenReturn(List.of());
        assertThatThrownBy(() -> writer.write("run", 1, "child", 227, child, command(0), manager, receipts, ignored -> {}))
                .hasMessage("HISTORY_EXTENSION_PARENT_NOT_BOUND");
        var parent = new io.github.carlos_emr.carlos.commn.model.CdsImportUnit();
        parent.setState("COMMITTED");
        parent.setSourceDigest(prepared.parentDigest());
        parent.setTargetReferences("{\"protocol\":2,\"refs\":[{\"table\":\"casemgmt_note\",\"id\":\"91\",\"digest\":\"" + "a".repeat(64) + "\"}]}");
        parent.setTargetDigest("b".repeat(64));
        when(query.getResultList()).thenReturn(List.of(parent));
        assertThatThrownBy(() -> writer.write("run", 1, "child", 227, child, command(0), manager, receipts, ignored -> {}))
                .hasMessage("HISTORY_EXTENSION_PARENT_NOT_PROVEN");
        when(receipts.verify(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        var count = mock(jakarta.persistence.Query.class, RETURNS_SELF);
        when(manager.createNativeQuery(org.mockito.ArgumentMatchers.anyString())).thenReturn(count);
        when(count.getSingleResult()).thenReturn(0L);
        assertThatThrownBy(() -> writer.write("run", 1, "child", 227, child, command(0), manager, receipts, ignored -> {}))
                .hasMessage("HISTORY_EXTENSION_PARENT_PATIENT_MISMATCH");
        verifyNoInteractions(extensions);
    }

    @Test
    void shouldRefuseAlteredDerivedValues_beforePreparingANativeWrite() throws Exception {
        var child = children(parse(document(List.of(row("Treatment", "Original", null))))).getFirst();
        Path values = child.valuesFile();
        byte[] bytes = Files.readAllBytes(values);
        byte[] needle = "Original".getBytes(StandardCharsets.UTF_8);
        int at = -1;
        for (int i = 0; i <= bytes.length - needle.length; i++) {
            boolean same = true;
            for (int j = 0; j < needle.length; j++) same &= bytes[i + j] == needle[j];
            if (same) { at = i; break; }
        }
        assertThat(at).isGreaterThanOrEqualTo(0);
        bytes[at] = 'X';
        Files.write(values, bytes);
        assertThatThrownBy(() -> value(child, 0)).isInstanceOf(java.io.IOException.class);
    }

    @Test
    void shouldReadOnlyTheCurrentExtensionRows_withoutReplayingPreviousRevisions() throws Exception {
        String details = "{\"version\":1,\"nativeHistory\":{\"version\":1,\"revisions\":["
                + "{\"extensions\":[{\"key\":\"Treatment\",\"value\":\"Old\"}]},"
                + "{\"extensions\":[{\"key\":\"Treatment\",\"value\":\"Current\"}]}]}}";
        var rows = new ArrayList<CdsHistoryExtensionReader.Value>();
        CdsHistoryExtensionReader.current(() -> new java.io.StringReader(details), rows::add);
        assertThat(rows).extracting(CdsHistoryExtensionReader.Value::value).containsExactly("Current");
        for (String malformed : List.of("{\"version\":1,\"revisions\":[{\"extensions\":[{\"key\":\"Treatment\",\"value\":null}]}]}",
                "{\"version\":1,\"revisions\":[{\"extensions\":[{\"key\":\"Treatment\",\"key\":\"Other\"}]}]}"))
            assertThatThrownBy(() -> CdsHistoryExtensionReader.current(() -> new java.io.StringReader(malformed), ignored -> {}))
                    .hasMessage("CDS_HISTORY_EXTENSION_DETAILS_MALFORMED");
    }

    @Test
    void shouldReportSpoolIoAndLockFailures_asOperationalStops() throws Exception {
        var parent = parse(document(List.of(row("Treatment", "Original", null))));
        var emitted = new ArrayList<CdsRecordDto>();
        var limits = CdsInputLimitsDto.defaults();
        try (var locked = new CdsNativeChildSpool(parent, limits)) {
            assertThatThrownBy(() -> new CdsHistoryExtensionChildren(fields).forEach(parent, limits, emitted::add))
                    .hasMessage("CDS_DERIVED_SPOOL_BUSY");
        }
        Path values = parent.fieldsFile().getParent().resolve("native-extension-values.bin");
        Files.delete(values);
        Files.createDirectory(values);
        assertThatThrownBy(() -> new CdsHistoryExtensionChildren(fields).forEach(parent, limits, emitted::add))
                .hasMessage("CDS_SPOOL_IO_FAILED");
        assertThat(emitted).isEmpty();
    }

}
