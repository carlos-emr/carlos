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
package io.github.carlos_emr.carlos.demographic.cdsexport.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import io.github.carlos_emr.carlos.demographic.cds.contract.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.command.CdsExportCommand;
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import io.github.carlos_emr.carlos.demographic.cdsexport.engine.CdsExportRunControl;
import java.io.IOException;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Tag("unit") @Tag("cds-export")
class CdsExportClinicalJobServiceUnitTest {
    private final CdsExportImplementationIdentityService identity = mock(CdsExportImplementationIdentityService.class);
    private final CdsExportActivePlanService plans = mock(CdsExportActivePlanService.class);
    private final CdsExportConfigurationService configuration = mock(CdsExportConfigurationService.class);
    private final CdsExportAccessService access = mock(CdsExportAccessService.class);
    private final CdsExportDisclosurePolicyService disclosure = mock(CdsExportDisclosurePolicyService.class);
    private final CdsExportStagingFactory stores = mock(CdsExportStagingFactory.class);
    private final CdsExportSelectionService selections = mock(CdsExportSelectionService.class);
    private final CdsExportCohortService cohorts = mock(CdsExportCohortService.class);
    private final CdsExportLedgerService ledger = mock(CdsExportLedgerService.class);
    private final CdsExportClinicalPackageService packages = mock(CdsExportClinicalPackageService.class);
    private final CdsExportDeliveryService delivery = mock(CdsExportDeliveryService.class);
    private final ObjectProvider<CdsExportPublicationService> provider = mock(ObjectProvider.class);
    private final CdsExportPublicationService publication = mock(CdsExportPublicationService.class);
    private final CdsExportRunControl control = mock(CdsExportRunControl.class);
    private final CdsExportStagingService store = mock(CdsExportStagingService.class);
    private final CdsExportClinicalPackageService.Prepared prepared = mock(CdsExportClinicalPackageService.Prepared.class);
    private final CdsExportDeliveryService.Encrypted encrypted = mock(CdsExportDeliveryService.Encrypted.class);
    private final CdsExportCohortService.Sealed sealed = mock(CdsExportCohortService.Sealed.class);
    private final AtomicReference<String> runtime = new AtomicReference<>("a".repeat(64));
    private CdsExportClinicalJobService service;
    private CdsExportActivePlanService.Plan plan;
    private CdsExportLeaseDto lease;
    private CdsExportSelectionDto selected;
    private CdsExportJobDto ready;
    @BeforeEach void clinicalJob() throws Exception {
        var settings = new Properties(); settings.setProperty("cds.export.clinicalZone", "America/Toronto"); plan = new CdsExportActivePlanService(settings, () -> "2026.09.0-test").resolve();
        when(plans.resolve()).thenReturn(plan); when(identity.current()).thenAnswer(call -> runtime.get());
        service = new CdsExportClinicalJobService(identity, plans, configuration, access, disclosure, stores, selections, cohorts, ledger, packages, delivery, provider);
        var command = new CdsExportCommand("job-proof", "999998", null, CdsLogicalProfile.ONTARIO_MD_5_2, CdsExportCommand.Selection.SELECTED_PATIENTS, List.of(), List.of(27), Set.of(CdsCategory.DEMOGRAPHICS), "fixture", "fixture");
        var actor = new CdsExportActorDto("agent", "999998", null, null); var binding = new CdsExportStorageBindingDto("primary", "primary", "a".repeat(64));
        var document = new CdsExportJobDocumentDto(2, actor, command, service.compatibilityDigest(), "sender", "b".repeat(64), "c".repeat(64), binding);
        lease = new CdsExportLeaseDto(UUID.randomUUID().toString(), 1, UUID.randomUUID().toString(), document);
        var now = Instant.now().truncatedTo(java.time.temporal.ChronoUnit.MICROS);
        selected = new CdsExportSelectionDto(now, now, 1, new CdsExportStagedFileDto("d".repeat(64), "PATIENT_SELECTION", "e".repeat(64), 1));
        when(stores.bind()).thenReturn(binding); when(stores.create(eq(lease), any())).thenReturn(store);
        when(selections.capture(eq(command), eq(store), eq(plan.archive().patients()), any())).thenReturn(selected); when(cohorts.prepare(lease, selected, control)).thenReturn(sealed);
        when(packages.compose(lease, plan.applicationVersion(), plan.clinicalZone(), plan.source(), plan.xml(), plan.archive(), control)).thenReturn(prepared);
        when(delivery.encrypt(lease, prepared, plan.ciphertextBytes(), control)).thenReturn(encrypted); when(provider.getObject()).thenReturn(publication);
        ready = new CdsExportJobDto(lease.runId(), CdsExportState.READY, 20, 1L, 1, 0, false, false, lease.epoch(), null, Instant.now(), Instant.now(), true);
        when(publication.publish(lease, encrypted, plan.retentionSeconds(), control)).thenReturn(ready);
        when(disclosure.resolve(any(), any())).thenReturn(new CdsExportDisclosurePolicyService.Resolved("sender", "Owned group", "Owned contact", "b".repeat(64), null, "c".repeat(64), "Owned audit"));
    }
    @AfterEach void clearTransactionFixture() { TransactionSynchronizationManager.setActualTransactionActive(false); TransactionSynchronizationManager.setCurrentTransactionReadOnly(false); }
    @Test void shouldBindOnlyServerOwnedPolicyStorageAndSemanticIdentity_toTheExactAuthorizedIntent() throws Exception {
        var bound = service.bind(lease.document().actor(), lease.document().command()); assertThat(bound).isEqualTo(lease.document());
        verify(access).requireScope(bound.actor(), bound.command()); verify(configuration).requireEnabled();
    }
    @Test void shouldCoordinateTheCompletePrivatePipeline_andReturnOnlyThePublishedResult() throws Exception {
        assertThat(service.execute(lease, control)).isEqualTo(ready);
        var order = inOrder(stores, selections, cohorts, ledger, packages, delivery, publication);
        order.verify(stores).create(eq(lease), any()); order.verify(selections).capture(eq(lease.document().command()), eq(store), eq(plan.archive().patients()), any());
        order.verify(cohorts).prepare(lease, selected, control); order.verify(ledger).sealSelection(lease, sealed);
        order.verify(packages).compose(lease, plan.applicationVersion(), plan.clinicalZone(), plan.source(), plan.xml(), plan.archive(), control);
        order.verify(ledger).retainPrepared(lease, prepared); order.verify(delivery).encrypt(lease, prepared, plan.ciphertextBytes(), control); order.verify(publication).publish(lease, encrypted, plan.retentionSeconds(), control);
    }
    @Test void shouldRouteStagingCancellationToTheIndependentMetadataProbe_insideSelectionSnapshots() throws Exception {
        final Runnable[] callback = {null}; doAnswer(call -> { callback[0] = call.getArgument(1); callback[0].run(); return store; }).when(stores).create(eq(lease), any());
        doAnswer(call -> {
            TransactionSynchronizationManager.setActualTransactionActive(true); TransactionSynchronizationManager.setCurrentTransactionReadOnly(true);
            try { callback[0].run(); ((Runnable) call.getArgument(3)).run(); }
            finally { TransactionSynchronizationManager.setActualTransactionActive(false); TransactionSynchronizationManager.setCurrentTransactionReadOnly(false); }
            return selected;
        }).when(selections).capture(eq(lease.document().command()), eq(store), eq(plan.archive().patients()), any());
        service.execute(lease, control); verify(control, times(2)).captureCheckpoint();
    }
    @Test void shouldRefuseAnEmptyRoster_asReviewBeforeSealingOrSerializingAnything() throws Exception {
        when(selections.capture(any(), any(), anyLong(), any())).thenReturn(new CdsExportSelectionDto(selected.readStartedAt(), selected.readCompletedAt(), 0, selected.inventory()));
        assertThatThrownBy(() -> service.execute(lease, control)).hasMessage("CDS_EXPORT_NO_PATIENTS_SELECTED"); verifyNoInteractions(cohorts, packages, delivery, publication);
    }
    @Test void shouldRefuseRuntimeDrift_beforeOpeningAnyStagingDirectory() throws Exception {
        runtime.set("f".repeat(64)); assertThatThrownBy(() -> service.execute(lease, control)).hasMessage("CDS_EXPORT_RUNTIME_CHANGED"); verify(stores, never()).create(any(), any());
    }
    @Test void shouldRefuseRuntimeDriftAfterEncryption_withoutDeletingPossiblyRetainedFiles() throws Exception {
        doAnswer(call -> { runtime.set("f".repeat(64)); return encrypted; }).when(delivery).encrypt(lease, prepared, plan.ciphertextBytes(), control);
        assertThatThrownBy(() -> service.execute(lease, control)).hasMessage("CDS_EXPORT_RUNTIME_CHANGED"); verifyNoInteractions(publication); verify(store, never()).discardUnpublished(any());
    }
    @Test void shouldPreserveAllAttemptAssets_afterAnUnknownPublicationAcknowledgement() throws Exception {
        when(publication.publish(lease, encrypted, plan.retentionSeconds(), control)).thenThrow(new IOException("UNKNOWN_COMMIT_ACK"));
        assertThatThrownBy(() -> service.execute(lease, control)).hasMessage("UNKNOWN_COMMIT_ACK"); verify(store, never()).discardUnpublished(any()); verify(ledger, never()).release(any());
    }
    @Test void shouldRejectAmbientTransactions_beforePolicyFileOrClinicalWork() {
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThatThrownBy(() -> service.bind(lease.document().actor(), lease.document().command())).hasMessage("CDS_EXPORT_AMBIENT_TRANSACTION_REFUSED");
        assertThatThrownBy(() -> service.execute(lease, control)).hasMessage("CDS_EXPORT_AMBIENT_TRANSACTION_REFUSED");
        verifyNoInteractions(access, stores, packages, delivery, publication);
    }
    @Test void shouldRefuseAnUnpublishedReturn_insteadOfTreatingPrivatePackageCompletionAsSuccess() throws Exception {
        var incomplete = new CdsExportJobDto(lease.runId(), CdsExportState.ENCRYPTING, 19, 1L, 0, 0, false, true, lease.epoch(), null, Instant.now(), Instant.now(), false);
        when(publication.publish(lease, encrypted, plan.retentionSeconds(), control)).thenReturn(incomplete);
        assertThatThrownBy(() -> service.execute(lease, control)).hasMessage("CDS_EXPORT_PUBLICATION_INCOMPLETE");
    }
    @Test void shouldRefuseRevokedGlobalScope_beforeOpeningTheStore() throws Exception {
        doThrow(new SecurityException("REVOKED")).when(access).requireScope(any(), any()); assertThatThrownBy(() -> service.execute(lease, control)).hasMessage("REVOKED"); verify(stores, never()).create(any(), any());
    }
}
