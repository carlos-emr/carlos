/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.utility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("TomcatRuntimeVersionListener")
@Tag("unit")
@Tag("fast")
class TomcatRuntimeVersionListenerUnitTest {

    @ParameterizedTest(name = "{0} supported={1}")
    @CsvSource({
            "11.0.20, false",
            "11.0.23, false",
            "11.0.24, true",
            "11.0.25, true",
            "11.0.24-dev, true",
            "10.1.99, false",
            "12.0.0, false",
            "unknown, false"
    })
    void shouldEnforceTomcatElevenPatchBaseline(String version, boolean supported) {
        assertThat(TomcatRuntimeVersionListener.isSupported(version)).isEqualTo(supported);
    }
}
