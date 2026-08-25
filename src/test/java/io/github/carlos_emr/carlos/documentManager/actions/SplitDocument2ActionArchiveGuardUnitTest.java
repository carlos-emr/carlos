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

@DisplayName("SplitDocument2Action archive guard")
@Tag("unit")
@Tag("security")
class SplitDocument2ActionArchiveGuardUnitTest extends CarlosUnitTestBase {

    private MockedStatic<ServletActionContext> servletActionContext;
    private HttpServletRequest request;
    private DocumentDao documentDao;
    private SplitDocument2Action action;

    @BeforeEach
    void setUp() {
        request = mock(HttpServletRequest.class);
        HttpServletResponse response = mock(HttpServletResponse.class);
        documentDao = mock(DocumentDao.class);
        OutboundEmailArchiveDao archiveDao = mock(OutboundEmailArchiveDao.class);

        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);

        registerMock(SecurityInfoManager.class, mock(SecurityInfoManager.class));
        registerMock(DocumentDao.class, documentDao);
        registerMock(OutboundEmailArchiveDao.class, archiveDao);
        when(request.getParameter("document")).thenReturn("321");
        when(archiveDao.existsByDocumentNo(321)).thenReturn(true);

        action = new SplitDocument2Action();
    }

    @AfterEach
    void tearDown() {
        servletActionContext.close();
    }

    @Test
    @DisplayName("should refuse remove-first-page before loading or rewriting the PDF")
    void shouldRefuseRemoveFirstPage_beforeLoadingOrRewritingPdf() {
        assertThatThrownBy(action::removeFirstPage)
                .isInstanceOf(SecurityException.class)
                .hasMessageContaining("controlled archive workflow");

        verify(documentDao, never()).getDocument(anyString());
    }
}
