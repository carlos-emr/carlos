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
package io.github.carlos_emr.carlos.app;

import io.github.carlos_emr.carlos.app.RateLimitFilter.RateConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.Arrays;
import java.util.Map;
import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Lints the <strong>shipped</strong> rate-limit configuration in
 * {@code src/main/resources/carlos.properties} so the defaults stay internally consistent.
 *
 * <p>{@link RateLimitFilter} increments and checks the global per-IP counter on every request,
 * stacking it with any matched path tier. A path tier set <em>higher</em> than the global cap can
 * therefore never bind — the global cap trips first — which silently makes the tier inert and the
 * documented limit misleading (see issue #3058). These tests pin the invariant against the actual
 * shipped numbers, reusing the production parser so they cannot drift from filter behavior. They
 * are intentionally value-agnostic: tuning the numbers later will not break them as long as the
 * config stays self-consistent.</p>
 */
@Tag("unit")
@Tag("security")
@DisplayName("Shipped rate-limit config invariants")
class RateLimitConfigInvariantUnitTest {

    private static final String CONFIG_RESOURCE = "carlos.properties";

    private Properties loadShippedConfig() throws Exception {
        // Load from the test classpath (the processed copy of src/main/resources/carlos.properties)
        // rather than a relative filesystem path, so the test is not dependent on the launcher's
        // working directory (parent-module builds, IDE/CI runners).
        Properties props = new Properties();
        try (InputStream in = RateLimitConfigInvariantUnitTest.class
                .getClassLoader()
                .getResourceAsStream(CONFIG_RESOURCE)) {
            assertThat(in)
                    .as("shipped config %s must exist on the test classpath", CONFIG_RESOURCE)
                    .isNotNull();
            props.load(in);
        }
        return props;
    }

    @Test
    @DisplayName("no WAF_RATE_LIMIT_PATHS tier exceeds the global per-IP cap")
    void shouldKeepEveryPathTierAtOrBelowGlobalCap_forShippedConfig() throws Exception {
        Properties props = loadShippedConfig();

        String globalRaw = props.getProperty("WAF_RATE_LIMIT_DEFAULT_REQUESTS");
        assertThat(globalRaw).as("WAF_RATE_LIMIT_DEFAULT_REQUESTS must be set").isNotBlank();
        int globalCap = Integer.parseInt(globalRaw.trim());

        // Reuse the production parser so the test cannot drift from filter parsing behavior.
        Map<String, RateConfig> tiers =
                new RateLimitFilter().parsePathRates(props.getProperty("WAF_RATE_LIMIT_PATHS"));
        assertThat(tiers).as("shipped config should define path tiers").isNotEmpty();

        tiers.forEach((path, cfg) ->
                assertThat(cfg.requests)
                        .as("path tier '%s'=%d/%ds must not exceed the global cap of %d/window "
                                        + "(a tier above the global cap is inert — see issue #3058)",
                                path, cfg.requests, cfg.windowSeconds, globalCap)
                        .isLessThanOrEqualTo(globalCap));
    }

    @Test
    @DisplayName("every shipped WAF_RATE_LIMIT_PATHS entry parses with no tier silently dropped")
    void shouldParseEveryShippedPathEntry_withoutDroppingTiers() throws Exception {
        Properties props = loadShippedConfig();

        String raw = props.getProperty("WAF_RATE_LIMIT_PATHS");
        assertThat(raw).as("WAF_RATE_LIMIT_PATHS must be set").isNotBlank();

        long declaredEntries = Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(entry -> !entry.isEmpty())
                .count();

        Map<String, RateConfig> tiers = new RateLimitFilter().parsePathRates(raw);

        assertThat((long) tiers.size())
                .as("every comma-separated tier in WAF_RATE_LIMIT_PATHS should parse; a dropped "
                        + "entry means a malformed or duplicate-path tier in the shipped config")
                .isEqualTo(declaredEntries);
    }
}
