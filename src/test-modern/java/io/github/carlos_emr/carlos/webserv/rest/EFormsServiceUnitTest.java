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

import io.github.carlos_emr.OscarProperties;
import io.github.carlos_emr.carlos.commn.dao.EFormDao.EFormSortOrder;
import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.GenericRestResponse.ResponseStatus;
import io.github.carlos_emr.carlos.webserv.rest.to.RestResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.EFormTo1;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link EFormsService}.
 *
 * <p>Tests the authorization guard and list retrieval behaviour for eForms,
 * images, and database tags. Uses a testable subclass that overrides
 * {@code getLoggedInInfo()} to avoid requiring the CXF HTTP request context.</p>
 *
 * @since 2026-03-14
 * @see EFormsService
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("EFormsService Unit Tests")
@Tag("unit")
@Tag("fast")
class EFormsServiceUnitTest {

    @Mock
    private FormsManager mockFormsManager;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    private LoggedInInfo loggedInInfo;
    private MockedStatic<OscarProperties> oscarPropertiesMock;

    private EFormsService service;

    @BeforeEach
    void setUp() throws Exception {
        // Stub OscarProperties static calls
        oscarPropertiesMock = mockStatic(OscarProperties.class);
        oscarPropertiesMock.when(OscarProperties::getBuildDate).thenReturn("2026-01-01");
        oscarPropertiesMock.when(OscarProperties::getBuildTag).thenReturn("test");
        // Stub getEformImageDirectory to avoid NPE in getEFormImageList when authorised
        oscarPropertiesMock.when(OscarProperties::getInstance).thenReturn(mock(OscarProperties.class));

        // Build a minimal LoggedInInfo
        Provider provider = mock(Provider.class);
        when(provider.getProviderNo()).thenReturn("101");
        loggedInInfo = new LoggedInInfo();
        loggedInInfo.setIp("127.0.0.1");
        Field providerField = LoggedInInfo.class.getDeclaredField("loggedInProvider");
        providerField.setAccessible(true);
        providerField.set(loggedInInfo, provider);

        // Create testable subclass
        LoggedInInfo capturedInfo = loggedInInfo;
        service = new EFormsService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return capturedInfo;
            }
        };

        // Inject mocked dependencies
        injectField(service, "formsManager", mockFormsManager);
        injectField(service, "securityInfoManager", mockSecurityInfoManager);
    }

    @AfterEach
    void tearDown() {
        if (oscarPropertiesMock != null) oscarPropertiesMock.close();
    }

    // ─── getEFormList ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("getEFormList")
    @Tag("read")
    class GetEFormList {

        @Test
        @DisplayName("should return success with eForm list when user has read privilege")
        void shouldReturnSuccessWithEFormList_whenUserHasReadPrivilege() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_eform"), eq("r"), any())).thenReturn(true);
            EForm eForm = buildEForm("Annual Checkup", "<html>c</html>");
            when(mockFormsManager.findByStatus(any(), eq(true), eq(EFormSortOrder.NAME)))
                    .thenReturn(Collections.singletonList(eForm));

            RestResponse<List<EFormTo1>> response = service.getEFormList();

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.SUCCESS);
            assertThat(response.getBody()).hasSize(1);
            assertThat(response.getBody().get(0).getFormName()).isEqualTo("Annual Checkup");
        }

        @Test
        @DisplayName("should return empty list when no eForms exist and user has privilege")
        void shouldReturnEmptyList_whenNoEFormsExistAndUserHasPrivilege() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_eform"), eq("r"), any())).thenReturn(true);
            when(mockFormsManager.findByStatus(any(), anyBoolean(), any()))
                    .thenReturn(Collections.emptyList());

            RestResponse<List<EFormTo1>> response = service.getEFormList();

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.SUCCESS);
            assertThat(response.getBody()).isEmpty();
        }

        @Test
        @DisplayName("should return error response when user lacks read privilege")
        void shouldReturnErrorResponse_whenUserLacksReadPrivilege() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_eform"), eq("r"), any())).thenReturn(false);

            RestResponse<List<EFormTo1>> response = service.getEFormList();

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
            assertThat(response.getError().getMessage()).isEqualTo("Access Denied");
            verify(mockFormsManager, never()).findByStatus(any(), anyBoolean(), any());
        }

        @Test
        @DisplayName("should return list sorted by name when multiple eForms exist")
        void shouldReturnListSortedByName_whenMultipleEFormsExist() {
            when(mockSecurityInfoManager.hasPrivilege(any(), eq("_eform"), eq("r"), any())).thenReturn(true);
            EForm b = buildEForm("Beta Form", "<html>b</html>");
            EForm a = buildEForm("Alpha Form", "<html>a</html>");
            when(mockFormsManager.findByStatus(any(), eq(true), eq(EFormSortOrder.NAME)))
                    .thenReturn(Arrays.asList(a, b));

            RestResponse<List<EFormTo1>> response = service.getEFormList();

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.SUCCESS);
            assertThat(response.getBody()).hasSize(2);
        }
    }

    // ─── getEFormDatabaseTagList ────────────────────────────────────────────────

    @Nested
    @DisplayName("getEFormDatabaseTagList")
    @Tag("read")
    class GetEFormDatabaseTagList {

        @Test
        @DisplayName("should return error response when user lacks read privilege")
        void shouldReturnErrorResponse_whenUserLacksReadPrivilege() {
            when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);

            RestResponse<List<String>> response = service.getEFormDatabaseTagList();

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
            assertThat(response.getError().getMessage()).isEqualTo("Access Denied");
        }
    }

    // ─── getEFormImageList ──────────────────────────────────────────────────────

    @Nested
    @DisplayName("getEFormImageList")
    @Tag("read")
    class GetEFormImageList {

        @Test
        @DisplayName("should return error response when user lacks read privilege")
        void shouldReturnErrorResponse_whenUserLacksReadPrivilege() {
            when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);

            RestResponse<List<String>> response = service.getEFormImageList();

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.ERROR);
            assertThat(response.getError().getMessage()).isEqualTo("Access Denied");
        }
    }

    // ─── Helpers ───────────────────────────────────────────────────────────────

    private EForm buildEForm(String formName, String formHtml) {
        EForm eForm = new EForm();
        eForm.setFormName(formName);
        eForm.setFormHtml(formHtml);
        eForm.setCurrent(true);
        return eForm;
    }

    private void injectField(Object target, String fieldName, Object value) {
        Class<?> clazz = target.getClass();
        while (clazz != null) {
            try {
                Field field = clazz.getDeclaredField(fieldName);
                field.setAccessible(true);
                field.set(target, value);
                return;
            } catch (NoSuchFieldException e) {
                clazz = clazz.getSuperclass();
            } catch (IllegalAccessException e) {
                throw new RuntimeException("Failed to inject field: " + fieldName, e);
            }
        }
        throw new RuntimeException("Field not found: " + fieldName + " in " + target.getClass().getName());
    }
}
