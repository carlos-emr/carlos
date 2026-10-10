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
package io.github.carlos_emr.carlos.clinical.summary;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.*;

class ChartUpdateSuggestionsUnitTest extends CarlosUnitTestBase {
    private ChartUpdateSuggestions.Suggestion reminder(String text, String date) {
        return ChartUpdateSuggestions.suggest(new ChartUpdateProposals.Proposal("tickler", text), date, "101");
    }

    @ParameterizedTest
    @CsvSource({
        "Referral for neurology OP follow-up in 4 weeks,2026-09-28,2026-10-26",
        "Review in four weeks,2026-09-28,2026-10-26",
        "Review tomorrow for potential discharge,2026-09-28,2026-09-29",
        "Review in a month,2024-01-31,2024-02-29",
        "Recheck within 2 days,2026-12-31,2027-01-02",
        "Review in one year,2024-02-29,2025-02-28",
        "4 weeks,2026-09-28 14:00:00.0,2026-10-26",
        "Review in 4 weeks,2020-01-01,2020-01-29"
    })
    void shouldCalculateRelativeDate_fromDocumentDate(String passage, String anchor, String due) {
        var result = reminder(passage, anchor);
        assertThat(result.draft().dueDate()).isEqualTo(due);
        assertThat(result.dateBasis()).isEqualTo("relative");
        assertThat(result.anchor()).isEqualTo(anchor.substring(0, 10));
        assertThat(result.draft().assignee()).isEqualTo("101");
        assertThat(result.draft().text()).isEqualTo(passage);
    }

    @ParameterizedTest
    @ValueSource(strings = {"Review in two weeks if symptoms persist", "Review in 2-4 weeks",
        "Review in two or four weeks", "Review 4 weeks after discharge", "Review in 2 weeks and 3 days",
        "Symptoms for 4 weeks; arrange review", "Review last done 4 weeks ago", "Routine OP follow-up",
        "Review in 0 days", "0.5 weeks follow-up", "Two to four weeks follow-up", "Review in four weeks as needed", "No review in four weeks", "Review in 4 weeks from surgery",
        "Review on 2026-02-30", "Review on 2026-10-01 or 2026-10-02", "Surgery 2026-10-01; review later",
        "Advise routine GP follow-up 6 weeks post-surgery", "Review in six weeks postoperatively",
        "Review in 6 weeks following surgery", "Review in 2 weeks postop", "Review in 6 weeks since discharge",
        "Review within six weeks of surgery", "Review in 6 weeks at discharge", "Review in 6 weeks on admission",
        "Review 4 weeks later", "Recheck two weeks later"})
    void shouldLeaveDateEmpty_withAmbiguousOrConditionalTiming(String passage) {
        assertThat(reminder(passage, "2026-09-28").draft().dueDate()).isEmpty();
    }

    @Test void shouldUseExplicitDate_withoutDocumentDate() {
        var result = reminder("Review on 2026-10-26", "Not recorded");
        assertThat(result.draft().dueDate()).isEqualTo("2026-10-26");
        assertThat(result.dateBasis()).isEqualTo("explicit");
        assertThat(reminder("Review in four weeks", "Not recorded").draft().dueDate()).isEmpty();
        assertThat(reminder("Review tomorrow", "2026-02-30").draft().dueDate()).isEmpty();
    }

    @Test void shouldSuggestChartSection_fromHistoricalWording() {
        for (String text : List.of("History of asthma", "Medical history: appendectomy", "Past surgical history: appendectomy", "Resolved pneumonia")) {
            assertThat(ChartUpdateSuggestions.suggest(new ChartUpdateProposals.Proposal("history", text), "", "101")
                    .draft().destination()).isEqualTo("MedHistory");
        }
        // HPI headings describe the current presentation even though they start with "history of".
        for (String text : List.of("Suspected asthma", "History of present illness: cough for 3 days",
                "History of presenting complaint: chest pain", "History of presenting complaints: chest pain",
                "History of the presenting complaint: chest pain")) {
            assertThat(ChartUpdateSuggestions.suggest(new ChartUpdateProposals.Proposal("history", text), "", "101")
                    .draft().destination()).isEqualTo("Concerns");
        }
    }

    @Test void shouldPreserveClinicianEdits_andOriginalSuggestionAcrossRefresh() {
        var proposal = new ChartUpdateProposals.Proposal("tickler", "Review in four weeks");
        var snapshot = new ChartUpdateContext.Snapshot(42, 3001, "Synthetic", "", "2026-09-28", proposal.evidence(),
                "hash", "fingerprint", "10016", "1", List.of());
        var review = new ChartUpdateReview("101", snapshot, List.of(proposal), "Fixture", "101");
        assertThat(review.draft(proposal.key()).dueDate()).isEqualTo("2026-10-26");
        review.remember(proposal.key(), new ChartUpdateReview.Draft("Edited", "", "", ""));
        review.refresh("new fingerprint");
        assertThat(review.draft(proposal.key()).text()).isEqualTo("Edited");
        assertThat(review.draft(proposal.key()).dueDate()).isEmpty();
        assertThat(review.draft(proposal.key()).assignee()).isEmpty();
        assertThat(review.suggestion(proposal.key()).draft().dueDate()).isEqualTo("2026-10-26");
    }
    @Test void shouldSuggestTheSelectedSourceSection_withoutInventingNativeFields() {
        var social = ChartUpdateSuggestions.suggest(new ChartUpdateProposals.Proposal("history", "Non-smoker.", "SocHistory"), "2026-01-09", "101");
        assertThat(social.draft().destination()).isEqualTo("SocHistory");
        var allergy = ChartUpdateSuggestions.suggest(new ChartUpdateProposals.Proposal("review", "Allergies: none.", "Allergies"), "2026-01-09", "101");
        assertThat(allergy.draft().destination()).isEqualTo("Allergies");
        assertThat(allergy.draft().dueDate()).isEmpty();
        assertThat(allergy.draft().assignee()).isEmpty();
    }
}
