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

import io.github.carlos_emr.carlos.commn.model.DemographicMerged;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.OscarSearchResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DemographicMergedTo1;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DemographicMergeService} privilege enforcement.
 *
 * <p>Verifies that every endpoint enforces the {@code _demographic} security object
 * before delegating to {@link DemographicManager}. Uses a testable subclass that
 * overrides {@code getLoggedInInfo()} to bypass the CXF HTTP request context.</p>
 *
 * @see DemographicMergeService
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DemographicMergeService Unit Tests")
@Tag("unit")
@Tag("fast")
class DemographicMergeServiceUnitTest extends CarlosUnitTestBase {

    @Mock
    private DemographicManager mockDemographicManager;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    private DemographicMergeService service;

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
        service = new DemographicMergeService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return capturedInfo;
            }
        };

        inject("demographicManager", mockDemographicManager);
        inject("securityInfoManager", mockSecurityInfoManager);
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = DemographicMergeService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }

    @Test
    @DisplayName("should return merged ids when caller has _demographic read privilege")
    @Tag("read")
    void shouldReturnMergedIds_whenCallerHasReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_demographic"), eq("r"), any())).thenReturn(true);
        when(mockDemographicManager.getMergedDemographics(any(), eq(5)))
            .thenReturn(Collections.<DemographicMerged>emptyList());

        OscarSearchResponse<DemographicMergedTo1> response = service.getMergedDemographicIds(5);

        assertThat(response).isNotNull();
        verify(mockDemographicManager).getMergedDemographics(any(), eq(5));
    }

    @Test
    @DisplayName("should deny read of merged ids when caller lacks _demographic read privilege")
    @Tag("read")
    void shouldDenyReadOfMergedIds_whenCallerLacksReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.getMergedDemographicIds(5))
            .isInstanceOf(SecurityException.class)
            .hasMessage("missing required sec object (_demographic)");

        verify(mockDemographicManager, never()).getMergedDemographics(any(), anyInt());
    }

    @Test
    @DisplayName("should merge when caller has _demographic write privilege")
    @Tag("update")
    void shouldMerge_whenCallerHasWritePrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_demographic"), eq("w"), any())).thenReturn(true);

        service.mergeDemographic(1, 2);

        verify(mockDemographicManager).mergeDemographics(any(), eq(1), anyList());
    }

    @Test
    @DisplayName("should deny merge when caller lacks _demographic write privilege")
    @Tag("update")
    void shouldDenyMerge_whenCallerLacksWritePrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.mergeDemographic(1, 2))
            .isInstanceOf(SecurityException.class)
            .hasMessage("missing required sec object (_demographic)");

        verify(mockDemographicManager, never()).mergeDemographics(any(), anyInt(), anyList());
    }

    @Test
    @DisplayName("should unmerge when caller has _demographic write privilege")
    @Tag("update")
    void shouldUnmerge_whenCallerHasWritePrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_demographic"), eq("w"), any())).thenReturn(true);

        service.unmergeDemographic(1, 2);

        verify(mockDemographicManager).unmergeDemographics(any(), eq(1), anyList());
    }

    @Test
    @DisplayName("should deny unmerge when caller lacks _demographic write privilege")
    @Tag("update")
    void shouldDenyUnmerge_whenCallerLacksWritePrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.unmergeDemographic(1, 2))
            .isInstanceOf(SecurityException.class)
            .hasMessage("missing required sec object (_demographic)");

        verify(mockDemographicManager, never()).unmergeDemographics(any(), anyInt(), anyList());
    }
}
