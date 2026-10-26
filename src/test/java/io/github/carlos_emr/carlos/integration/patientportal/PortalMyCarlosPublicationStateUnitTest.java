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
package io.github.carlos_emr.carlos.integration.patientportal;

import static io.github.carlos_emr.carlos.integration.patientportal.PortalMyCarlosKeyTestFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("unit")
@Tag("patient-portal")
class PortalMyCarlosPublicationStateUnitTest {
    @Test
    void shouldBindLeaseToTargetAndSnapshot_rejectingLateAcknowledgements() {
        var catalogue = catalogue();
        var state = PortalMyCarlosPublicationState.pending(catalogue, TARGET_A)
                .leased(TOKEN_A, NOW, Duration.ofMinutes(2));
        var claim = new PortalMyCarlosPublicationState.Claim(
                catalogue, TARGET_A, TOKEN_A, state.leaseExpiresAt(), 0);
        assertThat(state.owns(claim, TARGET_A, NOW.plusSeconds(1))).isTrue();
        assertThat(state.owns(claim, TARGET_B, NOW.plusSeconds(1))).isFalse();
        assertThat(state.owns(claim, TARGET_A, state.leaseExpiresAt())).isFalse();
        assertThat(state.matches(catalogue.replaceComplete(1, List.of(FIRST, SECOND)), TARGET_A)).isFalse();
        assertThat(state.claimableAt(NOW.plusSeconds(1))).isFalse();
        assertThat(state.claimableAt(state.leaseExpiresAt())).isTrue();
    }

    @Test
    void shouldMakeTargetRepointPending_withoutChangingCatalogueGeneration() {
        var catalogue = catalogue();
        var publishedA = PortalMyCarlosPublicationState.pending(catalogue, TARGET_A)
                .leased(TOKEN_A, NOW, Duration.ofMinutes(2)).acknowledged(NOW);
        assertThat(publishedA.matches(catalogue, TARGET_A)).isTrue();
        assertThat(publishedA.matches(catalogue, TARGET_B)).isFalse();
        var pendingB = PortalMyCarlosPublicationState.pending(catalogue, TARGET_B);
        assertThat(pendingB.generation()).isEqualTo(publishedA.generation());
        assertThat(pendingB.published()).isFalse();
        assertThat(pendingB.claimableAt(NOW)).isTrue();
    }

    @Test
    void shouldRetainRetryDeadlineAndClearLease_untilTheBoundedNextAttempt() {
        var state = PortalMyCarlosPublicationState.pending(catalogue(), TARGET_A)
                .leased(TOKEN_A, NOW, Duration.ofMinutes(2))
                .retryAfter(NOW.plusSeconds(1), Duration.ofSeconds(30));
        assertThat(state.leaseToken()).isNull();
        assertThat(state.failures()).isEqualTo(1);
        assertThat(state.claimableAt(NOW.plusSeconds(30))).isFalse();
        assertThat(state.claimableAt(NOW.plusSeconds(31))).isTrue();
        assertThatThrownBy(() -> state.retryAfter(NOW, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> state.retryAfter(NOW, Duration.ofMinutes(16)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldKeepRevocationPublicationIndependent_ofAnyDocumentSigner() {
        var revoked = PortalMyCarlosKeyInventory.create(CLINIC, List.of(revoked(FIRST)));
        var state = PortalMyCarlosPublicationState.pending(revoked, TARGET_A);
        assertThat(state.claimableAt(NOW)).isTrue();
        assertThatThrownBy(() -> state.acknowledged(NOW)).isInstanceOf(IllegalArgumentException.class);
        assertThat(state.leased(TOKEN_A, NOW, Duration.ofMinutes(2)).acknowledged(NOW).published()).isTrue();
    }

    @Test
    void shouldRefuseUnrelatedOrCredentialedTargets_withoutNormalizingThemIntoTheEndpoint() {
        for (String target : List.of(
                "https://portal.example/another-route",
                "https://user:password@portal.example" + PortalMyCarlosPublicationState.ENDPOINT_PATH,
                "https://portal.example" + PortalMyCarlosPublicationState.ENDPOINT_PATH + "?query=1",
                "http://portal.example" + PortalMyCarlosPublicationState.ENDPOINT_PATH)) {
            assertThatThrownBy(() -> new PortalMyCarlosPublicationState.Target(CLINIC, URI.create(target)))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }
}
