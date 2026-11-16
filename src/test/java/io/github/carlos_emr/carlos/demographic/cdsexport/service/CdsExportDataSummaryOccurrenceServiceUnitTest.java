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

import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportIdentityDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportReportReferenceDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Group;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Row;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Value;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryRegistry.Kind;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Each data summary is a schema-valid Reports entry and every captured field is assessed. */
@Tag("unit")
@Tag("cds-export")
@org.junit.jupiter.api.extension.ExtendWith(
        io.github.carlos_emr.carlos.test.mocks.MockInvocationReleaseExtension.class)
class CdsExportDataSummaryOccurrenceServiceUnitTest {
    private static final String KEY = "a".repeat(64);

    private static Row row(String table, String key, String... columns) {
        var values = new LinkedHashMap<String, Value>();
        for (int i = 0; i < columns.length; i += 2)
            values.put(columns[i], columns[i + 1] == null ? Value.sqlNull() : Value.text(columns[i + 1]));
        return new Row(table, key, values);
    }

    @Test
    void shouldWriteSchemaValidReports_inCdsOrderWithTheRevisionAndTrueDatePrecision() throws Exception {
        var revision =
                new Group(
                        Kind.ENCOUNTER_FORM,
                        "formRourke2020",
                        "12",
                        List.of(row("formRourke2020", CdsExportPatientTableSourceReader.key(12), "ID", "12")),
                        3,
                        80,
                        new CdsExportDateDto(CdsExportDateDto.Precision.DATE, "2024-01-01"),
                        false);
        var tickler =
                new Group(
                        Kind.TICKLER,
                        "tickler",
                        "7",
                        List.of(row("tickler", CdsExportPatientTableSourceReader.key(7), "tickler_no", "7")),
                        0,
                        0,
                        new CdsExportDateDto(CdsExportDateDto.Precision.DATE_TIME, "2024-03-10T09:30:00-04:00"),
                        false);
        var consent =
                new Group(
                        Kind.CONSENT,
                        "Consent",
                        "4",
                        List.of(row("Consent", CdsExportPatientTableSourceReader.key(4), "id", "4")),
                        0,
                        0,
                        null,
                        false);
        var patient = new LinkedHashMap<String, String>();
        CdsExportDemographicRowDto.COLUMNS.forEach(column -> patient.put(column, null));
        patient.putAll(
                Map.of(
                        "first_name", "Synthetic",
                        "last_name", "Fixture",
                        "year_of_birth", "1980",
                        "month_of_birth", "01",
                        "date_of_birth", "01",
                        "patient_status", "AC",
                        "sex", "U"));
        var demographics =
                new CdsExportDemographicMapper().map(new CdsExportDemographicRowDto(27, patient));
        var sections = new ArrayList<CdsExportXmlComposer.Section>();
        sections.add(
                new CdsExportXmlComposer.Section(
                        new CdsExportIdentityDto(KEY, CdsCategory.DEMOGRAPHICS, "b".repeat(64)),
                        writer -> new CdsExportDemographicXmlWriter().write(demographics, writer)));
        for (var group : List.of(revision, tickler, consent))
            sections.add(
                    new CdsExportXmlComposer.Section(
                            CdsExportDataSummaryOccurrenceService.identity(KEY, group),
                            writer ->
                                    CdsExportDataSummaryOccurrenceService.write(
                                            group, "Reports/" + group.table() + ".pdf", writer)));
        var output = new ByteArrayOutputStream();
        var selected = Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.REPORTS);
        new CdsExportXmlComposer()
                .compose(
                        output,
                        "d".repeat(64),
                        KEY,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        selected,
                        sections.iterator(),
                        ignored -> {},
                        1L << 20,
                        () -> {});
        String xml = output.toString(StandardCharsets.UTF_8);
        var references = new ArrayList<CdsExportReportReferenceDto>();
        var checked =
                new CdsExportXmlValidator()
                        .validate(
                                new ByteArrayInputStream(output.toByteArray()),
                                "27",
                                selected,
                                "2026.08.0-test",
                                CdsLogicalProfile.ONTARIO_MD_5_2,
                                references::add,
                                () -> {});
        assertThat(checked.occurrences().get(CdsCategory.REPORTS)).isEqualTo(3L);
        assertThat(checked.binaryReports()).isEqualTo(3);
        assertThat(references)
                .extracting(CdsExportReportReferenceDto::relativePath)
                .containsExactly("Reports/formRourke2020.pdf", "Reports/tickler.pdf", "Reports/Consent.pdf");
        String first = xml.substring(xml.indexOf("<Reports>"), xml.indexOf("</Reports>"));
        assertThat(first)
                .containsSubsequence(
                        "<Media>Download</Media>",
                        "<Format>Binary</Format>",
                        "<FileExtensionAndVersion>pdf</FileExtensionAndVersion>",
                        "<FilePath>Reports/formRourke2020.pdf</FilePath>",
                        "<Class>Medical Records Report</Class>",
                        "<SubClass>formRourke2020 revision 3 of 80</SubClass>",
                        "<EventDateTime>",
                        "FullDate>2024-01-01</",
                        "</EventDateTime>",
                        "<Notes>CARLOS data summary:");
        assertThat(xml).contains("FullDateTime>2024-03-10T09:30:00-04:00</");
    }

    @Test
    void shouldAssessAWithheldEmailCredential_asOperationalNotMapped() throws Exception {
        var values = new LinkedHashMap<String, Value>();
        values.put("subject", Value.text("Your results"));
        values.put("password", Value.text("S3cret-Pa55"));
        values.put("passwordClue", Value.sqlNull());
        var row = new Row("emailLog", CdsExportPatientTableSourceReader.key(5), values);
        var group = new Group(Kind.EMAIL, "emailLog", "5", List.of(row), 1, 1, null, false);

        var assessments = CdsExportDataSummaryOccurrenceService.assessments(group);

        assertThat(assessments.values())
                .extracting(
                        CdsExportFieldAssessmentDto::presence,
                        CdsExportFieldAssessmentDto::outcome,
                        CdsExportFieldAssessmentDto::rule)
                .containsExactlyInAnyOrder(
                        org.assertj.core.groups.Tuple.tuple(
                                CdsExportFieldAssessmentDto.Presence.VALUE,
                                CdsExportFieldAssessmentDto.Outcome.MAPPED,
                                "CARLOS_DATA_SUMMARY_EXACT_VALUE"),
                        org.assertj.core.groups.Tuple.tuple(
                                CdsExportFieldAssessmentDto.Presence.VALUE,
                                CdsExportFieldAssessmentDto.Outcome.OPERATIONAL,
                                "CARLOS_DATA_SUMMARY_CREDENTIAL_WITHHELD"),
                        org.assertj.core.groups.Tuple.tuple(
                                CdsExportFieldAssessmentDto.Presence.SQL_NULL,
                                CdsExportFieldAssessmentDto.Outcome.MAPPED,
                                "CARLOS_DATA_SUMMARY_EXACT_VALUE"));
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(
                        List.of(row.assessed()), assessments, () -> {});
    }

    @Test
    void shouldAssessEveryCapturedFieldOfTheRecord_asMappedWithItsPresence() throws Exception {
        var values = new LinkedHashMap<String, Value>();
        values.put("message", Value.text("Call back"));
        values.put("priority", Value.sqlNull());
        values.put("status", Value.text(""));
        values.put("tickler_no", Value.text("7"));
        var group =
                new Group(
                        Kind.TICKLER,
                        "tickler",
                        "7",
                        List.of(
                                new Row("tickler", CdsExportPatientTableSourceReader.key(7), values),
                                row("tickler_comments", CdsExportPatientTableSourceReader.key(1), "id", "1", "tickler_no", "7")),
                        0,
                        0,
                        null,
                        false);
        var assessments = CdsExportDataSummaryOccurrenceService.assessments(group);
        assertThat(assessments).hasSize(6);
        assertThat(assessments.values())
                .allSatisfy(
                        assessment -> {
                            assertThat(assessment.outcome()).isEqualTo(CdsExportFieldAssessmentDto.Outcome.MAPPED);
                            assertThat(assessment.destination()).isEqualTo("Reports/FilePath");
                        });
        var result =
                new CdsExportNativeFieldAssessmentValidator()
                        .validate(group.rows().stream().map(Row::assessed).toList(), assessments, () -> {});
        assertThat(result.representable()).isTrue();
        assertThat(result.sqlNulls()).isEqualTo(1);
        assertThat(result.empty()).isEqualTo(1);
        assertThat(result.mapped()).isEqualTo(6);
    }

    private static final CdsExportDataSummaryHtmlComposer.Patient SUMMARY_PATIENT =
            CdsExportDataSummaryHtmlComposer.Patient.of(
                    Map.of("demographic_no", "27", "last_name", "FAKE-Fixture", "first_name", "Synthetic"));

    private static Group tickler(String message) {
        var values = new LinkedHashMap<String, Value>();
        values.put("message", Value.text(message));
        values.put("priority", Value.sqlNull());
        values.put("tickler_no", Value.text("7"));
        return new Group(
                Kind.TICKLER,
                "tickler",
                "7",
                List.of(new Row("tickler", CdsExportPatientTableSourceReader.key(7), values)),
                0,
                0,
                new CdsExportDateDto(CdsExportDateDto.Precision.DATE, "2024-03-10"),
                false);
    }

    @Test
    void shouldExportTheSummaryAsHtml_whenTheRenderingIsNoPdf() throws Exception {
        // Neither the kind's rendering nor the data summary gives a loadable PDF: the summary is
        // exported as HTML, never held.
        CdsExportDataSummaryOccurrenceService.Renderer broken =
                (group, patient) -> "%PDF-1.7 truncated".getBytes(StandardCharsets.US_ASCII);
        var service =
                new CdsExportDataSummaryOccurrenceService(
                        org.mockito.Mockito.mock(CdsExportNativeCaptureService.class),
                        org.mockito.Mockito.mock(CdsExportStagingFactory.class),
                        broken,
                        Map.of(Kind.TICKLER, broken));

        var rendered = service.render(Kind.TICKLER, tickler("Call back"), SUMMARY_PATIENT);

        assertThat(rendered.pdf()).isNull();
        assertThat(rendered.notice()).isEqualTo(CdsExportDataSummaryPdfService.HTML_NOTICE);
        assertThat(rendered.html()).contains("Call back");
        assertThat(rendered.record()).contains("\"text\":\"Call back\"");
    }

    @Test
    void shouldWriteTheHtmlSummaryAndItsRecord_asTwoSchemaValidTextReports() throws Exception {
        var group = tickler("Recall in 6 months — café, 語");
        var rendered = CdsExportDataSummaryPdfService.html(group, SUMMARY_PATIENT);
        var patient = new LinkedHashMap<String, String>();
        CdsExportDemographicRowDto.COLUMNS.forEach(column -> patient.put(column, null));
        patient.putAll(
                Map.of(
                        "first_name", "Synthetic",
                        "last_name", "Fixture",
                        "year_of_birth", "1980",
                        "month_of_birth", "01",
                        "date_of_birth", "01",
                        "patient_status", "AC",
                        "sex", "U"));
        var demographics =
                new CdsExportDemographicMapper().map(new CdsExportDemographicRowDto(27, patient));
        String notes = CdsExportDataSummaryOccurrenceService.NOTES + " " + rendered.notice();
        var summary = CdsExportDataSummaryOccurrenceService.identity(KEY, group);
        var record = CdsExportDataSummaryOccurrenceService.recordIdentity(KEY, group);
        assertThat(record).isNotEqualTo(summary);
        var sections =
                List.of(
                        new CdsExportXmlComposer.Section(
                                new CdsExportIdentityDto(KEY, CdsCategory.DEMOGRAPHICS, "b".repeat(64)),
                                writer -> new CdsExportDemographicXmlWriter().write(demographics, writer)),
                        new CdsExportXmlComposer.Section(
                                summary,
                                writer ->
                                        CdsExportDataSummaryOccurrenceService.writeHtml(
                                                group, rendered.html(), notes, writer)),
                        new CdsExportXmlComposer.Section(
                                record,
                                writer ->
                                        CdsExportDataSummaryOccurrenceService.writeRecord(
                                                group, rendered.record(), writer)));
        var output = new ByteArrayOutputStream();
        var selected = Set.of(CdsCategory.DEMOGRAPHICS, CdsCategory.REPORTS);
        new CdsExportXmlComposer()
                .compose(
                        output,
                        "d".repeat(64),
                        KEY,
                        "2026.08.0-test",
                        CdsLogicalProfile.ONTARIO_MD_5_2,
                        selected,
                        sections.iterator(),
                        ignored -> {},
                        1L << 22,
                        () -> {});
        var references = new ArrayList<CdsExportReportReferenceDto>();
        var checked =
                new CdsExportXmlValidator()
                        .validate(
                                new ByteArrayInputStream(output.toByteArray()),
                                "27",
                                selected,
                                "2026.08.0-test",
                                CdsLogicalProfile.ONTARIO_MD_5_2,
                                references::add,
                                () -> {});
        assertThat(checked.occurrences().get(CdsCategory.REPORTS)).isEqualTo(2L);
        assertThat(checked.textReports()).isEqualTo(2);
        assertThat(checked.binaryReports()).isZero();
        assertThat(references).isEmpty();
        String xml = output.toString(StandardCharsets.UTF_8);
        assertThat(xml)
                .containsSubsequence(
                        "<Format>Text</Format>",
                        "<FileExtensionAndVersion>html</FileExtensionAndVersion>",
                        "&lt;!DOCTYPE html&gt;",
                        "<SubClass>" + group.subClass() + "</SubClass>",
                        "could not make this record",
                        "<Format>Text</Format>",
                        "<FileExtensionAndVersion>json</FileExtensionAndVersion>",
                        "CARLOS.DataSummary.Record.v1",
                        "<SubClass>" + CdsExportDataSummaryPdfService.RECORD_SUBCLASS + "</SubClass>");
        assertThat(xml).contains("Recall in 6 months — café, 語");
        // Every field of the record is accounted by the HTML summary's receipt.
        var html =
                CdsExportDataSummaryOccurrenceService.htmlAssessments(
                        CdsExportDataSummaryOccurrenceService.assessments(group));
        assertThat(html.values())
                .allSatisfy(
                        assessment -> {
                            assertThat(assessment.destination()).isEqualTo("Reports/Content");
                            assertThat(assessment.rule())
                                    .isEqualTo(CdsExportDataSummaryOccurrenceService.HTML_RULE);
                        });
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(
                        group.rows().stream().map(Row::assessed).toList(), html, () -> {});
    }
}
