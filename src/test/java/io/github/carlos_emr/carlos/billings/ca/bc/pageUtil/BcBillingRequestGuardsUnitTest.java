/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.billings.ca.bc.pageUtil;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.CarlosProperties;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@Tag("unit")
@Tag("billing")
class BcBillingRequestGuardsUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @ValueSource(strings = {"BC", "bc", " Bc "})
    void shouldAllowBcProvince_withTrimmedCaseInsensitiveConfiguration(String region) throws Exception {
        var properties = mock(CarlosProperties.class);
        when(properties.getProperty("billregion", "")).thenReturn(region);
        try (var mocked = mockStatic(CarlosProperties.class)) {
            mocked.when(CarlosProperties::getInstance).thenReturn(properties);
            var response = new MockHttpServletResponse();
            assertThat(BcBillingRequestGuards.requireBcProvince(response)).isTrue();
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(response.isCommitted()).isFalse();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ON", "other", " BC-ON "})
    void shouldReturnNotFound_whenProvinceDoesNotProvideBcSchema(String region) throws Exception {
        var properties = mock(CarlosProperties.class);
        when(properties.getProperty("billregion", "")).thenReturn(region);
        try (var mocked = mockStatic(CarlosProperties.class)) {
            mocked.when(CarlosProperties::getInstance).thenReturn(properties);
            var response = new MockHttpServletResponse();
            assertThat(BcBillingRequestGuards.requireBcProvince(response)).isFalse();
            assertThat(response.getStatus()).isEqualTo(404);
            assertThat(response.isCommitted()).isTrue();
        }
    }
}
