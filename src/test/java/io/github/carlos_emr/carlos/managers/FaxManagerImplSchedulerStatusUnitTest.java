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
package io.github.carlos_emr.carlos.managers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.fax.core.FaxSchedulerJob;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/**
 * Unit tests for {@link FaxManagerImpl#getFaxSchedularStatus(LoggedInInfo)}.
 *
 * <p>Pins the three-way status contract surfaced to the admin UI:</p>
 * <ul>
 *   <li>running &rarr; {@code "Scheduler Running"}</li>
 *   <li>not running with an empty/null lastError (benign startup state, no active fax
 *       accounts) &rarr; {@code "Scheduler Idle (No Active Fax Accounts)"}</li>
 *   <li>not running with a recorded lastError &rarr; {@code "Scheduler Stopped (Fatal Error)"}</li>
 * </ul>
 *
 * <p>The endpoint requires READ on {@code _admin.fax.restart}; a missing privilege must
 * fail before the scheduler is consulted.</p>
 *
 * @since 2026-08-21
 * @see FaxManagerImpl
 * @see FaxSchedulerJob
 */
@DisplayName("FaxManagerImpl scheduler status")
@Tag("unit")
@Tag("fax")
@Tag("manager")
class FaxManagerImplSchedulerStatusUnitTest extends CarlosUnitTestBase {

    @Mock private SecurityInfoManager securityInfoManager;
    @Mock private FaxSchedulerJob faxSchedulerJob;
    @Mock private LoggedInInfo loggedInInfo;

    private AutoCloseable mocks;
    private FaxManagerImpl manager;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
        manager = new FaxManagerImpl();
        injectDependency(manager, "securityInfoManager", securityInfoManager);
        injectDependency(manager, "faxSchedulerJob", faxSchedulerJob);

        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_admin.fax.restart"),
                eq(SecurityInfoManager.READ), isNull())).thenReturn(true);
    }

    @AfterEach
    void tearDown() throws Exception {
        if (mocks != null) {
            mocks.close();
        }
    }

    @Test
    @DisplayName("should report running when scheduler is active")
    void shouldReportRunning_whenSchedulerActive() {
        // Given
        when(faxSchedulerJob.isRunning()).thenReturn(true);
        when(faxSchedulerJob.getLastSuccessfulRunEpochMs()).thenReturn(1755700000000L);
        when(faxSchedulerJob.getLastError()).thenReturn("");

        // When
        ObjectNode status = manager.getFaxSchedularStatus(loggedInInfo);

        // Then
        assertThat(status.get("faxSchedularStatus").asText()).isEqualTo("Scheduler Running");
        assertThat(status.get("isRunning").asBoolean()).isTrue();
        assertThat(status.get("lastSuccessfulRunEpochMs").asLong()).isEqualTo(1755700000000L);
        assertThat(status.get("lastError").asText()).isEmpty();
    }

    @Test
    @DisplayName("should report idle when stopped without a recorded error")
    void shouldReportIdle_whenStoppedWithoutError() {
        // Given: benign startup state - no active fax account has ever started the scheduler
        when(faxSchedulerJob.isRunning()).thenReturn(false);
        when(faxSchedulerJob.getLastSuccessfulRunEpochMs()).thenReturn(0L);
        when(faxSchedulerJob.getLastError()).thenReturn("");

        // When
        ObjectNode status = manager.getFaxSchedularStatus(loggedInInfo);

        // Then: idle, not fatal - admins must not be sent chasing a failure that never happened
        assertThat(status.get("faxSchedularStatus").asText())
                .isEqualTo("Scheduler Idle (No Active Fax Accounts)");
        assertThat(status.get("isRunning").asBoolean()).isFalse();
        assertThat(status.get("lastError").asText()).isEmpty();
    }

    @Test
    @DisplayName("should report idle when stopped with a null last error")
    void shouldReportIdle_whenStoppedWithNullLastError() {
        // Given: defensive null path - JSON must still carry an empty string, not "null"
        when(faxSchedulerJob.isRunning()).thenReturn(false);
        when(faxSchedulerJob.getLastError()).thenReturn(null);

        // When
        ObjectNode status = manager.getFaxSchedularStatus(loggedInInfo);

        // Then
        assertThat(status.get("faxSchedularStatus").asText())
                .isEqualTo("Scheduler Idle (No Active Fax Accounts)");
        assertThat(status.get("lastError").asText()).isEmpty();
    }

    @Test
    @DisplayName("should report fatal error when stopped with a recorded last error")
    void shouldReportFatalError_whenStoppedWithLastError() {
        // Given
        when(faxSchedulerJob.isRunning()).thenReturn(false);
        when(faxSchedulerJob.getLastError()).thenReturn("NullPointerException: x");

        // When
        ObjectNode status = manager.getFaxSchedularStatus(loggedInInfo);

        // Then
        assertThat(status.get("faxSchedularStatus").asText())
                .isEqualTo("Scheduler Stopped (Fatal Error)");
        assertThat(status.get("isRunning").asBoolean()).isFalse();
        assertThat(status.get("lastError").asText()).isEqualTo("NullPointerException: x");
    }

    @Test
    @DisplayName("should throw RuntimeException when caller lacks restart read privilege")
    void shouldThrowRuntimeException_whenStatusLacksRestartReadPrivilege() {
        // Given
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_admin.fax.restart"),
                eq(SecurityInfoManager.READ), isNull())).thenReturn(false);

        // When / Then: fails before the scheduler is ever consulted
        assertThatThrownBy(() -> manager.getFaxSchedularStatus(loggedInInfo))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("_admin.fax.restart");
        verifyNoInteractions(faxSchedulerJob);
    }
}
