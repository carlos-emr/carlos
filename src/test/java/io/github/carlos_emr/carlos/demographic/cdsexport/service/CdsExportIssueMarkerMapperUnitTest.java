/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsNativeExtensionContract;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedIndexDto;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("cds-export")
class CdsExportIssueMarkerMapperUnitTest {
    private static final String HASH = "a".repeat(64);

    @Test
    void shouldCarryEveryStoredField_withOnlyLocalIdentitiesExcluded() throws Exception {
        var marker = CdsExportNativeHistoryFixture.marker(41, null);
        var mapped = CdsExportIssueMarkerMapper.map(27, marker, ignored -> true, () -> {});
        var document = new com.fasterxml.jackson.databind.ObjectMapper().readTree(mapped.json());
        for (var row : List.of(marker.issue().value(), marker.definition().value())) {
            for (var field : row.fields().entrySet()) {
                var carried = document.path(row.table()).get(field.getKey());
                if (Set.of("id", "issue_id", "demographic_no").contains(field.getKey()) || field.getValue() == null)
                    assertThat(carried).isNull();
                else assertThat(carried.textValue()).isEqualTo(field.getValue());
            }
        }
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(mapped.rows(), mapped.fields(), () -> {});
        String xml = new String(CdsExportNativeHistoryFixture.markerDocument(41, null), StandardCharsets.UTF_8);
        assertThat(xml).contains(CdsNativeExtensionContract.NATIVE_ISSUES_CATEGORY)
                .doesNotContain("<ProblemList>", "<ClinicalNotes>");
    }

    @Test
    void shouldRefuseARestrictedMarker_whenTheProgramIsNotReadable() {
        assertThatThrownBy(() -> CdsExportIssueMarkerMapper.map(27,
                CdsExportNativeHistoryFixture.marker(41, "77"), ignored -> false, () -> {}))
                .hasMessage("CDS_EXPORT_NATIVE_ISSUE_PROGRAM_REQUIRES_REVIEW");
    }

    @Test
    void shouldRefuseAForeignPatient_beforeCarryingItsMarker() {
        assertThatThrownBy(() -> CdsExportIssueMarkerMapper.map(28,
                CdsExportNativeHistoryFixture.marker(41, null), ignored -> true, () -> {}))
                .hasMessage("CDS_EXPORT_ISSUE_MARKER_SCOPE_INVALID");
    }

    @Test
    void shouldRequireAllHistorySources_beforeIdentifyingNoteFreeMarkers() {
        var categories = java.util.EnumSet.copyOf(CdsExportNoteSourceReader.CATEGORIES);
        categories.add(CdsCategory.NEW_CATEGORY);
        var tables = Set.of("casemgmt_issue", "casemgmt_issue_notes", "issue");
        assertThat(CdsExportNewCategoryOccurrenceService.carriesIssues(categories, tables)).isTrue();
        categories.remove(CdsCategory.CLINICAL_NOTES);
        assertThat(CdsExportNewCategoryOccurrenceService.carriesIssues(categories, tables)).isFalse();
    }

    @Test
    void shouldRetainThePreviousPayloadAsSourceOnly_evenWithoutCurrentMarkers() throws Exception {
        var refs = List.of(new CdsExportRetainedIndexDto.Reference("demographic", "27", HASH),
                new CdsExportRetainedIndexDto.Reference("casemgmt_issue", "41", HASH));
        var name = new CdsExportRetainedFieldDto(HASH, "11111111-1111-4111-8111-111111111111",
                HASH, HASH, CdsCategory.NEW_CATEGORY,
                "/OmdCds[1]/PatientRecord[1]/NewCategory[1]/CategoryName[1]", 0,
                "cds", "TEXT", null, CdsNativeExtensionContract.NATIVE_ISSUES_CATEGORY,
                CdsNativeExtensionContract.NATIVE_ISSUES_CATEGORY, null);
        var changes = CdsExportNewCategoryOccurrenceService.markerSourceChanges(refs, List.of(name));
        assertThat(changes).hasSize(1);
        assertThat(changes.getFirst().relativePath()).isEqualTo("CategoryName[1]");
        assertThat(changes.getFirst().expectedPriorFields()).containsExactly(HASH);
        var text = new ArrayList<String>();
        var writer = org.mockito.Mockito.mock(CdsExportXmlComposer.ContentWriter.class);
        org.mockito.Mockito.doAnswer(call -> { text.add(call.getArgument(0)); return null; })
                .when(writer).text(org.mockito.ArgumentMatchers.anyString());
        changes.getFirst().content().write(writer);
        assertThat(text).containsExactly(CdsNativeExtensionContract.NATIVE_ISSUES_SOURCE_CATEGORY);
        assertThat(CdsExportNewCategoryOccurrenceService.markerSourceChanges(refs.subList(0, 1), List.of(name)))
                .isEmpty();
    }

    @Test
    void shouldRefuseAnUnprovableRetainedMarker_insteadOfReemittingARestorableCopy() {
        var refs = List.of(new CdsExportRetainedIndexDto.Reference("demographic", "27", HASH),
                new CdsExportRetainedIndexDto.Reference("casemgmt_issue", "41", HASH));
        assertThatThrownBy(() -> CdsExportNewCategoryOccurrenceService.markerSourceChanges(refs, List.of()))
                .hasMessage("CDS_EXPORT_NEW_CATEGORY_OCCURRENCE_INVALID");
    }
}
