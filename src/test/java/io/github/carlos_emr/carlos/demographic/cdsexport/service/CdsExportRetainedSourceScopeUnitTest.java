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
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.commn.dao.CdsImportFieldDao;
import io.github.carlos_emr.carlos.commn.dao.CdsImportPatientDao;
import io.github.carlos_emr.carlos.commn.dto.CdsExportRawCellDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportRawKeyRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("cds-export")
class CdsExportRetainedSourceScopeUnitTest {
    @Test
    void shouldBindTheSelectedReadDomainsAndQueryScope_withoutChangingTheSingleton()
            throws Exception {
        var dao = mock(CdsImportFieldDao.class);
        var original = new CdsExportRetainedFieldSourceReader(dao);
        var selected =
                Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS);
        var reader = original.forRequest(command(selected));
        var wire = Set.of("Demographics", "AllergiesAndAdverseReactions");
        when(dao.countCdsExportRetainedRows(27, wire)).thenReturn(0L);
        when(dao.findCdsExportRetainedRowsAfter(27, wire, null, 32)).thenReturn(List.of());
        assertThat(reader.count(27)).isZero();
        assertThat(reader.categories()).isEqualTo(selected);
        assertThat(reader.requiresSensitiveLaboratoryRead()).isFalse();
        capture(reader, 0);
        verify(dao).findCdsExportRetainedRowsAfter(27, wire, null, 32);
        assertThat(original.categories()).containsExactly(CdsCategory.DEMOGRAPHICS);
    }

    @Test
    void shouldRequireSensitiveLabAuthority_forUnclassifiedRetainedLaboratoryEvidence() {
        var reader =
                new CdsExportRetainedFieldSourceReader(mock(CdsImportFieldDao.class))
                        .forRequest(
                                command(
                                        Set.of(
                                                CdsCategory.DEMOGRAPHICS,
                                                CdsCategory.LABORATORY_RESULTS)));
        assertThat(reader.requiresSensitiveLaboratoryRead()).isTrue();
        var patients =
                new CdsExportRetainedPatientSourceReader(mock(CdsImportPatientDao.class))
                        .forRequest(
                                command(
                                        Set.of(
                                                CdsCategory.DEMOGRAPHICS,
                                                CdsCategory.LABORATORY_RESULTS)));
        assertThat(patients.categories()).containsExactly(CdsCategory.DEMOGRAPHICS);
        assertThat(patients.requiresSensitiveLaboratoryRead()).isFalse();
    }

    @Test
    void shouldVerifyFileEvidenceUsesTheSelectedClinicalDomainsAndFreshSensitiveLaboratoryAuthority_forRetainedSourceScopeContract()
            throws Exception {
        var dao = mock(io.github.carlos_emr.carlos.commn.dao.CdsImportFileDao.class);
        var singleton = new CdsExportRetainedFileSourceReader(dao);
        var selected = Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.LABORATORY_RESULTS);
        var reader = singleton.forRequest(command(selected));
        var wire = Set.of("Demographics", "LaboratoryResults");
        when(dao.countCdsExportRetainedRows(27, wire)).thenReturn(0L);
        when(dao.findCdsExportRetainedRowsAfter(27, wire, null, 32)).thenReturn(List.of());
        assertThat(reader.count(27)).isZero();
        assertThat(reader.categories()).isEqualTo(selected);
        assertThat(reader.requiresSensitiveLaboratoryRead()).isTrue();
        capture(reader, 0);
        verify(dao).findCdsExportRetainedRowsAfter(27, wire, null, 32);
        assertThat(singleton.categories()).containsExactly(CdsCategory.DEMOGRAPHICS);
    }

    @Test
    void shouldVerifyFileReferenceEvidenceNeverFallsBack_toTheWholeImportRun() throws Exception {
        var dao = mock(io.github.carlos_emr.carlos.commn.dao.CdsImportFileReferenceDao.class);
        var selected =
                Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.ALLERGIES_AND_ADVERSE_REACTIONS);
        var reader =
                new CdsExportRetainedFileReferenceSourceReader(dao).forRequest(command(selected));
        var wire = Set.of("Demographics", "AllergiesAndAdverseReactions");
        when(dao.findCdsExportRetainedRowsAfter(27, wire, null, 32)).thenReturn(List.of());
        capture(reader, 0);
        verify(dao).findCdsExportRetainedRowsAfter(27, wire, null, 32);
        assertThat(reader.table()).isEqualTo("cds_import_file_reference");
        assertThat(reader.categories()).isEqualTo(selected);
        assertThat(reader.requiresSensitiveLaboratoryRead()).isFalse();
    }

    @Test
    void shouldKeepAllLeadingZeroesInDigestKeys_andRefuseUnorderedOrNumericSubstitutions()
            throws Exception {
        var dao = mock(CdsImportFieldDao.class);
        var reader = new CdsExportRetainedFieldSourceReader(dao);
        var cells = new TreeMap<String, CdsExportRawCellDto>();
        reader.columns()
                .forEach(
                        column ->
                                cells.put(
                                        column,
                                        new CdsExportRawCellDto(
                                                true, 0, CdsExportRawCellDto.EMPTY_SHA256, null)));
        var row = new CdsExportRawKeyRowDto("0".repeat(63) + "1", cells);
        when(dao.findCdsExportRetainedRowsAfter(27, Set.of("Demographics"), null, 32))
                .thenReturn(List.of(row));
        String index = capture(reader, 1);
        assertThat(index).contains(row.id());
        when(dao.findCdsExportRetainedRowsAfter(27, Set.of("Demographics"), null, 32))
                .thenReturn(List.of(row, row));
        assertThatThrownBy(() -> capture(reader, 2)).hasMessage("CDS_EXPORT_RETAINED_PAGE_INVALID");
        when(dao.findCdsExportRetainedRowsAfter(27, Set.of("Demographics"), null, 32))
                .thenReturn(List.of(new CdsExportRawKeyRowDto("1", cells)));
        assertThatThrownBy(() -> capture(reader, 1)).hasMessage("CDS_EXPORT_RETAINED_PAGE_INVALID");
    }

    @Test
    void shouldPreserveTheExactParentRunUuid_andRequireOnlyItsDemographicMetadataDomain()
            throws Exception {
        var dao = mock(io.github.carlos_emr.carlos.commn.dao.CdsImportRunDao.class);
        var reader =
                new CdsExportRetainedRunSourceReader(dao)
                        .forRequest(
                                command(
                                        Set.of(
                                                CdsCategory.DEMOGRAPHICS,
                                                CdsCategory.LABORATORY_RESULTS)));
        String run = "00000000-0000-4000-8000-000000000001";
        var cells = new TreeMap<String, CdsExportRawCellDto>();
        reader.columns()
                .forEach(
                        column ->
                                cells.put(
                                        column,
                                        new CdsExportRawCellDto(
                                                true, 0, CdsExportRawCellDto.EMPTY_SHA256, null)));
        when(dao.findCdsExportRetainedRowsAfter(27, Set.of("Demographics"), null, 32))
                .thenReturn(List.of(new CdsExportRawKeyRowDto(run, cells)));
        assertThat(capture(reader, 1)).contains(run);
        assertThat(reader.categories()).containsExactly(CdsCategory.DEMOGRAPHICS);
        assertThat(reader.requiresSensitiveLaboratoryRead()).isFalse();
    }

    private static String capture(CdsExportNativeSourceReader reader, long count) throws Exception {
        var values = new ByteArrayOutputStream();
        var index = new ByteArrayOutputStream();
        new CdsExportNativeCaptureComposer()
                .compose(
                        values,
                        index,
                        Set.of(reader.table()),
                        1000,
                        10000,
                        () -> {},
                        writer -> {
                            writer.beginTable(reader.table(), reader.columns(), count);
                            reader.capture(27, writer);
                            writer.endTable();
                        });
        return index.toString(java.nio.charset.StandardCharsets.UTF_8);
    }

    private static CdsExportCommand command(Set<CdsCategory> categories) {
        return new CdsExportCommand(
                "scope",
                "999998",
                null,
                CdsLogicalProfile.ONTARIO_MD_5_2,
                CdsExportCommand.Selection.SELECTED_PATIENTS,
                List.of(),
                List.of(27),
                categories,
                "receiver",
                "audit");
    }
}
