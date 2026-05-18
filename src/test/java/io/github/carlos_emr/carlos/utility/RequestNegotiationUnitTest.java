/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.utility;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("unit")
class RequestNegotiationUnitTest {

    @Test
    @DisplayName("should detect AJAX when conventional header is present")
    void shouldDetectAjax_whenConventionalHeaderIsPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Requested-With", "xmlhttprequest");

        assertThat(RequestNegotiation.isAjax(request)).isTrue();
    }

    @Test
    @DisplayName("should detect HTML when content type has charset")
    void shouldDetectHtml_whenContentTypeHasCharset() {
        assertThat(RequestNegotiation.isHtmlContentType("Text/Html;charset=UTF-8")).isTrue();
    }

    @Test
    @DisplayName("should detect JSON when literal media type appears")
    void shouldDetectJson_whenLiteralMediaTypeAppears() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept", "application/xml, application/json;q=0.8");

        assertThat(RequestNegotiation.acceptsJson(request)).isTrue();
    }

    @Test
    @DisplayName("should detect JSON when structured suffix is present")
    void shouldDetectJson_whenStructuredSuffixIsPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Accept", "application/problem+json");

        assertThat(RequestNegotiation.acceptsJson(request)).isTrue();
    }
}
