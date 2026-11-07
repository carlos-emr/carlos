/**
 * Copyright (c) 2026 CARLOS Contributors. All Rights Reserved.
 *
 * This software is published under the GPL GNU General Public License.
 */
package io.github.carlos_emr.carlos.documentManager.actions;

import io.github.carlos_emr.carlos.commn.dao.DocumentDao;
import io.github.carlos_emr.carlos.commn.dao.OutboundEmailArchiveDao;
import io.github.carlos_emr.carlos.commn.model.Document;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ActionSupport;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Regression coverage for archive access protections.
 *
 * <p>Page operations rewrite the stored file, which would invalidate the SHA-256 an outbound email
 * archive recorded for it. Since the 2026.08 forward-merge the action answers every refusal itself
 * (JSON 403, {@code NONE}) instead of throwing, so these assert the response and, above all, that the
 * document is never loaded or rewritten.</p>
 *
 * @since 2026-08-25
 */
@DisplayName("SplitDocument2Action archive guard")
@Tag("unit")
@Tag("security")
class SplitDocument2ActionArchiveGuardUnitTest extends CarlosUnitTestBase {

    private static final String REVISION = "0".repeat(64);

    private MockedStatic<ServletActionContext> servletActionContext;
    private MockedStatic<LoggedInInfo> sessions;
    private MockHttpServletRequest request;
    private MockHttpServletResponse response;
    private DocumentDao documentDao;
    private OutboundEmailArchiveDao archiveDao;
    private SplitDocument2Action action;

    @BeforeEach
    void setUp() {
        request = new MockHttpServletRequest("POST", "/documentManager/SplitDocument");
        response = new MockHttpServletResponse();
        documentDao = mock(DocumentDao.class);
        archiveDao = mock(OutboundEmailArchiveDao.class);
        SecurityInfoManager security = mock(SecurityInfoManager.class);
        LoggedInInfo info = mock(LoggedInInfo.class);

        servletActionContext = mockStatic(ServletActionContext.class);
        servletActionContext.when(ServletActionContext::getRequest).thenReturn(request);
        servletActionContext.when(ServletActionContext::getResponse).thenReturn(response);
        sessions = mockStatic(LoggedInInfo.class);
        sessions.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(info);
        when(security.hasPrivilege(eq(info), eq("_edoc"), eq("w"), isNull())).thenReturn(true);

        registerMock(SecurityInfoManager.class, security);
        registerMock(DocumentDao.class, documentDao);
        registerMock(OutboundEmailArchiveDao.class, archiveDao);
        request.setParameter("method", "removeFirstPage");
        request.setParameter("document", "321");
        request.setParameter("sourceRevision", REVISION);
        when(archiveDao.existsByDocumentNo(321)).thenReturn(true);

        action = new SplitDocument2Action();
    }

    @AfterEach
    void tearDown() {
        sessions.close();
        servletActionContext.close();
    }

    @Test
    @DisplayName("should refuse remove-first-page before loading or rewriting the PDF")
    void shouldRefuseRemoveFirstPage_beforeLoadingOrRewritingPdf() throws Exception {
        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"accepted\":false");
        verify(documentDao, never()).getDocument(anyString());
    }

    @Test
    @DisplayName("should refuse an ordinary document id that resolves to an archive filename")
    void shouldRefuseOrdinaryDocumentId_thatResolvesToArchiveFilename() throws Exception {
        Document alias = new Document();
        alias.setDocumentNo(321);
        alias.setDocfilename("20260707120000_outbound-email-44.eml");
        when(archiveDao.existsByDocumentNo(321)).thenReturn(false);
        when(archiveDao.existsByFileName(alias.getDocfilename())).thenReturn(true);
        when(documentDao.getDocument("321")).thenReturn(alias);

        assertThat(action.execute()).isEqualTo(ActionSupport.NONE);

        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).contains("\"accepted\":false");
        verify(documentDao, never()).merge(any());
    }
}
