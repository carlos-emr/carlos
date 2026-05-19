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

import io.github.carlos_emr.carlos.commn.dao.EFormDao;
import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * HTTP-level endpoint tests for {@link EFormService} using CXF local transport.
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("EFormService REST endpoint tests")
class EFormServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private EFormDao mockEFormDao;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Override
    protected Object getServiceBean() {
        EFormService service = new EFormService();
        injectDependency(service, "eFormDao", mockEFormDao);
        injectDependency(service, "securityInfoManager", mockSecurityInfoManager);
        return service;
    }

    @BeforeEach
    void setUpSecurity() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_eform"), any(), any()))
            .thenReturn(true);
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");
        when(mockLoggedInInfo.getIp()).thenReturn("127.0.0.1");
    }

    @Nested
    @DisplayName("GET /eform/{dataId}")
    class LoadEForm {

        @Test
        @DisplayName("should return 200 with eForm when found")
        void shouldReturn200_whenEFormFound() {
            EForm eform = new EForm();
            eform.setFormName("Test Form");
            eform.setFormHtml("<html></html>");
            when(mockEFormDao.findById(eq(1))).thenReturn(eform);

            Response response = request().path("/eform/1").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 200 with error when eForm not found")
        void shouldReturn200WithError_whenEFormNotFound() {
            when(mockEFormDao.findById(eq(999))).thenReturn(null);

            Response response = request().path("/eform/999").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("POST /eform/json")
    class SaveEFormJson {

        @Test
        @DisplayName("should return 200 when saving valid eForm JSON")
        void shouldReturn200_whenSavingValidJson() {
            when(mockEFormDao.findByName(any())).thenReturn(null);

            String json = "{\"formName\":\"New Form\",\"formHtml\":\"<html>test</html>\"}";

            Response response = request().path("/eform/json")
                .post(jakarta.ws.rs.client.Entity.json(json));

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 200 with error when name already exists")
        void shouldReturn200WithError_whenNameAlreadyExists() {
            EForm existing = new EForm();
            existing.setFormName("Existing Form");
            when(mockEFormDao.findByName("Existing Form")).thenReturn(existing);

            String json = "{\"formName\":\"Existing Form\",\"formHtml\":\"<html>test</html>\"}";

            Response response = request().path("/eform/json")
                .post(jakarta.ws.rs.client.Entity.json(json));

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
