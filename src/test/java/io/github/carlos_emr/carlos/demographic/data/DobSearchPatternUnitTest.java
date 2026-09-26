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
package io.github.carlos_emr.carlos.demographic.data;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Grammar contract for {@link DobSearchPattern}, the authoritative DOB search keyword parser.
 *
 * <p>Cases come from {@code demographic/dob-search-keywords.tsv}, which
 * {@code scripts/dob-search-keyword.test.js} also reads to hold the browser validator to the same
 * accept/reject decisions.</p>
 */
@Tag("unit")
@Tag("fast")
@Tag("demographic")
@DisplayName("DOB search keyword grammar")
class DobSearchPatternUnitTest {

    private static final String FIXTURE = "/demographic/dob-search-keywords.tsv";

    static Stream<Arguments> fixtureCases() throws IOException {
        try (InputStream in = DobSearchPatternUnitTest.class.getResourceAsStream(FIXTURE)) {
            assertThat(in).as("fixture %s on the test classpath", FIXTURE).isNotNull();
            List<String> lines = new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                .filter(line -> !line.isBlank() && !line.startsWith("#"))
                .toList();
            return lines.stream().map(line -> {
                String[] cols = line.split("\t", -1);
                String keyword = cols[0].replace("<SP>", " ");
                if ("INVALID".equals(cols[1])) {
                    return Arguments.of(keyword, null);
                }
                return Arguments.of(keyword, new DobSearchPattern(cols[1], cols[2], cols[3]));
            });
        }
    }

    @ParameterizedTest(name = "[{index}] \"{0}\" -> {1}")
    @MethodSource("fixtureCases")
    @DisplayName("should parse or reject each shared fixture keyword")
    void shouldMatchSharedFixture_forEachKeyword(String keyword, DobSearchPattern expected) {
        assertThat(DobSearchPattern.parse(keyword)).isEqualTo(Optional.ofNullable(expected));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"   ", "\t"})
    @DisplayName("should reject a missing or blank keyword")
    void shouldReturnEmpty_forBlankKeyword(String keyword) {
        assertThat(DobSearchPattern.parse(keyword)).isEmpty();
    }

    @Test
    @DisplayName("should keep a full date an exact match on all three columns")
    void shouldBindExactValues_forFullDate() {
        // Legacy code bound "1975%", "03%", "05%"; on the 4/2/2-character columns that is the
        // same set of rows as an exact match, which is what the parser now produces.
        assertThat(DobSearchPattern.parse("1975-03-05"))
            .contains(new DobSearchPattern("1975", "03", "05"));
    }

    @Test
    @DisplayName("should never emit the single-character LIKE wildcard")
    void shouldNotEmitUnderscore_forAnyAcceptedKeyword() throws IOException {
        fixtureCases()
            .map(arguments -> DobSearchPattern.parse((String) arguments.get()[0]))
            .flatMap(Optional::stream)
            .forEach(pattern -> assertThat(pattern.year() + pattern.month() + pattern.day())
                .doesNotContain("_"));
    }
}
