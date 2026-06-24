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
package io.github.carlos_emr.carlos.webserv.oauth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.carlos_emr.carlos.commn.dao.ServiceAccessTokenDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.ws.rs.core.HttpHeaders;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@Tag("unit")
@ExtendWith(MockitoExtension.class)
class OAuthStatusServiceUnitTest extends CarlosUnitTestBase {

    @Mock private ServiceAccessTokenDao serviceAccessTokenDao;
    @Mock private LoggedInInfo loggedInInfo;
    @Mock private HttpHeaders mockHeaders;

    private OAuthStatusService service;

    @BeforeEach
    void setUp() {
        registerMock(ServiceAccessTokenDao.class, serviceAccessTokenDao);

        Provider provider = new Provider();
        provider.setProviderNo("100");
        provider.setFirstName("Jane");
        provider.setLastName("Doe");
        provider.setSpecialty("FM");
        provider.setOhipNo("1234567890");
        provider.setBillingNo("BILL99");
        provider.setAddress("123 Main St");
        provider.setPhone("555-1234");
        provider.setEmail("jane@clinic.ca");

        when(loggedInInfo.getLoggedInProvider()).thenReturn(provider);
        when(mockHeaders.getHeaderString("Authorization")).thenReturn(null);

        service = new OAuthStatusService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return loggedInInfo;
            }
        };
        injectDependency(service, "serviceAccessTokenDao", serviceAccessTokenDao);
    }

    @Test
    @DisplayName("should not expose sensitive provider fields in response")
    void shouldNotExposeSensitiveFields_whenProviderHasSensitiveData() throws Exception {
        String json = service.oauthInfo(mockHeaders);
        JsonNode root = new ObjectMapper().readTree(json);

        assertFalse(root.has("ohipNo"),    "ohipNo must not be exposed");
        assertFalse(root.has("billingNo"), "billingNo must not be exposed");
        assertFalse(root.has("address"),   "address must not be exposed");
        assertFalse(root.has("phone"),     "phone must not be exposed");
        assertFalse(root.has("email"),     "email must not be exposed");
        // provider fields must only appear nested, not at root
        assertFalse(root.has("providerNo"), "providerNo must not be at root level");
        assertFalse(root.has("firstName"),  "firstName must not be at root level");
        assertFalse(root.has("lastName"),   "lastName must not be at root level");
    }

    @Test
    @DisplayName("should include expected provider fields nested under \"provider\" key")
    void shouldIncludeExpectedFields_whenProviderIsAuthenticated() throws Exception {
        String json = service.oauthInfo(mockHeaders);
        JsonNode root = new ObjectMapper().readTree(json);

        JsonNode p = root.get("provider");
        assertNotNull(p, "\"provider\" node must be present");
        assertThat(p.get("providerNo").asText()).isEqualTo("100");
        assertThat(p.get("firstName").asText()).isEqualTo("Jane");
        assertThat(p.get("lastName").asText()).isEqualTo("Doe");
        assertThat(p.get("specialty").asText()).isEqualTo("FM");
        assertThat(root.has("roles")).isTrue();
    }
}
