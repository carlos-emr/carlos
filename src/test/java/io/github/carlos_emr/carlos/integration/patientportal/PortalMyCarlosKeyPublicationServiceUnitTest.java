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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** No scheduler is enabled and no HTTP or document signer is used by these tests. */
@Tag("unit")
@Tag("patient-portal")
class PortalMyCarlosKeyPublicationServiceUnitTest extends CarlosUnitTestBase {
    private PortalMyCarlosKeyInventoryService inventory;
    private PatientPortalService clientA;
    private PatientPortalService clientB;
    private AtomicReference<PatientPortalService> client;
    private AtomicBoolean enabled;
    private AtomicBoolean portalOn;
    private AtomicInteger keyReads;
    private AtomicInteger clientReads;
    private AtomicInteger configurationReads;
    private PortalMyCarlosKeyPublicationService publisher;
    private PortalMyCarlosPublicationState.Claim claim;
    private PortalJobAssertionSigner signer;

    @BeforeEach
    void setUp() {
        inventory = mock(PortalMyCarlosKeyInventoryService.class);
        clientA = mock(PatientPortalService.class);
        clientB = mock(PatientPortalService.class);
        when(clientA.clinicKeysPublicationTarget()).thenReturn(TARGET_A);
        when(clientB.clinicKeysPublicationTarget()).thenReturn(TARGET_B);
        client = new AtomicReference<>(clientA);
        enabled = new AtomicBoolean(true);
        portalOn = new AtomicBoolean(true);
        keyReads = new AtomicInteger();
        clientReads = new AtomicInteger();
        configurationReads = new AtomicInteger();
        claim = new PortalMyCarlosPublicationState.Claim(
                catalogue(), TARGET_A, TOKEN_A, NOW.plusSeconds(120), 0);
        when(inventory.claim(TARGET_A, TOKEN_A, NOW, PortalMyCarlosKeyPublicationService.LEASE))
                .thenReturn(Optional.of(claim));
        signer = signer(PortalJob.KEY_PUBLISH);
        publisher = new PortalMyCarlosKeyPublicationService(inventory, enabled::get, () -> {
            configurationReads.incrementAndGet();
            return portalOn.get();
        }, () -> {
            clientReads.incrementAndGet();
            return client.get();
        }, job -> {
            keyReads.incrementAndGet();
            return Optional.empty();
        }, Clock.fixed(NOW, ZoneOffset.UTC), () -> TOKEN_A, () -> 0);
    }

    private static PortalJobAssertionSigner signer(PortalJob job) {
        return PortalJobAssertionSigner.from(new PortalJobKey(
                job, PortalSecret.of(PortalTestKeys.JOB_PRIVATE_KEY), "FAKE-job-key"));
    }

    @Test
    void shouldShortCircuitWhenDefaultOff_beforeConfigurationKeysCatalogueOrClientAreRead() {
        enabled.set(false);
        publisher.start();
        publisher.runOnce(null);
        publisher.close();
        assertThat(keyReads).hasValue(0);
        assertThat(clientReads).hasValue(0);
        assertThat(configurationReads).hasValue(0);
        verifyNoInteractions(inventory);
    }

    @Test
    void shouldRemainOffForMissingPortalOrJobKey_withoutCreatingANetworkAttempt() {
        portalOn.set(false);
        publisher.start();
        publisher.runOnce(null);
        assertThat(keyReads).hasValue(0);
        portalOn.set(true);
        publisher.start(); // The injected key source returns empty; no executor is created.
        publisher.close();
        assertThat(keyReads).hasValue(1);
        assertThat(clientReads).hasValue(0);
        verifyNoInteractions(inventory);
    }

    @Test
    void shouldAvoidTransportWithoutADurableClaim_andRejectOtherJobIdentities() {
        when(inventory.claim(TARGET_A, TOKEN_A, NOW, PortalMyCarlosKeyPublicationService.LEASE))
                .thenReturn(Optional.empty());
        publisher.runOnce(signer);
        verify(clientA, never()).publishClinicKeys(any(), any());
        assertThatThrownBy(() -> publisher.runOnce(signer(PortalJob.BOOKING_SYNC)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldAcknowledgeTheCompleteSnapshot_afterRecheckingTheEffectiveTarget() {
        var response = acknowledgement(claim);
        when(clientA.publishClinicKeys(claim.inventory().publication(), signer)).thenAnswer(call -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            client.set(clientB); // Configuration was repointed while A's request was in flight.
            return response;
        });
        publisher.runOnce(signer);
        verify(inventory).acknowledge(claim, TARGET_B, response, NOW);
        verify(inventory, never()).acknowledge(claim, TARGET_A, response, NOW);
        verify(clientB, never()).publishClinicKeys(any(), any());
    }

    @Test
    void shouldLeaveTimeoutPending_withBoundedPersistentRetryBookkeeping() {
        when(clientA.publishClinicKeys(claim.inventory().publication(), signer))
                .thenThrow(new IllegalStateException("FAKE transport failure, do not log this message"));
        publisher.runOnce(signer);
        verify(inventory).failed(claim, TARGET_A, NOW, Duration.ofSeconds(30));
        verify(inventory, never()).acknowledge(any(), any(), any(), any());
    }

    @Test
    void shouldNotAcknowledgeAfterDisablement_evenIfTheAlreadySentAttemptSucceeded() {
        when(clientA.publishClinicKeys(claim.inventory().publication(), signer)).thenAnswer(call -> {
            enabled.set(false);
            return acknowledgement(claim);
        });
        publisher.runOnce(signer);
        verify(inventory, never()).acknowledge(any(), any(), any(), any());
        verify(inventory, never()).failed(any(), any(), any(), any());
        assertThat(clientReads).hasValue(1);
    }

    @Test
    void shouldRefuseAnOuterTransaction_beforeClaimingOrPerformingHTTP() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            assertThatThrownBy(() -> publisher.runOnce(signer)).isInstanceOf(IllegalStateException.class)
                    .hasMessage("clinic key publication must run outside a database transaction");
            verifyNoInteractions(inventory);
            assertThat(clientReads).hasValue(0);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void shouldBoundExponentialRetryAndJitter_withoutOverflowOrUnboundedScheduling() {
        for (int failures = 0; failures <= 30; failures++) {
            for (double sample : new double[]{0, 0.5, 1}) {
                assertThat(PortalMyCarlosKeyPublicationService.retryDelay(failures, sample))
                        .isBetween(Duration.ofSeconds(30), Duration.ofMinutes(15));
            }
        }
        assertThat(PortalMyCarlosKeyPublicationService.retryDelay(30, 1)).isEqualTo(Duration.ofMinutes(15));
        assertThatThrownBy(() -> PortalMyCarlosKeyPublicationService.retryDelay(-1, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PortalMyCarlosKeyPublicationService.retryDelay(31, 0))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PortalMyCarlosKeyPublicationService.retryDelay(1, Double.NaN))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
