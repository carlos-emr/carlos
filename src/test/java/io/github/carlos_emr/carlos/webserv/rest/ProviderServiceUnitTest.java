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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.managers.ProviderManager2;
import io.github.carlos_emr.carlos.managers.model.ProviderSettings;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.webserv.rest.to.GenericRestResponse.ResponseStatus;
import io.github.carlos_emr.carlos.webserv.rest.to.RestResponse;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response.Status;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

/**
 * Unit tests for {@link ProviderService}, focused on the authorization contract of
 * {@code saveProviderSettings} (issue #2821 — IDOR).
 *
 * <p>Uses a testable subclass that overrides {@code getLoggedInInfo()} to bypass the
 * CXF HTTP request context, and injects mocked collaborators via the package-private
 * fields (this test lives in the same package as {@link ProviderService}).</p>
 *
 * @since 2026-07-21
 * @see ProviderService
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("ProviderService Unit Tests")
@Tag("unit")
@Tag("fast")
class ProviderServiceUnitTest extends CarlosUnitTestBase {

    private static final String CALLER_PROVIDER_NO = "999998";
    private static final String OTHER_PROVIDER_NO = "1";

    @Mock
    private ProviderManager2 mockProviderManager;

    private LoggedInInfo loggedInInfo;
    private ProviderService service;
    private MockedStatic<CarlosProperties> carlosPropertiesMock;

    @BeforeEach
    void setUp() {
        // RestResponse header construction reads these static properties.
        carlosPropertiesMock = mockStatic(CarlosProperties.class);
        carlosPropertiesMock.when(CarlosProperties::getBuildDate).thenReturn("2026-01-01");
        carlosPropertiesMock.when(CarlosProperties::getBuildTag).thenReturn("test");

        loggedInInfo = loggedInInfoFor(CALLER_PROVIDER_NO);

        // Testable subclass: return the current test's LoggedInInfo (reassignable per test).
        service = new ProviderService() {
            @Override
            protected LoggedInInfo getLoggedInInfo() {
                return loggedInInfo;
            }
        };
        service.providerManager = mockProviderManager;
    }

    @AfterEach
    void tearDown() {
        if (carlosPropertiesMock != null) carlosPropertiesMock.close();
    }

    private static LoggedInInfo loggedInInfoFor(String providerNo) {
        LoggedInInfo info = new LoggedInInfo();
        if (providerNo != null) {
            info.setLoggedInProvider(new Provider(providerNo));
        }
        return info;
    }

    @Nested
    @DisplayName("saveProviderSettings authorization")
    @Tag("update")
    class SaveProviderSettings {

        @Test
        @DisplayName("should save settings when provider edits own settings")
        void shouldSaveSettings_whenProviderEditsOwnSettings() {
            ProviderSettings settings = mock(ProviderSettings.class);

            RestResponse<String> response = service.saveProviderSettings(settings, CALLER_PROVIDER_NO);

            assertThat(response.getStatus()).isEqualTo(ResponseStatus.SUCCESS);
            verify(mockProviderManager).updateProviderSettings(eq(loggedInInfo), eq(CALLER_PROVIDER_NO), eq(settings));
        }

        @Test
        @DisplayName("should return 403 when provider edits another provider")
        void shouldReturn403_whenProviderEditsAnotherProvider() {
            ProviderSettings settings = mock(ProviderSettings.class);

            assertThatThrownBy(() -> service.saveProviderSettings(settings, OTHER_PROVIDER_NO))
                    .isInstanceOf(WebApplicationException.class)
                    .extracting(exception -> ((WebApplicationException) exception).getResponse().getStatus())
                    .isEqualTo(Status.FORBIDDEN.getStatusCode());

            verify(mockProviderManager, never()).updateProviderSettings(any(), any(), any());
        }

        @Test
        @DisplayName("should return 403 when target provider is null")
        void shouldReturn403_whenTargetProviderIsNull() {
            ProviderSettings settings = mock(ProviderSettings.class);

            assertThatThrownBy(() -> service.saveProviderSettings(settings, (String) null))
                    .isInstanceOf(WebApplicationException.class)
                    .extracting(exception -> ((WebApplicationException) exception).getResponse().getStatus())
                    .isEqualTo(Status.FORBIDDEN.getStatusCode());

            verify(mockProviderManager, never()).updateProviderSettings(any(), any(), any());
        }

        @Test
        @DisplayName("should return 403 when session provider is null")
        void shouldReturn403_whenSessionProviderIsNull() {
            loggedInInfo = loggedInInfoFor(null);
            ProviderSettings settings = mock(ProviderSettings.class);

            assertThatThrownBy(() -> service.saveProviderSettings(settings, CALLER_PROVIDER_NO))
                    .isInstanceOf(WebApplicationException.class)
                    .extracting(exception -> ((WebApplicationException) exception).getResponse().getStatus())
                    .isEqualTo(Status.FORBIDDEN.getStatusCode());

            verify(mockProviderManager, never()).updateProviderSettings(any(), any(), any());
        }
    }
}
