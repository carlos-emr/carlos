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
package io.github.carlos_emr.carlos.utility;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.LoggerContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MiscUtils#addLoggingOverrideConfiguration(String)}.
 *
 * <p>Verifies context-name substitution, no-op behaviour when the system
 * property is unset, and early return with warning when the resolved path
 * does not point to a readable file.</p>
 *
 * @since 2026-04-14
 * @see MiscUtils
 */
@Tag("unit")
@Tag("fast")
@DisplayName("MiscUtils.addLoggingOverrideConfiguration")
class MiscUtilsLoggingOverrideUnitTest {

    private static final String PROPERTY_KEY = "log4j.override.configuration";

    /** Snapshot of the LoggerContext config URI before each test, for restoration. */
    private URI originalConfigLocation;

    @TempDir
    Path tempDir;

    @BeforeEach
    void saveOriginalConfig() {
        originalConfigLocation = getLoggerContextConfigLocation();
        System.clearProperty(PROPERTY_KEY);
    }

    @AfterEach
    void restoreOriginalConfig() {
        System.clearProperty(PROPERTY_KEY);
        if (originalConfigLocation != null) {
            LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
            ctx.setConfigLocation(originalConfigLocation);
        }
    }

    @Test
    @DisplayName("should not change config when system property is unset")
    void shouldNotChangeConfig_whenPropertyIsUnset() {
        URI before = getLoggerContextConfigLocation();

        MiscUtils.addLoggingOverrideConfiguration("/myapp");

        assertThat(getLoggerContextConfigLocation()).isEqualTo(before);
    }

    @Test
    @DisplayName("should apply override config when file exists and is readable")
    void shouldApplyOverrideConfig_whenFileExistsAndIsReadable() throws IOException {
        Path configFile = tempDir.resolve("override-log4j2.xml");
        Files.writeString(configFile, minimalLog4j2Config());

        System.setProperty(PROPERTY_KEY, configFile.toAbsolutePath().toString());

        MiscUtils.addLoggingOverrideConfiguration("");

        assertThat(getLoggerContextConfigLocation())
                .isEqualTo(configFile.toFile().toURI());
    }

    @Test
    @DisplayName("should substitute ${contextName} in config path")
    void shouldSubstituteContextName_inConfigPath() throws IOException {
        Path appDir = tempDir.resolve("myapp");
        Files.createDirectories(appDir);
        Path configFile = appDir.resolve("log4j2.xml");
        Files.writeString(configFile, minimalLog4j2Config());

        System.setProperty(PROPERTY_KEY, tempDir.toAbsolutePath() + File.separator + "${contextName}" + File.separator + "log4j2.xml");

        MiscUtils.addLoggingOverrideConfiguration("/myapp");

        assertThat(getLoggerContextConfigLocation())
                .isEqualTo(configFile.toFile().toURI());
    }

    @Test
    @DisplayName("should not apply config when resolved path does not exist")
    void shouldNotApplyConfig_whenResolvedPathDoesNotExist() {
        System.setProperty(PROPERTY_KEY, "/nonexistent/path/log4j2.xml");
        URI before = getLoggerContextConfigLocation();

        MiscUtils.addLoggingOverrideConfiguration("");

        assertThat(getLoggerContextConfigLocation()).isEqualTo(before);
    }

    @Test
    @DisplayName("should not apply config when resolved path is a directory")
    void shouldNotApplyConfig_whenResolvedPathIsDirectory() {
        System.setProperty(PROPERTY_KEY, tempDir.toAbsolutePath().toString());
        URI before = getLoggerContextConfigLocation();

        MiscUtils.addLoggingOverrideConfiguration("");

        assertThat(getLoggerContextConfigLocation()).isEqualTo(before);
    }

    @Test
    @DisplayName("should strip leading slash from context path before substitution")
    void shouldStripLeadingSlash_fromContextPath() throws IOException {
        Path appDir = tempDir.resolve("webapp");
        Files.createDirectories(appDir);
        Path configFile = appDir.resolve("log4j2.xml");
        Files.writeString(configFile, minimalLog4j2Config());

        System.setProperty(PROPERTY_KEY, tempDir.toAbsolutePath() + File.separator + "${contextName}" + File.separator + "log4j2.xml");

        MiscUtils.addLoggingOverrideConfiguration("/webapp");

        assertThat(getLoggerContextConfigLocation())
                .isEqualTo(configFile.toFile().toURI());
    }

    private static URI getLoggerContextConfigLocation() {
        LoggerContext ctx = (LoggerContext) LogManager.getContext(false);
        return ctx.getConfigLocation();
    }

    private static String minimalLog4j2Config() {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<Configuration status=\"OFF\">\n"
                + "  <Appenders>\n"
                + "    <Console name=\"Console\" target=\"SYSTEM_OUT\">\n"
                + "      <PatternLayout pattern=\"%m%n\"/>\n"
                + "    </Console>\n"
                + "  </Appenders>\n"
                + "  <Loggers>\n"
                + "    <Root level=\"error\"><AppenderRef ref=\"Console\"/></Root>\n"
                + "  </Loggers>\n"
                + "</Configuration>\n";
    }
}
