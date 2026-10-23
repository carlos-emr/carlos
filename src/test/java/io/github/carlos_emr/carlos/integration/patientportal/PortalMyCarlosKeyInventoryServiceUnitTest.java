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

import io.github.carlos_emr.carlos.commn.dao.PropertyDao;
import io.github.carlos_emr.carlos.commn.dao.ProviderLabRoutingDao;
import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/** Exercises persisted JSON across service instances; real database locking needs its own integration proof. */
@Tag("unit")
@Tag("patient-portal")
class PortalMyCarlosKeyInventoryServiceUnitTest extends CarlosUnitTestBase {
    private PropertyDao properties;
    private ProviderLabRoutingDao coordination;
    private AtomicReference<Property> persisted;
    private List<Property> otherRows;
    private PortalMyCarlosKeyInventoryService service;

    @BeforeEach
    void setUp() {
        properties = mock(PropertyDao.class);
        coordination = mock(ProviderLabRoutingDao.class);
        persisted = new AtomicReference<>();
        otherRows = new ArrayList<>();
        when(properties.findByName(PortalMyCarlosKeyInventoryService.PROPERTY_NAME))
                .thenAnswer(ignored -> rows());
        when(properties.findByNameForUpdate(PortalMyCarlosKeyInventoryService.PROPERTY_NAME))
                .thenAnswer(ignored -> rows());
        doAnswer(invocation -> {
            persisted.set(invocation.getArgument(0));
            return null;
        })
                .when(properties).persist(any(Property.class));
        doAnswer(invocation -> {
            persisted.set(invocation.getArgument(0));
            return null;
        })
                .when(properties).merge(any(Property.class));
        service = anotherInstance();
    }

    private List<Property> rows() {
        var result = new ArrayList<>(otherRows);
        if (persisted.get() != null) {
            result.add(persisted.get());
        }
        return result;
    }

    private PortalMyCarlosKeyInventoryService anotherInstance() {
        return PortalMyCarlosInventoryTestSupport.service(properties, coordination);
    }

    private PortalMyCarlosKeyInventory initial() {
        return service.provision(CLINIC, 0, List.of(FIRST), (old, next) -> {});
    }

    @Test
    void shouldCoordinateBeforeFirstInsertion_andLeaveProviderScopedRowsAlone() {
        Property provider = new Property(PortalMyCarlosKeyInventoryService.PROPERTY_NAME);
        provider.setProviderNo("fake-provider");
        provider.setValue("not a global catalogue");
        otherRows.add(provider);
        assertThat(service.read()).isEmpty();
        initial();
        var order = inOrder(coordination, properties);
        order.verify(coordination).lockRoutingReport(PortalMyCarlosKeyInventoryService.COORDINATION_KEY);
        order.verify(properties).findByNameForUpdate(PortalMyCarlosKeyInventoryService.PROPERTY_NAME);
        order.verify(properties).persist(any(Property.class));
        assertThat(provider.getValue()).isEqualTo("not a global catalogue");
        assertThat(anotherInstance().read().orElseThrow().publication().keys()).containsExactly(FIRST);
    }

    @Test
    void shouldRejectStaleProvisionBeforePreparation_orPrivateMaterialCanBeRead() {
        initial();
        AtomicInteger preparations = new AtomicInteger();
        String before = persisted.get().getValue();
        assertThatThrownBy(() -> service.provisionPrepared(CLINIC, 0, old -> {
            preparations.incrementAndGet();
            return List.of(FIRST, SECOND);
        }, (old, next) -> {})).isInstanceOf(IllegalStateException.class);
        assertThat(preparations).hasValue(0);
        assertThat(persisted.get().getValue()).isEqualTo(before);
    }

    @Test
    void shouldInvalidateAcknowledgementOnTargetChange_andRejectLateOldTargetReply() {
        initial();
        var a = service.claim(TARGET_A, TOKEN_A, NOW, Duration.ofMinutes(2)).orElseThrow();
        assertThat(service.acknowledge(a, TARGET_A, acknowledgement(a), NOW.plusSeconds(1))).isTrue();
        assertThat(service.isPublished(TARGET_A)).isTrue();
        assertThat(service.isPublished(TARGET_B)).isFalse();
        var b = anotherInstance().claim(TARGET_B, TOKEN_B, NOW.plusSeconds(2), Duration.ofMinutes(2))
                .orElseThrow();
        assertThat(service.acknowledge(a, TARGET_B, acknowledgement(a), NOW.plusSeconds(3))).isFalse();
        assertThat(service.isPublished(TARGET_B)).isFalse();
        assertThat(service.acknowledge(b, TARGET_B, acknowledgement(b), NOW.plusSeconds(4))).isTrue();
        assertThat(service.isPublished(TARGET_A)).isFalse();
        assertThat(service.isPublished(TARGET_B)).isTrue();
    }

    @Test
    void shouldRejectOldGenerationReply_afterNewCompleteInventoryWasProvisioned() {
        initial();
        var old = service.claim(TARGET_A, TOKEN_A, NOW, Duration.ofMinutes(2)).orElseThrow();
        service.provision(CLINIC, 1, List.of(FIRST, SECOND), (previous, next) -> {});
        assertThat(service.acknowledge(old, TARGET_A, acknowledgement(old), NOW.plusSeconds(1))).isFalse();
        var current = service.claim(TARGET_A, TOKEN_B, NOW.plusSeconds(2), Duration.ofMinutes(2))
                .orElseThrow();
        assertThat(current.inventory().generation()).isEqualTo(2);
        assertThat(current.inventory().publication().keys()).containsExactlyInAnyOrder(FIRST, SECOND);
        assertThat(service.acknowledge(current, TARGET_A, acknowledgement(current), NOW.plusSeconds(3)))
                .isTrue();
    }

    @Test
    void shouldPersistRetryAndLeaseOwnership_acrossIndependentServiceInstances() {
        initial();
        var first = service.claim(TARGET_A, TOKEN_A, NOW, Duration.ofMinutes(2)).orElseThrow();
        assertThat(anotherInstance().claim(TARGET_A, TOKEN_B, NOW.plusSeconds(1), Duration.ofMinutes(2)))
                .isEmpty();
        assertThat(service.failed(first, TARGET_A, NOW.plusSeconds(1), Duration.ofSeconds(30))).isTrue();
        assertThat(anotherInstance().claim(TARGET_A, TOKEN_B, NOW.plusSeconds(30), Duration.ofMinutes(2)))
                .isEmpty();
        var next = anotherInstance().claim(TARGET_A, TOKEN_B, NOW.plusSeconds(31), Duration.ofMinutes(2))
                .orElseThrow();
        assertThat(next.failures()).isEqualTo(1);
        assertThat(service.acknowledge(first, TARGET_A, acknowledgement(first), NOW.plusSeconds(32))).isFalse();
        assertThat(service.acknowledge(next, TARGET_A, acknowledgement(next), NOW.plusSeconds(32))).isTrue();
    }

    @Test
    void shouldRefuseIncompleteOrWrongClinicAcknowledgements_withoutMarkingPublished() {
        initial();
        var claim = service.claim(TARGET_A, TOKEN_A, NOW, Duration.ofMinutes(2)).orElseThrow();
        var wrong = new PatientPortalClinicKeysPublishResult("other-clinic", true,
                acknowledgement(claim).keys());
        var missing = new PatientPortalClinicKeysPublishResult(CLINIC, true, List.of());
        assertThat(service.acknowledge(claim, TARGET_A, wrong, NOW.plusSeconds(1))).isFalse();
        assertThat(service.acknowledge(claim, TARGET_A, missing, NOW.plusSeconds(1))).isFalse();
        assertThat(service.isPublished(TARGET_A)).isFalse();
        assertThat(service.acknowledge(claim, TARGET_A, acknowledgement(claim), NOW.plusSeconds(2))).isTrue();
    }

    @Test
    void shouldPreserveAmbiguousOrMalformedStorage_insteadOfRepairingIt() {
        initial();
        Property duplicate = new Property(PortalMyCarlosKeyInventoryService.PROPERTY_NAME);
        duplicate.setProviderNo(" ");
        duplicate.setValue(persisted.get().getValue());
        otherRows.add(duplicate);
        assertThatThrownBy(service::read).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.claim(TARGET_A, TOKEN_A, NOW, Duration.ofMinutes(2)))
                .isInstanceOf(IllegalArgumentException.class);
        otherRows.clear();
        persisted.get().setValue("{\"schema_version\":999}");
        String malformed = persisted.get().getValue();
        assertThatThrownBy(service::read).isInstanceOf(IllegalArgumentException.class)
                .hasMessage("persisted clinic public key catalogue is invalid");
        assertThat(persisted.get().getValue()).isEqualTo(malformed);
    }

    @Test
    void shouldPublishWhollyRevokedHistory_withoutAnyDocumentSignerDependency() {
        service.provision(CLINIC, 0, List.of(revoked(FIRST)), (previous, next) -> {});
        var claim = service.claim(TARGET_A, TOKEN_A, NOW, Duration.ofMinutes(2)).orElseThrow();
        assertThat(service.acknowledge(claim, TARGET_A, acknowledgement(claim), NOW.plusSeconds(1))).isTrue();
        assertThat(service.isPublished(TARGET_A)).isTrue();
    }

    @Test
    void shouldRefuseLateAcknowledgementWhenTheFinalPropertyLockWaitCrossesLeaseExpiry() {
        initial();
        var claim = service.claim(TARGET_A, TOKEN_A, NOW, Duration.ofMinutes(2)).orElseThrow();
        AtomicReference<Instant> clock = new AtomicReference<>(NOW.plusSeconds(119));
        when(properties.findByNameForUpdate(PortalMyCarlosKeyInventoryService.PROPERTY_NAME))
                .thenAnswer(ignored -> {
                    clock.set(NOW.plusSeconds(120));
                    return rows();
                });
        assertThat(service.acknowledgeCurrent(claim, () -> TARGET_A, acknowledgement(claim), clock::get)).isFalse();
        assertThat(service.isPublished(TARGET_A)).isFalse();
    }

    @Test
    void shouldStartTheClaimLeaseFromTheFreshClockAfterTheFinalPropertyLock() {
        initial();
        AtomicReference<Instant> clock = new AtomicReference<>(NOW);
        when(properties.findByNameForUpdate(PortalMyCarlosKeyInventoryService.PROPERTY_NAME))
                .thenAnswer(ignored -> {
                    clock.set(NOW.plusSeconds(40));
                    return rows();
                });
        var claim = service.claimCurrent(() -> TARGET_A, TOKEN_A, clock::get, Duration.ofMinutes(2)).orElseThrow();
        assertThat(claim.expiresAt()).isEqualTo(NOW.plusSeconds(160));
    }

    @Test
    void shouldNotScheduleFailureBookkeepingForAClaimWhichExpiredDuringTheFinalPropertyLock() {
        initial();
        var claim = service.claim(TARGET_A, TOKEN_A, NOW, Duration.ofMinutes(2)).orElseThrow();
        AtomicReference<Instant> clock = new AtomicReference<>(NOW.plusSeconds(119));
        when(properties.findByNameForUpdate(PortalMyCarlosKeyInventoryService.PROPERTY_NAME))
                .thenAnswer(ignored -> {
                    clock.set(NOW.plusSeconds(120));
                    return rows();
                });
        assertThat(service.failedCurrent(claim, () -> TARGET_A, clock::get, Duration.ofSeconds(30))).isFalse();
    }

    @Test
    void shouldRecheckTheEffectiveTargetAfterTheFinalPropertyLockBeforeAcknowledgement() {
        initial();
        var claim = service.claim(TARGET_A, TOKEN_A, NOW, Duration.ofMinutes(2)).orElseThrow();
        AtomicReference<PortalMyCarlosPublicationState.Target> target = new AtomicReference<>(TARGET_A);
        when(properties.findByNameForUpdate(PortalMyCarlosKeyInventoryService.PROPERTY_NAME))
                .thenAnswer(ignored -> {
                    target.set(TARGET_B);
                    return rows();
                });
        assertThat(service.acknowledgeCurrent(claim, target::get, acknowledgement(claim),
                () -> NOW.plusSeconds(1))).isFalse();
        assertThat(service.isPublished(TARGET_A)).isFalse();
    }
}
