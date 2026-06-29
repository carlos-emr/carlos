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
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.PatientListConfigTo1;
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

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

        verify(mockSecurityInfoManager).hasPrivilege(any(), eq("_pref"), eq("u"), isNull());
        // numberOfApptsToShow (>0) and showReason are each saved.
        verify(mockUserPropertyDao, org.mockito.Mockito.times(2)).saveProp(any(io.github.carlos_emr.carlos.commn.model.UserProperty.class));
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
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Access Denied");

        verify(mockUserPropertyDao, never()).saveProp(any(io.github.carlos_emr.carlos.commn.model.UserProperty.class));
    }
}
