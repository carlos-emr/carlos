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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.EmailLogDao;
import io.github.carlos_emr.carlos.commn.model.EmailLog.TransactionType;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("PortalInviteCodeSweeper")
@Tag("unit")
class PortalInviteCodeSweeperUnitTest extends CarlosUnitTestBase {

    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");

    private EmailLogDao emailLogs;
    private PortalInviteCodeSweeper sweeper;

    @BeforeEach
    void setUp() {
        emailLogs = mock(EmailLogDao.class);
        sweeper = new PortalInviteCodeSweeper(emailLogs, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    @DisplayName("should look only at portal invitation emails from the code's lifetime, idle 15 minutes")
    void shouldAskForRecentIdleInvitationEmails_whenSweeping() {
        sweeper.forgetLeftoverCodes();

        verify(emailLogs).findIdsByTransactionTypeChangedBetween(TransactionType.PORTAL_INVITE,
                Date.from(NOW.minus(Duration.ofDays(8))), Date.from(NOW.minus(Duration.ofMinutes(15))));
    }

    @Test
    @DisplayName("should replace the body of every leftover email and count the ones that changed")
    void shouldForgetEachCode_andCountChangedRows() {
        when(emailLogs.findIdsByTransactionTypeChangedBetween(any(), any(Date.class), any(Date.class))).thenReturn(List.of(1, 2, 3));
        when(emailLogs.replaceBody(1, PortalInviteEmailComposer.CODE_FORGOTTEN)).thenReturn(1);
        when(emailLogs.replaceBody(2, PortalInviteEmailComposer.CODE_FORGOTTEN)).thenReturn(0);
        when(emailLogs.replaceBody(3, PortalInviteEmailComposer.CODE_FORGOTTEN)).thenReturn(1);

        assertThat(sweeper.forgetLeftoverCodes()).isEqualTo(2);
        verify(emailLogs).replaceBody(2, PortalInviteEmailComposer.CODE_FORGOTTEN);
    }

    @Test
    @DisplayName("should keep going past an email it cannot rewrite, leaving it for the next sweep")
    void shouldSkipAFailedRow_whenReplacingThrows() {
        when(emailLogs.findIdsByTransactionTypeChangedBetween(any(), any(Date.class), any(Date.class))).thenReturn(List.of(1, 2));
        when(emailLogs.replaceBody(1, PortalInviteEmailComposer.CODE_FORGOTTEN))
                .thenThrow(new IllegalStateException("lock wait timeout"));
        when(emailLogs.replaceBody(2, PortalInviteEmailComposer.CODE_FORGOTTEN)).thenReturn(1);

        assertThat(sweeper.forgetLeftoverCodes()).isEqualTo(1);
        verify(emailLogs).replaceBody(2, PortalInviteEmailComposer.CODE_FORGOTTEN);
    }

    @Test
    @DisplayName("should touch no email when none qualifies")
    void shouldChangeNothing_whenNoEmailQualifies() {
        when(emailLogs.findIdsByTransactionTypeChangedBetween(any(), any(Date.class), any(Date.class))).thenReturn(List.of());

        assertThat(sweeper.forgetLeftoverCodes()).isZero();
        verify(emailLogs, never()).replaceBody(any(), any());
    }
}
