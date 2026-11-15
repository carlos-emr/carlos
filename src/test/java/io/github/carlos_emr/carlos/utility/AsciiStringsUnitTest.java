/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.utility;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class AsciiStringsUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldMatchAsciiProtocolNames_withoutUnicodeCaseEquivalents() {
        assertThat(AsciiStrings.equalsIgnoreCase("X-Carlos-XML-Profile", "x-cArLoS-xMl-pRoFiLe")).isTrue();
        assertThat(AsciiStrings.equalsIgnoreCase("HTTPS", "https")).isTrue();
        assertThat(AsciiStrings.equalsIgnoreCase("example.INVALID", "EXAMPLE.invalid")).isTrue();
        assertThat(AsciiStrings.equalsIgnoreCase("X-Carlos-XML-Profile", "X-Carloſ-XML-Profile")).isFalse();
        assertThat(AsciiStrings.equalsIgnoreCase("i", "İ")).isFalse();
        assertThat(AsciiStrings.equalsIgnoreCase("i", "ı")).isFalse();
        assertThat(AsciiStrings.equalsIgnoreCase("é", "é")).isFalse();
        assertThat(AsciiStrings.equalsIgnoreCase(null, "a")).isFalse();
        assertThat(AsciiStrings.equalsIgnoreCase("a", null)).isFalse();
        assertThat(AsciiStrings.equalsIgnoreCase("a", "ab")).isFalse();
        assertThat(AsciiStrings.equalsIgnoreCase("ab", "ac")).isFalse();
    }
}
