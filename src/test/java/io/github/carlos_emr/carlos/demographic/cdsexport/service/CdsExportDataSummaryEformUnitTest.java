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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.commn.dto.CdsExportDemographicRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsLogicalProfile;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedFieldDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportIdentityDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportReportReferenceDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportXmlLimitsDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryOccurrenceService.Hidden;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryOccurrenceService.Withheld;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Group;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Row;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Value;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryRegistry.Kind;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportEformRenderService.Reason;
import io.github.carlos_emr.carlos.demographic.cdsexport.validator.CdsExportXmlValidator;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * eForms (plan item 2.5): the chart's eForms keep their lossless data summary and gain the form as
 * CARLOS displays it; an unavailable render becomes a note, never a hold; removed and
 * patient-independent eForms are accounted and never exported.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportDataSummaryEformUnitTest {
    private static final String KEY = "a".repeat(64);
    private static final CdsExportDateDto WHEN =
            new CdsExportDateDto(CdsExportDateDto.Precision.DATE_TIME, "2024-05-06T13:00:00-04:00");

    private static Group eform(String fdid, String status, String independent) {
        var values = new LinkedHashMap<String, Value>();
        values.put("fdid", Value.text(fdid));
        values.put("fid", Value.text("1"));
        values.put("form_name", Value.text("Rich Text Letter"));
        values.put("subject", Value.text("Referral to cardiology"));
        values.put("demographic_no", Value.text("27"));
        values.put("status", Value.text(status));
        values.put("form_date", Value.text("2024-05-06"));
        values.put("form_time", Value.text("13:00:00"));
        values.put("form_provider", Value.text("999998"));
        values.put("form_data", Value.text("<html><input name=\"weight\" value=\"70\"></html>"));
        values.put("patient_independent", Value.text(independent));
        values.put("showLatestFormOnly", Value.text("0"));
        values.put("roleType", Value.sqlNull());
        var child = new LinkedHashMap<String, Value>();
        child.put("id", Value.text("3"));
        child.put("fdid", Value.text(fdid));
        child.put("var_name", Value.text("weight"));
        child.put("var_value", Value.text("70"));
        return new Group(
                Kind.EFORM,
                "eform_data",
                fdid,
                List.of(
                        new Row("eform_data", CdsExportPatientTableSourceReader.key(Long.parseLong(fdid)), values),
                        new Row("eform_values", CdsExportPatientTableSourceReader.key(3), child)),
                0,
                0,
                WHEN,
                false);
    }

    private static CdsExportEformRenderings.Rendering rendering(
            String fdid, Reason reason, String first, String last) {
        String key = CdsExportPatientTableSourceReader.key(Long.parseLong(fdid));
        var fields = new TreeMap<String, String>();
        fields.put("author_first_name", first);
        fields.put("author_last_name", last);
        fields.put("fdid", fdid);
        fields.put("outcome", reason == null ? "RENDERED" : "UNAVAILABLE");
        fields.put("reason", reason == null ? "" : reason.name());
        fields.put("content", reason == null ? "sha256:" + "e".repeat(64) : "");
        return new CdsExportEformRenderings.Rendering(
                fdid,
                reason,
                first,
                last,
                reason == null
                        ? new CdsExportCapturedFieldDto(
                                CdsExportEformRenderingSourceReader.TABLE,
                                key,
                                "content",
                                CdsExportCapturedFieldDto.Kind.BINARY,
                                0,
                                1234,
                                "e".repeat(64))
                        : null,
                new CdsExportCapturedTextRowDto(CdsExportEformRenderingSourceReader.TABLE, key, fields));
    }

    @Test
    void shouldWithholdRemovedAndPatientIndependentEforms_withEveryCapturedFieldAccounted()
            throws Exception {
        assertThat(CdsExportDataSummaryOccurrenceService.withheld(eform("5", "1", "0"))).isNull();
        assertThat(CdsExportDataSummaryOccurrenceService.withheld(eform("6", "0", "0")))
                .isEqualTo(Withheld.REMOVED_EFORM);
        assertThat(CdsExportDataSummaryOccurrenceService.withheld(eform("7", "1", "1")))
                .isEqualTo(Withheld.PATIENT_INDEPENDENT_EFORM);
        assertThat(CdsExportDataSummaryOccurrenceService.withheld(eform("8", "0", "1")))
                .isEqualTo(Withheld.PATIENT_INDEPENDENT_EFORM);

        var removed = eform("6", "0", "0");
        var assessments =
                CdsExportDataSummaryOccurrenceService.withheldAssessments(
                        new Hidden(removed, Withheld.REMOVED_EFORM));

        // The eForm row and the values row that belongs to it: every field, none exported.
        assertThat(assessments).hasSize(17);
        assertThat(assessments.values())
                .allSatisfy(
                        assessment -> {
                            assertThat(assessment.outcome())
                                    .isEqualTo(CdsExportFieldAssessmentDto.Outcome.OPERATIONAL);
                            assertThat(assessment.rule()).isEqualTo("CARLOS_EFORM_REMOVED_NOT_EXPORTED");
                        });
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(
                        removed.rows().stream().map(Row::assessed).toList(), assessments, 1000, () -> {});
    }

    @Test
    void shouldWriteTheRenderedFormBesideItsDataSummary_asTwoSchemaValidReports() throws Exception {
        var group = eform("5", "1", "0");
        var rendering = rendering("5", null, "Ann", "Fixture");
        var entry = CdsExportDataSummaryOccurrenceService.rendered(group, rendering);
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(
                        List.of(group.root().assessed(), rendering.row()), entry.fields(), 1000, () -> {});
        assertThat(entry.subClass()).isEqualTo("Rich Text Letter");
        assertThat(entry.fields().values())
                .extracting(CdsExportFieldAssessmentDto::destination)
                .contains(
                        "Reports/FilePath",
                        "Reports/SubClass",
                        "Reports/Notes",
                        "Reports/EventDateTime",
                        "Reports/SourceAuthorPhysician",
                        "Reports/SourceAuthorPhysician/AuthorName");

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
        sections.add(
                new CdsExportXmlComposer.Section(
                        CdsExportDataSummaryOccurrenceService.identity(KEY, group),
                        writer ->
                                CdsExportDataSummaryOccurrenceService.write(
                                        group, "Reports/summary.pdf", writer)));
        sections.add(
                new CdsExportXmlComposer.Section(
                        CdsExportDataSummaryOccurrenceService.renderedIdentity(KEY, "5"),
                        writer ->
                                CdsExportDataSummaryOccurrenceService.writeRendered(
                                        group, entry, "Reports/rendered.pdf", writer)));
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
        var references = new ArrayList<CdsExportReportReferenceDto>();
        var checked =
                new CdsExportXmlValidator()
                        .validate(
                                new ByteArrayInputStream(output.toByteArray()),
                                "27",
                                selected,
                                "2026.08.0-test",
                                CdsLogicalProfile.ONTARIO_MD_5_2,
                                CdsExportXmlLimitsDto.defaults(),
                                references::add,
                                () -> {});

        assertThat(checked.occurrences().get(CdsCategory.REPORTS)).isEqualTo(2L);
        assertThat(checked.binaryReports()).isEqualTo(2);
        assertThat(CdsExportDataSummaryOccurrenceService.renderedIdentity(KEY, "5"))
                .isNotEqualTo(CdsExportDataSummaryOccurrenceService.identity(KEY, group));
        String xml = output.toString(StandardCharsets.UTF_8);
        String second = xml.substring(xml.lastIndexOf("<Reports>"), xml.lastIndexOf("</Reports>"));
        assertThat(second)
                .containsSubsequence(
                        "<Format>Binary</Format>",
                        "<FilePath>Reports/rendered.pdf</FilePath>",
                        "<Class>Medical Records Report</Class>",
                        "<SubClass>Rich Text Letter</SubClass>",
                        "FullDateTime>2024-05-06T13:00:00-04:00</",
                        "<SourceAuthorPhysician>",
                        "FirstName>Ann</",
                        "LastName>Fixture</",
                        "<Notes>CARLOS eForm:",
                        "Additional information: Referral to cardiology</Notes>");
    }

    @Test
    void shouldNoteAnUnavailableRenderOnTheDataSummary_withAFixedReasonAndNoHold() throws Exception {
        var group = eform("9", "1", "0");
        var rendering = rendering("9", Reason.INCOMPLETE, null, null);
        var assessments =
                new LinkedHashMap<>(CdsExportDataSummaryOccurrenceService.assessments(group));
        assessments.putAll(CdsExportDataSummaryOccurrenceService.unavailableAssessments(rendering));
        var rows = new ArrayList<CdsExportCapturedTextRowDto>();
        for (var row : group.rows()) rows.add(row.assessed());
        rows.add(rendering.row());

        new CdsExportNativeFieldAssessmentValidator().requireRepresentable(rows, assessments, 1000, () -> {});

        String note = CdsExportDataSummaryOccurrenceService.unavailableNote(Reason.INCOMPLETE);
        assertThat(note)
                .isEqualTo(
                        "The eForm as CARLOS displays it could not be rendered for this export (the"
                                + " renderer could not show all of the form's content, and an export"
                                + " never approves an incomplete form), so this data summary is its"
                                + " only Report.");
        String html =
                new CdsExportDataSummaryHtmlComposer()
                        .compose(
                                group.withNotice(note),
                                new CdsExportDataSummaryHtmlComposer.Patient(
                                        "27", "Fixture", "Synthetic", "1980-01-01", "U", "", ""));
        assertThat(html).contains("could not be rendered for this export");
        assertThat(
                        assessments.get(
                                new io.github.carlos_emr.carlos.demographic.cdsexport.dto
                                        .CdsExportNativeFieldKeyDto(
                                        CdsExportEformRenderingSourceReader.TABLE,
                                        CdsExportPatientTableSourceReader.key(9),
                                        "reason")))
                .extracting(CdsExportFieldAssessmentDto::destination)
                .isEqualTo("Reports/Notes");
    }

    @Test
    void shouldRefuseRenderOutcomes_thatDoNotMatchTheListedEformsExactly() throws Exception {
        var tables = Set.of("eform_data", CdsExportEformRenderingSourceReader.TABLE);
        var listed = List.of(eform("5", "1", "0"));

        CdsExportDataSummaryOccurrenceService.requireRenderingScope(
                listed, tables, Map.of("5", rendering("5", null, null, null)));
        assertThatThrownBy(
                        () ->
                                CdsExportDataSummaryOccurrenceService.requireRenderingScope(
                                        listed, tables, Map.of()))
                .hasMessage("CDS_EXPORT_EFORM_RENDERING_SCOPE_INVALID");
        assertThatThrownBy(
                        () ->
                                CdsExportDataSummaryOccurrenceService.requireRenderingScope(
                                        listed,
                                        tables,
                                        Map.of(
                                                "5", rendering("5", null, null, null),
                                                "6", rendering("6", Reason.CHANGED, null, null))))
                .hasMessage("CDS_EXPORT_EFORM_RENDERING_SCOPE_INVALID");
        // A job accepted before the renderings existed keeps the summaries alone.
        CdsExportDataSummaryOccurrenceService.requireRenderingScope(listed, Set.of("eform_data"), Map.of());
    }
}
