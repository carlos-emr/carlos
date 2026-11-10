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
import io.github.carlos_emr.carlos.demographic.cdsexport.dto.*;
import java.io.IOException;
import java.util.*;
import org.junit.jupiter.api.*;

@Tag("unit")
@Tag("cds-export")
class CdsExportRetentionServiceUnitTest {
    private final CdsExportLedgerService ledger = mock(CdsExportLedgerService.class);
    private final CdsExportStagingFactory stores = mock(CdsExportStagingFactory.class);
    private final CdsExportConfigurationService configuration =
            mock(CdsExportConfigurationService.class);
    private final CdsExportJobDocumentDto document = mock(CdsExportJobDocumentDto.class);
    private final String run = UUID.randomUUID().toString();
    private CdsExportLedgerService.Cleanup claim;
    private CdsExportRetentionService service;

    @BeforeEach
    void expiredAttempt() {
        when(document.protocol()).thenReturn(2);
        when(document.storage())
                .thenReturn(new CdsExportStorageBindingDto("primary", "primary", "a".repeat(64)));
        claim = new CdsExportLedgerService.Cleanup(run, 1, UUID.randomUUID().toString(), document);
        when(ledger.expiredArtifacts(16)).thenReturn(List.of(run));
        when(ledger.artifactEpochs(run, 0, 64)).thenReturn(List.of(1L));
        when(ledger.beginCleanup(run, 1)).thenReturn(claim);
        service = new CdsExportRetentionService(ledger, stores, configuration);
    }

    @AfterEach
    void close() {
        service.close();
    }

    @Test
    void shouldRemoveOnlyTheExclusivelyClaimedAttempt_andAcknowledgeAfterDeletion()
            throws Exception {
        var storage = document.storage();
        doAnswer(
                        call -> {
                            ((Runnable) call.getArgument(3)).run();
                            return null;
                        })
                .when(stores)
                .purgeAttempt(eq(run), eq(1L), eq(storage), any());
        service.sweep();
        var order = inOrder(ledger, stores);
        order.verify(ledger).beginCleanup(run, 1);
        order.verify(stores).purgeAttempt(eq(run), eq(1L), eq(storage), any());
        order.verify(ledger, atLeastOnce()).cleanupCheckpoint(claim);
        order.verify(ledger).finishCleanup(claim, true);
    }

    @Test
    void shouldPreserveFailureEvidence_afterAnErasureFailure() throws Exception {
        doThrow(new IOException("OWNED_ERASURE_FAILED"))
                .when(stores)
                .purgeAttempt(any(), anyLong(), any(), any());
        service.sweep();
        verify(ledger).finishCleanup(claim, false);
        verify(ledger, never()).finishCleanup(claim, true);
    }

    @Test
    void shouldSkipAnAttemptWhoseDownloadOrWorkerStillOwnsIt_forRetentionServiceContract() {
        when(ledger.beginCleanup(run, 1)).thenReturn(null);
        service.sweep();
        verifyNoInteractions(stores);
        verify(ledger, never()).finishCleanup(any(), anyBoolean());
    }

    @Test
    void shouldNotTraverseAnyStorage_forALegacyMetadataOnlyAttempt() throws Exception {
        when(document.protocol()).thenReturn(1);
        service.sweep();
        verifyNoInteractions(stores);
        verify(ledger).finishCleanup(claim, true);
    }

    @Test
    void shouldPropagateAnUnknownRetirementAcknowledgement_insteadOfReportingSuccess() {
        doThrow(new IllegalStateException("UNKNOWN_ACKNOWLEDGEMENT"))
                .when(ledger)
                .finishCleanup(claim, true);
        assertThatThrownBy(service::sweep).hasMessage("UNKNOWN_ACKNOWLEDGEMENT");
    }

    @Test
    void shouldNotStartABackgroundRetentionWorker_inTheDedicatedCliContext() {
        when(configuration.workerEnabled()).thenReturn(false);
        clearInvocations(ledger);
        service.start(null);
        verifyNoInteractions(ledger);
    }
}
