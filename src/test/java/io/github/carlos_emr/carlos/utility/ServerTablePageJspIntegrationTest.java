/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.utility;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.commn.dao.QueryPage;
import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.ProfessionalSpecialist;
import io.github.carlos_emr.carlos.test.web.JspRenderServer;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Path;
import java.text.MessageFormat;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.stream.IntStream;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Renders {@code /WEB-INF/jspf/server-table-page.jspf}, the fragment the server-paged tables
 * (9c2014ef3867, #6134) include below their rows, inside the real Ontario referral directory page
 * ({@code admin/billingreferralAdmin.jsp}, the {@code list} result of
 * {@code admin/ManageBillingReferral}) with the request attributes {@link ServerTablePage#publish}
 * leaves. The fragment must show the page banner ("N items found, displaying a to b.") and a form
 * with First, Prev, Next and Last buttons that send {@code tablePage}, disabled where there is no
 * such page, the "page / pages" counter, the retained search parameters as encoded hidden fields,
 * the action and method the action published, and the error slot and scripts the in-page paging
 * uses; without a published page it renders nothing. Before #6336 the packaged browser check paged
 * the Attach Client and BC tray tables through these controls, but no test asserted the banner or
 * each button, and nothing rendered the referral directory's include.
 *
 * <p>The referral page itself is served with an empty checklist; none of the classes it runs keeps
 * a {@code SpringUtils} bean in a static field.</p>
 */
@Tag("integration")
@Tag("utility")
class ServerTablePageJspIntegrationTest {
    private static final String PAGE = "/WEB-INF/jsp/admin/billingreferralAdmin.jsp";
    /** Rows per page in the tests; the fragment shows whatever page size the action used. */
    private static final int SIZE = 50;
    private static final int TOTAL = 120;
    private static final String HOSTILE_NAME = "FAKE \"Smith\" & <b>Co</b>";

    @TempDir
    static Path directory;
    private static JspRenderServer server;

    @BeforeAll
    static void startServer() throws Exception {
        server = JspRenderServer.builder(directory)
                .jsp(PAGE)
                .bean(SecObjPrivilegeDao.class, JspRenderServer.privilegeDaoGranting("admin", "x"))
                .start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void shouldShowTheBannerAndEnableEveryButton_onAMiddlePage() throws Exception {
        Document html = render(new QueryPage<>(specialists(50, SIZE), TOTAL, 1, SIZE), searchParameters()).document();

        assertThat(banner(html)).isEqualTo(banner(TOTAL, 51, 100));
        Element form = pager(html);
        assertButton(form, "first", "table.paging.first", "0");
        assertButton(form, "previous", "table.paging.previous", "0");
        assertButton(form, "next", "table.paging.next", "2");
        assertButton(form, "last", "table.paging.last", "2");
        assertThat(counter(form)).isEqualTo("2 / 3");
        // The page's own rows are the ones the published page holds.
        assertThat(html.select("#referral tbody tr")).hasSize(SIZE);
    }

    @Test
    void shouldDisableFirstAndPrev_onTheFirstPage() throws Exception {
        Document html = render(new QueryPage<>(specialists(0, SIZE), TOTAL, 0, SIZE), searchParameters()).document();

        assertThat(banner(html)).isEqualTo(banner(TOTAL, 1, 50));
        Element form = pager(html);
        assertDisabled(form, "first");
        assertDisabled(form, "previous");
        assertButton(form, "next", "table.paging.next", "1");
        assertButton(form, "last", "table.paging.last", "2");
        assertThat(counter(form)).isEqualTo("1 / 3");
    }

    @Test
    void shouldDisableNextAndLast_onTheLastPartialPage() throws Exception {
        Document html = render(new QueryPage<>(specialists(100, 20), TOTAL, 2, SIZE), searchParameters()).document();

        assertThat(banner(html)).isEqualTo(banner(TOTAL, 101, 120));
        Element form = pager(html);
        assertButton(form, "first", "table.paging.first", "0");
        assertButton(form, "previous", "table.paging.previous", "1");
        assertDisabled(form, "next");
        assertDisabled(form, "last");
        assertThat(counter(form)).isEqualTo("3 / 3");
    }

    @Test
    void shouldShowOneEmptyPage_whenNothingMatches() throws Exception {
        Document html = render(new QueryPage<ProfessionalSpecialist>(List.of(), 0, 0, SIZE), searchParameters())
                .document();

        assertThat(banner(html)).isEqualTo(banner(0, 0, 0));
        Element form = pager(html);
        for (String button : List.of("first", "previous", "next", "last")) {
            assertDisabled(form, button);
        }
        assertThat(counter(form)).isEqualTo("1 / 1");
    }

    @Test
    void shouldKeepTheSearchInEncodedHiddenFields_andSubmitToThePublishedAction() throws Exception {
        JspRenderServer.RenderedPage page = render(new QueryPage<>(specialists(0, SIZE), TOTAL, 0, SIZE),
                searchParameters());

        Element form = pager(page.document());
        assertThat(form.attr("action")).isEqualTo(JspRenderServer.CONTEXT_PATH + "/admin/ManageBillingReferral");
        assertThat(form.attr("method")).isEqualTo("get");
        assertThat(form.attr("data-context")).isEqualTo(JspRenderServer.CONTEXT_PATH);
        List<String> fields = form.select("input").stream()
                .map(input -> input.attr("type") + " " + input.attr("name") + "=" + input.val()).toList();
        assertThat(fields).containsExactly("hidden method=advancedSearch", "hidden nameQuery=" + HOSTILE_NAME,
                "hidden specialtyQuery=FAKE first", "hidden specialtyQuery=FAKE second", "hidden showHidden=on");
        assertThat(page.body()).contains("value=\"FAKE &#34;Smith&#34; &amp; &lt;b>Co&lt;/b>\"")
                .doesNotContain(HOSTILE_NAME);
        assertThat(form.select("b")).isEmpty();
    }

    @Test
    void shouldPostThePager_whenTheActionPublishesAPostForm() throws Exception {
        Map<String, String[]> parameters = new LinkedHashMap<>();
        parameters.put("criteria.lastName", new String[] {"FAKE"});
        parameters.put("method", new String[] {"attachSearch"});

        Element form = pager(render(new QueryPage<>(specialists(0, SIZE), TOTAL, 0, SIZE), parameters,
                "/PMmodule/ClientSearch2", "post").document());

        assertThat(form.attr("action")).isEqualTo(JspRenderServer.CONTEXT_PATH + "/PMmodule/ClientSearch2");
        assertThat(form.attr("method")).isEqualTo("post");
        assertThat(form.select("input").stream().map(input -> input.attr("name") + "=" + input.val()).toList())
                .containsExactly("criteria.lastName=FAKE", "method=attachSearch");
    }

    @Test
    void shouldGiveThePagingScriptsAnErrorSlot_insideTheSwappedRegion() throws Exception {
        ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", Locale.ENGLISH);

        Document html = render(new QueryPage<>(specialists(0, SIZE), TOTAL, 0, SIZE), searchParameters()).document();

        Element form = pager(html);
        Elements errors = form.select("span[data-server-page-error]");
        assertThat(errors).hasSize(1);
        assertThat(errors.first().attr("role")).isEqualTo("alert");
        assertThat(errors.first().attr("data-error")).isEqualTo(bundle.getString("table.paging.loadFailed"));
        assertThat(errors.first().text()).isEqualTo("");
        Element region = html.getElementById("serverTableRegion");
        assertThat(region.hasAttr("data-server-page-region")).isTrue();
        assertThat(form.parent()).isSameAs(region);
        assertThat(html.select("span.pagebanner").first().parent()).isSameAs(region);
        List<String> scripts = region.select("script").stream()
                .map(script -> script.attr("src") + (script.hasAttr("defer") ? " defer" : "")).toList();
        assertThat(scripts).containsExactly(JspRenderServer.CONTEXT_PATH + "/share/javascript/csrfTokenFetch.js defer",
                JspRenderServer.CONTEXT_PATH + "/js/server-table-page.js defer");
    }

    @Test
    void shouldRenderNoPager_whenTheActionPublishedNoPage() throws Exception {
        Document html = server.render(PAGE, ServerTablePageJspIntegrationTest::signIn).assertOk().document();

        assertThat(html.select("span.pagebanner")).isEmpty();
        assertThat(html.select("form[data-server-page-form]")).isEmpty();
        assertThat(html.select("button[name=tablePage]")).isEmpty();
        assertThat(html.select("script").stream().map(script -> script.attr("src"))
                .filter(src -> src.endsWith("/js/server-table-page.js")).toList()).isEmpty();
        assertThat(html.getElementById("serverTableRegion").text())
                .isEqualTo(ResourceBundle.getBundle("oscarResources", Locale.ENGLISH).getString("table.msg.emptyList"));
    }

    /** Renders the referral directory as {@code BillingreferralEdit2Action} leaves it for a GET search. */
    private static JspRenderServer.RenderedPage render(QueryPage<ProfessionalSpecialist> page,
            Map<String, String[]> parameters) throws Exception {
        return render(page, parameters, "/admin/ManageBillingReferral", "get");
    }

    private static JspRenderServer.RenderedPage render(QueryPage<ProfessionalSpecialist> page,
            Map<String, String[]> parameters, String action, String method) throws Exception {
        return server.render(PAGE, request -> {
            signIn(request);
            request.setAttribute("referrals", page.getRows());
            request.setAttribute("selectedReferralIds", new HashSet<Integer>());
            ServerTablePage.publish(request, page, action, method, parameters);
        }).assertOk();
    }

    /** The parameters the referral action keeps for its GET search, with one repeated. */
    private static Map<String, String[]> searchParameters() {
        Map<String, String[]> parameters = new LinkedHashMap<>();
        parameters.put("method", new String[] {"advancedSearch"});
        parameters.put("nameQuery", new String[] {HOSTILE_NAME});
        parameters.put("specialtyQuery", new String[] {"FAKE first", "FAKE second"});
        parameters.put("showHidden", new String[] {"on"});
        return parameters;
    }

    /** FAKE specialists numbered from {@code first}, as a page of the directory holds them. */
    private static List<ProfessionalSpecialist> specialists(int first, int count) {
        return IntStream.range(first, first + count).mapToObj(index -> {
            ProfessionalSpecialist specialist = new ProfessionalSpecialist(9000 + index);
            specialist.setFirstName("FAKE");
            specialist.setLastName("Specialist" + index);
            specialist.setSpecialtyType("FAKE specialty");
            specialist.setStreetAddress("1 FAKE Street");
            specialist.setPhoneNumber("555-0100");
            specialist.setFaxNumber("555-0199");
            specialist.setReferralNo("FAKE" + index);
            return specialist;
        }).toList();
    }

    /** The session the page's {@code _admin,_admin.misc} check reads. */
    private static void signIn(HttpServletRequest request) {
        request.getSession().setAttribute("userrole", "admin");
        request.getSession().setAttribute("user", "999998");
    }

    private static Element pager(Document html) {
        Elements forms = html.select("form[data-server-page-form]");
        assertThat(forms).hasSize(1);
        Element form = forms.first();
        assertThat(form.id()).isEqualTo("serverTablePageForm");
        return form;
    }

    private static String banner(Document html) {
        Elements banners = html.select("span.pagebanner");
        assertThat(banners).hasSize(1);
        assertThat(banners.first().attr("aria-live")).isEqualTo("polite");
        return banners.first().text();
    }

    /** The banner as JSTL formats it: the bundle sentence with the total, the items word and the window. */
    private static String banner(long total, long from, long to) {
        ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", Locale.ENGLISH);
        return new MessageFormat(bundle.getString("table.paging.someItemsFound"), Locale.ENGLISH)
                .format(new Object[] {total, bundle.getString("table.paging.itemsName"), from, to});
    }

    /** The "page / pages" text: the form's one span that is not the error slot. */
    private static String counter(Element form) {
        List<Element> spans = form.children().stream()
                .filter(child -> "span".equals(child.tagName()) && !child.hasAttr("data-server-page-error")).toList();
        assertThat(spans).hasSize(1);
        return spans.get(0).text();
    }

    private static Element button(Element form, String focus) {
        Elements buttons = form.select("button[data-server-page-focus=" + focus + "]");
        assertThat(buttons).hasSize(1);
        Element button = buttons.first();
        assertThat(button.attr("type")).isEqualTo("submit");
        assertThat(button.attr("name")).isEqualTo("tablePage");
        return button;
    }

    private static void assertButton(Element form, String focus, String labelKey, String value) {
        Element button = button(form, focus);
        assertThat(button.hasAttr("disabled")).isFalse();
        assertThat(button.val()).isEqualTo(value);
        assertThat(button.text()).isEqualTo(ResourceBundle.getBundle("oscarResources", Locale.ENGLISH).getString(labelKey));
    }

    private static void assertDisabled(Element form, String focus) {
        assertThat(button(form, focus).hasAttr("disabled")).isTrue();
    }
}
