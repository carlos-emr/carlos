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

import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportCapturedTextRowDto;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.Outcome;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportNativeFieldKeyDto;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Document review rows and document inbox routes as the report path decides them: every review
 * travels whole in Notes (ReportReviewed holds a date only), an undated review is kept there
 * alone, and an imported document's routes are workflow.
 */
@Tag("unit")
@Tag("cds-export")
class CdsExportReportReviewerMappingUnitTest {
    private static final ZoneId ZONE = ZoneId.of("America/Toronto");

    private static CdsExportCapturedTextRowDto review(long id, String provider, String time) {
        var fields = new LinkedHashMap<String, String>();
        fields.put("id", Long.toString(id));
        fields.put("document_no", "91");
        fields.put("provider_no", provider);
        fields.put("date_reviewed", time);
        return new CdsExportCapturedTextRowDto(
                "document_review", CdsExportPatientTableSourceReader.key(id), fields);
    }

    private static CdsExportCapturedTextRowDto route(
            long id, String provider, String status, String comment) {
        var row =
                CdsExportLaboratoryReviewerMappingUnitTest.route(
                        id, provider, status, "2026-03-08 11:45:00", comment);
        var fields = new LinkedHashMap<>(row.fields());
        fields.put("lab_no", "91");
        fields.put("lab_type", "DOC");
        return new CdsExportCapturedTextRowDto(
                CdsExportDocumentRouteSourceReader.TABLE, row.recordKey(), fields);
    }

    private static Map<String, CdsExportCapturedTextRowDto> providers() {
        return Map.of(
                "101",
                CdsExportLaboratoryReviewerMappingUnitTest.provider(
                        "101", "Ada", "First", "123456"));
    }

    @Test
    void shouldCarryEveryReviewAndAcknowledgement_wholeInTheReportNotes() throws Exception {
        var dated = review(1, "101", "2026-03-06 15:30:00");
        var acknowledged = route(2, "101", "A", "");
        var waiting = route(3, "0", "N", null);
        var reviews =
                CdsExportInboxReviewMapper.report()
                        .map(List.of(acknowledged, waiting), List.of(dated), providers(), ZONE);
        // The element holds a date only; the exact times travel in Notes.
        assertThat(reviews.carried()).containsExactly(acknowledged, dated);
        assertThat(reviews.reviewers()).hasSize(2);
        var time =
                reviews.fields()
                        .get(
                                new CdsExportNativeFieldKeyDto(
                                        "document_review", dated.recordKey(), "date_reviewed"));
        assertThat(time.rule()).isEqualTo("REVIEW_DATE_ON_THE_WIRE_EXACT_TIME_IN_SOURCE_CONTEXT");
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(reviews.rows(), reviews.fields(), 100000, () -> {});
    }

    @Test
    void shouldKeepAnUndatedReview_inTheReportNotesOnly() throws Exception {
        var undated = review(4, "101", null);
        var reviews =
                CdsExportInboxReviewMapper.report()
                        .map(List.of(), List.of(undated), providers(), ZONE);
        assertThat(reviews.reviewers()).isEmpty();
        assertThat(reviews.carried()).containsExactly(undated);
        assertThat(reviews.providers()).containsKey("101");
        // The provider's public values are accounted in Notes, not as a reviewer element.
        assertThat(reviews.fields().entrySet())
                .filteredOn(entry -> entry.getKey().table().equals("provider"))
                .filteredOn(entry -> entry.getValue().outcome() == Outcome.MAPPED)
                .allSatisfy(
                        entry ->
                                assertThat(entry.getValue().destination())
                                        .isEqualTo("Reports/Notes"));
        new CdsExportNativeFieldAssessmentValidator()
                .requireRepresentable(reviews.rows(), reviews.fields(), 100000, () -> {});
    }

    @Test
    void shouldCarryAnAcknowledgementOrComment_butNotUnclaimedWorkflow() {
        assertThat(CdsExportInboxReviewMapper.carriedByReport(route(5, "101", "A", "")))
                .isTrue();
        assertThat(CdsExportInboxReviewMapper.carriedByReport(route(6, "0", "N", null)))
                .isFalse();
        assertThat(CdsExportInboxReviewMapper.carriedByReport(route(7, "0", "A", null)))
                .isFalse();
        assertThat(CdsExportInboxReviewMapper.carriedByReport(route(8, "102", "N", "Seen")))
                .isTrue();
        assertThat(
                        CdsExportInboxReviewMapper.carriedByReport(
                                review(9, "101", "2026-03-06 15:30:00")))
                .isTrue();
    }

    @Test
    void shouldAccountEveryFieldOfARemovedDocumentsReviewers_asNotShownInTheChart() {
        var rows = List.of(review(8, "101", "2026-03-06 15:30:00"), route(9, "101", "A", "Seen"));
        var fields =
                CdsExportInboxReviewMapper.removed(
                        rows, CdsExportDocumentRepresentation.REMOVED_RULE);
        assertThat(fields).hasSize(4 + CdsExportInboxReviewMapper.ROUTING_COLUMNS.size());
        assertThat(fields.values())
                .allMatch(
                        field ->
                                field.outcome() == Outcome.OPERATIONAL
                                        && field.rule()
                                                .equals(
                                                        CdsExportDocumentRepresentation
                                                                .REMOVED_RULE));
    }
}
