/*
 * Copyright (c) 2026. CARLOS EMR contributors and others.
 *
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
 */
package io.github.carlos_emr.carlos.fax.provider;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.test.unit.OpenOUnitTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 * Unit tests for {@link SRFaxProviderClient#validateCredentials(FaxConfig)} private method.
 *
 * <p>Uses reflection to test the private validateCredentials method directly, since it is
 * the gatekeeper for all SRFax API operations. FaxConfig is mocked to bypass the
 * EncryptionUtils dependency in getFaxPasswd().</p>
 *
 * @since 2026-02-13
 */
@Tag("unit")
@Tag("fax")
@Tag("srfax")
@DisplayName("SRFaxProviderClient Credential Validation Tests")
class SRFaxProviderClientValidationTest extends OpenOUnitTestBase {

    private SRFaxProviderClient client;
    private Method validateCredentialsMethod;

    @BeforeEach
    void setUp() throws Exception {
        client = new SRFaxProviderClient();

        validateCredentialsMethod = SRFaxProviderClient.class.getDeclaredMethod(
                "validateCredentials", FaxConfig.class);
        validateCredentialsMethod.setAccessible(true);
    }

    /**
     * Invokes the private validateCredentials method, unwrapping InvocationTargetException.
     *
     * @param faxConfig the config to validate
     * @throws Throwable the actual exception thrown by validateCredentials
     */
    private void invokeValidateCredentials(FaxConfig faxConfig) throws Throwable {
        try {
            validateCredentialsMethod.invoke(client, faxConfig);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

    @Test
    @DisplayName("should throw FaxProviderException when faxUser is null")
    void shouldThrowFaxProviderException_whenFaxUserIsNull() {
        // Given
        FaxConfig config = mock(FaxConfig.class);
        when(config.getFaxUser()).thenReturn(null);
        when(config.getFaxPasswd()).thenReturn("valid-password");

        // When/Then
        assertThatThrownBy(() -> invokeValidateCredentials(config))
                .isInstanceOf(FaxProviderException.class)
                .hasMessageContaining("SRFax username")
                .hasMessageContaining("not configured");
    }

    @Test
    @DisplayName("should throw FaxProviderException when faxUser is empty")
    void shouldThrowFaxProviderException_whenFaxUserIsEmpty() {
        // Given
        FaxConfig config = mock(FaxConfig.class);
        when(config.getFaxUser()).thenReturn("");
        when(config.getFaxPasswd()).thenReturn("valid-password");

        // When/Then
        assertThatThrownBy(() -> invokeValidateCredentials(config))
                .isInstanceOf(FaxProviderException.class)
                .hasMessageContaining("SRFax username")
                .hasMessageContaining("not configured");
    }

    @Test
    @DisplayName("should throw FaxProviderException when faxPassword is null")
    void shouldThrowFaxProviderException_whenFaxPasswordIsNull() {
        // Given
        FaxConfig config = mock(FaxConfig.class);
        when(config.getFaxUser()).thenReturn("valid-user");
        when(config.getFaxPasswd()).thenReturn(null);

        // When/Then
        assertThatThrownBy(() -> invokeValidateCredentials(config))
                .isInstanceOf(FaxProviderException.class)
                .hasMessageContaining("SRFax password")
                .hasMessageContaining("not configured");
    }

    @Test
    @DisplayName("should throw FaxProviderException when faxPassword is empty")
    void shouldThrowFaxProviderException_whenFaxPasswordIsEmpty() {
        // Given
        FaxConfig config = mock(FaxConfig.class);
        when(config.getFaxUser()).thenReturn("valid-user");
        when(config.getFaxPasswd()).thenReturn("");

        // When/Then
        assertThatThrownBy(() -> invokeValidateCredentials(config))
                .isInstanceOf(FaxProviderException.class)
                .hasMessageContaining("SRFax password")
                .hasMessageContaining("not configured");
    }

    @Test
    @DisplayName("should not throw when credentials are valid")
    void shouldNotThrow_whenCredentialsAreValid() {
        // Given
        FaxConfig config = mock(FaxConfig.class);
        when(config.getFaxUser()).thenReturn("valid-user");
        when(config.getFaxPasswd()).thenReturn("valid-password");

        // When/Then
        assertThatCode(() -> invokeValidateCredentials(config))
                .doesNotThrowAnyException();
    }
}
