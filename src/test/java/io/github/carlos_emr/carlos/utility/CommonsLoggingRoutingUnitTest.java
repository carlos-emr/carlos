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

import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.commons.logging.impl.Log4jApiLogFactory;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.config.Configurator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import io.github.carlos_emr.carlos.test.logging.LogCapture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Regression guard for issue #5413: Commons Logging (JCL) has exactly one implementation on the
 * classpath, {@code commons-logging:commons-logging}, and its discovery binds straight to the
 * Log4j 2 backend.
 *
 * <p>{@code org.slf4j:jcl-over-slf4j} used to arrive through CXF alongside commons-logging. Both
 * jars define the classes below, and the bridge also registers a {@code LogFactory} service that
 * reroutes JCL through SLF4J, so which {@code LogFactory} won depended on class-loader order. The
 * Maven build bans the bridge (maven-enforcer-plugin, {@code ban-duplicate-commons-logging}); this
 * test checks the resolved classes themselves, so a library that shades its own copy is caught too.
 * {@code debian/check-war-commons-logging.py} applies the same rule to the packaged WAR.</p>
 */
@DisplayName("Commons Logging routing (#5413)")
@Tag("unit")
class CommonsLoggingRoutingUnitTest {

    private static final String LOG_FACTORY_SERVICE = "META-INF/services/org.apache.commons.logging.LogFactory";
    private static final String LOG_FACTORY_PROPERTIES = "commons-logging.properties";

    private static ClassLoader classLoader() {
        return CommonsLoggingRoutingUnitTest.class.getClassLoader();
    }

    private static List<URL> resources(String name) throws IOException {
        return Collections.list(classLoader().getResources(name));
    }

    /** File name of the jar a {@code jar:} resource URL points into, or the whole URL otherwise. */
    private static String containerName(URL url) throws IOException, URISyntaxException {
        if (!"jar".equals(url.getProtocol())) {
            return url.toString();
        }
        URL jar = ((JarURLConnection) url.openConnection()).getJarFileURL();
        return Path.of(jar.toURI()).getFileName().toString();
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {
            "org.apache.commons.logging.Log",
            "org.apache.commons.logging.LogFactory",
            "org.apache.commons.logging.LogConfigurationException",
            "org.apache.commons.logging.impl.NoOpLog",
            "org.apache.commons.logging.impl.SimpleLog"
    })
    @DisplayName("should define each Commons Logging class once, in the commons-logging jar")
    void shouldDefineClassOnce_inCommonsLoggingJar(String className) throws Exception {
        List<URL> definitions = resources(className.replace('.', '/') + ".class");

        assertThat(definitions)
                .as("definitions of %s on the classpath", className)
                .hasSize(1);
        assertThat(containerName(definitions.get(0)))
                .as("jar defining %s", className)
                .startsWith("commons-logging-")
                .endsWith(".jar")
                .doesNotContain("-api-");
    }

    @Test
    @DisplayName("should not ship the SLF4J bridge's LogFactory implementation")
    void shouldNotLoadBridgeFactory_onClasspath() {
        assertThatThrownBy(() -> Class.forName("org.apache.commons.logging.impl.SLF4JLogFactory", false, classLoader()))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    @DisplayName("should carry no LogFactory service file or commons-logging.properties override")
    void shouldHaveNoDiscoveryOverride_onClasspath() throws Exception {
        assertThat(resources(LOG_FACTORY_SERVICE))
                .as("%s resources (jcl-over-slf4j and log4j-jcl register one)", LOG_FACTORY_SERVICE)
                .isEmpty();
        assertThat(resources(LOG_FACTORY_PROPERTIES))
                .as("%s resources", LOG_FACTORY_PROPERTIES)
                .isEmpty();
    }

    @Test
    @DisplayName("should bind Commons Logging discovery to the Log4j API factory")
    void shouldBindLog4jApiLogFactory_forDefaultDiscovery() {
        assertThat(System.getProperty(LogFactory.FACTORY_PROPERTY))
                .as("system property %s overrides discovery", LogFactory.FACTORY_PROPERTY)
                .isNull();
        assertThat(LogFactory.getFactory()).isInstanceOf(Log4jApiLogFactory.class);
    }

    @Test
    @DisplayName("should deliver each Commons Logging call to Log4j 2 once, at the configured level")
    void shouldDeliverEachCallToLog4jOnce_withConfiguredLevel() {
        String loggerName = "carlos.test.jcl.routing." + UUID.randomUUID();
        RuntimeException cause = new IllegalStateException("synthetic failure");

        try (LogCapture capture = LogCapture.forLogger(loggerName)) {
            // The level comes from the Log4j 2 configuration, not from Commons Logging.
            Configurator.setLevel(loggerName, Level.INFO);
            Log log = LogFactory.getLog(loggerName);

            assertThat(log.isDebugEnabled()).isFalse();
            assertThat(log.isInfoEnabled()).isTrue();

            log.debug("below the configured level");
            log.warn("routed through commons-logging", cause);

            List<LogEvent> events = capture.events();
            assertThat(events).hasSize(1);
            LogEvent event = events.get(0);
            assertThat(event.getLoggerName()).isEqualTo(loggerName);
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getMessage().getFormattedMessage()).isEqualTo("routed through commons-logging");
            assertThat(event.getThrown()).isSameAs(cause);
        }
    }
}
