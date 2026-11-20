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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeFieldKeyDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsInputLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsLaboratoryNativeDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsRecordDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsClinicalNormalizationService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsFieldParser;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsLaboratoryImportService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsNativePreparationService;
import io.github.carlos_emr.carlos.demographic.cdsimport.service.CdsXmlParser;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A stored HL7 message that names more than one patient exports only this patient's part (owner
 * decision 2026-11-14): the MSH segment and the PID groups whose identity matches the chart, as
 * the elements and as the carried message; when no group matches, nothing of it. Another
 * patient's results never leave the clinic. A message with one PID is read whole, as before.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportNativeLaboratoryMultiPatientUnitTest {
    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");
    private static final String HEADER =
            "MSH|^~\\&|LABAPP|Native Lab|CARLOS|Clinic|20260710123000-0400||ORU^R01|batch-1|P|2.3\r";
    /** The first patient: a health number in PID-2 and a laboratory number in PID-3. */
    private static final String FIRST =
            "PID|1|1111111111|EXTERNAL-A^^^LAB^MR||FAKE-Other^Alice||19700101|F\r"
                    + "OBR|1||ACC-A|NA^Sodium panel^L|||20260709100000-0400\r"
                    + "OBX|1|NM|2951-2^Sodium^LN||140|mmol/L|135-145|N|||F|||20260709100000-0400\r";
    /** The second patient: the health number with its version code, as Excelleris sends it. */
    private static final String SECOND =
            "PID|2|2222222222AB|EXTERNAL-B^^^LAB^MR||FAKE-Synthetic^Bea||19800102|F\r"
                    + "OBR|1||ACC-B|K^Potassium panel^L|||20260709110000-0400\r"
                    + "OBX|1|NM|2823-3^Potassium^LN||4.1|mmol/L|3.5-5.0|N|||F|||20260709110000-0400\r";

    private static final CdsExportNativeLaboratoryParser.Chart ALICE =
            new CdsExportNativeLaboratoryParser.Chart(
                    "1111111111", "FAKE-Other", "Alice", "1970", "01", "01");
    private static final CdsExportNativeLaboratoryParser.Chart BEA =
            new CdsExportNativeLaboratoryParser.Chart(
                    "2222222222", "FAKE-Synthetic", "Bea", "1980", "01", "02");
    private static final CdsExportNativeLaboratoryParser.Chart CAROL =
            new CdsExportNativeLaboratoryParser.Chart(
                    "3333333333", "FAKE-Third", "Carol", "1990", "03", "03");

    private static CdsExportCapturedTextRowDto row(Plan plan, String id, Map<String, String> values) {
        var fields = new LinkedHashMap<String, String>();
        plan.columns().forEach(column -> fields.put(column, null));
        fields.putAll(values);
        return new CdsExportCapturedTextRowDto(plan.table(), id, fields);
    }

    /**
     * The report as the capture holds it, read for {@code chart}. The upload stored the first
     * patient's identity and the first order's accession and date ({@code MessageUploader} reads
     * them through the handler from the first PID).
     */
    private static CdsExportLaboratoryOccurrenceService.NativeSource source(
            String text, CdsExportNativeLaboratoryParser.Chart chart) throws Exception {
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
                                "PATHL7",
                                "created",
                                "2026-07-10 12:31:00"));
        var information =
                row(
                        Plan.LABORATORY_INFORMATION,
                        "92",
                        Map.of(
                                "id",
                                "92",
                                "lab_no",
                                "91",
                                "last_name",
                                "FAKE-Other",
                                "first_name",
                                "Alice",
                                "health_no",
                                "1111111111",
                                "sending_facility",
                                "Native Lab",
                                "accessionNum",
                                "ACC-A",
                                "obr_date",
                                "2026-07-09 10:00:00"));
        var routing =
                row(
                        Plan.LABORATORY_ROUTING,
                        "93",
                        Map.of("id", "93", "lab_no", "91", "demographic_no", "27", "lab_type", "HL7"));
        return new CdsExportLaboratoryOccurrenceService.NativeSource(
                "91",
                message,
                information,
                routing,
                new CdsExportNativeLaboratoryParser()
                        .parse(message.fields().get("message"), chart, () -> {}));
    }

    /** The CARLOS.Laboratory.v1 record of the report's supporting NewCategory entry. */
    private static JsonNode supporting(CdsExportLaboratoryOccurrenceService.NativeSource source)
            throws Exception {
        byte[] xml =
                CdsExportNativeHistoryMapperUnitTest.render(
                        CdsCategory.NEW_CATEGORY,
                        writer -> new CdsExportNativeLaboratoryMapper().writeSupporting(source, writer));
        var factory = javax.xml.parsers.DocumentBuilderFactory.newInstance();
        factory.setNamespaceAware(true);
        factory.setFeature(javax.xml.XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        var contents =
                factory.newDocumentBuilder()
                        .parse(new ByteArrayInputStream(xml))
                        .getElementsByTagNameNS("cds_dt", "Content");
        assertThat(contents.getLength()).isEqualTo(1);
        return new ObjectMapper().readTree(contents.item(0).getTextContent());
    }

    private static String carriedMessage(JsonNode record) {
        for (var entry : record.path("rows"))
            if (entry.path("table").asText().equals("hl7TextMessage"))
                return new String(
                        Base64.getDecoder().decode(entry.path("fields").path("message").asText()),
                        StandardCharsets.UTF_8);
        throw new AssertionError("no message row");
    }

    private static JsonNode informationRow(JsonNode record) {
        for (var entry : record.path("rows"))
            if (entry.path("table").asText().equals("hl7TextInfo")) return entry.path("fields");
        throw new AssertionError("no information row");
    }

    private static CdsExportFieldAssessmentDto field(
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields,
            String table,
            String column) {
        return fields.entrySet().stream()
                .filter(entry -> entry.getKey().table().equals(table))
                .filter(entry -> entry.getKey().column().equals(column))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void shouldExportOnlyThisPatientsPart_whenThisPatientIsSecondInTheBatch() throws Exception {
        var source = source(HEADER + FIRST + SECOND, BEA);
        var selection = source.parsed().selection();

        assertThat(selection.part()).isEqualTo(CdsExportNativeLaboratoryParser.Part.THIS_PATIENT);
        assertThat(selection.groups()).isEqualTo(2);
        assertThat(selection.exported()).containsExactly(2);
        assertThat(selection.storedInformation()).isFalse();
        var values = new CdsExportNativeLaboratoryMapper().map(source, TORONTO);
        assertThat(values).hasSize(1);
        assertThat(values.getFirst().text())
                .containsEntry("TestName", "Potassium")
                .containsEntry("ResultValue", "4.1")
                // The stored accession is the first patient's order: this part's own OBR-3 is
                // written, and the laboratory comes from the MSH this part keeps.
                .containsEntry("AccessionNumber", "ACC-B")
                .containsEntry("LaboratoryName", "Native Lab");
        // The carried message is the MSH segment and this patient's group, byte for byte.
        var record = supporting(source);
        assertThat(carriedMessage(record)).isEqualTo(HEADER + SECOND);
        assertThat(record.toString())
                .doesNotContain("Sodium")
                .doesNotContain("FAKE-Other")
                .doesNotContain("1111111111")
                .doesNotContain("ACC-A");
        // The values the upload read from the first patient are left out; CARLOS's own stay.
        assertThat(informationRow(record).properties())
                .extracting(Map.Entry::getKey)
                .containsExactlyInAnyOrder("id", "lab_no");
        assertThat(record.path("part").path("rule").asText())
                .isEqualTo(CdsExportNativeLaboratoryMapper.THIS_PATIENTS_PART);
        assertThat(record.path("part").path("exported").toString()).isEqualTo("[2]");
        var fields = CdsExportNativeLaboratoryMapper.assessments(source);
        assertThat(field(fields, "hl7TextMessage", "message").rule())
                .isEqualTo(CdsExportNativeLaboratoryMapper.THIS_PATIENTS_PART);
        assertThat(field(fields, "hl7TextInfo", "last_name").outcome())
                .isEqualTo(CdsExportFieldAssessmentDto.Outcome.OPERATIONAL);
        assertThat(field(fields, "hl7TextInfo", "accessionNum").rule())
                .isEqualTo(CdsExportNativeLaboratoryMapper.ANOTHER_PATIENTS_PART);
        assertThat(field(fields, "hl7TextInfo", "lab_no").outcome())
                .isEqualTo(CdsExportFieldAssessmentDto.Outcome.MAPPED);
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(source.rows(), fields, () -> {});
    }

    @Test
    void shouldExportOnlyThisPatientsPart_whenThisPatientIsFirstInTheBatch() throws Exception {
        var source = source(HEADER + FIRST + SECOND, ALICE);
        var selection = source.parsed().selection();

        assertThat(selection.exported()).containsExactly(1);
        assertThat(selection.storedInformation()).isTrue();
        var values = new CdsExportNativeLaboratoryMapper().map(source, TORONTO);
        assertThat(values)
                .singleElement()
                .satisfies(
                        value ->
                                assertThat(value.text())
                                        .containsEntry("TestName", "Sodium")
                                        .containsEntry("AccessionNumber", "ACC-A"));
        var record = supporting(source);
        assertThat(carriedMessage(record)).isEqualTo(HEADER + FIRST);
        assertThat(record.toString())
                .doesNotContain("Potassium")
                .doesNotContain("FAKE-Synthetic")
                .doesNotContain("2222222222");
        // The stored values are this patient's: carried whole.
        assertThat(informationRow(record).path("last_name").asText()).isEqualTo("FAKE-Other");
    }

    @Test
    void shouldMatchByNamesAndBirthDate_onlyWhenTheGroupStatesNoHealthNumber() throws Exception {
        // Groups with no identifier: names compared ignoring letter case and trailing spaces,
        // the birth date exactly, as the HL7 matcher's collation compares them.
        String anonymous =
                HEADER
                        + FIRST.replace("PID|1|1111111111|EXTERNAL-A^^^LAB^MR|", "PID|1|||")
                        + SECOND.replace(
                                "PID|2|2222222222AB|EXTERNAL-B^^^LAB^MR||FAKE-Synthetic^Bea|",
                                "PID|2||||fake-synthetic ^BEA|");
        assertThat(source(anonymous, BEA).parsed().selection().exported()).containsExactly(2);
        // A birth date that differs is another person.
        var older =
                new CdsExportNativeLaboratoryParser.Chart(
                        "2222222222", "FAKE-Synthetic", "Bea", "1980", "01", "03");
        assertThat(source(anonymous, older).foreign()).isTrue();
        // A group that states a health number is matched by it alone, never by its name.
        var renamed =
                new CdsExportNativeLaboratoryParser.Chart(
                        "4444444444", "FAKE-Synthetic", "Bea", "1980", "01", "02");
        assertThat(source(HEADER + FIRST + SECOND, renamed).foreign()).isTrue();
        // An accent the database collation would ignore is not ignored here.
        var accented =
                new CdsExportNativeLaboratoryParser.Chart(
                        null, "FAKE-Synthétic", "Bea", "1980", "01", "02");
        assertThat(source(anonymous, accented).foreign()).isTrue();
    }

    @Test
    void shouldExportNothingAndNameTheReport_whenNoGroupIsThisPatients() throws Exception {
        var source = source(HEADER + FIRST + SECOND, CAROL);

        assertThat(source.foreign()).isTrue();
        assertThat(source.parsed().selection().part())
                .isEqualTo(CdsExportNativeLaboratoryParser.Part.NONE);
        assertThat(source.parsed().observations()).isEmpty();
        assertThat(source.parsed().documents()).isEmpty();
        // Every field of the report is accounted, and no record is written.
        var fields = CdsExportNativeLaboratoryMapper.foreign(source, List.of());
        assertThat(fields.values())
                .allSatisfy(
                        assessment -> {
                            assertThat(assessment.outcome())
                                    .isEqualTo(CdsExportFieldAssessmentDto.Outcome.OPERATIONAL);
                            assertThat(assessment.rule())
                                    .isEqualTo(CdsExportNativeLaboratoryMapper.OTHER_PATIENTS_ONLY);
                        });
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(source.rows(), fields, () -> {});
        // The ReadMe names the report as CARLOS lists it in the chart.
        assertThat(CdsExportPatientInventoryService.otherPatientsReport(source))
                .isEqualTo(
                        "Laboratory report naming several patients, none of them this patient"
                                + " (2026-07-09, Native Lab, accession ACC-A)");
    }

    @Test
    void shouldReadAMessageWithOnePatientWhole_asItAlwaysWas() throws Exception {
        String single = HEADER + SECOND;
        var source = source(single, CdsExportNativeLaboratoryParser.Chart.UNKNOWN);

        assertThat(source.parsed().selection())
                .isEqualTo(CdsExportNativeLaboratoryParser.Selection.WHOLE);
        assertThat(source.parsed().observations()).hasSize(1);
        assertThat(carriedMessage(supporting(source))).isEqualTo(single);
        assertThat(supporting(source).has("part")).isFalse();
    }

    @Test
    void shouldStoreOnlyThisPatientsResult_whenCarlosImportsTheExportedPart() throws Exception {
        var source = source(HEADER + FIRST + SECOND, BEA);
        var mapper = new CdsExportNativeLaboratoryMapper();
        var value = mapper.map(source, TORONTO).getFirst();
        byte[] xml =
                CdsExportNativeHistoryMapperUnitTest.render(
                        CdsCategory.LABORATORY_RESULTS, writer -> mapper.write(value, writer));
        Path root =
                Files.createTempDirectory(
                        Path.of(
                                        System.getProperty(
                                                "cds.export.test.documentRoot",
                                                System.getProperty("user.home")))
                                .toAbsolutePath(),
                        "owned-lab-import-",
                        PosixFilePermissions.asFileAttribute(
                                PosixFilePermissions.fromString("rwx------")));
        try {
            var file = Files.write(root.resolve("patient.xml"), xml);
            var records = new ArrayList<CdsRecordDto>();
            new CdsXmlParser()
                    .parse(
                            file,
                            root.resolve("parsed"),
                            CdsInputLimitsDto.defaults(),
                            () -> false,
                            records::add);
            var laboratory = records.get(1);
            var fields = new CdsFieldParser();
            var preparation = org.mockito.Mockito.mock(CdsNativePreparationService.class);
            var service =
                    new CdsLaboratoryImportService(
                            fields,
                            new CdsClinicalNormalizationService(fields),
                            preparation,
                            null,
                            null,
                            null,
                            null,
                            null,
                            null);
            service.validate(laboratory, null, null, null);
            service.prepare("run", 1L, "unit", laboratory, null, null, null);
            var prepared = org.mockito.ArgumentCaptor.forClass(Object.class);
            org.mockito.Mockito.verify(preparation)
                    .store(
                            org.mockito.ArgumentMatchers.eq("run"),
                            org.mockito.ArgumentMatchers.eq(1L),
                            org.mockito.ArgumentMatchers.eq("unit"),
                            org.mockito.ArgumentMatchers.eq(laboratory.effectiveSha256()),
                            org.mockito.ArgumentMatchers.isNull(),
                            prepared.capture());
            var stored = ((CdsLaboratoryNativeDto) prepared.getValue()).values();
            assertThat(stored)
                    .containsEntry("TestName", "Potassium")
                    .containsEntry("AccessionNumber", "ACC-B");
            assertThat(stored.toString()).doesNotContain("Sodium").doesNotContain("ACC-A");
        } finally {
            try (var paths = Files.walk(root)) {
                for (var path : paths.sorted(Comparator.reverseOrder()).toList()) Files.delete(path);
            }
        }
    }
}
