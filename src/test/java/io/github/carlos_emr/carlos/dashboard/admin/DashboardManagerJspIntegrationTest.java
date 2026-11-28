/*
 * SPDX-License-Identifier: GPL-2.0-or-later
 * Copyright (C) 2026 CARLOS Contributors
 */
package io.github.carlos_emr.carlos.dashboard.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.commn.model.Dashboard;
import io.github.carlos_emr.carlos.managers.DashboardManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.web.JspRenderServer;
import io.github.carlos_emr.carlos.utility.FormSubmissionGuard;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import java.nio.file.Path;
import java.util.Date;
import java.util.List;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.jsoup.nodes.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.web.MockHttpServletResponse;

/** Exercises the action's refused form through the actual Jasper-rendered result page. */
@Tag("integration")
class DashboardManagerJspIntegrationTest {
    private static final String PAGE = "/WEB-INF/jsp/web/dashboard/admin/DashboardManager.jsp";
    private static final String ROUTE = "/carlos/web/dashboard/admin/DashboardManager";
    @TempDir static Path directory;
    private static JspRenderServer server;
    private DashboardManager manager;
    private SecurityInfoManager security;
    private LoggedInInfo info;
    private Dashboard current;

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

    @BeforeEach
    void setUp() {
        manager = mock(DashboardManager.class);
        security = mock(SecurityInfoManager.class);
        info = mock(LoggedInInfo.class);
        when(security.hasPrivilege(info, "_dashboardManager", "r", null)).thenReturn(true);
        when(security.hasPrivilege(info, "_dashboardManager", "w", null)).thenReturn(true);
        current = new Dashboard();
        current.setId(17);
        current.setName("FAKE current name");
        current.setDescription("The other administrator's description");
        current.setEdited(new Date(2000));
        when(manager.getDashboards(info)).thenReturn(List.of(current));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "FAKE submitted name"})
    void shouldKeepTheSubmittedRevision_whenRedisplayingAnInvalidOrStaleEdit(String name) throws Exception {
        Document html = server.request(PAGE).post()
                .parameter("operation", "save").parameter("dashboardId", "17")
                .parameter("dashboardRevision", "1000").parameter("name", name)
                .parameter("description", "FAKE older description")
                .render(request -> runAction(request, name.isEmpty() ? 400 : 409)).assertOk().document();

        var editor = html.getElementById("dashboard-editor");
        assertThat(editor.selectFirst("input[name=dashboardRevision]").val()).isEqualTo("1000");
        assertThat(editor.selectFirst("input[name=name]").val()).isEqualTo(name);
        assertThat(editor.selectFirst("textarea[name=description]").val()).isEqualTo("FAKE older description");
        assertThat(html.selectFirst("#dashboards input[name=dashboardRevision]").val()).isEqualTo("2000");
        assertThat(html.selectFirst("[role=alert]").text()).isNotBlank();
        if (name.isEmpty()) {
            verify(manager, never()).addDashboard(any(), any(), any());
        } else {
            verify(manager).addDashboard(eq(info), any(), eq("1000"));
            assertThat(html.selectFirst("[role=alert]").text()).contains("not saved", "Reopen");
            assertThat(editor.selectFirst("a#dashboard-reopen").attr("href").replaceFirst(";jsessionid=[^?]*", ""))
                    .isEqualTo(ROUTE + "?dashboardId=17");
        }

        // Correcting validation or simply pressing Save again must still carry the old revision.
        String retryRevision = editor.selectFirst("input[name=dashboardRevision]").val();
        server.request(PAGE).post().parameter("operation", "save").parameter("dashboardId", "17")
                .parameter("dashboardRevision", retryRevision).parameter("name", "FAKE retry name")
                .parameter("description", "FAKE older description")
                .render(request -> runAction(request, 409)).assertOk();
        verify(manager, atLeastOnce()).addDashboard(eq(info), any(), eq("1000"));
        verify(manager, never()).addDashboard(eq(info), any(), eq("2000"));
    }

    @Test
    void shouldLoadTheCurrentRevisionAndEscapeStoredFields_whenReopeningTheEdit() throws Exception {
        current.setName("FAKE <b>name</b> & \"quote\"");
        current.setDescription("</textarea><script>bad()</script>");
        Document html = server.request(PAGE).parameter("dashboardId", "17")
                .render(request -> runAction(request, 200)).assertOk().document();
        var editor = html.getElementById("dashboard-editor");
        assertThat(editor.selectFirst("input[name=dashboardRevision]").val()).isEqualTo("2000");
        assertThat(editor.selectFirst("input[name=name]").val()).isEqualTo(current.getName());
        assertThat(editor.selectFirst("textarea[name=description]").val()).isEqualTo(current.getDescription());
        assertThat(html.select("b, script, [role=alert]")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void shouldHideMutationControls_whenReadOnlyOrLocked(boolean readOnly) throws Exception {
        when(security.hasPrivilege(info, "_dashboardManager", "w", null)).thenReturn(!readOnly);
        current.setLocked(!readOnly);
        Document html = server.request(PAGE).parameter("dashboardId", "17")
                .render(request -> runAction(request, 200)).assertOk().document();
        assertThat(html.select("form")).isEmpty();
        assertThat(html.selectFirst("#dashboards th[scope=row]").text()).isEqualTo(current.getName());
    }

    @Test
    void shouldRenderAOneTimeCreateToken_whenOpeningTheList() throws Exception {
        Document html = server.render(PAGE, request -> runAction(request, 200)).assertOk().document();
        var editor = html.getElementById("dashboard-editor");
        assertThat(editor.attr("action").replaceFirst(";jsessionid=[^?]*", "")).isEqualTo(ROUTE);
        assertThat(editor.attr("method")).isEqualTo("post");
        assertThat(editor.selectFirst("input[name=" + FormSubmissionGuard.PARAMETER + "]").val()).hasSize(36);
        assertThat(editor.selectFirst("input[name=name]").val()).isEmpty();
        assertThat(editor.selectFirst("textarea[name=description]").val()).isEmpty();
    }

    private void runAction(HttpServletRequest request, int expectedStatus) throws Exception {
        var response = new MockHttpServletResponse();
        try (var servlet = mockStatic(ServletActionContext.class); var login = mockStatic(LoggedInInfo.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(info);
            assertThat(new DashboardManager2Action(manager, security).execute()).isEqualTo(ActionSupport.SUCCESS);
        }
        assertThat(response.getStatus()).isEqualTo(expectedStatus);
    }
}
