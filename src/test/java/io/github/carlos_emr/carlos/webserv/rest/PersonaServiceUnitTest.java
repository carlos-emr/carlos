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

import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.commn.model.UserProperty;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.PatientListConfigTo1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.lang.reflect.Field;
import java.util.List;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.carlos_emr.carlos.email.core.EmailFooterService;
import io.github.carlos_emr.carlos.email.core.EmailData;
import io.github.carlos_emr.carlos.webserv.rest.to.ResponseStatus;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.doThrow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link PersonaService}.
 *
 * <p>Focused on the privilege enforcement added for #2798: {@code saveMyPatientListConfig}
 * persists the caller's own provider preferences and must require {@code _pref}/{@code u}, matching
 * the sibling preference mutators {@code updatePreference}/{@code updatePreferences} in the same
 * class. The four authorization-primitive endpoints ({@code /rights}, {@code /hasRight},
 * {@code /hasRights}, {@code /isAllowedAccessToPatientRecord}) are deliberately left ungated
 * (self-scoped privilege API the UI consumes) and are not exercised here.</p>
 *
 * <p>Uses a testable subclass that overrides {@code getLoggedInInfo()} to avoid requiring the CXF
 * HTTP request context at test time; {@code UserPropertyDAO} is supplied via the SpringUtils mock
 * registry from {@link CarlosUnitTestBase}.</p>
 *
 * @since 2026-06-29
 * @see PersonaService
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("PersonaService Unit Tests")
@Tag("unit")
@Tag("fast")
class PersonaServiceUnitTest extends CarlosUnitTestBase {

    private static final String PROVIDER_NO = "101";

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private UserPropertyDAO mockUserPropertyDao;

    private LoggedInInfo loggedInInfo;
    private PersonaService service;

    @BeforeEach
    void setUp() throws Exception {
        Provider provider = mock(Provider.class);
        when(provider.getProviderNo()).thenReturn(PROVIDER_NO);

        loggedInInfo = new LoggedInInfo();
        Field providerField = LoggedInInfo.class.getDeclaredField("loggedInProvider");
        providerField.setAccessible(true);
        providerField.set(loggedInInfo, provider);
        loggedInInfo.setIp("127.0.0.1");

        LoggedInInfo capturedInfo = loggedInInfo;
        service = new PersonaService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return capturedInfo;
            }
        };

        injectDependency(service, "securityInfoManager", mockSecurityInfoManager);
        registerMock(UserPropertyDAO.class, mockUserPropertyDao);
    }

    @Test
    @Tag("create")
    @DisplayName("should persist provider preferences when caller has _pref update privilege")
    void shouldPersistProviderPreferences_whenCallerHasPrefUpdatePrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_pref"), eq("u"), isNull())).thenReturn(true);
        when(mockUserPropertyDao.getProp(eq(PROVIDER_NO), any())).thenReturn(null);

        PatientListConfigTo1 config = new PatientListConfigTo1();
        config.setNumberOfApptstoShow(10);
        config.setShowReason(true);

        service.saveMyPatientListConfig(config);

        // The privilege check must be made against the current caller's own context, not some
        // other subject — capture and assert the LoggedInInfo rather than matching any().
        ArgumentCaptor<LoggedInInfo> infoCaptor = ArgumentCaptor.forClass(LoggedInInfo.class);
        verify(mockSecurityInfoManager).hasPrivilege(infoCaptor.capture(), eq("_pref"), eq("u"), isNull());
        assertThat(infoCaptor.getValue()).isSameAs(loggedInInfo);
        // Assert the exact properties persisted (provider, name, value), not just the save count,
        // so the test fails if the wrong preference is written.
        ArgumentCaptor<UserProperty> propCaptor = ArgumentCaptor.forClass(UserProperty.class);
        verify(mockUserPropertyDao, times(2)).saveProp(propCaptor.capture());
        List<UserProperty> saved = propCaptor.getAllValues();
        assertThat(saved)
                .extracting(UserProperty::getProviderNo, UserProperty::getName, UserProperty::getValue)
                .containsExactlyInAnyOrder(
                        tuple(PROVIDER_NO, "patientListConfig.numberOfApptsToShow", "10"),
                        tuple(PROVIDER_NO, "patientListConfig.showReason", "true"));
    }

    @Test
    @Tag("create")
    @DisplayName("should enforce privilege but skip the appointment count when it is not a positive number")
    void shouldSkipAppointmentCount_whenNotPositive() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_pref"), eq("u"), isNull())).thenReturn(true);
        when(mockUserPropertyDao.getProp(eq(PROVIDER_NO), any())).thenReturn(null);

        PatientListConfigTo1 config = new PatientListConfigTo1();
        config.setNumberOfApptstoShow(0); // not > 0 → count is not persisted
        config.setShowReason(true);

        service.saveMyPatientListConfig(config);

        // privilege is still enforced, and the single persisted property is showReason — assert
        // the exact property (not just the count) so the test fails if the count "0" were saved
        // instead of the count branch being skipped.
        verify(mockSecurityInfoManager).hasPrivilege(any(), eq("_pref"), eq("u"), isNull());
        ArgumentCaptor<UserProperty> propCaptor = ArgumentCaptor.forClass(UserProperty.class);
        verify(mockUserPropertyDao, times(1)).saveProp(propCaptor.capture());
        assertThat(propCaptor.getValue())
                .extracting(UserProperty::getProviderNo, UserProperty::getName, UserProperty::getValue)
                .containsExactly(PROVIDER_NO, "patientListConfig.showReason", "true");
    }

    @Test
    @Tag("security")
    @DisplayName("should throw and persist nothing when caller lacks _pref update privilege")
    void shouldThrowAndPersistNothing_whenCallerLacksPrefUpdatePrivilege() {
        when(mockSecurityInfoManager.hasPrivilege(any(), eq("_pref"), eq("u"), isNull())).thenReturn(false);

        PatientListConfigTo1 config = new PatientListConfigTo1();
        config.setNumberOfApptstoShow(10);
        config.setShowReason(true);

        assertThatThrownBy(() -> service.saveMyPatientListConfig(config))
                .isInstanceOf(SecurityException.class)
                .hasMessage("missing required sec object (_pref)");

        verify(mockUserPropertyDao, never()).saveProp(any(UserProperty.class));
    }
    private ObjectNode preference(String key,String value) {
        var json=new ObjectMapper().createObjectNode();json.put("key",key);json.put("value",value);return json;
    }
    private UserProperty allowExistingFooter() {
        when(mockSecurityInfoManager.hasPrivilege(any(),eq("_pref"),eq("u"),isNull())).thenReturn(true);
        when(mockSecurityInfoManager.hasPrivilege(any(),eq("_email"),eq(SecurityInfoManager.WRITE),isNull())).thenReturn(true);
        var row=new UserProperty();row.setId(7);row.setName("email_footer");row.setProviderNo(PROVIDER_NO);row.setValue("Old personal");
        when(mockUserPropertyDao.getProp(PROVIDER_NO,"email_footer")).thenReturn(row);
        when(mockUserPropertyDao.findPersonalEmailFooterForUpdate(PROVIDER_NO)).thenReturn(List.of(row));
        registerMock(EmailFooterService.class,new EmailFooterService(mockUserPropertyDao));return row;
    }
    @Test void shouldDenyLegacyFooterUpdate_whenEmailWriteIsMissing() {
        allowExistingFooter();
        when(mockSecurityInfoManager.hasPrivilege(any(),eq("_email"),eq(SecurityInfoManager.WRITE),isNull())).thenReturn(false);
        assertThatThrownBy(()->service.updatePreference(preference("email_footer","Forged personal")))
                .isInstanceOf(jakarta.ws.rs.ForbiddenException.class);
        verifyNoInteractions(mockUserPropertyDao);
    }
    @Test void shouldDenyLegacyFooterUpdate_whenPreferencePermissionIsMissing() {
        allowExistingFooter();
        when(mockSecurityInfoManager.hasPrivilege(any(),eq("_pref"),eq("u"),isNull())).thenReturn(false);
        assertThatThrownBy(()->service.updatePreference(preference("email_footer","Forged personal")))
                .isInstanceOf(RuntimeException.class).hasMessage("Access Denied");
        verifyNoInteractions(mockUserPropertyDao);
    }
    @Test void shouldCleanLegacyFooterUpdate_forCurrentOwnerDespiteForgedOwnerAndClinicFields() {
        var row=allowExistingFooter();
        var json=preference("email_footer","<b>Personal</b><script>unsafe()</script>");
        json.put("providerNo","202");json.put("clinicFooter","Forged clinic");
        assertThat(service.updatePreference(json).getStatus()).isEqualTo(ResponseStatus.SUCCESS);
        assertThat(row.getValue()).isEqualTo("<b>Personal</b>");
        verify(mockUserPropertyDao).lockPersonalEmailFooterOwner(PROVIDER_NO);
        verify(mockUserPropertyDao).savePersonalEmailFooterRow(PROVIDER_NO,row);
        verify(mockUserPropertyDao,never()).merge(any());
        verify(mockUserPropertyDao,never()).findClinicEmailFooter();
    }
    @Test void shouldKeepLegacyFooterUnchanged_whenValueIsTooLong() {
        var row=allowExistingFooter();
        var result=service.updatePreference(preference("email_footer","x".repeat(EmailData.FOOTER_MAX_LENGTH+1)));
        assertThat(result.getStatus()).isEqualTo(ResponseStatus.ERROR);
        assertThat(result.getError().getMessage()).isEqualTo("Personal footer is too long");
        assertThat(row.getValue()).isEqualTo("Old personal");
        verify(mockUserPropertyDao,never()).lockPersonalEmailFooterOwner(any());
        verify(mockUserPropertyDao,never()).merge(any());
    }
    @Test void shouldKeepLegacyFooterUnchanged_whenValueIsMissingNullOrNontext() {
        var row=allowExistingFooter();
        var missing=preference("email_footer","");missing.remove("value");
        var explicitNull=preference("email_footer","");explicitNull.putNull("value");
        var number=preference("email_footer","");number.put("value",123);
        for(var json:List.of(missing,explicitNull,number))
            assertThat(service.updatePreference(json).getStatus()).isEqualTo(ResponseStatus.ERROR);
        assertThat(row.getValue()).isEqualTo("Old personal");
        verify(mockUserPropertyDao,never()).lockPersonalEmailFooterOwner(any());
        verify(mockUserPropertyDao,never()).merge(any());
    }
    @Test void shouldClearOnlyOwnLegacyFooter_whenExplicitBlankIsAuthorized() {
        allowExistingFooter();
        assertThat(service.updatePreference(preference("email_footer","")).getStatus()).isEqualTo(ResponseStatus.SUCCESS);
        verify(mockUserPropertyDao).deletePersonalEmailFooterRow(PROVIDER_NO,7);
        verify(mockUserPropertyDao,never()).merge(any());
        verify(mockUserPropertyDao,never()).findClinicEmailFooter();
    }
    @Test void shouldPreserveOrdinaryPreferenceUpdates_withoutEmailWritePermission() {
        allowExistingFooter();
        when(mockSecurityInfoManager.hasPrivilege(any(),eq("_email"),eq(SecurityInfoManager.WRITE),isNull())).thenReturn(false);
        var row=new UserProperty();row.setId(9);row.setProviderNo(PROVIDER_NO);row.setName("dashboard.expiredTicklersOnly");
        when(mockUserPropertyDao.getProp(PROVIDER_NO,row.getName())).thenReturn(row);
        assertThat(service.updatePreference(preference(row.getName(),"true")).getStatus()).isEqualTo(ResponseStatus.SUCCESS);
        assertThat(row.getValue()).isEqualTo("true");verify(mockUserPropertyDao).merge(row);
        verify(mockSecurityInfoManager,never()).hasPrivilege(any(),eq("_email"),eq(SecurityInfoManager.WRITE),isNull());
        verify(mockUserPropertyDao,never()).lockPersonalEmailFooterOwner(any());
    }
    @Test void shouldPreserveMissingPreferenceResponse_whenLegacyFooterHasNoRow() {
        allowExistingFooter();when(mockUserPropertyDao.getProp(PROVIDER_NO,"email_footer")).thenReturn(null);
        var result=service.updatePreference(preference("email_footer","Personal"));
        assertThat(result.getStatus()).isEqualTo(ResponseStatus.ERROR);
        assertThat(result.getError().getMessage()).isEqualTo("Preference not found");
        verify(mockUserPropertyDao,never()).lockPersonalEmailFooterOwner(any());
    }
    @Test void shouldReturnNeutralSaveError_whenLegacyFooterHitsTransactionConflict() {
        var row=allowExistingFooter();
        doThrow(new jakarta.persistence.OptimisticLockException("FAKE private exception detail"))
                .when(mockUserPropertyDao).lockPersonalEmailFooterOwner(PROVIDER_NO);
        var result=service.updatePreference(preference("email_footer","Personal"));
        assertThat(result.getStatus()).isEqualTo(ResponseStatus.ERROR);
        assertThat(result.getError().getMessage()).isEqualTo("Personal footer could not be saved; try again");
        assertThat(row.getValue()).isEqualTo("Old personal");
        verify(mockUserPropertyDao,never()).savePersonalEmailFooterRow(any(),any());
        verify(mockUserPropertyDao,never()).merge(any());
    }

}
