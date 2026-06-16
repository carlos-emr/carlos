/*
 * Copyright (c) 2026 CARLOS EMR Project. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 *
 * CARLOS EMR Project
 * https://github.com/carlos-emr/carlos
 */
package io.github.carlos_emr.carlos.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.jsp.JspWriter;
import jakarta.servlet.jsp.PageContext;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
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
    @DisplayName("should preserve existing path shape for absolute jspPage values")
    void shouldPreserveExistingPathShape_forAbsoluteJspPageValues() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET",
                "/carlos/billing/CA/BC/adjustBill.jsp");

        String url = FullPathReWrite.buildRelativeUrl(request,
                "/billing/CA/BC/ViewBillingCodeNewSearch");

        assertThat(url).isEqualTo("/carlos/billing/CA/BC//billing/CA/BC/ViewBillingCodeNewSearch");
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
    @DisplayName("should encode relative URL when rendering tag")
    void shouldEncodeUrl_whenRenderingTag() throws Exception {
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
}
