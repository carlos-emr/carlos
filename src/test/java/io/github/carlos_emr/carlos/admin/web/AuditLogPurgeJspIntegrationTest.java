/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.admin.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.github.carlos_emr.carlos.commn.dao.SecObjPrivilegeDao;
import io.github.carlos_emr.carlos.test.web.JspRenderServer;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Path;
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
 * Renders the Audit Log Purge page, the {@code success} result of {@code admin/AuditLogPurge}, with
 * the request the action leaves. c50477da9931 (#6133) replaced the result paragraph's raw
 * {@code <%=msg%>} with {@code <carlos:encode>}: the server's message must reach the clinician as
 * text, HTML-encoded, in place of the purge form, and an absent message must give the form, never
 * the word "null". Nothing rendered that branch before #6336: the packaged admin-audit-log check
 * never submits the form, and {@code AuditLogPurgeActionUnitTest} stops at the request attribute.
 */
@Tag("integration")
@Tag("admin")
class AuditLogPurgeJspIntegrationTest {
    private static final String PAGE = "/WEB-INF/jsp/admin/auditLogPurge.jsp";
    private static final String HOSTILE_MESSAGE = "<b onmouseover=x>FAKE</b> & Success";

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
    void shouldShowTheServerMessageAsEncodedText_insteadOfThePurgeForm() throws Exception {
        JspRenderServer.RenderedPage page = server.render(PAGE, request -> {
            signInAsAdministrator(request);
            request.setAttribute("msg", HOSTILE_MESSAGE);
        }).assertOk();

        assertThat(page.body())
                .contains("<p style=\"color:red\">&lt;b onmouseover=x&gt;FAKE&lt;/b&gt; &amp; Success")
                .doesNotContain("<b onmouseover");
        Document html = page.document();
        Element message = html.selectFirst("p[style]");
        assertThat(message.attr("style")).isEqualTo("color:red");
        assertThat(message.text()).isEqualTo(HOSTILE_MESSAGE);
        assertThat(message.children()).isEmpty();
        assertThat(html.select("b")).isEmpty();
        assertThat(html.select("form")).isEmpty();
        assertThat(html.getElementById("dateBegin")).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "Error parsing date. Should be in yyyy-MM-dd format",
            "Success. Removed 3 records from the audit log."})
    void shouldShowEachActionMessage_verbatim(String message) throws Exception {
        Document html = server.render(PAGE, request -> {
            signInAsAdministrator(request);
            request.setAttribute("msg", message);
        }).assertOk().document();

        assertThat(html.select("p[style]")).hasSize(1);
        assertThat(html.selectFirst("p[style]").text()).isEqualTo(message);
        assertThat(html.select("form")).isEmpty();
    }

    @Test
    void shouldShowThePurgeForm_whenTheActionSetNoMessage() throws Exception {
        JspRenderServer.RenderedPage page = server.render(PAGE, AuditLogPurgeJspIntegrationTest::signInAsAdministrator)
                .assertOk();

        Document html = page.document();
        Element form = html.selectFirst("form");
        assertThat(form.attr("action")).isEqualTo(JspRenderServer.CONTEXT_PATH + "/admin/AuditLogPurge");
        assertThat(form.attr("method")).isEqualTo("post");
        assertThat(form.select("input#dateBegin[name=dateBegin]")).hasSize(1);
        assertThat(html.select("p[style]")).isEmpty();
        assertThat(page.body()).doesNotContainPattern("\\bnull\\b");
    }

    /** The session {@code <security:oscarSec>} reads; the bean grants the role write on _admin.auditLogPurge. */
    private static void signInAsAdministrator(HttpServletRequest request) {
        request.getSession().setAttribute("userrole", "admin");
        request.getSession().setAttribute("user", "999998");
    }
}
