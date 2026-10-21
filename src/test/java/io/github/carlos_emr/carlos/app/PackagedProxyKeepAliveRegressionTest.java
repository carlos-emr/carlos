/**
 * Copyright (c) 2026 CARLOS Contributors
 *
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 * CARLOS EMR
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.app;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the packaged front door's upstream keep-alive pool to an idle limit shorter than the
 * packaged Tomcat connector's.
 *
 * <p>nginx reuses pooled connections to Tomcat for up to its upstream {@code keepalive_timeout}
 * (60s by default); Tomcat closes an idle keep-alive connection at {@code connectionTimeout}
 * (20s in the packaged server.xml). Between the two, the pool hands out a connection Tomcat
 * is closing and the request fails with "upstream prematurely closed connection" (502), and
 * nginx does not retry a POST. On a packaged Ubuntu 26.04 install the chart print reproduced
 * this on the first POST after a 20s pause (#3623). The fix is nginx closing first; this test
 * fails the build if either side drifts back across the other.</p>
 */
@Tag("unit")
@DisplayName("Packaged nginx upstream keep-alive vs Tomcat idle timeout")
class PackagedProxyKeepAliveRegressionTest {

    private static final Path NGINX_LIMITS = resolveProjectPath(
            Path.of("debian", "assets", "nginx", "conf.d", "carlos-emr-limits.conf"));
    private static final Path TOMCAT_SERVER_XML = resolveProjectPath(
            Path.of("debian", "assets", "tomcat", "server.xml"));

    private static final Pattern UPSTREAM_BLOCK = Pattern.compile(
            "upstream\\s+carlos_backend\\s*\\{(.*?)\\}", Pattern.DOTALL);
    private static final Pattern KEEPALIVE_TIMEOUT = Pattern.compile(
            "^\\s*keepalive_timeout\\s+(\\d+)(m?s)\\s*;", Pattern.MULTILINE);
    private static final Pattern KEEPALIVE_POOL = Pattern.compile("^\\s*keepalive\\s+\\d+\\s*;", Pattern.MULTILINE);
    private static final Pattern CONNECTION_TIMEOUT = Pattern.compile("connectionTimeout=\"(\\d+)\"");
    private static final Pattern KEEP_ALIVE_TIMEOUT_ATTR = Pattern.compile("keepAliveTimeout=\"(\\d+)\"");

    @Test
    @DisplayName("nginx closes an idle pooled connection before Tomcat's connector does")
    void shouldCloseIdleUpstreamConnection_beforeTomcatConnectionTimeout() throws IOException {
        String upstream = upstreamBlock();
        Matcher timeout = KEEPALIVE_TIMEOUT.matcher(upstream);
        assertThat(timeout.find())
                .as("upstream carlos_backend must set keepalive_timeout explicitly; nginx's 60s default outlives Tomcat's idle close")
                .isTrue();
        long nginxIdleMillis = Long.parseLong(timeout.group(1)) * ("ms".equals(timeout.group(2)) ? 1 : 1000);

        assertThat(nginxIdleMillis)
                .as("nginx must give up a pooled connection strictly before Tomcat closes it (connectionTimeout, or keepAliveTimeout when set)")
                .isLessThan(tomcatIdleCloseMillis());
    }

    @Test
    @DisplayName("the upstream pool itself is still declared, so the timeout guards a real pool")
    void shouldKeepUpstreamPool_withKeepaliveDirective() throws IOException {
        assertThat(KEEPALIVE_POOL.matcher(upstreamBlock()).find())
                .as("upstream carlos_backend must keep its `keepalive N;` pool; the idle timeout only matters with one")
                .isTrue();
    }

    private static String upstreamBlock() throws IOException {
        String conf = Files.readString(NGINX_LIMITS, StandardCharsets.UTF_8);
        Matcher block = UPSTREAM_BLOCK.matcher(conf);
        assertThat(block.find()).as("upstream carlos_backend block in " + NGINX_LIMITS).isTrue();
        return block.group(1);
    }

    /** Tomcat's idle close: keepAliveTimeout when the connector sets it, else connectionTimeout. */
    private static long tomcatIdleCloseMillis() throws IOException {
        String serverXml = Files.readString(TOMCAT_SERVER_XML, StandardCharsets.UTF_8);
        Matcher keepAlive = KEEP_ALIVE_TIMEOUT_ATTR.matcher(serverXml);
        if (keepAlive.find()) {
            return Long.parseLong(keepAlive.group(1));
        }
        Matcher connection = CONNECTION_TIMEOUT.matcher(serverXml);
        assertThat(connection.find()).as("connectionTimeout on the packaged connector in " + TOMCAT_SERVER_XML).isTrue();
        return Long.parseLong(connection.group(1));
    }

    private static Path resolveProjectPath(Path relativePath) {
        Path current = Path.of(System.getProperty(
                "maven.multiModuleProjectDirectory",
                System.getProperty("user.dir"))).toAbsolutePath();
        while (current != null) {
            Path candidate = current.resolve(relativePath);
            if (Files.exists(candidate)) {
                return candidate;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate " + relativePath + " from " + System.getProperty("user.dir"));
    }
}
