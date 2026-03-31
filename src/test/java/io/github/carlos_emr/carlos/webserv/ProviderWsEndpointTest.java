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
package io.github.carlos_emr.carlos.webserv;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

import io.github.carlos_emr.carlos.commn.model.Property;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.test.base.CarlosSoapTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.transfer_objects.ProviderPropertyTransfer;
import io.github.carlos_emr.carlos.webserv.transfer_objects.ProviderTransfer;

/**
 * SOAP endpoint tests for {@link ProviderWs} using CXF local transport.
 *
 * @since 2026-03-31
 * @see CarlosSoapTestBase
 */
@Tag("unit")
@Tag("endpoint")
@Tag("soap")
@DisplayName("ProviderWs SOAP endpoint tests")
class ProviderWsEndpointTest extends CarlosSoapTestBase {

    @Mock
    private ProviderManager2 mockProviderManager;

    @Mock
    private Provider mockProvider;

    @Override
    protected Object getServiceBean() {
        ProviderWs ws = new ProviderWs();
        injectDependency(ws, "providerManager", mockProviderManager);
        return ws;
    }

    @Override
    protected Class<?> getServiceInterface() {
        return ProviderWs.class;
    }

    @BeforeEach
    void setUpProvider() {
        when(mockLoggedInInfo.getLoggedInProvider()).thenReturn(mockProvider);
        when(mockProvider.getProviderNo()).thenReturn("999998");
        when(mockProvider.getFirstName()).thenReturn("Test");
        when(mockProvider.getLastName()).thenReturn("Doctor");
    }

    @Test
    @DisplayName("should return logged-in provider transfer via SOAP")
    void shouldReturnLoggedInProviderTransfer_viaSoap() {
        ProviderWs proxy = createClient(ProviderWs.class);
        ProviderTransfer result = proxy.getLoggedInProviderTransfer();

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("should return active providers via SOAP")
    void shouldReturnActiveProviders_viaSoap() {
        Provider provider = new Provider();
        provider.setProviderNo("100");
        provider.setFirstName("Jane");
        provider.setLastName("Smith");
        when(mockProviderManager.getProviders(any(LoggedInInfo.class), eq(true)))
            .thenReturn(List.of(provider));

        ProviderWs proxy = createClient(ProviderWs.class);
        ProviderTransfer[] results = proxy.getProviders2(true);

        assertThat(results).isNotEmpty();
    }

    @Test
    @DisplayName("should return empty array when no providers match")
    void shouldReturnEmptyArray_whenNoProvidersMatch() {
        when(mockProviderManager.getProviders(any(LoggedInInfo.class), eq(false)))
            .thenReturn(Collections.emptyList());

        ProviderWs proxy = createClient(ProviderWs.class);
        ProviderTransfer[] results = proxy.getProviders2(false);

        assertThat(results).isEmpty();
    }

    @Test
    @DisplayName("should return provider properties via SOAP")
    void shouldReturnProviderProperties_viaSoap() {
        Property property = new Property();
        when(mockProviderManager.getProviderProperties(any(LoggedInInfo.class), eq("100"), eq("rxAddress")))
            .thenReturn(List.of(property));

        ProviderWs proxy = createClient(ProviderWs.class);
        ProviderPropertyTransfer[] results = proxy.getProviderProperties("100", "rxAddress");

        assertThat(results).isNotEmpty();
    }
}
