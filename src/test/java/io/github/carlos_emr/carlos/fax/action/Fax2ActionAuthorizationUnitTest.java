/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.fax.action;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.github.carlos_emr.carlos.documentManager.DocumentAttachmentManager;
import io.github.carlos_emr.carlos.managers.FaxManager;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

@DisplayName("Fax2Action authorization unit tests")
@Tag("unit")
@Tag("fast")
class Fax2ActionAuthorizationUnitTest {

    @Test
    @DisplayName("should reject getPageCount when fax read privilege is missing")
    void shouldRejectGetPageCount_whenFaxReadPrivilegeMissing() {
        FaxManager faxManager = mock(FaxManager.class);
        DocumentAttachmentManager documentAttachmentManager = mock(DocumentAttachmentManager.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        when(securityInfoManager.hasPrivilege(any(LoggedInInfo.class), eq("_fax"), eq(SecurityInfoManager.READ), isNull()))
                .thenReturn(false);

        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setParameter("faxFilePath", "/tmp/example.pdf");
        LoggedInInfo.setLoggedInInfoIntoSession(request.getSession(), new LoggedInInfo());
        MockHttpServletResponse response = new MockHttpServletResponse();

        try (MockedStatic<SpringUtils> springUtilsMock = mockStatic(SpringUtils.class);
             MockedStatic<ServletActionContext> servletActionContextMock = mockStatic(ServletActionContext.class)) {
            springUtilsMock.when(() -> SpringUtils.getBean(FaxManager.class)).thenReturn(faxManager);
            springUtilsMock.when(() -> SpringUtils.getBean(DocumentAttachmentManager.class)).thenReturn(documentAttachmentManager);
            springUtilsMock.when(() -> SpringUtils.getBean(SecurityInfoManager.class)).thenReturn(securityInfoManager);
            servletActionContextMock.when(ServletActionContext::getRequest).thenReturn(request);
            servletActionContextMock.when(ServletActionContext::getResponse).thenReturn(response);

            Fax2Action action = new Fax2Action();
            action.getPageCount();

            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_FORBIDDEN);
        }
    }
}
