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
