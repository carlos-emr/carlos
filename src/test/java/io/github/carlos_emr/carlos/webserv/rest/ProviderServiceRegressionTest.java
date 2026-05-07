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
@Tag("integration")
@Tag("rest")
@Tag("regression")
class ProviderServiceRegressionTest {

    @Mock
    private ProviderDao providerDao;

    private ProviderService service;

    @BeforeEach
    void setUp() {
        service = new ProviderService();
        service.providerDao = providerDao;
    }

    @Test
    @DisplayName("should return provider for valid ID")
    void shouldReturnProvider_forValidId() {
        when(providerDao.getProvider("123")).thenReturn(provider("123"));

        ProviderTransfer response = service.getProvider("123");

        assertThat(response.getProviderNo()).isEqualTo("123");
        assertThat(response.getFirstName()).isEqualTo("Ada");
        assertThat(response.getLastName()).isEqualTo("Lovelace");
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

        assertThatThrownBy(() -> service.getProviders())
                .isInstanceOf(WebApplicationException.class)
                .extracting(exception -> ((WebApplicationException) exception).getResponse().getStatus())
                .isEqualTo(Status.INTERNAL_SERVER_ERROR.getStatusCode());
    }

    private static Provider provider(String providerNo) {
        Provider provider = new Provider(providerNo, "Lovelace", "doctor", "F", "general", "Ada");
        provider.setStatus("1");
        return provider;
    }
}
