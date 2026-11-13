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

import static io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportNativeHistoryMapperUnitTest.render;
import static io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportNativeLaboratoryMapperUnitTest.message;
import static io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportNativeLaboratoryMapperUnitTest.source;
import static org.assertj.core.api.Assertions.*;

import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Ordinary inbound HL7 lab feeds (Excelleris, Epsilon, CML) that used to hold the patient:
 * structured numeric (SN) results, embedded documents (ED), hour or minute timestamps and orders
 * without results. An ORU message without its trigger is refused as CARLOS's own handlers refuse
 * it. The repository's own sample messages drive each case.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportNativeLaboratoryFeedUnitTest {
    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");

    /** The messages of a repository sample file, each as CARLOS stores it (CR separators). */
    static List<String> fixture(String resource) throws IOException {
        String text;
        try (InputStream input =
                CdsExportNativeLaboratoryFeedUnitTest.class.getResourceAsStream(
                        "/labs/" + resource)) {
            assertThat(input).as(resource).isNotNull();
            text = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }
        var messages = new ArrayList<String>();
        var matcher = Pattern.compile("MSH\\|.*?(?=\\]\\]>|\\z)", Pattern.DOTALL).matcher(text);
        while (matcher.find())
            messages.add(
                    String.join("\r", matcher.group().strip().split("\r\n|\r|\n")) + "\r");
        return messages;
    }

    /** The MSH segment and one patient's PID, OBR, OBX and NTE segments of a batch file. */
    static String patient(String batch, int patient) {
        var segments = batch.split("\r");
        var result = new StringBuilder(segments[0]).append('\r');
        int seen = 0;
        for (int i = 1; i < segments.length; i++) {
            if (segments[i].startsWith("PID")) seen++;
            if (seen == patient) result.append(segments[i]).append('\r');
        }
        return result.toString();
    }

    @Test
    void shouldExportAStructuredNumeric_asItsComparatorNumberSeparatorAndUnit() throws Exception {
        var mapper = new CdsExportNativeLaboratoryMapper();
        for (var entry :
                List.of(
                        List.of(">^60", ">60"),
                        List.of("^1^:^20", "1:20"),
                        List.of("^10^-^20", "10-20"),
                        List.of("^2^+", "2+"),
                        List.of("<=^0.5", "<=0.5"),
                        List.of("^4.2", "4.2"))) {
            var values =
                    mapper.map(
                            source(
                                    message()
                                            .replace("|NM|2345-7^Glucose^LN||5.4|", "|SN|33914-3^eGFR^LN||" + entry.getFirst() + "|")
                                            .replace("|mmol/L|3.5-7.8|", "|mL/min/1.73m2|>59|")),
                            TORONTO);
            assertThat(values.getFirst().text())
                    .as(entry.getFirst())
                    .containsEntry("ResultValue", entry.get(1))
                    .containsEntry("ResultUnit", "mL/min/1.73m2")
                    .containsEntry("ReferenceRangeText", ">59");
            assertThat(
                            new String(
                                    render(
                                            CdsCategory.LABORATORY_RESULTS,
                                            xml -> mapper.write(values.getFirst(), xml)),
                                    StandardCharsets.UTF_8))
                    .contains(entry.get(1).replace("<", "&lt;").replace(">", "&gt;"));
        }
    }

    @Test
    void shouldRenderTheExcellerisEscapedTitre_asOneToTwentyWithoutHoldingThePatient()
            throws Exception {
        String text = fixture("Excelleris-ON/excellerisON-Micro.xml").get(2);
        assertThat(text).contains("|SN|29953-7^Nuclear Antibody Titre||\\S\\1\\S\\:\\S\\20|");
        var source = source(text);
        var values = new CdsExportNativeLaboratoryMapper().map(source, TORONTO);
        assertThat(values)
                .filteredOn(value -> "29953-7".equals(value.text().get("LabTestCode")))
                .singleElement()
                .satisfies(
                        value ->
                                assertThat(value.text())
                                        .containsEntry("ResultValue", "1:20")
                                        .containsEntry("ReferenceRangeText", "< 1:40"));
        assertThat(values).hasSize(source.parsed().observations().size());
    }

    @Test
    void shouldExportTheEpsilonSpecificGravity_andItsMinutePrecisionRequisitionAsADate()
            throws Exception {
        String batch = fixture("HL7-Epsilon/medhealth-general.hl7").getFirst();
        // The sample leaves OBR-7 and OBX-14 empty, so it states no collection time at all; a
        // minute-precision OBX-14 like its OBR-6 exercises the collection path.
        String text =
                patient(batch, 3)
                        .replace("|F||||||\r", "|F|||201204030004|||\r");
        assertThat(text).contains("|SN|URINALYSIS^S.G^Specific Gravity||>=1.030|");
        var values = new CdsExportNativeLaboratoryMapper().map(source(text), TORONTO);
        assertThat(values).hasSize(8);
        assertThat(values.getLast().text())
                .containsEntry("ResultValue", ">=1.030")
                .containsEntry("ReferenceRangeText", "1.010-1.030");
        for (var value : values) {
            assertThat(value.dates().get("LabRequisitionDateTime"))
                    .isEqualTo(new CdsExportDateDto(CdsExportDateDto.Precision.DATE, "2012-04-03"));
            assertThat(value.dates().get("CollectionDateTime"))
                    .isEqualTo(new CdsExportDateDto(CdsExportDateDto.Precision.DATE, "2012-04-03"));
        }
    }

    @Test
    void shouldStillHoldAMalformedStructuredNumeric_withItsNamedCode() {
        for (String malformed : List.of("^abc", "^1^:^2^3^4", "approximately 5"))
            assertThatThrownBy(
                            () ->
                                    new CdsExportNativeLaboratoryMapper()
                                            .map(
                                                    source(
                                                            message()
                                                                    .replace(
                                                                            "|NM|2345-7^Glucose^LN||5.4|",
                                                                            "|SN|2345-7^Glucose^LN||" + malformed + "|")),
                                                    TORONTO))
                    .as(malformed)
                    .hasMessage("CDS_EXPORT_NATIVE_LAB_VALUE_COMPONENTS_REQUIRES_REVIEW");
    }

    @Test
    void shouldExportHourAndMinutePrecisionTimestamps_asTheDateWithoutInventingSeconds()
            throws Exception {
        assertThat(CdsExportNativeLaboratoryMapper.temporal("202607091435", TORONTO))
                .isEqualTo(new CdsExportDateDto(CdsExportDateDto.Precision.DATE, "2026-07-09"));
        assertThat(CdsExportNativeLaboratoryMapper.temporal("2026070914", TORONTO))
                .isEqualTo(new CdsExportDateDto(CdsExportDateDto.Precision.DATE, "2026-07-09"));
        assertThat(CdsExportNativeLaboratoryMapper.temporal("202607092355-0500", TORONTO))
                .isEqualTo(new CdsExportDateDto(CdsExportDateDto.Precision.DATE, "2026-07-09"));
        // A minute inside the autumn DST overlap has a date, so it is no longer ambiguous.
        assertThat(CdsExportNativeLaboratoryMapper.temporal("202611010130", TORONTO).lexical())
                .isEqualTo("2026-11-01");
        assertThatThrownBy(() -> CdsExportNativeLaboratoryMapper.temporal("202613091435", TORONTO))
                .hasMessage("CDS_EXPORT_NATIVE_LAB_DATE_REQUIRES_REVIEW");
        assertThatThrownBy(() -> CdsExportNativeLaboratoryMapper.temporal("202607091475", TORONTO))
                .hasMessage("CDS_EXPORT_NATIVE_LAB_DATE_REQUIRES_REVIEW");
        assertThatThrownBy(() -> CdsExportNativeLaboratoryMapper.temporal("202607091435+1500", TORONTO))
                .hasMessage("CDS_EXPORT_NATIVE_LAB_OFFSET_REQUIRES_REVIEW");
    }

    @Test
    void shouldCarryAnOrderWithoutResults_onlyInTheCompleteSourceMessage() throws Exception {
        String text = fixture("HL7-CML/HT05038.hl7").getFirst();
        assertThat(text).contains("OBR|3|HT05038||700^DOCUMENTATION FEE");
        var source = source(text);
        // Three results under the first two orders; the fee order adds none.
        assertThat(source.parsed().observations())
                .extracting(CdsExportNativeLaboratoryParser.Observation::ordinal)
                .containsExactly("1:1:0", "1:2:0", "2:1:0");
        assertThat(source.parsed().documents()).isEmpty();
        var values = new CdsExportNativeLaboratoryMapper().map(source, TORONTO);
        assertThat(values).hasSize(3);
        String supporting =
                new String(
                        render(
                                CdsCategory.NEW_CATEGORY,
                                xml -> new CdsExportNativeLaboratoryMapper().writeSupporting(source, xml)),
                        StandardCharsets.UTF_8);
        assertThat(supporting).contains("CARLOS.NativeLaboratory.v1");
        // A message whose orders all wait for results exports no result and is not held.
        String pending =
                "MSH|^~\\&|Reports|CML||28662|200507141114-0500||ORU^R01||1|2.3\r"
                        + "PID|1|||1849782642^LG^ON|SYNTHETIC^PATIENT||19410619|F\r"
                        + "ORC|NW|HT05038|||F\r"
                        + "OBR|1|HT05038||3670^MICROALB:CREAT-RU||20040713|20040713\r"
                        + "NTE|1|L|TEST PENDING\r";
        var waiting = source(pending);
        assertThat(waiting.parsed().observations()).isEmpty();
        assertThat(new CdsExportNativeLaboratoryMapper().map(waiting, TORONTO)).isEmpty();
        // A message without any order still is no laboratory report.
        assertThatThrownBy(
                        () ->
                                source(
                                        "MSH|^~\\&|Reports|CML||28662|200507141114-0500||ORU^R01||1|2.3\r"
                                                + "PID|1|||1849782642^LG^ON|SYNTHETIC^PATIENT||19410619|F\r"))
                .hasMessage("CDS_EXPORT_NATIVE_LAB_INCOMPLETE_MESSAGE");
    }

    @Test
    void shouldRefuseAnOruWithoutItsTrigger_asCarlosOwnLabHandlersDo() throws Exception {
        // The only such sample (HL7-CML/dummy.hl7) also lacks two encoding characters. HAPI
        // cannot tell a message structure from "ORU" alone, so CMLHandler could never have
        // stored it; the exporter refuses it the same way, before any type rule.
        String text =
                fixture("HL7-CML/HT05038.hl7").getFirst().replace("||ORU^R01||", "||ORU||");
        assertThatThrownBy(
                        () -> new io.github.carlos_emr.carlos.lab.ca.all.parsers.CMLHandler().init(text))
                .isInstanceOf(ca.uhn.hl7v2.HL7Exception.class);
        assertThatThrownBy(() -> source(text))
                .hasMessage("CDS_EXPORT_NATIVE_LAB_MESSAGE_REQUIRES_REVIEW");
        String dummy = fixture("HL7-CML/dummy.hl7").getFirst();
        assertThatThrownBy(
                        () -> new io.github.carlos_emr.carlos.lab.ca.all.parsers.CMLHandler().init(dummy))
                .isInstanceOf(ca.uhn.hl7v2.HL7Exception.class);
        assertThatThrownBy(() -> source(dummy))
                .hasMessage("CDS_EXPORT_NATIVE_LAB_MESSAGE_REQUIRES_REVIEW");
        assertThatThrownBy(() -> source(message().replace("|ORU^R01|", "|ORU^R30|")))
                .hasMessage("CDS_EXPORT_NATIVE_LAB_MESSAGE_TYPE_REQUIRES_REVIEW");
    }

    @Test
    void shouldExportAnExcellerisPdf_asALabReportLinkedByItsAccession() throws Exception {
        String text = fixture("Excelleris-ON/excellerisON-Path-PDF.xml").getFirst();
        var source = source(text);
        assertThat(source.parsed().documents()).hasSize(1);
        // Every other result of the report is still a laboratory result.
        assertThat(new CdsExportNativeLaboratoryMapper().map(source, TORONTO))
                .hasSize(source.parsed().observations().size())
                .isNotEmpty();
        var payload = CdsExportLaboratoryDocumentMapper.payload(source, 0);
        assertThat(payload.text()).isNull();
        assertThat(new String(payload.bytes(), 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(
                        CdsExportReportFileFormats.extension(
                                null, null, CdsExportLaboratoryDocumentMapper.prefix(payload.bytes())))
                .isEqualTo("pdf");
        var mapper = new CdsExportLaboratoryDocumentMapper();
        String path = "Reports/" + "a".repeat(64) + ".pdf";
        var result =
                mapper.map(
                        source,
                        0,
                        payload,
                        path,
                        "pdf",
                        TORONTO,
                        CdsExportInboxReviewMapper.Reviews.NONE);
        assertThat(result.text())
                .containsEntry("Format", "Binary")
                .containsEntry("FileExtensionAndVersion", "pdf")
                .containsEntry("FilePath", path)
                .containsEntry("Class", "Lab Report")
                .containsEntry("SourceFacility", "Native Lab")
                .doesNotContainKey("TextContent");
        assertThat(result.text().get("Notes"))
                .startsWith("[CARLOS embedded laboratory document]\nAccession number: ACC-1\n")
                .contains("\"accessionNumber\":\"ACC-1\"")
                .doesNotContain("JVBERi0");
        String xml =
                new String(
                        render(CdsCategory.REPORTS, body -> mapper.write(result, body)),
                        StandardCharsets.UTF_8);
        assertThat(xml).contains("Lab Report").contains(path);
    }

    @Test
    void shouldExportAStandardEdValue_andATextPayload_asReportsWithoutHoldingThePatient()
            throws Exception {
        byte[] pdf = "%PDF-1.4\n% synthetic\n".getBytes(StandardCharsets.US_ASCII);
        String encoded = java.util.Base64.getEncoder().encodeToString(pdf);
        String standard =
                message()
                        .replace(
                                "|NM|2345-7^Glucose^LN||5.4|",
                                "|ED|PDF^Pathology report^L||^TEXT^PDF^Base64^" + encoded + "|");
        var source = source(standard);
        assertThat(source.parsed().documents())
                .extracting(CdsExportNativeLaboratoryParser.Observation::ordinal)
                .containsExactly("1:1:0");
        assertThat(source.parsed().observations())
                .extracting(CdsExportNativeLaboratoryParser.Observation::ordinal)
                .containsExactly("1:2:0", "2:1:0", "2:1:1");
        // The result mapper never sees the document, so it no longer holds the patient.
        assertThat(new CdsExportNativeLaboratoryMapper().map(source, TORONTO)).hasSize(3);
        var payload = CdsExportLaboratoryDocumentMapper.payload(source, 0);
        assertThat(payload.bytes()).isEqualTo(pdf);
        assertThat(payload.encoding()).isEqualTo("Base64");
        var mapper = new CdsExportLaboratoryDocumentMapper();
        var binary =
                mapper.map(
                        source,
                        0,
                        payload,
                        "Reports/" + "b".repeat(64) + ".pdf",
                        "pdf",
                        TORONTO,
                        CdsExportInboxReviewMapper.Reviews.NONE);
        assertThat(binary.text()).containsEntry("SubClass", "Pathology report");
        assertThat(binary.dates().get("EventDateTime").lexical())
                .isEqualTo("2026-07-09T14:35:00-04:00");
        assertThat(binary.text().get("Notes")).contains("Exact café | details 😀");
        // ED.4 "A" is text, and a declared encoding the payload does not satisfy stays text.
        for (String value : List.of("^TEXT^^A^Line one\\.br\\Line two", "^TEXT^PDF^Base64^not base64!")) {
            var text =
                    source(
                            message()
                                    .replace(
                                            "|NM|2345-7^Glucose^LN||5.4|",
                                            "|ED|NOTE^Report text^L||" + value + "|"));
            var decoded = CdsExportLaboratoryDocumentMapper.payload(text, 0);
            assertThat(decoded.bytes()).as(value).isNull();
            var written =
                    mapper.map(
                            text, 0, decoded, null, null, TORONTO, CdsExportInboxReviewMapper.Reviews.NONE);
            assertThat(written.text())
                    .containsEntry("Format", "Text")
                    .containsEntry("FileExtensionAndVersion", "txt")
                    .containsKey("TextContent");
            assertThat(render(CdsCategory.REPORTS, body -> mapper.write(written, body))).isNotEmpty();
        }
        assertThat(
                        CdsExportLaboratoryDocumentMapper.payload(
                                        source(
                                                message()
                                                        .replace(
                                                                "|NM|2345-7^Glucose^LN||5.4|",
                                                                "|ED|NOTE^Report text^L||^TEXT^^A^Line one\\.br\\Line two|")),
                                        0)
                                .text())
                .isEqualTo("Line one\nLine two");
    }
}
