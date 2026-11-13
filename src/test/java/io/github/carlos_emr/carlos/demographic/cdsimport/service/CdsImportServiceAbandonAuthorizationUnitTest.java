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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.demographic.cdsimport.command.CdsImportCommand;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobBindingDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportJobDocumentDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * A browser-started import has no abandon in the browser, so a root-delegated operator acting
 * for its provider abandons it from the command line. Only such an operator, and only for the
 * run's own provider, facility and program, is authorized; a web account never is.
 *
 * @since 2026-11-13
 */
@Tag("unit")
@Tag("cds-import")
@DisplayName("CDS import abandon authorization")
class CdsImportServiceAbandonAuthorizationUnitTest {
    private static final String RUN = "5613b36a-4909-48bd-86f1-bd07b31c11f1";
    private static final CdsImportActorDto STARTED_IN_BROWSER =
            new CdsImportActorDto("web-7", "999998", "999998", 7, 1, 2);

    private final CdsImportLedgerService ledger = mock(CdsImportLedgerService.class);
    private final CdsImportJobService jobs = mock(CdsImportJobService.class);
    private final CdsImportAccessService access = mock(CdsImportAccessService.class);
    private final CdsImportCommand command = mock(CdsImportCommand.class);
    private CdsImportService service;

    @BeforeEach
    void setUp() throws Exception {
        var binding = mock(CdsImportJobBindingDto.class);
        var context = mock(CdsImportJobService.ReadContext.class);
        var document = mock(CdsImportJobDocumentDto.class);
        when(jobs.binding(RUN)).thenReturn(binding);
        when(jobs.loadContext(binding)).thenReturn(context);
        when(context.document()).thenReturn(document);
        when(document.actor()).thenReturn(STARTED_IN_BROWSER);
        when(document.command()).thenReturn(command);
        service =
                new CdsImportService(
                        ledger,
                        jobs,
                        mock(CdsImportExecutionService.class),
                        mock(CdsImportResultService.class),
                        access,
                        mock(CdsImportConfigurationService.class),
                        mock(CdsImportCleanupService.class),
                        mock(CdsImportReviewService.class),
                        mock(CdsImportFailureEvidenceService.class),
                        mock(CdsImportObservationService.class));
    }

    @Test
    void shouldAuthorizeARootDelegatedOperator_forTheRunsOwnScope() throws Exception {
        var delegated = new CdsImportActorDto("unix:operator", "999998", null, null, 1, 2);

        service.observeAbandon(delegated, RUN, 0, "5613b36a-4909-48bd-86f1-bd07b31c11f2");

        // Its live delegation and the provider's current rights are checked, then observed.
        verify(access).context(delegated, command, null);
        verify(ledger)
                .observeAbandon(
                        RUN, "unix:operator", "999998", 0, "5613b36a-4909-48bd-86f1-bd07b31c11f2");
    }

    @Test
    void shouldRefuseAWebAccount_evenForTheSameProvider() {
        var otherAccount = new CdsImportActorDto("web-8", "999998", "999998", 8, 1, 2);

        assertThatThrownBy(() -> service.authorizeAbandon(otherAccount, RUN))
                .isInstanceOf(SecurityException.class);
        verify(access, never()).context(any(), any(), any());
        verify(ledger, never()).observeAbandon(anyString(), anyString(), anyString(), anyLong(), anyString());
    }

    @Test
    void shouldRefuseADelegatedOperator_outsideTheRunsProviderFacilityOrProgram() {
        for (var outside :
                new CdsImportActorDto[] {
                    new CdsImportActorDto("unix:operator", "999997", null, null, 1, 2),
                    new CdsImportActorDto("unix:operator", "999998", null, null, 3, 2),
                    new CdsImportActorDto("unix:operator", "999998", null, null, 1, null)
                })
            assertThatThrownBy(() -> service.authorizeAbandon(outside, RUN))
                    .isInstanceOf(SecurityException.class);
        verify(access, never()).context(any(), any(), any());
    }
}
