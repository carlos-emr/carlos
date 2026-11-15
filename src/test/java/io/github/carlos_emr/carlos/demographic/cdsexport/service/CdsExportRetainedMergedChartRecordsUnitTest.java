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

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsMergedChartContract;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedIndexDto;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The merged-chart records a sending CARLOS wrote are recognised among a chart's imported
 * NewCategory units by their CategoryName, and resolved to the occurrence unit each belongs to.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportRetainedMergedChartRecordsUnitTest {
    private static final String RUN = "0b9f1c2e-3d4a-4b5c-8d6e-7f8091a2b3c4";

    @Test
    void shouldFindTheOccurrenceUnitsOfReceivedRecords_byTheirCategoryName() {
        var rows = new HashMap<String, CdsExportCapturedTextRowDto>();
        var parents = new HashMap<String, String>();
        // The record of a merged chart and the links record, each in its own field unit.
        field(rows, parents, "a", "b", "/OmdCds[1]/PatientRecord[1]/NewCategory[4]/CategoryName[1]",
                CdsMergedChartContract.CHART_RECORD);
        field(rows, parents, "c", "d", "/OmdCds[1]/PatientRecord[1]/NewCategory[6]/CategoryName[1]",
                CdsMergedChartContract.LINKS_RECORD);
        // Another CARLOS record, the same name elsewhere in a record, and a record's content.
        field(rows, parents, "e", "f", "/OmdCds[1]/PatientRecord[1]/NewCategory[1]/CategoryName[1]",
                "CARLOS.NativeAppointment.v1");
        field(rows, parents, "1", "2",
                "/OmdCds[1]/PatientRecord[1]/NewCategory[4]/ResidualInfo[1]/DataElement[1]/Name[1]",
                CdsMergedChartContract.CHART_RECORD);
        var index =
                new CdsExportRetainedIndexDto(
                        "0".repeat(64),
                        rows.size(),
                        1,
                        Map.of("cds_import_field", rows),
                        Set.of(address("b"), address("d"), address("f"), address("2")),
                        Map.of(),
                        parents);

        assertThat(CdsExportRetainedMergedChartRecords.units(index))
                .containsExactly(address("b"), address("d"));
    }

    @Test
    void shouldTellAReceivedRecord_fromItsOwnRetainedFields() {
        assertThat(
                        CdsExportRetainedMergedChartRecords.received(
                                List.of(
                                        dto("3", "/OmdCds[1]/PatientRecord[1]/NewCategory[2]/CategoryName[1]",
                                                CdsMergedChartContract.LINKS_RECORD))))
                .isTrue();
        assertThat(
                        CdsExportRetainedMergedChartRecords.received(
                                List.of(
                                        dto("4", "/OmdCds[1]/PatientRecord[1]/NewCategory[2]/CategoryName[1]",
                                                "CARLOS.NativeDemographic.v1"),
                                        dto("5", "/OmdCds[1]/PatientRecord[1]/NewCategory[2]/CategoryDescription[1]",
                                                CdsMergedChartContract.CHART_RECORD))))
                .isFalse();
    }

    private static String address(String unit) {
        return RUN + ":" + unit.repeat(64);
    }

    /** One retained CategoryName-like field row in its field unit, linked to its occurrence unit. */
    private static void field(
            Map<String, CdsExportCapturedTextRowDto> rows,
            Map<String, String> parents,
            String fieldUnit,
            String occurrenceUnit,
            String path,
            String value) {
        String id = fieldUnit.repeat(63) + "0";
        var fields = new HashMap<String, String>();
        fields.put("id", id);
        fields.put("category", CdsCategory.NEW_CATEGORY.wireName());
        fields.put("source_path", path);
        fields.put("original_value", value);
        fields.put("run_id", RUN);
        fields.put("unit_key", fieldUnit.repeat(64));
        rows.put(id, new CdsExportCapturedTextRowDto("cds_import_field", id, fields));
        parents.put(id, address(occurrenceUnit));
    }

    private static CdsExportRetainedFieldDto dto(String key, String path, String value) {
        return new CdsExportRetainedFieldDto(
                key.repeat(64),
                RUN,
                "a".repeat(64),
                "b".repeat(64),
                CdsCategory.NEW_CATEGORY,
                path,
                0,
                "merged-fixture",
                "{cds_dt}text",
                null,
                value,
                null,
                null);
    }
}
