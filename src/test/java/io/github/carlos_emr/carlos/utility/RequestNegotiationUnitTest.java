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
    @DisplayName("should detect AJAX when CSRFGuard appended its marker to the header")
    void shouldDetectAjax_whenCsrfGuardAppendedItsMarker() {
        // jQuery sets X-Requested-With before send(); CSRFGuard's XHR hijack then calls
        // setRequestHeader again, and the XHR spec combines repeated values with ", ". Every
        // browser XHR in CARLOS therefore arrives in this shape, and an exact-match check used to
        // classify it as a browser page request — which let the response-decorating filters append
        // their script block to AJAX replies.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Requested-With", "XMLHttpRequest, OWASP CSRFGuard Project");

        assertThat(RequestNegotiation.isAjax(request)).isTrue();
    }

    @Test
    @DisplayName("should detect AJAX when only the CSRFGuard marker is present")
    void shouldDetectAjax_whenOnlyCsrfGuardMarkerIsPresent() {
        // carlos-ajax.js deliberately leaves the header to CSRFGuard to avoid a duplicated
        // CSRF-TOKEN value, so its requests carry the marker on its own.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Requested-With", "OWASP CSRFGuard Project");

        assertThat(RequestNegotiation.isAjax(request)).isTrue();
    }

    @Test
    @DisplayName("should detect AJAX when the marker arrives as a repeated header line")
    void shouldDetectAjax_whenMarkerArrivesAsRepeatedHeaderLine() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Requested-With", "SomeOtherClient");
        request.addHeader("X-Requested-With", "XMLHttpRequest");

        assertThat(RequestNegotiation.isAjax(request)).isTrue();
    }

    @Test
    @DisplayName("should fall back to the shipped marker when CSRFGuard is not initialised")
    void shouldFallBackToShippedMarker_whenCsrfGuardIsNotInitialised() {
        // The marker is read from the running CSRFGuard so an installation overriding
        // JavascriptServlet.xRequestedWith in Owasp.CsrfGuard.overlay.properties is honoured.
        // Nothing initialises CSRFGuard in a unit test, so this exercises the fallback: the
        // predicate must still work rather than throwing out of a filter.
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Requested-With", "OWASP CSRFGuard Project");

        assertThat(RequestNegotiation.isAjax(request)).isTrue();
    }

    @Test
    @DisplayName("should not detect AJAX when no recognised marker is present")
    void shouldNotDetectAjax_whenNoRecognisedMarkerIsPresent() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Requested-With", "ShockwaveFlash/32.0");

        assertThat(RequestNegotiation.isAjax(request)).isFalse();
    }

    @Test
    @DisplayName("should not detect AJAX when the header is absent")
    void shouldNotDetectAjax_whenHeaderIsAbsent() {
        assertThat(RequestNegotiation.isAjax(new MockHttpServletRequest())).isFalse();
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
