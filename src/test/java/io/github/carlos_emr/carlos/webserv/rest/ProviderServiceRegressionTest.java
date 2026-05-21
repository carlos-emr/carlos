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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.transfer_objects.ProviderTransfer;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Regression tests for provider REST service WebApplicationException handling.
 *
 * @since 2026-05-07
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("ProviderService REST exception regression tests")
@Tag("unit")
@Tag("rest")
@Tag("regression")
class ProviderServiceRegressionTest {

    private static final String TEST_PROVIDER_NUMBER = "123";
    private static final String TEST_PROVIDER_FIRST_NAME = "Ada";
    private static final String TEST_PROVIDER_LAST_NAME = "Lovelace";

    @Mock
    private ProviderDao providerDao;

    private ProviderService service;

    @BeforeEach
    void setUp() {
        service = new ProviderService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return new LoggedInInfo();
            }
        };
        service.providerDao = providerDao;
    }

    @Test
    @DisplayName("should return provider for valid ID")
    void shouldReturnProvider_forValidId() {
        when(providerDao.getProvider(TEST_PROVIDER_NUMBER)).thenReturn(provider(TEST_PROVIDER_NUMBER));

        ProviderTransfer response = service.getProvider(TEST_PROVIDER_NUMBER);

        assertThat(response.getProviderNo()).isEqualTo(TEST_PROVIDER_NUMBER);
        assertThat(response.getFirstName()).isEqualTo(TEST_PROVIDER_FIRST_NAME);
        assertThat(response.getLastName()).isEqualTo(TEST_PROVIDER_LAST_NAME);
    }

    @Test
    @DisplayName("should return 404 for missing provider")
    void shouldReturn404_forMissingProvider() {
        when(providerDao.getProvider("missing")).thenReturn(null);

        assertThatThrownBy(() -> service.getProvider("missing"))
                .isInstanceOf(WebApplicationException.class)
                .extracting(exception -> ((WebApplicationException) exception).getResponse().getStatus())
                .isEqualTo(Status.NOT_FOUND.getStatusCode());
    }

    @Test
    @DisplayName("should return 500 for internal error")
    void shouldReturn500_forInternalError() {
        when(providerDao.getActiveProviders()).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(() -> service.getProvidersAsJSON())
                .isInstanceOf(WebApplicationException.class)
                .extracting(exception -> ((WebApplicationException) exception).getResponse().getStatus())
                .isEqualTo(Status.INTERNAL_SERVER_ERROR.getStatusCode());
    }

    private static Provider provider(String providerNo) {
        Provider provider = new Provider(providerNo, TEST_PROVIDER_LAST_NAME, "doctor", "F", "general",
                TEST_PROVIDER_FIRST_NAME);
        provider.setStatus("1");
        return provider;
    }
}
