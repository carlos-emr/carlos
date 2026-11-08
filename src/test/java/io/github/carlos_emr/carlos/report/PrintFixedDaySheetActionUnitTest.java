/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.report;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.report.pageUtil.printLabDaySheet2Action;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@Tag("unit")
class PrintFixedDaySheetActionUnitTest extends CarlosUnitTestBase {
    @Test
    void shouldAuthorizeBeforeLoading_forTheFixedPdfRoute() throws Exception {
        exercise(false, false);
    }

    @Test
    void shouldReturnAnError_withoutASuccessfulDownloadWhenTheServiceFails() throws Exception {
        exercise(true, true);
    }

    @Test
    void shouldPreserveTheTemplateAllowlist_forAnAuthorizedReader() throws Exception {
        exercise(true, false);
    }

    private void exercise(boolean allowed, boolean fails) throws Exception {
        var security = mock(SecurityInfoManager.class);
        var service = mock(FixedDaySheetReportService.class);
        registerMock(SecurityInfoManager.class, security);
        registerMock(FixedDaySheetReportService.class, service);
        var request = new MockHttpServletRequest();
        var response = new MockHttpServletResponse();
        request.setParameter("input_date", "2025-01-01");
        request.setParameter("xmlStyle", "untrusted.xml");
        when(security.hasPrivilege(any(), eq("_report"), eq("r"), isNull())).thenReturn(allowed);
        if (fails) doThrow(new java.io.IOException("synthetic export failure")).when(service).writePdf(any(), any(), any());
        try (var context = mockStatic(ServletActionContext.class)) {
            context.when(ServletActionContext::getRequest).thenReturn(request);
            context.when(ServletActionContext::getResponse).thenReturn(response);
            var action = new printLabDaySheet2Action();
            if (!allowed) {
                assertThatThrownBy(action::execute).isInstanceOf(SecurityException.class);
                verifyNoInteractions(service);
            } else {
                assertThat(action.execute()).isEqualTo("none");
                verify(service).writePdf(response, "2025-01-01", "labDaySheet.xml");
                assertThat(response.getStatus()).isEqualTo(fails ? 500 : 200);
                if (fails) {
                    assertThat(response.getContentAsByteArray()).isEmpty();
                    assertThat(response.getContentType()).isNull();
                }
            }
        }
    }
}
