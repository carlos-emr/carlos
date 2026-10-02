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

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import static org.assertj.core.api.Assertions.*;

class ChartUpdateReviewUnitTest {
    private final ChartUpdateProposals.Proposal proposal = new ChartUpdateProposals.Proposal("history", "Suspected asthma.");
    private ChartUpdateReview review(int patient, int document) {
        return new ChartUpdateReview("101", new ChartUpdateContext.Snapshot(document, patient, "Synthetic patient", "", "", proposal.evidence(),
                "source-hash", "fingerprint", "10016", "1", List.of()), List.of(proposal));
    }

    @Test void shouldScopeReceiptToPatient_acrossSessionsAndIdenticalImports() {
        var first = review(3001, 42);
        var imported = review(3001, 43);
        assertThat(first.getToken()).isNotEqualTo(imported.getToken());
        assertThat(first.receiptKey(proposal.key())).isEqualTo(imported.receiptKey(proposal.key()));
        assertThat(first.receiptKey(proposal.key())).isNotEqualTo(review(3002, 42).receiptKey(proposal.key()));
    }

    @Test void shouldRejectReview_whenExpiredOrActorOrTokenMismatch() {
        var review = review(3001, 42);
        assertThatThrownBy(() -> review.authorize("102", review.getToken())).isInstanceOf(SecurityException.class);
        assertThatThrownBy(() -> review.authorize("101", "forged")).isInstanceOf(SecurityException.class);
        review.authorize("101", review.getToken());
        ReflectionTestUtils.setField(review, "expiresAt", 0L);
        assertThatThrownBy(() -> review.authorize("101", review.getToken())).hasMessageContaining("expired");
    }

    @Test void shouldRejectUnknownProposals_withoutExposingMutableMaps() {
        var review = review(3001, 42);
        assertThatThrownBy(() -> review.record("forged", "saved")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> review.getProposals().clear()).isInstanceOf(UnsupportedOperationException.class);
        review.record(proposal.key(), "Dismissed");
        assertThat(review.getOutcomes()).containsEntry(proposal.key(), "Dismissed");
    }
}
