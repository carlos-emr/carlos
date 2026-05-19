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
import static org.mockito.Mockito.when;

import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.PMmodule.service.ProviderManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;

/**
 * HTTP-level endpoint tests for {@link StatusService} using CXF local transport.
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("StatusService REST endpoint tests")
class StatusServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private ProviderManager mockProviderManager;

    @Override
    protected Object getServiceBean() {
        StatusService service = new StatusService();
        injectDependency(service, "providerManager", mockProviderManager);
        return service;
    }

    @Test
    @DisplayName("should return 200 with provider number when authenticated")
    void shouldReturn200WithProviderNo_whenAuthenticated() {
        when(mockLoggedInInfo.getLoggedInProviderNo()).thenReturn("999998");

        Response response = request().path("/status/checkIfAuthed").get();

        assertThat(response.getStatus()).isEqualTo(200);
    }
}
