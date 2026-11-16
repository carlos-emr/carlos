/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.managers;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;

/**
 * The content evidence of a prescription save: the token that tells an exact replay of a saved
 * draft from the same draft posted again with edited fields.
 */
@Tag("unit")
@Tag("prescription")
@DisplayName("Rx submission receipt content fingerprint")
class RxSubmissionReceiptServiceUnitTest extends CarlosUnitTestBase {

    private static final String DRAFT = RxSubmissionReceiptService.fingerprint(
            Map.of("123", "11111111-2222-3333-4444-555555555555"));
    private static final String OTHER_DRAFT = RxSubmissionReceiptService.fingerprint(
            Map.of("123", "99999999-2222-3333-4444-555555555555"));

    private static Map<String, String[]> fields(String... namesAndValues) {
        Map<String, String[]> fields = new LinkedHashMap<>();
        for (int index = 0; index < namesAndValues.length; index += 2) {
            fields.put(namesAndValues[index], new String[] {namesAndValues[index + 1]});
        }
        return fields;
    }

    @Test
    void shouldReturnTheSameToken_forTheSameFieldsInAnyOrder() {
        String first = RxSubmissionReceiptService.contentFingerprint(DRAFT,
                fields("drugName_123", "Amoxicillin", "instructions_123", "1 tab PO OD"));
        String second = RxSubmissionReceiptService.contentFingerprint(DRAFT,
                fields("instructions_123", "1 tab PO OD", "drugName_123", "Amoxicillin"));

        assertThat(first).matches("[a-f0-9]{64}").isEqualTo(second);
    }

    @Test
    void shouldReturnAnotherToken_whenAFieldValueChanges() {
        String saved = RxSubmissionReceiptService.contentFingerprint(DRAFT, fields("instructions_123", "1 tab PO OD"));

        assertThat(RxSubmissionReceiptService.contentFingerprint(DRAFT, fields("instructions_123", "2 tabs PO OD")))
                .isNotEqualTo(saved);
        // Surrounding whitespace is part of what was posted; the comparison never guesses.
        assertThat(RxSubmissionReceiptService.contentFingerprint(DRAFT, fields("instructions_123", "1 tab PO OD ")))
                .isNotEqualTo(saved);
    }

    @Test
    void shouldReturnAnotherToken_whenAFieldIsAddedOrRemoved() {
        String saved = RxSubmissionReceiptService.contentFingerprint(DRAFT,
                fields("drugName_123", "Amoxicillin", "repeats_123", ""));

        assertThat(RxSubmissionReceiptService.contentFingerprint(DRAFT, fields("drugName_123", "Amoxicillin")))
                .isNotEqualTo(saved);
        assertThat(RxSubmissionReceiptService.contentFingerprint(DRAFT,
                fields("drugName_123", "Amoxicillin", "repeats_123", "", "longTerm_123", "on"))).isNotEqualTo(saved);
    }

    @Test
    void shouldReturnAnotherToken_whenTextMovesBetweenFieldsOrValues() {
        // Length framing: the same characters split differently are different submissions.
        assertThat(RxSubmissionReceiptService.contentFingerprint(DRAFT, fields("a", "bc", "d", "")))
                .isNotEqualTo(RxSubmissionReceiptService.contentFingerprint(DRAFT, fields("a", "b", "cd", "")));
        assertThat(RxSubmissionReceiptService.contentFingerprint(DRAFT, Map.of("a", new String[] {"b", "c"})))
                .isNotEqualTo(RxSubmissionReceiptService.contentFingerprint(DRAFT, Map.of("a", new String[] {"bc"})))
                .isNotEqualTo(RxSubmissionReceiptService.contentFingerprint(DRAFT, Map.of("a", new String[] {"c", "b"})));
    }

    @Test
    void shouldReturnAnotherToken_forTheSameFieldsOfAnotherDraft() {
        Map<String, String[]> same = fields("instructions_123", "1 tab PO OD");

        assertThat(RxSubmissionReceiptService.contentFingerprint(DRAFT, same))
                .isNotEqualTo(RxSubmissionReceiptService.contentFingerprint(OTHER_DRAFT, same))
                // Never the draft's own receipt key, even for a request that carried no fields.
                .isNotEqualTo(DRAFT);
        assertThat(RxSubmissionReceiptService.contentFingerprint(DRAFT, Map.of())).matches("[a-f0-9]{64}").isNotEqualTo(DRAFT);
    }

    @Test
    void shouldTreatMissingValuesAsEmpty_forAFieldWithoutValues() {
        Map<String, String[]> absent = new LinkedHashMap<>();
        absent.put("comment_123", null);
        Map<String, String[]> nullValue = new LinkedHashMap<>();
        nullValue.put("comment_123", new String[] {null});

        assertThat(RxSubmissionReceiptService.contentFingerprint(DRAFT, absent))
                .isEqualTo(RxSubmissionReceiptService.contentFingerprint(DRAFT, Map.of("comment_123", new String[0])));
        assertThat(RxSubmissionReceiptService.contentFingerprint(DRAFT, nullValue))
                .isEqualTo(RxSubmissionReceiptService.contentFingerprint(DRAFT, fields("comment_123", "")));
    }

    @Test
    void shouldReturnNoToken_withoutAValidDraftToken() {
        assertThat(RxSubmissionReceiptService.contentFingerprint(null, fields("a", "b"))).isNull();
        assertThat(RxSubmissionReceiptService.contentFingerprint("not-a-receipt-token", fields("a", "b"))).isNull();
        assertThat(RxSubmissionReceiptService.contentFingerprint(DRAFT, null)).isNull();
    }
}
