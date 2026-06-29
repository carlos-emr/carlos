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
package io.github.carlos_emr.carlos.webserv.rest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.commn.model.OscarJob;
import io.github.carlos_emr.carlos.managers.OscarJobManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.OscarJobResponse;
import java.util.Collections;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Security tests for {@link OscarJobService}, the {@code /ws/rs/jobs/**} scheduled-job
 * administration endpoints.
 *
 * <p>These endpoints are only meant to be driven from the {@code _admin}-gated Administration
 * job pages. The JSP view guard does not protect the REST endpoint itself, so the service must
 * enforce the {@code _admin} security object directly (read for queries, write for mutations).
 * These tests pin that contract — in particular that the job-type mutator, which persists a
 * caller-supplied className the scheduler later instantiates and runs, refuses callers without
 * {@code _admin} write before touching any persistence.
 *
 * @since 2026-06-29
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OscarJobService _admin authorization tests")
@Tag("unit")
@Tag("rest")
@Tag("security")
class OscarJobServiceSecurityTest {

    @Mock
    private OscarJobManager oscarJobManager;

    @Mock
    private SecurityInfoManager securityInfoManager;

    @Mock
    private LoggedInInfo loggedInInfo;

    private OscarJobService service;

    @BeforeEach
    void setUp() {
        // Spy so the CXF-backed getLoggedInInfo() can be stubbed without a live message chain.
        service = spy(new OscarJobService());
        service.oscarJobManager = oscarJobManager;
        service.securityInfoManager = securityInfoManager;
        doReturn(loggedInInfo).when(service).getLoggedInInfo();
    }

    private void grantAdmin(String action, boolean granted) {
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq(action), nullable(String.class)))
                .thenReturn(granted);
    }

    @Test
    @DisplayName("should deny read endpoint when lacking _admin read")
    void shouldDenyRead_whenLackingAdminRead() {
        grantAdmin("r", false);

        assertThatThrownBy(() -> service.getAllJobs())
                .isInstanceOf(AccessDeniedException.class)
                .satisfies(thrown -> {
                    AccessDeniedException denied = (AccessDeniedException) thrown;
                    assertThat(denied.getPermission()).isEqualTo("_admin");
                    assertThat(denied.getAction()).isEqualTo("r");
                });

        verifyNoInteractions(oscarJobManager);
    }

    @Test
    @DisplayName("should deny saveJob when lacking _admin write")
    void shouldDenySaveJob_whenLackingAdminWrite() {
        grantAdmin("w", false);

        // params is never read: the privilege gate is the first statement in saveJob.
        assertThatThrownBy(() -> service.saveJob(null))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(oscarJobManager);
    }

    @Test
    @DisplayName("should deny saveJobType before persisting any caller-supplied className when lacking _admin write")
    void shouldDenySaveJobType_whenLackingAdminWrite() {
        grantAdmin("w", false);

        assertThatThrownBy(() -> service.saveJobType(null))
                .isInstanceOf(AccessDeniedException.class)
                .satisfies(thrown -> assertThat(((AccessDeniedException) thrown).getAction()).isEqualTo("w"));

        // No job type is persisted for an unauthorized caller (closes the scheduled-execution vector).
        verifyNoInteractions(oscarJobManager);
    }

    @Test
    @DisplayName("should deny enableJob when lacking _admin write")
    void shouldDenyEnableJob_whenLackingAdminWrite() {
        grantAdmin("w", false);

        assertThatThrownBy(() -> service.enableJob(7))
                .isInstanceOf(AccessDeniedException.class);

        verifyNoInteractions(oscarJobManager);
    }

    @Test
    @DisplayName("should delegate to manager when _admin read is granted")
    void shouldDelegateToManager_whenAdminReadGranted() {
        grantAdmin("r", true);
        when(oscarJobManager.getAllJobs(loggedInInfo)).thenReturn(Collections.<OscarJob>emptyList());

        OscarJobResponse response = service.getAllJobs();

        assertThat(response).isNotNull();
        assertThat(response.getJobs()).isEmpty();
    }
}
