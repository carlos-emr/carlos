/* Copyright (c) 2026 CARLOS Contributors. SPDX-License-Identifier: GPL-2.0-or-later */
package io.github.carlos_emr.carlos.documentManager.gate;

import io.github.carlos_emr.carlos.commn.dao.*;
import io.github.carlos_emr.carlos.commn.model.*;
import io.github.carlos_emr.carlos.managers.ProgramManager2;
import io.github.carlos_emr.carlos.managers.SecurityInfoManager;
import io.github.carlos_emr.carlos.test.unit.CarlosUnitTestBase;
import io.github.carlos_emr.carlos.utility.LoggedInInfo;
import org.apache.struts2.ServletActionContext;
import org.junit.jupiter.api.*;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@Tag("unit")
@Tag("fast")
class ViewDocumentWrite2ActionUnitTest extends CarlosUnitTestBase {
    private final MockHttpServletRequest request = new MockHttpServletRequest();
    private final SecurityInfoManager security = mock(SecurityInfoManager.class);
    private final LoggedInInfo info = mock(LoggedInInfo.class);
    private final DocumentDao documents = mock(DocumentDao.class);
    private final CtlDocumentDao links = mock(CtlDocumentDao.class);
    private final Document document = new Document();
    private MockedStatic<ServletActionContext> servlet;
    private MockedStatic<LoggedInInfo> login;

    @BeforeEach void setUpView() {
        servlet = mockStatic(ServletActionContext.class); servlet.when(ServletActionContext::getRequest).thenReturn(request);
        login = mockStatic(LoggedInInfo.class); login.when(() -> LoggedInInfo.getLoggedInInfoFromSession(request)).thenReturn(info);
        registerMock(SecurityInfoManager.class, security); registerMock(DocumentDao.class, documents);
        registerMock(CtlDocumentDao.class, links); registerMock(PatientLabRoutingDao.class, mock(PatientLabRoutingDao.class));
        registerMock(QueueDocumentLinkDao.class, mock(QueueDocumentLinkDao.class));
        when(security.hasPrivilege(info, "_edoc", "w", (String) null)).thenReturn(true);
        when(documents.find(42)).thenReturn(document);
    }
    @AfterEach void closeStatics() {login.close(); servlet.close();}

    @Test void editRenderCannotTrustSubmittedAccessiblePatient() {
        request.setParameter("editDocumentNo", "42"); request.setParameter("functionid", "100");
        CtlDocument link = new CtlDocument(); link.setId(new CtlDocumentPK("demographic", 200, 42));
        when(links.findByDocumentNoAndModule(42, "demographic")).thenReturn(List.of(link));
        assertThatThrownBy(() -> new ViewDocumentWrite2Action().execute()).isInstanceOf(SecurityException.class);
        verify(security).isAllowedAccessToPatientRecord(info, 200);
    }

    @Test void editRenderCannotExposeAnotherProgramDocument() {
        request.setParameter("editDocumentNo", "42"); document.setRestrictToProgram(true); document.setProgramId(17);
        registerMock(ProgramManager2.class, mock(ProgramManager2.class));
        assertThatThrownBy(() -> new ViewDocumentWrite2Action().execute()).isInstanceOf(SecurityException.class).hasMessageContaining("program");
    }

    @Test void legitimateUnfiledEditStillRenders() throws Exception {
        request.setParameter("editDocumentNo", "42");
        assertThat(new ViewDocumentWrite2Action().execute()).isEqualTo("success");
    }

    @Test void newDocumentUploadFormDoesNotRequireAnExistingDocument() throws Exception {
        assertThat(new ViewDocumentWrite2Action().execute()).isEqualTo("success"); verifyNoInteractions(documents);
    }

    @Test void malformedEditIdIsRejectedBeforeDocumentRead() {
        request.setParameter("editDocumentNo", "42/other");
        assertThatThrownBy(() -> new ViewDocumentWrite2Action().execute()).isInstanceOf(SecurityException.class);
        verifyNoInteractions(documents);
    }

    @Test void forwardedDocumentAttributeOverridesAnAccessibleParameter() {
        request.setParameter("editDocumentNo", "42"); request.setAttribute("editDocumentNo", "77");
        assertThatThrownBy(() -> new ViewDocumentWrite2Action().execute()).isInstanceOf(SecurityException.class);
        verify(documents).find(77); verify(documents, never()).find(42);
    }

    @Test void authorizedForwardUsesTheActualRenderedDocument() throws Exception {
        request.setParameter("editDocumentNo", "77"); request.setAttribute("editDocumentNo", "42");
        assertThat(new ViewDocumentWrite2Action().execute()).isEqualTo("success");
        verify(documents).find(42); verify(documents, never()).find(77);
    }

    @Test void emptyForwardedSelectionDoesNotFallBackToAnUnrenderedParameter() throws Exception {
        request.setParameter("editDocumentNo", "77"); request.setAttribute("editDocumentNo", "");
        assertThat(new ViewDocumentWrite2Action().execute()).isEqualTo("success"); verifyNoInteractions(documents);
    }
}
