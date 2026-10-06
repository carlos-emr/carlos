/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.provider.web;

import io.github.carlos_emr.carlos.commn.dao.DocumentDescriptionTemplateDao;
import io.github.carlos_emr.carlos.commn.dao.UserPropertyDAO;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import io.github.carlos_emr.carlos.utility.SpringUtils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Security contract tests for document-description template mutation dispatch.
 *
 * <p>The action intentionally keeps two read methods available to GET, so the aggregated mutator
 * contract cannot drive it without mutation-intent parameters. These focused tests pin the write
 * methods directly.</p>
 */
@Tag("unit")
@Tag("security")
@DisplayName("DocumentDescriptionTemplate2Action")
class DocumentDescriptionTemplate2ActionUnitTest {

    @ParameterizedTest(name = "{0} {1} is rejected before DAO access")
    @CsvSource({
            "GET,addDocumentDescription",
            "HEAD,addDocumentDescription",
            "GET,updateDocumentDescription",
            "HEAD,updateDocumentDescription",
            "GET,deleteDocumentDescription",
            "HEAD,deleteDocumentDescription",
            "GET,saveDocumentDescriptionTemplatePreference",
            "HEAD,saveDocumentDescriptionTemplatePreference"
    })
    @DisplayName("should reject non-POST mutation methods before DAO access")
    void shouldRejectNonPostMutationMethods_beforeDaoAccess(
            String httpMethod, String actionMethod) {

        MockHttpServletRequest request = new MockHttpServletRequest(httpMethod, "/carlos/DocumentDescriptionTemplate");
        request.addParameter("method", actionMethod);
        MockHttpServletResponse response = new MockHttpServletResponse();
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        DocumentDescriptionTemplateDao documentDescriptionTemplateDao =
                mock(DocumentDescriptionTemplateDao.class);
        UserPropertyDAO userPropertyDAO = mock(UserPropertyDAO.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);

        try (MockedStatic<ServletActionContext> servletContext = mockStatic(ServletActionContext.class);
             MockedStatic<SpringUtils> springUtils = mockStatic(SpringUtils.class);
             MockedStatic<LoggedInInfo> loggedInInfoStatic = mockStatic(LoggedInInfo.class)) {

            servletContext.when(ServletActionContext::getRequest).thenReturn(request);
            servletContext.when(ServletActionContext::getResponse).thenReturn(response);
            springUtils.when(() -> SpringUtils.getBean(SecurityInfoManager.class))
                    .thenReturn(securityInfoManager);
            springUtils.when(() -> SpringUtils.getBean(DocumentDescriptionTemplateDao.class))
                    .thenReturn(documentDescriptionTemplateDao);
            springUtils.when(() -> SpringUtils.getBean(UserPropertyDAO.class))
                    .thenReturn(userPropertyDAO);
            loggedInInfoStatic.when(() -> LoggedInInfo.getLoggedInInfoFromSession(any(HttpServletRequest.class)))
                    .thenReturn(loggedInInfo);
            when(securityInfoManager.hasPrivilege(eq(loggedInInfo), eq("_admin"), eq("w"), isNull()))
                    .thenReturn(true);

            DocumentDescriptionTemplate2Action action = new DocumentDescriptionTemplate2Action();

            assertThat(action.execute()).isEqualTo(ActionSupport.NONE);
            assertThat(response.getStatus()).isEqualTo(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            assertThat(response.getHeader("Allow")).isEqualTo("POST");
            verifyNoInteractions(documentDescriptionTemplateDao, userPropertyDAO);
        }
    }
}
