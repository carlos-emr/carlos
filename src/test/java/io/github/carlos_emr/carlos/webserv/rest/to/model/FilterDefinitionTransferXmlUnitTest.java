/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.webserv.rest.to.model;

import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.xml.XmlBaselineAssertions;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("xml")
class FilterDefinitionTransferXmlUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @ValueSource(strings = {"default", "populated"})
    void shouldRetainLegacyMapShape_whenReadingAndWritingCapturedXml(String sample) throws Exception {
        var value = XmlBaselineAssertions.assertBaselineRoundTrip(FilterDefinitionTransfer.class, sample);
        if ("populated".equals(sample)) {
            assertThat(value.getFilterClassName()).isEqualTo("7");
            assertThat(value.isOn()).isTrue();
            assertThat(value.getParams()).containsExactlyEntriesOf(java.util.Map.of("7", "7"));
        } else {
            assertThat(value.getParams()).isEmpty();
            assertThat(value.isOn()).isFalse();
        }
    }
}
