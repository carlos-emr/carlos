/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDao;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DisplayName("CombinePDF2Action archive guard")
@Tag("unit")
@Tag("security")
class CombinePDF2ActionArchiveGuardUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContext;
    private MockedStatic<LoggedInInfo> loggedInInfoStatic;
    private DocumentDao documentDao;
    private CombinePDF2Action action;

    @BeforeEach
    void setUp() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        LoggedInInfo loggedInInfo = mock(LoggedInInfo.class);
        SecurityInfoManager securityInfoManager = mock(SecurityInfoManager.class);
        OutboundEmailArchiveDao archiveDao = mock(OutboundEmailArchiveDao.class);
        documentDao = mock(DocumentDao.class);

        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);
        loggedInInfoStatic = mockStatic(LoggedInInfo.class);
        loggedInInfoStatic.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(loggedInInfo);

        registerMock(SecurityInfoManager.class, securityInfoManager);
        registerMock(OutboundEmailArchiveDao.class, archiveDao);
        registerMock(DocumentDao.class, documentDao);
        when(securityInfoManager.hasPrivilege(loggedInInfo, "_edoc", "w", null)).thenReturn(true);
        when(request.getParameterValues("docNo")).thenReturn(new String[] {"321"});
        when(archiveDao.existsByDocumentNo(321)).thenReturn(true);

        action = new CombinePDF2Action();
    }

    @AfterEach
    void tearDown() {
        loggedInInfoStatic.close();
        servletActionContext.close();
    }

    @Test
    @DisplayName("should refuse an archive before resolving or combining its file")
    void shouldRefuseArchive_beforeResolvingOrCombiningFile() {
        assertThatThrownBy(action::execute)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");
        verify(documentDao, never()).getDocument(anyString());
    }
}
