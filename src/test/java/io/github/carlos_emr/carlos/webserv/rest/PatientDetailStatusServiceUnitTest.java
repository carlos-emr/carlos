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

import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.commn.model.Demographic;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.GenericRestResponse.ResponseStatus;
import io.github.carlos_emr.carlos.webserv.rest.to.RestResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ValidateHCRequestTo1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PatientDetailStatusService} privilege enforcement.
 *
 * <p>Verifies that the patient-detail-status endpoints enforce the {@code _demographic}
 * security object before reading demographic data, validating, or searching health cards.</p>
 *
 * @see PatientDetailStatusService
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PatientDetailStatusService Unit Tests")
@Tag("unit")
@Tag("fast")
class PatientDetailStatusServiceUnitTest extends CarlosUnitTestBase {

    @Mock
    private DemographicManager mockDemographicManager;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    private PatientDetailStatusService service;

    @BeforeEach
    void setUp() throws Exception {
        Provider provider = mock(Provider.class);
        when(provider.getProviderNo()).thenReturn("101");
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        Field providerField = LoggedInInfo.class.getDeclaredField("loggedInProvider");
        providerField.setAccessible(true);
        providerField.set(loggedInInfo, provider);
        loggedInInfo.setIp("127.0.0.1");

        final LoggedInInfo capturedInfo = loggedInInfo;
        service = new PatientDetailStatusService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return capturedInfo;
            }
        };

        inject("demographicManager", mockDemographicManager);
        inject("securityInfoManager", mockSecurityInfoManager);
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = PatientDetailStatusService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }

    @Test
    @DisplayName("should deny status when caller lacks _demographic read privilege")
    @Tag("read")
    void shouldDenyStatus_whenCallerLacksReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.getStatus(1))
            .isInstanceOf(SecurityException.class)
            .hasMessage("missing required sec object (_demographic)");
    }

    @Test
    @DisplayName("should deny health-card validation when caller lacks _demographic read privilege")
    @Tag("read")
    void shouldDenyValidateHC_whenCallerLacksReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);

        ValidateHCRequestTo1 request = new ValidateHCRequestTo1();
        request.setHin("1234567890");

        assertThatThrownBy(() -> service.validateHC(request))
            .isInstanceOf(SecurityException.class)
            .hasMessage("missing required sec object (_demographic)");
    }

    @Test
    @DisplayName("should return uniqueness result when caller has _demographic read privilege")
    @Tag("read")
    void shouldReturnUniquenessResult_whenCallerHasReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_demographic"), eq("r"), any())).thenReturn(true);
        when(mockDemographicManager.searchByHealthCard(any(), eq("1234567890")))
            .thenReturn(Collections.<Demographic>emptyList());

        RestResponse<String> response = service.isUniqueHC("1234567890", 5);

        assertThat(response.getStatus()).isEqualTo(ResponseStatus.SUCCESS);
        verify(mockDemographicManager).searchByHealthCard(any(), eq("1234567890"));
    }

    @Test
    @DisplayName("should deny uniqueness check when caller lacks _demographic read privilege")
    @Tag("read")
    void shouldDenyUniquenessCheck_whenCallerLacksReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.isUniqueHC("1234567890", 5))
            .isInstanceOf(SecurityException.class)
            .hasMessage("missing required sec object (_demographic)");

        verify(mockDemographicManager, never()).searchByHealthCard(any(), anyString());
    }
}
