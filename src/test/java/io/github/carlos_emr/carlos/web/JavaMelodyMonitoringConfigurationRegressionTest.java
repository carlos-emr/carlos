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
package io.github.carlos_emr.carlos.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("JavaMelody monitoring configuration regression tests")
@Tag("unit")
@Tag("regression")
class JavaMelodyMonitoringConfigurationRegressionTest {
    private static final Path WEB_XML = Path.of("src/main/webapp/WEB-INF/web.xml");
    private static final Path DEVCONTAINER_MAKE = Path.of(".devcontainer/development/scripts/make");
    private static final Path DEVCONTAINER_HOT_RELOAD = Path.of(
            ".devcontainer/development/setup/setup-hot-reload.sh");
    private static final Pattern SYSTEM_ACTIONS_PARAM = Pattern.compile(
            "<param-name>\\s*+system-actions-enabled\\s*+</param-name>\\s*+"
                    + "<param-value>\\s*+([^<\\s]++)\\s*+</param-value>",
            Pattern.DOTALL);
    private static final Pattern INITIAL_DEVCONTAINER_OVERRIDE = Pattern.compile(
            "(?m)^\\s*enable_devcontainer_javamelody_system_actions\\s+"
                    + "\"[^\"]*/WEB-INF/web\\.xml\"\\s*$");
    private static final Pattern HOT_RELOAD_DEVCONTAINER_OVERRIDE = Pattern.compile(
            "if\\s+\\[\\[\\s+\"\\$RELATIVE_PATH/\\$filename\"\\s+==\\s+\"WEB-INF/web\\.xml\"\\s+\\]\\];"
                    + "\\s*then\\s*enable_devcontainer_javamelody_system_actions\\s+\"\\$DEST_FILE\"",
            Pattern.DOTALL);
    private static final String SYSTEM_ACTIONS_FUNCTION = "enable_devcontainer_javamelody_system_actions";
    private static final String VALID_WEB_XML = """
            <web-app>
                <init-param>
                    <param-name>system-actions-enabled</param-name>
                    <param-value>false</param-value>
                </init-param>
            </web-app>
            """;
    private static final String MISSING_PARAM_WEB_XML = """
            <web-app>
            </web-app>
            """;
    private static final String DUPLICATE_PARAM_WEB_XML = """
            <web-app>
                <init-param>
                    <param-name>system-actions-enabled</param-name>
                    <param-value>false</param-value>
                </init-param>
                <init-param>
                    <param-name>system-actions-enabled</param-name>
                    <param-value>false</param-value>
                </init-param>
            </web-app>
            """;
    private static final String MALFORMED_PARAM_WEB_XML = """
            <web-app>
                <init-param>
                    <param-name>system-actions-enabled</param-name>
                </init-param>
            </web-app>
            """;
    private static final String COMMENTED_VALUE_WEB_XML = """
            <web-app>
                <init-param>
                    <param-name>system-actions-enabled</param-name>
                    <!-- <param-value>false</param-value> -->
                </init-param>
            </web-app>
            """;
    private static final String INVALID_VALUE_WEB_XML = """
            <web-app>
                <init-param>
                    <param-name>system-actions-enabled</param-name>
                    <param-value>unexpected</param-value>
                </init-param>
            </web-app>
            """;
    private static final String EMPTY_VALUE_WEB_XML = """
            <web-app>
                <init-param>
                    <param-name>system-actions-enabled</param-name>
                    <param-value></param-value>
                </init-param>
            </web-app>
            """;
    private static final String MULTILINE_COMMENT_WEB_XML = """
            <web-app>
                <!--
                <init-param>
                    <param-name>system-actions-enabled</param-name>
                    <param-value>false</param-value>
                </init-param>
                -->
            </web-app>
            """;

    @TempDir
    private Path tempDir;

    @Test
    @DisplayName("production web.xml should disable JavaMelody system actions")
    void shouldDisableJavaMelodySystemActions_inProductionWebXml() throws IOException {
        String webXml = Files.readString(WEB_XML, StandardCharsets.UTF_8);
        Matcher matcher = SYSTEM_ACTIONS_PARAM.matcher(webXml);

        assertThat(matcher.find())
                .as("JavaMelody system-actions-enabled init-param must be present")
                .isTrue();
        assertThat(matcher.group(1).trim())
                .as("production monitoring credentials must not allow heap dumps or other JVM system actions")
                .isEqualTo("false");
        assertThat(matcher.find())
                .as("system-actions-enabled should only be configured once")
                .isFalse();
    }

    @Test
    @DisplayName("devcontainer deployment should enable JavaMelody system actions")
    void shouldEnableJavaMelodySystemActions_inDevcontainerDeployment() throws IOException {
        String makeScript = Files.readString(DEVCONTAINER_MAKE, StandardCharsets.UTF_8);
        String hotReloadScript = Files.readString(DEVCONTAINER_HOT_RELOAD, StandardCharsets.UTF_8);

        assertThat(makeScript)
                .as("initial devcontainer deployment must enable system actions in its deployed web.xml")
                .containsPattern(INITIAL_DEVCONTAINER_OVERRIDE);
        assertThat(hotReloadScript)
                .as("hot reload must restore the devcontainer override after copying web.xml")
                .containsPattern(HOT_RELOAD_DEVCONTAINER_OVERRIDE);
    }

    @Test
    @DisplayName("devcontainer helpers should only override a valid JavaMelody configuration")
    void shouldOverrideJavaMelodySystemActions_onlyForValidDevcontainerConfiguration()
            throws IOException, InterruptedException {
        List<ShellHelper> helpers = List.of(
                new ShellHelper("make", "sh", DEVCONTAINER_MAKE),
                new ShellHelper("hot-reload", "bash", DEVCONTAINER_HOT_RELOAD));
        List<OverrideScenario> scenarios = List.of(
                new OverrideScenario("valid", VALID_WEB_XML, true),
                new OverrideScenario("missing", MISSING_PARAM_WEB_XML, false),
                new OverrideScenario("duplicate", DUPLICATE_PARAM_WEB_XML, false),
                new OverrideScenario("malformed", MALFORMED_PARAM_WEB_XML, false),
                new OverrideScenario("commented-value", COMMENTED_VALUE_WEB_XML, false),
                new OverrideScenario("invalid-value", INVALID_VALUE_WEB_XML, false),
                new OverrideScenario("empty-value", EMPTY_VALUE_WEB_XML, false),
                new OverrideScenario("multiline-comment", MULTILINE_COMMENT_WEB_XML, false));

        for (ShellHelper helper : helpers) {
            String source = Files.readString(helper.source(), StandardCharsets.UTF_8);
            String function = extractShellFunction(source);

            for (OverrideScenario scenario : scenarios) {
                assertOverrideBehavior(helper, function, scenario);
            }
        }
    }

    private void assertOverrideBehavior(
            ShellHelper helper, String function, OverrideScenario scenario)
            throws IOException, InterruptedException {
        String testName = helper.name() + "-" + scenario.name();
        Path webXml = tempDir.resolve(testName + "-web.xml");
        Path testScript = tempDir.resolve(testName + "-helper.sh");
        Path logFile = tempDir.resolve(testName + ".log");
        Files.writeString(webXml, scenario.webXml(), StandardCharsets.UTF_8);
        Files.writeString(
                testScript,
                "set -e\n" + function + "\n" + SYSTEM_ACTIONS_FUNCTION + " \"$1\"\n",
                StandardCharsets.UTF_8);

        ProcessBuilder processBuilder =
                new ProcessBuilder(helper.shell(), testScript.toString(), webXml.toString())
                        .redirectErrorStream(true);
        processBuilder.environment().put("LOG_FILE", logFile.toString());
        Process process = processBuilder.start();
        process.getOutputStream().close();
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exitCode = process.waitFor();
        String actualWebXml = Files.readString(webXml, StandardCharsets.UTF_8);

        assertThat(exitCode)
                .as(
                        "%s helper should keep the devcontainer workflow nonfatal: %s",
                        helper.name(),
                        output)
                .isZero();
        assertThat(Files.exists(Path.of(webXml.toString() + ".tmp")))
                .as("%s helper should clean up its temporary file", helper.name())
                .isFalse();
        if (scenario.shouldEnable()) {
            assertThat(actualWebXml)
                    .as("%s helper should enable a valid configuration", helper.name())
                    .contains("<param-value>true</param-value>");
        } else {
            assertThat(actualWebXml)
                    .as("%s helper should not rewrite a %s configuration", helper.name(), scenario.name())
                    .isEqualTo(scenario.webXml());
        }
    }

    private static String extractShellFunction(String script) {
        StringBuilder function = new StringBuilder();
        boolean insideFunction = false;

        for (String line : script.lines().toList()) {
            if (line.equals(SYSTEM_ACTIONS_FUNCTION + "() {")) {
                insideFunction = true;
            }
            if (insideFunction) {
                function.append(line).append('\n');
                if (line.equals("}")) {
                    return function.toString();
                }
            }
        }

        throw new IllegalStateException("Devcontainer JavaMelody override function not found");
    }

    private record ShellHelper(String name, String shell, Path source) {}

    private record OverrideScenario(String name, String webXml, boolean shouldEnable) {}
}
