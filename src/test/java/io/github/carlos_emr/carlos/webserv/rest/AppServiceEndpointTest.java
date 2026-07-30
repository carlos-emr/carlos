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
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.managers.AppManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.AppDefinitionTo1;

/**
 * HTTP-level endpoint tests for {@link AppService} using CXF local transport.
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("AppService REST endpoint tests")
class AppServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private AppManager mockAppManager;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Override
    protected Object getServiceBean() {
        AppService service = new AppService();
        injectDependency(service, "appManager", mockAppManager);
        injectDependency(service, "securityInfoManager", mockSecurityInfoManager);
        return service;
    }

    @Test
    @DisplayName("should return 200 with app definitions")
    void shouldReturn200_whenAppsExist() {
        AppDefinitionTo1 app = new AppDefinitionTo1();
        when(mockAppManager.getAppDefinitions(any(LoggedInInfo.class)))
            .thenReturn(List.of(app));

        Response response = request().path("/app/getApps/").get();

        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    @DisplayName("should return 200 with empty list when no apps")
    void shouldReturn200WithEmptyList_whenNoAppsExist() {
        when(mockAppManager.getAppDefinitions(any(LoggedInInfo.class)))
            .thenReturn(Collections.emptyList());

        Response response = request().path("/app/getApps/").get();

        assertThat(response.getStatus()).isEqualTo(200);
    }
}
