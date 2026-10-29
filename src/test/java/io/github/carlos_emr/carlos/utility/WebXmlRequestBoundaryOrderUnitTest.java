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

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import io.github.carlos_emr.carlos.utility.WebXmlFilterMappings.Mapping;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins where web.xml maps the request-boundary filters (issue #5305). Struts executes an action
 * without calling the rest of the chain, so DbConnectionFilter and UserActivityFilter only cover
 * actions when they are mapped before {@code struts2-prepare}/{@code struts2-execute}.
 *
 * <p>RequestBoundaryFilterOrderIntegrationTest runs this same order in a real Tomcat.</p>
 */
@Tag("unit")
@Tag("filter")
@DisplayName("web.xml request-boundary filter order")
class WebXmlRequestBoundaryOrderUnitTest {

    /**
     * The only mappings allowed outside the JDBC cleanup boundary: response sanitizing, the
     * header-only COOP filter, monitoring and the WAF layer. None of them reaches application code.
     */
    private static final Set<String> OUTSIDE_CLEANUP_BOUNDARY = Set.of(
            "ResponseSanitizationFilter",
            "CrossOriginOpenerPolicyFilter",
            "monitoring",
            "XforwardHeader",
            "RateLimitFilter");

    private final List<Mapping> mappings = WebXmlFilterMappings.production();

    @Test
    @DisplayName("should map DbConnectionFilter once, for REQUEST only, on every path")
    void shouldMapDbConnectionFilterOnce_forRequestDispatchOnly() {
        List<Mapping> db = WebXmlFilterMappings.mappingsFor(mappings, "DbConnectionFilter");

        assertThat(db).hasSize(1);
        assertThat(db.get(0).urlPatterns()).containsExactly("/*");
        assertThat(db.get(0).effectiveDispatchers()).containsExactly("REQUEST");
    }

    @Test
    @DisplayName("should map DbConnectionFilter as the first application filter")
    void shouldMapDbConnectionFilter_beforeEveryApplicationFilter() {
        int db = WebXmlFilterMappings.indexOf(mappings, "DbConnectionFilter");

        assertThat(mappings.subList(0, db))
                .as("only the sanitizer, COOP, monitoring and WAF filters may run outside the cleanup")
                .allSatisfy(m -> assertThat(OUTSIDE_CLEANUP_BOUNDARY).contains(m.filterName()));
        assertThat(db)
                .isLessThan(WebXmlFilterMappings.indexOf(mappings, "LoginFilter"))
                .isLessThan(WebXmlFilterMappings.indexOf(mappings, "RootEntryRedirectFilter"))
                .isLessThan(WebXmlFilterMappings.indexOf(mappings, "struts2-prepare"))
                .isLessThan(WebXmlFilterMappings.indexOf(mappings, "struts2-execute"));
    }

    @Test
    @DisplayName("should map UserActivityFilter once, after LoginFilter and before Struts")
    void shouldMapUserActivityFilter_betweenLoginFilterAndStruts() {
        List<Mapping> activity = WebXmlFilterMappings.mappingsFor(mappings, "UserActivityFilter");
        int index = WebXmlFilterMappings.indexOf(mappings, "UserActivityFilter");

        assertThat(activity).hasSize(1);
        assertThat(activity.get(0).urlPatterns()).containsExactly("/*");
        assertThat(activity.get(0).effectiveDispatchers()).containsExactly("REQUEST");
        assertThat(index)
                .isGreaterThan(WebXmlFilterMappings.indexOf(mappings, "LoginFilter"))
                .isLessThan(WebXmlFilterMappings.indexOf(mappings, "struts2-prepare"))
                .isLessThan(WebXmlFilterMappings.indexOf(mappings, "struts2-execute"));
    }

    @Test
    @DisplayName("should keep Struts prepare ahead of execute")
    void shouldKeepStrutsPrepare_beforeExecute() {
        assertThat(WebXmlFilterMappings.indexOf(mappings, "struts2-prepare"))
                .isGreaterThan(-1)
                .isLessThan(WebXmlFilterMappings.indexOf(mappings, "struts2-execute"));
    }
}
