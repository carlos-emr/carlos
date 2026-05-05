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
package io.github.carlos_emr.carlos.fax.ringcentral;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for RingCentral OAuth credential validation.
 *
 * @since 2026-05-05
 */
@Tag("unit")
@Tag("fax")
@Tag("ringcentral")
@DisplayName("RingCentralAuthService Unit Tests")
class RingCentralAuthServiceTest extends CarlosUnitTestBase {

    private final RingCentralAuthService authService = new RingCentralAuthService();

    @Test
    @DisplayName("should throw RingCentralException when client ID is missing")
    void shouldThrowRingCentralException_whenClientIdIsMissing() {
        FaxConfig config = mock(FaxConfig.class);
        when(config.getRingCentralClientId()).thenReturn("");
        when(config.getRingCentralClientSecret()).thenReturn("secret");
        when(config.getRingCentralJwtToken()).thenReturn("jwt");

        assertThatThrownBy(() -> authService.validateCredentials(config))
                .isInstanceOf(RingCentralException.class)
                .hasMessageContaining("client ID");
    }

    @Test
    @DisplayName("should throw RingCentralException when client secret is missing")
    void shouldThrowRingCentralException_whenClientSecretIsMissing() {
        FaxConfig config = mock(FaxConfig.class);
        when(config.getRingCentralClientId()).thenReturn("client");
        when(config.getRingCentralClientSecret()).thenReturn("");
        when(config.getRingCentralJwtToken()).thenReturn("jwt");

        assertThatThrownBy(() -> authService.validateCredentials(config))
                .isInstanceOf(RingCentralException.class)
                .hasMessageContaining("client secret");
    }

    @Test
    @DisplayName("should throw RingCentralException when JWT token is missing")
    void shouldThrowRingCentralException_whenJwtTokenIsMissing() {
        FaxConfig config = mock(FaxConfig.class);
        when(config.getRingCentralClientId()).thenReturn("client");
        when(config.getRingCentralClientSecret()).thenReturn("secret");
        when(config.getRingCentralJwtToken()).thenReturn("");

        assertThatThrownBy(() -> authService.validateCredentials(config))
                .isInstanceOf(RingCentralException.class)
                .hasMessageContaining("JWT token");
    }

    @Test
    @DisplayName("should not throw when RingCentral credentials are present")
    void shouldNotThrow_whenCredentialsArePresent() {
        FaxConfig config = mock(FaxConfig.class);
        when(config.getRingCentralClientId()).thenReturn("client");
        when(config.getRingCentralClientSecret()).thenReturn("secret");
        when(config.getRingCentralJwtToken()).thenReturn("jwt");

        assertThatCode(() -> authService.validateCredentials(config)).doesNotThrowAnyException();
    }
}
