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
package io.github.carlos_emr.carlos.fax.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.test.unit.OpenOUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Unit tests for {@link MiddlewareFaxProviderClient} validation logic.
 *
 * <p>Tests the validateMiddlewareConfig() private method and the requireMatchingProviderType()
 * default method inherited from FaxProviderClient. These validations guard all middleware
 * API operations and are critical for fail-fast error reporting.</p>
 *
 * <p>HTTP transport behavior is not tested here (requires WireMock or similar). These tests
 * focus on pre-flight validation logic that can be tested as pure unit tests.</p>
 *
 * @since 2026-02-19
 */
@Tag("unit")
@Tag("fax")
@Tag("middleware")
@DisplayName("MiddlewareFaxProviderClient Validation Tests")
class MiddlewareFaxProviderClientValidationTest extends OpenOUnitTestBase {

    private MiddlewareFaxProviderClient client;
    private Method validateMiddlewareConfigMethod;

    @BeforeEach
    void setUp() throws Exception {
        client = new MiddlewareFaxProviderClient();

        validateMiddlewareConfigMethod = MiddlewareFaxProviderClient.class.getDeclaredMethod(
                "validateMiddlewareConfig", FaxConfig.class);
        validateMiddlewareConfigMethod.setAccessible(true);
    }

    private void invokeValidateMiddlewareConfig(FaxConfig config) throws Throwable {
        try {
            validateMiddlewareConfigMethod.invoke(client, config);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Test
    @DisplayName("should report MIDDLEWARE provider type")
    void shouldReportMiddlewareProviderType() {
        assertThat(client.getProviderType()).isEqualTo(FaxConfig.ProviderType.MIDDLEWARE);
    }

    @Nested
    @DisplayName("validateMiddlewareConfig()")
    class ValidateMiddlewareConfigTests {

        @Test
        @DisplayName("should throw when URL is null")
        void shouldThrow_whenUrlIsNull() {
            FaxConfig config = createConfig(null, "siteUser", "faxUser");

            assertThatThrownBy(() -> invokeValidateMiddlewareConfig(config))
                    .isInstanceOf(FaxProviderException.class)
                    .hasMessageContaining("URL")
                    .hasMessageContaining("not configured");
        }

        @Test
        @DisplayName("should throw when URL is empty")
        void shouldThrow_whenUrlIsEmpty() {
            FaxConfig config = createConfig("", "siteUser", "faxUser");

            assertThatThrownBy(() -> invokeValidateMiddlewareConfig(config))
                    .isInstanceOf(FaxProviderException.class)
                    .hasMessageContaining("URL")
                    .hasMessageContaining("not configured");
        }

        @Test
        @DisplayName("should throw when URL is whitespace only")
        void shouldThrow_whenUrlIsWhitespace() {
            FaxConfig config = createConfig("   ", "siteUser", "faxUser");

            assertThatThrownBy(() -> invokeValidateMiddlewareConfig(config))
                    .isInstanceOf(FaxProviderException.class)
                    .hasMessageContaining("URL");
        }

        @Test
        @DisplayName("should throw when siteUser is null")
        void shouldThrow_whenSiteUserIsNull() {
            FaxConfig config = createConfig("https://fax.example.com", null, "faxUser");

            assertThatThrownBy(() -> invokeValidateMiddlewareConfig(config))
                    .isInstanceOf(FaxProviderException.class)
                    .hasMessageContaining("site user")
                    .hasMessageContaining("not configured");
        }

        @Test
        @DisplayName("should throw when siteUser is empty")
        void shouldThrow_whenSiteUserIsEmpty() {
            FaxConfig config = createConfig("https://fax.example.com", "", "faxUser");

            assertThatThrownBy(() -> invokeValidateMiddlewareConfig(config))
                    .isInstanceOf(FaxProviderException.class)
                    .hasMessageContaining("site user");
        }

        @Test
        @DisplayName("should throw when faxUser is null")
        void shouldThrow_whenFaxUserIsNull() {
            FaxConfig config = createConfig("https://fax.example.com", "siteUser", null);

            assertThatThrownBy(() -> invokeValidateMiddlewareConfig(config))
                    .isInstanceOf(FaxProviderException.class)
                    .hasMessageContaining("fax user")
                    .hasMessageContaining("not configured");
        }

        @Test
        @DisplayName("should throw when faxUser is empty")
        void shouldThrow_whenFaxUserIsEmpty() {
            FaxConfig config = createConfig("https://fax.example.com", "siteUser", "");

            assertThatThrownBy(() -> invokeValidateMiddlewareConfig(config))
                    .isInstanceOf(FaxProviderException.class)
                    .hasMessageContaining("fax user");
        }

        @Test
        @DisplayName("should throw when passwd is null")
        void shouldThrow_whenPasswdIsNull() {
            FaxConfig config = createConfig("https://fax.example.com", "siteUser", "faxUser", null, "faxPassword");

            assertThatThrownBy(() -> invokeValidateMiddlewareConfig(config))
                    .isInstanceOf(FaxProviderException.class)
                    .hasMessageContaining("site password")
                    .hasMessageContaining("not configured");
        }

        @Test
        @DisplayName("should throw when passwd is empty")
        void shouldThrow_whenPasswdIsEmpty() {
            FaxConfig config = createConfig("https://fax.example.com", "siteUser", "faxUser", "", "faxPassword");

            assertThatThrownBy(() -> invokeValidateMiddlewareConfig(config))
                    .isInstanceOf(FaxProviderException.class)
                    .hasMessageContaining("site password");
        }

        @Test
        @DisplayName("should throw when faxPasswd is null")
        void shouldThrow_whenFaxPasswdIsNull() {
            FaxConfig config = createConfig("https://fax.example.com", "siteUser", "faxUser", "sitePassword", null);

            assertThatThrownBy(() -> invokeValidateMiddlewareConfig(config))
                    .isInstanceOf(FaxProviderException.class)
                    .hasMessageContaining("fax password")
                    .hasMessageContaining("not configured");
        }

        @Test
        @DisplayName("should throw when faxPasswd is empty")
        void shouldThrow_whenFaxPasswdIsEmpty() {
            FaxConfig config = createConfig("https://fax.example.com", "siteUser", "faxUser", "sitePassword", "");

            assertThatThrownBy(() -> invokeValidateMiddlewareConfig(config))
                    .isInstanceOf(FaxProviderException.class)
                    .hasMessageContaining("fax password");
        }

        @Test
        @DisplayName("should not throw when all config values are valid")
        void shouldNotThrow_whenAllConfigValuesAreValid() throws Throwable {
            FaxConfig config = createConfig("https://fax.example.com", "siteUser", "faxUser");

            // Should not throw
            invokeValidateMiddlewareConfig(config);
        }
    }

    @Nested
    @DisplayName("requireMatchingProviderType()")
    class RequireMatchingProviderTypeTests {

        @Test
        @DisplayName("should throw when SRFAX config passed to MIDDLEWARE client")
        void shouldThrow_whenSrfaxConfigPassedToMiddlewareClient() {
            FaxConfig srfaxConfig = mock(FaxConfig.class);
            when(srfaxConfig.getProviderType()).thenReturn(FaxConfig.ProviderType.SRFAX);

            assertThatThrownBy(() -> client.sendFax(srfaxConfig, new FaxJob(), Paths.get("/tmp/test.pdf")))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("SRFAX")
                    .hasMessageContaining("MIDDLEWARE");
        }

        @Test
        @DisplayName("should throw on listInboundFaxes with wrong provider type")
        void shouldThrow_onListInboundFaxes_withWrongProviderType() {
            FaxConfig srfaxConfig = mock(FaxConfig.class);
            when(srfaxConfig.getProviderType()).thenReturn(FaxConfig.ProviderType.SRFAX);

            assertThatThrownBy(() -> client.listInboundFaxes(srfaxConfig))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should throw on downloadFax with wrong provider type")
        void shouldThrow_onDownloadFax_withWrongProviderType() {
            FaxConfig srfaxConfig = mock(FaxConfig.class);
            when(srfaxConfig.getProviderType()).thenReturn(FaxConfig.ProviderType.SRFAX);

            assertThatThrownBy(() -> client.downloadFax(srfaxConfig, new FaxJob()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should throw on deleteFax with wrong provider type")
        void shouldThrow_onDeleteFax_withWrongProviderType() {
            FaxConfig srfaxConfig = mock(FaxConfig.class);
            when(srfaxConfig.getProviderType()).thenReturn(FaxConfig.ProviderType.SRFAX);

            assertThatThrownBy(() -> client.deleteFax(srfaxConfig, new FaxJob()))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("should throw on fetchFaxStatus with wrong provider type")
        void shouldThrow_onFetchFaxStatus_withWrongProviderType() {
            FaxConfig srfaxConfig = mock(FaxConfig.class);
            when(srfaxConfig.getProviderType()).thenReturn(FaxConfig.ProviderType.SRFAX);

            assertThatThrownBy(() -> client.fetchFaxStatus(srfaxConfig, new FaxJob()))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    // -- helper methods --

    /**
     * Creates a FaxConfig with the specified middleware connection parameters and valid passwords.
     * Uses mock to avoid EncryptionUtils dependency in getPasswd()/getFaxPasswd().
     */
    private FaxConfig createConfig(String url, String siteUser, String faxUser) {
        return createConfig(url, siteUser, faxUser, "sitePassword", "faxPassword");
    }

    /**
     * Creates a FaxConfig with full control over all middleware connection parameters.
     */
    private FaxConfig createConfig(String url, String siteUser, String faxUser, String passwd, String faxPasswd) {
        FaxConfig config = mock(FaxConfig.class);
        when(config.getUrl()).thenReturn(url);
        when(config.getSiteUser()).thenReturn(siteUser);
        when(config.getFaxUser()).thenReturn(faxUser);
        when(config.getPasswd()).thenReturn(passwd);
        when(config.getFaxPasswd()).thenReturn(faxPasswd);
        when(config.getProviderType()).thenReturn(FaxConfig.ProviderType.MIDDLEWARE);
        return config;
    }
}
