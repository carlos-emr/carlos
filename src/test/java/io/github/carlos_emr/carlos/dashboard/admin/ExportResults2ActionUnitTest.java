/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.dashboard.admin;

import java.nio.charset.StandardCharsets;
import io.github.carlos_emr.carlos.managers.DashboardManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ExportResults2ActionUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldWriteExactUtf8Bytes_whenExportContainsNonAscii() throws Exception {
        var security = mock(SecurityInfoManager.class);
        var dashboard = mock(DashboardManager.class);
        var user = mock(LoggedInInfo.class);
        registerMock(SecurityInfoManager.class, security);
        registerMock(DashboardManager.class, dashboard);
        when(security.hasPrivilege(user, "_tickler", SecurityInfoManager.WRITE, null)).thenReturn(true);
        String csv = "Name,Address\nZoë,東京\n";
        when(dashboard.exportDrilldownQueryResultsToCSV(user, 4174)).thenReturn(csv);
        ExportResults2Action action;
        try (var servlet = mockStatic(org.apache.struts2.ServletActionContext.class)) {
            action = new ExportResults2Action();
        }
        action.request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        action.response = response;
        LoggedInInfo.setLoggedInInfoIntoSession(action.request.getSession(), user);
        ((MockHttpServletRequest) action.request).setParameter("indicatorId", "4174");
        assertThat(action.execute()).isEqualTo("none");
        assertThat(response.getContentAsByteArray()).isEqualTo(csv.getBytes(StandardCharsets.UTF_8));
        assertThat(response.getContentLength()).isEqualTo(csv.getBytes(StandardCharsets.UTF_8).length);
        assertThat(response.getCharacterEncoding()).isEqualTo("UTF-8");
    }
}
