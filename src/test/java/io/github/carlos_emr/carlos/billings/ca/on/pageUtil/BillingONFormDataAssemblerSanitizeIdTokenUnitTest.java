/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.billings.ca.on.pageUtil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the package-private
 * {@link BillingONFormDataAssembler#sanitizeIdToken(String)} helper.
 *
 * <p>The helper is the gate against malformed {@code ctl_billservice} rows
 * producing invalid HTML ids that break the inline JS lookup-by-id pattern
 * the billing form uses. Regression-armor for future changes to the
 * safe-character set or the replacement character.</p>
 *
 * @since 2026-04-25
 */
@DisplayName("BillingONFormDataAssembler.sanitizeIdToken")
@Tag("unit")
@Tag("billing")
class BillingONFormDataAssemblerSanitizeIdTokenUnitTest {

    @Test
    @DisplayName("should return empty string for null input")
    void shouldReturnEmptyString_forNullInput() {
        assertThat(BillingONFormDataAssembler.sanitizeIdToken(null)).isEqualTo("");
    }

    @Test
    @DisplayName("should leave alphanumeric codes unchanged")
    void shouldLeaveCodeUnchanged_whenAlphanumeric() {
        assertThat(BillingONFormDataAssembler.sanitizeIdToken("0010")).isEqualTo("0010");
        assertThat(BillingONFormDataAssembler.sanitizeIdToken("PRI")).isEqualTo("PRI");
        assertThat(BillingONFormDataAssembler.sanitizeIdToken("ABC123")).isEqualTo("ABC123");
    }

    @Test
    @DisplayName("should preserve hyphen and underscore as id-safe characters")
    void shouldPreserveHyphenAndUnderscore_whenInputContainsThem() {
        assertThat(BillingONFormDataAssembler.sanitizeIdToken("safe-id_1")).isEqualTo("safe-id_1");
        assertThat(BillingONFormDataAssembler.sanitizeIdToken("_-_-_")).isEqualTo("_-_-_");
    }

    @Test
    @DisplayName("should replace whitespace with underscore")
    void shouldReplaceWhitespace_withUnderscore() {
        assertThat(BillingONFormDataAssembler.sanitizeIdToken("FOO BAR")).isEqualTo("FOO_BAR");
        assertThat(BillingONFormDataAssembler.sanitizeIdToken("A\tB")).isEqualTo("A_B");
        assertThat(BillingONFormDataAssembler.sanitizeIdToken(" leading"))
                .isEqualTo("_leading");
        assertThat(BillingONFormDataAssembler.sanitizeIdToken("trailing "))
                .isEqualTo("trailing_");
    }

    @Test
    @DisplayName("should replace HTML-significant characters with underscore")
    void shouldReplaceHtmlSignificantChars_withUnderscore() {
        assertThat(BillingONFormDataAssembler.sanitizeIdToken("A&B<C>")).isEqualTo("A_B_C_");
        assertThat(BillingONFormDataAssembler.sanitizeIdToken("\"quoted\"")).isEqualTo("_quoted_");
        assertThat(BillingONFormDataAssembler.sanitizeIdToken("a'b")).isEqualTo("a_b");
    }

    @Test
    @DisplayName("should replace path / URL-significant characters with underscore")
    void shouldReplaceUriSignificantChars_withUnderscore() {
        assertThat(BillingONFormDataAssembler.sanitizeIdToken("a/b")).isEqualTo("a_b");
        assertThat(BillingONFormDataAssembler.sanitizeIdToken("a?b=c"))
                .isEqualTo("a_b_c");
        assertThat(BillingONFormDataAssembler.sanitizeIdToken("a;jsessionid=x"))
                .isEqualTo("a_jsessionid_x");
    }

    @Test
    @DisplayName("should return empty string for empty input")
    void shouldReturnEmptyString_forEmptyInput() {
        assertThat(BillingONFormDataAssembler.sanitizeIdToken("")).isEqualTo("");
    }

    @Test
    @DisplayName("should produce well-formed token even when every char is replaced")
    void shouldProduceWellFormedToken_whenAllCharsAreReplaced() {
        // The output is still a valid HTML id (no spaces); each unsafe char
        // becomes an underscore one-for-one. Important for JS lookup-by-id
        // round-trip with the JSP loop variable.
        assertThat(BillingONFormDataAssembler.sanitizeIdToken("!@#$%"))
                .isEqualTo("_____");
    }

    @Test
    @DisplayName("should replace non-ASCII letters with underscore")
    void shouldReplaceNonAsciiLetters_withUnderscore() {
        // Surprising but defensible for a localization-touchy clinic. The
        // regex matches only [A-Za-z0-9_-]; non-ASCII is replaced one
        // codepoint per `_`. Locks the behavior so a future regex tightening
        // (e.g. adding `\\p{L}`) is a deliberate change, not an accident.
        assertThat(BillingONFormDataAssembler.sanitizeIdToken("Café"))
                .isEqualTo("Caf_");
        assertThat(BillingONFormDataAssembler.sanitizeIdToken("mañana"))
                .isEqualTo("ma_ana");
        // Each non-BMP CJK codepoint counts as ≥1 char in Java's regex; the
        // exact replacement count depends on UTF-16 char-by-char iteration.
        // Keep the assertion shape tolerant.
        assertThat(BillingONFormDataAssembler.sanitizeIdToken("日本"))
                .matches("_+");
    }

    @Test
    @DisplayName("should preserve hyphen adjacent to a replaced char")
    void shouldPreserveHyphen_adjacentToReplacedChar() {
        // Verifies the hyphen-in-character-class isn't accidentally treated
        // as a range when something gets replaced next to it. "a- b" should
        // sanitize to "a-_b" (hyphen kept, space replaced) rather than
        // "a__b" or "a-b".
        assertThat(BillingONFormDataAssembler.sanitizeIdToken("a- b"))
                .isEqualTo("a-_b");
        assertThat(BillingONFormDataAssembler.sanitizeIdToken("-a-"))
                .isEqualTo("-a-");
        assertThat(BillingONFormDataAssembler.sanitizeIdToken("a -b"))
                .isEqualTo("a_-b");
    }
}
