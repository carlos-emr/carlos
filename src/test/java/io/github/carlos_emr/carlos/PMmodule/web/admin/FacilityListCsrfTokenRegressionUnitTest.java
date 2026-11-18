/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.PMmodule.web.admin;

import io.github.carlos_emr.carlos.app.CsrfGuardScriptInjectionFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterConfig;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jsoup.Jsoup;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.owasp.csrfguard.CsrfGuard;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/** Real injection filter over production template text; native rendering is checked separately by Playwright. */
@Tag("unit")
@Tag("security")
class FacilityListCsrfTokenRegressionUnitTest {
    @Test
    void shouldGiveTheNativeFacilityPostFormsTheirCsrfGuardClient() throws Exception {
        String source = Files.readString(Path.of(
            "src/main/webapp/WEB-INF/jsp/PMmodule/Admin/Facility/ListFacilities.jsp"), StandardCharsets.UTF_8);
        // JSP comments/directives/scriptlets emit no template text. This is a
        // source approximation, with no include expansion or JSP server claim.
        String body = source.replaceAll("(?s)<%--.*?--%>", "")
            .replaceAll("(?s)<%=.*?%>", "/carlos")
            .replaceAll("(?s)<%.*?%>", "");
        assertThat(Jsoup.parse(body).select("form[method=post]")).isNotEmpty();
        var request = new MockHttpServletRequest("GET", "/PMmodule/FacilityManager");
        request.setContextPath("/carlos");
        request.setDispatcherType(DispatcherType.REQUEST);
        var response = new MockHttpServletResponse();
        var filter = new CsrfGuardScriptInjectionFilter();
        filter.init(mock(FilterConfig.class));
        CsrfGuard guard = mock(CsrfGuard.class);
        when(guard.isEnabled()).thenReturn(true);
        try (MockedStatic<CsrfGuard> csrf = mockStatic(CsrfGuard.class)) {
            csrf.when(CsrfGuard::getInstance).thenReturn(guard);
            filter.doFilter(request, response, (ignored, output) -> {
                output.setContentType("text/html;charset=UTF-8");
                output.getWriter().write(body);
            });
        }
        assertThat(response.getContentAsString()).contains("<script src=\"/carlos/csrfguard\"></script>");
    }
}
