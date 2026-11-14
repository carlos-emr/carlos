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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import io.github.carlos_emr.carlos.commn.dao.DemographicCustDao;
import io.github.carlos_emr.carlos.commn.dto.CdsExportRawCellDto;
import io.github.carlos_emr.carlos.commn.dto.CdsExportRawRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("cds-export")
class CdsExportDemographicCustScopeUnitTest {
    @Test
    void shouldNeverFetchOrCaptureAlertBytes_whenOnlyDemographicsWasSelected() throws Exception {
        var source = mock(DemographicCustDao.class);
        var original = new CdsExportDemographicCustSourceReader(source);
        var reader = original.forRequest(command(false));
        assertThat(reader.categories()).containsExactly(CdsCategory.DEMOGRAPHICS);
        assertThat(reader.columns()).doesNotContain("cust3");
        when(source.findCdsExportDemographicRowsAfter(27, null, 32))
                .thenReturn(List.of(row(reader.columns())));
        var values = new ByteArrayOutputStream();
        var index = new ByteArrayOutputStream();
        new CdsExportNativeCaptureComposer()
                .compose(
                        values,
                        index,
                        Set.of("demographiccust"),
                        10,
                        10000,
                        () -> {},
                        writer -> {
                            writer.beginTable(reader.table(), reader.columns(), 1);
                            reader.capture(27, writer);
                            writer.endTable();
                        });
        assertThat(index.toString(StandardCharsets.UTF_8)).doesNotContain("cust3");
        assertThat(values.toString(StandardCharsets.UTF_8))
                .contains("DEMOGRAPHIC_NOTE")
                .doesNotContain("PRIVATE_ALERT");
        verify(source, never()).findCdsExportRowsAfter(anyInt(), isNull(), anyInt());
        assertThat(original.columns())
                .contains(
                        "cust3"); // Request projection never mutates the singleton/default reader.
    }

    @Test
    void shouldRequireTheAlertReadCategory_andCaptureItsExactValueWhenItWasSelected()
            throws Exception {
        var source = mock(DemographicCustDao.class);
        var reader = new CdsExportDemographicCustSourceReader(source).forRequest(command(true));
        assertThat(reader.categories())
                .containsExactlyInAnyOrder(
                        CdsCategory.DEMOGRAPHICS, CdsCategory.ALERTS_AND_SPECIAL_NEEDS);
        assertThat(reader.columns()).contains("cust3");
        when(source.findCdsExportRowsAfter(27, null, 32))
                .thenReturn(List.of(row(reader.columns())));
        var values = new ByteArrayOutputStream();
        var index = new ByteArrayOutputStream();
        new CdsExportNativeCaptureComposer()
                .compose(
                        values,
                        index,
                        Set.of("demographiccust"),
                        10,
                        10000,
                        () -> {},
                        writer -> {
                            writer.beginTable(reader.table(), reader.columns(), 1);
                            reader.capture(27, writer);
                            writer.endTable();
                        });
        assertThat(values.toString(StandardCharsets.UTF_8))
                .contains("DEMOGRAPHIC_NOTE", "PRIVATE_ALERT");
        verify(source, never()).findCdsExportDemographicRowsAfter(anyInt(), isNull(), anyInt());
    }

    private static CdsExportRawRowDto row(Set<String> columns) throws Exception {
        var cells = new TreeMap<String, CdsExportRawCellDto>();
        for (String column : columns)
            cells.put(
                    column,
                    new CdsExportRawCellDto(true, 0, CdsExportRawCellDto.EMPTY_SHA256, null));
        for (var field :
                Map.of("content", "DEMOGRAPHIC_NOTE", "cust3", "PRIVATE_ALERT").entrySet()) {
            if (!columns.contains(field.getKey())) continue;
            byte[] bytes = field.getValue().getBytes(StandardCharsets.UTF_8);
            cells.put(
                    field.getKey(),
                    new CdsExportRawCellDto(
                            false,
                            bytes.length,
                            HexFormat.of()
                                    .formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)),
                            bytes));
        }
        return new CdsExportRawRowDto(27, cells);
    }

    private static CdsExportCommand command(boolean alerts) {
        return new CdsExportCommand(
                "scope",
                "999998",
                null,
                CdsLogicalProfile.ONTARIO_MD_5_2,
                CdsExportCommand.Selection.SELECTED_PATIENTS,
                List.of(),
                List.of(27),
                alerts
                        ? Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.ALERTS_AND_SPECIAL_NEEDS)
                        : Set.of(CdsCategory.DEMOGRAPHICS),
                "receiver",
                "audit");
    }
}
