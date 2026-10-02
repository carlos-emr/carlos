/* Copyright (c) 2026 CARLOS Contributors. GPL-2.0-or-later. */
package io.github.carlos_emr.carlos.eform;

import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@Tag("unit")
class EformLogError2ActionUnitTest extends CarlosUnitTestBase {
    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE"})
    void shouldRejectNonPost_withoutChangingStability(String method) throws Exception {
        var security = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, security);
        var request = new MockHttpServletRequest(method, "/eform/logEformError");
        request.setParameter("formId", "42");
        var response = new MockHttpServletResponse();
        try (var servlet = mockStatic(ServletActionContext.class); var utility = mockStatic(EFormUtil.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            new EformLogError2Action().execute();
            assertThat(response.getStatus()).isEqualTo(405);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            utility.verifyNoInteractions();
        }
    }

    @Test
    void shouldKeepAuthorizedPostReportingFunctional() throws Exception {
        var security = mock(SecurityInfoManager.class);
        registerMock(SecurityInfoManager.class, security);
        when(security.hasPrivilege(any(), eq("_eform"), eq("r"), isNull())).thenReturn(true);
        var request = new MockHttpServletRequest("POST", "/eform/logEformError");
        request.setParameter("formId", "42");
        request.setParameter("error", "Synthetic error");
        try (var servlet = mockStatic(ServletActionContext.class); var utility = mockStatic(EFormUtil.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(new MockHttpServletResponse());
            new EformLogError2Action().execute();
            utility.verify(() -> EFormUtil.logError(42, "Synthetic error"));
        }
    }
}
