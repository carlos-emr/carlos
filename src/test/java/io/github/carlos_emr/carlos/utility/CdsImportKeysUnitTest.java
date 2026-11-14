/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.utility;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** The digest and keyword comparisons CDS import and export use in place of String.equals. */
@Tag("unit")
@DisplayName("CdsImportKeys comparisons")
class CdsImportKeysUnitTest {
    private static final String EMPTY =
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855";

    @Test
    @DisplayName("should answer as String.equals does for digests")
    void shouldAnswerAsEquals_forDigests() {
        assertThat(CdsImportKeys.sameDigest(EMPTY, new String(EMPTY.toCharArray()))).isTrue();
        assertThat(CdsImportKeys.sameDigest(EMPTY, EMPTY.substring(0, 63) + "4")).isFalse();
        assertThat(CdsImportKeys.sameDigest(EMPTY, EMPTY.substring(0, 63))).isFalse();
        assertThat(CdsImportKeys.sameDigest(EMPTY, EMPTY.toUpperCase(java.util.Locale.ROOT)))
                .isFalse();
        assertThat(CdsImportKeys.sameDigest("", "")).isTrue();
    }

    @Test
    @DisplayName("should treat a missing right-hand digest as unequal and a missing left as a fault")
    void shouldKeepNullBehaviourOfEquals_forMissingDigests() {
        assertThat(CdsImportKeys.sameDigest(EMPTY, null)).isFalse();
        assertThatThrownBy(() -> CdsImportKeys.sameDigest(null, EMPTY))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    @DisplayName("should keep strings apart that a charset encoder would merge")
    void shouldKeepDifferentTextApart_forUnpairedSurrogates() {
        // Both would encode to '?' in UTF-8; String.equals tells them apart and so must this.
        assertThat(CdsImportKeys.sameDigest("\ud800", "\ud801")).isFalse();
        assertThat(CdsImportKeys.sameDigest("\ud800", "?")).isFalse();
        assertThat(CdsImportKeys.sameDigest("\u00e9", "e\u0301")).isFalse();
    }

    @Test
    @DisplayName("should match an ASCII keyword in any letter case, whatever the default locale")
    void shouldMatchKeywordInAnyAsciiCase_underTurkishDefaultLocale() {
        java.util.Locale original = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.of("tr", "TR"));
            assertThat(CdsImportKeys.asciiKeyword("active", "active")).isTrue();
            assertThat(CdsImportKeys.asciiKeyword("active", "ACTIVE")).isTrue();
            assertThat(CdsImportKeys.asciiKeyword("active", "Active")).isTrue();
            assertThat(CdsImportKeys.asciiKeyword("yes", "YES")).isTrue();
            assertThat(CdsImportKeys.asciiKeyword("active", "inactive")).isFalse();
            assertThat(CdsImportKeys.asciiKeyword("active", " active")).isFalse();
            assertThat(CdsImportKeys.asciiKeyword("active", null)).isFalse();
        } finally {
            java.util.Locale.setDefault(original);
        }
    }

    @Test
    @DisplayName("should not take a letter that only folds to ASCII for the ASCII letter")
    void shouldRefuseLookAlikeLetters_forAsciiKeyword() {
        // String.equalsIgnoreCase accepts each of these: a dotless i, a dotted capital I, a long
        // s and the Kelvin sign fold to i, i, s and k.
        assertThat("active".equalsIgnoreCase("act\u0131ve")).isTrue();
        assertThat(CdsImportKeys.asciiKeyword("active", "act\u0131ve")).isFalse();
        assertThat(CdsImportKeys.asciiKeyword("active", "ACT\u0130VE")).isFalse();
        assertThat(CdsImportKeys.asciiKeyword("yes", "ye\u017f")).isFalse();
        assertThat(CdsImportKeys.asciiKeyword("ok", "o\u212a")).isFalse();
    }
}
