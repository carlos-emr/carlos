/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.fax;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.FaxConfig;
import io.github.carlos_emr.carlos.test.web.JspRenderServer;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Path;
import java.util.List;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/**
 * Renders the fax cover page, the {@code preview} result of {@code fax/faxAction}'s prepareFax,
 * with the request attributes Fax2Action sets for an eForm fax.
 *
 * <p>CoverPage.jsp has no {@code pageEncoding} and web.xml sets none, so Jasper reads it as
 * ISO-8859-1. The "Loading preview" status used to end in a literal U+2026 (an ellipsis), which
 * that reading turned into three characters and the page served as mojibake. 72fedd972c (#6305)
 * wrote it as the numeric entity {@code &#8230;}, which is ASCII in the template and an ellipsis in
 * the browser. The preview script replaces the status text once the page count arrives, so the
 * served HTML is the only place the changed text can be seen; this asserts it there. Before #6336
 * only {@code JspPageEncodingRegressionTest} read the JSP's source text for it.</p>
 */
@Tag("integration")
@Tag("fax")
class FaxCoverPageJspIntegrationTest {
    private static final String PAGE = "/WEB-INF/jsp/fax/CoverPage.jsp";
    private static final String SERVED_STATUS = "<div id=\"previewStatus\" class=\"text-muted\">Loading preview&#8230;</div>";
    /** How U+2026 read as ISO-8859-1 comes back out of a UTF-8 response, and how a Windows-1252 reader shows it. */
    private static final List<String> ELLIPSIS_MOJIBAKE = List.of("\u00E2\u0080\u00A6", "\u00E2\u20AC\u00A6");

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
    void shouldServeTheLoadingPreviewStatusWithAnEllipsis_beforeThePreviewScriptRuns(String language) throws Exception {
        JspRenderServer.RenderedPage page = server.request(PAGE)
                .header("Accept-Language", language)
                .render(request -> prepareFax(request, "EFORM"))
                .assertOk();

        assertThat(page.body()).contains(SERVED_STATUS);
        Document html = page.document();
        Element status = html.getElementById("previewStatus");
        assertThat(status.wholeText()).isEqualTo("Loading preview\u2026");
        assertThat(html.select("#preview-panel #previewStatus")).hasSize(1);
        assertThat(html.select("#preview-panel #previewPdfLink")).hasSize(1);
        assertThat(html.getElementById("previewImages").children()).isEmpty();
        // Only what the reader sees: the page still has a literal U+2026 in a script comment, which
        // is served garbled but never shown.
        String panel = html.getElementById("preview-panel").outerHtml();
        assertThat(html.text()).doesNotContain("\uFFFD");
        assertThat(panel).doesNotContain("\uFFFD");
        for (String mojibake : ELLIPSIS_MOJIBAKE) {
            assertThat(html.text()).doesNotContain(mojibake);
            assertThat(panel).doesNotContain(mojibake);
        }
    }

    @Test
    void shouldLeaveOutThePreviewPanel_forAConsultationFax() throws Exception {
        JspRenderServer.RenderedPage page = server.render(PAGE, request -> prepareFax(request, "CONSULTATION"))
                .assertOk();

        assertThat(page.document().getElementById("previewStatus")).isNull();
        assertThat(page.document().getElementById("preview-panel")).isNull();
        assertThat(page.body()).doesNotContain("Loading preview");
    }

    /** What Fax2Action.prepareFax leaves for its {@code preview} result once the PDF is staged. */
    private static void prepareFax(HttpServletRequest request, String transactionType) {
        request.getSession().setAttribute("userrole", "doctor");
        request.getSession().setAttribute("user", "999998");
        FaxConfig account = new FaxConfig();
        account.setAccountName("FAKE Clinic Fax");
        account.setFaxNumber("5550000001");
        Path pdf = Path.of("FAKE-fax-4242.pdf");
        request.setAttribute("accounts", List.of(account));
        request.setAttribute("demographicNo", 1001);
        request.setAttribute("documents", List.of(pdf));
        request.setAttribute("transactionType", transactionType);
        request.setAttribute("transactionId", 4242L);
        request.setAttribute("faxFilePath", pdf);
        request.setAttribute("letterheadFax", "5550000001");
        request.setAttribute("professionalSpecialistName", "FAKE Specialist");
        request.setAttribute("fax", "5550000002");
    }
}
