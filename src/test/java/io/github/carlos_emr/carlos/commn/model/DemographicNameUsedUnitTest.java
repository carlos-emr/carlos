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
package io.github.carlos_emr.carlos.commn.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What a Master Record save stores in pref_name for a Name Used (issue #5128): the value when it
 * fits the 30-character column, otherwise its first 30 characters, cut at whole characters.
 *
 * @since 2026-10-21
 */
@DisplayName("Demographic pref_name for a Name Used")
@Tag("unit")
@Tag("demographic")
class DemographicNameUsedUnitTest {

    // U+1F600, one character stored as a surrogate pair of two Java chars.
    private static final String SMILE = new String(Character.toChars(0x1F600));

    @Test
    void shouldKeepTheValue_whenItFitsTheColumn() {
        String thirty = "N".repeat(Demographic.PREF_NAME_MAX_LENGTH);

        assertThat(Demographic.prefNameForNameUsed(thirty)).isEqualTo(thirty);
        assertThat(Demographic.prefNameForNameUsed("ALEX")).isEqualTo("ALEX");
    }

    @Test
    void shouldKeepTheFirstThirtyCharacters_whenTheValueIsLonger() {
        String value = "A".repeat(30) + "B";

        assertThat(Demographic.prefNameForNameUsed(value)).isEqualTo("A".repeat(30));
        assertThat(Demographic.prefNameForNameUsed("C".repeat(Demographic.ALIAS_MAX_LENGTH)))
                .isEqualTo("C".repeat(30));
    }

    @Test
    void shouldStoreAnEmptyValue_whenNoNameUsedWasSent() {
        // pref_name is NOT NULL; a disabled Name Used box sends no value at all.
        assertThat(Demographic.prefNameForNameUsed(null)).isEmpty();
    }

    @Test
    void shouldCountCharactersNotChars_whenTheValueHasASurrogatePair() {
        String thirtyCharacters = "A".repeat(29) + SMILE;

        assertThat(thirtyCharacters).hasSize(31);
        assertThat(Demographic.prefNameForNameUsed(thirtyCharacters)).isEqualTo(thirtyCharacters);
    }

    @Test
    void shouldNeverSplitASurrogatePair_whenCuttingAtTheColumn() {
        String value = "A".repeat(29) + SMILE + SMILE;

        String stored = Demographic.prefNameForNameUsed(value);

        assertThat(stored).isEqualTo("A".repeat(29) + SMILE);
        assertThat(Character.isHighSurrogate(stored.charAt(stored.length() - 1))).isFalse();
    }
}
