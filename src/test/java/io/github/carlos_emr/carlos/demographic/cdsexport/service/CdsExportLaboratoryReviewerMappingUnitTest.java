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
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.commn.dao.CdsExportPatientTableReadSupport.Plan;
import io.github.carlos_emr.carlos.commn.dto.CdsExportProviderRowDto;
import io.github.carlos_emr.carlos.demographic.cds.contract.CdsCategory;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeFieldKeyDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportRetainedIndexDto;
import java.nio.charset.StandardCharsets;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * The inbox routes of a native HL7 report become its ResultReviewer elements, its comments travel
 * in its supporting record, and every route field has an explicit disposition.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportLaboratoryReviewerMappingUnitTest {
    private static final ZoneId ZONE = ZoneId.of("America/Toronto");

    /**
     * The captured value of a reviewer TIMESTAMP: the instant ({@code UNIX_TIMESTAMP}) of a
     * clinic wall-clock time, so fixtures stay readable.
     */
    static String instant(String clinicTime) {
        return clinicTime == null
                ? null
                : Long.toString(
                        java.time.LocalDateTime.parse(clinicTime.replace(' ', 'T'))
                                .atZone(ZONE)
                                .toEpochSecond());
    }

    static CdsExportCapturedTextRowDto route(
            long id, String provider, String status, String timestamp, String comment) {
        var fields = new LinkedHashMap<String, String>();
        Plan.LABORATORY_REVIEWERS.columns().forEach(column -> fields.put(column, null));
        fields.put("id", Long.toString(id));
        fields.put("lab_no", "91");
        fields.put("lab_type", "HL7");
        fields.put("provider_no", provider);
        fields.put("status", status);
        fields.put("timestamp", instant(timestamp));
        fields.put("comment", comment);
        return new CdsExportCapturedTextRowDto(
                "providerLabRouting", CdsExportPatientTableSourceReader.key(id), fields);
    }

    static CdsExportCapturedTextRowDto provider(String number, String first, String last, String ohip) {
        var fields = new LinkedHashMap<String, String>();
        CdsExportProviderRowDto.COLUMNS.forEach(column -> fields.put(column, null));
        fields.put("provider_no", number);
        fields.put("first_name", first);
        fields.put("last_name", last);
        fields.put("ohip_no", ohip);
        return new CdsExportCapturedTextRowDto(
                "provider", CdsExportPublicProviderSourceReader.key(number), fields);
    }

    private static Map<String, CdsExportCapturedTextRowDto> providers() {
        return Map.of(
                "101", provider("101", "Ada", "Reviewer", "123456"),
                // A BC MSP number is not an OHIP number: the name is exported, not the number.
                "102", provider("102", "Bea", "Second", "12345"),
                "103", provider("103", "Cy", "Filer", "654321"));
    }

    private static CdsExportInboxReviewMapper.Reviews reviews(
            List<CdsExportCapturedTextRowDto> routes) throws Exception {
        return CdsExportInboxReviewMapper.laboratory().map(routes, List.of(), providers(), ZONE);
    }

    private static String rule(
            CdsExportInboxReviewMapper.Reviews reviews,
            CdsExportCapturedTextRowDto row,
            String column) {
        return reviews.fields()
                .get(new CdsExportNativeFieldKeyDto(row.table(), row.recordKey(), column))
                .rule();
    }

    @Test
    void shouldExportEveryAcknowledgement_asAResultReviewerOfEveryResultOfTheReport()
            throws Exception {
        var unclaimed = route(1, "0", "N", "2026-07-10 09:00:00", "");
        var filed = route(2, "103", "F", "2026-07-10 09:30:00", null);
        var second = route(3, "102", "A", "2026-07-11 08:15:00", "");
        var first = route(4, "101", "A", "2026-07-10 16:45:00", null);
        var reviews = reviews(List.of(unclaimed, filed, second, first));
        var source = CdsExportNativeLaboratoryMapperUnitTest.source(
                CdsExportNativeLaboratoryMapperUnitTest.message());
        var mapper = new CdsExportNativeLaboratoryMapper();
        var values = mapper.map(source, ZONE, reviews);
        assertThat(values).hasSize(4);
        for (var value : values) {
            assertThat(value.reviewers())
                    .extracting(CdsExportInboxReviewMapper.Review::providerNo)
                    .containsExactly("101", "102");
            String xml =
                    new String(
                            render(CdsCategory.LABORATORY_RESULTS, out -> mapper.write(value, out)),
                            StandardCharsets.UTF_8);
            assertThat(xml.split("<ResultReviewer>", -1)).hasSize(3);
            assertThat(xml)
                    .contains(
                            "<ResultReviewer><Name><cdsd:FirstName>Ada</cdsd:FirstName>"
                                    + "<cdsd:LastName>Reviewer</cdsd:LastName></Name>"
                                    + "<OHIPPhysicianId>123456</OHIPPhysicianId>"
                                    + "<DateTimeResultReviewed><cdsd:FullDateTime>"
                                    + "2026-07-10T16:45:00-04:00</cdsd:FullDateTime>",
                            "<ResultReviewer><Name><cdsd:FirstName>Bea</cdsd:FirstName>"
                                    + "<cdsd:LastName>Second</cdsd:LastName></Name>"
                                    + "<DateTimeResultReviewed>",
                            "</ResultReviewer><ResultNormalAbnormalFlag>")
                    .doesNotContain("12345<", "Filer");
        }
        var assessments = CdsExportNativeLaboratoryMapper.assessments(source, reviews);
        var rows = new ArrayList<>(source.rows());
        rows.addAll(reviews.rows());
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(rows, assessments, 100000, () -> {});
        assertThat(rule(reviews, unclaimed, "provider_no"))
                .isEqualTo("UNCLAIMED_INBOX_ROUTING_PLACEHOLDER");
        assertThat(rule(reviews, filed, "status"))
                .isEqualTo("UNACKNOWLEDGED_INBOX_ROUTING_WORKFLOW");
        assertThat(rule(reviews, first, "timestamp"))
                .isEqualTo("ACKNOWLEDGEMENT_TIME_IN_CLINIC_ZONE");
        // A route without a comment adds nothing to the supporting record.
        assertThat(reviews.carried()).isEmpty();
        // Each provider row is accounted once, however many routes name it.
        assertThat(reviews.rows().stream().filter(row -> row.table().equals("provider")))
                .hasSize(2);
    }

    @Test
    void shouldCarryAReviewComment_wholeInTheReportsSupportingRecord() throws Exception {
        var acknowledged =
                route(5, "101", "A", "2026-07-10 16:45:00", "Repeat in 3 months; \"call\" patient");
        var noted = route(6, "103", "N", "2026-07-10 09:30:00", "Waiting for imaging");
        var reviews = reviews(List.of(acknowledged, noted));
        assertThat(reviews.carried()).containsExactly(acknowledged, noted);
        assertThat(reviews.reviewers()).hasSize(1);
        assertThat(rule(reviews, acknowledged, "comment"))
                .isEqualTo("INBOX_REVIEW_COMMENT_IN_SOURCE_CONTEXT");
        assertThat(rule(reviews, noted, "status"))
                .isEqualTo(CdsExportInboxReviewMapper.CARRIED_RULE);
        // The acknowledgement still maps to its reviewer element.
        assertThat(rule(reviews, acknowledged, "status")).isEqualTo("ACKNOWLEDGED_INBOX_STATUS");
        var source = CdsExportNativeLaboratoryMapperUnitTest.source(
                CdsExportNativeLaboratoryMapperUnitTest.message());
        String residual =
                new String(
                        render(
                                CdsCategory.NEW_CATEGORY,
                                xml ->
                                        new CdsExportNativeLaboratoryMapper()
                                                .writeSupporting(
                                                        source,
                                                        CdsExportInboxReviewMapper.carried(
                                                                List.of(noted, acknowledged)),
                                                        xml)),
                        StandardCharsets.UTF_8);
        // Residual Content is an xs:token: spaces travel escaped so none collapse.
        assertThat(residual)
                .contains(
                        "Repeat\\u0020in\\u00203\\u0020months;\\u0020\\\"call\\\"\\u0020patient",
                        "Waiting\\u0020for\\u0020imaging")
                .doesNotContain("Waiting for imaging");
        var carried =
                CdsExportInboxReviewMapper.carriedFields(
                        reviews.carried(), "NewCategory/ResidualInfo");
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(reviews.carried(), carried, 100000, () -> {});
    }

    @Test
    void shouldWriteOneElement_forTheSameAcknowledgementRecordedTwice() throws Exception {
        var reviews =
                reviews(
                        List.of(
                                route(7, "101", "A", "2026-07-10 16:45:00", null),
                                route(8, "101", "A", "2026-07-10 16:45:00", null)));
        assertThat(reviews.reviewers()).hasSize(1);
        assertThat(reviews.rows()).hasSize(3);
    }

    @Test
    void shouldHoldOnlyThePatient_forAnUnknownStatusOrAnUnknownReviewer() throws Exception {
        assertThatThrownBy(
                        () -> reviews(List.of(route(9, "101", "Z", "2026-07-10 16:45:00", null))))
                .hasMessage("CDS_EXPORT_INBOX_ROUTING_STATUS_REQUIRES_REVIEW");
        assertThatThrownBy(
                        () -> reviews(List.of(route(10, "999", "A", "2026-07-10 16:45:00", null))))
                .hasMessage("CDS_EXPORT_NATIVE_REPORT_REVIEWER_REQUIRED");
        // An unclaimed placeholder is never a reviewer, whatever its status.
        assertThat(
                        CdsExportInboxReviewMapper.laboratory()
                                .map(
                                        List.of(route(11, "0", "A", "2026-07-10 16:45:00", null)),
                                        List.of(),
                                        Map.of(),
                                        ZONE)
                                .reviewers())
                .isEmpty();
    }

    @Test
    void shouldFindTheImportedResultsWithACommentedRoute_fromTheirAppliedUnits() throws Exception {
        String applied = "11111111-1111-4111-8111-111111111111:" + "b".repeat(64);
        String unapplied = "11111111-1111-4111-8111-111111111111:" + "c".repeat(64);
        var units = new java.util.HashMap<String, CdsExportCapturedTextRowDto>();
        for (String address : List.of(applied, unapplied))
            units.put(
                    address,
                    new CdsExportCapturedTextRowDto(
                            "cds_import_unit",
                            address.substring(37),
                            Map.of(
                                    "run_id",
                                    address.substring(0, 36),
                                    "unit_key",
                                    address.substring(37),
                                    "category",
                                    "LaboratoryResults")));
        var index =
                new CdsExportRetainedIndexDto(
                        "a".repeat(64),
                        0,
                        0,
                        Map.of("cds_import_unit", units),
                        java.util.Set.of(applied),
                        Map.of(
                                applied,
                                List.of(
                                        new CdsExportRetainedIndexDto.Reference(
                                                "cds_laboratory_result", "d".repeat(64), "e".repeat(64)),
                                        new CdsExportRetainedIndexDto.Reference(
                                                "hl7TextMessage", "91", "e".repeat(64)))),
                        Map.of());
        var commented = route(14, "101", "A", "2026-07-10 16:45:00", "Discussed with patient");
        var acknowledged = route(12, "101", "A", "2026-07-10 16:45:00", null);
        var found =
                CdsExportLaboratoryOccurrenceService.importedComments(
                        index, Map.of("91", List.of(acknowledged, commented)));
        assertThat(found).singleElement().satisfies(
                comments -> {
                    assertThat(comments.result()).isEqualTo("d".repeat(64));
                    assertThat(comments.lab()).isEqualTo("91");
                    assertThat(comments.routes()).containsExactly(commented);
                });
        assertThat(
                        CdsExportLaboratoryOccurrenceService.importedComments(
                                index, Map.of("91", List.of(acknowledged))))
                .isEmpty();
    }
}
