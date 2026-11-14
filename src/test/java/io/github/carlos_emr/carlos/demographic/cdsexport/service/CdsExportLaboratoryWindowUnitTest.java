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

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import io.github.carlos_emr.carlos.utility.CdsImportKeys;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.*;
import org.junit.jupiter.api.*;

/**
 * The LaboratoryResults assembly over a chart of imported results and native HL7 reports, read
 * whole and read window by window: the same occurrences in the same order with the same
 * receipts whatever the window, every row owned once, and no chart refused for its size.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportLaboratoryWindowUnitTest extends CdsExportLaboratoryWindowFixture {
    // ---- equivalence --------------------------------------------------------------------------

    @Test
    void shouldEmitImportedResultsThenNativeReports_inUnitOrderAndNumberTextOrder() throws Exception {
        mixedChart(6, 5);
        var xml = new String(compose(), StandardCharsets.UTF_8);
        // Six imported results, then four results of each of five native reports.
        assertThat(receipts).hasSize(6 + 4 * 5);
        assertThat(result.importedRows()).isEqualTo(6);
        assertThat(result.nativeOnlyRows()).isEqualTo(20);
        assertThat(result.retainedSourceFields()).isEqualTo(6);
        var importedOrder =
                units.values().stream().map(unit -> unit.fields().get("unit_key")).toList();
        assertThat(receipts.subList(0, 6).stream().map(r -> r.nativeId()).toList())
                .isEqualTo(importedOrder);
        // 8, 21, 34, 47, 60 as text: every number here has one or two digits and they sort
        // "21" < "34" < "47" < "60" < "8".
        assertThat(
                        receipts.subList(6, 26).stream()
                                .map(r -> r.nativeId().substring(0, r.nativeId().indexOf(':')))
                                .distinct()
                                .toList())
                .containsExactly("21", "34", "47", "60", "8");
        assertThat(xml).contains("Imported 00000001").contains("Ada101");
        // The whole chart: one pass over the report tables, one over the witnesses, one over
        // the routes, and no pass that lists the reports.
        assertThat(rowPasses).isEqualTo(1);
        assertThat(witnessPasses).isEqualTo(1);
        assertThat(routePasses).isEqualTo(1);
        assertThat(messagePasses).isZero();
    }

    @Test
    void shouldEmitTheSameBytesAndReceipts_whenAChartIsReadInSmallWindows() throws Exception {
        mixedChart(23, 19);
        var whole = compose();
        var expected = List.copyOf(receipts);
        assertThat(expected).hasSize(23 + 4 * 19);
        assertThat(messagePasses).isZero();
        for (int window : new int[] {1, 2, 5, 7, 40, 41}) {
            bounds = bounds.withWindow(window, 64L << 20);
            assertThat(compose()).as("window of %d", window).isEqualTo(whole);
            assertThat(receipts).as("window of %d", window).isEqualTo(expected);
            // The reports were listed once and read in ceil(42 / window) windows.
            assertThat(messagePasses).isEqualTo(1);
            assertThat(witnessPasses).isEqualTo((42 + window - 1) / window);
        }
        // The byte size alone: the chart fits the row count but not 60,000 bytes, so the whole
        // read is abandoned before anything is emitted and the chart is read in halves.
        bounds = bounds.withWindow(8192, 60_000);
        assertThat(compose()).isEqualTo(whole);
        assertThat(receipts).isEqualTo(expected);
        assertThat(witnessPasses).isGreaterThan(2);
    }

    @Test
    void shouldEmitTheSameBytes_whenOneReportIsLargerThanTheWholeWindow() throws Exception {
        mixedChart(4, 9);
        // One report far larger than the window's bytes, in the middle of the chart.
        nativeReport(50);
        var large = tables.get("hl7TextMessage").get(CdsExportPatientTableSourceReader.key(50));
        var fields = new HashMap<>(large.fields());
        fields.put(
                "message",
                Base64.getEncoder()
                        .encodeToString(
                                (message(50)
                                                + "NTE|1||"
                                                + "A long laboratory comment. ".repeat(8000)
                                                + "\r")
                                        .getBytes(StandardCharsets.UTF_8)));
        tables.get("hl7TextMessage").put(large.recordKey(), new CdsExportCapturedTextRowDto(large.table(), large.recordKey(), fields));
        var replaced = witness(tables.get("hl7TextMessage").get(large.recordKey()));
        witnesses.put("hl7TextMessage:50", replaced);
        var whole = compose();
        var expected = List.copyOf(receipts);
        bounds = bounds.withWindow(8192, 100_000);
        assertThat(compose()).isEqualTo(whole);
        assertThat(receipts).isEqualTo(expected);
    }

    @Test
    void shouldReadAChartPastTheFormerRowBound_inWindowsWithoutRefusingIt() throws Exception {
        // 2,731 native reports are 8,193 rows of the report tables: one more than the 8,192 the
        // assembly used to accept before it stopped the job with RESOURCE_LIMIT.
        for (int report = 1; report <= 2_731; report++) nativeReport(report);
        compose(false);
        assertThat(result.nativeOnlyRows()).isEqualTo(4L * 2_731);
        assertThat(messagePasses).isEqualTo(1);
        // 2,731 reports fit one window of 8,192 reports once the chart is read by report.
        assertThat(witnessPasses).isEqualTo(1);
    }

    @Test
    void shouldReadAnImportedChartPastTheFormerRowBound_inWindows() throws Exception {
        // 2,049 imported results are 8,196 rows.
        for (int number = 1; number <= 2_049; number++) importedResult(number, 10_000L + number);
        compose(false);
        assertThat(result.importedRows()).isEqualTo(2_049);
        assertThat(result.retainedSourceFields()).isEqualTo(2_049);
    }

    // ---- memory -------------------------------------------------------------------------------

    @Test
    void shouldHoldOneWindowOfReports_whileAChartOfManyReportsIsEmitted() throws Exception {
        // A return to loading the whole chart would keep every report's message reachable while
        // the reports are emitted. Each pass hands out fresh rows; while a window is emitted,
        // only that window's messages may still be reachable.
        int count = 2_048, window = 64;
        for (int report = 1; report <= count; report++) nativeReport(report);
        bounds = bounds.withWindow(window, 64L << 20);
        handed = new ArrayList<>();
        long[] most = {0}, least = {Long.MAX_VALUE};
        beforeWitnesses =
                () -> {
                    // The pass over the report tables has just ended. Of every report message
                    // any pass has handed out so far, only this window's are still held: the
                    // rows of every earlier window are gone.
                    System.gc();
                    long kept = handed.stream().filter(row -> row.get() != null).count();
                    most[0] = Math.max(most[0], kept);
                    least[0] = Math.min(least[0], kept);
                };
        compose(false);
        assertThat(result.nativeOnlyRows()).isEqualTo(4L * count);
        assertThat(witnessPasses).isEqualTo(count / window);
        assertThat(least[0]).isEqualTo(window);
        assertThat(most[0]).isEqualTo(window);
    }

    // ---- completeness -------------------------------------------------------------------------

    private void windows() {
        bounds = bounds.withWindow(3, 64L << 20);
    }

    @Test
    void shouldRefuseAMetadataRowOfNoReport_whenTheChartIsReadInWindows() throws Exception {
        mixedChart(4, 6);
        put(
                row(
                        Plan.LABORATORY_INFORMATION,
                        CdsExportPatientTableSourceReader.key(9_999_999),
                        Map.of("id", "9999999", "lab_no", "424242")));
        assertThatThrownBy(this::compose).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
        windows();
        assertThatThrownBy(this::compose).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRefuseAReportWithoutItsRouting_whenTheChartIsReadInWindows() throws Exception {
        mixedChart(4, 6);
        String key = CdsExportPatientTableSourceReader.key(7_000_000 + 34);
        tables.get("patientLabRouting").remove(key);
        witnesses.remove("patientLabRouting:" + (7_000_000 + 34));
        assertThatThrownBy(this::compose).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
        windows();
        assertThatThrownBy(this::compose).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRefuseAnUnwitnessedRow_whenTheChartIsReadInWindows() throws Exception {
        mixedChart(4, 6);
        witnesses.remove("hl7TextInfo:" + (5_000_000 + 47));
        assertThatThrownBy(this::compose).hasMessage("CDS_EXPORT_LABORATORY_OCCURRENCE_INVALID");
        windows();
        assertThatThrownBy(this::compose).hasMessage("CDS_EXPORT_LABORATORY_OCCURRENCE_INVALID");
    }

    @Test
    void shouldRefuseARowChangedSinceItsWitness_whenTheChartIsReadInWindows() throws Exception {
        mixedChart(4, 6);
        var info = tables.get("hl7TextInfo").get(CdsExportPatientTableSourceReader.key(5_000_000 + 60));
        var fields = new HashMap<>(info.fields());
        fields.put("accessionNum", "CHANGED");
        tables.get("hl7TextInfo")
                .put(info.recordKey(), new CdsExportCapturedTextRowDto(info.table(), info.recordKey(), fields));
        assertThatThrownBy(this::compose).hasMessage("CDS_EXPORT_LABORATORY_OCCURRENCE_INVALID");
        windows();
        assertThatThrownBy(this::compose).hasMessage("CDS_EXPORT_LABORATORY_OCCURRENCE_INVALID");
    }

    @Test
    void shouldRefuseAStructuredResultNoImportUnitNames_whenTheChartIsReadInWindows()
            throws Exception {
        mixedChart(4, 6);
        // A blocked result that no unit names: owned by no occurrence.
        var fields = new HashMap<String, String>();
        fields.put("id", resultId(900));
        fields.put("demographicNo", "27");
        fields.put("runId", RUN);
        fields.put("sourceBlocked", "Y");
        put(row(Plan.LABORATORY_RESULTS, resultId(900), fields));
        assertThatThrownBy(this::compose).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
        windows();
        assertThatThrownBy(this::compose).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRefuseTwoStructuredResultsOfOneReport_whenTheyFallInDifferentWindows()
            throws Exception {
        mixedChart(4, 6);
        // A second result that claims the report of the first imported result; no unit names it.
        var fields = new HashMap<String, String>();
        fields.put("id", resultId(901));
        fields.put("demographicNo", "27");
        fields.put("runId", RUN);
        fields.put("nativeLabNo", "2001");
        put(row(Plan.LABORATORY_RESULTS, resultId(901), fields));
        assertThatThrownBy(this::compose).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
        windows();
        assertThatThrownBy(this::compose).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRefuseAReportTwoImportUnitsName_whenTheyFallInDifferentWindows() throws Exception {
        mixedChart(7, 3);
        // The last unit names the report of the first as well as its own result.
        String last = RUN + ":" + resultId(7);
        var named = new ArrayList<>(references.get(last));
        named.set(1, reference("hl7TextMessage", "2001"));
        references.put(last, named);
        assertThatThrownBy(this::compose).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
        bounds = bounds.withWindow(2, 64L << 20);
        assertThatThrownBy(this::compose).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
    }

    @Test
    void shouldRefuseARouteOfNoReport_whenTheChartIsReadInWindows() throws Exception {
        mixedChart(4, 6);
        route(900, 424_242, "101", "A", "2026-07-12 10:00:00", null);
        assertThatThrownBy(this::compose).hasMessage("CDS_EXPORT_LABORATORY_OCCURRENCE_INVALID");
        windows();
        assertThatThrownBy(this::compose).hasMessage("CDS_EXPORT_LABORATORY_OCCURRENCE_INVALID");
    }

    @Test
    void shouldRefuseAnotherPatientsRouting_whenItsReportIsInNoWindowYet() throws Exception {
        mixedChart(4, 6);
        String key = CdsExportPatientTableSourceReader.key(7_000_000 + 8);
        var routing = tables.get("patientLabRouting").get(key);
        var fields = new HashMap<>(routing.fields());
        fields.put("demographic_no", "28");
        var changed = new CdsExportCapturedTextRowDto(routing.table(), key, fields);
        tables.get("patientLabRouting").put(key, changed);
        witnesses.put("patientLabRouting:" + (7_000_000 + 8), witness(changed));
        assertThatThrownBy(this::compose).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
        windows();
        assertThatThrownBy(this::compose).hasMessage("CDS_EXPORT_UNMAPPED_DATA");
        // Refused by the first window, before anything of a later window is emitted.
        assertThat(receipts).isEmpty();
    }

    // ---- the other assemblies -----------------------------------------------------------------

    private List<String> visit() throws Exception {
        capture();
        rowPasses = witnessPasses = routePasses = messagePasses = 0;
        var visited = new ArrayList<String>();
        long count =
                service()
                        .forEachNativeSource(
                                lease,
                                patient,
                                source.capture().tables().keySet(),
                                source,
                                index,
                                bounds,
                                control,
                                new CdsExportLaboratoryOccurrenceService.NativeSources() {
                                    @Override
                                    public boolean routed(
                                            CdsExportLaboratoryOccurrenceService.NativeSource report) {
                                        return Long.parseLong(report.id()) % 2 == 0;
                                    }

                                    @Override
                                    public void accept(
                                            CdsExportLaboratoryOccurrenceService.NativeSource report,
                                            List<CdsExportCapturedTextRowDto> routes) {
                                        visited.add(
                                                report.id()
                                                        + "/"
                                                        + report.parsed().observations().size()
                                                        + "/"
                                                        + routes.stream()
                                                                .map(route -> route.fields().get("id"))
                                                                .toList());
                                    }
                                });
        assertThat(count).isEqualTo(visited.size());
        return visited;
    }

    @Test
    void shouldFeedTheSameNativeReportsAndRoutes_whenAChartIsReadInSmallWindows() throws Exception {
        mixedChart(9, 14);
        var whole = visit();
        assertThat(whole).hasSize(14);
        // Report 8 has two routes and was asked for; report 21 has none it was asked for.
        assertThat(whole).contains("8/4/[" + routeIds(8) + "]").contains("21/4/[]");
        assertThat(messagePasses).isZero();
        for (int window : new int[] {1, 3, 14}) {
            bounds = bounds.withWindow(window, 64L << 20);
            assertThat(visit()).as("window of %d", window).isEqualTo(whole);
            assertThat(messagePasses).isEqualTo(1);
        }
        bounds = bounds.withWindow(8192, 40_000);
        assertThat(visit()).isEqualTo(whole);
    }

    private String routeIds(long lab) {
        return String.join(
                ", ",
                tables.get(CdsExportLaboratoryOccurrenceService.ROUTES).values().stream()
                        .filter(route -> route.fields().get("lab_no").equals(Long.toString(lab)))
                        .map(route -> route.fields().get("id"))
                        .toList());
    }

    private List<String> comments() throws Exception {
        capture();
        routePasses = 0;
        var visited = new ArrayList<String>();
        service()
                .forEachImportedComments(
                        lease,
                        patient,
                        source.capture().tables().keySet(),
                        source,
                        index,
                        bounds,
                        control,
                        comments ->
                                visited.add(
                                        comments.result().substring(56)
                                                + "/"
                                                + comments.lab()
                                                + "/"
                                                + comments.routes().size()));
        return visited;
    }

    @Test
    void shouldFeedTheSameCommentedImportedResults_whenTheRoutesAreReadInSmallWindows()
            throws Exception {
        for (int number = 1; number <= 12; number++) importedResult(number, 3_000L + number);
        route(1, 3_002, "101", "A", "2026-07-12 10:00:00", "Recheck");
        route(2, 3_002, "102", "A", "2026-07-12 11:00:00", null);
        route(3, 3_009, "102", "A", "2026-07-13 11:00:00", "Call patient");
        route(4, 3_011, "101", "A", "2026-07-13 12:00:00", null);
        var whole = comments();
        assertThat(whole).containsExactly("00000002/3002/1", "00000009/3009/1");
        assertThat(routePasses).isEqualTo(1);
        bounds = bounds.withWindow(3, 64L << 20);
        assertThat(comments()).isEqualTo(whole);
        // Four routes are more than one window of three: the twelve results in four windows.
        assertThat(routePasses).isEqualTo(4);
    }
}
