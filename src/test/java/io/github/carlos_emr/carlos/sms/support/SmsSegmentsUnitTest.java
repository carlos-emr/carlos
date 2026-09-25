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
package io.github.carlos_emr.carlos.sms.support;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("support")
class SmsSegmentsUnitTest {

    @Test
    @DisplayName("count fits 160 plain characters in one GSM-7 segment")
    void shouldFitOneSegment_withOneHundredSixtyGsmCharacters() {
        SmsSegments.Count count = SmsSegments.count("a".repeat(160));

        assertThat(count.encoding()).isEqualTo(SmsSegments.Encoding.GSM_7);
        assertThat(count.units()).isEqualTo(160);
        assertThat(count.segments()).isEqualTo(1);
        assertThat(count.singleSegmentLimit()).isEqualTo(160);
    }

    @Test
    @DisplayName("count splits GSM-7 text into 153-character parts once it exceeds one segment")
    void shouldUseMultipartSize_whenGsmTextExceedsOneSegment() {
        assertThat(SmsSegments.count("a".repeat(161)).segments()).isEqualTo(2);
        assertThat(SmsSegments.count("a".repeat(306)).segments()).isEqualTo(2);
        assertThat(SmsSegments.count("a".repeat(307)).segments()).isEqualTo(3);
    }

    @Test
    @DisplayName("count keeps French accents that GSM-7 supports in GSM-7")
    void shouldStayGsm_forAccentsInTheGsmAlphabet() {
        SmsSegments.Count count = SmsSegments.count("Rendez-vous prévu à 9 h, Ç");

        assertThat(count.encoding()).isEqualTo(SmsSegments.Encoding.GSM_7);
        assertThat(count.units()).isEqualTo(26);
    }

    @Test
    @DisplayName("count charges two units for each GSM-7 extension character")
    void shouldCountTwoUnits_forGsmExtensionCharacters() {
        SmsSegments.Count count = SmsSegments.count("{}[]~|^\\€");

        assertThat(count.encoding()).isEqualTo(SmsSegments.Encoding.GSM_7);
        assertThat(count.units()).isEqualTo(18);
    }

    @Test
    @DisplayName("count rejects one segment when extension characters push GSM-7 text past 160 units")
    void shouldNeedTwoSegments_whenExtensionCharactersExceedTheLimit() {
        SmsSegments.Count count = SmsSegments.count("a".repeat(159) + "€");

        assertThat(count.units()).isEqualTo(161);
        assertThat(count.segments()).isEqualTo(2);
    }

    @ParameterizedTest
    @ValueSource(strings = {"hôpital", "Françoise", "It’s today", "9–10 am", "Ωmega 🙂"})
    @DisplayName("count switches to UCS-2 when any character is outside GSM-7")
    void shouldUseUcs2_whenTextHasCharacterOutsideGsm(String body) {
        SmsSegments.Count count = SmsSegments.count(body);

        assertThat(count.encoding()).isEqualTo(SmsSegments.Encoding.UCS_2);
        assertThat(count.singleSegmentLimit()).isEqualTo(70);
    }

    @Test
    @DisplayName("count fits 70 UCS-2 characters in one segment and uses 67-character parts beyond that")
    void shouldApplyUcs2Limits_withUnicodeText() {
        assertThat(SmsSegments.count("ô".repeat(70)).segments()).isEqualTo(1);
        assertThat(SmsSegments.count("ô".repeat(71)).segments()).isEqualTo(2);
        assertThat(SmsSegments.count("ô".repeat(134)).segments()).isEqualTo(2);
        assertThat(SmsSegments.count("ô".repeat(135)).segments()).isEqualTo(3);
    }

    @Test
    @DisplayName("count charges an emoji two UCS-2 units")
    void shouldCountTwoUnits_forEmoji() {
        assertThat(SmsSegments.count("🙂").units()).isEqualTo(2);
    }

    @Test
    @DisplayName("count treats null and empty text as zero segments")
    void shouldReturnZeroSegments_forEmptyText() {
        assertThat(SmsSegments.count(null).segments()).isZero();
        assertThat(SmsSegments.count("").segments()).isZero();
        assertThat(SmsSegments.count("").encoding()).isEqualTo(SmsSegments.Encoding.GSM_7);
    }
}
