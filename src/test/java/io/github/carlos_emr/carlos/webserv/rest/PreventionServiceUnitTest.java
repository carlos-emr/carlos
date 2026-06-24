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

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.carlos_emr.carlos.commn.exception.AccessDeniedException;
import io.github.carlos_emr.carlos.managers.PreventionManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.PreventionResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PreventionService}.
 *
 * <p>Verifies the patient-level {@code _prevention} read privilege check added to the
 * preventive-measure and immunization endpoints so a caller cannot read another patient's
 * prevention data by supplying an arbitrary {@code demographicNo}.
 *
 * @since 2026-06-24
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PreventionService unit tests")
@Tag("unit")
@Tag("fast")
class PreventionServiceUnitTest extends CarlosUnitTestBase {

    @Mock
    private PreventionManager preventionManager;

    @Mock
    private SecurityInfoManager securityInfoManager;

    private PreventionService service;
    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        loggedInInfo = new LoggedInInfo();
        service = new PreventionService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return loggedInInfo;
            }
        };

        injectDependency(service, "preventionManager", preventionManager);
        injectDependency(service, "securityInfoManager", securityInfoManager);
    }

    @Test
    @DisplayName("should deny active preventions when caller lacks prevention read privilege")
    void shouldDenyActivePreventions_whenCallerLacksReadPrivilege() {
        when(securityInfoManager.hasPrivilege(any(), eq("_prevention"), eq("r"), eq(99))).thenReturn(false);

        assertThatThrownBy(() -> service.getCurrentPreventions(99))
                .isInstanceOf(AccessDeniedException.class);

        verify(securityInfoManager).hasPrivilege(eq(loggedInInfo), eq("_prevention"), eq("r"), eq(99));
        verify(preventionManager, never()).getPreventionsByDemographicNo(any(), any());
    }

    @Test
    @DisplayName("should return active preventions when caller has prevention read privilege")
    void shouldReturnActivePreventions_whenCallerHasReadPrivilege() {
        when(securityInfoManager.hasPrivilege(any(), eq("_prevention"), eq("r"), eq(7))).thenReturn(true);
        when(preventionManager.getPreventionsByDemographicNo(any(), eq(7))).thenReturn(Collections.emptyList());

        PreventionResponse response = service.getCurrentPreventions(7);

        assertThat(response).isNotNull();
        assertThat(response.getPreventions()).isEmpty();
        verify(preventionManager).getPreventionsByDemographicNo(eq(loggedInInfo), eq(7));
    }

    @Test
    @DisplayName("should deny immunizations when caller lacks prevention read privilege")
    void shouldDenyImmunizations_whenCallerLacksReadPrivilege() {
        when(securityInfoManager.hasPrivilege(any(), eq("_prevention"), eq("r"), eq(99))).thenReturn(false);

        assertThatThrownBy(() -> service.getImmunizations(99))
                .isInstanceOf(AccessDeniedException.class);

        verify(securityInfoManager).hasPrivilege(eq(loggedInInfo), eq("_prevention"), eq("r"), eq(99));
        verify(preventionManager, never()).getImmunizationsByDemographic(any(), any());
    }

    @Test
    @DisplayName("should return immunizations when caller has prevention read privilege")
    void shouldReturnImmunizations_whenCallerHasReadPrivilege() {
        when(securityInfoManager.hasPrivilege(any(), eq("_prevention"), eq("r"), eq(7))).thenReturn(true);
        when(preventionManager.getImmunizationsByDemographic(any(), eq(7))).thenReturn(Collections.emptyList());

        PreventionResponse response = service.getImmunizations(7);

        assertThat(response).isNotNull();
        assertThat(response.getPreventions()).isEmpty();
        verify(preventionManager).getImmunizationsByDemographic(eq(loggedInInfo), eq(7));
    }
}
