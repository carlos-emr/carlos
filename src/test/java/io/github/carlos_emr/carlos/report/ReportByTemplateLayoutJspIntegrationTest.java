/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.report;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.ReportTemplatesDao;
import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.RBTGroup;
import io.github.carlos_emr.carlos.commn.model.ReportTemplates;
import io.github.carlos_emr.carlos.managers.RBTGroupManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.report.reportByTemplate.ReportObjectGeneric;
import io.github.carlos_emr.carlos.test.web.JspRenderServer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.jsp.tagext.TagSupport;
import java.nio.file.Path;
import java.util.List;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Renders the six Report by Template pages, each the way its route's action leaves the request,
 * and checks the page layout 829c8033b91e (#6236) gave them: every page links the scoped
 * {@code reportByTemplateLayout.css} once, in its head, and marks its body {@code rbt-page}, the
 * class every rule of that stylesheet is scoped to; the shared {@code rbtTopNav.jspf} bar is
 * {@code sticky-top} with an opaque {@code bg-body}, a border and spacing instead of
 * {@code fixed-top}, so it stays in the page flow directly above the page heading rather than
 * covering it; and the result page's heading carries the id {@code rbtReportHeading}, which names
 * the result's focusable {@code role="region"} (the region the stylesheet lets scroll sideways).
 * The groups page also shows the action's error encoded in an alert (7230348d4233, #6232).
 *
 * <p>Before #6336 only {@code ReportByTemplateLayoutJspRegressionUnitTest} read the JSP text, and
 * the packaged checks never looked at layout. Computed styles (the bar's {@code position},
 * {@code overflow-x}, print hiding) need a browser and are outside this render test.</p>
 *
 * <p>The fixture has no Struts, so the {@code /struts-tags} URI that {@code addEditTemplate.jsp}
 * declares is mapped by a test TLD to {@link NoActionErrorsTag}, which renders nothing, as
 * {@code <s:if test="hasActionErrors()">} does when the action reported no error. The pages' data
 * comes from a mocked {@link ReportTemplatesDao}, read through the real {@code ReportManager}, and
 * a mocked {@link RBTGroupManager}; none of the classes the pages run keeps a {@code SpringUtils}
 * bean in a static field. Public, because Jasper's generated page code names the nested tag
 * handler.</p>
 */
@Tag("integration")
@Tag("report")
public class ReportByTemplateLayoutJspIntegrationTest {
    private static final String DIRECTORY = "/WEB-INF/jsp/oscarReport/reportByTemplate/";
    private static final String STYLESHEET = "/oscarReport/reportByTemplate/reportByTemplateLayout.css";
    private static final String CONTEXT = JspRenderServer.CONTEXT_PATH;
    private static final String BAR_CLASSES = "navbar sticky-top bg-body border-bottom mb-2 noprint";
    private static final Integer TEMPLATE_NUMBER = 7;
    private static final String TEMPLATE_ID = "7";
    private static final String TITLE = "FAKE <i>Template</i> & Co";
    private static final String DESCRIPTION = "FAKE description";
    private static final String GROUP = "FAKE Group";
    private static final String RESULT_TABLE = "<table class=\"reportTable\"><thead><tr><th>FAKE column</th></tr></thead>"
            + "<tbody><tr><td>FAKE value</td></tr></tbody></table>";

    /** Maps the {@code /struts-tags} URI of addEditTemplate.jsp to the stand-in below. */
    private static final String STRUTS_TAGS_STAND_IN = """
            <?xml version="1.0" encoding="UTF-8" ?>
            <taglib xmlns="http://java.sun.com/xml/ns/javaee"
                    xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                    xsi:schemaLocation="http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-jsptaglibrary_2_1.xsd"
                    version="2.1">
                <tlib-version>1.0</tlib-version>
                <short-name>s</short-name>
                <uri>/struts-tags</uri>
                <tag>
                    <name>if</name>
                    <tag-class>%1$s</tag-class>
                    <body-content>JSP</body-content>
                    <attribute>
                        <name>test</name>
                        <required>true</required>
                        <rtexprvalue>false</rtexprvalue>
                    </attribute>
                </tag>
                <tag>
                    <name>actionerror</name>
                    <tag-class>%1$s</tag-class>
                    <body-content>empty</body-content>
                </tag>
            </taglib>
            """.formatted(NoActionErrorsTag.class.getName());

    @TempDir
    static Path directory;
    private static JspRenderServer server;

    @BeforeAll
    static void startServer() throws Exception {
        ReportTemplates template = new ReportTemplates();
        template.setId(TEMPLATE_NUMBER);
        template.setTemplateTitle(TITLE);
        template.setTemplateDescription(DESCRIPTION);
        template.setTemplateSql("SELECT 1");
        template.setTemplateXml("");
        template.setType("");
        template.setSequence(Boolean.FALSE);
        template.setActive(1);
        template.setUuid("FAKE-template-uuid");
        ReportTemplatesDao templates = mock(ReportTemplatesDao.class);
        when(templates.findActive()).thenReturn(List.of(template));
        when(templates.find((Object) TEMPLATE_NUMBER)).thenReturn(template);

        RBTGroup member = new RBTGroup();
        member.setGroupName(GROUP);
        member.setTemplateId(TEMPLATE_NUMBER);
        RBTGroupManager groups = mock(RBTGroupManager.class);
        when(groups.getTemplateGroups(any())).thenReturn(List.of(GROUP));
        when(groups.getGroup(any(), any())).thenReturn(List.of(member));

        server = JspRenderServer.builder(directory)
                .jsp(DIRECTORY + "homePage.jsp", DIRECTORY + "addEditTemplate.jsp", DIRECTORY + "reportConfiguration.jsp",
                        DIRECTORY + "viewTemplate.jsp", DIRECTORY + "resultReport.jsp", DIRECTORY + "templateGroups.jsp",
                        STYLESHEET)
                .file("/WEB-INF/struts-tags-stand-in.tld", STRUTS_TAGS_STAND_IN)
                .bean(SecObjPrivilegeDao.class, JspRenderServer.privilegeDaoGranting("admin", "x"))
                .bean(ReportTemplatesDao.class, templates)
                .bean(SecurityInfoManager.class, mock(SecurityInfoManager.class))
                .bean(RBTGroupManager.class, groups)
                .start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"homePage.jsp", "addEditTemplate.jsp", "reportConfiguration.jsp", "viewTemplate.jsp",
            "resultReport.jsp", "templateGroups.jsp"})
    void shouldLinkTheScopedStylesheetOnce_andMarkTheBodyForIt(String page) throws Exception {
        Document html = render(page).assertOk().document();

        List<Element> layout = html.select("link").stream()
                .filter(link -> (CONTEXT + STYLESHEET).equals(link.attr("href"))).toList();
        assertThat(layout).hasSize(1);
        assertThat(layout.get(0).attr("rel")).isEqualTo("stylesheet");
        assertThat(layout.get(0).parent()).isSameAs(html.head());
        assertThat(html.body().attr("class")).isEqualTo("rbt-page");

        // The link names a file the application serves, written for that body class.
        JspRenderServer.RenderedPage stylesheet = server.render(STYLESHEET, request -> { }).assertOk();
        assertThat(stylesheet.header("Content-Type").orElse("")).startsWith("text/css");
        assertThat(stylesheet.body()).contains(".rbt-page ");
    }

    @ParameterizedTest
    @ValueSource(strings = {"homePage.jsp", "addEditTemplate.jsp", "reportConfiguration.jsp", "viewTemplate.jsp",
            "resultReport.jsp", "templateGroups.jsp"})
    void shouldKeepTheStickyBarInThePageFlow_directlyAboveTheHeading(String page) throws Exception {
        Document html = render(page).assertOk().document();

        Elements bars = html.select("div.navbar");
        assertThat(bars).hasSize(1);
        Element bar = bars.first();
        assertThat(bar.attr("class")).isEqualTo(BAR_CLASSES);
        assertThat(html.select(".fixed-top")).isEmpty();
        assertThat(html.body().child(0)).isSameAs(bar);
        assertThat(bar.nextElementSibling().tagName()).isEqualTo("h3");

        assertThat(bar.child(0).attr("class")).isEqualTo("container-fluid px-0");
        Element menu = bar.selectFirst("ul");
        assertThat(menu.attr("class")).isEqualTo("nav gap-3");
        assertThat(menu.select("a").stream().map(link -> link.attr("href")).toList()).containsExactly(
                CONTEXT + "/oscarReport/reportByTemplate/ViewHomePage",
                CONTEXT + "/oscarReport/reportByTemplate/rbtGroup",
                CONTEXT + "/oscarReport/reportByTemplate/ViewAddEditTemplate");
    }

    @Test
    void shouldNameTheScrollingResultRegion_byTheReportHeading() throws Exception {
        Document html = render("resultReport.jsp").assertOk().document();

        Elements headings = html.select("#rbtReportHeading");
        assertThat(headings).hasSize(1);
        Element heading = headings.first();
        assertThat(heading.tagName()).isEqualTo("h3");
        assertThat(heading.text()).isEqualTo(TITLE + " " + DESCRIPTION);
        assertThat(heading.select("i")).isEmpty();

        Elements regions = html.select("div.reportBorderDiv");
        assertThat(regions).hasSize(1);
        Element region = regions.first();
        assertThat(region.attr("role")).isEqualTo("region");
        assertThat(region.attr("aria-labelledby")).isEqualTo(heading.id());
        assertThat(region.attr("tabindex")).isEqualTo("0");
        assertThat(region.select("table.reportTable td").text()).isEqualTo("FAKE value");
        // The class was added beside the unload beacon that discards the result kept for export.
        assertThat(html.body().attr("onunload")).isEqualTo("clearSession();");
    }

    @Test
    void shouldShowTheActionErrorEncoded_onTheGroupsPage() throws Exception {
        String error = "FAKE <b onmouseover=x>group</b> & \"name\" already exists";

        JspRenderServer.RenderedPage page = server.render(DIRECTORY + "templateGroups.jsp", request -> {
            request.setAttribute("rbtGroups", List.of(GROUP));
            request.setAttribute("errormsg", error);
        }).assertOk();

        Elements alerts = page.document().select("div.alert-danger[role=alert]");
        assertThat(alerts).hasSize(1);
        assertThat(alerts.first().text()).isEqualTo(error);
        assertThat(alerts.first().children()).isEmpty();
        assertThat(page.body()).contains("&lt;b onmouseover=x&gt;group&lt;/b&gt; &amp;")
                .doesNotContain("<b onmouseover");
        assertThat(render("templateGroups.jsp").assertOk().document().select("div.alert-danger[role=alert]")).isEmpty();
    }

    /** Renders a page with the request its route's action leaves. */
    private static JspRenderServer.RenderedPage render(String page) throws Exception {
        String jsp = DIRECTORY + page;
        return switch (page) {
            case "reportConfiguration.jsp", "viewTemplate.jsp" -> server.request(jsp)
                    .parameter("templateid", TEMPLATE_ID)
                    .render(ReportByTemplateLayoutJspIntegrationTest::signIn);
            case "resultReport.jsp" -> server.render(jsp, request -> {
                signIn(request);
                request.setAttribute("reportobject", new ReportObjectGeneric(TEMPLATE_ID, TITLE, DESCRIPTION));
                request.setAttribute("sql", "SELECT 1");
                request.setAttribute("resultsethtml", RESULT_TABLE);
            });
            case "templateGroups.jsp" -> server.render(jsp, request -> request.setAttribute("rbtGroups", List.of(GROUP)));
            default -> server.render(jsp, ReportByTemplateLayoutJspIntegrationTest::signIn);
        };
    }

    /** The session the pages' {@code _admin,_report} check reads; the bean grants the role every right. */
    private static void signIn(HttpServletRequest request) {
        request.getSession().setAttribute("userrole", "admin");
        request.getSession().setAttribute("user", "999998");
    }

    /**
     * Stand-in for Struts' {@code <s:if>} and {@code <s:actionerror/>} on addEditTemplate.jsp:
     * renders nothing, as they do when the action reported no error.
     */
    public static final class NoActionErrorsTag extends TagSupport {
        private static final long serialVersionUID = 1L;

        /** The OGNL test, {@code hasActionErrors()}; there is no action here, so it is false. */
        public void setTest(String test) {
            // Nothing to evaluate without Struts.
        }

        @Override
        public int doStartTag() {
            return SKIP_BODY;
        }
    }
}
