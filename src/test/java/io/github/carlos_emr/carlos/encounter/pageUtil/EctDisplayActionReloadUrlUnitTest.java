/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 * This software is published under the GPL GNU General Public License.
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA 02111-1307, USA.
 * <p>
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */

package io.github.carlos_emr.carlos.encounter.pageUtil;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the left-navbar reload URL built by {@link EctDisplayAction}.
 *
 * <p>The rendered value is posted straight back by {@code reloadNav()} after a popup such
 * as Add Tickler saves, so its shape is what a packaged deployment's WAF inspects. An
 * absolute {@code scheme://host/...} value scored OWASP CRS 931100 ("URL Parameter using
 * IP Address") on an install reached by IP and turned every navbar refresh into a 403; the
 * echoed reload plumbing nested the previous URL inside the next one on every refresh.</p>
 *
 * @since 2026-09-06
 */
@DisplayName("EctDisplayAction - navbar reload URL")
@Tag("unit")
@Tag("fast")
@Tag("encounter")
class EctDisplayActionReloadUrlUnitTest {

    private static final String URI = "/carlos/encounter/displayTickler";

    /** Builds a single-valued parameter map from alternating name/value arguments. */
    private static Map<String, String[]> params(String... keyValues) {
        if (keyValues.length % 2 != 0) {
            throw new IllegalArgumentException(
                    "params() takes alternating name/value arguments, got " + keyValues.length);
        }
        Map<String, String[]> map = new LinkedHashMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            map.put(keyValues[i], new String[]{keyValues[i + 1]});
        }
        return map;
    }

    @Test
    @DisplayName("Reload URL stays root-relative so CRS 931100 cannot match it")
    void shouldReturnRootRelativeUrl_withoutSchemeOrHost() {
        String reloadUrl = EctDisplayAction.buildReloadUrl(URI, params("hC", "FF6600"));

        assertThat(reloadUrl)
                .isEqualTo("/carlos/encounter/displayTickler?hC=FF6600")
                .doesNotContain("://");
    }

    @Test
    @DisplayName("The caller's own reload plumbing is not echoed back into the URL")
    void shouldDropReloadPlumbingParameters_whenRefreshingAModule() {
        String reloadUrl = EctDisplayAction.buildReloadUrl(URI, params(
                "hC", "FF6600",
                "reloadURL", "/carlos/encounter/displayTickler?hC=FF6600",
                "numToDisplay", "6",
                "cmd", "tickler"));

        assertThat(reloadUrl).isEqualTo("/carlos/encounter/displayTickler?hC=FF6600");
    }

    @Test
    @DisplayName("Refreshing repeatedly yields the same URL instead of nesting it")
    void shouldProduceStableUrl_forRepeatedRefreshes() {
        String first = EctDisplayAction.buildReloadUrl(URI, params("hC", "FF6600"));

        // popColumn() posts reloadURL/numToDisplay/cmd alongside the parameters already in
        // the URL, which is what the next render sees.
        String second = EctDisplayAction.buildReloadUrl(URI, params(
                "hC", "FF6600",
                "reloadURL", first,
                "numToDisplay", "6",
                "cmd", "tickler"));

        assertThat(second).isEqualTo(first);
    }

    @Test
    @DisplayName("The CSRF token is never reflected into the reload URL")
    void shouldOmitCsrfToken_whenPostedAsARequestParameter() {
        String reloadUrl = EctDisplayAction.buildReloadUrl(URI, params(
                "hC", "FF6600",
                "CSRF-TOKEN", "GK3C-GYLL-NDFP-3AM6"));

        assertThat(reloadUrl).doesNotContain("CSRF-TOKEN").doesNotContain("GK3C");
    }

    @Test
    @DisplayName("Chart-scoping parameters survive so a refreshed box renders like the first load")
    void shouldPreserveRemainingParameters_withUrlEncoding() {
        String reloadUrl = EctDisplayAction.buildReloadUrl(URI, params(
                "demographicNo", "42",
                "reason", "Lab Results"));

        assertThat(reloadUrl)
                .isEqualTo("/carlos/encounter/displayTickler?demographicNo=42&reason=Lab+Results");
    }

    @Test
    @DisplayName("A request with no parameters yields the bare action path")
    void shouldReturnBarePath_whenNoParametersRemain() {
        assertThat(EctDisplayAction.buildReloadUrl(URI, params("cmd", "tickler"))).isEqualTo(URI);
        assertThat(EctDisplayAction.buildReloadUrl(URI, Map.of())).isEqualTo(URI);
        assertThat(EctDisplayAction.buildReloadUrl(URI, null)).isEqualTo(URI);
    }

    @Test
    @DisplayName("Multi-valued parameters keep every value")
    void shouldRepeatKey_forMultiValuedParameters() {
        Map<String, String[]> map = new LinkedHashMap<>();
        map.put("hC", new String[]{"FF6600", "009999"});

        assertThat(EctDisplayAction.buildReloadUrl(URI, map))
                .isEqualTo("/carlos/encounter/displayTickler?hC=FF6600&hC=009999");
    }
}
