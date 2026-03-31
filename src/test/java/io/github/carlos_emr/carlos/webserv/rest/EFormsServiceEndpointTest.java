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

import java.util.Collections;
import java.util.List;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.model.EForm;
import io.github.carlos_emr.carlos.managers.FormsManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * HTTP-level endpoint tests for {@link EFormsService} using CXF local transport.
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("EFormsService REST endpoint tests")
class EFormsServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private FormsManager mockFormsManager;

    @Mock
    private SecurityInfoManager mockSecurityInfoManager;

    @Override
    protected Object getServiceBean() {
        EFormsService service = new EFormsService();
        injectDependency(service, "formsManager", mockFormsManager);
        injectDependency(service, "securityInfoManager", mockSecurityInfoManager);
        return service;
    }

    @BeforeEach
    void setUpSecurity() {
        when(mockSecurityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_eform"), eq("r"), any()))
            .thenReturn(true);
    }

    @Nested
    @DisplayName("GET /eforms/")
    class GetEFormList {

        @Test
        @DisplayName("should return 200 with eForm list")
        void shouldReturn200_whenEFormsExist() {
            EForm eform = new EForm();
            eform.setId(1);
            eform.setFormName("Test Form");
            when(mockFormsManager.findByStatus(any(LoggedInInfo.class), eq(true), any()))
                .thenReturn(List.of(eform));

            Response response = request().path("/eforms/").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 200 with empty list when no eForms")
        void shouldReturn200WithEmptyList_whenNoEFormsExist() {
            when(mockFormsManager.findByStatus(any(LoggedInInfo.class), eq(true), any()))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/eforms/").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
