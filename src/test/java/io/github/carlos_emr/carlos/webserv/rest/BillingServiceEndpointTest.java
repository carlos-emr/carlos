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
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.managers.BillingManager;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;

/**
 * HTTP-level endpoint tests for {@link BillingService} using CXF local transport.
 *
 * <p>BillingService uses {@code CarlosProperties.getInstance()} directly, so
 * this test injects a mock CarlosProperties instance via reflection.</p>
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("BillingService REST endpoint tests")
class BillingServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private BillingManager mockBillingManager;

    @Mock
    private CarlosProperties mockCarlosProperties;

    @Override
    protected Object getServiceBean() {
        BillingService service = new BillingService();
        injectDependency(service, "billingManager", mockBillingManager);
        injectDependency(service, "oscarProperties", mockCarlosProperties);
        return service;
    }

    @Nested
    @DisplayName("GET /billing/uniqueServiceTypes")
    class GetUniqueServiceTypes {

        @Test
        @DisplayName("should return 200 with service types")
        void shouldReturn200_whenServiceTypesExist() {
            when(mockBillingManager.getUniqueServiceTypes(any(LoggedInInfo.class)))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/billing/uniqueServiceTypes").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 200 with filtered service types when type provided")
        void shouldReturn200_whenTypeFilterProvided() {
            when(mockBillingManager.getUniqueServiceTypes(any(LoggedInInfo.class), any(String.class)))
                .thenReturn(Collections.emptyList());

            Response response = request().path("/billing/uniqueServiceTypes")
                .query("type", "MSP")
                .get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("GET /billing/billingRegion")
    class GetBillingRegion {

        @Test
        @DisplayName("should return 200 with billing region")
        void shouldReturn200_whenBillingRegionConfigured() {
            when(mockCarlosProperties.getProperty("billregion", "")).thenReturn("BC");

            Response response = request().path("/billing/billingRegion").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 200 with error when region not configured")
        void shouldReturn200WithError_whenRegionNotConfigured() {
            when(mockCarlosProperties.getProperty("billregion", "")).thenReturn("");

            Response response = request().path("/billing/billingRegion").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("GET /billing/defaultView")
    class GetDefaultView {

        @Test
        @DisplayName("should return 200 with default view")
        void shouldReturn200_whenDefaultViewConfigured() {
            when(mockCarlosProperties.getProperty("default_view", "")).thenReturn("summary");

            Response response = request().path("/billing/defaultView").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 200 with error when view not configured")
        void shouldReturn200WithError_whenViewNotConfigured() {
            when(mockCarlosProperties.getProperty("default_view", "")).thenReturn("");

            Response response = request().path("/billing/defaultView").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
