/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
@Tag("security")
@DisplayName("FullPathReWrite")
class FullPathReWriteUnitTest {

    @Test
    @DisplayName("should not include request host data when building rewrite URL")
    void shouldNotIncludeRequestHostData_whenBuildingRewriteUrl() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/carlos/documentManager/documentReport.jsp");
        request.setScheme("https");
        request.setServerName("attacker.example");
        request.setServerPort(4443);

        String url = FullPathReWrite.buildRelativeUrl(request, "combinePDFs");

        assertThat(url).isEqualTo("/carlos/documentManager/combinePDFs");
        assertThat(url)
                .doesNotContain("attacker.example")
                .doesNotContain("https://")
                .doesNotContain(":4443");
    }

    @Test
    @DisplayName("should preserve existing path shape for absolute jspPage values")
    void shouldPreserveExistingPathShape_forAbsoluteJspPageValues() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/carlos/billing/CA/BC/adjustBill.jsp");

        String url = FullPathReWrite.buildRelativeUrl(request,
                "/billing/CA/BC/ViewBillingCodeNewSearch");

        assertThat(url).isEqualTo("/carlos/billing/CA/BC//billing/CA/BC/ViewBillingCodeNewSearch");
    }
}
