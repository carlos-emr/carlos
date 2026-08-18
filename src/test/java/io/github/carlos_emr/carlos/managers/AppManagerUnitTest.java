/**
 * Copyright (c) 2026 CARLOS EMR Contributors. All Rights Reserved.
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
package io.github.carlos_emr.carlos.managers;

import io.github.carlos_emr.carlos.commn.dao.AppDefinitionDao;
import io.github.carlos_emr.carlos.commn.dao.AppUserDao;
import io.github.carlos_emr.carlos.commn.model.AppDefinition;
import io.github.carlos_emr.carlos.commn.model.AppUser;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.AppDefinitionTo1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link AppManagerImpl}.
 *
 * <p>Verifies app authentication flags are resolved through a single batched
 * provider lookup instead of per-app DAO calls.</p>
 *
 * @since 2026-04-17
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("App Manager Unit Tests")
@Tag("unit")
@Tag("fast")
@Tag("manager")
public class AppManagerUnitTest extends CarlosUnitTestBase {

    @Mock
    private AppDefinitionDao mockAppDefinitionDao;

    @Mock
    private AppUserDao mockAppUserDao;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private LoggedInInfo mockLoggedInInfo;

    private AppManagerImpl appManager;

    @BeforeEach
    void setUp() {
        appManager = new AppManagerImpl();
        injectDependency(appManager, "appDefinitionDao", mockAppDefinitionDao);
        injectDependency(appManager, "appUserDao", mockAppUserDao);
        injectDependency(appManager, "securityInfoManager", mockSecurityInfoManager);
    }

    @Test
    @DisplayName("should batch load authenticated apps for provider")
    void shouldBatchLoadAuthenticatedApps_forProvider() {
        // Given
        AppDefinition appOne = createAppDefinition(10, "One");
        AppDefinition appTwo = createAppDefinition(20, "Two");
        AppDefinition appThree = createAppDefinition(30, "Three");
        AppUser appUserOne = createAppUser(10, "999998");
        AppUser appUserThree = createAppUser(30, "999998");

        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(mockAppDefinitionDao.findAll()).thenReturn(List.of(appOne, appTwo, appThree));
        when(mockAppUserDao.findForProviderByAppIds(List.of(10, 20, 30), "999998"))
                .thenReturn(List.of(appUserOne, appUserThree));

        // When
        List<AppDefinitionTo1> result = appManager.getAppDefinitions(mockLoggedInInfo);

        // Then
        assertThat(result).extracting(AppDefinitionTo1::getId).containsExactly(10, 20, 30);
        assertThat(result).extracting(AppDefinitionTo1::getAuthenticated).containsExactly(true, false, true);
        verify(mockAppUserDao).findForProviderByAppIds(List.of(10, 20, 30), "999998");
        verify(mockAppUserDao, never()).findForProvider(10, "999998");
        verify(mockAppUserDao, never()).findForProvider(20, "999998");
        verify(mockAppUserDao, never()).findForProvider(30, "999998");
    }

    /**
     * Creates an app definition fixture for authentication flag tests.
     *
     * @param id Integer the application identifier
     * @param name String the application display name
     * @return AppDefinition the populated test app definition
     */
    private AppDefinition createAppDefinition(Integer id, String name) {
        AppDefinition appDefinition = new AppDefinition();
        appDefinition.setId(id);
        appDefinition.setName(name);
        appDefinition.setAppType(AppDefinition.OAUTH2_TYPE);
        appDefinition.setActive(true);
        appDefinition.setAddedBy("admin");
        appDefinition.setAdded(new Date());
        return appDefinition;
    }

    /**
     * Creates an authenticated app-user association fixture.
     *
     * @param appId Integer the application identifier
     * @param providerNo String the provider number linked to the app
     * @return AppUser the populated test association
     */
    private AppUser createAppUser(Integer appId, String providerNo) {
        AppUser appUser = new AppUser();
        appUser.setAppId(appId);
        appUser.setProviderNo(providerNo);
        return appUser;
    }
}
