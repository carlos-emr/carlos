/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.documentManager;

import io.github.carlos_emr.carlos.commn.model.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.owasp.encoder.Encode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * document.docdesc is a utf8mb4 VARCHAR(255), counted in code points, and the packaged database
 * cuts a longer value off at the end without a word. A description CARLOS builds itself is
 * shortened here instead, to exactly what fits, never inside a surrogate pair, keeping what
 * matters at the end (a file name's extension, a suffix such as " (annotated)").
 */
@Tag("unit")
@Tag("fast")
@DisplayName("DocumentDescription")
class DocumentDescriptionUnitTest {

    private static final int MAX = Document.DESCRIPTION_MAX_LENGTH;
    /** One character in the column, two Java chars. */
    private static final String EMOJI = "😀";

    private static int codePoints(String value) {
        return value.codePointCount(0, value.length());
    }

    private static boolean hasLoneSurrogate(String value) {
        return value.codePoints().anyMatch(cp -> cp >= Character.MIN_SURROGATE && cp <= Character.MAX_SURROGATE);
    }

    @Test
    void shouldReturnTheDescriptionUnchanged_whenItFitsTheColumn() {
        for (String fits : new String[] {"", "Lab report", "d".repeat(MAX), EMOJI.repeat(MAX)}) {
            assertThat(DocumentDescription.fit(fits)).isSameAs(fits);
        }
        assertThat(DocumentDescription.fit(null)).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"d", EMOJI})
    void shouldCutToTheColumnByCodePoints_whenTheDescriptionIsLonger(String character) {
        String shortened = DocumentDescription.fit(character.repeat(MAX + 40));

        assertThat(shortened).isEqualTo(character.repeat(MAX));
        assertThat(codePoints(shortened)).isEqualTo(MAX);
    }

    @Test
    void shouldNeverSplitASurrogatePair_whenTheJavaCharLimitFallsInsideOne() {
        // A cut at Java char 255 would separate the 128th emoji's two halves.
        String shortened = DocumentDescription.fit("x" + EMOJI.repeat(300));

        assertThat(hasLoneSurrogate(shortened)).isFalse();
        assertThat(codePoints(shortened)).isEqualTo(MAX);
        assertThat(shortened).isEqualTo("x" + EMOJI.repeat(MAX - 1));
    }

    @Test
    void shouldDropTheBlankLeftAtTheCut_whenTheCutFallsAfterASpace() {
        String shortened = DocumentDescription.fit("a".repeat(MAX - 1) + " " + "b".repeat(20));

        assertThat(shortened).isEqualTo("a".repeat(MAX - 1));
    }

    @ParameterizedTest
    @ValueSource(strings = {"r", EMOJI})
    void shouldKeepTheSuffixWhole_whenTheBaseLeavesNoRoomForIt(String character) {
        String suffix = " (annotated)";

        String built = DocumentDescription.fitWithSuffix(character.repeat(MAX), suffix);

        assertThat(built).endsWith(suffix);
        assertThat(codePoints(built)).isEqualTo(MAX);
        assertThat(built).isEqualTo(character.repeat(MAX - suffix.length()) + suffix);
        assertThat(hasLoneSurrogate(built)).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"s", EMOJI})
    void shouldKeepTheWholeSuffix_whenTheSuffixAloneFillsTheColumn(String character) {
        String suffix = character.repeat(MAX);

        assertThat(DocumentDescription.fitWithSuffix("A", suffix)).isEqualTo(suffix);
    }

    @ParameterizedTest
    @ValueSource(strings = {"s", EMOJI})
    void shouldKeepTheStartOfTheSuffix_whenTheSuffixAloneIsLongerThanTheColumn(String character) {
        String built = DocumentDescription.fitWithSuffix("A", character.repeat(MAX + 1));

        assertThat(built).isEqualTo(character.repeat(MAX));
        assertThat(hasLoneSurrogate(built)).isFalse();
    }

    @Test
    void shouldAppendTheSuffixUntouched_whenTheWholeDescriptionFits() {
        assertThat(DocumentDescription.fitWithSuffix("Consult letter", " (annotated)")).isEqualTo("Consult letter (annotated)");
        assertThat(DocumentDescription.fitWithSuffix(null, " (annotated)")).isEqualTo(" (annotated)");
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", EMOJI})
    void shouldKeepTheExtension_whenAFileNameIsLongerThanTheColumn(String character) {
        String shortened = DocumentDescription.fitFileName(character.repeat(300) + ".pdf");

        assertThat(shortened).isEqualTo(character.repeat(MAX - 4) + ".pdf");
        assertThat(codePoints(shortened)).isEqualTo(MAX);
    }

    @Test
    void shouldCutTheEnd_whenALongFileNameHasNoShortExtension() {
        assertThat(DocumentDescription.fitFileName("a".repeat(300))).isEqualTo("a".repeat(MAX));
        assertThat(DocumentDescription.fitFileName("a." + "b".repeat(300))).isEqualTo("a." + "b".repeat(MAX - 2));
        assertThat(DocumentDescription.fitFileName("report.pdf")).isEqualTo("report.pdf");
        assertThat(DocumentDescription.fitFileName(null)).isNull();
    }

    @Test
    void shouldStayWithinTheColumnAfterEncoding_withoutCuttingAnEntity() {
        // "&" stores as the five characters "&amp;": 51 whole entities fill 255, a 52nd would not fit.
        String stored = DocumentDescription.fitEncoded("&".repeat(300), Encode::forHtml);

        assertThat(stored).isEqualTo("&amp;".repeat(51));
        assertThat(codePoints(stored)).isLessThanOrEqualTo(MAX);
    }

    @Test
    void shouldEncodeTheWholeText_whenItsEncodingFits() {
        assertThat(DocumentDescription.fitEncoded("A & B", Encode::forHtml)).isEqualTo("A &amp; B");
        String emoji = EMOJI.repeat(300);
        String stored = DocumentDescription.fitEncoded(emoji, Encode::forHtml);
        assertThat(stored).isEqualTo(EMOJI.repeat(MAX));
    }
}
