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

import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.commn.model.FaxJob;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for FaxProviderClientFactory.
 * Tests provider client resolution and factory configuration.
 *
 * @since 2026-02-11
 */
@Tag("unit")
@Tag("fax")
@DisplayName("FaxProviderClientFactory Unit Tests")
class FaxProviderClientFactoryTest extends CarlosUnitTestBase {

    @Test
    @DisplayName("should return middleware client when config defaults to MIDDLEWARE")
    void shouldReturnMiddlewareClient_whenConfigDefaultsToMiddleware() throws FaxProviderException {
        // Given
        FaxProviderClient middlewareClient = new TestClient(FaxConfig.ProviderType.MIDDLEWARE);
        FaxProviderClientFactory factory = new FaxProviderClientFactory(Collections.singletonList(middlewareClient));
        FaxConfig faxConfig = new FaxConfig();

        // When
        FaxProviderClient result = factory.getClient(faxConfig);

        // Then
        assertThat(faxConfig.getProviderType()).isEqualTo(FaxConfig.ProviderType.MIDDLEWARE);
        assertThat(result).isSameAs(middlewareClient);
    }

    @Test
    @DisplayName("should return correct client when provider type is configured")
    void shouldReturnCorrectClient_whenProviderTypeIsConfigured() throws FaxProviderException {
        // Given
        FaxProviderClient middlewareClient = new TestClient(FaxConfig.ProviderType.MIDDLEWARE);
        FaxProviderClient srfaxClient = new TestClient(FaxConfig.ProviderType.SRFAX);
        FaxProviderClientFactory factory = new FaxProviderClientFactory(Arrays.asList(middlewareClient, srfaxClient));

        FaxConfig faxConfig = new FaxConfig();
        faxConfig.setProviderType(FaxConfig.ProviderType.SRFAX);

        // When
        FaxProviderClient result = factory.getClient(faxConfig);

        // Then
        assertThat(result).isSameAs(srfaxClient);
    }

    @Test
    @DisplayName("should throw FaxProviderException when config is null")
    void shouldThrowFaxProviderException_whenConfigIsNull() {
        // Given
        FaxProviderClient middlewareClient = new TestClient(FaxConfig.ProviderType.MIDDLEWARE);
        FaxProviderClientFactory factory = new FaxProviderClientFactory(Collections.singletonList(middlewareClient));

        // When/Then
        assertThatThrownBy(() -> factory.getClient(null))
            .isInstanceOf(FaxProviderException.class)
            .hasMessage("Fax configuration is required to resolve provider client");
    }

    @Test
    @DisplayName("should throw FaxProviderException when provider type is not registered")
    void shouldThrowFaxProviderException_whenProviderTypeNotRegistered() {
        // Given - factory only has MIDDLEWARE, not SRFAX
        FaxProviderClient middlewareClient = new TestClient(FaxConfig.ProviderType.MIDDLEWARE);
        FaxProviderClientFactory factory = new FaxProviderClientFactory(Collections.singletonList(middlewareClient));
        FaxConfig faxConfig = new FaxConfig();
        faxConfig.setProviderType(FaxConfig.ProviderType.SRFAX);

        // When/Then
        assertThatThrownBy(() -> factory.getClient(faxConfig))
            .isInstanceOf(FaxProviderException.class)
            .hasMessageContaining("No fax provider client configured for provider type: SRFAX");
    }

    @Test
    @DisplayName("should throw IllegalStateException when no provider clients are provided")
    void shouldThrowIllegalStateException_whenNoProviderClients() {
        // When/Then
        assertThatThrownBy(() -> new FaxProviderClientFactory(Collections.emptyList()))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("No FaxProviderClient implementations found");
    }

    @Test
    @DisplayName("should throw IllegalStateException when duplicate provider types are registered")
    void shouldThrowIllegalStateException_whenDuplicateProviderTypesRegistered() {
        // Given
        FaxProviderClient first = new TestClient(FaxConfig.ProviderType.MIDDLEWARE);
        FaxProviderClient second = new TestClient(FaxConfig.ProviderType.MIDDLEWARE);

        // When/Then
        assertThatThrownBy(() -> new FaxProviderClientFactory(Arrays.asList(first, second)))
            .isInstanceOf(IllegalStateException.class)
            .hasMessage("Duplicate FaxProviderClient beans configured for provider type: MIDDLEWARE");
    }

    /**
     * Test implementation of FaxProviderClient for factory testing.
     */
    private static class TestClient implements FaxProviderClient {
        private final FaxConfig.ProviderType type;

        private TestClient(FaxConfig.ProviderType type) {
            this.type = type;
        }

        @Override
        public FaxConfig.ProviderType getProviderType() {
            return type;
        }

        @Override
        public FaxJob sendFax(FaxConfig faxConfig, FaxJob faxJob, Path filePath) {
            return faxJob;
        }

        @Override
        public List<FaxJob> listInboundFaxes(FaxConfig faxConfig) {
            return Collections.emptyList();
        }

        @Override
        public FaxJob downloadFax(FaxConfig faxConfig, FaxJob fax) {
            return fax;
        }

        @Override
        public void deleteFax(FaxConfig faxConfig, FaxJob fax) {
            // Intentionally left blank: deletion side effects are not needed in this test stub.
        }

        @Override
        public FaxJob fetchFaxStatus(FaxConfig faxConfig, FaxJob faxJob) {
            return faxJob;
        }
    }
}
