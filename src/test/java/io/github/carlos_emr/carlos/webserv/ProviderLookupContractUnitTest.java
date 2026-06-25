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
package io.github.carlos_emr.carlos.webserv;

import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.carlos_emr.carlos.PMmodule.dao.ProviderDao;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.OscarSearchResponse;
import io.github.carlos_emr.carlos.webserv.transfer_objects.ProviderTransfer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Contract tests for the native CARLOS provider lookup behind the {@code get_providers} row of
 * {@code docs/api/cortico-carlos-compatibility.md}.
 *
 * <p>The matrix marks {@code get_providers} as "Partial": it is <strong>not</strong> a
 * {@code ScheduleService} operation. Native CARLOS satisfies it through SOAP
 * {@code ProviderService.getProviders/getProviders2} or the REST provider routes. These tests
 * exercise both native surfaces with representative successful calls; any literal Cortico/Juno
 * provider operation path is an adapter/proxy responsibility and is out of scope here.</p>
 *
 * <p>Fixtures are synthetic; no PHI, credentials, or live calls are used.</p>
 *
 * @since 2026-06-25
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Native provider lookup contract (get_providers)")
@Tag("unit")
@Tag("webservice")
class ProviderLookupContractUnitTest extends CarlosUnitTestBase {

    @Mock
    private ProviderManager2 providerManager;

    @Mock
    private ProviderDao providerDao;

    private LoggedInInfo loggedInInfo;

    @BeforeEach
    void setUp() {
        loggedInInfo = new LoggedInInfo();
    }

    @Test
    @DisplayName("should read active providers when get_providers maps to SOAP ProviderService.getProviders")
    void shouldReadProviders_whenUsingSoapProviderService() {
        ProviderWs soapService = new ProviderWs() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return loggedInInfo;
            }
        };
        injectDependency(soapService, "providerManager", providerManager);
        when(providerManager.getProviders(loggedInInfo, Boolean.TRUE)).thenReturn(Collections.emptyList());

        ProviderTransfer[] result = soapService.getProviders(true);

        assertThat(result).isEmpty();
        verify(providerManager).getProviders(loggedInInfo, Boolean.TRUE);
    }

    @Test
    @DisplayName("should read active providers when get_providers maps to SOAP ProviderService.getProviders2")
    void shouldReadProviders_whenUsingSoapProviderServiceVersion2() {
        ProviderWs soapService = new ProviderWs() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return loggedInInfo;
            }
        };
        injectDependency(soapService, "providerManager", providerManager);
        when(providerManager.getProviders(loggedInInfo, Boolean.FALSE)).thenReturn(Collections.emptyList());

        ProviderTransfer[] result = soapService.getProviders2(Boolean.FALSE);

        assertThat(result).isEmpty();
        verify(providerManager).getProviders(loggedInInfo, Boolean.FALSE);
    }

    @Test
    @DisplayName("should read active providers when get_providers maps to the REST provider route")
    void shouldReadProviders_whenUsingRestProviderService() {
        io.github.carlos_emr.carlos.webserv.rest.ProviderService restService =
                new io.github.carlos_emr.carlos.webserv.rest.ProviderService();
        injectDependency(restService, "providerDao", providerDao);
        when(providerDao.getActiveProviders()).thenReturn(Collections.emptyList());

        OscarSearchResponse<ProviderTransfer> response = restService.getProviders();

        assertThat(response).isNotNull();
        assertThat(response.getTotal()).isZero();
        assertThat(response.getContent()).isEmpty();
        verify(providerDao).getActiveProviders();
    }
}
