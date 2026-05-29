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
package io.github.carlos_emr.carlos.webserv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.model.Allergy;
import io.github.carlos_emr.carlos.managers.AllergyManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import java.util.Calendar;
import java.util.Date;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("AllergyWs security tests")
@Tag("unit")
@Tag("fast")
@Tag("webservice")
@Tag("security")
class AllergyWsSecurityTest {

    @Mock
    private AllergyManager allergyManager;

    @Mock
    private SecurityInfoManager securityInfoManager;

    @Mock
    private LoggedInInfo loggedInInfo;

    private AllergyWs service;

    @BeforeEach
    void setUp() {
        service = new AllergyWs() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return loggedInInfo;
            }
        };

        ReflectionTestUtils.setField(service, "allergyManager", allergyManager);
        ReflectionTestUtils.setField(service, "securityInfoManager", securityInfoManager);
    }

    @Test
    @DisplayName("should throw and skip manager when getAllergy is called without _allergy read privilege")
    void shouldThrow_whenGetAllergyPrivilegeDenied() {
        denyAllergyReadPrivilege();

        assertThatThrownBy(() -> service.getAllergy(12))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_allergy");

        verifyNoInteractions(allergyManager);
    }

    @Test
    @DisplayName("should throw and skip manager when getAllergiesUpdatedAfterDate is called without _allergy read privilege")
    void shouldThrow_whenGetAllergiesUpdatedAfterDatePrivilegeDenied() {
        denyAllergyReadPrivilege();

        assertThatThrownBy(() -> service.getAllergiesUpdatedAfterDate(new Date(), 5))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_allergy");

        verifyNoInteractions(allergyManager);
    }

    @Test
    @DisplayName("should throw and skip manager when composite query is called without _allergy read privilege")
    void shouldThrow_whenCompositeQueryPrivilegeDenied() {
        denyAllergyReadPrivilege();

        assertThatThrownBy(() -> service.getAllergiesByProgramProviderDemographicDate(
                1, "999990", 12345, Calendar.getInstance(), 5))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_allergy");

        verifyNoInteractions(allergyManager);
    }

    @Test
    @DisplayName("should throw and skip manager when demographic query is called without _allergy read privilege")
    void shouldThrow_whenDemographicQueryPrivilegeDenied() {
        denyAllergyReadPrivilege();

        assertThatThrownBy(() -> service.getAllergiesByDemographicIdAfter(Calendar.getInstance(), 12345))
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("_allergy");

        verifyNoInteractions(allergyManager);
    }

    @Test
    @DisplayName("should delegate getAllergy when _allergy read privilege is granted")
    void shouldDelegate_whenGetAllergyPrivilegeGranted() {
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_allergy", SecurityInfoManager.READ, null))
                .thenReturn(true);
        Allergy allergy = new Allergy();
        allergy.setDescription("Penicillin");
        when(allergyManager.getAllergy(loggedInInfo, 12)).thenReturn(allergy);

        assertThat(service.getAllergy(12)).isNotNull();

        verify(securityInfoManager).hasPrivilege(loggedInInfo, "_allergy", SecurityInfoManager.READ, null);
        verify(allergyManager).getAllergy(loggedInInfo, 12);
        verifyNoMoreInteractions(allergyManager);
    }

    private void denyAllergyReadPrivilege() {
        when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_allergy"),
                eq(SecurityInfoManager.READ), isNull())).thenReturn(false);
    }
}
