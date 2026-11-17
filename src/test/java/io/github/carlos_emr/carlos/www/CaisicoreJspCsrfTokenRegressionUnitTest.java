/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.www;

import io.github.carlos_emr.carlos.app.CsrfGuardScriptInjectionFilter;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterConfig;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.MockedStatic;
import org.owasp.csrfguard.CsrfGuard;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * The CAISI administration pages under {@code WEB-INF/jsp/caisicore} are each loaded as their own document in the
 * Administration panel's iframe. A classic POST form there gets its CSRF token only from the CSRFGuard client, and
 * {@link CsrfGuardScriptInjectionFilter} adds that client only before {@code </head>} or {@code </body>}: a page with
 * neither is treated as a fragment and served unchanged, so its form posts no token and every save is refused.
 * The Issue Editor's add form was such a page (#6078).
 *
 * <p>Each page with a real same-origin POST form is pushed through the real filter as rendered template text (JSP
 * comments, directives and scriptlets removed, expressions replaced by a placeholder), and must come out carrying the
 * client script, unless it bootstraps the token itself with {@code csrf-token.jspf}.
 *
 * <p>This is a source-level approximation, not a JSP render: {@code <%@ include %>} and
 * {@code <jsp:include>} are not expanded. A page that took its {@code </head>} or {@code </body>}
 * from an included file would be reported as a fragment, and the check would have to learn to
 * follow that include. The rendered behaviour is pinned separately for the Issue Editor by the
 * {@code admin-issue-editor-save} Playwright check.
 *
 * @since 2026-11-09
 */
@DisplayName("CAISI admin pages with a POST form receive the CSRFGuard client")
@Tag("unit")
@Tag("security")
@Tag("regression")
class CaisicoreJspCsrfTokenRegressionUnitTest {

    private static final Path CAISICORE = Path.of("src/main/webapp/WEB-INF/jsp/caisicore");
    private static final String CONTEXT = "/carlos";
    private static final String CLIENT_SCRIPT = "<script src=\"" + CONTEXT + "/csrfguard\"></script>";
    private static final String TOKEN_BOOTSTRAP = "/WEB-INF/jspf/csrf-token.jspf";

    /** Every caisicore JSP whose rendered template text holds a real POST form, in name order. */
    static Stream<Path> pagesWithPostForms() throws IOException {
        try (Stream<Path> files = Files.list(CAISICORE)) {
            List<Path> pages = files.filter(path -> path.getFileName().toString().endsWith(".jsp"))
                    .filter(path -> hasRealPostForm(rendered(read(path))))
                    .sorted()
                    .toList();
            return pages.stream();
        }
    }

    @Test
    @DisplayName("should find the Issue Editor's add form among the pages the guard checks")
    void shouldIncludeIssueEditorForm_inScannedPages() throws IOException {
        assertThat(pagesWithPostForms().map(path -> path.getFileName().toString()))
                .as("CAISI pages with a real POST form")
                .contains("issueAdminForm.jsp", "SystemMessageForm.jsp", "FacilityMessageForm.jsp",
                        "DefaultEncounterIssueForm.jsp");
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("pagesWithPostForms")
    @DisplayName("should receive the CSRFGuard client, or bootstrap the token itself, when it has a POST form")
    void shouldReceiveCsrfGuardClient_whenPageHasPostForm(Path page) throws Exception {
        String source = read(page);
        if (source.contains(TOKEN_BOOTSTRAP)) {
            return;
        }

        String served = serveThroughFilter(rendered(source));

        assertThat(served)
                .as("%s must render </head> or </body> so the filter adds the CSRFGuard client", page.getFileName())
                .contains(CLIENT_SCRIPT);
    }

    @Test
    @DisplayName("should report a POST form left as a fragment, as the Issue Editor form was before #6078")
    void shouldReportMissingClient_forPostFormFragment() throws Exception {
        String fragment = "<title>MyIssues ~ Issue Details</title>\n"
                + "<form action=\"/carlos/issueAdmin\" method=\"post\"><input type=\"submit\" value=\"Save\"/></form>";

        assertThat(hasRealPostForm(fragment)).isTrue();
        assertThat(serveThroughFilter(fragment)).doesNotContain(CLIENT_SCRIPT).isEqualTo(fragment);
    }

    @Test
    @DisplayName("should ignore </head> text that only a JSP comment carries")
    void shouldIgnoreHeadText_insideJspComment() {
        String source = "<%-- the filter injects before </head> or </body> --%>\n"
                + "<form action=\"<%= request.getContextPath() %>/issueAdmin\" method=\"post\"></form>";

        String rendered = rendered(source);

        assertThat(rendered).doesNotContain("</head>").doesNotContain("</body>");
        assertThat(hasRealPostForm(rendered)).isTrue();
    }

    /** True when the page has a POST form whose action is a URL, the forms CSRFGuard's client fills in. */
    private static boolean hasRealPostForm(String html) {
        for (Element form : Jsoup.parse(html).select("form")) {
            String method = form.attr("method").trim().toLowerCase(Locale.ROOT);
            String action = form.attr("action").trim().toLowerCase(Locale.ROOT);
            if ("post".equals(method) && !action.isEmpty() && !action.startsWith("javascript:")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Approximates what the JSP renders as template text: JSP comments, directives and scriptlets produce no output,
     * and an expression produces some text.
     */
    private static String rendered(String jsp) {
        return jsp.replaceAll("(?s)<%--.*?--%>", "")
                .replaceAll("(?s)<%=.*?%>", "x")
                .replaceAll("(?s)<%.*?%>", "");
    }

    /**
     * Serves {@code body} as a text/html response through a real, CSRFGuard-enabled
     * {@link CsrfGuardScriptInjectionFilter} and returns what the browser would receive.
     */
    private static String serveThroughFilter(String body) throws Exception {
        CsrfGuardScriptInjectionFilter filter = new CsrfGuardScriptInjectionFilter();
        filter.init(mock(FilterConfig.class));
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/issueAdmin");
        request.setContextPath(CONTEXT);
        request.setDispatcherType(DispatcherType.REQUEST);
        MockHttpServletResponse response = new MockHttpServletResponse();

        CsrfGuard csrfGuard = mock(CsrfGuard.class);
        when(csrfGuard.isEnabled()).thenReturn(true);
        try (MockedStatic<CsrfGuard> csrfGuardMock = mockStatic(CsrfGuard.class)) {
            csrfGuardMock.when(CsrfGuard::getInstance).thenReturn(csrfGuard);
            filter.doFilter(request, response, (servletRequest, servletResponse) -> {
                servletResponse.setContentType("text/html;charset=UTF-8");
                servletResponse.getWriter().write(body);
            });
        }
        return response.getContentAsString();
    }

    /** Reads a JSP as UTF-8, failing the test with the path when it cannot be read. */
    private static String read(Path path) {
        try {
            return Files.readString(path, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }
}
