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
package io.github.carlos_emr.carlos.deb;

import io.github.carlos_emr.carlos.utility.MariaDbCompatibilityDriver;
import io.github.carlos_emr.carlos.utility.MariaDbJdbcCompatibility;
import io.github.carlos_emr.carlos.utility.MariaDbPreferredTlsPlugin;
import java.util.Properties;
import java.util.ServiceLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.mariadb.jdbc.Configuration;
import org.mariadb.jdbc.plugin.TlsSocketPlugin;

/** Verify legacy carlos-ctl URLs with the exact driver and translator shipped in the WAR. */
public final class FlywayRunnerJdbcTest {
    private FlywayRunnerJdbcTest() {
    }

    /**
     * Runs the packaged launcher's URL, TLS and input-error contracts without a database connection.
     *
     * @param args unused command-line arguments
     * @throws Exception if driver parsing, the child launcher or any assertion fails
     */
    public static void main(String[] args) throws Exception {
        require(ServiceLoader.load(TlsSocketPlugin.class).stream()
                .anyMatch(provider -> provider.type() == MariaDbPreferredTlsPlugin.class),
                "legacy preferred TLS provider must be discoverable in the packaged launcher");
        String legacy = FlywayRunner.jdbcUrl("jdbc:mysql://127.0.0.1:3306/carlos"
                + "?useSSL=false&allowPublicKeyRetrieval=true");
        require(new MariaDbCompatibilityDriver().acceptsURL(legacy),
                "installed carlos-ctl URL must be accepted by MariaDB");
        Configuration configuration = parsed(legacy);
        require("carlos".equals(configuration.database()), "database selection must survive translation");
        require("DISABLE".equals(configuration.sslMode().name()), "explicit disabled TLS must remain disabled");
        require(configuration.allowPublicKeyRetrieval(), "authentication option must survive translation");

        String secured = FlywayRunner.jdbcUrl("jdbc:mysql://db.example:3306/carlos?sslMode=VERIFY_IDENTITY");
        require("VERIFY_FULL".equals(parsed(secured).sslMode().name()),
                "legacy certificate and hostname verification must remain enabled");
        String nativeUrl = FlywayRunner.jdbcUrl("jdbc:mariadb://db.example:3306/carlos?sslMode=verify-full");
        require("VERIFY_FULL".equals(parsed(nativeUrl).sslMode().name()),
                "native MariaDB TLS policy must remain enabled");
        String encoded = FlywayRunner.jdbcUrl("jdbc:mysql://db.example/carlos"
                + "?user=encoded%2Buser&password=p%26ss%3Dword&serverTimezone=America%2FToronto");
        Configuration decoded = parsed(encoded);
        require("encoded+user".equals(decoded.user()) && "p&ss=word".equals(decoded.password()),
                "encoded URL credentials must be decoded exactly once");
        require("America/Toronto".equals(decoded.connectionTimeZone()),
                "encoded Connector/J timezone must survive migration");
        shouldRefuseInvalidUrl_withoutExposingCredentials();
        System.out.println("PASS Flyway MariaDB driver, legacy URL compatibility and TLS policy");
    }

    private static void shouldRefuseInvalidUrl_withoutExposingCredentials() throws Exception {
        for (String url : new String[] {
                "jdbc:mysql://localhost/carlos?password=synthetic-secret%XX",
                "jdbc:mysql://localhost/carlos?password=synthetic-secret&characterEncoding=latin1",
                "jdbc:postgresql://localhost/carlos?password=synthetic-secret",
                "jdbc:mysql:not-a-url?password=synthetic-secret",
                "jdbc:mysql://localhost/carlos?password=synthetic-secret&zeroDateTimeBehavior=invalid",
                "jdbc:mysql://localhost:synthetic-secret/carlos"}) {
            ProcessBuilder launcher = new ProcessBuilder(
                    Path.of(System.getProperty("java.home"), "bin", "java").toString(),
                    "-cp", System.getProperty("java.class.path"), FlywayRunner.class.getName(),
                    "validate", "classpath:db/migration/common");
            launcher.environment().put("FLYWAY_URL", url);
            launcher.environment().put("FLYWAY_USER", "synthetic-user");
            launcher.environment().put("FLYWAY_PASSWORD", "synthetic-password");
            Process child = launcher.start();
            try {
                require(child.waitFor(20, TimeUnit.SECONDS), "invalid URL must fail without connecting");
                String error = new String(child.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
                require(child.exitValue() == 2, "invalid URL must use the input-error exit code");
                require(error.equals("invalid FLYWAY_URL: unsupported or malformed JDBC configuration"
                                + System.lineSeparator()),
                        "invalid URL must produce one sanitized error line without a stack trace");
                require(child.getInputStream().readAllBytes().length == 0,
                        "invalid URL must not report a successful migration");
            } finally {
                child.destroyForcibly();
            }
        }
    }

    private static Configuration parsed(String url) throws Exception {
        var settings = MariaDbJdbcCompatibility.settings(url, new Properties());
        return Configuration.parse(settings.url(), settings.properties());
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
