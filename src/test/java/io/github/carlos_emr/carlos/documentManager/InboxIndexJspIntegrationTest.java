/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.documentManager;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.test.web.JspRenderServer;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Path;
import java.util.Locale;
import java.util.ResourceBundle;
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
 * Renders the classic inbox index, oscarMDS/Index.jsp, the {@code dms_index} result of
 * {@code documentManager/inboxManage} (prepareForIndexPage), with the request attributes that
 * method leaves.
 *
 * <p>4f6f848e7ca3 (#6135) added a warning banner that the page shows when the
 * {@code inboxDocumentsIncomplete} request attribute is {@code Boolean.TRUE}: some document rows
 * could not be listed, so the inbox is incomplete. This pins that the banner appears once, as an
 * alert, in the page's language, and not at all when the attribute is false or absent. Before
 * #6336 nothing rendered it.</p>
 *
 * <p>The banner is rendered here with the attribute set by the test. In the production route the
 * attribute is never set on this page: prepareForIndexPage does not set it, only
 * prepareForContentPage does, and that method renders oscarMDS/Page.jsp, which has a banner of its
 * own (see the #6336 Task 10 report). The sidebar's {@code categoryData} is left out: the banner
 * does not read it, and every expression that does tolerates its absence.</p>
 */
@Tag("integration")
@Tag("documentManager")
@Tag("inbox")
class InboxIndexJspIntegrationTest {
    private static final String PAGE = "/WEB-INF/jsp/oscarMDS/Index.jsp";
    private static final String BANNER = "div[data-inbox-incomplete]";

    @TempDir
    static Path directory;
    private static JspRenderServer server;

    @BeforeAll
    static void startServer() throws Exception {
        server = JspRenderServer.builder(directory)
                .jsp(PAGE)
                .bean(SecObjPrivilegeDao.class, JspRenderServer.privilegeDaoGranting("doctor", "x"))
                .start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"en", "fr-CA"})
    void shouldWarnThatTheInboxIsIncomplete_whenDocumentRowsWereOmitted(String language) throws Exception {
        ResourceBundle bundle = ResourceBundle.getBundle("oscarResources", Locale.forLanguageTag(language));

        Document html = server.request(PAGE)
                .header("Accept-Language", language)
                .render(request -> {
                    prepareIndex(request);
                    request.setAttribute("inboxDocumentsIncomplete", Boolean.TRUE);
                })
                .assertOk()
                .document();

        Elements banners = html.select(BANNER);
        assertThat(banners).hasSize(1);
        Element banner = banners.first();
        assertThat(banner.attr("data-inbox-incomplete")).isEqualTo("true");
        assertThat(banner.attr("role")).isEqualTo("alert");
        assertThat(banner.classNames()).contains("alert", "alert-warning");
        assertThat(banner.wholeText()).isEqualTo(bundle.getString("inbox.documentsIncomplete"));
        assertThat(banner.children()).isEmpty();
        // Above the inbox itself, not inside the area the report rows are loaded into.
        assertThat(html.body().child(0)).isSameAs(banner);
        assertThat(html.select("#docViews " + BANNER)).isEmpty();
        assertThat(html.getElementById("inbox_wrapper")).isNotNull();
    }

    @Test
    void shouldShowNoIncompleteWarning_whenEveryDocumentRowWasListed() throws Exception {
        Document listed = server.render(PAGE, request -> {
            prepareIndex(request);
            request.setAttribute("inboxDocumentsIncomplete", Boolean.FALSE);
        }).assertOk().document();

        assertThat(listed.select(BANNER)).isEmpty();
        assertThat(listed.select("div[role=alert]")).isEmpty();
        assertThat(listed.getElementById("inbox_wrapper")).isNotNull();

        Document unset = server.render(PAGE, InboxIndexJspIntegrationTest::prepareIndex).assertOk().document();

        assertThat(unset.select(BANNER)).isEmpty();
        assertThat(unset.select("div[role=alert]")).isEmpty();
        assertThat(unset.getElementById("inbox_wrapper")).isNotNull();
    }

    /** The signed-in session and what prepareForIndexPage sets for a provider's new-items inbox. */
    private static void prepareIndex(HttpServletRequest request) {
        request.getSession().setAttribute("userrole", "doctor");
        request.getSession().setAttribute("user", "999998");
        request.setAttribute("patientFirstName", "");
        request.setAttribute("patientLastName", "");
        request.setAttribute("patientHealthNumber", "");
        request.setAttribute("providerNo", "999998");
        request.setAttribute("searchProviderNo", "999998");
        request.setAttribute("ackStatus", "N");
        request.setAttribute("abnormalStatus", "all");
    }
}
