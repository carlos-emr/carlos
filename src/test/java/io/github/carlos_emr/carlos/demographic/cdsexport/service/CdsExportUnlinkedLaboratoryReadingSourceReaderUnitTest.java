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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.MeasurementDao;
import io.github.carlos_emr.carlos.commn.dto.CdsExportUnlinkedLaboratoryReadingDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Laboratory readings whose report is no longer linked to the chart are left out and listed, by
 * test name, date and count, never held (owner decision 2026-11-14).
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportUnlinkedLaboratoryReadingSourceReaderUnitTest {
    private static final List<CdsExportUnlinkedLaboratoryReadingDto> READINGS =
            List.of(
                    new CdsExportUnlinkedLaboratoryReadingDto("", "", 1),
                    new CdsExportUnlinkedLaboratoryReadingDto("HbA1c", "2020-02-29", 2),
                    new CdsExportUnlinkedLaboratoryReadingDto("Glucose, fasting", "2020-03-01", 1));

    @Test
    void shouldCaptureOneRowPerTestAndDate_withItsCountAndNoResultValue() throws Exception {
        var measurements = mock(MeasurementDao.class);
        when(measurements.findCdsExportUnlinkedLaboratoryReadings(27)).thenReturn(READINGS);
        var reader = new CdsExportUnlinkedLaboratoryReadingSourceReader(measurements);
        assertThat(reader.table()).isEqualTo(CdsExportUnlinkedLaboratoryReadingSourceReader.TABLE);
        assertThat(reader.categories()).containsExactly(CdsCategory.LABORATORY_RESULTS);
        assertThat(reader.requiresSensitiveLaboratoryRead()).isTrue();
        assertThat(reader.columns()).containsExactlyInAnyOrder("observed_date", "reading_count", "test_name");
        assertThat(reader.count(27)).isEqualTo(3);
        // The schema contract is the one the capture checks the reader against.
        assertThat(
                        new CdsExportSourceSchemaValidator()
                                .verify(reader.schema(), reader.columns()))
                .matches("[0-9a-f]{64}");

        var values = new ByteArrayOutputStream();
        var index = new ByteArrayOutputStream();
        new CdsExportNativeCaptureComposer()
                .compose(
                        values,
                        index,
                        Set.of(reader.table()),
                        1_000_000,
                        1L << 30,
                        () -> {},
                        writer -> {
                            writer.beginTable(reader.table(), reader.columns(), 3);
                            reader.capture(27, writer, () -> {});
                            writer.endTable();
                        });
        var rows = new java.util.TreeMap<String, Map<String, String>>();
        var current = new ByteArrayOutputStream[1];
        new CdsExportNativeCaptureReader()
                .scan(
                        new ByteArrayInputStream(values.toByteArray()),
                        new ByteArrayInputStream(index.toByteArray()),
                        Map.of(
                                reader.table(),
                                new CdsExportNativeCaptureReader.Table(reader.columns(), 3)),
                        1_000_000,
                        1L << 30,
                        () -> {},
                        new CdsExportNativeCaptureReader.Fields() {
                            @Override
                            public java.io.OutputStream output(
                                    io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                                    .CdsExportCapturedFieldDto
                                            field) {
                                current[0] = new ByteArrayOutputStream();
                                return current[0];
                            }

                            @Override
                            public void verified(
                                    io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                                    .CdsExportCapturedFieldDto
                                            field) {
                                rows.computeIfAbsent(field.recordKey(), key -> new java.util.TreeMap<>())
                                        .put(
                                                field.column(),
                                                current[0].toString(
                                                        java.nio.charset.StandardCharsets.UTF_8));
                            }
                        });
        assertThat(new ArrayList<>(rows.values()))
                .containsExactly(
                        Map.of("observed_date", "", "reading_count", "1", "test_name", ""),
                        Map.of("observed_date", "2020-02-29", "reading_count", "2", "test_name", "HbA1c"),
                        Map.of(
                                "observed_date",
                                "2020-03-01",
                                "reading_count",
                                "1",
                                "test_name",
                                "Glucose, fasting"));
    }

    @Test
    void shouldNameEachTestAndDateOnItsOwnReadmeLine_withPlainMarksForWhatIsNotStored() {
        var lines = CdsExportUnlinkedLaboratoryReadingSourceReader.notExported(READINGS);

        assertThat(lines)
                .containsExactly(
                        Map.entry(
                                "Laboratory readings whose report is not linked to this chart"
                                        + " (no test name, no date)",
                                1L),
                        Map.entry(
                                "Laboratory readings whose report is not linked to this chart"
                                        + " (HbA1c, 2020-02-29)",
                                2L),
                        Map.entry(
                                "Laboratory readings whose report is not linked to this chart"
                                        + " (Glucose, fasting, 2020-03-01)",
                                1L));
        // A test literally named like the mark adds to the same line rather than repeating it.
        assertThat(
                        CdsExportUnlinkedLaboratoryReadingSourceReader.notExported(
                                List.of(
                                        new CdsExportUnlinkedLaboratoryReadingDto("", "", 1),
                                        new CdsExportUnlinkedLaboratoryReadingDto(
                                                "no test name", "", 2))))
                .containsExactly(
                        Map.entry(
                                "Laboratory readings whose report is not linked to this chart"
                                        + " (no test name, no date)",
                                3L));
    }
}
