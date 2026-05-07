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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.CarlosProperties;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for RingCentral API endpoint resolution.
 *
 * @since 2026-05-06
 */
@Tag("unit")
@Tag("fax")
@Tag("ringcentral")
@DisplayName("RingCentralApiConnector Unit Tests")
class RingCentralApiConnectorTest extends CarlosUnitTestBase {

    private final CarlosProperties properties = CarlosProperties.getInstance();

    @AfterEach
    void tearDown() {
        properties.setProperty("ringcentral.api.url", RingCentralApiConnector.DEFAULT_RINGCENTRAL_API_URL);
        properties.setProperty("ringcentral.api.sandbox.url", RingCentralApiConnector.DEFAULT_RINGCENTRAL_SANDBOX_API_URL);
        properties.setProperty("ringcentral.use.sandbox", "false");
    }

    @Test
    @DisplayName("should return production origin when production endpoint is configured")
    void shouldReturnProductionOrigin_whenProductionEndpointConfigured() {
        properties.setProperty("ringcentral.api.url", "https://platform.ringcentral.com");
        properties.setProperty("ringcentral.use.sandbox", "false");

        assertThat(RingCentralApiConnector.resolveRingCentralApiUrl())
                .isEqualTo(RingCentralApiConnector.DEFAULT_RINGCENTRAL_API_URL);
    }

    @Test
    @DisplayName("should return sandbox origin when sandbox mode is enabled")
    void shouldReturnSandboxOrigin_whenSandboxModeEnabled() {
        properties.setProperty("ringcentral.use.sandbox", "true");

        assertThat(RingCentralApiConnector.resolveRingCentralApiUrl())
                .isEqualTo(RingCentralApiConnector.DEFAULT_RINGCENTRAL_SANDBOX_API_URL);
    }

    @Test
    @DisplayName("should reject configured endpoint when path is present")
    void shouldRejectConfiguredEndpoint_whenPathIsPresent() {
        properties.setProperty("ringcentral.api.url", "https://platform.ringcentral.com/foo");

        assertThat(RingCentralApiConnector.resolveRingCentralApiUrl())
                .isEqualTo(RingCentralApiConnector.DEFAULT_RINGCENTRAL_API_URL);
    }

    @Test
    @DisplayName("should reject configured endpoint when custom port is present")
    void shouldRejectConfiguredEndpoint_whenCustomPortIsPresent() {
        properties.setProperty("ringcentral.api.url", "https://platform.ringcentral.com:8443");

        assertThat(RingCentralApiConnector.resolveRingCentralApiUrl())
                .isEqualTo(RingCentralApiConnector.DEFAULT_RINGCENTRAL_API_URL);
    }

    @Test
    @DisplayName("should fall back to ~ when account or extension id is null or blank")
    void shouldFallBackToTilde_whenAccountOrExtensionIdIsNullOrBlank() {
        assertThat(RingCentralApiConnector.normalizeAccountOrExtensionId(null)).isEqualTo("~");
        assertThat(RingCentralApiConnector.normalizeAccountOrExtensionId("   ")).isEqualTo("~");
    }

    @Test
    @DisplayName("should fall back to ~ when account or extension id sanitizes to empty")
    void shouldFallBackToTilde_whenAccountOrExtensionIdSanitizesToEmpty() {
        assertThat(RingCentralApiConnector.normalizeAccountOrExtensionId("!@#$%")).isEqualTo("~");
    }

    @Test
    @DisplayName("should preserve allowed characters and ~ sentinel in account id")
    void shouldPreserveAllowedCharacters_inAccountOrExtensionId() {
        assertThat(RingCentralApiConnector.normalizeAccountOrExtensionId("123-abc_XYZ~")).isEqualTo("123-abc_XYZ~");
        assertThat(RingCentralApiConnector.normalizeAccountOrExtensionId("a1!b2@c3")).isEqualTo("a1b2c3");
        assertThat(RingCentralApiConnector.normalizeAccountOrExtensionId("~")).isEqualTo("~");
    }

    @Test
    @DisplayName("should reject blank message id with RingCentralException")
    void shouldRejectBlankMessageId_withRingCentralException() {
        assertThatThrownBy(() -> RingCentralApiConnector.normalizeMessageId(null))
                .isInstanceOf(RingCentralException.class)
                .hasMessageContaining("required");
        assertThatThrownBy(() -> RingCentralApiConnector.normalizeMessageId("   "))
                .isInstanceOf(RingCentralException.class)
                .hasMessageContaining("required");
    }

    @Test
    @DisplayName("should reject message id that sanitizes to empty with RingCentralException")
    void shouldRejectMessageIdThatSanitizesToEmpty_withRingCentralException() {
        assertThatThrownBy(() -> RingCentralApiConnector.normalizeMessageId("!@#$%"))
                .isInstanceOf(RingCentralException.class)
                .hasMessageContaining("no valid characters");
    }

    @Test
    @DisplayName("should preserve allowed characters in message id and strip the ~ sentinel")
    void shouldPreserveAllowedCharacters_inMessageId() throws Exception {
        assertThat(RingCentralApiConnector.normalizeMessageId("123-abc_XYZ")).isEqualTo("123-abc_XYZ");
        assertThat(RingCentralApiConnector.normalizeMessageId("a1!b2@c3")).isEqualTo("a1b2c3");
        // ~ is intentionally not allowed for message ids - it has no documented meaning here.
        assertThat(RingCentralApiConnector.normalizeMessageId("123~456")).isEqualTo("123456");
    }
}
