/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.test.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.github.carlos_emr.carlos.utility.SpringUtils;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.BeanFactory;

/**
 * Runs {@link JspRenderServer}, the shared fixture of the #6336 JSP render tests, against test-only
 * pages: the forward carries the test's request, session and parameters; literal includes,
 * {@code *.jspf} includes and stand-in routes render; the taglibs, the bundle and UTF-8 output
 * behave as in production; failures and redirects reach the test; and the {@link SpringUtils} bean
 * factory is the fixture's only while it runs. {@code JspRenderServerUnitTest} covers the web
 * application assembly without Tomcat.
 */
@Tag("integration")
@Tag("web")
class JspRenderServerIntegrationTest {
    private static final String FIXTURE = "/WEB-INF/jsp/jsp-render-fixture/";
    private static final String PAGE = FIXTURE + "page.jsp";
    private static final String FAILING_PAGE = FIXTURE + "fails.jsp";
    private static final String FAILING_PAGE_WITHOUT_ERROR_PAGE = FIXTURE + "failsWithoutErrorPage.jsp";
    private static final String REDIRECTING_PAGE = FIXTURE + "redirects.jsp";
    private static final String GREETING_BEAN = "jspRenderFixtureGreeting";

    /** Reads the request, session, parameters, a bean, the bundle and two includes; includes taglibs.jsp. */
    private static final String PAGE_TEXT = String.join("\n",
            "<%@ page contentType=\"text/html; charset=UTF-8\" pageEncoding=\"UTF-8\" %>",
            "<%@ include file=\"/taglibs.jsp\" %>",
            "<%@ taglib uri=\"carlos\" prefix=\"carlos\" %>",
            "<%@ page import=\"io.github.carlos_emr.carlos.utility.SpringUtils\" %>",
            "<fmt:setBundle basename=\"oscarResources\"/>",
            "<!DOCTYPE html>",
            "<html lang=\"${pageContext.request.locale.language}\"><body>",
            "<p id=\"attribute\"><carlos:encode value=\"${requestScope.fixtureAttribute}\"/></p>",
            "<p id=\"session\"><carlos:encode value=\"${sessionScope.fixtureSession}\"/></p>",
            "<p id=\"parameter\"><carlos:encode value=\"${param.fixtureParameter}\"/></p>",
            "<p id=\"method\"><%= request.getMethod() %></p>",
            "<p id=\"bean\"><carlos:encode value='<%= String.valueOf(SpringUtils.getBean(\"" + GREETING_BEAN + "\")) %>'/></p>",
            "<p id=\"message\"><fmt:message key=\"global.about\"/></p>",
            "<p id=\"unicode\">Équipe 日本語</p>",
            "<%@ include file=\"static.jspf\" %>",
            "<jsp:include page=\"dynamic.jspf\"/>",
            "<jsp:include page=\"/jsp-render-fixture/StrutsRoute\"/>",
            "</body></html>");

    @TempDir
    static Path directory;
    private static JspRenderServer server;

    @BeforeAll
    static void startServer() throws Exception {
        server = JspRenderServer.builder(directory)
                .file(PAGE, PAGE_TEXT)
                .file(FIXTURE + "static.jspf", "<p id=\"static\">static fragment</p>")
                // Runs as a page only if *.jspf is mapped to the JSP servlet, as in production.
                .file(FIXTURE + "dynamic.jspf", "<p id=\"dynamic\"><%= \"dynamic\" + \" fragment\" %></p>")
                .file(FAILING_PAGE, "<%@ include file=\"/taglibs.jsp\" %><% if (true) { "
                        + "throw new IllegalStateException(\"FAKE page failure\"); } %>")
                .file(FAILING_PAGE_WITHOUT_ERROR_PAGE, "<% if (true) { "
                        + "throw new IllegalStateException(\"FAKE unhandled failure\"); } %>")
                .file(REDIRECTING_PAGE,
                        "<% response.sendRedirect(request.getContextPath() + \"/securityError?type=_fixture\"); %>")
                .bean(GREETING_BEAN, "FAKE greeting from a bean")
                .servlet("/jsp-render-fixture/StrutsRoute", new RouteStandIn())
                .start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void shouldRenderTheForwardedPage_withTheRequestItWasGiven() throws Exception {
        JspRenderServer.RenderedPage page = server.request(PAGE)
                .parameter("fixtureParameter", "FAKE parameter & more")
                .header("Accept-Language", "fr-CA")
                .render(request -> {
                    request.setAttribute("fixtureAttribute", "<b>FAKE attribute</b>");
                    request.getSession().setAttribute("fixtureSession", "FAKE session value");
                })
                .assertOk();

        Document html = page.document();
        assertThat(html.selectFirst("html").attr("lang")).isEqualTo("fr");
        assertThat(html.getElementById("attribute").text()).isEqualTo("<b>FAKE attribute</b>");
        assertThat(html.select("#attribute b")).isEmpty();
        assertThat(html.getElementById("session").text()).isEqualTo("FAKE session value");
        assertThat(html.getElementById("parameter").text()).isEqualTo("FAKE parameter & more");
        assertThat(html.getElementById("method").text()).isEqualTo("GET");
        assertThat(html.getElementById("bean").text()).isEqualTo("FAKE greeting from a bean");
        assertThat(html.getElementById("message").text()).isEqualTo("À propos de");
        assertThat(html.getElementById("unicode").text()).isEqualTo("Équipe 日本語");
        assertThat(html.getElementById("static").text()).isEqualTo("static fragment");
        assertThat(html.getElementById("dynamic").text()).isEqualTo("dynamic fragment");
        assertThat(html.getElementById("route").text()).isEqualTo("stand-in route");
        assertThat(page.header("Content-Type").orElseThrow()).contains("UTF-8");
    }

    @Test
    void shouldSendEnglishFormPost_whenTheTestAsksForOne() throws Exception {
        Document html = server.request(PAGE)
                .parameter("fixtureParameter", "FAKE posted é")
                .post()
                .render(request -> { })
                .assertOk()
                .document();

        assertThat(html.selectFirst("html").attr("lang")).isEqualTo("en");
        assertThat(html.getElementById("method").text()).isEqualTo("POST");
        assertThat(html.getElementById("parameter").text()).isEqualTo("FAKE posted é");
        assertThat(html.getElementById("message").text()).isEqualTo("About");
        assertThat(html.getElementById("session").text()).isEmpty();
    }

    @Test
    void shouldReportThePageException_throughTheStandInErrorPage() throws Exception {
        JspRenderServer.RenderedPage page = server.render(FAILING_PAGE, request -> { });

        assertThat(page.status()).isEqualTo(500);
        assertThat(page.failure().orElseThrow())
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("FAKE page failure");
        assertThat(page.document().getElementById("jsp-render-failure").text()).contains("FAKE page failure");
        assertThatThrownBy(page::assertOk)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("FAKE page failure");
    }

    @Test
    void shouldReportThePageException_whenThePageNamesNoErrorPage() throws Exception {
        JspRenderServer.RenderedPage page = server.render(FAILING_PAGE_WITHOUT_ERROR_PAGE, request -> { });

        assertThat(page.status()).isEqualTo(500);
        assertThat(page.failure()).isPresent();
        assertThatThrownBy(page::assertOk)
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("FAKE unhandled failure");
    }

    @Test
    void shouldReturnTheRedirect_withoutFollowingIt() throws Exception {
        JspRenderServer.RenderedPage page = server.render(REDIRECTING_PAGE, request -> { });

        assertThat(page.status()).isEqualTo(302);
        assertThat(page.header("Location")).hasValue("/carlos/securityError?type=_fixture");
        assertThat(page.failure()).isEmpty();
    }

    @Test
    void shouldRethrowTheSetupFailure_onTheTestThread() {
        assertThatThrownBy(() -> server.render(PAGE, request -> {
            throw new IllegalArgumentException("FAKE setup failure");
        }))
                .isInstanceOf(IllegalStateException.class)
                .hasCauseInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void shouldCopyAPageWithItsIncludes_whenItIsFirstRendered() throws Exception {
        Path source = directory.resolve("late-source");
        Files.createDirectories(source.resolve("WEB-INF/jsp/late"));
        Files.writeString(source.resolve("WEB-INF/jsp/late/page.jsp"),
                "<%@ include file=\"part.jspf\" %><p id=\"page\">late page</p>");
        Files.writeString(source.resolve("WEB-INF/jsp/late/part.jspf"), "<p id=\"part\">late fragment</p>");
        try (JspRenderServer late = JspRenderServer.builder(directory).source(source).start()) {
            assertThat(Files.exists(late.webapp().resolve("WEB-INF/jsp/late/page.jsp"))).isFalse();

            Document html = late.render("/WEB-INF/jsp/late/page.jsp", request -> { }).assertOk().document();

            assertThat(html.getElementById("page").text()).isEqualTo("late page");
            assertThat(html.getElementById("part").text()).isEqualTo("late fragment");
            assertThat(Files.exists(late.webapp().resolve("WEB-INF/jsp/late/part.jspf"))).isTrue();
            assertThat(Files.exists(late.webapp().resolve(JspRenderServer.ERROR_PAGE))).isTrue();
        }
    }

    @Test
    void shouldInstallItsBeans_onlyUntilClosed() throws Exception {
        BeanFactory outer = SpringUtils.getBeanFactory();
        JspRenderServer second = JspRenderServer.builder(directory)
                .bean(GREETING_BEAN, "FAKE second greeting")
                .start();
        try {
            assertThat(SpringUtils.getBeanFactory()).isNotSameAs(outer);
            assertThat(SpringUtils.getBean(GREETING_BEAN)).isEqualTo("FAKE second greeting");
        } finally {
            second.close();
        }

        assertThat(SpringUtils.getBeanFactory()).isSameAs(outer);
        assertThat(SpringUtils.getBean(GREETING_BEAN)).isEqualTo("FAKE greeting from a bean");
        second.close();
        assertThat(SpringUtils.getBeanFactory()).isSameAs(outer);
    }

    /** Stands in for a Struts route that a page includes with {@code <jsp:include>}. */
    private static final class RouteStandIn extends HttpServlet {
        @Override
        protected void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException {
            response.getWriter().write("<p id=\"route\">stand-in route</p>");
        }

        @Override
        protected void doPost(HttpServletRequest request, HttpServletResponse response) throws IOException {
            doGet(request, response);
        }
    }
}
