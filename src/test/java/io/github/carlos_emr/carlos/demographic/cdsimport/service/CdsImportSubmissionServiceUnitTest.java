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
package io.github.carlos_emr.carlos.demographic.cdsimport.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDocumentDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.io.IOException;
import java.nio.file.Path;

@Tag("unit")
class CdsImportSubmissionServiceUnitTest {
    private final CdsImportLedgerService ledger = mock(CdsImportLedgerService.class);
    private final CdsImportJobService jobs = mock(CdsImportJobService.class);
    private final CdsImportAccessService access = mock(CdsImportAccessService.class);
    private final CdsImportSubmissionService service =
            new CdsImportSubmissionService(ledger, jobs, access);
    private final CdsImportJobDto row = mock(CdsImportJobDto.class);
    private final CdsImportCommand command = command("T099", "submission");
    private final CdsImportActorDto currentActor =
            new CdsImportActorDto("operator", "T099", null, null, 2, 3);

    @BeforeEach
    void accepted() throws Exception {
        when(row.id()).thenReturn("accepted-run");
        when(row.operator()).thenReturn("operator");
        when(row.providerNo()).thenReturn("T099");
        when(ledger.findSubmission("operator", "submission")).thenReturn(row);
        when(jobs.load("accepted-run")).thenReturn(document(command));
        when(access.delegatedActor("operator", command)).thenReturn(currentActor);
    }

    @Test
    void shouldResolveStoredSubmissionWithCurrentAuthority_whenScopedAcceptanceExists()
            throws Exception {
        var resolved = service.resolve("operator", "T099", "submission");
        assertThat(resolved.runId()).isEqualTo("accepted-run");
        assertThat(resolved.command()).isSameAs(command);
        assertThat(resolved.actor()).isSameAs(currentActor);
        verify(ledger).findSubmission("operator", "submission");
        verifyNoMoreInteractions(ledger);
        verify(jobs).load("accepted-run");
        verify(access).delegatedActor("operator", command);
        assertThat(resolved.toString()).isEqualTo("Resolved[protected]");
    }

    @Test
    void shouldKeepOutcomeUnknown_whenAcceptanceRowIsNotYetVisible() {
        when(ledger.findSubmission("operator", "submission")).thenReturn(null);
        unknown(() -> service.resolve("operator", "T099", "submission"));
        verifyNoInteractions(jobs, access);
    }

    @Test
    void shouldKeepOutcomeUnknownWithoutOpeningStoredJob_whenProviderDiffers() {
        when(row.providerNo()).thenReturn("T002");
        unknown(() -> service.resolve("operator", "T099", "submission"));
        verifyNoInteractions(jobs, access);
    }

    @Test
    void shouldNeverProbeAnotherOperator_whenScopedLookupReturnsAnotherOwner() {
        when(row.operator()).thenReturn("different-operator");
        unknown(() -> service.resolve("operator", "T099", "submission"));
        verify(ledger).findSubmission("operator", "submission");
        verifyNoMoreInteractions(ledger);
        verifyNoInteractions(jobs, access);
    }

    @Test
    void shouldRecheckStoredCommandProvider_whenLedgerScopeMatches() throws Exception {
        when(jobs.load("accepted-run")).thenReturn(document(command("T002", "submission")));
        unknown(() -> service.resolve("operator", "T099", "submission"));
        verifyNoInteractions(access);
    }

    @Test
    void shouldRecheckStoredSubmissionKey_whenLedgerScopeMatches() throws Exception {
        when(jobs.load("accepted-run")).thenReturn(document(command("T099", "different-key")));
        unknown(() -> service.resolve("operator", "T099", "submission"));
        verifyNoInteractions(access);
    }

    @Test
    void shouldRefuseResolution_whenCurrentDelegationWasRevoked() {
        doThrow(new SecurityException("Import operator delegation denied"))
                .when(access)
                .delegatedActor("operator", command);
        assertThatThrownBy(() -> service.resolve("operator", "T099", "submission"))
                .isInstanceOf(SecurityException.class)
                .hasMessage("Import operator delegation denied");
    }

    @Test
    void shouldRecoverLostAcknowledgement_whenTheSameScopedRowBecomesVisibleLater()
            throws Exception {
        when(ledger.findSubmission("operator", "submission")).thenReturn(null, row);
        unknown(() -> service.resolve("operator", "T099", "submission"));
        assertThat(service.resolve("operator", "T099", "submission").runId())
                .isEqualTo("accepted-run");
        verify(ledger, times(2)).findSubmission("operator", "submission");
        verifyNoMoreInteractions(ledger);
        verify(jobs, times(1)).load("accepted-run");
        verify(access, times(1)).delegatedActor("operator", command);
    }

    @Test
    void shouldRefuseBeforeLookupOrFileIo_whenAnAmbientTransactionExists() {
        boolean active = TransactionSynchronizationManager.isActualTransactionActive();
        try {
            TransactionSynchronizationManager.setActualTransactionActive(true);
            assertThatThrownBy(() -> service.resolve("operator", "T099", "submission"))
                    .isInstanceOf(CdsImportException.class);
            verifyNoInteractions(ledger, jobs, access);
        } finally {
            TransactionSynchronizationManager.setActualTransactionActive(active);
        }
    }

    @Test
    void shouldKeepOutcomeUnknownBeforeAnyLookup_whenInputIsUnboundedOrMalformed() {
        for (String key : new String[] {null, "", " ", "x".repeat(129), "bad\nkey"})
            unknown(() -> service.resolve("operator", "T099", key));
        unknown(() -> service.resolve("x".repeat(66), "T099", "submission"));
        unknown(() -> service.resolve("operator", "T".repeat(17), "submission"));
        verifyNoInteractions(ledger, jobs, access);
    }

    @Test
    void shouldPropagateStoredDocumentFailure_whenAcceptedEvidenceCannotBeLoaded()
            throws Exception {
        when(jobs.load("accepted-run")).thenThrow(new IOException("protected job unavailable"));
        assertThatThrownBy(() -> service.resolve("operator", "T099", "submission"))
                .isInstanceOf(IOException.class)
                .hasMessage("protected job unavailable");
        verifyNoInteractions(access);
    }

    private static void unknown(org.assertj.core.api.ThrowableAssert.ThrowingCallable operation) {
        assertThatThrownBy(operation)
                .isInstanceOfSatisfying(
                        CdsImportException.class,
                        failure -> {
                            assertThat(failure.getCode())
                                    .isEqualTo("SUBMISSION_OUTCOME_NOT_YET_KNOWN");
                            assertThat(failure.isOutcomeUnknown()).isTrue();
                        });
    }

    private static CdsImportCommand command(String provider, String key) {
        return new CdsImportCommand(
                Path.of("/protected/synthetic-source.xml"),
                key,
                provider,
                3,
                2,
                "synthetic-source",
                "omd-5.2",
                false,
                false,
                false,
                0,
                null,
                false);
    }

    private static CdsImportJobDocumentDto document(CdsImportCommand value) {
        return new CdsImportJobDocumentDto(
                value,
                new CdsImportActorDto("operator", value.providerNo(), "old-provider", 1, 2, 3),
                null,
                null,
                Path.of("/protected/job"));
    }
}
