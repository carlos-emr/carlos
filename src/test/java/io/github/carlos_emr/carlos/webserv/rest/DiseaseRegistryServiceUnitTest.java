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

import io.github.carlos_emr.carlos.casemgmt.dao.IssueDAO;
import io.github.carlos_emr.carlos.commn.dao.DxresearchDAO;
import io.github.carlos_emr.carlos.commn.dao.QuickListDao;
import io.github.carlos_emr.carlos.commn.model.Dxresearch;
import io.github.carlos_emr.carlos.commn.model.QuickList;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.DiagnosisTo1;
import io.github.carlos_emr.carlos.webserv.rest.to.model.IssueTo1;
import jakarta.ws.rs.core.Response;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link DiseaseRegistryService} privilege enforcement.
 *
 * <p>Verifies that every endpoint enforces the {@code _newCasemgmt.DxRegistry}
 * security object before reading or writing disease-registry data.</p>
 *
 * @see DiseaseRegistryService
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("DiseaseRegistryService Unit Tests")
@Tag("unit")
@Tag("fast")
class DiseaseRegistryServiceUnitTest extends CarlosUnitTestBase {

    private static final String DX_REGISTRY = "_newCasemgmt.DxRegistry";

    @Mock
    private QuickListDao mockQuickListDao;

    @Mock
    private DxresearchDAO mockDxresearchDao;

    @Mock
    private IssueDAO mockIssueDao;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    private DiseaseRegistryService service;

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
        service = new DiseaseRegistryService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return capturedInfo;
            }
        };

        inject("quickListDao", mockQuickListDao);
        inject("dxresearchDao", mockDxresearchDao);
        inject("issueDao", mockIssueDao);
        inject("securityInfoManager", mockSecurityInfoManager);
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = DiseaseRegistryService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }

    @Test
    @DisplayName("should return quick lists when caller has DxRegistry read privilege")
    @Tag("read")
    void shouldReturnQuickLists_whenCallerHasReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq(DX_REGISTRY), eq("r"), any())).thenReturn(true);
        when(mockQuickListDao.findAll()).thenReturn(Collections.<QuickList>emptyList());

        List<?> result = service.getQuickLists();

        assertThat(result).isEmpty();
        verify(mockQuickListDao).findAll();
    }

    @Test
    @DisplayName("should deny quick lists when caller lacks DxRegistry read privilege")
    @Tag("read")
    void shouldDenyQuickLists_whenCallerLacksReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.getQuickLists())
            .isInstanceOf(SecurityException.class)
            .hasMessage("missing required sec object (_newCasemgmt.DxRegistry)");

        verify(mockQuickListDao, never()).findAll();
    }

    @Test
    @DisplayName("should deny findLikeIssues when caller lacks DxRegistry read privilege")
    @Tag("read")
    void shouldDenyFindLikeIssues_whenCallerLacksReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.findLikeIssues(new DiagnosisTo1()))
            .isInstanceOf(SecurityException.class)
            .hasMessage("missing required sec object (_newCasemgmt.DxRegistry)");

        verify(mockIssueDao, never()).findIssueByTypeAndCode(anyString(), anyString());
    }

    @Test
    @DisplayName("should deny add when caller lacks DxRegistry write privilege")
    @Tag("create")
    void shouldDenyAdd_whenCallerLacksWritePrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.addToDiseaseRegistry(123, new IssueTo1()))
            .isInstanceOf(SecurityException.class)
            .hasMessage("missing required sec object (_newCasemgmt.DxRegistry)");

        verify(mockDxresearchDao, never()).activeEntryExists(anyInt(), anyString(), anyString());
        verify(mockDxresearchDao, never()).persist(any());
    }

    @Test
    @DisplayName("should return disease registry when caller has DxRegistry read privilege")
    @Tag("read")
    void shouldReturnDiseaseRegistry_whenCallerHasReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq(DX_REGISTRY), eq("r"), any())).thenReturn(true);
        when(mockDxresearchDao.getByDemographicNo(123)).thenReturn(Collections.<Dxresearch>emptyList());

        Response response = service.getDiseaseRegistry(123);

        assertThat(response.getStatus()).isEqualTo(Response.Status.OK.getStatusCode());
        verify(mockDxresearchDao).getByDemographicNo(123);
    }

    @Test
    @DisplayName("should deny disease registry when caller lacks DxRegistry read privilege")
    @Tag("read")
    void shouldDenyDiseaseRegistry_whenCallerLacksReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.getDiseaseRegistry(123))
            .isInstanceOf(SecurityException.class)
            .hasMessage("missing required sec object (_newCasemgmt.DxRegistry)");

        verify(mockDxresearchDao, never()).getByDemographicNo(anyInt());
    }
}
