/**
 * Copyright (c) 2026. CARLOS EMR Project. All Rights Reserved.
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.dao.AppDefinitionDao;
import io.github.carlos_emr.carlos.commn.dao.AppUserDao;
import io.github.carlos_emr.carlos.commn.dao.ResourceStorageDao;
import io.github.carlos_emr.carlos.commn.model.ResourceStorage;
import io.github.carlos_emr.carlos.managers.AppManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.prevention.PreventionDS;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * HTTP-level endpoint tests for {@link ResourceService} using CXF local transport.
 *
 * <p>ResourceService uses {@code CarlosProperties.getInstance()} and
 * {@code SecurityInfoManager.hasPrivilege()} internally, so both are mocked.</p>
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("ResourceService REST endpoint tests")
class ResourceServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Mock
    private AppDefinitionDao mockAppDefinitionDao;

    @Mock
    private AppManager mockAppManager;

    @Mock
    private AppUserDao mockAppUserDao;

    @Mock
    private ResourceStorageDao mockResourceStorageDao;

    @Mock
    private PreventionDS mockPreventionDS;

    @Mock
    private CarlosProperties mockCarlosProperties;

    @Override
    protected Object getServiceBean() {
        ResourceService service = new ResourceService();
        injectDependency(service, "securityInfoManager", mockSecurityInfoManager);
        injectDependency(service, "appDefinitionDao", mockAppDefinitionDao);
        injectDependency(service, "appManager", mockAppManager);
        injectDependency(service, "appUserDao", mockAppUserDao);
        injectDependency(service, "resourceStorageDao", mockResourceStorageDao);
        injectDependency(service, "preventionDS", mockPreventionDS);
        registerMock(CarlosProperties.class, mockCarlosProperties);
        return service;
    }

    @BeforeEach
    void setUpSecurity() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_admin"), eq("r"), any()))
            .thenReturn(true);
    }

    @Nested
    @DisplayName("GET /resources/currentPreventionRulesVersion")
    class GetCurrentPreventionRulesVersion {

        @Test
        @DisplayName("should return 200 with default prevention rules version")
        void shouldReturn200_withDefaultVersion() {
            when(mockCarlosProperties.getProperty("PREVENTION_FILE")).thenReturn(null);
            when(mockResourceStorageDao.findActive(ResourceStorage.PREVENTION_RULES)).thenReturn(null);

            Response response = request().path("/resources/currentPreventionRulesVersion").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("GET /resources/currentLuCodesVersion")
    class GetCurrentLuCodesVersion {

        @Test
        @DisplayName("should return 200 with default LU codes version")
        void shouldReturn200_withDefaultVersion() {
            when(mockCarlosProperties.getProperty("odb_formulary_file")).thenReturn(null);
            when(mockResourceStorageDao.findActive(ResourceStorage.LU_CODES)).thenReturn(null);

            Response response = request().path("/resources/currentLuCodesVersion").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
