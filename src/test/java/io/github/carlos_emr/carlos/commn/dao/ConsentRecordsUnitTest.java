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
package io.github.carlos_emr.carlos.commn.dao;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.commn.model.Consent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@Tag("unit")
@DisplayName("ConsentRecords")
class ConsentRecordsUnitTest {

    private static Consent consent(int id, boolean optout, Long editedAt) {
        Consent consent = new Consent();
        ReflectionTestUtils.setField(consent, "id", id);
        consent.setOptout(optout);
        consent.setEditDate(editedAt == null ? null : new Date(editedAt));
        return consent;
    }

    /**
     * Asserts the choice in both input orders, so a rule that just takes the first or the last
     * record (what the chart used to do) fails one of them.
     */
    private static void assertDeciding(Consent expected, Consent... records) {
        List<Consent> forward = List.of(records);
        List<Consent> backward = new ArrayList<>(forward);
        Collections.reverse(backward);
        assertThat(ConsentRecords.effective(forward)).isSameAs(expected);
        assertThat(ConsentRecords.effective(backward)).isSameAs(expected);
    }

    @Test
    @DisplayName("should find nothing when there are no live records")
    void shouldReturnNull_whenThereAreNoRecords() {
        assertThat(ConsentRecords.effective(List.of())).isNull();
        assertThat(ConsentRecords.effective(null)).isNull();
    }

    @Test
    @DisplayName("should let an older opt-out win over a newer opt-in")
    void shouldChooseTheOptOut_whenRecordsDisagree() {
        Consent olderOptOut = consent(1, true, 1_000L);
        Consent newerOptIn = consent(2, false, 2_000L);

        assertDeciding(olderOptOut, newerOptIn, olderOptOut);
    }

    @Test
    @DisplayName("should choose the most recent opt-out when several opt out")
    void shouldChooseTheNewestOptOut_whenSeveralOptOut() {
        Consent older = consent(1, true, 1_000L);
        Consent newer = consent(2, true, 3_000L);

        assertDeciding(newer, older, consent(3, false, 5_000L), newer);
    }

    @Test
    @DisplayName("should choose the most recently edited record when none opts out")
    void shouldChooseTheNewest_whenNoneOptsOut() {
        Consent older = consent(1, false, 1_000L);
        Consent newer = consent(2, false, 2_000L);

        assertDeciding(newer, older, newer);
    }

    private static Consent explicit(Consent consent) {
        consent.setExplicit(true);
        return consent;
    }

    @Test
    @DisplayName("should let an older explicit opt-in win over a newer implied one")
    void shouldChooseTheExplicitOptIn_whenNewerOneIsImplied() {
        // An old chart: the patient confirmed in person, then an import or a routine save added
        // an implied record. The patient's own confirmation still decides.
        Consent olderExplicit = explicit(consent(1, false, 1_000L));
        Consent newerImplied = consent(2, false, 2_000L);

        assertDeciding(olderExplicit, newerImplied, olderExplicit);
    }

    @Test
    @DisplayName("should let an implied opt-out win over an explicit opt-in")
    void shouldChooseTheOptOut_whenOptInIsExplicit() {
        Consent impliedOptOut = consent(1, true, 1_000L);
        Consent explicitOptIn = explicit(consent(2, false, 2_000L));

        assertDeciding(impliedOptOut, explicitOptIn, impliedOptOut);
    }

    @Test
    @DisplayName("should choose the most recent explicit record when several are explicit")
    void shouldChooseTheNewestExplicit_whenSeveralAreExplicit() {
        Consent older = explicit(consent(1, false, 1_000L));
        Consent newer = explicit(consent(2, false, 2_000L));

        assertDeciding(newer, older, consent(3, false, 5_000L), newer);
    }

    @Test
    @DisplayName("should rank an undated record as the oldest, and break date ties by the higher id")
    void shouldRankUndatedOldest_andBreakTiesById() {
        Consent undated = consent(9, false, null);
        Consent dated = consent(1, false, 1_000L);
        assertDeciding(dated, undated, dated);

        Consent lowerId = consent(4, false, 1_000L);
        Consent higherId = consent(5, false, 1_000L);
        assertDeciding(higherId, lowerId, higherId);
    }

    @Test
    @DisplayName("should keep one deciding record per consent type")
    void shouldKeepTheDecidingRecord_forEachConsentType() {
        Consent olderOptOut = consent(1, true, 1_000L);
        Consent newerOptIn = consent(2, false, 2_000L);
        Consent otherType = consent(3, false, 1_500L);
        olderOptOut.setConsentTypeId(10);
        newerOptIn.setConsentTypeId(10);
        otherType.setConsentTypeId(20);

        assertThat(ConsentRecords.effectivePerType(List.of(olderOptOut, newerOptIn, otherType)))
                .containsExactly(olderOptOut, otherType);
        assertThat(ConsentRecords.effectivePerType(List.of(otherType, newerOptIn, olderOptOut)))
                .containsExactly(otherType, olderOptOut);
        assertThat(ConsentRecords.effectivePerType(List.of())).isEmpty();
        assertThat(ConsentRecords.effectivePerType(null)).isEmpty();
    }
}
