/*
 * Copyright (c) 2026 CARLOS Contributors.
 * Licensed under the GNU General Public License, version 2 or later.
 */
package io.github.carlos_emr.carlos.mds.pageUtil;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import io.github.carlos_emr.carlos.commn.dao.TicklerDao;
import io.github.carlos_emr.carlos.commn.dao.TicklerLinkDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@Tag("unit")
@DisplayName("Lab mutation HTTP method boundaries")
class LabMutationMethodUnitTest extends CarlosUnitTestBase {
    @ParameterizedTest
    @ValueSource(strings = {"GET", "HEAD", "PUT", "DELETE", "PATCH", "OPTIONS", "post"})
    @DisplayName("should reject every non-POST method before lab, macro, or comment side effects")
    void shouldRejectNonPost_beforeDispatch(String method) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/oscarMDS/UpdateStatus");
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityInfoManager security = createAndRegisterMock(SecurityInfoManager.class);
        TicklerDao ticklers = createAndRegisterMock(TicklerDao.class);
        TicklerLinkDao links = createAndRegisterMock(TicklerLinkDao.class);
        UserPropertyDAO preferences = createAndRegisterMock(UserPropertyDAO.class);
        try (MockedStatic<ServletActionContext> servlet = mockStatic(ServletActionContext.class)) {
            servlet.when(ServletActionContext::getRequest).thenReturn(request);
            servlet.when(ServletActionContext::getResponse).thenReturn(response);
            ReportStatusUpdate2Action status = new ReportStatusUpdate2Action();
            assertThat(status.execute()).isEqualTo(ActionSupport.NONE);
            request.setParameter("method", "addComment");
            assertThat(status.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(status.executemain()).isEqualTo(ActionSupport.NONE);
            assertThat(status.addComment()).isEqualTo(ActionSupport.NONE);
            assertThat(new ReportMacro2Action().execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(405);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            assertThat(response.getContentAsString()).isEmpty();
            verifyNoInteractions(security, ticklers, links, preferences);
        }
    }
}
