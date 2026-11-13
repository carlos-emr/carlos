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
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportMeasurementSourceReaderUnitTest {
    private final MeasurementDao source = mock(MeasurementDao.class);
    private final CdsExportMeasurementSourceReader reader =
            new CdsExportMeasurementSourceReader(source);

    private Map<String, CdsExportRawCellDto> cells() {
        var fields = new TreeMap<String, CdsExportRawCellDto>();
        for (String field : reader.columns())
            fields.put(
                    field,
                    new CdsExportRawCellDto(true, 0, CdsExportRawCellDto.EMPTY_SHA256, null));
        fields.put("id", cell("91"));
        return fields;
    }

    private static CdsExportRawCellDto cell(String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        return new CdsExportRawCellDto(
                false,
                bytes.length,
                HexFormat.of().formatHex(CdsImportKeys.digest().digest(bytes)),
                bytes.length <= 16384 ? bytes : null);
    }

    private byte[] capture(long count) throws Exception {
        var data = new ByteArrayOutputStream();
        var index = new ByteArrayOutputStream();
        new CdsExportNativeCaptureComposer()
                .compose(
                        data,
                        index,
                        Set.of(reader.table()),
                        10000,
                        1L << 20,
                        () -> {},
                        out -> {
                            out.beginTable(reader.table(), reader.columns(), count);
                            reader.capture(27, out);
                            out.endTable();
                        });
        new CdsExportNativeCaptureReader()
                .verify(
                        new ByteArrayInputStream(data.toByteArray()),
                        new ByteArrayInputStream(index.toByteArray()),
                        Map.of(
                                reader.table(),
                                new CdsExportNativeCaptureReader.Table(reader.columns(), count)),
                        10000,
                        1L << 20,
                        () -> {});
        return data.toByteArray();
    }

    @Test
    void shouldCaptureDeletedCare_withoutRequiringASupportingNewCategorySelection() {
        // Before: deleted observations were exported as NewCategory history, so the reader held
        // the patient when NewCategory was not selected. They are now accounted and never
        // exported (CdsExportNativeCareMapper.retired), so no carrier is needed.
        var dao = mock(MeasurementsDeletedDao.class);
        var singleton = new CdsExportDeletedMeasurementSourceReader(dao);
        when(dao.countCdsExportRows(27)).thenReturn(1L);
        var command =
                new io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand(
                        "deleted-proof",
                        "999998",
                        null,
                        io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile
                                .ONTARIO_MD_5_2,
                        io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand
                                .Selection.SELECTED_PATIENTS,
                        List.of(),
                        List.of(27),
                        Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.CARE_ELEMENTS),
                        "receiver",
                        "audit");
        assertThat(singleton.forRequest(command).count(27)).isEqualTo(1L);
        assertThat(singleton.count(27)).isEqualTo(1L);
    }

    @Test
    void shouldKeepLargeUnicodeCareValues_andNullAndEmptyFieldsDistinct() throws Exception {
        String body = "Complete native care 😀 & literal text\r\n".repeat(2000);
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        var fields = cells();
        fields.put("dataField", cell(body));
        fields.put("comments", cell(""));
        when(source.findCdsExportRowsAfter(27, null, 32))
                .thenReturn(List.of(new CdsExportRawRowDto(91, fields)));
        when(source.readCdsExportValueChunk(eq(27), eq(91L), eq("dataField"), anyLong(), anyInt()))
                .thenAnswer(
                        call -> {
                            int offset = Math.toIntExact(call.getArgument(3, Long.class));
                            return Arrays.copyOfRange(
                                    bytes, offset, offset + call.getArgument(4, Integer.class));
                        });
        assertThat(new String(capture(1), StandardCharsets.UTF_8)).contains(body);
        verify(source, atLeastOnce())
                .readCdsExportValueChunk(eq(27), eq(91L), eq("dataField"), anyLong(), anyInt());
    }

    @Test
    void shouldRefuseChangedLargeChunks_beforeReturningACompleteCapture() {
        var fields = cells();
        fields.put("dataField", cell("x".repeat(20000)));
        when(source.findCdsExportRowsAfter(27, null, 32))
                .thenReturn(List.of(new CdsExportRawRowDto(91, fields)));
        when(source.readCdsExportValueChunk(eq(27), eq(91L), eq("dataField"), anyLong(), anyInt()))
                .thenAnswer(call -> new byte[call.getArgument(4, Integer.class)]);
        assertThatThrownBy(() -> capture(1)).hasMessage("CDS_EXPORT_NATIVE_VALUE_CHANGED");
    }

    @Test
    void shouldRequireEveryPhysicalMeasurementColumn_insteadOfDroppingUnknownNativeData() {
        var fields = cells();
        fields.remove("comments");
        when(source.findCdsExportRowsAfter(27, null, 32))
                .thenReturn(List.of(new CdsExportRawRowDto(91, fields)));
        assertThatThrownBy(() -> capture(1)).hasMessage("CDS_EXPORT_NATIVE_PAGE_INVALID");
    }

    @Test
    void shouldRefuseDuplicatePhysicalIds_insteadOfDeduplicatingCareObservations() {
        var row = new CdsExportRawRowDto(91, cells());
        when(source.findCdsExportRowsAfter(27, null, 32)).thenReturn(List.of(row, row));
        assertThatThrownBy(() -> capture(2)).hasMessage("CDS_EXPORT_NATIVE_PAGE_INVALID");
    }

    @Test
    void shouldRequireIndependentRowCounts_evenForAnEmptyReturnedPage() {
        when(source.findCdsExportRowsAfter(27, null, 32)).thenReturn(List.of());
        assertThatThrownBy(() -> capture(1)).isInstanceOf(IOException.class);
    }

    @Test
    void shouldQualifyAllFourExactPhysicalContracts_andDeclareTheSameCareDependencyClosure()
            throws Exception {
        for (CdsExportNativeSourceReader value :
                List.of(
                        reader,
                        new CdsExportMeasurementExtensionSourceReader(
                                mock(MeasurementsExtDao.class)),
                        new CdsExportDeletedMeasurementSourceReader(
                                mock(MeasurementsDeletedDao.class)),
                        new CdsExportMeasurementTypeSourceReader(mock(MeasurementTypeDao.class)))) {
            assertThat(value.categories()).containsExactly(CdsCategory.CARE_ELEMENTS);
            assertThat(value.requiredTables())
                    .containsExactlyInAnyOrder(
                            "measurements",
                            "measurementsExt",
                            "measurementsDeleted",
                            "measurementType");
            assertThat(
                            new CdsExportSourceSchemaValidator()
                                    .expectedSha256(value.table(), value.columns()))
                    .matches("[0-9a-f]{64}");
        }
    }

    @Test
    void shouldProveAnEmptyDeletedSource_withoutTreatingItAsAClinicalMapping() {
        var dao = mock(MeasurementsDeletedDao.class);
        when(dao.countCdsExportRows(27)).thenReturn(0L);
        assertThat(new CdsExportDeletedMeasurementSourceReader(dao).count(27)).isZero();
    }

    @Test
    void shouldCheckMeasurementAssociationsOncePerSnapshot_insteadOfOnEveryDaoCall() {
        // Before: every count, page and chunk call of all four measurement DAOs rescanned the
        // unindexed measurementsDeleted table, many times per patient inside one snapshot.
        var em = mock(jakarta.persistence.EntityManager.class);
        var query = mock(jakarta.persistence.Query.class, RETURNS_SELF);
        when(query.getResultList()).thenReturn(List.of());
        when(em.createNativeQuery(anyString(), eq(Object[].class))).thenReturn(query);
        org.springframework.transaction.support.TransactionSynchronizationManager
                .initSynchronization();
        org.springframework.transaction.support.TransactionSynchronizationManager
                .setActualTransactionActive(true);
        org.springframework.transaction.support.TransactionSynchronizationManager
                .setCurrentTransactionReadOnly(true);
        org.springframework.transaction.support.TransactionSynchronizationManager
                .setCurrentTransactionIsolationLevel(java.sql.Connection.TRANSACTION_REPEATABLE_READ);
        try {
            for (int i = 0; i < 5; i++)
                CdsExportPatientTableReadSupport.requireLocalMeasurementAssociations(em, 27);
            verify(em, times(2)).createNativeQuery(anyString(), eq(Object[].class));
            CdsExportPatientTableReadSupport.requireLocalMeasurementAssociations(em, 28);
            verify(em, times(4)).createNativeQuery(anyString(), eq(Object[].class));
        } finally {
            for (var synchronization :
                    org.springframework.transaction.support.TransactionSynchronizationManager
                            .getSynchronizations())
                synchronization.afterCompletion(
                        org.springframework.transaction.support.TransactionSynchronization
                                .STATUS_COMMITTED);
            org.springframework.transaction.support.TransactionSynchronizationManager.clear();
        }
        // The snapshot ended: nothing remembered, so the next transaction checks again.
        assertThat(
                        org.springframework.transaction.support.TransactionSynchronizationManager
                                .getResourceMap())
                .isEmpty();
    }
}
