/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The retired chart mode cannot be re-enabled by a deployment override (#4793). */
@Tag("unit")
@Isolated
class CarlosPropertiesModernChartUnitTest {
    private static final String KEY = "AbandonOldChart";
    private final CarlosProperties properties = CarlosProperties.getInstance();
    private Object original;

    @BeforeEach
    void rememberRawProperty() {
        original = properties.get(KEY);
    }

    @AfterEach
    void restoreRawProperty() {
        if (original == null) properties.remove(KEY);
        else properties.put(KEY, original);
    }

    @Test
    void shouldUseModernChart_whenPropertyIsAbsent() {
        properties.remove(KEY);

        assertModernChart();
    }

    @ParameterizedTest
    @ValueSource(strings = {"false", "no", "off", "", "   ", "garbage", "true", "yes", "on"})
    void shouldUseModernChart_whenDeploymentOverridesProperty(String value) {
        properties.setProperty(KEY, value);

        assertModernChart();
        assertThat(properties).as("the stored operator setting is not rewritten").containsEntry(KEY, value);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void shouldRejectBlankKey_whenPropertyIsRequested(String key) {
        assertThatThrownBy(() -> properties.getProperty(key))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Property key cannot be blank.");
    }

    private void assertModernChart() {
        assertThat(properties.getProperty(KEY)).isEqualTo("true");
        // All nine remaining readers use this inherited overload with a false fallback.
        assertThat(Boolean.parseBoolean(properties.getProperty(KEY, "false"))).isTrue();
        assertThat(properties.isPropertyActive(KEY)).isTrue();
        assertThat(properties.getBooleanProperty(KEY, "yes")).isTrue();
    }
}
