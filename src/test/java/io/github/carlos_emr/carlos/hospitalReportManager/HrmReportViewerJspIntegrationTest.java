/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.hospitalReportManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.commn.model.Provider;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMCategoryDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentCommentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentSubClassDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToDemographicDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMDocumentToProviderDao;
import io.github.carlos_emr.carlos.hospitalReportManager.dao.HRMSubClassDao;
import io.github.carlos_emr.carlos.hospitalReportManager.model.HRMDocumentToDemographic;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.web.JspRenderServer;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Path;
import java.util.List;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Renders the HRM report viewer, hospitalReportManager/displayHRMReport.jsp, for a report id that
 * names no report, as the {@code display} result of {@code hospitalReportManager/Display} and as
 * the inbox's inline view includes it.
 *
 * <p>4f6f848e7ca3 (#6135) made the viewer read the report with {@code HRMDocumentDao.find}, which
 * returns {@code null} for a missing row, and answer 404 before reading anything else; it used to
 * take {@code findById(id).get(0)}, which threw and served the error page. The 404 is decided by
 * this page, not by {@link HRMDisplayReport2Action}: the gate checks only the module right and the
 * report's patient links, and a missing report has none, so it forwards to the page
 * ({@code HRMDisplayReport2ActionUnitTest} pins that side). Inside the inbox's
 * {@code <jsp:include>} the container ignores the status, so the inline view must simply write
 * nothing for the report. The patient lock is still decided before the report row is read.
 * Before #6336 no test asserted the 404.</p>
 */
@Tag("integration")
@Tag("hrm")
class HrmReportViewerJspIntegrationTest {
    private static final String PAGE = "/WEB-INF/jsp/hospitalReportManager/displayHRMReport.jsp";
    /** A test-only stand-in for oscarMDS/Page.jsp's inline include of the viewer, with the same parameters. */
    private static final String INBOX_ROW = "/WEB-INF/jsp/jsp-render-fixture/inboxInlineHrmReport.jsp";
    private static final String PROVIDER = "999998";
    private static final int MISSING_REPORT = 987;
    private static final int LOCKED_REPORT = 988;
    private static final int MISSING_INLINE_REPORT = 989;
    private static final int LOCKED_PATIENT = 4242;

    @TempDir
    static Path directory;
    private static JspRenderServer server;
    private static final HRMDocumentDao DOCUMENTS = mock(HRMDocumentDao.class);
    private static final HRMDocumentToDemographicDao PATIENT_LINKS = mock(HRMDocumentToDemographicDao.class);
    private static final HRMDocumentToProviderDao PROVIDER_LINKS = mock(HRMDocumentToProviderDao.class);
    private static final HRMCategoryDao CATEGORIES = mock(HRMCategoryDao.class);
    private static final HRMDocumentCommentDao COMMENTS = mock(HRMDocumentCommentDao.class);

    @BeforeAll
    static void startServer() throws Exception {
        HRMDocumentToDemographic link = new HRMDocumentToDemographic();
        link.setHrmDocumentId(LOCKED_REPORT);
        link.setDemographicNo(LOCKED_PATIENT);
        when(PATIENT_LINKS.findByHrmDocumentId(LOCKED_REPORT)).thenReturn(List.of(link));
        server = JspRenderServer.builder(directory)
                .jsp(PAGE)
                .file(INBOX_ROW, String.join("\n",
                        "<%@ page contentType=\"text/html; charset=UTF-8\" pageEncoding=\"UTF-8\" %>",
                        "<!DOCTYPE html>",
                        "<html><body>",
                        "<p id=\"before\">FAKE inbox row before</p>",
                        "<jsp:include page=\"/WEB-INF/jsp/hospitalReportManager/displayHRMReport.jsp\" flush=\"true\">",
                        "    <jsp:param name=\"inboxInline\" value=\"true\"/>",
                        "    <jsp:param name=\"id\" value=\"" + MISSING_INLINE_REPORT + "\"/>",
                        "    <jsp:param name=\"segmentID\" value=\"" + MISSING_INLINE_REPORT + "\"/>",
                        "    <jsp:param name=\"providerNo\" value=\"" + PROVIDER + "\"/>",
                        "    <jsp:param name=\"duplicateLabIds\" value=\"\"/>",
                        "</jsp:include>",
                        "<p id=\"after\">FAKE inbox row after</p>",
                        "</body></html>"))
                .bean(SecObjPrivilegeDao.class, JspRenderServer.privilegeDaoGranting("doctor", "x"))
                .bean(SecurityInfoManager.class, mock(SecurityInfoManager.class))
                .bean(HRMDocumentDao.class, DOCUMENTS)
                .bean(HRMDocumentToDemographicDao.class, PATIENT_LINKS)
                .bean(HRMDocumentToProviderDao.class, PROVIDER_LINKS)
                .bean(HRMDocumentSubClassDao.class, mock(HRMDocumentSubClassDao.class))
                .bean(HRMSubClassDao.class, mock(HRMSubClassDao.class))
                .bean(HRMCategoryDao.class, CATEGORIES)
                .bean(HRMDocumentCommentDao.class, COMMENTS)
                .start();
    }

    @AfterAll
    static void stopServer() throws Exception {
        if (server != null) {
            server.close();
        }
    }

    @Test
    void shouldAnswerNotFound_whenNoReportHasTheId() throws Exception {
        JspRenderServer.RenderedPage page = server.request(PAGE)
                .parameter("id", String.valueOf(MISSING_REPORT))
                .parameter("segmentID", String.valueOf(MISSING_REPORT))
                .render(HrmReportViewerJspIntegrationTest::signIn);

        assertThat(page.status()).isEqualTo(404);
        assertThat(page.failure()).isEmpty();
        assertThat(page.document().getElementById("hrmdoc_" + MISSING_REPORT)).isNull();
        assertThat(page.body()).doesNotContain("HRM report not found!").doesNotContain("duplicatesMessage");
        verify(DOCUMENTS).find(MISSING_REPORT);
        // Nothing past the report row is read for a report that is not there.
        verify(CATEGORIES, never()).findAll();
        verify(PROVIDER_LINKS, never()).findByHrmDocumentIdNoSystemUser(MISSING_REPORT);
        verify(COMMENTS, never()).getCommentsForDocument(MISSING_REPORT);
    }

    @Test
    void shouldWriteNothingForTheReport_whenTheInboxIncludesAMissingReport() throws Exception {
        JspRenderServer.RenderedPage page = server.render(INBOX_ROW, HrmReportViewerJspIntegrationTest::signIn)
                .assertOk();

        Document html = page.document();
        assertThat(html.getElementById("before").nextElementSibling()).isSameAs(html.getElementById("after"));
        assertThat(html.getElementById("hrmdoc_" + MISSING_INLINE_REPORT)).isNull();
        assertThat(html.select("div.hrm-report-refused")).isEmpty();
        assertThat(page.body()).doesNotContain("HRM report not found!");
        verify(DOCUMENTS).find(MISSING_INLINE_REPORT);
        verify(COMMENTS, never()).getCommentsForDocument(MISSING_INLINE_REPORT);
    }

    @Test
    void shouldRefuseBeforeReadingTheReport_whenItIsFiledToALockedPatient() throws Exception {
        JspRenderServer.RenderedPage page = server.request(PAGE)
                .parameter("id", String.valueOf(LOCKED_REPORT))
                .render(HrmReportViewerJspIntegrationTest::signIn);

        assertThat(page.status()).isEqualTo(403);
        assertThat(page.failure()).isEmpty();
        verify(DOCUMENTS, never()).find(LOCKED_REPORT);
    }

    /** A provider with the _hrm read right the page's security tag checks; patient locks come from SecurityInfoManager. */
    private static void signIn(HttpServletRequest request) {
        request.getSession().setAttribute("userrole", "doctor");
        request.getSession().setAttribute("user", PROVIDER);
        LoggedInInfo provider = new LoggedInInfo();
        provider.setLoggedInProvider(new Provider(PROVIDER));
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), provider);
    }
}
