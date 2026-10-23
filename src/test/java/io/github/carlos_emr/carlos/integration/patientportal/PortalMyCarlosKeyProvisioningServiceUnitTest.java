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
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.PDFSigningConfig;
import java.security.PrivateKey;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Tag("unit")
@Tag("patient-portal")
class PortalMyCarlosKeyProvisioningServiceUnitTest extends CarlosUnitTestBase {
    private PropertyDao properties;
    private ProviderLabRoutingDao coordination;
    private SecurityInfoManager security;
    private LoggedInInfo admin;
    private AtomicReference<Property> persisted;
    private AtomicReference<PatientPortalClinicKeysPublishRequest.Key> configuredSigner;
    private AtomicReference<PrivateKey> retainedPrivate;
    private AtomicInteger signerReads;
    private AtomicInteger retainedReads;
    private PortalMyCarlosKeyInventoryService inventory;
    private PortalMyCarlosKeyProvisioningService provisioning;

    @BeforeEach
    void setUp() {
        properties = mock(PropertyDao.class);
        coordination = mock(ProviderLabRoutingDao.class);
        security = mock(SecurityInfoManager.class);
        admin = mock(LoggedInInfo.class);
        persisted = new AtomicReference<>();
        configuredSigner = new AtomicReference<>(FIRST);
        retainedPrivate = new AtomicReference<>(FIRST_PAIR.getPrivate());
        signerReads = new AtomicInteger();
        retainedReads = new AtomicInteger();
        when(properties.findByNameForUpdate(PortalMyCarlosKeyInventoryService.PROPERTY_NAME))
                .thenAnswer(ignored -> persisted.get() == null ? List.of() : List.of(persisted.get()));
        when(properties.findByName(PortalMyCarlosKeyInventoryService.PROPERTY_NAME))
                .thenAnswer(ignored -> persisted.get() == null ? List.of() : List.of(persisted.get()));
        doAnswer(call -> { persisted.set(call.getArgument(0)); return null; })
                .when(properties).persist(any(Property.class));
        doAnswer(call -> { persisted.set(call.getArgument(0)); return null; })
                .when(properties).merge(any(Property.class));
        when(security.hasPrivilege(admin, "_admin", SecurityInfoManager.WRITE, null)).thenReturn(true);
        inventory = new PortalMyCarlosKeyInventoryService(properties, coordination);
        provisioning = new PortalMyCarlosKeyProvisioningService(inventory, security, () -> CLINIC, () -> {
            signerReads.incrementAndGet();
            var signer = configuredSigner.get();
            if (signer == null) {
                throw new IllegalStateException("FAKE disabled/expired signer");
            }
            return signer;
        }, (selection, expected) -> {
            retainedReads.incrementAndGet();
            return retainedPrivate.get();
        });
        TransactionSynchronizationManager.initSynchronization();
    }

    @AfterEach
    void clearSynchronization() {
        TransactionSynchronizationManager.clearSynchronization();
    }

    @Test
    void shouldRequireAdminPrivilege_beforeReadingInventoryOrPrivateMaterial() {
        when(security.hasPrivilege(admin, "_admin", SecurityInfoManager.WRITE, null)).thenReturn(false);
        assertThatThrownBy(() -> provisioning.provision(admin, 0, List.of(FIRST), true))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(properties, coordination);
        assertThat(signerReads).hasValue(0);
        assertThat(retainedReads).hasValue(0);
    }

    @Test
    void shouldRequireExplicitCompleteness_beforeInferringAnythingFromTheSigner() {
        assertThatThrownBy(() -> provisioning.provision(admin, 0, List.of(FIRST), false))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(properties, coordination);
        assertThat(signerReads).hasValue(0);
    }

    @Test
    void shouldBindNewActiveInventory_toTheActualConfiguredPublicSigner() {
        assertThatThrownBy(() -> provisioning.provision(admin, 0, List.of(SECOND), true))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(persisted).hasValue(null);
        var result = provisioning.provision(admin, 0, List.of(FIRST), true);
        assertThat(result.publication().keys()).containsExactly(FIRST);
        assertThat(signerReads).hasValue(2);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
    }

    @Test
    void shouldRevokeWithAnUnavailableSigner_withoutReadingAnyPrivateMaterial() {
        inventory.provision(CLINIC, 0, List.of(FIRST), (old, next) -> {});
        configuredSigner.set(null);
        var result = provisioning.provision(admin, 1, List.of(revoked(FIRST)), true);
        assertThat(result.publication().keys()).containsExactly(revoked(FIRST));
        assertThat(signerReads).hasValue(0);
        assertThat(retainedReads).hasValue(0);
    }

    @Test
    void shouldPersistOneReplacementProof_andReuseItsExactBytesOnAnExplicitCurrentGenerationRetry() {
        inventory.provision(CLINIC, 0, List.of(FIRST), (old, next) -> {});
        configuredSigner.set(SECOND);
        PDFSigningConfig selection = mock(PDFSigningConfig.class);
        var first = provisioning.provisionWithReplacement(admin, 1, List.of(FIRST, SECOND), true,
                SECOND.kid(), FIRST.kid(), selection);
        var signed = first.publication().keys().stream().filter(key -> key.kid().equals(SECOND.kid()))
                .findFirst().orElseThrow();
        assertThat(signed.replacementSignature()).isNotNull();
        var repeated = provisioning.provisionWithReplacement(admin, 2, List.of(FIRST, SECOND), true,
                SECOND.kid(), FIRST.kid(), selection);
        assertThat(repeated.toStoredJson()).isEqualTo(first.toStoredJson());
        assertThat(repeated.publication().keys()).contains(signed);
        assertThat(retainedReads).hasValue(1);
        assertThat(signerReads).hasValue(1);
        assertThatThrownBy(() -> provisioning.provisionWithReplacement(
                admin, 1, List.of(FIRST, SECOND), true, SECOND.kid(), FIRST.kid(), selection))
                .isInstanceOf(IllegalStateException.class);
        assertThat(retainedReads).hasValue(1);
    }

    @Test
    void shouldRefusePreviouslyRevokedPredecessors_beforeLoadingRetainedPrivateMaterial() {
        inventory.provision(CLINIC, 0, List.of(revoked(FIRST)), (old, next) -> {});
        configuredSigner.set(SECOND);
        assertThatThrownBy(() -> provisioning.provisionWithReplacement(admin, 1,
                List.of(revoked(FIRST), SECOND), true, SECOND.kid(), FIRST.kid(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(retainedReads).hasValue(0);
        assertThat(signerReads).hasValue(0);
        assertThat(inventory.read().orElseThrow().generation()).isEqualTo(1);
    }

    @Test
    void shouldRefuseTheWrongRetainedPrivateKey_withoutPersistingTheNewCatalogue() {
        inventory.provision(CLINIC, 0, List.of(FIRST), (old, next) -> {});
        configuredSigner.set(SECOND);
        retainedPrivate.set(SECOND_PAIR.getPrivate());
        String original = persisted.get().getValue();
        assertThatThrownBy(() -> provisioning.provisionWithReplacement(admin, 1,
                List.of(FIRST, SECOND), true, SECOND.kid(), FIRST.kid(), null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(persisted.get().getValue()).isEqualTo(original);
        assertThat(signerReads).hasValue(0);
    }
}
