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
package io.github.carlos_emr.carlos.demographic.cds.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsMergedChartContract.ChartRecord;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsMergedChartContract.Link;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsMergedChartContract.LinkRow;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsMergedChartContract.LinksRecord;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsMergedChartContract.PrivilegeRow;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsMergedChartContract.Range;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsMergedChartContract.Shape;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The group rule, positions, sender identifiers and the two records' strict forms. */
@Tag("unit")
@Tag("cds-export")
class CdsMergedChartContractUnitTest {
    private static final Set<Integer> EXISTING = Set.of(1, 2, 3, 4, 5, 6, 7, 9, 12, 34, 56);
    private static final String RESIDUAL =
            "<ResidualInfo><cdsd:DataElement><cdsd:Name>CARLOS.Demographic.v1</cdsd:Name>"
                    + "<cdsd:DataType>string</cdsd:DataType><cdsd:Content>{}</cdsd:Content>"
                    + "</cdsd:DataElement></ResidualInfo>";

    private static CdsMergedChartContract.Groups groups(Link... rows) {
        return CdsMergedChartContract.groups(List.of(rows), EXISTING::contains);
    }

    @Test
    void shouldFollowEveryActiveLink_toTheHeadOfAChain() {
        var groups = groups(new Link(1, 1, 2, 0), new Link(2, 2, 3, 0));
        assertThat(groups.head(1)).isEqualTo(3);
        assertThat(groups.head(2)).isEqualTo(3);
        assertThat(groups.head(3)).isEqualTo(3);
        assertThat(groups.group(3)).containsExactly(1, 2, 3);
        assertThat(groups.shape(3)).containsExactly(Shape.CHAIN);
        // A chart no row names is its own head, alone.
        assertThat(groups.head(9)).isEqualTo(9);
        assertThat(groups.group(9)).containsExactly(9);
    }

    @Test
    void shouldFollowOnlyTheNewestActiveLink_whenAChartIsMergedTwice() {
        var groups = groups(new Link(5, 1, 2, 0), new Link(9, 1, 3, 0));
        assertThat(groups.parent(1)).isEqualTo(3);
        assertThat(groups.head(1)).isEqualTo(3);
        assertThat(groups.group(3)).containsExactly(1, 3);
        // The older link's chart keeps its own file.
        assertThat(groups.group(2)).containsExactly(2);
        assertThat(groups.shape(3)).containsExactly(Shape.FORK);
    }

    @Test
    void shouldTakeTheSmallestChartOfACycle_asItsHead() {
        var groups = groups(new Link(1, 5, 7, 0), new Link(2, 7, 5, 0), new Link(3, 9, 7, 0));
        assertThat(groups.head(5)).isEqualTo(5);
        assertThat(groups.head(7)).isEqualTo(5);
        assertThat(groups.head(9)).isEqualTo(5);
        assertThat(groups.group(5)).containsExactly(5, 7, 9);
        assertThat(groups.shape(5)).contains(Shape.CYCLE);
    }

    @Test
    void shouldKeepAChartLinkedToItself_itsOwnHead() {
        var groups = groups(new Link(4, 4, 4, 0));
        assertThat(groups.head(4)).isEqualTo(4);
        assertThat(groups.group(4)).containsExactly(4);
        assertThat(groups.shape(4)).containsExactly(Shape.SELF_LINK);
    }

    @Test
    void shouldNeverFollowALinkNamingAMissingChart_andRecordItAsDangling() {
        // 8 and 10 are not charts: one link leaves a chart for nothing, one arrives from nothing.
        var groups = groups(new Link(1, 6, 8, 0), new Link(2, 10, 6, 0));
        assertThat(groups.head(6)).isEqualTo(6);
        assertThat(groups.group(6)).containsExactly(6);
        assertThat(groups.shape(6)).containsExactly(Shape.DANGLING);
        assertThat(groups.charts()).containsExactly(6);
    }

    @Test
    void shouldTreatEveryDeletedValueOtherThanZero_asHistory() {
        var groups =
                groups(
                        new Link(1, 1, 2, 1),
                        new Link(2, 3, 2, 2),
                        new Link(3, 4, 2, -1),
                        new Link(4, 5, 2, 0));
        assertThat(groups.head(1)).isEqualTo(1);
        assertThat(groups.head(3)).isEqualTo(3);
        assertThat(groups.head(4)).isEqualTo(4);
        assertThat(groups.group(2)).containsExactly(2, 5);
        assertThat(groups.shape(2)).isEmpty();
    }

    @Test
    void shouldRefuseTheSameRowTwice_inOneGrouping() {
        assertThatThrownBy(() -> groups(new Link(1, 1, 2, 0), new Link(1, 1, 2, 0)))
                .hasMessage("CDS_MERGED_CHART_CARRIER_INVALID");
    }

    @Test
    void shouldNameAChartByItsSenderIdentifier_andReadOnlyThatForm() {
        assertThat(CdsMergedChartContract.senderId(34)).isEqualTo("C34");
        assertThat(CdsMergedChartContract.chart("C34")).isEqualTo(34);
        assertThat(CdsMergedChartContract.chart("C2147483647")).isEqualTo(Integer.MAX_VALUE);
        for (String malformed :
                new String[] {"C0", "C034", "34", "c34", "C-1", "C2147483648", "C 1", ""})
            assertThatThrownBy(() -> CdsMergedChartContract.chart(malformed))
                    .hasMessage("CDS_MERGED_CHART_CARRIER_INVALID");
        assertThatThrownBy(() -> CdsMergedChartContract.chart(null))
                .hasMessage("CDS_MERGED_CHART_CARRIER_INVALID");
        assertThatThrownBy(() -> CdsMergedChartContract.senderId(0))
                .hasMessage("CDS_MERGED_CHART_CARRIER_INVALID");
    }

    @Test
    void shouldAcceptRangesInsideTheirSections_andRefuseOverlapsOrOverruns() {
        var sections =
                Map.of(
                        CdsCategory.APPOINTMENTS, 5L,
                        CdsCategory.NEW_CATEGORY, 8L,
                        CdsCategory.DEMOGRAPHICS, 1L);
        var first = new TreeMap<CdsCategory, Range>();
        first.put(CdsCategory.APPOINTMENTS, new Range(3, 2));
        first.put(CdsCategory.NEW_CATEGORY, new Range(2, 3));
        var second = new TreeMap<CdsCategory, Range>();
        second.put(CdsCategory.NEW_CATEGORY, new Range(5, 2));
        CdsMergedChartContract.requirePositions(sections, List.of(first, second));
        var overlapping = new TreeMap<CdsCategory, Range>();
        overlapping.put(CdsCategory.NEW_CATEGORY, new Range(4, 1));
        assertThatThrownBy(
                        () ->
                                CdsMergedChartContract.requirePositions(
                                        sections, List.of(first, overlapping)))
                .hasMessage("CDS_MERGED_CHART_CARRIER_INVALID");
        var overrun = new TreeMap<CdsCategory, Range>();
        overrun.put(CdsCategory.APPOINTMENTS, new Range(5, 2));
        assertThatThrownBy(
                        () -> CdsMergedChartContract.requirePositions(sections, List.of(overrun)))
                .hasMessage("CDS_MERGED_CHART_CARRIER_INVALID");
        var absent = new TreeMap<CdsCategory, Range>();
        absent.put(CdsCategory.REPORTS, new Range(1, 1));
        assertThatThrownBy(
                        () -> CdsMergedChartContract.requirePositions(sections, List.of(absent)))
                .hasMessage("CDS_MERGED_CHART_CARRIER_INVALID");
        assertThatThrownBy(() -> new Range(0, 1)).hasMessage("CDS_MERGED_CHART_CARRIER_INVALID");
        assertThatThrownBy(() -> new Range(1, 0)).hasMessage("CDS_MERGED_CHART_CARRIER_INVALID");
    }

    @Test
    void shouldReadTheRecordOfAMergedChart_exactlyAsItWasWritten() {
        String document = document("C34", "");
        var positions = new TreeMap<CdsCategory, Range>();
        positions.put(CdsCategory.NEW_CATEGORY, new Range(4, 2));
        positions.put(CdsCategory.APPOINTMENTS, new Range(2, 1));
        var record =
                new ChartRecord(
                        34,
                        12,
                        positions,
                        document,
                        CdsMergedChartContract.sha256(document.getBytes(StandardCharsets.UTF_8)));
        String json = CdsMergedChartContract.json(record);
        assertThat(json)
                .startsWith(
                        "{\"version\":1,\"chart\":\"C34\",\"head\":\"C12\",\"positions\":"
                                + "{\"Appointments\":{\"first\":2,\"count\":1},"
                                + "\"NewCategory\":{\"first\":4,\"count\":2}},\"document\":");
        assertThat(CdsMergedChartContract.chartRecord(json)).isEqualTo(record);
        CdsMergedChartContract.verifyDocument(record);
        // Another digest, a member more or less, another version, an identifier of another form.
        for (String malformed :
                List.of(
                        json.replace(record.documentSha256(), "0".repeat(64)),
                        json.replace("\"version\":1", "\"version\":2"),
                        json.replace("\"chart\":\"C34\"", "\"chart\":\"34\""),
                        json.replace("\"head\":\"C12\"", "\"head\":\"C34\""),
                        json.replace("{\"version\":1,", "{\"version\":1,\"extra\":0,"),
                        json.replace("\"first\":2", "\"first\":0"),
                        json.replace("\"Appointments\"", "\"Demographics\""),
                        "{}",
                        "not json"))
            assertThatThrownBy(() -> CdsMergedChartContract.chartRecord(malformed))
                    .hasMessage("CDS_MERGED_CHART_CARRIER_INVALID");
    }

    @Test
    void shouldRefuseAStandaloneDocument_thatIsNotTheChartsOwnDemographics() {
        for (String wrong :
                List.of(
                        // Another chart's identifier.
                        document("C35", ""),
                        // A record other than the native demographic record beside it.
                        document(
                                "C34",
                                "<NewCategory><CategoryName>CARLOS.NativeAppointment.v1</CategoryName>"
                                        + "<CategoryDescription>x</CategoryDescription>"
                                        + RESIDUAL
                                        + "</NewCategory>"),
                        // A DTD is forbidden even when the document otherwise validates.
                        document("C34", "").replace("<OmdCds", "<!DOCTYPE OmdCds><OmdCds"),
                        // Not schema-valid.
                        document("C34", "<Unknown/>"))) {
            var record =
                    new ChartRecord(
                            34,
                            12,
                            new TreeMap<>(),
                            wrong,
                            CdsMergedChartContract.sha256(wrong.getBytes(StandardCharsets.UTF_8)));
            assertThatThrownBy(() -> CdsMergedChartContract.verifyDocument(record))
                    .hasMessage("CDS_MERGED_CHART_CARRIER_INVALID");
        }
        String withNative =
                document(
                        "C34",
                        "<NewCategory><CategoryName>CARLOS.NativeDemographic.v1</CategoryName>"
                                + "<CategoryDescription>Native demographic values</CategoryDescription>"
                                + RESIDUAL
                                + "</NewCategory>");
        CdsMergedChartContract.verifyDocument(
                new ChartRecord(
                        34,
                        12,
                        new TreeMap<>(),
                        withNative,
                        CdsMergedChartContract.sha256(withNative.getBytes(StandardCharsets.UTF_8))));
    }

    @Test
    void shouldReadTheRecordOfTheLinks_exactlyAsItWasWritten() {
        var record =
                new LinksRecord(
                        List.of(12, 34, 56),
                        List.of(
                                new LinkRow("3", "34", "12", "0", "999998", "2026-01-02", false),
                                new LinkRow("7", "56", "12", "1", null, null, false),
                                new LinkRow("9", "77", "34", "0", "999998", "2026-02-03", true)),
                        List.of(
                                new PrivilegeRow(34, "_all", "_eChart$34", "|or|", "0", "0"),
                                new PrivilegeRow(56, "_all", "_eChart$56", "|or|", null, null)),
                        new TreeSet<>(Set.of(Shape.CHAIN)),
                        new TreeSet<>(Set.of(CdsMergedChartContract.CHANGED_AFTER_SELECTION)));
        String json = CdsMergedChartContract.json(record);
        assertThat(json)
                .contains("\"charts\":[\"C12\",\"C34\",\"C56\"]")
                .contains(
                        "{\"id\":\"7\",\"demographic_no\":\"56\",\"merged_to\":\"12\",\"deleted\":\"1\","
                                + "\"lastUpdateUser\":null,\"lastUpdateDate\":null}")
                .contains("\"outside\":true");
        assertThat(CdsMergedChartContract.linksRecord(json)).isEqualTo(record);
        for (String malformed :
                List.of(
                        json.replace("\"C34\",\"C56\"", "\"C34\",\"56\""),
                        json.replace("\"outside\":true", "\"outside\":false"),
                        json.replace("\"id\":\"7\"", "\"id\":\"2\""),
                        json.replace("\"shape\":[\"CHAIN\"]", "\"shape\":[\"TANGLE\"]"),
                        json.replace("\"lastUpdateUser\":\"999998\"", "\"lastUpdateUser\":7")))
            assertThatThrownBy(() -> CdsMergedChartContract.linksRecord(malformed))
                    .hasMessage("CDS_MERGED_CHART_CARRIER_INVALID");
    }

    /** A schema-valid one-patient file with only Demographics, and what follows it. */
    private static String document(String senderId, String after) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><OmdCds xmlns=\"cds\" xmlns:cds=\"cds\""
                + " xmlns:cdsd=\"cds_dt\"><PatientRecord><Demographics><Names><cdsd:LegalName"
                + " namePurpose=\"L\"><cdsd:FirstName><cdsd:Part>FAKE-Merged</cdsd:Part>"
                + "<cdsd:PartType>GIV</cdsd:PartType></cdsd:FirstName><cdsd:LastName>"
                + "<cdsd:Part>FAKE-Chart</cdsd:Part><cdsd:PartType>FAMC</cdsd:PartType>"
                + "</cdsd:LastName></cdsd:LegalName></Names><DateOfBirth>1971-03-04</DateOfBirth>"
                + "<Gender>F</Gender><UniqueVendorIdSequence>"
                + senderId
                + "</UniqueVendorIdSequence><PersonStatusCode><PersonStatusAsEnum>A"
                + "</PersonStatusAsEnum></PersonStatusCode></Demographics>"
                + after
                + "</PatientRecord></OmdCds>";
    }
}
