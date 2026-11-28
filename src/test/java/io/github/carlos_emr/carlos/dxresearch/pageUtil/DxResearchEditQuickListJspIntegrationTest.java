/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.dxresearch.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.test.web.JspRenderServer;
import java.nio.file.Path;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Renders the Disease Registry quick-list editor, the view of
 * {@code oscarResearch/oscarDxResearch/dxResearchLoadQuickListItems}
 * ({@link dxResearchLoadQuickListItems2Action}), with the quick-list name that action keeps in the
 * session. 72fedd972c50 (#6305) changed the heading's dash from the raw UTF-8 bytes of an em dash,
 * which Jasper misread in this encoding-less page as U+00E2 U+0080 U+0094, to {@code &#8212;}: the
 * heading must read as an em dash (U+2014) and then the list's name. Before #6336 the packaged
 * dx-registry-quicklist check read the heading for the name only, and
 * {@code JspPageEncodingRegressionTest} read the JSP source.
 */
@Tag("integration")
@Tag("dxresearch")
class DxResearchEditQuickListJspIntegrationTest {
    private static final String PAGE = "/WEB-INF/jsp/oscarResearch/oscarDxResearch/dxResearchEditQuickList.jsp";
    private static final String QUICK_LIST = "FAKE Quick List";
    private static final String MISREAD_EM_DASH = "\u00E2\u0080\u0094";

    @TempDir
    static Path directory;
    private static JspRenderServer server;

    @BeforeAll
    static void startServer() throws Exception {
        server = JspRenderServer.builder(directory).jsp(PAGE).start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void shouldShowAnEmDashBeforeTheListName_inTheEditorHeading() throws Exception {
        JspRenderServer.RenderedPage page = server.render(PAGE,
                request -> request.getSession().setAttribute("quickListName", QUICK_LIST)).assertOk();

        assertThat(page.header("Content-Type").orElse("")).containsIgnoringCase("charset=UTF-8");
        Element heading = page.document().selectFirst("h4.page-header-title");
        assertThat(heading.text())
                .endsWith("\u2014 " + QUICK_LIST)
                .doesNotContain(MISREAD_EM_DASH, "\uFFFD", "&#8212;");
        assertThat(page.body()).doesNotContain(MISREAD_EM_DASH, "\uFFFD");
        // The editor posts the same list back.
        assertThat(page.document().selectFirst("form input[name=quickListName]").val()).isEqualTo(QUICK_LIST);
    }
}
