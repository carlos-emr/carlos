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

import io.github.carlos_emr.carlos.managers.DrugLookUp;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.DrugLookupResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.DrugResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DrugSearchTo1;
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
 * Unit tests for {@link RxLookupService} privilege enforcement.
 *
 * <p>Verifies that the drug-lookup endpoints enforce the {@code _rx} security object
 * before performing any drug-product database lookup or instruction parsing.</p>
 *
 * @see RxLookupService
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RxLookupService Unit Tests")
@Tag("unit")
@Tag("fast")
class RxLookupServiceUnitTest extends CarlosUnitTestBase {

    @Mock
    private DrugLookUp mockDrugLookUpManager;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    private RxLookupService service;

    @BeforeEach
    void setUp() throws Exception {
        Provider provider = mock(Provider.class);
        when(provider.getProviderNo()).thenReturn("101");
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setLoggedInProvider(provider);
        loggedInInfo.setIp("127.0.0.1");

        final LoggedInInfo capturedInfo = loggedInInfo;
        service = new RxLookupService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return capturedInfo;
            }
        };

        inject("drugLookUpManager", mockDrugLookUpManager);
        inject("securityInfoManager", mockSecurityInfoManager);
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = RxLookupService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }

    @Test
    @DisplayName("should perform search when caller has _rx read privilege")
    @Tag("read")
    void shouldPerformSearch_whenCallerHasReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_rx"), eq("r"), any())).thenReturn(true);
        when(mockDrugLookUpManager.search("aspirin")).thenReturn(Collections.<DrugSearchTo1>emptyList());

        DrugLookupResponse response = service.search("aspirin");

        assertThat(response.isSuccess()).isTrue();
        verify(mockDrugLookUpManager).search("aspirin");
    }

    @Test
    @DisplayName("should deny search when caller lacks _rx read privilege")
    @Tag("read")
    void shouldDenySearch_whenCallerLacksReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.search("aspirin"))
            .isInstanceOf(SecurityException.class)
            .hasMessage("missing required sec object (_rx)");

        verify(mockDrugLookUpManager, never()).search(anyString());
    }

    @Test
    @DisplayName("should return details when caller has _rx read privilege")
    @Tag("read")
    void shouldReturnDetails_whenCallerHasReadPrivilege() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_rx"), eq("r"), any())).thenReturn(true);
        when(mockDrugLookUpManager.details("42")).thenReturn(mock(DrugSearchTo1.class));

        DrugLookupResponse response = service.details("42");

        assertThat(response.isSuccess()).isTrue();
        verify(mockDrugLookUpManager).details("42");
    }

    @Test
    @DisplayName("should deny details when caller lacks _rx read privilege")
    @Tag("read")
    void shouldDenyDetails_whenCallerLacksReadPrivilege() throws Exception {
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.details("42"))
            .isInstanceOf(SecurityException.class)
            .hasMessage("missing required sec object (_rx)");

        verify(mockDrugLookUpManager, never()).details(anyString());
    }

    @Test
    @DisplayName("should deny instruction parsing when caller lacks _rx read privilege")
    @Tag("read")
    void shouldDenyParseInstructions_whenCallerLacksReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.parseInstructions("1 tab po daily"))
            .isInstanceOf(SecurityException.class)
            .hasMessage("missing required sec object (_rx)");
    }

    @Test
    @DisplayName("should parse instructions when caller has _rx read privilege")
    @Tag("read")
    void shouldParseInstructions_whenCallerHasReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_rx"), eq("r"), any())).thenReturn(true);

        DrugResponse response = service.parseInstructions("1 tab po daily");

        assertThat(response).isNotNull();
        assertThat(response.getDrug()).isNotNull();
    }
}
