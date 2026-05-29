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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@DisplayName("JavaMelody monitoring configuration regression tests")
@Tag("unit")
@Tag("regression")
class JavaMelodyMonitoringConfigurationRegressionTest {
    private static final Path WEB_XML = Path.of("src/main/webapp/WEB-INF/web.xml");
    private static final Path DEVCONTAINER_MAKE = Path.of(".devcontainer/development/scripts/make");
    private static final Path DEVCONTAINER_HOT_RELOAD = Path.of(
            ".devcontainer/development/setup/setup-hot-reload.sh");
    private static final Pattern SYSTEM_ACTIONS_PARAM = Pattern.compile(
            "<param-name>\\s*system-actions-enabled\\s*</param-name>\\s*"
                    + "<param-value>\\s*([^<]+?)\\s*</param-value>",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL);

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
                .contains("enable_devcontainer_javamelody_system_actions")
                .contains("system-actions-enabled")
                .contains("<param-value>true</param-value>")
                .contains("/usr/local/tomcat/webapps/$snapshot_dest_dir/WEB-INF/web.xml");
        assertThat(hotReloadScript)
                .contains("enable_devcontainer_javamelody_system_actions")
                .contains("system-actions-enabled")
                .contains("<param-value>true</param-value>")
                .contains("WEB-INF/web.xml");
    }
}
