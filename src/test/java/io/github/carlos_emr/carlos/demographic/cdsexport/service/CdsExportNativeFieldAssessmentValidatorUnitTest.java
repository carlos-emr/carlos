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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.CdsExportFieldAssessmentDto.*;
import java.util.*;
import java.io.IOException;
import org.junit.jupiter.api.Test;

class CdsExportNativeFieldAssessmentValidatorUnitTest {
    private final CdsExportNativeFieldAssessmentValidator validator =
            new CdsExportNativeFieldAssessmentValidator();

    @Test
    void shouldVerifyValidatesExactIdentitiesAndSeparatelyCountsNullEmptyValueAndOutcomes_forNativeFieldAssessmentValidatorContract() throws Exception {
        var row = row();
        var result = validator.validate(List.of(row), assessments(row), 100, () -> {});
        assertThat(result)
                .isEqualTo(
                        new CdsExportNativeFieldAssessmentValidator.Result(3, 1, 1, 1, 2, 1, 0, 0));
        assertThat(result.representable()).isTrue();
    }

    @Test
    void shouldVerifyEqualSizeCannotHideAMissingFieldBehindAnInventedColumn_forNativeFieldAssessmentValidatorContract() {
        var row = row();
        var fields = assessments(row);
        fields.put(key("invented"), fields.remove(key("value")));
        refused(List.of(row), fields);
    }

    @Test
    void shouldVerifyEqualValuesCannotSubstituteAnotherRowOrTable_forNativeFieldAssessmentValidatorContract() {
        for (var key :
                List.of(
                        new CdsExportNativeFieldKeyDto("other", "1", "value"),
                        new CdsExportNativeFieldKeyDto("preventions", "2", "value"))) {
            var row = row();
            var fields = assessments(row);
            fields.put(key, fields.remove(key("value")));
            refused(List.of(row), fields);
        }
    }

    @Test
    void shouldVerifyMissingAndExtraAssessmentsAreBothScopeFailures_forNativeFieldAssessmentValidatorContract() {
        var row = row();
        var fields = assessments(row);
        fields.remove(key("value"));
        refused(List.of(row), fields);
        fields = assessments(row);
        fields.put(key("extra"), fields.get(key("value")));
        refused(List.of(row), fields);
    }

    @Test
    void shouldVerifyDuplicateRowsCannotBorrowOneSetOfFieldDecisions_forNativeFieldAssessmentValidatorContract() {
        var row = row();
        refused(List.of(row, row), assessments(row));
    }

    @Test
    void shouldVerifyNullEmptyAndValueCannotBeRelabeled_forNativeFieldAssessmentValidatorContract() {
        var row = row();
        for (var column : List.of("null_value", "empty_value", "value"))
            for (var presence : Presence.values()) {
                var fields = assessments(row);
                var old = fields.get(key(column));
                if (presence == old.presence()) continue;
                fields.put(
                        key(column),
                        new CdsExportFieldAssessmentDto(
                                presence, old.outcome(), old.destination(), old.rule()));
                refused(List.of(row), fields);
            }
    }

    @Test
    void shouldVerifyPopulatedValuesCannotBeMarkedAbsentOrMapped_withoutADestination() {
        var row = row();
        for (var outcome : List.of(Outcome.ABSENT, Outcome.MAPPED)) {
            var fields = assessments(row);
            fields.put(
                    key("value"),
                    new CdsExportFieldAssessmentDto(Presence.VALUE, outcome, "", "CLAIM"));
            refused(List.of(row), fields);
        }
    }

    @Test
    void shouldVerifyAReviewFieldRemainsBlockedAndCannotReturnRepresentable_forNativeFieldAssessmentValidatorContract() throws Exception {
        var row = row();
        var fields = assessments(row);
        fields.put(
                key("value"),
                new CdsExportFieldAssessmentDto(
                        Presence.VALUE, Outcome.REQUIRES_REVIEW, "", "UNMAPPED_NATIVE_VALUE"));
        assertThat(validator.validate(List.of(row), fields, 100, () -> {}).blocked()).isEqualTo(1);
        assertThatThrownBy(
                        () -> validator.requireRepresentable(List.of(row), fields, 100, () -> {}))
                .hasMessage("CDS_EXPORT_FIELD_ASSESSMENT_REQUIRES_REVIEW");
    }

    @Test
    void shouldVerifyOperationalValuesRequireAnExplicitBoundedRule_forNativeFieldAssessmentValidatorContract() throws Exception {
        var row = row();
        var fields = assessments(row);
        fields.put(
                key("value"),
                new CdsExportFieldAssessmentDto(
                        Presence.VALUE, Outcome.OPERATIONAL, "", "CAPTURED_NATIVE_IDENTITY"));
        assertThat(validator.validate(List.of(row), fields, 100, () -> {}).operational())
                .isEqualTo(1);
        fields.put(
                key("value"),
                new CdsExportFieldAssessmentDto(Presence.VALUE, Outcome.OPERATIONAL, "", " "));
        refused(List.of(row), fields);
    }

    @Test
    void shouldVerifyBoundsAndFinalCancellationNeverReturnACompleteReceipt_forNativeFieldAssessmentValidatorContract() {
        var row = row();
        assertThatThrownBy(() -> validator.validate(List.of(row), assessments(row), 2, () -> {}))
                .hasMessage("CDS_EXPORT_FIELD_ASSESSMENT_RESOURCE_LIMIT");
        var calls = new java.util.concurrent.atomic.AtomicInteger();
        assertThatThrownBy(
                        () ->
                                validator.validate(
                                        List.of(row),
                                        assessments(row),
                                        100,
                                        () -> {
                                            if (calls.incrementAndGet() == 5)
                                                throw new java.util.concurrent
                                                        .CancellationException("FINAL_CANCEL");
                                        }))
                .isInstanceOf(java.util.concurrent.CancellationException.class)
                .hasMessage("FINAL_CANCEL");
    }

    @Test
    void shouldVerifyEmptyInputCannotBorrowUnrelatedDecisionsOrClaimWholeChartCoverage_forNativeFieldAssessmentValidatorContract() throws Exception {
        assertThat(validator.validate(List.of(), Map.of(), 100, () -> {}).fields()).isZero();
        refused(List.of(), assessments(row()));
    }

    @Test
    void shouldVerifyForgedSummaryCountsCannotBeLabeledComplete_forNativeFieldAssessmentValidatorContract() {
        assertThatThrownBy(
                        () ->
                                new CdsExportNativeFieldAssessmentValidator.Result(
                                        3, 1, 1, 0, 2, 1, 0, 0))
                .hasMessage("CDS_EXPORT_FIELD_ASSESSMENT_COUNTS_INVALID");
    }

    private void refused(
            List<CdsExportCapturedTextRowDto> rows,
            Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> fields) {
        assertThatThrownBy(() -> validator.validate(rows, fields, 100, () -> {}))
                .hasMessage("CDS_EXPORT_FIELD_ASSESSMENT_SCOPE_INVALID");
    }

    private static CdsExportCapturedTextRowDto row() {
        var fields = new TreeMap<String, String>();
        fields.put("null_value", null);
        fields.put("empty_value", "");
        fields.put("value", "café 😀\r\n");
        return new CdsExportCapturedTextRowDto("preventions", "1", fields);
    }

    private static CdsExportNativeFieldKeyDto key(String column) {
        return new CdsExportNativeFieldKeyDto("preventions", "1", column);
    }

    private static Map<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto> assessments(
            CdsExportCapturedTextRowDto row) {
        var fields = new LinkedHashMap<CdsExportNativeFieldKeyDto, CdsExportFieldAssessmentDto>();
        row.fields()
                .forEach(
                        (column, text) ->
                                fields.put(
                                        key(column),
                                        new CdsExportFieldAssessmentDto(
                                                text == null
                                                        ? Presence.SQL_NULL
                                                        : text.isEmpty()
                                                                ? Presence.EMPTY
                                                                : Presence.VALUE,
                                                text == null || text.isEmpty()
                                                        ? Outcome.ABSENT
                                                        : Outcome.MAPPED,
                                                text == null || text.isEmpty() ? "" : "Notes",
                                                "EXACT_NATIVE_TEXT")));
        return fields;
    }
}
