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
import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.dto.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.CancellationException;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportLaboratorySourceReaderUnitTest {
    private static final String ID = "a".repeat(64);
    private final CdsLaboratoryResultDao dao = mock(CdsLaboratoryResultDao.class);
    private final CdsExportLaboratorySourceReader reader = new CdsExportLaboratorySourceReader(dao);

    private Map<String, CdsExportRawCellDto> cells() {
        var result = new TreeMap<String, CdsExportRawCellDto>();
        for (var column : reader.columns())
            result.put(
                    column,
                    new CdsExportRawCellDto(true, 0, CdsExportRawCellDto.EMPTY_SHA256, null));
        result.put("id", cell(ID));
        return result;
    }

    private static CdsExportRawCellDto cell(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return new CdsExportRawCellDto(
                false,
                bytes.length,
                HexFormat.of().formatHex(CdsImportKeys.digest().digest(bytes)),
                bytes.length > 16_384 ? null : bytes);
    }

    private byte[] capture(long count, Runnable checkpoint) throws Exception {
        var values = new ByteArrayOutputStream();
        var index = new ByteArrayOutputStream();
        new CdsExportNativeCaptureComposer()
                .compose(
                        values,
                        index,
                        Set.of(reader.table()),
                        10_000,
                        1L << 20,
                        checkpoint,
                        output -> {
                            output.beginTable(reader.table(), reader.columns(), count);
                            reader.capture(27, output, checkpoint);
                            output.endTable();
                        });
        new CdsExportNativeCaptureReader()
                .verify(
                        new ByteArrayInputStream(values.toByteArray()),
                        new ByteArrayInputStream(index.toByteArray()),
                        Map.of(
                                reader.table(),
                                new CdsExportNativeCaptureReader.Table(reader.columns(), count)),
                        10_000,
                        1L << 20,
                        checkpoint);
        return values.toByteArray();
    }

    @Test
    void shouldPreserveNullEmptyAndLargeUnicodeResults_withTheExactStringIdentity()
            throws Exception {
        String large = "résultat 漢字\n".repeat(3000);
        byte[] body = large.getBytes(StandardCharsets.UTF_8);
        var cells = cells();
        cells.put("informationFromLab", cell(large));
        cells.put("notesFromLab", cell(""));
        when(dao.findCdsExportRowsAfter(27, null, 32))
                .thenReturn(List.of(new CdsExportRawKeyRowDto(ID, cells)));
        when(dao.readCdsExportValueChunk(
                        eq(27), eq(ID), eq("informationFromLab"), anyLong(), anyInt()))
                .thenAnswer(
                        call -> {
                            int offset = Math.toIntExact(call.getArgument(3, Long.class)),
                                    length = call.getArgument(4);
                            return Arrays.copyOfRange(body, offset, offset + length);
                        });
        assertThat(new String(capture(1, () -> {}), StandardCharsets.UTF_8)).contains(large);
        verify(dao, atLeastOnce())
                .readCdsExportValueChunk(
                        eq(27), eq(ID), eq("informationFromLab"), anyLong(), anyInt());
    }

    private static io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand command(
            Set<CdsCategory> categories) {
        return new io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand(
                "test",
                "999998",
                null,
                io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile.ONTARIO_MD_5_2,
                io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand.Selection
                        .SELECTED_PATIENTS,
                List.of(),
                List.of(27),
                categories,
                "recipient",
                "audit");
    }

    @Test
    void shouldHoldAChartWithLegacyFormatReports_whenLaboratoryResultsIsExportedWithoutReports()
            throws Exception {
        when(dao.hasCdsExportLegacyReports(27)).thenReturn(true);
        when(dao.findCdsExportRowsAfter(27, null, 32)).thenReturn(List.of());
        // Its MDS, CML or PathNet reports travel only as data summaries with Reports.
        var alone = reader.forRequest(command(Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.LABORATORY_RESULTS)));
        assertThat(alone).isNotSameAs(reader);
        assertThat(alone.table()).isEqualTo(reader.table());
        assertThat(alone.categories()).isEqualTo(reader.categories());
        var values = new ByteArrayOutputStream();
        assertThatThrownBy(
                        () ->
                                new CdsExportNativeCaptureComposer()
                                        .compose(
                                                values,
                                                new ByteArrayOutputStream(),
                                                Set.of(reader.table()),
                                                10_000,
                                                1L << 20,
                                                () -> {},
                                                output -> {
                                                    output.beginTable(
                                                            reader.table(), reader.columns(), 0);
                                                    alone.capture(27, output, () -> {});
                                                    output.endTable();
                                                }))
                .isInstanceOf(
                        io.github.carlos_emr.carlos.demographic.cdsexport.support
                                .CdsExportMappingReviewException.class)
                .hasMessage("CDS_EXPORT_LEGACY_LAB_REPORTS_NEED_REPORTS");
        // With Reports selected the reports are exported, so the structured capture proceeds.
        assertThat(reader.forRequest(command(Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.LABORATORY_RESULTS, CdsCategory.REPORTS))))
                .isSameAs(reader);
        capture(0, () -> {});
        verify(dao, times(1)).hasCdsExportLegacyReports(27);
    }

    @Test
    void shouldRequireSensitiveLaboratoryAuthority_andAllMirrorDependencies() {
        assertThat(reader.categories()).containsExactly(CdsCategory.LABORATORY_RESULTS);
        assertThat(reader.requiresSensitiveLaboratoryRead()).isTrue();
        assertThat(reader.requiredTables())
                .containsExactlyInAnyOrder(
                        "cds_laboratory_result",
                        "hl7TextMessage",
                        "hl7TextInfo",
                        "patientLabRouting");
        for (var sibling :
                List.of(
                        new CdsExportLaboratoryMessageSourceReader(mock(Hl7TextMessageDao.class)),
                        new CdsExportLaboratoryInfoSourceReader(mock(Hl7TextInfoDao.class)),
                        new CdsExportLaboratoryRoutingSourceReader(
                                mock(PatientLabRoutingDao.class)))) {
            assertThat(sibling.requiresSensitiveLaboratoryRead()).isTrue();
            assertThat(sibling.requiredTables()).isEqualTo(reader.requiredTables());
        }
    }

    @Test
    void shouldMatchEveryPhysicalColumn_andItsPinnedSchema() throws Exception {
        for (var plan :
                List.of(
                        CdsExportPatientTableReadSupport.Plan.LABORATORY_RESULTS,
                        CdsExportPatientTableReadSupport.Plan.LABORATORY_MESSAGES,
                        CdsExportPatientTableReadSupport.Plan.LABORATORY_INFORMATION,
                        CdsExportPatientTableReadSupport.Plan.LABORATORY_ROUTING)) {
            assertThat(
                            new CdsExportSourceSchemaValidator()
                                    .expectedSha256(plan.table(), Set.copyOf(plan.columns())))
                    .matches("[0-9a-f]{64}");
        }
    }

    @Test
    void shouldRefuseUnknownAndMissingColumns_insteadOfReturningAPartialResult() {
        var cells = cells();
        cells.put("unreviewed", cell("value"));
        when(dao.findCdsExportRowsAfter(27, null, 32))
                .thenReturn(List.of(new CdsExportRawKeyRowDto(ID, cells)));
        assertThatThrownBy(() -> capture(1, () -> {}))
                .hasMessage("CDS_EXPORT_LABORATORY_PAGE_INVALID");
        cells = cells();
        cells.remove("physiciansNotes");
        when(dao.findCdsExportRowsAfter(27, null, 32))
                .thenReturn(List.of(new CdsExportRawKeyRowDto(ID, cells)));
        assertThatThrownBy(() -> capture(1, () -> {}))
                .hasMessage("CDS_EXPORT_LABORATORY_PAGE_INVALID");
    }

    @Test
    void shouldRefuseNoncanonicalDigestKeys_beforeEmittingTheRow() {
        for (var key : List.of("27", UUID.randomUUID().toString())) {
            when(dao.findCdsExportRowsAfter(27, null, 32))
                    .thenReturn(List.of(new CdsExportRawKeyRowDto(key, cells())));
            assertThatThrownBy(() -> capture(1, () -> {}))
                    .hasMessage("CDS_EXPORT_LABORATORY_PAGE_INVALID");
        }
    }

    @Test
    void shouldRefuseRepeatedOrReorderedKeys_insteadOfHidingDuplicates() {
        var row = new CdsExportRawKeyRowDto(ID, cells());
        when(dao.findCdsExportRowsAfter(27, null, 32)).thenReturn(List.of(row, row));
        assertThatThrownBy(() -> capture(2, () -> {}))
                .hasMessage("CDS_EXPORT_LABORATORY_PAGE_INVALID");
    }

    @Test
    void shouldRefuseAnOversizedDaoPage_beforeAcceptingAnyRow() {
        when(dao.findCdsExportRowsAfter(27, null, 32))
                .thenReturn(Collections.nCopies(33, new CdsExportRawKeyRowDto(ID, cells())));
        assertThatThrownBy(() -> capture(33, () -> {}))
                .hasMessage("CDS_EXPORT_LABORATORY_PAGE_INVALID");
    }

    @Test
    void shouldRequireTheIndependentRowCount_atAuthenticatedEndOfCapture() {
        when(dao.findCdsExportRowsAfter(27, null, 32)).thenReturn(List.of());
        assertThatThrownBy(() -> capture(1, () -> {})).isInstanceOf(IOException.class);
    }

    @Test
    void shouldRefuseCorruptLargeBodyChunks_insteadOfExportingChangedBytes() {
        var cells = cells();
        cells.put("informationFromLab", cell("x".repeat(20000)));
        when(dao.findCdsExportRowsAfter(27, null, 32))
                .thenReturn(List.of(new CdsExportRawKeyRowDto(ID, cells)));
        when(dao.readCdsExportValueChunk(
                        eq(27), eq(ID), eq("informationFromLab"), anyLong(), anyInt()))
                .thenAnswer(call -> new byte[call.getArgument(4, Integer.class)]);
        assertThatThrownBy(() -> capture(1, () -> {}))
                .hasMessage("CDS_EXPORT_NATIVE_VALUE_CHANGED");
    }

    @Test
    void shouldPropagateCancellation_beforeAnyAdditionalLaboratoryPayloadRead() {
        assertThatThrownBy(
                        () ->
                                capture(
                                        1,
                                        () -> {
                                            throw new CancellationException("CANCELLED");
                                        }))
                .hasMessage("CANCELLED");
        verifyNoInteractions(dao);
    }
}
