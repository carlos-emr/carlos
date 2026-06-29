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

import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.AbstractSearchResponse;
import io.github.carlos_emr.carlos.webserv.rest.to.model.MenuTo1;
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
import java.util.Arrays;
import java.util.List;
import java.util.ResourceBundle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link FormsService} privilege enforcement (#2798).
 *
 * <p>{@code FormsService} is the mixed-security-object case: its endpoints span eForm reads
 * (guarded by {@code _eform}) and SQL-table encounter form reads (guarded by
 * {@code _newCasemgmt.forms}). These tests pin the per-endpoint mapping and verify each endpoint
 * fails closed with the CARLOS paren-form {@link SecurityException} before touching the
 * {@link FormsManager}.</p>
 *
 * @see FormsService
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("FormsService Unit Tests")
@Tag("unit")
@Tag("fast")
class FormsServiceUnitTest extends CarlosUnitTestBase {

    @Mock
    private FormsManager mockFormsManager;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    private FormsService service;

    @BeforeEach
    void setUp() throws Exception {
        Provider provider = mock(Provider.class);
        when(provider.getProviderNo()).thenReturn("101");
        LoggedInInfo loggedInInfo = new LoggedInInfo();
        loggedInInfo.setLoggedInProvider(provider);
        loggedInInfo.setIp("127.0.0.1");

        final LoggedInInfo capturedInfo = loggedInInfo;
        service = new FormsService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return capturedInfo;
            }

            // getFormOptions consults the UI bundle, which would otherwise require a live CXF
            // request context; the bundle value is unused by the method, so a null is sufficient.
            @Override
            protected ResourceBundle getResourceBundle() {
                return null;
            }
        };

        inject("formsManager", mockFormsManager);
        inject("securityInfoManager", mockSecurityInfoManager);
    }

    private void inject(String fieldName, Object value) throws Exception {
        Field field = FormsService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(service, value);
    }

    // --- eForm endpoints: _eform ---

    @Test
    @DisplayName("should return eform group names when caller has _eform read privilege")
    @Tag("read")
    void shouldReturnGroupNames_whenCallerHasEformReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_eform"), eq("r"), any())).thenReturn(true);
        List<String> groups = Arrays.asList("Cardiology", "Diabetes");
        when(mockFormsManager.getGroupNames()).thenReturn(groups);

        AbstractSearchResponse<String> response = service.getGroupNames();

        assertThat(response.getContent()).containsExactly("Cardiology", "Diabetes");
        verify(mockFormsManager).getGroupNames();
    }

    @Test
    @DisplayName("should deny eform group names when caller lacks _eform read privilege")
    @Tag("read")
    void shouldDenyGroupNames_whenCallerLacksEformReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.getGroupNames())
            .isInstanceOf(SecurityException.class)
            .hasMessage("missing required sec object (_eform)");

        verify(mockFormsManager, never()).getGroupNames();
    }

    @Test
    @DisplayName("should deny all eform names when caller lacks _eform read privilege")
    @Tag("read")
    void shouldDenyAllEFormNames_whenCallerLacksEformReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.getAllEFormNames())
            .isInstanceOf(SecurityException.class)
            .hasMessage("missing required sec object (_eform)");

        verify(mockFormsManager, never()).findByStatus(any(), anyBoolean(), any());
    }

    @Test
    @DisplayName("should deny forms-for-heading when caller lacks _eform read privilege")
    @Tag("read")
    void shouldDenyFormsForHeading_whenCallerLacksEformReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.getFormsForHeading(1, "Completed"))
            .isInstanceOf(SecurityException.class)
            .hasMessage("missing required sec object (_eform)");

        verify(mockFormsManager, never()).findByDemographicId(any(), any());
    }

    // --- Encounter-form endpoints: _newCasemgmt.forms ---

    @Test
    @DisplayName("should deny all encounter form names when caller lacks _newCasemgmt.forms read privilege")
    @Tag("read")
    void shouldDenyAllEncounterFormNames_whenCallerLacksFormsReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.getAllFormNames())
            .isInstanceOf(SecurityException.class)
            .hasMessage("missing required sec object (_newCasemgmt.forms)");

        verify(mockFormsManager, never()).getAllEncounterForms();
    }

    @Test
    @DisplayName("should deny selected encounter form names when caller lacks _newCasemgmt.forms read privilege")
    @Tag("read")
    void shouldDenySelectedEncounterFormNames_whenCallerLacksFormsReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.getSelectedFormNames())
            .isInstanceOf(SecurityException.class)
            .hasMessage("missing required sec object (_newCasemgmt.forms)");

        verify(mockFormsManager, never()).getSelectedEncounterForms();
    }

    @Test
    @DisplayName("should deny completed encounter forms (patient PHI) when caller lacks _newCasemgmt.forms read privilege")
    @Tag("read")
    void shouldDenyCompletedEncounterForms_whenCallerLacksFormsReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.getCompletedFormNames("1"))
            .isInstanceOf(SecurityException.class)
            .hasMessage("missing required sec object (_newCasemgmt.forms)");

        verify(mockFormsManager, never()).getAllEncounterForms();
    }

    // --- Generic forms-panel menu: read on either object ---

    @Test
    @DisplayName("should deny form options when caller lacks both eform and forms read privilege")
    @Tag("read")
    void shouldDenyFormOptions_whenCallerLacksBothReadPrivileges() {
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.getFormOptions("1"))
            .isInstanceOf(SecurityException.class)
            .hasMessage("missing required sec object (_eform)");
    }

    @Test
    @DisplayName("should return form options when caller has only _newCasemgmt.forms read privilege")
    @Tag("read")
    void shouldReturnFormOptions_whenCallerHasOnlyFormsReadPrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_eform"), eq("r"), any())).thenReturn(false);
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_newCasemgmt.forms"), eq("r"), any())).thenReturn(true);

        MenuTo1 menu = service.getFormOptions("1");

        assertThat(menu).isNotNull();
    }

    // --- Patient-scoped reads thread the demographic into the privilege check ---

    @Test
    @DisplayName("should scope eform privilege check to the patient when reading forms for a heading")
    @Tag("read")
    void shouldScopePrivilegeToPatient_whenReadingFormsForHeading() {
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.getFormsForHeading(7, "Completed"))
            .isInstanceOf(SecurityException.class);

        verify(mockSecurityInfoManager).hasPrivilege(any(), eq("_eform"), eq("r"), eq("7"));
    }

    @Test
    @DisplayName("should scope forms privilege check to the patient when reading completed encounter forms")
    @Tag("read")
    void shouldScopePrivilegeToPatient_whenReadingCompletedEncounterForms() {
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.getCompletedFormNames("42"))
            .isInstanceOf(SecurityException.class);

        verify(mockSecurityInfoManager).hasPrivilege(any(), eq("_newCasemgmt.forms"), eq("r"), eq("42"));
    }

    @Test
    @DisplayName("should scope form-options privilege check to the patient")
    @Tag("read")
    void shouldScopePrivilegeToPatient_whenReadingFormOptions() {
        when(mockSecurityInfoManager.hasPrivilege(any(), anyString(), anyString(), any())).thenReturn(false);

        assertThatThrownBy(() -> service.getFormOptions("99"))
            .isInstanceOf(SecurityException.class);

        verify(mockSecurityInfoManager).hasPrivilege(any(), eq("_eform"), eq("r"), eq("99"));
        verify(mockSecurityInfoManager).hasPrivilege(any(), eq("_newCasemgmt.forms"), eq("r"), eq("99"));
    }
}
