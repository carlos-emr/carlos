/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.util;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.jsp.JspException;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.PageContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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

        assertThat(url)
                .isEqualTo("/carlos/documentManager/combinePDFs")
                .doesNotContain("attacker.example")
                .doesNotContain("https://")
                .doesNotContain(":4443");
    }

    @Test
    @DisplayName("should resolve leading-slash targets against the context path")
    void shouldResolveAgainstContextPath_forLeadingSlashJspPage() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/carlos/WEB-INF/jsp/billing/CA/BC/adjustBill.jsp");
        request.setContextPath("/carlos");

        String url = FullPathReWrite.buildRelativeUrl(request,
                "/billing/CA/BC/ViewBillingCodeNewSearch");

        assertThat(url).isEqualTo("/carlos/billing/CA/BC/ViewBillingCodeNewSearch");
    }

    @Test
    @DisplayName("should resolve leading-slash targets from the root context")
    void shouldResolveAgainstRootContext_forLeadingSlashJspPage() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/WEB-INF/jsp/prevention/index.jsp");
        request.setContextPath("");

        assertThat(FullPathReWrite.buildRelativeUrl(request, "/prevention/printPrevention"))
                .isEqualTo("/prevention/printPrevention");
    }

    @Test
    @DisplayName("should ignore the forward URI for leading-slash targets")
    void shouldStayContextRelative_whenForwardUriIsSetForLeadingSlashJspPage() {
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/carlos/WEB-INF/jsp/prevention/index.jsp");
        request.setContextPath("/carlos");
        request.setAttribute(RequestDispatcher.FORWARD_REQUEST_URI,
                "/carlos/prevention/ViewPreventionIndex");

        assertThat(FullPathReWrite.buildRelativeUrl(request, "/billing/CA/BC/ViewBillingCodeNewSearch"))
                .isEqualTo("/carlos/billing/CA/BC/ViewBillingCodeNewSearch");
    }

    @ParameterizedTest
    @ValueSource(strings = {"//attacker.example/path", "///attacker.example/path", "/\\attacker.example/path",
            "\\/attacker.example/path", "\\\\attacker.example/path", "/\t//attacker.example/path",
            "\t//attacker.example/path", "/\n/attacker.example/path", "/\r\n//attacker.example/path"})
    @DisplayName("should never emit a protocol-relative URL under the root context")
    void shouldCollapseLeadingSeparators_forProtocolRelativeJspPage(String jspPage) {
        // A top-level request URI, so no page directory is prepended to mask the result.
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/index.jsp");
        request.setContextPath("");

        String url = FullPathReWrite.buildRelativeUrl(request, jspPage);

        assertThat(url).isEqualTo("/attacker.example/path");
    }

    @Test
    @DisplayName("should resolve relative targets against the browser URL after a gate forward")
    void shouldUseForwardRequestUri_whenPageWasReachedThroughGateForward() {
        // Issue #4132: after the gate forward getRequestURI() is the internal JSP path.
        MockHttpServletRequest request = new MockHttpServletRequest("POST",
                "/carlos/WEB-INF/jsp/prevention/index.jsp");
        request.setContextPath("/carlos");
        request.setAttribute(RequestDispatcher.FORWARD_REQUEST_URI,
                "/carlos/prevention/ViewPreventionIndex");

        assertThat(FullPathReWrite.buildRelativeUrl(request, "printPrevention"))
                .isEqualTo("/carlos/prevention/printPrevention")
                .doesNotContain("WEB-INF");
    }

    @Test
    @DisplayName("should map a WEB-INF/jsp base to its route directory when no forward URI is set")
    void shouldMapWebInfJspDirectory_whenForwardUriIsMissing() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/carlos/WEB-INF/jsp/documentManager/documentReport.jsp");
        request.setContextPath("/carlos");

        assertThat(FullPathReWrite.buildRelativeUrl(request, "combinePDFs"))
                .isEqualTo("/carlos/documentManager/combinePDFs");
    }

    @Test
    @DisplayName("should map a top-level WEB-INF/jsp page to the context root")
    void shouldMapToContextRoot_forTopLevelWebInfJspPage() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/carlos/WEB-INF/jsp/index.jsp");
        request.setContextPath("/carlos");

        assertThat(FullPathReWrite.buildRelativeUrl(request, "login"))
                .isEqualTo("/carlos/login");
    }

    @Test
    @DisplayName("should never emit a WEB-INF URL for a non-jsp WEB-INF base")
    void shouldFallBackToContextRoot_forOtherWebInfDirectory() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/carlos/WEB-INF/jspf/fragment.jspf");
        request.setContextPath("/carlos");

        assertThat(FullPathReWrite.buildRelativeUrl(request, "target"))
                .isEqualTo("/carlos/target");
    }

    @Test
    @DisplayName("should ignore an empty forward URI attribute")
    void shouldUseRequestUri_whenForwardUriIsEmpty() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/carlos/documentManager/documentReport");
        request.setContextPath("/carlos");
        request.setAttribute(RequestDispatcher.FORWARD_REQUEST_URI, "");

        assertThat(FullPathReWrite.buildRelativeUrl(request, "combinePDFs"))
                .isEqualTo("/carlos/documentManager/combinePDFs");
    }

    @Test
    @DisplayName("should not treat a WEB-INF-like route prefix as WEB-INF")
    void shouldKeepPath_whenDirectoryOnlyStartsWithWebInfText() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/carlos/WEB-INF-docs/page");
        request.setContextPath("/carlos");

        assertThat(FullPathReWrite.buildRelativeUrl(request, "next"))
                .isEqualTo("/carlos/WEB-INF-docs/next");
    }

    @Test
    @DisplayName("should return jspPage when request is unavailable")
    void shouldReturnJspPage_whenRequestIsUnavailable() {
        assertThat(FullPathReWrite.buildRelativeUrl(null, "combinePDFs"))
                .isEqualTo("combinePDFs");
    }

    @Test
    @DisplayName("should return jspPage when request URI is unavailable")
    void shouldReturnJspPage_whenRequestUriIsUnavailable() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn(null);

        assertThat(FullPathReWrite.buildRelativeUrl(request, "combinePDFs"))
                .isEqualTo("combinePDFs");
    }

    @Test
    @DisplayName("should use root path when request URI has no slash")
    void shouldUseRootPath_whenRequestUriHasNoSlash() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getRequestURI()).thenReturn("documentReport.jsp");

        assertThat(FullPathReWrite.buildRelativeUrl(request, "combinePDFs"))
                .isEqualTo("/combinePDFs");
    }

    @Test
    @DisplayName("should treat null jspPage as empty")
    void shouldTreatJspPage_whenValueIsNull() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/carlos/documentManager/documentReport.jsp");

        assertThat(FullPathReWrite.buildRelativeUrl(request, null))
                .isEqualTo("/carlos/documentManager/");
    }

    @Test
    @DisplayName("should use legacy HTML encoding by default when rendering tag")
    void shouldUseLegacyHtmlEncodingByDefault_whenRenderingTag() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/carlos/documentManager/documentReport.jsp");
        PageContext pageContext = mock(PageContext.class);
        JspWriter writer = mock(JspWriter.class);
        when(pageContext.getRequest()).thenReturn(request);
        when(pageContext.getOut()).thenReturn(writer);
        FullPathReWrite tag = new FullPathReWrite();
        tag.setPageContext(pageContext);
        tag.setJspPage("combinePDFs?name=\"O'Brien\"&x=<script>");

        int result = tag.doStartTag();

        assertThat(result).isEqualTo(jakarta.servlet.jsp.tagext.Tag.EVAL_BODY_INCLUDE);
        verify(writer).write("/carlos/documentManager/combinePDFs?name=&#34;O&#39;Brien&#34;&amp;x=&lt;script&gt;");
        verify(writer).flush();
    }

    @Test
    @DisplayName("should encode URL for HTML attributes when requested")
    void shouldEncodeUrlForHtmlAttributes_whenRenderingTag() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/carlos/prevention/index.jsp");
        PageContext pageContext = mock(PageContext.class);
        JspWriter writer = mock(JspWriter.class);
        when(pageContext.getRequest()).thenReturn(request);
        when(pageContext.getOut()).thenReturn(writer);
        FullPathReWrite tag = new FullPathReWrite();
        tag.setPageContext(pageContext);
        tag.setJspPage("printPrevention?name=\"O'Brien\"&x=<script>");
        tag.setContext("htmlAttribute");

        int result = tag.doStartTag();

        assertThat(result).isEqualTo(jakarta.servlet.jsp.tagext.Tag.EVAL_BODY_INCLUDE);
        verify(writer).write("/carlos/prevention/printPrevention?name=&#34;O&#39;Brien&#34;&amp;x=&lt;script>");
        verify(writer).flush();
    }

    @Test
    @DisplayName("should encode URL for JavaScript attributes when requested")
    void shouldEncodeUrlForJavaScriptAttributes_whenRenderingTag() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/carlos/documentManager/documentReport.jsp");
        PageContext pageContext = mock(PageContext.class);
        JspWriter writer = mock(JspWriter.class);
        when(pageContext.getRequest()).thenReturn(request);
        when(pageContext.getOut()).thenReturn(writer);
        FullPathReWrite tag = new FullPathReWrite();
        tag.setPageContext(pageContext);
        tag.setJspPage("combinePDFs', window.__pwned = 1, '");
        tag.setContext("javaScriptAttribute");

        int result = tag.doStartTag();

        assertThat(result).isEqualTo(jakarta.servlet.jsp.tagext.Tag.EVAL_BODY_INCLUDE);
        verify(writer).write("/carlos/documentManager/combinePDFs\\x27, window.__pwned = 1, \\x27");
        verify(writer).flush();
    }

    @Test
    @DisplayName("should encode URL for JavaScript blocks when requested")
    void shouldEncodeUrlForJavaScriptBlocks_whenRenderingTag() throws Exception {
        assertThat(FullPathReWrite.encodeForContext(
                "/carlos/x?name=\"O'Brien\"&x=</script>", "javaScriptBlock"))
                .isEqualTo("\\/carlos\\/x?name=\\\"O\\'Brien\\\"\\x26x=<\\/script>");
    }

    @Test
    @DisplayName("should use default HTML encoding when context is blank")
    void shouldUseDefaultHtmlEncoding_whenContextIsBlank() throws Exception {
        assertThat(FullPathReWrite.encodeForContext("<script>", null))
                .isEqualTo("&lt;script&gt;");
        assertThat(FullPathReWrite.encodeForContext("<script>", " "))
                .isEqualTo("&lt;script&gt;");
    }

    @Test
    @DisplayName("should reset tag attributes when tag ends")
    void shouldResetTagAttributes_whenTagEnds() throws Exception {
        FullPathReWrite tag = new FullPathReWrite();
        tag.setServer("legacy.example");
        tag.setJspPage("combinePDFs");
        tag.setContext("javaScriptAttribute");

        int result = tag.doEndTag();

        assertThat(result).isEqualTo(jakarta.servlet.jsp.tagext.Tag.EVAL_PAGE);
        assertThat(tag.getServer()).isNull();
        assertThat(tag.getJspPage()).isEmpty();
        assertThat(tag.getContext()).isEqualTo("html");
    }

    @Test
    @DisplayName("should reject unsupported encoding context")
    void shouldRejectUnsupportedEncodingContext_whenRenderingTag() {
        assertThatThrownBy(() -> FullPathReWrite.encodeForContext("/carlos/x", "cssUrl"))
                .isInstanceOf(JspException.class)
                .hasMessageContaining("Unsupported FullPathReWrite encoding context");
    }
}
