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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportDateDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Row;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryPlanner.Value;
import io.github.carlos_emr.carlos.demographic.cdsexport.service.CdsExportDataSummaryRegistry.Kind;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** One record per tickler, form revision, eForm, consultation and message, with its children. */
@Tag("unit")
@Tag("cds-export")
class CdsExportDataSummaryPlannerUnitTest {
    private static final ZoneId TORONTO = ZoneId.of("America/Toronto");

    static Row row(String table, String key, String... columns) {
        var values = new LinkedHashMap<String, Value>();
        for (int i = 0; i < columns.length; i += 2)
            values.put(columns[i], columns[i + 1] == null ? Value.sqlNull() : Value.text(columns[i + 1]));
        return new Row(table, key, values);
    }

    static String key(long id) {
        return CdsExportPatientTableSourceReader.key(id);
    }

    @Test
    void shouldGroupATicklerWithItsCommentsUpdatesAndDocuments_inOneRecord() {
        var rows = new HashMap<String, List<Row>>();
        rows.put(
                "tickler",
                List.of(
                        row("tickler", key(7), "tickler_no", "7", "service_date", "2024-03-10 09:30:00"),
                        row("tickler", key(8), "tickler_no", "8", "service_date", null)));
        rows.put(
                "tickler_comments",
                List.of(row("tickler_comments", key(1), "id", "1", "tickler_no", "7")));
        rows.put(
                "tickler_update",
                List.of(row("tickler_update", key(2), "id", "2", "tickler_no", "7")));
        rows.put("ticklerdocs", List.of(row("ticklerdocs", key(3), "id", "3", "tickler_id", "8")));
        var groups = CdsExportDataSummaryPlanner.plan(rows, TORONTO);
        assertThat(groups).hasSize(2);
        var first = groups.getFirst();
        assertThat(first.kind()).isEqualTo(Kind.TICKLER);
        assertThat(first.id()).isEqualTo("7");
        assertThat(first.rows())
                .extracting(Row::table)
                .containsExactly("tickler", "tickler_comments", "tickler_update");
        assertThat(first.subClass()).isEqualTo("Tickler");
        assertThat(first.event())
                .isEqualTo(
                        new CdsExportDateDto(
                                CdsExportDateDto.Precision.DATE_TIME, "2024-03-10T09:30:00-04:00"));
        assertThat(groups.get(1).rows()).extracting(Row::table).containsExactly("tickler", "ticklerdocs");
        // No clinical date is stored, so none is invented.
        assertThat(groups.get(1).event()).isNull();
    }

    @Test
    void shouldNumberEachSavedFormRow_asARevisionAndAttachItsRourkeCheckboxes() {
        var rows = new HashMap<String, List<Row>>();
        rows.put(
                "formRourke2020",
                List.of(
                        row("formRourke2020", key(31), "ID", "31", "formCreated", "2024-02-02"),
                        row("formRourke2020", key(12), "ID", "12", "formCreated", "2024-01-01")));
        rows.put(
                "formRourke2017",
                List.of(row("formRourke2017", key(12), "ID", "12", "formCreated", "2019-05-05")));
        rows.put(
                "form_boolean_value",
                List.of(
                        row("form_boolean_value", "00000000000000000001", "form_name", "formRourke2017", "form_id", "12", "field_name", "a", "value", "1"),
                        row("form_boolean_value", "00000000000000000002", "form_name", "formRourke2020", "form_id", "12", "field_name", "a", "value", "0"),
                        row("form_boolean_value", "00000000000000000003", "form_name", "formRourke2020", "form_id", "12", "field_name", "b", "value", null),
                        row("form_boolean_value", "00000000000000000004", "form_name", "formRourke2020", "form_id", "99", "field_name", "c", "value", "1")));
        var groups = CdsExportDataSummaryPlanner.plan(rows, TORONTO);
        var rourke2020 =
                groups.stream().filter(group -> group.table().equals("formRourke2020")).toList();
        assertThat(rourke2020).extracting(CdsExportDataSummaryPlanner.Group::id).containsExactly("12", "31");
        assertThat(rourke2020.getFirst().subClass()).isEqualTo("formRourke2020 revision 1 of 2");
        assertThat(rourke2020.get(1).subClass()).isEqualTo("formRourke2020 revision 2 of 2");
        assertThat(rourke2020.getFirst().rows()).hasSize(3);
        assertThat(rourke2020.getFirst().event())
                .isEqualTo(new CdsExportDateDto(CdsExportDateDto.Precision.DATE, "2024-01-01"));
        // The same form_id in the other Rourke table is a different record.
        var rourke2017 =
                groups.stream().filter(group -> group.table().equals("formRourke2017")).findFirst().orElseThrow();
        assertThat(rourke2017.rows()).hasSize(2);
        assertThat(rourke2017.rows().get(1).text("form_name")).isEqualTo("formRourke2017");
        // A checkbox value whose revision is not in this chart is never guessed into another one.
        var orphan =
                groups.stream().filter(CdsExportDataSummaryPlanner.Group::detached).findFirst().orElseThrow();
        assertThat(orphan.table()).isEqualTo("form_boolean_value");
        assertThat(orphan.id()).isEqualTo("00000000000000000004");
        assertThat(orphan.subClass()).isEqualTo("Encounter form: form_boolean_value record");
    }

    @Test
    void shouldNumberArchivedConsultationVersions_amongTheVersionsOfTheSameRequest() {
        var rows = new HashMap<String, List<Row>>();
        rows.put(
                "consultationRequestsArchive",
                List.of(
                        row("consultationRequestsArchive", key(3), "Id", "3", "requestId", "5",
                                "referalDate", "2024-02-03", "lastUpdateDate", "2024-02-05 09:00:00"),
                        row("consultationRequestsArchive", key(1), "Id", "1", "requestId", "5",
                                "referalDate", "2024-02-03", "lastUpdateDate", "2024-02-04 09:00:00"),
                        row("consultationRequestsArchive", key(2), "Id", "2", "requestId", "6",
                                "referalDate", "2024-03-01", "lastUpdateDate", null)));
        rows.put(
                "consultationRequestExtArchive",
                List.of(row("consultationRequestExtArchive", key(7), "id", "7", "requestId", "5",
                        "consultationRequestArchiveId", "3", "name", "letterheadTitle", "value", "Dr")));

        var groups = CdsExportDataSummaryPlanner.plan(rows, TORONTO);

        assertThat(groups).extracting(CdsExportDataSummaryPlanner.Group::id).containsExactly("1", "2", "3");
        assertThat(groups).extracting(CdsExportDataSummaryPlanner.Group::kind)
                .containsOnly(Kind.CONSULTATION_REQUEST_VERSION);
        assertThat(groups).extracting(CdsExportDataSummaryPlanner.Group::revisionLabel)
                .containsExactly(
                        "Revision 1 of 2, saved 2024-02-04 09:00:00",
                        "Revision 1 of 1",
                        "Revision 2 of 2, saved 2024-02-05 09:00:00");
        assertThat(groups.get(2).rows()).extracting(Row::table)
                .containsExactly("consultationRequestsArchive", "consultationRequestExtArchive");
        assertThat(groups.get(0).subClass()).isEqualTo("consultationRequestsArchive revision 1 of 2");
        assertThat(groups.get(2).event().lexical()).isEqualTo("2024-02-03");
    }

    @Test
    void shouldKeepAnAmbiguousPlannerRecordOnItsOwn_insteadOfGuessingItsForm() {
        var rows = new HashMap<String, List<Row>>();
        rows.put("formBCAR", List.of(row("formBCAR", key(5), "ID", "5", "formCreated", "2020-01-01")));
        rows.put("formBCAR2007", List.of(row("formBCAR2007", key(5), "ID", "5", "formCreated", "2020-01-01")));
        rows.put("formAnnual", List.of(row("formAnnual", key(6), "ID", "6", "formCreated", "2020-01-01")));
        rows.put(
                "desaprisk",
                List.of(row("desaprisk", key(1), "desaprisk_no", "1", "form_no", "5", "desaprisk_date", "2020-01-02", "desaprisk_time", "10:15:00")));
        rows.put("desannualreviewplan", List.of(row("desannualreviewplan", key(2), "des_no", "2", "form_no", "6")));
        var groups = CdsExportDataSummaryPlanner.plan(rows, TORONTO);
        var annual = groups.stream().filter(group -> group.table().equals("formAnnual")).findFirst().orElseThrow();
        assertThat(annual.rows()).extracting(Row::table).containsExactly("formAnnual", "desannualreviewplan");
        var risk = groups.stream().filter(group -> group.table().equals("desaprisk")).findFirst().orElseThrow();
        assertThat(risk.detached()).isTrue();
        assertThat(risk.event())
                .isEqualTo(
                        new CdsExportDateDto(
                                CdsExportDateDto.Precision.DATE_TIME, "2020-01-02T10:15:00-05:00"));
    }

    @Test
    void shouldGroupEFormsConsultationsAndMessages_withTheirOwnChildren() {
        var rows = new HashMap<String, List<Row>>();
        rows.put("eform_data", List.of(row("eform_data", key(4), "fdid", "4", "form_date", "2024-05-06", "form_time", "13:00:00")));
        rows.put("eform_values", List.of(row("eform_values", key(40), "id", "40", "fdid", "4")));
        rows.put("EFormDocs", List.of(row("EFormDocs", key(41), "id", "41", "fdid", "4")));
        rows.put("consultationRequests", List.of(row("consultationRequests", key(9), "requestId", "9", "referalDate", "2024-07-08")));
        rows.put("consultationRequestExt", List.of(row("consultationRequestExt", key(90), "id", "90", "requestId", "9")));
        rows.put("consultdocs", List.of(row("consultdocs", key(91), "id", "91", "requestId", "9")));
        rows.put("emailLog", List.of(row("emailLog", key(3), "id", "3", "timestamp", "2024-11-03 01:30:00")));
        rows.put("emailAttachment", List.of(row("emailAttachment", key(30), "id", "30", "logId", "3")));
        rows.put("messagetbl", List.of(row("messagetbl", key(2), "messageid", "2", "thedate", "0000-00-00", "theime", "12:00:00")));
        rows.put("msgDemoMap", List.of(row("msgDemoMap", key(20), "id", "20", "messageID", "2")));
        var groups = CdsExportDataSummaryPlanner.plan(rows, TORONTO);
        assertThat(groups).extracting(CdsExportDataSummaryPlanner.Group::kind)
                .containsExactly(Kind.EFORM, Kind.CONSULTATION_REQUEST, Kind.EMAIL, Kind.MESSAGE);
        assertThat(groups.get(0).rows()).extracting(Row::table).containsExactly("eform_data", "eform_values", "EFormDocs");
        assertThat(groups.get(0).event())
                .isEqualTo(new CdsExportDateDto(CdsExportDateDto.Precision.DATE_TIME, "2024-05-06T13:00:00-04:00"));
        assertThat(groups.get(1).rows()).extracting(Row::table)
                .containsExactly("consultationRequests", "consultationRequestExt", "consultdocs");
        // 01:30 on the autumn change happens twice in Toronto: the exact date stays, the clock does not.
        assertThat(groups.get(2).event())
                .isEqualTo(new CdsExportDateDto(CdsExportDateDto.Precision.DATE, "2024-11-03"));
        assertThat(groups.get(2).rows()).extracting(Row::table).containsExactly("emailLog", "emailAttachment");
        // A zero date is not a date.
        assertThat(groups.get(3).event()).isNull();
        assertThat(groups.get(3).rows()).extracting(Row::table).containsExactly("messagetbl", "msgDemoMap");
    }

    @Test
    void shouldGroupAPathNetReport_withSegmentsReachedThroughItsPatientAndOrderRows() {
        var rows = new HashMap<String, List<Row>>();
        rows.put("hl7_message", List.of(row("hl7_message", key(5), "message_id", "5", "date_time", "2012-03-04 08:00:00")));
        rows.put("hl7_msh", List.of(row("hl7_msh", key(51), "msh_id", "51", "message_id", "5")));
        rows.put("hl7_pid", List.of(row("hl7_pid", key(52), "pid_id", "52", "message_id", "5")));
        rows.put("hl7_link", List.of(row("hl7_link", key(52), "pid_id", "52", "demographic_no", "1")));
        rows.put("hl7_orc", List.of(row("hl7_orc", key(53), "orc_id", "53", "pid_id", "52")));
        rows.put(
                "hl7_obr",
                List.of(
                        row("hl7_obr", key(54), "obr_id", "54", "pid_id", "52"),
                        row("hl7_obr", key(55), "obr_id", "55", "pid_id", "52")));
        rows.put(
                "hl7_obx",
                List.of(
                        row("hl7_obx", key(56), "obx_id", "56", "obr_id", "55"),
                        row("hl7_obx", key(57), "obx_id", "57", "obr_id", "54"),
                        // A result whose order row is in no record of this chart stands alone.
                        row("hl7_obx", key(58), "obx_id", "58", "obr_id", "99", "observation_date_time", "2012-03-01 07:30:00")));
        var groups = CdsExportDataSummaryPlanner.plan(rows, TORONTO);
        assertThat(groups).hasSize(2);
        var report = groups.getFirst();
        assertThat(report.kind()).isEqualTo(Kind.PATHNET_LAB_REPORT);
        assertThat(report.subClass()).isEqualTo("PathNet laboratory report");
        assertThat(report.event())
                .isEqualTo(new CdsExportDateDto(CdsExportDateDto.Precision.DATE_TIME, "2012-03-04T08:00:00-05:00"));
        // Every segment joins the one report its chain names, two and three links away.
        assertThat(report.rows())
                .extracting(Row::table)
                .containsExactly(
                        "hl7_message", "hl7_msh", "hl7_pid", "hl7_link", "hl7_orc", "hl7_obr",
                        "hl7_obr", "hl7_obx", "hl7_obx");
        assertThat(report.rows().subList(7, 9)).extracting(r -> r.text("obx_id")).containsExactly("56", "57");
        var alone = groups.get(1);
        assertThat(alone.detached()).isTrue();
        assertThat(alone.subClass()).isEqualTo("PathNet laboratory report: hl7_obx record");
        assertThat(alone.event())
                .isEqualTo(new CdsExportDateDto(CdsExportDateDto.Precision.DATE_TIME, "2012-03-01T07:30:00-05:00"));
    }

    @Test
    void shouldGroupMdsSegmentsByOrdinal_andCmlLinesWithTheirReportAndUploadHeader() {
        var rows = new HashMap<String, List<Row>>();
        rows.put("mdsMSH", List.of(row("mdsMSH", key(70), "segmentID", "70", "dateTime", "2004-05-06 07:08:09")));
        // Keyless segments are named by their capture ordinal; two identical rows stay two rows.
        rows.put(
                "mdsOBX",
                List.of(
                        row("mdsOBX", "00000000000000000002", "segmentID", "70", "observationValue", "5.1"),
                        row("mdsOBX", "00000000000000000001", "segmentID", "70", "observationValue", "5.1")));
        rows.put("mdsNTE", List.of(row("mdsNTE", "00000000000000000001", "segmentID", "70", "comment", "Fasting")));
        rows.put(
                "labPatientPhysicianInfo",
                List.of(
                        row("labPatientPhysicianInfo", key(80), "id", "80", "labReportInfo_id", "3", "service_date", "20040215"),
                        row("labPatientPhysicianInfo", key(81), "id", "81", "labReportInfo_id", "4", "service_date", "2004-02-1")));
        rows.put(
                "labTestResults",
                List.of(row("labTestResults", key(800), "id", "800", "labPatientPhysicianInfo_id", "80")));
        rows.put(
                "labReportInformation",
                List.of(row("labReportInformation", key(3), "id", "3"), row("labReportInformation", key(4), "id", "4")));
        var groups = CdsExportDataSummaryPlanner.plan(rows, TORONTO);
        assertThat(groups).extracting(CdsExportDataSummaryPlanner.Group::kind)
                .containsExactly(Kind.MDS_LAB_REPORT, Kind.CML_LAB_REPORT, Kind.CML_LAB_REPORT);
        var mds = groups.getFirst();
        assertThat(mds.rows()).extracting(Row::table).containsExactly("mdsMSH", "mdsOBX", "mdsOBX", "mdsNTE");
        assertThat(mds.rows()).extracting(Row::recordKey)
                .containsExactly(key(70), "00000000000000000001", "00000000000000000002", "00000000000000000001");
        var cml = groups.get(1);
        assertThat(cml.rows()).extracting(Row::table)
                .containsExactly("labPatientPhysicianInfo", "labTestResults", "labReportInformation");
        // The CML service date is stored as YYYYMMDD; text that is not a date gives none.
        assertThat(cml.event()).isEqualTo(new CdsExportDateDto(CdsExportDateDto.Precision.DATE, "2004-02-15"));
        assertThat(groups.get(2).rows()).extracting(Row::table)
                .containsExactly("labPatientPhysicianInfo", "labReportInformation");
        assertThat(groups.get(2).event()).isNull();
    }

    @Test
    void shouldKeepAnUploadHeaderOnItsOwn_whenTwoReportsOfTheChartNameIt() {
        var rows = new HashMap<String, List<Row>>();
        rows.put(
                "labPatientPhysicianInfo",
                List.of(
                        row("labPatientPhysicianInfo", key(80), "id", "80", "labReportInfo_id", "3"),
                        row("labPatientPhysicianInfo", key(81), "id", "81", "labReportInfo_id", "3")));
        rows.put("labReportInformation", List.of(row("labReportInformation", key(3), "id", "3")));
        var groups = CdsExportDataSummaryPlanner.plan(rows, TORONTO);
        assertThat(groups).hasSize(3);
        assertThat(groups.get(2).detached()).isTrue();
        assertThat(groups.get(2).table()).isEqualTo("labReportInformation");
        assertThat(groups.get(0).rows()).hasSize(1);
    }

    @Test
    void shouldNumberLegacyEncounterAndScheduleRevisions_andKeepRelationshipsAndAdmissionsApart() {
        var rows = new HashMap<String, List<Row>>();
        rows.put(
                "eChart",
                List.of(
                        row("eChart", key(9), "eChartId", "9", "timeStamp", "2005-06-07 10:00:00"),
                        row("eChart", key(4), "eChartId", "4", "timeStamp", "2005-01-02 10:00:00")));
        rows.put("immunizations", List.of(row("immunizations", key(2), "ID", "2", "save_date", "2003-04-05")));
        rows.put("relationships", List.of(row("relationships", key(6), "id", "6", "creation_date", null)));
        rows.put("admission", List.of(row("admission", key(8), "am_id", "8", "admission_date", "2010-01-01 09:00:00")));
        var groups = CdsExportDataSummaryPlanner.plan(rows, TORONTO);
        assertThat(groups).extracting(CdsExportDataSummaryPlanner.Group::subClass)
                .containsExactly(
                        "Program admission",
                        "eChart revision 1 of 2",
                        "eChart revision 2 of 2",
                        "immunizations revision 1 of 1",
                        "Patient relationship");
        assertThat(groups.get(1).id()).isEqualTo("4");
        assertThat(groups.get(1).title())
                .isEqualTo("CARLOS data summary: Legacy encounter eChart (revision 1 of 2, saved 2005-01-02 10:00:00)");
        assertThat(groups.get(1).revisionLabel()).isEqualTo("Revision 1 of 2, saved 2005-01-02 10:00:00");
        assertThat(groups.get(2).revisionLabel()).isEqualTo("Revision 2 of 2, saved 2005-06-07 10:00:00");
        assertThat(groups.get(3).kind()).isEqualTo(Kind.IMMUNIZATION_SCHEDULE);
        assertThat(groups.get(3).revisionLabel()).isEqualTo("Revision 1 of 1, saved 2003-04-05");
        assertThat(groups.get(4).revisionLabel()).isEmpty();
        assertThat(groups.get(4).event()).isNull();
    }

    @Test
    void shouldPlanTheSameRecordsAndIdentities_forTheSameCaptureInAnyRowOrder() {
        var forward = new HashMap<String, List<Row>>();
        forward.put("Consent", List.of(row("Consent", key(1), "id", "1"), row("Consent", key(2), "id", "2")));
        var backward = new HashMap<String, List<Row>>();
        backward.put("Consent", List.of(row("Consent", key(2), "id", "2"), row("Consent", key(1), "id", "1")));
        assertThat(CdsExportDataSummaryPlanner.plan(forward, TORONTO))
                .isEqualTo(CdsExportDataSummaryPlanner.plan(backward, TORONTO));
        assertThat(CdsExportDataSummaryPlanner.plan(Map.of(), TORONTO)).isEmpty();
    }
}
