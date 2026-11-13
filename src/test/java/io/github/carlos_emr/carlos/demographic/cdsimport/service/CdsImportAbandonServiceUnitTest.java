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

import io.github.carlos_emr.carlos.demographic.cdsimport.dto.CdsImportActorDto;
import io.github.carlos_emr.carlos.demographic.cdsimport.support.CdsImportException;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** The abandon control's refusals, which must leave the run, its holds and maintenance alone. */
@Tag("unit")
class CdsImportAbandonServiceUnitTest {
    private static final String RUN = "4f0c7f4e-6a59-4c3b-9d47-2f1d2d5f7a10";
    private static final CdsImportActorDto ACTOR =
            new CdsImportActorDto("unix:synthetic", "999998", "999998", 1, null, null);

    private final CdsImportLedgerService ledger = mock(CdsImportLedgerService.class);
    private final CdsImportCoordinatorService coordinators = mock(CdsImportCoordinatorService.class);
    private final CdsImportMaintenanceService maintenance = mock(CdsImportMaintenanceService.class);
    private final CdsImportConfigurationService configuration =
            mock(CdsImportConfigurationService.class);
    private final CdsImportAbandonService service =
            new CdsImportAbandonService(
                    ledger,
                    coordinators,
                    maintenance,
                    mock(CdsImportTargetSafetyService.class),
                    mock(CdsTargetReceiptService.class),
                    mock(CdsImportResultService.class),
                    mock(CdsImportFailureEvidenceService.class),
                    configuration);

    @Test
    void shouldAbandonOnlyAStoppedCancelledWrittenRun_withNoPendingResumeOrReview() {
        assertThat(CdsImportLedgerService.abandonRefusal("INCOMPLETE", false, true, false, false))
                .isNull();
        assertThat(
                        CdsImportLedgerService.abandonRefusal(
                                "RECONCILIATION_REQUIRED", false, true, false, false))
                .isNull();
        assertThat(CdsImportLedgerService.abandonRefusal("ABANDONED", false, true, false, false))
                .isEqualTo("IMPORT_ABANDONED");
        for (String state :
                new String[] {
                    "RUNNING",
                    "FINALIZING",
                    "RESUME_REQUESTED",
                    "COMPLETE",
                    "REVIEW_REQUIRED",
                    "FAILED_BEFORE_WRITE",
                    "CANCELLED_BEFORE_WRITE"
                })
            assertThat(CdsImportLedgerService.abandonRefusal(state, false, true, false, false))
                    .as(state)
                    .isEqualTo("ABANDON_RUN_NOT_STOPPED");
        assertThat(CdsImportLedgerService.abandonRefusal("INCOMPLETE", true, true, false, false))
                .isEqualTo("ABANDON_RUN_NOT_STOPPED");
        assertThat(CdsImportLedgerService.abandonRefusal("INCOMPLETE", false, false, false, false))
                .isEqualTo("ABANDON_CANCEL_REQUIRED");
        assertThat(CdsImportLedgerService.abandonRefusal("INCOMPLETE", false, true, true, false))
                .isEqualTo("ABANDON_RESUME_PENDING");
        assertThat(CdsImportLedgerService.abandonRefusal("INCOMPLETE", false, true, false, true))
                .isEqualTo("ABANDON_REVIEW_PENDING");
    }

    @Test
    void shouldRefuseAMismatchedConfirmationOrReason_beforeTouchingAnyOwner() {
        assertThatThrownBy(() -> service.abandon(RUN, ACTOR, RUN.toUpperCase(), "stopped by P1"))
                .isInstanceOfSatisfying(
                        CdsImportException.class,
                        refused ->
                                assertThat(refused.getCode())
                                        .isEqualTo("ABANDON_CONFIRMATION_MISMATCH"));
        for (String reason : new String[] {null, " ", "a\nb", "a\u2028b", "x".repeat(1001)})
            assertThatThrownBy(() -> service.abandon(RUN, ACTOR, RUN, reason))
                    .isInstanceOfSatisfying(
                            CdsImportException.class,
                            refused ->
                                    assertThat(refused.getCode())
                                            .isEqualTo("ABANDON_REASON_INVALID"));
        verifyNoInteractions(ledger, coordinators, maintenance, configuration);
        assertThat(CdsImportAbandonService.reason("  defect stops every resume  "))
                .isEqualTo("defect stops every resume");
        assertThat(CdsImportAbandonService.sha256("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        // Characters are counted as a person (and carlos-ctl) counts them, in code points.
        String wide = "\uD83D\uDE00".repeat(600);
        assertThat(CdsImportAbandonService.reason(wide)).isEqualTo(wide);
        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> CdsImportAbandonService.reason("\uD83D\uDE00".repeat(1001)))
                .isInstanceOf(CdsImportException.class);
        // The published digest is salted, never the bare digest of the reason.
        assertThat(CdsImportAbandonService.reasonDigest("00", "defect"))
                .isNotEqualTo(CdsImportAbandonService.sha256("defect"))
                .isEqualTo(CdsImportAbandonService.sha256("00\ndefect"));
    }

    @Test
    void shouldNeverTakeTheMaintenanceWindow_whenTheRunCannotBeAbandoned() {
        var coordinator = mock(CdsImportCoordinatorService.Owner.class);
        when(coordinators.acquire()).thenReturn(coordinator);
        doThrow(new CdsImportException("IMPORT_ABANDONED"))
                .when(ledger)
                .requireAbandonable(RUN, ACTOR);
        assertThatThrownBy(() -> service.abandon(RUN, ACTOR, RUN, "second abandon"))
                .isInstanceOfSatisfying(
                        CdsImportException.class,
                        refused -> assertThat(refused.getCode()).isEqualTo("IMPORT_ABANDONED"));
        doThrow(new SecurityException("IMPORT_ACCESS_DENIED"))
                .when(ledger)
                .requireAbandonable(RUN, ACTOR);
        assertThatThrownBy(() -> service.abandon(RUN, ACTOR, RUN, "wrong operator"))
                .isInstanceOf(SecurityException.class);
        verifyNoInteractions(maintenance);
        verify(coordinator, times(2)).close();
    }
}
