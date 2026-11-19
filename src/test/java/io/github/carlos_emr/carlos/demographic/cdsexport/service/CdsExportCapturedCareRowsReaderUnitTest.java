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

import static io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportCareCaptureFixture.*;
import static org.assertj.core.api.Assertions.*;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.support.CdsExportMappingReviewException;
import java.util.*;
import org.junit.jupiter.api.*;

/**
 * Issue #6200 (CARE-5): a chart with more measurements than one export window holds is read in
 * windows, never held for its size. Before, more than 8,192 observations (or 64 MiB of rows)
 * held the patient with CDS_EXPORT_NATIVE_CARE_PATIENT_LIMIT_EXCEEDED.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportCapturedCareRowsReaderUnitTest {
    private static final CdsExportCapturedCareRowsReader.Limits DEFAULT =
            new CdsExportCapturedCareRowsReader.Limits(8192, 64L << 20);
    private CdsExportCareCaptureFixture fixture;

    @BeforeEach
    void setup() throws Exception {
        fixture =
                new CdsExportCareCaptureFixture(
                        EnumSet.of(
                                CdsCategory.DEMOGRAPHICS,
                                CdsCategory.CARE_ELEMENTS,
                                CdsCategory.NEW_CATEGORY));
        fixture.add(vocabulary(1, "WT"));
    }

    /** Years of flowsheet entries: 20,000 weights, 300 deleted versions and their extensions. */
    private void longStandingChart() {
        for (long id = 1; id <= 20_000; id++) fixture.add(measurement(id, "WT", "70"));
        for (long i = 0; i < 300; i++) {
            fixture.add(deleted(900_000 + i, 30_000 + i));
            fixture.add(extension(800_000 + i, 30_000 + i, "comment", "scale not zeroed"));
        }
        for (long id = 1; id <= 200; id++)
            fixture.add(extension(700_000 + id, id * 50, "comment", "after exercise"));
    }

    @Test
    void shouldReadEveryMeasurementOnce_inWindowsOfAtMostTheObservationLimit() throws Exception {
        longStandingChart();
        var capture = fixture.reader().capture(DEFAULT);
        assertThat(capture.windowed()).isTrue();
        // 20,300 observations at 8,192 a window.
        assertThat(capture.windows()).isEqualTo(3);
        var seen = new HashSet<String>();
        var ownerWindow = new HashMap<Long, Integer>();
        String previousLast = null;
        for (int window = 0; window < capture.windows(); window++) {
            var current = capture.window(window).current();
            assertThat(current).containsKeys("measurementType:1", "demographic:27");
            long observations =
                    current.values().stream()
                            .filter(row -> !row.table().equals("measurementsExt"))
                            .filter(row -> row.table().startsWith("measurements"))
                            .count();
            assertThat(observations).isBetween(1L, 8192L);
            var owners = new TreeSet<String>();
            for (var entry : current.entrySet()) {
                var row = entry.getValue();
                if (!row.table().startsWith("measurements")) continue;
                assertThat(seen.add(entry.getKey())).isTrue();
                long owner = owner(row);
                owners.add(Long.toString(owner));
                assertThat(ownerWindow.putIfAbsent(owner, window)).isIn(null, window);
            }
            // Windows follow the decimal order the exported occurrences follow.
            if (previousLast != null) assertThat(owners.first()).isGreaterThan(previousLast);
            previousLast = owners.last();
        }
        long rows =
                fixture.tables.values().stream()
                        .flatMap(table -> table.values().stream())
                        .filter(row -> row.table().startsWith("measurements"))
                        .count();
        assertThat(seen).hasSize((int) rows);
        // One first pass, then one row pass and one witness pass for each window.
        assertThat(fixture.rowScans).isEqualTo(4);
        assertThat(fixture.witnessScans).isEqualTo(3);
    }

    @Test
    void shouldKeepAChartThatFitsOneWindow_inTheSingleFirstPass() throws Exception {
        for (long id = 1; id <= 100; id++) fixture.add(measurement(id, "WT", "70"));
        var capture = fixture.reader().capture(DEFAULT);
        assertThat(capture.windowed()).isFalse();
        assertThat(capture.windows()).isEqualTo(1);
        assertThat(capture.window(0).current()).hasSize(102);
        assertThat(capture.window(0).exact()).hasSize(102);
        // The same two scans as before windows existed.
        assertThat(fixture.rowScans).isEqualTo(1);
        assertThat(fixture.witnessScans).isEqualTo(1);
    }

    @Test
    void shouldOrderIdsAsTheirDecimalStringsSort_forByteStableOutput() {
        var random = new Random(6200);
        var ids = new ArrayList<Long>(List.of(1L, 9L, 10L, 99L, 100L, 101L, 1_000_000L, Long.MAX_VALUE));
        for (int i = 0; i < 2000; i++) ids.add(1 + (random.nextLong() >>> 1) % (Long.MAX_VALUE - 1));
        for (int i = 0; i < 2000; i++) ids.add(1 + (long) random.nextInt(1_000_000));
        for (long left : ids)
            for (long right : ids.subList(0, 64))
                assertThat(Integer.signum(CdsExportCapturedCareRowsReader.lexical(left, right)))
                        .isEqualTo(
                                Integer.signum(
                                        Long.toString(left).compareTo(Long.toString(right))));
    }

    @Test
    void shouldNeverSplitOneObservationAndItsVersions_acrossWindows() throws Exception {
        // Owner 5 is a current observation and two deleted versions (no extensions, which would
        // make them ambiguous): three observations, more than a window of two, kept together.
        fixture.add(measurement(5, "WT", "70"));
        fixture.add(deleted(905, 5));
        fixture.add(deleted(906, 5));
        for (long id = 10; id < 20; id++) fixture.add(measurement(id, "WT", "70"));
        fixture.add(extension(701, 12, "comment", "first"));
        fixture.add(extension(702, 12, "unit", "kg"));
        var capture =
                fixture.reader().capture(new CdsExportCapturedCareRowsReader.Limits(2, 64L << 20));
        assertThat(capture.windowed()).isTrue();
        var windowOf = new HashMap<String, Integer>();
        for (int window = 0; window < capture.windows(); window++)
            for (var row : capture.window(window).current().values())
                if (row.table().startsWith("measurements"))
                    windowOf.put(row.table() + ":" + row.fields().get("id"), window);
        assertThat(windowOf.get("measurementsDeleted:905"))
                .isEqualTo(windowOf.get("measurements:5"))
                .isEqualTo(windowOf.get("measurementsDeleted:906"));
        assertThat(windowOf.get("measurementsExt:701"))
                .isEqualTo(windowOf.get("measurements:12"))
                .isEqualTo(windowOf.get("measurementsExt:702"));
        assertThat(windowOf).hasSize(15);
    }

    @Test
    void shouldHoldAnAmbiguousExtensionOwner_beforeAnyWindowIsRead() throws Exception {
        longStandingChart();
        // Extension 800000 belongs to deleted original 30000; a live 30000 makes it ambiguous.
        fixture.add(measurement(30_000, "WT", "70"));
        var reader = fixture.reader();
        assertThatThrownBy(() -> reader.capture(DEFAULT))
                .isInstanceOf(CdsExportMappingReviewException.class)
                .hasMessage("CDS_EXPORT_NATIVE_CARE_EXTENSION_OWNER_AMBIGUOUS");
        assertThat(fixture.witnessScans).isZero();
    }

    @Test
    void shouldRefuseAnExtensionThatNoObservationOwns_forTheWholeChart() throws Exception {
        longStandingChart();
        fixture.add(extension(799_999, 55_555, "comment", "orphan"));
        var reader = fixture.reader();
        assertThatThrownBy(() -> reader.capture(DEFAULT))
                .hasMessage("CDS_EXPORT_CARE_OCCURRENCE_INVALID");
    }

    @Test
    void shouldRefuseAWindowRowThatDiffersFromItsWitness_whenThatWindowIsRead() throws Exception {
        longStandingChart();
        var changed = new HashMap<>(measurement(9_999, "WT", "70").fields());
        changed.put("dataField", "71");
        fixture.witnessOverrides.put(
                "measurements:9999",
                witness(
                        new CdsExportCapturedTextRowDto(
                                "measurements",
                                CdsExportPatientTableSourceReader.key(9_999),
                                changed)));
        var capture = fixture.reader().capture(DEFAULT);
        int failing = -1;
        for (int window = 0; window < capture.windows(); window++) {
            try {
                capture.window(window);
            } catch (java.io.IOException refused) {
                assertThat(refused).hasMessage("CDS_EXPORT_CARE_OCCURRENCE_INVALID");
                failing = window;
            }
        }
        assertThat(failing).isNotNegative();
    }

    @Test
    void shouldGiveImportedObservationsTheirRows_withoutTheRestOfTheChart() throws Exception {
        longStandingChart();
        fixture.add(extension(699_999, 17, "cdsCareElement", "Weight"));
        var capture = fixture.reader().capture(DEFAULT);
        var slice = capture.owners(Set.of("50", "30001"), Set.of("699999"));
        assertThat(slice.current().keySet())
                .containsExactlyInAnyOrder(
                        "demographic:27",
                        "measurementType:1",
                        "measurements:50",
                        "measurementsExt:700001",
                        "measurementsDeleted:900001",
                        "measurementsExt:800001",
                        "measurementsExt:699999");
        assertThat(slice.exact().keySet()).isEqualTo(slice.current().keySet());
    }

    private static long owner(CdsExportCapturedTextRowDto row) {
        return Long.parseLong(
                switch (row.table()) {
                    case "measurementsDeleted" -> row.fields().get("originalId");
                    case "measurementsExt" -> row.fields().get("measurement_id");
                    default -> row.fields().get("id");
                });
    }
}
