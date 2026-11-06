/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr;

import io.github.carlos_emr.carlos.test.logging.LogCapture;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.Isolated;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.File;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

    @ParameterizedTest
    @ValueSource(strings = {"false", "no", "off", "garbage", " False "})
    void shouldWarnAtStartup_whenStoredSettingIsIgnored(String value) {
        properties.setProperty(KEY, value);

        try (LogCapture logs = LogCapture.forLogger(CarlosProperties.class)) {
            assertThat(properties.warnIfLegacyChartSettingIgnored()).isEqualTo(value);
            assertThat(logs.messages()).singleElement().asString()
                    .contains("AbandonOldChart=", value.trim(), "#4793", "ignored");
        }
        assertModernChart();
    }

    @ParameterizedTest
    @ValueSource(strings = {"true", "yes", "on", "TRUE", " yes "})
    void shouldNotWarnAtStartup_whenStoredSettingAgreesWithPolicy(String value) {
        properties.setProperty(KEY, value);

        try (LogCapture logs = LogCapture.forLogger(CarlosProperties.class)) {
            assertThat(properties.warnIfLegacyChartSettingIgnored()).isNull();
            assertThat(logs.messages()).isEmpty();
        }
    }

    @Test
    void shouldNotWarnAtStartup_whenPropertyIsAbsent() {
        properties.remove(KEY);

        try (LogCapture logs = LogCapture.forLogger(CarlosProperties.class)) {
            assertThat(properties.warnIfLegacyChartSettingIgnored()).isNull();
            assertThat(logs.messages()).isEmpty();
        }
    }

    /**
     * The singleton is built while the class initializes. Loading a stored override must remain
     * safe before the later startup cutover guard handles warnings and persistence. A previous
     * constructor warning accessed an uninitialized static field and left the class unloadable.
     */
    @Test
    void shouldInitializeSingleton_whenStoredSettingIsPresentAtStartup() throws Exception {
        Path config = Files.createTempDirectory("carlos-properties-4793");
        try {
            Files.writeString(config.resolve("carlos.properties"), KEY + "=false\n");
            List<URL> classpath = new ArrayList<>();
            classpath.add(config.toUri().toURL());
            String entries = System.getProperty("surefire.test.class.path", System.getProperty("java.class.path"));
            for (String entry : entries.split(File.pathSeparator)) {
                if (!entry.isBlank()) classpath.add(Path.of(entry).toUri().toURL());
            }
            try (URLClassLoader isolated = new URLClassLoader(classpath.toArray(URL[]::new),
                    ClassLoader.getPlatformClassLoader())) {
                Class<?> type = Class.forName(CarlosProperties.class.getName(), true, isolated);
                assertThat(type.getClassLoader()).isSameAs(isolated);
                Object instance = type.getMethod("getInstance").invoke(null);
                assertThat(type.getMethod("get", Object.class).invoke(instance, KEY)).isEqualTo("false");
                assertThat(type.getMethod("getProperty", String.class).invoke(instance, KEY)).isEqualTo("true");
            }
        } finally {
            Files.deleteIfExists(config.resolve("carlos.properties"));
            Files.deleteIfExists(config);
        }
    }

    private void assertModernChart() {
        assertThat(properties.getProperty(KEY)).isEqualTo("true");
        // Existing legacy branches use this inherited overload with a false fallback.
        assertThat(Boolean.parseBoolean(properties.getProperty(KEY, "false"))).isTrue();
        assertThat(properties.isPropertyActive(KEY)).isTrue();
        assertThat(properties.getBooleanProperty(KEY, "yes")).isTrue();
    }
}
