/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * Licensed under GPL version 2 or later.
 */
package io.github.carlos_emr.carlos.decision;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Direct coverage for the antenatal link rule. Both the save-time validator and
 * the HTML renderer delegate here, so this predicate is the whole boundary
 * between a configured link and {@code window.open()} — it is worth pinning on
 * its own rather than only through its two callers.
 */
@DisplayName("Antenatal risk link safety")
@Tag("unit")
@Tag("decision")
class AntenatalRiskLinkUnitTest {

    @ParameterizedTest
    @ValueSource(strings = {
            "http://resource.example.test/ob/riskinfo/stillbirth",
            "https://example.test/guidance",
            "HTTPS://example.test/guidance",
            "/guidance/nutrition",
            "ar1risk_99_12.htm",
            "riskinfo?id=12&type=ob",
            "#section"
    })
    @DisplayName("should accept HTTP, HTTPS and same-origin relative links")
    void shouldAccept_forAllowedLinks(String href) {
        assertThat(AntenatalRiskLink.isSafe(href)).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "javascript:alert(1)",
            "JaVaScRiPt:alert(1)",
            "data:text/html,<script>alert(1)</script>",
            "vbscript:msgbox(1)",
            "file:///etc/passwd",
            "ftp://example.test/x",
            "//evil.test/phish",
            "\\\\evil.test\\share",
            "/\\evil.test",
            "http:/\\/\\evil.test",
            " javascript:alert(1)",
            "java\tscript:alert(1)"
    })
    @DisplayName("should reject scripting schemes, off-origin and malformed links")
    void shouldReject_forUnsafeLinks(String href) {
        assertThat(AntenatalRiskLink.isSafe(href)).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   "})
    @DisplayName("should reject a missing or blank link")
    void shouldReject_forBlankLink(String href) {
        assertThat(AntenatalRiskLink.isSafe(href)).isFalse();
    }

    @Test
    @DisplayName("should accept a link of exactly the maximum length")
    void shouldAccept_forLinkAtMaximumLength() {
        String prefix = "https://example.test/";
        String atLimit = prefix + "a".repeat(AntenatalRiskLink.MAX_LENGTH - prefix.length());

        // The bound is exclusive (length() > MAX_LENGTH); pin it so an off-by-one
        // does not start rejecting links that were previously stored and valid.
        assertThat(atLimit).hasSize(AntenatalRiskLink.MAX_LENGTH);
        assertThat(AntenatalRiskLink.isSafe(atLimit)).isTrue();
    }

    @Test
    @DisplayName("should reject a link longer than the accepted maximum")
    void shouldReject_forOverlongLink() {
        String tooLong = "https://example.test/" + "a".repeat(AntenatalRiskLink.MAX_LENGTH);

        assertThat(tooLong.length()).isGreaterThan(AntenatalRiskLink.MAX_LENGTH);
        assertThat(AntenatalRiskLink.isSafe(tooLong)).isFalse();
    }
}
