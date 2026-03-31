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

import jakarta.ws.rs.client.Entity;
import jakarta.ws.rs.core.Response;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.model.ConsentType;
import io.github.carlos_emr.carlos.managers.DemographicManager;
import io.github.carlos_emr.carlos.managers.OscarLogManager;
import io.github.carlos_emr.carlos.managers.PatientConsentManager;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.test.base.CarlosRestTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.model.ConsentTypeTo1;

/**
 * HTTP-level endpoint tests for {@link ConsentService} using CXF local transport.
 *
 * @since 2026-03-31
 * @see CarlosRestTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("rest")
@DisplayName("ConsentService REST endpoint tests")
class ConsentServiceEndpointTest extends CarlosRestTestBase {

    @Mock
    private ProviderDao mockProviderDao;

    @Mock
    private ProviderManager2 mockProviderManager;

    @Mock
    private OscarLogManager mockOscarLogManager;

    @Mock
    private DemographicManager mockDemographicManager;

    @Mock
    private PatientConsentManager mockPatientConsentManager;

    @Override
    protected Object getServiceBean() {
        ConsentService service = new ConsentService();
        injectDependency(service, "providerDao", mockProviderDao);
        injectDependency(service, "providerManager", mockProviderManager);
        injectDependency(service, "oscarLogManager", mockOscarLogManager);
        injectDependency(service, "demographicManager", mockDemographicManager);
        injectDependency(service, "patientConsentManager", mockPatientConsentManager);
        return service;
    }

    @Nested
    @DisplayName("GET /consentService/consentTypes")
    class GetActiveConsentTypes {

        @Test
        @DisplayName("should return 200 with consent types")
        void shouldReturn200_whenConsentTypesExist() {
            ConsentType ct = new ConsentType();
            ct.setId(1);
            ct.setName("Informed Consent");
            ct.setActive(true);
            when(mockPatientConsentManager.getActiveConsentTypes()).thenReturn(List.of(ct));

            Response response = request().path("/consentService/consentTypes").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 200 with empty list when no consent types")
        void shouldReturn200WithEmptyList_whenNoConsentTypes() {
            when(mockPatientConsentManager.getActiveConsentTypes()).thenReturn(Collections.emptyList());

            Response response = request().path("/consentService/consentTypes").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }

    @Nested
    @DisplayName("GET /consentService/consentType/{id}")
    class GetConsentType {

        @Test
        @DisplayName("should return 200 with consent type when found")
        void shouldReturn200_whenConsentTypeFound() {
            ConsentType ct = new ConsentType();
            ct.setId(1);
            ct.setName("Test Consent");
            ct.setActive(true);
            when(mockPatientConsentManager.getConsentTypeByConsentTypeId(eq(1))).thenReturn(ct);

            Response response = request().path("/consentService/consentType/1").get();

            assertThat(response.getStatus()).isEqualTo(200);
        }

        @Test
        @DisplayName("should return 404 when consent type not found")
        void shouldReturn404_whenConsentTypeNotFound() {
            when(mockPatientConsentManager.getConsentTypeByConsentTypeId(eq(999))).thenReturn(null);

            Response response = request().path("/consentService/consentType/999").get();

            assertThat(response.getStatus()).isEqualTo(404);
        }
    }

    @Nested
    @DisplayName("POST /consentService/consentType")
    class AddConsentType {

        @Test
        @DisplayName("should return 200 when consent type added successfully")
        void shouldReturn200_whenConsentTypeAdded() {
            ConsentTypeTo1 input = new ConsentTypeTo1();
            input.setName("New Consent");
            input.setDescription("Test description");
            input.setType("1");
            input.setActive(true);

            Response response = request().path("/consentService/consentType")
                .post(Entity.json(input));

            assertThat(response.getStatus()).isEqualTo(200);
        }
    }
}
