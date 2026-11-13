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
import static io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportNativeHistoryMapperUnitTest.render;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportNativeLaboratoryMapperUnitTest {
    static String segment(String name, int fields, Map<Integer, String> values) {
        var parts = new String[fields + 1];
        Arrays.fill(parts, "");
        parts[0] = name;
        values.forEach((i, value) -> parts[i] = value);
        return String.join("|", parts) + "\r";
    }

    static String message() {
        String header =
                "MSH|^~\\&|LABAPP|Native"
                        + " Lab|CARLOS|Clinic|20260710123000-0400||ORU^R01|native-1|P|2.5\r"
                        + "PID|1||EXTERNAL-27^^^LAB^MR||Synthetic^Native||20000102|F\r";
        String order =
                segment(
                        "OBR",
                        7,
                        Map.of(
                                1,
                                "1",
                                3,
                                "ACC-1",
                                4,
                                "PANEL^Metabolic panel^L",
                                6,
                                "20260708100000-0400",
                                7,
                                "20260709143500-0400"));
        String one =
                segment(
                        "OBX",
                        14,
                        Map.of(
                                1,
                                "1",
                                2,
                                "NM",
                                3,
                                "2345-7^Glucose^LN",
                                5,
                                "5.4",
                                6,
                                "mmol/L",
                                7,
                                "3.5-7.8",
                                8,
                                "N",
                                11,
                                "F",
                                14,
                                "20260709143500-0400"));
        String note = segment("NTE", 3, Map.of(1, "1", 3, "Exact café \\F\\ details 😀"));
        String two =
                segment(
                        "OBX",
                        14,
                        Map.of(
                                1,
                                "2",
                                2,
                                "ST",
                                3,
                                "COMMENT^Narrative^L",
                                5,
                                "A\\T\\B",
                                8,
                                "A",
                                11,
                                "F",
                                14,
                                "20260709143500-0400"));
        String secondOrder =
                segment(
                        "OBR",
                        7,
                        Map.of(1, "2", 3, "ACC-2", 4, "OTHER^Other panel^L", 7, "20260709"));
        String repeated =
                segment(
                        "OBX",
                        11,
                        Map.of(
                                1,
                                "1",
                                2,
                                "NM",
                                3,
                                "NATIVE^Repeated native result^L",
                                5,
                                "10~11",
                                6,
                                "mg/L",
                                11,
                                "P"));
        return header + order + one + note + two + secondOrder + repeated;
    }

    static CdsExportLaboratoryOccurrenceService.NativeSource source(String text) throws Exception {
        var message =
                row(
                        Plan.LABORATORY_MESSAGES,
                        "91",
                        Map.of(
                                "lab_id",
                                "91",
                                "message",
                                Base64.getEncoder()
                                        .encodeToString(text.getBytes(StandardCharsets.UTF_8)),
                                "type",
                                "HL7"));
        var information =
                row(
                        Plan.LABORATORY_INFORMATION,
                        "92",
                        Map.of(
                                "id",
                                "92",
                                "lab_no",
                                "91",
                                "sending_facility",
                                "Native Lab",
                                "accessionNum",
                                "ACC-1"));
        var routing =
                row(
                        Plan.LABORATORY_ROUTING,
                        "93",
                        Map.of(
                                "id",
                                "93",
                                "lab_no",
                                "91",
                                "demographic_no",
                                "27",
                                "lab_type",
                                "HL7"));
        return new CdsExportLaboratoryOccurrenceService.NativeSource(
                "91",
                message,
                information,
                routing,
                new CdsExportNativeLaboratoryParser()
                        .parse(message.fields().get("message"), () -> {}));
    }

    private static CdsExportCapturedTextRowDto row(
            Plan plan, String id, Map<String, String> values) {
        var fields = new LinkedHashMap<String, String>();
        plan.columns().forEach(c -> fields.put(c, null));
        fields.putAll(values);
        return new CdsExportCapturedTextRowDto(plan.table(), id, fields);
    }

    @Test
    void shouldAccountForEveryOrderObservationAndValueRepetition_beforeAnyClinicalMapping()
            throws Exception {
        assertThat(source(message()).parsed().observations())
                .extracting(CdsExportNativeLaboratoryParser.Observation::ordinal)
                .containsExactly("1:1:0", "1:2:0", "2:1:0", "2:1:1");
    }

    @Test
    void
            shouldExportEachNativeObservationThroughThePinnedSchema_withItsActualCodesValuesUnitsFlagsAndComments()
                    throws Exception {
        var source = source(message());
        var mapper = new CdsExportNativeLaboratoryMapper();
        var values = mapper.map(source, ZoneId.of("America/Toronto"));
        assertThat(values).hasSize(4);
        assertThat(values.getFirst().text())
                .containsEntry("ResultValue", "5.4")
                .containsEntry("ResultUnit", "mmol/L")
                .containsEntry("LabTestCode", "2345-7")
                .containsEntry("NotesFromLab", "Exact café | details 😀");
        assertThat(values.get(1).text()).containsEntry("ResultValue", "A&B");
        assertThat(values.get(2).text()).containsEntry("ResultValue", "10");
        assertThat(values.get(3).text()).containsEntry("ResultValue", "11");
        // No flag is the data dictionary's Unknown (review LAB-05).
        assertThat(values.get(2).flag()).isEqualTo("U");
        assertThat(values.get(2).enumFlag()).isTrue();
        assertThat(values.getFirst().dates().get("CollectionDateTime").lexical())
                .isEqualTo("2026-07-09T14:35:00-04:00");
        for (var value : values)
            assertThat(render(CdsCategory.LABORATORY_RESULTS, xml -> mapper.write(value, xml)))
                    .isNotEmpty();
        assertThat(render(CdsCategory.NEW_CATEGORY, xml -> mapper.writeSupporting(source, xml)))
                .isNotEmpty();
        var assessments = CdsExportNativeLaboratoryMapper.assessments(source);
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(source.rows(), assessments, 100000, () -> {});
        assertThat(assessments)
                .hasSize(source.rows().stream().mapToInt(r -> r.fields().size()).sum());
    }

    @Test
    void shouldMarkTheLaboratorysDoNotDiscloseResults_asBlockedTestResults() throws Exception {
        String text = message();
        assertThat(text).contains("|F|||20260709143500-0400\rNTE");
        var mapper = new CdsExportNativeLaboratoryMapper();
        // CML/GDML: OBX-13 "BLOCKED" blocks that one result.
        var oneBlocked =
                mapper.map(
                        source(text.replace("|F|||20260709143500-0400\rNTE", "|F||BLOCKED|20260709143500-0400\rNTE")),
                        ZoneId.of("America/Toronto"));
        assertThat(oneBlocked).extracting(CdsExportNativeLaboratoryMapper.Value::blocked)
                .containsExactly(true, false, false, false);
        String xml =
                new String(
                        render(
                                CdsCategory.LABORATORY_RESULTS,
                                body -> mapper.write(oneBlocked.getFirst(), body)),
                        StandardCharsets.UTF_8);
        assertThat(xml).containsPattern("TestResultStatus>F</[^>]*TestResultStatus><[^>]*BlockedTestResult>Y</");
        assertThat(
                        new String(
                                render(
                                        CdsCategory.LABORATORY_RESULTS,
                                        body -> mapper.write(oneBlocked.get(1), body)),
                                StandardCharsets.UTF_8))
                .doesNotContain("BlockedTestResult");
        // MDS: a ZPD segment with ZPD-3 "Y" blocks every result of the message.
        var allBlocked =
                mapper.map(source(text + "ZPD|1||Y\r"), ZoneId.of("America/Toronto"));
        assertThat(allBlocked).extracting(CdsExportNativeLaboratoryMapper.Value::blocked)
                .containsOnly(true);
        assertThat(mapper.map(source(text + "ZPD|1||N\r"), ZoneId.of("America/Toronto")))
                .extracting(CdsExportNativeLaboratoryMapper.Value::blocked)
                .containsOnly(false);
    }

    @Test
    void shouldKeepTextALaboratoryLeftUnescaped_insteadOfCuttingItAtTheFirstSeparator()
            throws Exception {
        // Review LAB-02: real feeds send unescaped & and ^ inside text.
        var source =
                source(
                        message()
                                .replace(
                                        "|ST|COMMENT^Narrative^L||A\\T\\B|",
                                        "|ST|COMMENT^Narrative^L||Hemolyzed & lipemic ^ repeat|")
                                .replace("Exact café \\F\\ details 😀", "Specimen A & B ^ C")
                                .replace("|mmol/L|3.5-7.8|", "|^mmol/L|3.5^5.0|"));
        var values = new CdsExportNativeLaboratoryMapper().map(source, ZoneId.of("America/Toronto"));
        assertThat(values.get(1).text()).containsEntry("ResultValue", "Hemolyzed & lipemic ^ repeat");
        assertThat(values.getFirst().text())
                .containsEntry("NotesFromLab", "Specimen A & B ^ C")
                .containsEntry("ReferenceRangeText", "3.5^5.0")
                .containsEntry("ResultUnit", "mmol/L");
        // A numeric value with an unexpected component is not silently cut.
        assertThatThrownBy(
                        () ->
                                new CdsExportNativeLaboratoryMapper()
                                        .map(
                                                source(message().replace("||5.4|", "||5.4^9|")),
                                                ZoneId.of("America/Toronto")))
                .hasMessage("CDS_EXPORT_NATIVE_LAB_VALUE_COMPONENTS_REQUIRES_REVIEW");
    }

    @Test
    void shouldDecodeHl7FormattingEscapes_asCarlosDisplaysThem() throws Exception {
        // Review LAB-03.
        var source =
                source(
                        message()
                                .replace(
                                        "Exact café \\F\\ details 😀",
                                        "line1\\.br\\line2 \\H\\bold\\N\\ \\X4869\\ \\.sp2\\end"));
        var value =
                new CdsExportNativeLaboratoryMapper()
                        .map(source, ZoneId.of("America/Toronto"))
                        .getFirst();
        assertThat(value.text()).containsEntry("NotesFromLab", "line1\nline2 bold Hi \n\nend");
        // An escaped escape is literal text, never a line break.
        var literal =
                new CdsExportNativeLaboratoryMapper()
                        .map(
                                source(message().replace("Exact café \\F\\ details 😀", "a\\E\\.br\\E\\b")),
                                ZoneId.of("America/Toronto"))
                        .getFirst();
        assertThat(literal.text()).containsEntry("NotesFromLab", "a\\.br\\b");
    }

    @Test
    void shouldJoinTheContinuationLinesOfANarrative_intoOneResult() throws Exception {
        // Review LAB-08: TX/FT repetitions are lines of one narrative, not separate results.
        var source =
                source(
                        message()
                                .replace(
                                        "|ST|COMMENT^Narrative^L||A\\T\\B|",
                                        "|TX|COMMENT^Narrative^L||First line~Second line|"));
        assertThat(source.parsed().observations())
                .extracting(CdsExportNativeLaboratoryParser.Observation::ordinal)
                .containsExactly("1:1:0", "1:2:0", "2:1:0", "2:1:1");
        var values = new CdsExportNativeLaboratoryMapper().map(source, ZoneId.of("America/Toronto"));
        assertThat(values.get(1).text()).containsEntry("ResultValue", "First line\nSecond line");
    }

    @Test
    void shouldNameTheTestAndUseTheStoredAccession_withoutCarlosProvenanceInLabText()
            throws Exception {
        // Reviews LAB-04 (#3959), LAB-06 and LAB-07.
        var source = source(message().replace("OBR|1||ACC-1|", "OBR|1||ORDER-77|"));
        var values = new CdsExportNativeLaboratoryMapper().map(source, ZoneId.of("America/Toronto"));
        assertThat(values.getFirst().text())
                .containsEntry("TestName", "Glucose")
                .containsEntry("AccessionNumber", "ACC-1");
        assertThat(values.getFirst().text().get("TestResultsInformationReportedByTheLab"))
                .contains("Ordered test: Metabolic panel", "Order number: ORDER-77")
                .doesNotContain("CARLOS", "Native HL7");
        var unnamed =
                new CdsExportNativeLaboratoryMapper()
                        .map(
                                source(message().replace("|2345-7^Glucose^LN|", "|2345-7^^LN|")),
                                ZoneId.of("America/Toronto"));
        assertThat(unnamed.getFirst().text())
                .containsEntry("TestName", "Metabolic panel")
                .doesNotContainKey("TestNameReportedByLab");
    }

    @Test
    void shouldKeepTheStoredAccessionOnASecondOrdersResults_andNoteThatOrdersOwnNumber()
            throws Exception {
        // The installed-export oracle expects exactly this for its fixture's second OBR.
        var mapper = new CdsExportNativeLaboratoryMapper();
        var values = mapper.map(source(message()), ZoneId.of("America/Toronto"));
        assertThat(values.getFirst().text())
                .containsEntry("AccessionNumber", "ACC-1")
                .containsEntry(
                        "TestResultsInformationReportedByTheLab", "Ordered test: Metabolic panel");
        for (var value : values.subList(2, 4)) {
            assertThat(value.text())
                    .containsEntry("AccessionNumber", "ACC-1")
                    .containsEntry(
                            "TestResultsInformationReportedByTheLab",
                            "Ordered test: Other panel\nOrder number: ACC-2");
            assertThat(
                            new String(
                                    render(
                                            CdsCategory.LABORATORY_RESULTS,
                                            xml -> mapper.write(value, xml)),
                                    StandardCharsets.UTF_8))
                    .contains("resultNormalAbnormalFlagAsEnum>U<")
                    .doesNotContain("resultNormalAbnormalFlagAsPlainText");
        }
    }

    @Test
    void shouldWriteTheSupportingRecordInOneFixedKeyOrder_soAnUnchangedReportRepeatsItsBytes()
            throws Exception {
        var source = source(message());
        var mapper = new CdsExportNativeLaboratoryMapper();
        String first =
                new String(
                        render(CdsCategory.NEW_CATEGORY, xml -> mapper.writeSupporting(source, xml)),
                        StandardCharsets.UTF_8);
        assertThat(
                        new String(
                                render(
                                        CdsCategory.NEW_CATEGORY,
                                        xml -> mapper.writeSupporting(source, xml)),
                                StandardCharsets.UTF_8))
                .isEqualTo(first);
        String content =
                first.substring(
                        first.indexOf("Content>") + "Content>".length(),
                        first.indexOf("</", first.indexOf("Content>")));
        // Map.of iterates in a per-JVM salted order; this order must not depend on the JVM.
        assertThat(content)
                .startsWith("{\"version\":1,\"rows\":[{\"table\":\"hl7TextMessage\",\"fields\":{");
        var rows =
                com.fasterxml.jackson.databind.json.JsonMapper.builder()
                        .build()
                        .readTree(content)
                        .get("rows");
        assertThat(rows).hasSize(3);
        for (var row : rows) {
            assertThat(row.fieldNames()).toIterable().containsExactly("table", "fields");
            var fields = new ArrayList<String>();
            row.get("fields").fieldNames().forEachRemaining(fields::add);
            assertThat(fields).isNotEmpty().isSorted();
        }
    }

    @Test
    void shouldWriteAnHl7ChangeUpFlag_asPlainTextRatherThanTheCdsUnknown() throws Exception {
        // Review LAB-05: HL7 U is "significant change up"; the CDS enumeration's U is Unknown.
        var mapper = new CdsExportNativeLaboratoryMapper();
        var value =
                mapper.map(source(message().replace("|3.5-7.8|N|", "|3.5-7.8|U|")), ZoneId.of("America/Toronto"))
                        .getFirst();
        assertThat(value.enumFlag()).isFalse();
        assertThat(new String(render(CdsCategory.LABORATORY_RESULTS, xml -> mapper.write(value, xml)), StandardCharsets.UTF_8))
                .contains("resultNormalAbnormalFlagAsPlainText>U<");
    }

    @Test
    void shouldPreserveExplicitHl7NullResults_asAbsentValuesWithTheirActualStatus()
            throws Exception {
        var source = source(message().replace("||5.4|mmol/L", "||\"\"|mmol/L"));
        var value =
                new CdsExportNativeLaboratoryMapper()
                        .map(source, ZoneId.of("America/Toronto"))
                        .getFirst();
        assertThat(value.text())
                .doesNotContainKey("ResultValue")
                .containsEntry("TestResultStatus", "F");
        assertThat(
                        render(
                                CdsCategory.LABORATORY_RESULTS,
                                xml -> new CdsExportNativeLaboratoryMapper().write(value, xml)))
                .isNotEmpty();
    }

    @Test
    void shouldDecodeAnHl7EscapeExactlyOnce_andKeepAuthoredWhitespace() throws Exception {
        String literal = "  Literal \\E\\F\\E\\ text  ";
        var source =
                source(
                        message()
                                .replace(
                                        "|ST|COMMENT^Narrative^L||A\\T\\B|",
                                        "|ST|COMMENT^Narrative^L||" + literal + "|"));
        var value =
                new CdsExportNativeLaboratoryMapper()
                        .map(source, ZoneId.of("America/Toronto"))
                        .get(1);
        assertThat(value.text().get("ResultValue")).isEqualTo("  Literal \\F\\ text  ");
    }

    @Test
    void shouldPreserveLongTextResultsWithoutClipping_andKeepThemDistinctFromRawSourceMessages()
            throws Exception {
        String full = "Authored result text café 😀 ".repeat(30);
        var source =
                source(
                        message()
                                .replace(
                                        "|ST|COMMENT^Narrative^L||A\\T\\B|",
                                        "|TX|COMMENT^Narrative^L||" + full + "|"));
        var result =
                new CdsExportNativeLaboratoryMapper()
                        .map(source, ZoneId.of("America/Toronto"))
                        .get(1);
        assertThat(result.text()).doesNotContainKey("ResultValue");
        assertThat(result.text().get("TestResultsInformationReportedByTheLab"))
                .contains(full)
                .doesNotContain("MSH|");
    }

    @Test
    void shouldRefuseComplexUnsupportedValues_missingCollectionClocks_andUnattributableOrders() {
        assertThatThrownBy(
                        () ->
                                new CdsExportNativeLaboratoryMapper()
                                        .map(
                                                source(message().replace("|NM|", "|ED|")),
                                                ZoneId.of("America/Toronto")))
                .hasMessage("CDS_EXPORT_NATIVE_LAB_VALUE_TYPE_REQUIRES_REVIEW");
        assertThatThrownBy(() -> source(message().replace("OBR|2", "ORC|2")))
                .hasMessage("CDS_EXPORT_NATIVE_LAB_RESULT_WITHOUT_ORDER");
        assertThatThrownBy(() -> source(message() + "PID|2||ANOTHER\r"))
                .hasMessage("CDS_EXPORT_NATIVE_LAB_MULTIPLE_PATIENTS");
        assertThatThrownBy(
                        () ->
                                new CdsExportNativeLaboratoryParser()
                                        .parse("not valid base64!", () -> {}))
                .hasMessage("CDS_EXPORT_NATIVE_LAB_MESSAGE_REQUIRES_REVIEW");
    }

    @Test
    void
            shouldPreservePartialDateAndExplicitOffsetPrecision_andRefuseInventedMinuteSecondsOrDstOffsets()
                    throws Exception {
        assertThat(CdsExportNativeLaboratoryMapper.temporal("2020", null).lexical())
                .isEqualTo("2020");
        assertThat(CdsExportNativeLaboratoryMapper.temporal("202002", null).lexical())
                .isEqualTo("2020-02");
        assertThat(CdsExportNativeLaboratoryMapper.temporal("20200229", null).lexical())
                .isEqualTo("2020-02-29");
        assertThat(
                        CdsExportNativeLaboratoryMapper.temporal("20260709143500.123400-0400", null)
                                .lexical())
                .isEqualTo("2026-07-09T14:35:00.1234-04:00");
        assertThatThrownBy(
                        () ->
                                CdsExportNativeLaboratoryMapper.temporal(
                                        "202607091435", ZoneId.of("America/Toronto")))
                .hasMessage("CDS_EXPORT_NATIVE_LAB_CLOCK_PRECISION_REQUIRES_REVIEW");
        assertThatThrownBy(
                        () ->
                                CdsExportNativeLaboratoryMapper.temporal(
                                        "20261101013000", ZoneId.of("America/Toronto")))
                .hasMessage("CDS_EXPORT_NATIVE_HISTORY_CLOCK_AMBIGUOUS");
    }
}
