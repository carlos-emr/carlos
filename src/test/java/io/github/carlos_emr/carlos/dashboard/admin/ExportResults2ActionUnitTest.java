/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.dashboard.admin;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import jakarta.servlet.http.HttpServletResponse;
import io.github.carlos_emr.carlos.managers.DashboardManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ExportResults2ActionUnitTest extends CarlosUnitTestBase {
    private ExportResults2Action action(String csv, HttpServletResponse response) {
        var security = mock(SecurityInfoManager.class);
        var dashboard = mock(DashboardManager.class);
        var user = mock(LoggedInInfo.class);
        registerMock(SecurityInfoManager.class, security);
        registerMock(DashboardManager.class, dashboard);
        when(security.hasPrivilege(user, "_tickler", SecurityInfoManager.WRITE, null)).thenReturn(true);
        when(dashboard.exportDrilldownQueryResultsToCSV(user, 4174)).thenReturn(csv);
        ExportResults2Action action;
        try (var servlet = mockStatic(org.apache.struts2.ServletActionContext.class)) {
            action = new ExportResults2Action();
        }
        var request = new MockHttpServletRequest();
        action.request = request;
        action.response = response;
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), user);
        request.setParameter("indicatorId", "4174");
        return action;
    }

    @Test
    void shouldWriteExactUtf8Bytes_whenExportContainsNonAscii() throws Exception {
        String csv = "Name,Address\nZoë,東京\n";
        var response = new MockHttpServletResponse();
        assertThat(action(csv, response).execute()).isEqualTo("none");
        assertThat(response.getContentAsByteArray()).isEqualTo(csv.getBytes(StandardCharsets.UTF_8));
        assertThat(response.getContentLength()).isEqualTo(csv.getBytes(StandardCharsets.UTF_8).length);
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
        assertThat(response.getHeader("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void shouldPropagateWriteFailure_whenOutputStreamIsUnavailable() throws Exception {
        var response = mock(HttpServletResponse.class);
        when(response.getOutputStream()).thenThrow(new IOException("write failed"));
        var action = action("Name\nZoë\n", response);
        assertThatThrownBy(action::execute).isInstanceOf(IOException.class).hasMessage("write failed");
    }

    @Test
    void shouldPropagateWriteFailure_whenWritingCsvBytes() throws Exception {
        var response = mock(HttpServletResponse.class);
        var stream = mock(jakarta.servlet.ServletOutputStream.class);
        when(response.getOutputStream()).thenReturn(stream);
        doThrow(new IOException("write failed")).when(stream).write(any(byte[].class));
        String csv = "Name\nZoë\n";
        var action = action(csv, response);
        assertThatThrownBy(action::execute).isInstanceOf(IOException.class).hasMessage("write failed");
        verify(stream).write(csv.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void shouldReturnForbidden_whenManagerDeniesExport() throws Exception {
        var response = new MockHttpServletResponse();
        assertThat(action(null, response).execute()).isEqualTo("none");
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsByteArray()).isEmpty();
    }
}
